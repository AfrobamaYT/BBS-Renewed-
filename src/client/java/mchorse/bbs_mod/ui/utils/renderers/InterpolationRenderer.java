package mchorse.bbs_mod.ui.utils.renderers;

import mchorse.bbs_mod.graphics.line.LineBuilder;
import mchorse.bbs_mod.graphics.line.SolidColorLineRenderer;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.tooltips.styles.TooltipStyle;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.InterpolationUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.math.IInterpolation;
import mchorse.bbs_mod.utils.math.MathUtils;

import java.util.List;

public class InterpolationRenderer
{
    private static Color color = new Color();

    public static void renderInterpolationPreview(IInterpolation interp, UIContext context, int x, int y, float anchorX, float anchorY, int duration)
    {
        if (interp == null)
        {
            return;
        }

        final float iterations = 40;
        final float padding = 50;

        int w = 140;
        int h = 130;

        FontRenderer font = context.batcher.getFont();
        TooltipStyle style = TooltipStyle.get();
        String tooltip = InterpolationUtils.getTooltip(interp).get();
        List<String> lines = font.wrap(tooltip, w - 20);
        int ah = lines.isEmpty() ? 0 : lines.size() * (font.getHeight() + 4);

        y = MathUtils.clamp(y, 0, context.menu.height - h - ah);

        x -= (int) (w * anchorX);
        y -= (int) (h * anchorY);

        x = MathUtils.clamp(x, 0, context.menu.width - w);
        y = MathUtils.clamp(y, 0, context.menu.height - h);

        Area.SHARED.set(x, y, w, h + ah);
        style.renderBackground(context, Area.SHARED);

        Color fg = color.set(style.getForegroundColor(), false);
        int fontColor = style.getTextColor();

        fg.a = 0.2F;

        String name = InterpolationUtils.getName(interp).get();

        context.batcher.textShadow(name, x + 10, y + 10, fontColor);

        for (int i = 0; i < lines.size(); i++)
        {
            context.batcher.textShadow(lines.get(i), x + 10, y + h - 5 + i * (font.getHeight() + 4), fontColor);
        }

        LineBuilder grid = new LineBuilder(0.25F);

        grid.add(x + 10, y + 20);
        grid.add(x + 10, y + h - 10);
        grid.add(x + w - 10, y + h - 10);
        grid.add(x + w - 10, y + 20);
        grid.add(x + 10, y + 20);

        grid.push();
        grid.add(x + w / 2, y + 20);
        grid.add(x + w / 2, y + h - 10);

        grid.push();
        grid.add(x + 10, y + h - 10 - padding / 2);
        grid.add(x + w - 10, y + h - 10 - padding / 2);

        grid.push();
        grid.add(x + 10, y + 20 + padding / 2);
        grid.add(x + w - 10, y + 20 + padding / 2);

        int h2 = (h + 10) / 2;

        grid.push();
        grid.add(x + 10, y + h2);
        grid.add(x + w - 10, y + h2);

        grid.render(context.batcher, SolidColorLineRenderer.get(fg));

        fg.a = 1F;

        LineBuilder line = new LineBuilder(0.75F);

        for (int i = 0; i <= iterations; i++)
        {
            float factor = i / iterations;
            float value = 1 - interp.interpolate(0, 1, factor);

            float x1 = x + 10 + factor * (w - 20);
            float y1 = y + 20 + padding / 2 + value * (h - 30 - padding);

            line.add(x1, y1);
        }

        line.render(context.batcher, SolidColorLineRenderer.get(fg));

        context.batcher.text("A", x + 14, (int)(y + h - 10 - padding / 2) + 4, fontColor);
        context.batcher.text("B", x + w - 19, (int)(y + 20 + padding / 2) - font.getHeight() - 4, fontColor);

        float tick = context.getTickTransition() % (duration + 20);
        float factor = MathUtils.clamp(tick / (float) duration, 0, 1);
        int px = x + w - 5;
        int py = y + 20 + (int) (padding / 2) + (int) ((1 - interp.interpolate(0, 1, factor)) * (h - 30 - padding));

        context.batcher.box(px - 2, py - 2, px + 2, py + 2, Colors.A100 + fg.getRGBColor());
    }
}