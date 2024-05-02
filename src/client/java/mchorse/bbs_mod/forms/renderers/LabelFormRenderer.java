package mchorse.bbs_mod.forms.renderers;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.forms.forms.LabelForm;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;

import java.util.List;

public class LabelFormRenderer extends FormRenderer<LabelForm>
{
    public LabelFormRenderer(LabelForm form)
    {
        super(form);
    }

    @Override
    public void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        int color = this.form.color.get(context.getTransition()).getARGBColor();

        context.batcher.wallText(StringUtils.processColoredText(this.form.text.get()), x1 + 4, y1 + 4, color, x2 - x1 - 8);
    }

    @Override
    public void render3D(FormRenderingContext context)
    {
        context.stack.push();

        TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
        float scale = 1F / 16F;

        MatrixStackUtils.scaleStack(context.stack, scale, -scale, scale);

        RenderSystem.disableCull();

        if (this.form.max.get(context.getTransition()) <= 10)
        {
            this.renderString(context, renderer);
        }
        else
        {
            this.renderLimitedString(context, renderer);
        }

        RenderSystem.enableCull();

        context.stack.pop();
    }

    private void renderString(FormRenderingContext context, TextRenderer renderer)
    {
        String content = StringUtils.processColoredText(this.form.text.get());
        float transition = context.getTransition();
        int w = renderer.getWidth(content) - 1;
        int h = renderer.fontHeight - 2;
        int x = (int) (-w * this.form.anchorX.get(transition));
        int y = (int) (-h * this.form.anchorY.get(transition));

        Color shadowColor = this.form.shadowColor.get(transition);
        Color color = this.form.color.get(transition);

        if (shadowColor.a > 0)
        {
            context.stack.push();
            context.stack.translate(0F, 0F, -0.1F);
            renderer.draw(
                content,
                x + this.form.shadowX.get(transition),
                y + this.form.shadowY.get(transition),
                shadowColor.getARGBColor(), false,
                context.stack.peek().getPositionMatrix(),
                MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers(),
                TextRenderer.TextLayerType.NORMAL,
                0,
                context.light
            );
            context.stack.pop();
        }

        renderer.draw(
            content,
            x,
            y,
            color.getARGBColor(), false,
            context.stack.peek().getPositionMatrix(),
            MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers(),
            TextRenderer.TextLayerType.NORMAL,
            0,
            context.light
        );

        RenderSystem.enableDepthTest();

        MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers().draw();

        this.renderShadow(context, x, y, w, h);
    }

    private void renderLimitedString(FormRenderingContext context, TextRenderer renderer)
    {
        float transition = context.getTransition();
        int w = 0;
        int h = renderer.fontHeight - 2;
        String content = StringUtils.processColoredText(this.form.text.get());
        List<String> lines = FontRenderer.wrap(renderer, content, this.form.max.get(transition));

        if (lines.size() <= 1)
        {
            this.renderString(context, renderer);

            return;
        }

        for (String line : lines)
        {
            w = Math.max(renderer.getWidth(line) - 1, w);
            h += 12;
        }

        h -= 12;

        int x = (int) (-w * this.form.anchorX.get(transition));
        int y = (int) (-h * this.form.anchorY.get(transition));
        int y2 = y;

        Color shadowColor = this.form.shadowColor.get(transition);

        if (shadowColor.a > 0)
        {
            context.stack.push();
            context.stack.translate(0F, 0F, -0.1F);

            for (String line : lines)
            {
                int x2 = x + (this.form.anchorLines.get() ? (int) ((w - renderer.getWidth(line)) * this.form.anchorX.get(transition)) : 0);

                renderer.draw(
                    line,
                    x2 + this.form.shadowX.get(transition),
                    y2 + this.form.shadowY.get(transition),
                    shadowColor.getARGBColor(), false,
                    context.stack.peek().getPositionMatrix(),
                    MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers(),
                    TextRenderer.TextLayerType.NORMAL,
                    0,
                    context.light
                );

                y2 += 12;
            }

            context.stack.pop();

            y2 = y;
        }

        int color = this.form.color.get(transition).getARGBColor();

        for (String line : lines)
        {
            int x2 = x + (this.form.anchorLines.get() ? (int) ((w - renderer.getWidth(line)) * this.form.anchorX.get(transition)) : 0);

            renderer.draw(
                line,
                x2,
                y2,
                color, false,
                context.stack.peek().getPositionMatrix(),
                MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers(),
                TextRenderer.TextLayerType.NORMAL,
                0,
                context.light
            );

            y2 += 12;
        }

        MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers().draw();

        RenderSystem.enableDepthTest();

        this.renderShadow(context, x, y, w, h);
    }

    private void renderShadow(FormRenderingContext context, int x, int y, int w, int h)
    {
        float offset = this.form.offset.get(context.getTransition());
        Color color = this.form.background.get(context.getTransition());

        if (color.a <= 0)
        {
            return;
        }

        context.stack.push();
        context.stack.translate(0, 0, -0.2F);

        BufferBuilder builder = Tessellator.getInstance().getBuffer();

        builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        Draw.fillQuad(
            builder, context.stack,
            x + w + offset, y - offset, 0,
            x - offset, y - offset, 0,
            x - offset, y + h + offset, 0,
            x + w + offset, y + h + offset, 0,
            color.r, color.g, color.b, color.a
        );

        RenderSystem.enableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferRenderer.drawWithGlobalProgram(builder.end());
        context.stack.pop();
    }
}