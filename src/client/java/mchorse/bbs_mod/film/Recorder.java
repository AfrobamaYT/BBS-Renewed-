package mchorse.bbs_mod.film;

import net.minecraft.client.renderer.state.CameraRenderState;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.camera.utils.TimeUtils;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.FormPipelines;
import mchorse.bbs_mod.film.replays.FormProperties;
import mchorse.bbs_mod.film.replays.Inventory;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.entities.MCEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.graphics.SofortZeichner;
import mchorse.bbs_mod.morphing.Morph;
import mchorse.bbs_mod.network.ClientNetwork;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.PlayerUtils;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.joml.Vectors;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Recorder extends WorldFilmController
{
    public ReplayKeyframes keyframes = new ReplayKeyframes("keyframes");
    public FormProperties properties = new FormProperties("properties");
    public Inventory inventory = new Inventory("inventory");

    /**
     * Mobs captured within {@link Film#mobRecordingRadius} when recording started.
     * Their base attributes are recorded each tick and turned into replays on stop.
     */
    public final List<RecordedMob> mobs = new ArrayList<>();

    public float hp;
    public float hunger;
    public int xpLevel;
    public float xpProgress;

    private static Matrix4f perspective = new Matrix4f();

    public Form lastForm;
    public Vector3d lastPosition;
    public Vector4f lastRotation;

    public int countdown;
    public final int initialTick;
    /** Erst ab diesem Tick wird aufgezeichnet; davor laeuft der Film nur (Vorlauf der Regiehilfe). */
    public int aufnahmeAb;

    /** Last value committed to the existing FormProperties model/mobId recording track. */
    private String lastFormTrackValue;

    public static void renderCameraPreview(Position position, CameraRenderState camera, PoseStack stack,
        net.minecraft.client.renderer.SubmitNodeCollector sammler)
    {
        if (!BBSSettings.recordingOverlays.get())
        {
            return;
        }

        Vector4f vector = Vectors.TEMP_4F;
        Matrix4f matrix = Matrices.TEMP_4F;
        float x = (float) (position.point.x - camera.pos.x);
        float y = (float) (position.point.y - camera.pos.y);
        float z = (float) (position.point.z - camera.pos.z);
        float fov = MathUtils.toRad(position.angle.fov);
        float aspect = BBSRendering.getVideoWidth() / (float) BBSRendering.getVideoHeight();
        float thickness = 0.025F;

        perspective.identity().perspective(fov, aspect, 0.001F, 100F).invert();

        matrix.identity()
            .rotateY(MathUtils.toRad(position.angle.yaw + 180))
            .rotateX(MathUtils.toRad(-position.angle.pitch));


        /* ⭐ EINREIHEN STATT SOFORTZUG (2026-08-01, an cmlup/26.1 abgelesen).
         *
         * Hier stand ein eigener Zeichendurchgang ueber FormPipelines.holen(TRIANGLES, ...)
         * samt selbst uebergebener Modellansicht. Ergebnis im Bild: statt duenner Frustum-
         * kanten eine grosse gefuellte weisse Flaeche ueber die halbe Vorschau
         * (Paritaets-vergleich gegen 1.21.1, Befund 2).
         *
         * Die RECHNUNG war nie falsch -- sie ist zeichengleich mit 26.1. Falsch war der Weg
         * zum Bild: 26.1 gibt die Kaesten an RenderTypes.debugFilledBox(), also an Vanillas
         * eigenen Zeichentyp fuer genau diesen Zweck. In 26.2 gibt es den weiterhin, nur
         * ohne draw() -- man reicht die Geometrie ueber den Sammler ein.
         *
         * Damit ist es DASSELBE Muster wie beim LineBuilder (a81b4d2a5) und beim Bodengitter:
         * ein eigener Sofortzug, wo der vorgesehene RenderType gereicht haette.
         *
         * ⛔ NACHTRAG 2026-08-02, DER ZWEITE HALBE SCHRITT: der RenderType allein reicht
         * nicht -- die GEOMETRIE muss seine Topologie sprechen. debugFilledBox() faehrt
         * VertexFormat.Mode.QUADS (an der laufenden 26.2 abgefragt, nicht vermutet), Draw
         * schrieb aber eine Dreiecksliste mit 6 Ecken je Flaeche. Der Indexpuffer legt dann
         * 4er-Gruppen (0,1,2,2,3,0) ueber eine 6er-Folge: von 12 richtigen Dreiecken je
         * Kasten ueberleben 3, der Rest wird zu Splittern zwischen den Flaechen. Genau das
         * meldete der Nutzer -- "manche Straenge haarfein, man sieht hindurch". Darum die
         * Vierecks-Variante von Draw.
         *
         * ⚠️ Die Durchsicht hat ZWEI Anteile: die Splitter (hiermit weg) und
         * DEBUG_FILLED_BOX mit writeDepth=false (gemessen). Der zweite ist eine
         * ENTSCHEIDUNG, keine Panne -- 26.1 macht es genauso -- und bleibt deshalb, bis der
         * Nutzer etwas anderes will. */
        sammler.submitCustomGeometry(stack, net.minecraft.client.renderer.rendertype.RenderTypes.debugFilledBox(), (pose, builder) ->
        {

            transformFrustum(vector, matrix, 1F, 1F);
            Draw.fillBoxTo(builder, stack, x, y, z, x + vector.x, y + vector.y, z + vector.z, thickness, 1F, 1F, 1F, 1F, true);

            transformFrustum(vector, matrix, -1F, 1F);
            Draw.fillBoxTo(builder, stack, x, y, z, x + vector.x, y + vector.y, z + vector.z, thickness, 1F, 1F, 1F, 1F, true);

            transformFrustum(vector, matrix, 1F, -1F);
            Draw.fillBoxTo(builder, stack, x, y, z, x + vector.x, y + vector.y, z + vector.z, thickness, 1F, 1F, 1F, 1F, true);

            transformFrustum(vector, matrix, -1F, -1F);
            Draw.fillBoxTo(builder, stack, x, y, z, x + vector.x, y + vector.y, z + vector.z, thickness, 1F, 1F, 1F, 1F, true);

            transformFrustum(vector, matrix, 0F, 0F);
            Draw.fillBoxTo(builder, stack, x, y, z, x + vector.x, y + vector.y, z + vector.z, thickness, 0F, 0.5F, 1F, 1F, true);

        });

        /* 26.2: das disableDepthTest, das hier stand, entfaellt ersatzlos. Es schaltete nach
         * dem Zeichnen den globalen Tiefentest ab; einen globalen Tiefenzustand gibt es nicht
         * mehr, und alles, was danach gezeichnet wird, bringt seinen eigenen in seiner
         * Pipeline mit. Die Vorschau selbst wurde schon vorher mit dem normalen Tiefentest
         * gezeichnet — ein enableDepthTest stand hier nie. */
    }

    private static void transformFrustum(Vector4f vector, Matrix4f matrix, float x, float y)
    {
        vector.set(x, y, 0F, 1F);
        vector.mul(perspective);
        vector.w = 1F;
        vector.normalize().mul(100F);
        vector.w = 1F;
        vector.mul(matrix);
    }

    public Recorder(Film film, Form form, int replayId, int tick)
    {
        super(film);

        this.lastForm = FormUtils.copy(form);
        this.exception = replayId;
        this.tick = tick;
        this.countdown = TimeUtils.toTick(BBSSettings.recordingCountdown.get());
        this.initialTick = tick;
    }

    public boolean hasNotStarted()
    {
        return this.countdown > 0;
    }

    public void update()
    {
        if (this.hasNotStarted())
        {
            this.countdown -= 1;

            return;
        }

        LocalPlayer player = Minecraft.getInstance().player;

        if (this.lastPosition == null)
        {
            this.lastPosition = new Vector3d(player.getX(), player.getY(), player.getZ());
            this.lastRotation = new Vector4f(player.getYRot(), player.getXRot(), player.getYHeadRot(), player.getVisualRotationYInDegrees());
            this.inventory.fromPlayer(player);

            this.hp = player.getHealth();
            this.hunger = player.getFoodData().getFoodLevel();
            this.xpLevel = player.experienceLevel;
            this.xpProgress = player.experienceProgress;

            this.captureMobs(player);
        }

        if (this.tick >= 0 && this.tick >= this.aufnahmeAb)
        {
            Morph morph = Morph.getMorph(player);

            this.keyframes.record(this.tick, morph.entity, null);
            this.recordFormTrack(morph == null ? null : morph.getForm());
            this.recordMobs();
        }

        super.update();
    }

    /**
     * Record the existing string property track that BBS' replay editor labels as Model.
     * ModelForm uses {@code model}; MobForm has the equivalent {@code mobId}. The target replay's
     * form class is authoritative because {@link mchorse.bbs_mod.ui.film.UIFilmPanel#applyRecordedKeyframes}
     * copies these channels into that form after stop. Cross-class switches cannot be represented
     * by either property and are deliberately skipped instead of writing an unusable channel.
     */
    private void recordFormTrack(Form current)
    {
        Replay replay = this.exception >= 0 && this.exception < this.film.replays.getList().size()
            ? this.film.replays.getList().get(this.exception) : null;
        Form target = replay == null ? null : replay.form.get();
        String key;
        String value;

        if (target instanceof ModelForm && current instanceof ModelForm model)
        {
            key = "model";
            value = model.model.get();
        }
        else if (target instanceof MobForm && current instanceof MobForm mob)
        {
            key = "mobId";
            value = mob.mobID.get();
        }
        else
        {
            return;
        }

        if (Objects.equals(this.lastFormTrackValue, value))
        {
            return;
        }

        mchorse.bbs_mod.utils.keyframes.KeyframeChannel channel =
            this.properties.getOrCreate(target, key);

        if (channel != null)
        {
            int index = channel.insert(this.tick, value);
            mchorse.bbs_mod.utils.keyframes.Keyframe keyframe = channel.get(index);

            if (keyframe != null)
            {
                keyframe.getInterpolation().setInterp(mchorse.bbs_mod.utils.interps.Interpolations.CONST);
            }

            this.lastFormTrackValue = value;
        }
    }

    /**
     * Snapshot every living entity (except the recording player) within
     * {@link Film#mobRecordingRadius} into {@link #mobs}. A radius of {@code 0} disables it.
     */
    private void captureMobs(LocalPlayer player)
    {
        float radius = this.film.mobRecordingRadius.get();

        if (radius <= 0)
        {
            return;
        }

        AABB box = player.getBoundingBox().inflate(radius);
        double radiusSq = radius * radius;

        for (LivingEntity entity : player.level().getEntitiesOfClass(LivingEntity.class, box, (e) -> e != player && e.isAlive() && e.distanceToSqr(player) <= radiusSq))
        {
            MobForm form = Morph.createMobForm(entity);

            if (form != null)
            {
                this.mobs.add(new RecordedMob(form, entity));
            }
        }
    }

    private void recordMobs()
    {
        for (RecordedMob mob : this.mobs)
        {
            if (mob.entity.getMcEntity().isAlive())
            {
                mob.keyframes.record(this.tick, mob.entity, null);
            }
        }
    }

    /**
     * A mob captured at recording start: its snapshotted {@link MobForm} plus the
     * keyframes recorded each tick from the live entity it wraps.
     */
    public static class RecordedMob
    {
        public final MobForm form;
        public final MCEntity entity;
        public final ReplayKeyframes keyframes = new ReplayKeyframes("keyframes");

        public RecordedMob(MobForm form, Entity mcEntity)
        {
            this.form = form;
            this.entity = new MCEntity(mcEntity);
        }
    }

    public void render(WorldRenderContext context)
    {
        super.render(context);

        /* Die Kamera-Vorschau wird EINGEREICHT (submitNodeCollector), nicht gezeichnet —
         * sie gehört damit in den Sammel-Durchgang, genau wie Schatten und Namensschild
         * (siehe FilmControllerContext#sammelDurchgang). Ohne diese Bedingung liefe sie in
         * BEIDEN Durchgängen und würde doppelt eingereicht. */
        if (FilmControllerContext.sammelDurchgang)
        {
            renderCameraPreview(this.position, context.worldState().cameraRenderState, context.matrices(), context.commandQueue());
        }
    }

    @Override
    public void shutdown()
    {
        Vector3d pos = this.lastPosition;

        if (pos != null)
        {
            Vector4f rot = this.lastRotation;

            PlayerUtils.teleport(pos.x, pos.y, pos.z, rot.z, rot.y);
            ClientNetwork.sendPlayerForm(this.lastForm);
        }

        super.shutdown();
    }
}
