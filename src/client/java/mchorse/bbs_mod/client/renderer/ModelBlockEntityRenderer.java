package mchorse.bbs_mod.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.blocks.entities.ModelProperties;
import mchorse.bbs_mod.client.renderer.entity.ActorEntityRenderer;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.model.View;
import mchorse.bbs_mod.entity.ActorEntity;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.model_blocks.UIModelBlockPanel;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import mchorse.bbs_mod.compat.alt.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * ⚠️ 26.2 — ZWEI PHASEN STATT EINER, wie bei den Entity-Renderern. {@code
 * BlockEntityRenderer} hat jetzt zwei Typargumente (Blockentity, Zustand): {@code
 * extractRenderState} zieht einmal pro Bild alles Noetige aus der Blockentity, {@code submit}
 * sieht nur noch den Zustand. Das ist der Grund, warum die Blick-Animation (die auf BBS'
 * IEntity schreibt) hier ins Einsammeln gewandert ist — sie ist Zustands-Ermittlung, kein
 * Zeichnen.
 */
//? if >=1.21.9 {
/*public class ModelBlockEntityRenderer implements BlockEntityRenderer<ModelBlockEntity, ModelBlockEntityRenderer.ModelBlockRenderState>
*///?} else {
public class ModelBlockEntityRenderer implements BlockEntityRenderer<ModelBlockEntity>
//?}
{
    private static ActorEntity entity;

    public static void renderShadow(SubmitNodeCollector provider, PoseStack matrices, float tickDelta, double x, double y, double z, float tx, float ty, float tz)
    {
        renderShadow(provider, matrices, tickDelta, x, y, z, tx, ty, tz, 0.5F, 1F);
    }

    /**
     * ⚠️ SIGNATUR IST FESTGENAGELT: {@code ModelShadowTimingMixin} nennt sie mit vollem
     * Deskriptor, {@code BaseFilmController} ruft genau diese Ueberladung. Nicht umbauen.
     */
    public static void renderShadow(SubmitNodeCollector provider, PoseStack matrices, float tickDelta, double x, double y, double z, float tx, float ty, float tz, float radius, float opacity)
    {
        ClientLevel world = Minecraft.getInstance().level;

        if (entity == null || entity.level() != world)
        {
            entity = new ActorEntity(BBSMod.ACTOR_ENTITY, world);
        }

        entity.setPos(x, y, z);
        entity.xOld = x;
        entity.yOld = y;
        entity.zOld = z;
        entity.xo = x;
        entity.yo = y;
        entity.zo = z;

        /* ⚠️ EntityRenderDispatcher.renderShadow(...) — das Ziel des bisherigen Invokers —
         * gibt es nicht mehr. Der Schatten ist in 26.2 ein Auftrag: die Bodenstuecke unter
         * ihm werden beim EINSAMMELN ermittelt (EntityRenderer.extractShadow) und mit
         * SubmitNodeCollector.submitShadow eingereicht.
         *
         * ⚠️ ZWEI ZEILEN SIND DABEI ENTFALLEN, WEIL VANILLA SIE JETZT SELBST MACHT — wer sie
         * stehen laesst, rechnet doppelt:
         *   - `distanceToSqr(x, y, z)` gibt es nicht mehr (nur noch distanceToSqr(Entity)),
         *     UND wird nicht mehr gebraucht: extractShadow liest state.distanceToCameraSq.
         *   - `opacity = (1 - distanz/256) * opacity` steht wortgleich in extractShadow
         *     (Bytecode geprueft), mit getShadowStrength() als zweitem Faktor. Deshalb geht
         *     `opacity` hier als STAERKE in die Sammlung und wird nicht vorher verrechnet.
         *
         * ⚠️ VERHALTENSAENDERUNG: extractShadow prueft zusaetzlich die Video-Einstellung
         * "Entity Shadows" und state.isInvisible. Modellblock-Schatten folgen dieser
         * Einstellung jetzt, vorher taten sie es nicht. */
        EntityRenderer<? super ActorEntity> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);

        if (!(renderer instanceof ActorEntityRenderer actorRenderer))
        {
            return;
        }

        //? if >=1.21.9 {
        /*ActorEntityRenderer.ActorRenderState zustand = actorRenderer.zustandFuerSchatten(entity, tickDelta, radius, opacity);

        if (zustand.shadowPieces.isEmpty())
        {
            return;
        }

        matrices.pushPose();
        matrices.translate(tx, ty, tz);

        provider.submitShadow(matrices, zustand.shadowRadius, zustand.shadowPieces);

        matrices.popPose();
        *///?} else {
        /* Vor 1.21.9: Vanillas Schatten direkt (EntityRenderDispatcher.renderShadow), mit der
         * Abschwaechung nach Entfernung, die Vanilla vor dem Aufruf rechnet. */
        ActorEntityRenderer.ActorRenderState zustand = actorRenderer.createRenderState();

        actorRenderer.extractRenderState(entity, zustand, tickDelta);

        double abstand = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceToSqr(x, y, z);
        float staerke = (float) ((1D - abstand / 256D) * opacity);

        if (staerke <= 0F || !(provider instanceof mchorse.bbs_mod.compat.gpu.SofortSammler sammler))
        {
            return;
        }

        matrices.pushPose();
        matrices.translate(tx, ty, tz);
        mchorse.bbs_mod.compat.mixin.EntitySchattenInvoker.bbs$schatten(matrices, sammler.puffer(), entity, staerke, tickDelta, entity.level(), radius);
        matrices.popPose();
        //?}
    }

    private static float getHeadYaw(float constraint, float yawDelta, float travel)
    {
        float headLimit = (float) Math.toRadians(constraint);
        float headYawBase = MathUtils.clamp(yawDelta, -headLimit, headLimit);

        float syncStart = (float) Math.toRadians(315D);
        float syncRange = (float) Math.toRadians(45D);
        float t = 0F;

        if (travel >= syncStart)
        {
            t = Math.min(1F, (travel - syncStart) / syncRange);
        }

        return headYawBase * (1F - t);
    }

    public ModelBlockEntityRenderer(BlockEntityRendererProvider.Context ctx)
    {}

    /**
     * ⚠️ VERHALTENSAENDERUNG, NICHT UEBERSEHEN: {@code shouldRenderOffScreen} nimmt in 26.2
     * KEINE Blockentity mehr — die Antwort gilt fuer den ganzen RENDERER, nicht mehr je
     * Block. Die Einstellung "global" pro Modellblock kann sie darum nicht mehr steuern.
     *
     * <p>Gewaehlt ist {@code true}, damit ein als global markierter Modellblock weiterhin
     * ausserhalb des Sichtkegels gezeichnet wird (sonst waere ein sichtbares Feature still
     * verschwunden). Preis: auch NICHT-globale Modellbloecke landen jetzt im globalen
     * Durchgang und werden nicht mehr am Sichtkegel weggeschnitten — die Entfernungsgrenze
     * aus {@link #getViewDistance()} (512) gilt weiter. Bei vielen Modellbloecken ist das
     * eine Mehrlast, die gemessen gehoert.
     */
    //? if >=1.21.9 {
    /*@Override
    public boolean shouldRenderOffScreen()
    *///?} else {
    @Override
    public boolean shouldRenderOffScreen(ModelBlockEntity entity)
    //?}
    {
        return true;
    }

    //? if >=1.21.9 {
    /*@Override
    public ModelBlockRenderState createRenderState()
    *///?} else {
    public ModelBlockRenderState createRenderState()
    //?}
    {
        return new ModelBlockRenderState();
    }

    //? if >=1.21.9 {
    /*@Override
    public void extractRenderState(ModelBlockEntity entity, ModelBlockRenderState zustand, float teilTick, Vec3 kameraPos, ModelFeatureRenderer.CrumblingOverlay bruchOverlay)
    {
        BlockEntityRenderer.super.extractRenderState(entity, zustand, teilTick, kameraPos, bruchOverlay);
    *///?} else {
    public void extractRenderState(ModelBlockEntity entity, ModelBlockRenderState zustand, float teilTick, Vec3 kameraPos, ModelFeatureRenderer.CrumblingOverlay bruchOverlay)
    {
        BlockEntityRenderState.extractBase(entity, zustand, bruchOverlay);
    //?}

        Minecraft mc = Minecraft.getInstance();
        ModelProperties properties = entity.getProperties();
        Transform transform = properties.getTransform();
        BlockPos pos = entity.getBlockPos();

        zustand.teilTick = teilTick;
        zustand.form = properties.getForm();
        zustand.bbsEntity = entity.getEntity();
        zustand.zeichnen = zustand.form != null && this.canRender(entity);
        zustand.debugKasten = mc.options.renderDebug;
        zustand.achsen = false;
        zustand.schatten = properties.isShadow();

        if (zustand.zeichnen)
        {
            /* ⚠️ Die Blick-Animation SCHREIBT auf BBS' IEntity (Kopfdrehung/Neigung). Das ist
             * Zustands-Ermittlung und gehoert deshalb hierher — im submit-Zweig wuerde sie
             * die Entity zu einem anderen Zeitpunkt lesen und schreiben als der Rest des
             * Bildes. Der else-Zweig (zuruecksetzen) muss aus demselben Grund mit. */
            if (properties.isLookAt())
            {
                zustand.transform = this.applyLookingAnimation(mc, entity, properties, teilTick);
            }
            else
            {
                IEntity iEntity = entity.getEntity();

                entity.resetLookYaw();
                iEntity.setHeadYaw(0F);
                iEntity.setPrevHeadYaw(0F);
                iEntity.setPitch(0F);
                iEntity.setPrevPitch(0F);

                zustand.transform = transform;
            }

            /* ⚠️ LevelRenderer.getLightColor(Level, BlockPos) ist umgezogen und heisst jetzt
             * LightCoordsUtil.getLightCoords(BlockAndLightGetter, BlockPos). Level erfuellt
             * BlockAndLightGetter (ueber LevelReader), der Aufruf ist sonst unveraendert. */
            zustand.lichtOben = LightCoordsUtil.getLightCoords(entity.getLevel(),
                pos.offset((int) transform.translate.x, (int) transform.translate.y, (int) transform.translate.z));

            zustand.achsen = this.canRenderAxes(entity) && UIBaseMenu.shouldRenderAxes();
        }

        if (zustand.schatten)
        {
            zustand.schattenTx = 0.5F + transform.translate.x;
            zustand.schattenTy = transform.translate.y;
            zustand.schattenTz = 0.5F + transform.translate.z;
            zustand.schattenX = pos.getX() + zustand.schattenTx;
            zustand.schattenY = pos.getY() + zustand.schattenTy;
            zustand.schattenZ = pos.getZ() + zustand.schattenTz;
        }
    }

    /** Form draws kept from submit for BBS' world pass, cleared there each frame. */
    private static final java.util.List<Runnable> WELTZUEGE = new java.util.ArrayList<>();

    /**
     * Draws the model blocks kept since the last frame; called from Films.render, which runs
     * twice per frame. Like the film actors (NativeCrowdPreparation.renderOrCollect) nothing is
     * drawn in the collect pass: the main pass clears color and depth after it, so immediate
     * draws there were lost -- the first version of this did exactly that.
     */
    public static void weltzuegeZeichnen()
    {
        if (mchorse.bbs_mod.film.FilmControllerContext.sammelDurchgang)
        {
            return;
        }

        for (Runnable zug : WELTZUEGE)
        {
            zug.run();
        }

        WELTZUEGE.clear();
    }

    //? if >=1.21.9 {
    /*@Override
    public void submit(ModelBlockRenderState zustand, PoseStack matrices, SubmitNodeCollector vertexConsumers, CameraRenderState kamera)
    *///?} else {
    /** Vor 1.21.9: Vanilla ruft render; der Ablauf extract -> submit laeuft hier selbst. */
    @Override
    public void render(ModelBlockEntity entity, float teilTick, PoseStack matrices, net.minecraft.client.renderer.MultiBufferSource puffer, int licht, int overlay)
    {
        ModelBlockRenderState zustand = this.createRenderState();

        this.extractRenderState(entity, zustand, teilTick, Minecraft.getInstance().gameRenderer.getMainCamera().getPosition(), null);
        zustand.lightCoords = licht;
        this.submit(zustand, matrices, new mchorse.bbs_mod.compat.gpu.SofortSammler(puffer),
            net.minecraft.client.renderer.state.LevelRenderState.INSTANZ.cameraRenderState);
    }

    public void submit(ModelBlockRenderState zustand, PoseStack matrices, SubmitNodeCollector vertexConsumers, CameraRenderState kamera)
    //?}
    {
        matrices.pushPose();
        matrices.translate(0.5F, 0F, 0.5F);

        if (zustand.zeichnen)
        {
            matrices.pushPose();

            MatrixStackUtils.applyTransform(matrices, zustand.transform);

            /* Das Paar enableDepthTest/disableDepthTest um die Form faellt ersatzlos weg.
             * Die Form zeichnet ueber ihre eigene Pipeline (ModelVAOPipeline.DURCHSICHTIG,
             * DepthStencilState(GREATER_THAN_OR_EQUAL, true) — das ist der normale Tiefentest
             * unter dem umgekehrten Z von 26.2) bzw. ueber einen RenderType, der ihn mitbringt.
             * Das anschliessende Abschalten galt Draw.coolerAxes darunter, und der bringt
             * seinen Tiefenzustand ebenfalls selbst mit. */

            /* ⚠️ Der Ueberlagerungs-Wert kam bis 1.21.1 als render()-Parameter herein — und war
             * dort IMMER OverlayTexture.NO_OVERLAY (BlockEntityRenderDispatcher reichte
             * nichts anderes durch). Der Bruch-Fortschritt liegt in 26.2 als
             * zustand.breakProgress separat vor und ist kein Ueberlagerungs-UV mehr.
             * Es geht also nichts verloren. */
            /* Not drawn here: submit runs before the level is drawn, and BBS models draw
             * immediately -- they were painted over (task 122). The pose is kept and the form
             * drawn in BBS' world pass with the film actors (BBSRendering.renderCoolStuff). */
            Form form = zustand.form;
            IEntity bbsEntity = zustand.bbsEntity;
            int licht = zustand.lichtOben;
            float teilTick = zustand.teilTick;
            PoseStack.Pose pose = mchorse.bbs_mod.compat.alt.AltApi.kopie(matrices.last());

            /* Bounded: should the draw pass ever not run, the list must not grow. */
            if (WELTZUEGE.size() > 4096)
            {
                WELTZUEGE.clear();
            }

            WELTZUEGE.add(() ->
            {
                PoseStack stapel = new PoseStack();

                stapel.last().set(pose);
                FormUtilsClient.render(form, new FormRenderingContext()
                    .set(FormRenderType.MODEL_BLOCK, bbsEntity, stapel, licht, OverlayTexture.NO_OVERLAY, teilTick)
                    .camera(Minecraft.getInstance().gameRenderer.mainCamera()));
            });

            if (zustand.achsen)
            {
                matrices.pushPose();
                MatrixStackUtils.scaleBack(matrices);
                Draw.coolerAxes(matrices, 0.5F, 0.005F);
                matrices.popPose();
            }

            matrices.popPose();
        }

        /* 26.2: disableDepthTest ist entfallen -- der Zustand steckt in der Pipeline
         * des Zuges. Absicht war: Tiefentest aus. */

        if (zustand.debugKasten)
        {
            Draw.renderBox(matrices, -0.5D, 0, -0.5D, 1, 1, 1, 0, 0.5F, 1F, 0.5F);
        }

        matrices.popPose();

        if (zustand.schatten)
        {
            renderShadow(vertexConsumers, matrices, zustand.teilTick,
                zustand.schattenX, zustand.schattenY, zustand.schattenZ,
                zustand.schattenTx, zustand.schattenTy, zustand.schattenTz);
        }
    }

    private Transform applyLookingAnimation(Minecraft mc, ModelBlockEntity entity, ModelProperties properties, float tickDelta)
    {
        Transform transform = properties.getTransform();
        Camera camera = mc.gameRenderer.mainCamera();
        Vec3 position = !mc.options.getCameraType().isFirstPerson() && mc.player != null
            ? mc.player.getEyePosition(tickDelta)
            : camera.position();

        BlockPos pos = entity.getBlockPos();
        double x = pos.getX() + 0.5D + transform.translate.x;
        double y = pos.getY() + transform.translate.y;
        double z = pos.getZ() + 0.5D + transform.translate.z;

        double dx = position.x - x;
        double dz = position.z - z;
        double distance = Math.sqrt(dx * dx + dz * dz);

        float initialYaw = lookYaw(transform);
        float yaw = (float) Math.atan2(dx, dz);
        float yawContinuous = entity.updateLookYawContinuous(yaw);
        float yawDelta = yawContinuous - initialYaw;
        float travel = Math.abs(yawDelta) % (MathUtils.PI * 2F);

        Transform finalTransform = transform.copy();
        Form form = properties.getForm();
        boolean lookAt = form instanceof MobForm;
        float headHeight = form.hitboxHeight.get() * form.hitboxEyeHeight.get() * finalTransform.scale.y;
        float constraint = 45F;
        boolean isPitching = true;

        if (form instanceof ModelForm modelForm)
        {
            ModelInstance model = ModelFormRenderer.getModel(modelForm);

            View view = model == null ? null : model.getView();

            if (view != null)
            {
                String headKey = view.headBone;

                lookAt = true;
                constraint = view.constraint;
                isPitching = view.pitch;

                if (FormUtilsClient.getBones(modelForm).contains(headKey))
                {
                    MatrixCache matrices = new MatrixCache();

                    model.captureMatrices(matrices);

                    Matrix4f matrix = matrices.get(headKey).matrix();

                    if (matrix != null)
                    {
                        headHeight = matrix.getTranslation(new Vector3f()).y * finalTransform.scale.y;
                    }
                }
            }
        }

        setLookYaw(finalTransform, yawContinuous);

        if (lookAt)
        {
            IEntity iEntity = entity.getEntity();
            double deltaHead = position.y - (y + headHeight);
            float pitch = MathUtils.clamp((float) Math.atan2(deltaHead, distance), -MathUtils.PI / 2F, MathUtils.PI / 2F);
            float headYaw = getHeadYaw(constraint, yawDelta, travel);
            float anchorYaw = yawDelta - headYaw;

            if (travel >= (float) Math.toRadians(359D))
            {
                headYaw = 0F;
                anchorYaw = 0F;

                entity.snapLookYawToBase(yaw, initialYaw);
            }

            setLookYaw(finalTransform, initialYaw + anchorYaw);
            headYaw = -MathUtils.toDeg(headYaw);
            pitch = -MathUtils.toDeg(isPitching ? pitch : 0F);

            iEntity.setHeadYaw(headYaw);
            iEntity.setPrevHeadYaw(headYaw);
            iEntity.setPitch(pitch);
            iEntity.setPrevPitch(pitch);
        }

        return finalTransform;
    }

    /**
     * The block transform's ZYX yaw channel, mode-aware: the euler channel directly, or — on a
     * quaternion transform, where the channels are stale — the quat decomposed on the branch
     * nearest those stale channels, so the yaw reads the same value the euler mode would hold
     * (a naive principal decomposition flips branches past ±90° and would read a wrong yaw).
     */
    private static float lookYaw(Transform transform)
    {
        if (transform.rotationMode == Transform.RotationMode.QUATERNION)
        {
            return Matrices.toCompatibleEulerZYXRadians(transform.quat, transform.rotate, new Vector3f()).y;
        }

        return transform.rotate.y;
    }

    /**
     * Writes the ZYX yaw channel mode-aware: the euler channel directly, or the quaternion
     * re-composed about the same compatible decomposition's X/Z tilt with the new yaw — the exact
     * quaternion equivalent of {@code rotate.y = yaw}, so look-at turns a quaternion-mode block
     * identically to a euler one.
     */
    private static void setLookYaw(Transform transform, float yaw)
    {
        if (transform.rotationMode == Transform.RotationMode.QUATERNION)
        {
            Vector3f euler = Matrices.toCompatibleEulerZYXRadians(transform.quat, transform.rotate, new Vector3f());

            transform.quat.rotationZYX(euler.z, yaw, euler.x);

            return;
        }

        transform.rotate.y = yaw;
    }

    @Override
    public int getViewDistance()
    {
        return 512;
    }

    private boolean canRenderAxes(ModelBlockEntity entity)
    {
        if (UIScreen.getCurrentMenu() instanceof UIDashboard dashboard)
        {
            if (dashboard.getPanels().panel instanceof UIModelBlockPanel modelBlockPanel)
            {
                /* The selected block shows the interactive gizmo instead of the plain axes. */
                return !modelBlockPanel.isShowingGizmo(entity);
            }
        }

        return false;
    }

    private boolean canRender(ModelBlockEntity entity)
    {
        if (!entity.getProperties().isEnabled())
        {
            return false;
        }

        if (!BBSSettings.renderAllModelBlocks.get())
        {
            return false;
        }

        if (UIScreen.getCurrentMenu() instanceof UIDashboard dashboard)
        {
            if (dashboard.getPanels().panel instanceof UIModelBlockPanel modelBlockPanel)
            {
                return !modelBlockPanel.isEditing(entity) || UIModelBlockPanel.toggleRendering;
            }
        }

        return true;
    }

    /**
     * Alles, was {@link #submit} braucht, EINMAL pro Bild aus der Blockentity gezogen.
     *
     * <p>⚠️ Nur {@link #extractRenderState} fuellt diese Felder; Zustandsobjekte werden
     * wiederverwendet, ein nicht gesetztes Feld traegt den Wert des vorherigen Bildes.
     */
    public static class ModelBlockRenderState extends BlockEntityRenderState
    {
        public Form form;
        public IEntity bbsEntity;

        /** Die tatsaechlich angewandte Transformation (Blick-Animation schon eingerechnet). */
        public Transform transform;

        /** Licht am Ort ueber dem Block — der Modellblock beleuchtet sich nicht am Boden. */
        public int lichtOben;

        public float teilTick;

        /** Ob die Form ueberhaupt gezeichnet wird (Einstellungen + Editor-Zustand). */
        public boolean zeichnen;

        /** Ob die Achsenkreuz-Hilfe dazu kommt. */
        public boolean achsen;

        /** Ob der Debug-Kasten gezeichnet wird (F3). */
        public boolean debugKasten;

        /** Ob dieser Block einen Schatten wirft, und wo. */
        public boolean schatten;
        public double schattenX;
        public double schattenY;
        public double schattenZ;
        public float schattenTx;
        public float schattenTy;
        public float schattenTz;
    }
}
