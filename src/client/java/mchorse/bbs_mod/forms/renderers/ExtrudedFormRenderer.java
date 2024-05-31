package mchorse.bbs_mod.forms.renderers;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.forms.forms.ExtrudedForm;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.texture.TextureExtruder;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public class ExtrudedFormRenderer extends FormRenderer<ExtrudedForm>
{
    public ExtrudedFormRenderer(ExtrudedForm form)
    {
        super(form);
    }

    @Override
    public void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        Link t = this.form.texture.get(context.getTransition());

        if (t == null)
        {
            return;
        }

        Texture texture = context.render.getTextures().getTexture(t);

        float min = Math.min(texture.width, texture.height);
        int ow = (x2 - x1) - 4;
        int oh = (y2 - y1) - 4;

        int w = (int) ((texture.width / min) * ow);
        int h = (int) ((texture.height / min) * ow);

        int x = x1 + (ow - w) / 2 + 2;
        int y = y1 + (oh - h) / 2 + 2;

        context.batcher.fullTexturedBox(texture, x, y, w, h);
    }

    @Override
    protected void render3D(FormRenderingContext context)
    {
        Link texture = this.form.texture.get(context.getTransition());
        TextureExtruder.CachedExtrudedData data = BBSModClient.getTextures().getExtruder().get(texture);

        if (data != null)
        {
            GameRenderer gameRenderer = MinecraftClient.getInstance().gameRenderer;

            gameRenderer.getLightmapTextureManager().enable();
            gameRenderer.getOverlayTexture().setupOverlayColor();
            BBSModClient.getTextures().bindTexture(texture);

            RenderSystem.setShader(getShader(context, GameRenderer::getRenderTypeEntityTranslucentProgram, BBSShaders::getPickerBillboardProgram));

            BufferBuilder buffer = Tessellator.getInstance().getBuffer();
            Matrix4f matrix = context.stack.peek().getPositionMatrix();
            Matrix3f normal = context.stack.peek().getNormalMatrix();

            buffer.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);

            for (int i = 0; i < data.getCount(); i++)
            {
                int offset = i * 8;

                buffer.vertex(matrix, data.data[offset], data.data[offset + 1], data.data[offset + 2])
                    .color(1F, 1F, 1F, 1F)
                    .texture(data.data[offset + 3], data.data[offset + 4])
                    .overlay(context.overlay)
                    .light(context.light)
                    .normal(normal, data.data[offset + 5], data.data[offset + 6], data.data[offset + 7])
                    .next();
            }

            RenderSystem.defaultBlendFunc();
            RenderSystem.enableBlend();
            BufferRenderer.drawWithGlobalProgram(buffer.end());
            RenderSystem.disableBlend();

            gameRenderer.getLightmapTextureManager().disable();
            gameRenderer.getOverlayTexture().teardownOverlayColor();
        }
    }
}