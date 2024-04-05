package mchorse.bbs_mod.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.entity.ActorEntity;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.mixin.client.EntityRendererDispatcherInvoker;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.model_blocks.UIModelBlockPanel;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;

public class ModelBlockEntityRenderer implements BlockEntityRenderer<ModelBlockEntity>
{
    private ActorEntity entity;

    public ModelBlockEntityRenderer(BlockEntityRendererFactory.Context ctx)
    {}

    @Override
    public void render(ModelBlockEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay)
    {
        if (this.entity == null)
        {
            this.entity = new ActorEntity(BBSMod.ACTOR_ENTITY, null);
        }

        ModelBlockEntity.Properties properties = entity.getProperties();
        Transform transform = properties.getTransform();
        BlockPos pos = entity.getPos();

        matrices.push();
        matrices.translate(0.5F, 0F, 0.5F);

        if (MinecraftClient.getInstance().getDebugHud().shouldShowDebugHud())
        {
            Draw.renderBox(matrices, -0.5D, 0, -0.5D, 1, 1, 1, 0, 0.5F, 1F, 0.5F);
        }

        MatrixStackUtils.multiply(matrices, transform.createMatrix());

        double x = pos.getX() + 0.5D + transform.translate.x;
        double y = pos.getY() + transform.translate.y;
        double z = pos.getZ() + 0.5D + transform.translate.z;

        this.entity.setPos(x, y, z);
        this.entity.lastRenderX = x;
        this.entity.lastRenderY = y;
        this.entity.lastRenderZ = z;
        this.entity.prevX = x;
        this.entity.prevY = y;
        this.entity.prevZ = z;

        int lightAbove = WorldRenderer.getLightmapCoordinates(entity.getWorld(), pos.add((int) transform.translate.x, (int) transform.translate.y, (int) transform.translate.z));
        Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();

        if (properties.getForm() != null && this.canRender(entity))
        {
            RenderSystem.enableDepthTest();
            FormUtilsClient.render(properties.getForm(), FormRenderingContext
                .set(entity.getEntity(), matrices, lightAbove, tickDelta)
                .camera(camera));
            RenderSystem.disableDepthTest();
        }

        matrices.pop();

        if (properties.getShadow())
        {
            double distance = MinecraftClient.getInstance().getEntityRenderDispatcher().getSquaredDistanceToCamera(x, y, z);
            float radius = 0.5F;
            float opacity = 1F;

            opacity = (float) (1D - distance / 256D * opacity);

            matrices.push();
            matrices.translate(0.5F + transform.translate.x,  transform.translate.y, 0.5F + transform.translate.z);

            EntityRendererDispatcherInvoker.bbs$renderShadow(matrices, vertexConsumers, this.entity, opacity, tickDelta, entity.getWorld(), radius);

            matrices.pop();
        }
    }

    private boolean canRender(ModelBlockEntity entity)
    {
        if (
            MinecraftClient.getInstance().currentScreen instanceof UIScreen screen &&
            screen.getMenu() instanceof UIDashboard dashboard
        ) {
            if (dashboard.getPanels().panel instanceof UIModelBlockPanel modelBlockPanel)
            {
                return !modelBlockPanel.isEditing(entity);
            }
        }

        return true;
    }
}