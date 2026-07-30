package mchorse.bbs_mod.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import mchorse.bbs_mod.entity.GunProjectileEntity;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.items.GunProperties;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.interps.Lerps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public class GunProjectileEntityRenderer extends EntityRenderer<GunProjectileEntity>
{
    public GunProjectileEntityRenderer(EntityRendererProvider.Context ctx)
    {
        super(ctx);
    }

    @Override
    public Identifier getTextureLocation(GunProjectileEntity entity)
    {
        return Identifier.parse("minecraft:textures/entity/player/wide/steve.png");
    }

    @Override
    public void render(GunProjectileEntity projectile, float yaw, float tickDelta, PoseStack matrices, SubmitNodeCollector vertexConsumers, int light)
    {
        matrices.pushPose();

        GunProperties properties = projectile.getProperties();
        int out = properties.lifeSpan - 2;

        float bodyYaw = Mth.rotLerp(tickDelta, projectile.yRotO, projectile.getYRot());
        float pitch = Mth.rotLerp(tickDelta, projectile.xRotO, projectile.getXRot());
        float scale = Lerps.envelope(projectile.tickCount + tickDelta, 0, properties.fadeIn, out - properties.fadeOut, out);

        if (properties.yaw) matrices.mulPose(Axis.YP.rotationDegrees(bodyYaw));
        if (properties.pitch) matrices.mulPose(Axis.XP.rotationDegrees(-pitch));
        matrices.scale(scale, scale, scale);
        MatrixStackUtils.applyTransform(matrices, properties.projectileTransform);

        /* Das enableDepthTest/disableDepthTest-Paar um den Form-Aufruf entfaellt: seit 26.2
         * ist der Tiefentest Teil der Pipeline des jeweiligen Zeichenwegs und nicht mehr
         * umschaltbar. Die Form bringt ihn selbst mit — Modellformen ueber ModelVAOPipeline,
         * gepufferte Formen ueber die Pipeline ihres RenderType. Auch das Abschalten danach
         * hat kein Ziel mehr: der Namensschild-Draw in super.render erbt keinen globalen
         * Zustand, sondern zieht seinen aus der eigenen Pipeline. */
        FormUtilsClient.render(projectile.getForm(), new FormRenderingContext()
            .set(FormRenderType.ENTITY, projectile.getEntity(), matrices, light, OverlayTexture.NO_OVERLAY, tickDelta)
            .camera(Minecraft.getInstance().gameRenderer.mainCamera()));

        matrices.popPose();

        super.render(projectile, yaw, tickDelta, matrices, vertexConsumers, light);
    }
}