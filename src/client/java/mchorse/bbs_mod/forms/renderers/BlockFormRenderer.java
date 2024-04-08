package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.forms.forms.BlockForm;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.joml.Vectors;
import mchorse.bbs_mod.utils.math.MathUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;

public class BlockFormRenderer extends FormRenderer<BlockForm>
{
    private Matrix4f uiMatrix = new Matrix4f();

    public BlockFormRenderer(BlockForm form)
    {
        super(form);
    }

    @Override
    public void renderUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        VertexConsumerProvider.Immediate consumers = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
        MatrixStack matrices = context.batcher.getContext().getMatrices();

        float scale = (y2 - y1) / 2.5F;
        int x = x1 + (x2 - x1) / 2;
        float y = y1 + (y2 - y1) * 0.85F;

        this.uiMatrix.identity();
        this.uiMatrix.translate(x, y, 40);
        this.uiMatrix.scale(scale, -scale, scale);
        this.uiMatrix.rotateX(MathUtils.PI / 8);
        this.uiMatrix.rotateY(MathUtils.toRad(context.mouseX - (x1 + x2) / 2) + MathUtils.PI);
        this.uiMatrix.mul(this.form.transform.get(context.getTransition()).createMatrix());

        matrices.push();
        MatrixStackUtils.multiply(matrices, this.uiMatrix);
        matrices.translate(-0.5F, 0F, -0.5F);

        matrices.peek().getNormalMatrix().getScale(Vectors.EMPTY_3F);
        matrices.peek().getNormalMatrix().scale(1F / Vectors.EMPTY_3F.x, -1F / Vectors.EMPTY_3F.y, 1F / Vectors.EMPTY_3F.z);

        MinecraftClient.getInstance().getBlockRenderManager().renderBlockAsEntity(this.form.blockState.get(context.getTransition()), matrices, consumers, LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);
        consumers.draw();

        matrices.pop();
    }

    @Override
    protected void render3D(FormRenderingContext context)
    {
        VertexConsumerProvider.Immediate consumers = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();

        context.stack.push();
        context.stack.translate(-0.5F, 0F, -0.5F);

        MinecraftClient.getInstance().getBlockRenderManager().renderBlockAsEntity(this.form.blockState.get(context.getTransition()), context.stack, consumers, context.light, OverlayTexture.DEFAULT_UV);
        consumers.draw();

        context.stack.pop();
    }
}