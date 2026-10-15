package mchorse.bbs_mod.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import mchorse.bbs_mod.entity.GunProjectileEntity;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.items.GunProperties;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.interps.Lerps;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;

/**
 * ⚠️ 26.2 — ZWEI PHASEN STATT EINER, siehe Klassendoc von {@link ActorEntityRenderer}.
 * {@code EntityRenderer} braucht zwei Typargumente, die Entity gibt es nur noch beim
 * Einsammeln, gezeichnet wird aus dem Zustand.
 */
public class GunProjectileEntityRenderer extends EntityRenderer<GunProjectileEntity, GunProjectileEntityRenderer.GunProjectileRenderState>
{
    public GunProjectileEntityRenderer(EntityRendererProvider.Context ctx)
    {
        super(ctx);
    }

    @Override
    public GunProjectileRenderState createRenderState()
    {
        return new GunProjectileRenderState();
    }

    @Override
    public void extractRenderState(GunProjectileEntity projectile, GunProjectileRenderState zustand, float teilTick)
    {
        super.extractRenderState(projectile, zustand, teilTick);

        GunProperties properties = projectile.getProperties();
        int out = properties.lifeSpan - 2;

        zustand.form = projectile.getForm();
        zustand.bbsEntity = projectile.getEntity();
        zustand.teilTick = teilTick;

        /* Die komplette Flugbahn-Mathematik wandert vom Zeichnen ins Einsammeln — sie liest
         * ausschliesslich Entity-Felder (yRotO/xRotO/tickCount) und die Waffen-Einstellung. */
        zustand.drehen = properties.yaw;
        zustand.neigen = properties.pitch;
        zustand.koerperDrehung = Mth.rotLerp(teilTick, projectile.yRotO, projectile.getYRot());
        zustand.neigung = Mth.rotLerp(teilTick, projectile.xRotO, projectile.getXRot());
        zustand.skalierung = Lerps.envelope(projectile.tickCount + teilTick, 0, properties.fadeIn, out - properties.fadeOut, out);
        zustand.transform = properties.projectileTransform;
    }

    //? if >=1.21.9 {
    @Override
    public void submit(GunProjectileRenderState zustand, PoseStack matrices, SubmitNodeCollector sammler, CameraRenderState kamera)
    //?} else {
    /*/^* Vor 1.21.9: Vanilla ruft render; abgegeben wird sofort (SofortSammler). ^/
    @Override
    public void render(GunProjectileRenderState zustand, PoseStack matrices, net.minecraft.client.renderer.MultiBufferSource puffer, int licht)
    {
        zustand.lightCoords = licht;
        this.submit(zustand, matrices, new mchorse.bbs_mod.compat.gpu.SofortSammler(puffer),
            net.minecraft.client.renderer.state.LevelRenderState.INSTANZ.cameraRenderState);
    }

    public void submit(GunProjectileRenderState zustand, PoseStack matrices, SubmitNodeCollector sammler, CameraRenderState kamera)
    *///?}
    {
        matrices.pushPose();

        if (zustand.drehen) matrices.rotate(Axis.YP.rotationDegrees(zustand.koerperDrehung));
        if (zustand.neigen) matrices.rotate(Axis.XP.rotationDegrees(-zustand.neigung));
        matrices.scale(zustand.skalierung, zustand.skalierung, zustand.skalierung);
        MatrixStackUtils.applyTransform(matrices, zustand.transform);

        /* Das enableDepthTest/disableDepthTest-Paar um den Form-Aufruf entfaellt: seit 26.2
         * ist der Tiefentest Teil der Pipeline des jeweiligen Zeichenwegs und nicht mehr
         * umschaltbar. Die Form bringt ihn selbst mit — Modellformen ueber ModelVAOPipeline,
         * gepufferte Formen ueber die Pipeline ihres RenderType. Auch das Abschalten danach
         * hat kein Ziel mehr: der Namensschild-Draw in super.submit erbt keinen globalen
         * Zustand, sondern zieht seinen aus der eigenen Pipeline. */
        FormUtilsClient.render(zustand.form, new FormRenderingContext()
            .set(FormRenderType.ENTITY, zustand.bbsEntity, matrices, zustand.lightCoords, OverlayTexture.NO_OVERLAY, zustand.teilTick)
            .camera(Minecraft.getInstance().gameRenderer.mainCamera()));

        matrices.popPose();

        /* Namensschild; hiess bis 1.21.1 super.render(...). */
        //? if >=1.21.9 {
        super.submit(zustand, matrices, sammler, kamera);
        //?} else {
        /*super.render(zustand, matrices, ((mchorse.bbs_mod.compat.gpu.SofortSammler) sammler).puffer(), zustand.lightCoords);
        *///?}
    }

    /* ⚠️ getTextureLocation(GunProjectileEntity) ist ersatzlos entfallen — EntityRenderer hat
     * in 26.2 keine Textur mehr. War hier ohnehin tot (die Form bringt ihre Textur mit). */

    /**
     * ⚠️ Wie bei {@link ActorEntityRenderer.ActorRenderState}: nur {@code extractRenderState}
     * fuellt diese Felder. Zustandsobjekte werden wiederverwendet — ein nicht gesetztes Feld
     * traegt den Wert des vorherigen Bildes.
     */
    public static class GunProjectileRenderState extends EntityRenderState
    {
        //? if <1.21.9 {
        /*/^* Vor 1.21.9 traegt EntityRenderState kein Licht; render setzt es hier. ^/
        public int lightCoords;
        *///?}
        public Form form;
        public IEntity bbsEntity;
        public float teilTick;

        /** Ob die Waffe das Geschoss um Hoch- bzw. Querachse ausrichtet. */
        public boolean drehen;
        public boolean neigen;

        public float koerperDrehung;
        public float neigung;

        /** Ein-/Ausblend-Skalierung ueber die Lebenszeit. */
        public float skalierung;

        /** Feste Zusatz-Transformation aus der Waffen-Einstellung. */
        public Transform transform;
    }
}
