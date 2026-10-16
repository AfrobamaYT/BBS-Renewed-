package mchorse.bbs_mod.ui.framework.elements.input.keyframes;

import mchorse.bbs_mod.graphics.line.LineBuilder;
import mchorse.bbs_mod.graphics.line.SolidColorLineRenderer;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.IUIKeyframeGraph;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.UIKeyframeGraph;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import org.lwjgl.sdl.SDLMouse;
import java.util.Locale;

/** Draws the motion shift handles, the dope sheet strips and the percent card (BBS FS 8f64c66b4). */
final class MotionShiftZeichnung
{
    private MotionShiftZeichnung()
    {}

    static void zeichnen(UIKeyframeMotionShift s, UIContext context)
    {
        UIKeyframeMotionShift.Hit hovered = s.isDragging() ? s.dragging : s.hit(context);
        int top = s.view.area.y + IUIKeyframeGraph.TOP_MARGIN;

        context.batcher.clip(new Area(s.view.graphArea.x, top, s.view.graphArea.w, s.view.area.ey() - top), context);

        for (UIKeyframeSheet sheet : s.view.getGraph().getSheets())
        {
            int y = s.dope() ? s.view.getDopeSheet().getDopeSheetY(sheet) + (int) s.view.getDopeSheet().getTrackHeight() / 2 : top;

            if (s.dope() && (y < top + 4 || y >= s.view.area.ey() - 4 || s.view.getDopeSheet().getSheet(y) != sheet)) continue;

            for (int i = 0; i + 1 < sheet.channel.getKeyframes().size(); i++)
            {
                Keyframe<?> key = sheet.channel.get(i);

                if (s.dope()) streifen(s, context, sheet, key, sheet.channel.get(i + 1), y);
                if (s.isDragging() && key == s.dragging.key()) continue;

                UIKeyframeMotionShift.Hit hit = s.sichtbar(sheet, key, sheet.channel.get(i + 1));

                if (hit != null) griff(s, context, hit, hovered != null && hovered.key() == hit.key());
            }
        }

        if (s.isDragging()) griff(s, context, s.dragging, true);
        context.batcher.unclip(context);

        if (s.isDragging())
        {
            float shift = s.dragging.key().getMotionShift();
            String label = shift == 0F ? "0%" : String.format(Locale.ROOT, "%+.1f%%", shift * 100F);
            int tx = Math.max(s.view.area.x + 4, Math.min(context.mouseX + 12, s.view.area.ex() - context.batcher.getFont().getWidth(label) - 4));
            int ty = Math.max(s.view.area.y + 4, Math.min(context.mouseY + 12, s.view.area.ey() - context.batcher.getFont().getHeight() - 4));

            context.batcher.textCard(label, tx, ty, Colors.WHITE, 0xE6181818);
        }
    }

    /** Dope sheet: a strip from the handle to the slow side; stronger shift, stronger strip. */
    private static void streifen(UIKeyframeMotionShift s, UIContext context, UIKeyframeSheet sheet, Keyframe<?> key, Keyframe<?> next, int y)
    {
        float shift = key.getMotionShift();

        if (shift == 0F || !key.supportsMotionShift() || !key.isEnabled() || key.getFactory().compare(key.getValue(), next.getValue())) return;

        float start = key.getTick(), finish = key.getDuration() > 0F ? Math.min(next.getTick(), start + key.getDuration()) : next.getTick();
        float midpoint = start + (finish - start) * (0.5F + shift);
        int left = Math.max(s.view.graphArea.x, s.view.toGraphX(shift > 0F ? start : midpoint));
        int right = Math.min(s.view.graphArea.ex(), s.view.toGraphX(shift > 0F ? midpoint : finish));

        if (right > left) context.batcher.box(left, y - 2, right, y + 2, Colors.setA(sheet.color, 0.35F * Math.min(1F, Math.abs(shift) / 0.49F)));
    }

    private static void griff(UIKeyframeMotionShift s, UIContext context, UIKeyframeMotionShift.Hit hit, boolean highlighted)
    {
        int x = s.view.toGraphX(s.mitteTick(hit)), y = s.handleY(hit);
        int left = Math.max(s.view.graphArea.x, s.view.toGraphX(hit.start()));
        int right = Math.min(s.view.graphArea.ex(), s.view.toGraphX(Math.min(hit.start() + hit.duration(), hit.sheet().channel.get(hit.sheet().channel.indexOf(hit.key()) + 1).getTick())));
        boolean dragging = s.dragging != null && s.dragging.key() == hit.key();
        boolean active = dragging || (highlighted && s.onHandle(context, hit));
        int color = active ? Colors.WHITE : Colors.setA(hit.sheet().color, highlighted ? 1F : 0.5F);

        if (highlighted && s.view.getGraph() instanceof UIKeyframeGraph graph)
        {
            LineBuilder line = new LineBuilder(1F);
            int steps = Math.max(2, (right - left) / 3);

            for (int i = 0; i <= steps; i++)
            {
                int px = left + (right - left) * i / steps;

                line.add(px, graph.toGraphY(hit.sheet().channel.getFactory().getY(hit.sheet().channel.interpolate((float) s.view.fromGraphX(px)))));
            }

            line.render(context.batcher, SolidColorLineRenderer.get(Colors.COLOR.set(Colors.setA(hit.sheet().color, 0.65F))));
        }
        else if (highlighted) context.batcher.box(left, y, right, y + 1, Colors.setA(hit.sheet().color, 0.45F));

        if (dragging)
        {
            int center = s.view.toGraphX(hit.start() + hit.duration() * 0.5F);

            context.batcher.box(center, y - 4, center + 1, y + 5, Colors.setA(Colors.WHITE, 0.3F));
        }

        context.batcher.box(x, y - 3, x + 1, y + 4, color);
        context.batcher.box(x - 2, y - 3, x + 3, y - 2, color);
        context.batcher.box(x - 2, y + 3, x + 3, y + 4, color);

        if (active) context.requestCursor(SDLMouse.SDL_SYSTEM_CURSOR_EW_RESIZE);
    }
}
