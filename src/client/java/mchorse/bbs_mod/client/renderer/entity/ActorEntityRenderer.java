package mchorse.bbs_mod.client.renderer.entity;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import mchorse.bbs_mod.cubic.render.vanilla.ArmorRenderer;
import mchorse.bbs_mod.entity.ActorEntity;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;

public class ActorEntityRenderer extends EntityRenderer<ActorEntity>
{
    public static ArmorRenderer armorRenderer;

    public ActorEntityRenderer(EntityRendererProvider.Context ctx)
    {
        super(ctx);

        armorRenderer = new ArmorRenderer(
            new HumanoidArmorModel(ctx.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
            new HumanoidArmorModel(ctx.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
            ctx.getModelManager()
        );

        this.shadowRadius = 0.5F;
    }

    @Override
    public Identifier getTextureLocation(ActorEntity entity)
    {
        return Identifier.parse("minecraft:textures/entity/player/wide/steve.png");
    }

    @Override
    public void render(ActorEntity livingEntity, float yaw, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumers, int light)
    {
        matrices.pushPose();

        float yBodyRot = Mth.rotLerp(tickDelta, livingEntity.yBodyRotO, livingEntity.yBodyRot);
        int overlay = LivingEntityRenderer.getOverlayCoords(livingEntity, 0F);

        this.setupTransforms(livingEntity, matrices, yBodyRot, tickDelta);

        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        FormUtilsClient.render(livingEntity.getForm(), new FormRenderingContext()
            .set(FormRenderType.ENTITY, livingEntity.getEntity(), matrices, light, overlay, tickDelta)
            .camera(Minecraft.getInstance().gameRenderer.mainCamera()));
        RenderSystem.disableDepthTest();
        RenderSystem.disableBlend();

        matrices.popPose();

        super.render(livingEntity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    protected boolean isVisible(ActorEntity entity)
    {
        return !entity.isInvisible();
    }

    protected void setupTransforms(ActorEntity entity, PoseStack matrices, float bodyYaw, float tickDelta)
    {
        if (!entity.hasPose(Pose.SLEEPING))
        {
            matrices.mulPose(Axis.YP.rotationDegrees(-bodyYaw));
        }

        if (entity.deathTime > 0)
        {
            float deathAngle = (entity.deathTime + tickDelta - 1F) / 20F * 1.6F;

            matrices.mulPose(Axis.ZP.rotationDegrees(Math.min(Mth.sqrt(deathAngle), 1F) * 90F));
        }
    }
}