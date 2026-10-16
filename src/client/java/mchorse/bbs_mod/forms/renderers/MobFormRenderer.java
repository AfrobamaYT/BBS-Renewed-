package mchorse.bbs_mod.forms.renderers;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.forms.CustomVertexConsumerProvider;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.ITickable;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.joml.Vectors;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MobFormRenderer extends FormRenderer<MobForm> implements ITickable
{
    private static final Map<ModelPart, Transform> cache = new HashMap<>();
    private static Pose currentPose;
    private static Pose currentPoseOverlay;
    private static MobForm currentForm;

    /** The vanilla entity this form renders through, kept in step with the actor (BBS FS 0a6aeef93) */
    private final mchorse.bbs_mod.forms.renderers.mob.MobStandIn standIn = new mchorse.bbs_mod.forms.renderers.mob.MobStandIn();
    private Entity entity;
    private final mchorse.bbs_mod.forms.renderers.mob.MobPickNetz pickNetz = new mchorse.bbs_mod.forms.renderers.mob.MobPickNetz();

    public static Pose getCurrentPose()
    {
        return currentPose;
    }

    public static Pose getCurrentPoseOverlay()
    {
        return currentPoseOverlay;
    }

    /** The form's extra pose tracks while it submits; none otherwise. */
    public static List<mchorse.bbs_mod.settings.values.core.ValuePose> getCurrentOverlays()
    {
        return currentPose == null || currentForm == null ? List.of() : currentForm.additionalOverlays;
    }

    public static Map<ModelPart, Transform> getCache()
    {
        return cache;
    }

    public MobFormRenderer(MobForm form)
    {
        super(form);
    }

    @Override
    public List<String> getBones()
    {
        this.ensureEntity();

        mchorse.bbs_mod.forms.renderers.mob.MobKnochen rig = mchorse.bbs_mod.forms.renderers.mob.MobKnochen.von(this.entity);

        if (rig != null)
        {
            return new ArrayList<>(rig.teile.keySet());
        }

        return super.getBones();
    }

    /** A body part with a bone rides that bone of the mob; one without stays where it was (BBS FS e341fa733) */
    @Override
    public void renderBodyParts(mchorse.bbs_mod.forms.renderers.FormRenderingContext context)
    {
        mchorse.bbs_mod.forms.renderers.utils.MatrixCache knochen = null;

        for (mchorse.bbs_mod.forms.forms.BodyPart part : this.form.parts.getAllTyped())
        {
            if (part.bone.get().isEmpty())
            {
                this.renderBodyPart(part, context);

                continue;
            }

            if (knochen == null)
            {
                knochen = new mchorse.bbs_mod.forms.renderers.utils.MatrixCache();
                this.ensureEntity();
                mchorse.bbs_mod.forms.renderers.mob.MobMatrizen.sammeln(this.entity, this.zusammen(), context.getTransition(), new PoseStack(), knochen, "");
            }

            var eintrag = knochen.get(part.bone.get());
            Matrix4f matrix = eintrag == null ? null : eintrag.matrix();

            context.stack.pushPose();
            if (context.world != null) context.world.pushPose();

            if (matrix != null)
            {
                mchorse.bbs_mod.utils.MatrixStackUtils.multiply(context.stack, matrix);
                if (context.world != null) mchorse.bbs_mod.utils.MatrixStackUtils.multiply(context.world, matrix);
            }

            this.renderBodyPart(part, context);
            context.stack.popPose();
            if (context.world != null) context.world.popPose();
        }
    }

    /** The form's pose with its overlays */
    private Pose zusammen()
    {
        Pose pose = this.form.pose.get().copy().ueberlagern(this.form.poseOverlay.get());

        for (mchorse.bbs_mod.settings.values.core.ValuePose weitere : this.form.additionalOverlays) pose.ueberlagern(weitere.get());

        return pose;
    }

    /** The mob's bones too, so anchors, trackers and the gizmo can sit on them (BBS FS e341fa733) */
    @Override
    public void collectMatrices(mchorse.bbs_mod.forms.entities.IEntity entity, PoseStack stack, mchorse.bbs_mod.forms.renderers.utils.MatrixCache matrices, String prefix, float transition)
    {
        super.collectMatrices(entity, stack, matrices, prefix, transition);
        this.ensureEntity();

        stack.pushPose();
        this.applyTransforms(stack, false, transition);
        mchorse.bbs_mod.forms.renderers.mob.MobMatrizen.sammeln(this.entity, this.zusammen(), transition, stack, matrices, prefix);
        stack.popPose();
    }

    /** The form, then each bone in rig order: the pick mesh's ids are those places (BBS FS e341fa733) */
    @Override
    protected void updateStencilMap(mchorse.bbs_mod.forms.renderers.FormRenderingContext context)
    {
        mchorse.bbs_mod.forms.renderers.mob.MobKnochen rig = mchorse.bbs_mod.forms.renderers.mob.MobKnochen.von(this.entity);

        context.stencilMap.addPicking(this.form);

        if (rig != null) rig.teile.keySet().forEach((name) -> context.stencilMap.addPicking(this.form, name));
    }

    private void bindTexture()
    {
        Link link = this.form.texture.get();

        if (link != null)
        {
            BBSModClient.getTextures().bindTexture(link);
        }
    }

    private void ensureEntity()
    {
        this.entity = this.standIn.ensure(this.form.mobID.get(), this.form.mobNBT.get(), this.form.slim.get(), this.form.isPlayer());
    }

    /**
     * Reicht die Wegwerf-Entity beim Bildgraphen ein.
     *
     * <p>⚠️ 26.2: {@code EntityRenderDispatcher.render(entity, x, y, z, yaw, teilTick,
     * stapel, verbraucher, licht)} gibt es nicht mehr — der Weg ist zweiphasig geworden:
     * {@code extractEntity()} zieht den Zustand aus der Entity, {@code submit()} reicht ihn
     * ein. Drei Dinge dabei, die man nicht raten darf:
     *
     * <ul>
     *   <li><b>Der yaw-Parameter (hier immer 0F) faellt weg</b> — folgenlos: er wurde schon
     *       in 1.21.1 nur an {@code EntityRenderer.render} durchgereicht, und
     *       {@code LivingEntityRenderer} ignorierte ihn und rechnete seine Koerperdrehung
     *       selbst aus {@code yBodyRotO}/{@code yBodyRot}. Genau die steht jetzt im
     *       Zustand, aus derselben Quelle.</li>
     *   <li><b>Das Licht wird nach dem Sammeln UEBERSCHRIEBEN.</b> Bis 1.21.1 war es ein
     *       render()-Parameter; jetzt setzt {@code extractRenderState} es aus der
     *       Weltposition der Wegwerf-Entity — und die steht irgendwo, nicht dort, wo die
     *       Form erscheint. Ohne das Ueberschreiben waere eine Form in der Oberflaeche
     *       stockdunkel.</li>
     *   <li><b>Die Kamera kommt aus dem laufenden Bild</b>, nicht aus einer selbstgebauten:
     *       {@code submit} reicht sie an den Namensschild- und den Flammen-Auftrag weiter,
     *       eine erfundene wuerde beide falsch ausrichten.</li>
     * </ul>
     */
    private void entityEinreichen(PoseStack stack, CustomVertexConsumerProvider consumers, float teilTick, int licht)
    {
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        EntityRenderState zustand = dispatcher.extractEntity(this.entity, teilTick);

        zustand.lightCoords = licht;

        CameraRenderState kamera = Minecraft.getInstance().gameRenderer.gameRenderState().levelRenderState.cameraRenderState;

        dispatcher.submit(zustand, kamera, 0D, 0D, 0D, stack, consumers.sammler());
    }

    @Override
    protected void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        this.ensureEntity();

        if (this.entity != null)
        {
            PoseStack stack = context.batcher.pose();

            stack.pushPose();

            Matrix4f uiMatrix = ModelFormRenderer.getUIMatrix(context, x1, y1, x2, y2);
            CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();
            float scale = this.form.uiScale.get();
            float width = this.entity.getBbWidth();
            float height = this.entity.getBbHeight();

            scale = scale * Math.min(1.8F / Math.max(width, height), 1F);

            this.applyTransforms(uiMatrix, context.getTransition());
            MatrixStackUtils.multiply(stack, uiMatrix);
            stack.scale(scale, scale, scale);

            if (!this.form.mobID.get().equals("minecraft:ender_dragon"))
            {
                stack.rotate(Axis.YP.rotation(MathUtils.PI));
            }

            stack.last().normal().getScale(Vectors.EMPTY_3F);
            stack.last().normal().scale(1F / Vectors.EMPTY_3F.x, -1F / Vectors.EMPTY_3F.y, 1F / Vectors.EMPTY_3F.z);

            BooleanHolder first = new BooleanHolder();

            /* ⛔ 26.2: der Hijack ist ersatzlos entfallen. Er setzte beim Zeichnen JEDER Ebene
             * einen anderen Shader -- ein Haken in Vanillas Sofort-Zeichnung, die es nicht
             * mehr gibt. Der Shader steht jetzt in der Pipeline des jeweiligen Zuges; fuer
             * den Auswahl-Durchgang waehlt ihn die Pipeline dort, nicht ein Rueckruf hier. */

            consumers.setUI(true);
            this.entityEinreichen(stack, consumers, context.getTransition(), LightCoordsUtil.pack(15, 0));
            consumers.draw();
            consumers.setUI(false);


            stack.popPose();

            /* ⛔ 26.2: RenderSystem.depthFunc(GL_ALWAYS) ist ersatzlos entfallen — es gibt
             * keinen globalen Tiefenvergleich mehr, er steckt in der Pipeline des jeweiligen
             * Zuges. Hier stand er ABSICHTLICH (nicht als Aufraeumen): alles, was nach einer
             * Mob-Form in der Oberflaeche gezeichnet wurde, sollte deren Tiefenwerte
             * ignorieren und immer davor landen.
             * ⚠️ VERHALTENSAENDERUNG, GEMELDET: dieser Vorrang faellt weg. Zurueckholen
             * laesst er sich nur ueber einen eigenen RenderType bzw. eine eigene Pipeline
             * fuer die UI-Zuege danach — nicht ueber einen Schalter. Sichtbar wuerde es
             * daran, dass ein UI-Element hinter einer Mob-Form verschwindet. */
        }
    }

    @Override
    protected void render3D(FormRenderingContext context)
    {
        this.ensureEntity();

        /* The pick pass runs in the UI phase, where no world collector exists to submit to */
        if (this.entity != null && context.isPicking())
        {
            context.stack.pushPose();

            if (this.form.mobID.get().equals("minecraft:ender_dragon")) context.stack.rotate(Axis.YP.rotation(MathUtils.PI));

            this.pickNetz.zeichnen(this.entity, this.zusammen(), context.getTransition(), this.form.texture.get(), context.stack, context.stencilMap.objectIndex, context.stencilMap.increment);
            context.stack.popPose();
        }
        else if (this.entity != null)
        {
            CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();
            int light = context.light;
            BooleanHolder first = new BooleanHolder();
            Matrix4f cached = new Matrix4f(RenderSystem.getModelViewMatrixCopy());

            context.stack.pushPose();
            if (context.world != null)
            {
                context.world.pushPose();
            }

            if (this.form.mobID.get().equals("minecraft:ender_dragon"))
            {
                context.stack.rotate(Axis.YP.rotation(MathUtils.PI));
                if (context.world != null)
                {
                    context.world.rotate(Axis.YP.rotation(MathUtils.PI));
                }
            }

            if (this.entity instanceof LivingEntity entity)
            {
                int u = context.overlay & '\uffff';
                int v = context.overlay >> 16 & '\uffff';

                entity.hurtTime = v != 10 ? 100 : 0;
            }

            currentPose = this.form.pose.get();
            currentPoseOverlay = this.form.poseOverlay.get();
            currentForm = this.form;

            /* Publishing the form's camera-space origin opts its translucent layers (slime
             * bodies, ghost textures) into the deferred sorted pass. */
            {
                Vector3f origin = context.stack.last().pose().getTranslation(new Vector3f());

                FormTranslucentQueue.setSortOrigin(new Matrix4f(RenderSystem.getModelViewMatrixCopy()).transformPosition(origin));
            }

            this.entityEinreichen(context.stack, consumers, context.getTransition(), light);

            currentPose = currentPoseOverlay = null;
            currentForm = null;

            consumers.draw();
            FormTranslucentQueue.setSortOrigin(null);

            context.stack.popPose();

            if (context.world != null)
            {
                context.world.popPose();
            }

            /* Restore the shared model-view matrix captured at entry instead of zeroing it.
             * The old identity() leaked into the 2D UI batch when a MobForm is rendered as a
             * body part inside a list/preview (through this 3D path) — the GUI transform was
             * dropped, so every UI element drawn afterwards landed off-screen ("half the UI
             * disappears" when a MobForm is nested under a ModelForm). Restoring is correct for
             * the 3D viewport too. */
            /* 26.2: das enableDepthTest, das hier stand, entfaellt ersatzlos. Es stellte nach
             * dem Zeichnen den globalen Tiefentest wieder her — den gibt es nicht mehr, jeder
             * folgende Zeichenaufruf bekommt seinen Tiefenzustand aus seiner Pipeline.
             * ⚠️ Nicht zu verwechseln mit dem depthFunc(GL_ALWAYS) am Ende von renderInUI:
             * das setzte ABSICHTLICH einen abweichenden Zustand fuer das, was danach
             * gezeichnet wird. Es ist inzwischen ebenfalls entfernt (es gibt keinen globalen
             * Tiefenvergleich mehr) und dort als Verhaltensaenderung ausgewiesen — hier steht
             * es nur, damit die beiden Faelle nicht verwechselt werden. */
            /* ⛔ 26.2-PORT, NICHT MECHANISCH ERSETZEN: das hier SCHREIBT in die lebende
             * Matrix. `getModelViewMatrix()` heisst in 26.2 `getModelViewMatrixCopy()` --
             * aber eine Kopie zu beschreiben uebersetzt sauber und stellt NICHTS wieder her.
             * Die Wiederherstellung oben faellt dann still aus, und die halbe UI verschwindet
             * wieder (genau der Bug, den der Kommentar darueber beschreibt).
             * Dies ist die EINZIGE schreibende Stelle im ganzen Baum -- alle 23 anderen
             * Vorkommen lesen nur und sind unkritisch. Der Ersatz ist getModelViewStack()
             * (existiert in 26.2 weiter, javap belegt), nicht die Kopie. */
            RenderSystem.getModelViewMatrixCopy().set(cached);
        }
    }

    @Override
    public void tick(IEntity entity)
    {
        this.ensureEntity();
        this.standIn.tick(entity);
    }

    private static class BooleanHolder
    {
        public boolean bool;
    }
}
