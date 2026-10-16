package mchorse.bbs_mod.ui.framework.elements.input.keyframes;

import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.IUIKeyframeGraph;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.UIKeyframeDopeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.UIKeyframeGraph;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeLoop;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import mchorse.bbs_mod.client.FormPipelines;
import org.joml.Matrix4f;
import org.lwjgl.sdl.SDLMouse;

/**
 * Draws UIKeyframeLoops: each loop's band, its end handle and the ghost keys of the repeats, and
 * the status line while dragging. From BBS FS 494fe5d19 (Wemppy4/bbs-fs, MIT), kept apart so the
 * loop editor stays one readable file.
 */
final class KeyframeLoopZeichnung
{
    private KeyframeLoopZeichnung()
    {}

    static void zeichnen(UIKeyframeLoops loops, UIContext context)
    {
        UIKeyframeLoops.Hit hovered = loops.hit(context);
        if (loops.ziehtSchleife()) context.requestCursor(loops.resizing ? SDLMouse.SDL_SYSTEM_CURSOR_EW_RESIZE : SDLMouse.SDL_SYSTEM_CURSOR_POINTER);
        else if (hovered != null && context.mouseY >= hovered.y() && context.mouseY < hovered.y() + hovered.height()
            && Math.abs(context.mouseX - loops.view.toGraphX(hovered.sheet().channel.getLoopEnd(hovered.loop()))) <= 5)
        {
            context.requestCursor(SDLMouse.SDL_SYSTEM_CURSOR_EW_RESIZE);
        }
        boolean dope = loops.view.getGraph() == loops.view.getDopeSheet();
        int rulerBottom = loops.view.area.y + IUIKeyframeGraph.TOP_MARGIN;
        context.batcher.clip(loops.view.graphArea.x, rulerBottom, loops.view.graphArea.w, loops.view.graphArea.ey() - rulerBottom, context);
        for (UIKeyframeSheet sheet : loops.view.getGraph().getSheets())
        {
            int y = dope ? loops.view.getDopeSheet().getDopeSheetY(sheet) : rulerBottom;
            int height = dope ? (int) loops.view.getDopeSheet().getTrackHeight() : 16;
            if (dope && loops.view.getDopeSheet().getSheet(y + height / 2) != sheet) continue;
            if (y + height < rulerBottom || y > loops.view.area.ey()) continue;

            for (Object entry : sheet.channel.getLoops())
            {
                KeyframeLoop loop = (KeyframeLoop) entry;
                float endTick = sheet.channel.getLoopEnd(loop);
                int start = loops.view.toGraphX(loop.start()), end = loops.view.toGraphX(endTick);
                if (end < loops.view.graphArea.x || start > loops.view.graphArea.ex()) continue;
                int sourceEnd = loops.view.toGraphX(loop.sourceEnd());
                boolean selected = loop.id().equals(loops.selectedId);
                boolean hover = hovered != null && hovered.sheet() == sheet && hovered.loop() == loop
                    && context.mouseY >= y && context.mouseY < y + height;
                int color = selected ? Colors.ACTIVE : sheet.color;
                context.batcher.box(start, y + 1, end, y + height - 1, Colors.setA(color, 0.12F));
                context.batcher.box(start, y + 1, end, y + 2, hover ? Colors.WHITE : Colors.setA(color, 0.9F));
                context.batcher.box(start, y + height - 2, end, y + height - 1, hover ? Colors.WHITE : Colors.setA(color, 0.7F));
                context.batcher.box(start, y + 1, start + 1, y + height - 1, hover ? Colors.WHITE : Colors.setA(color, 0.8F));
                context.batcher.box(sourceEnd, y + 3, sourceEnd + 1, y + height - 2, Colors.setA(color, 0.5F));
                context.batcher.box(end - 2, y + 1, end + 2, y + height - 1, hover ? Colors.WHITE : Colors.setA(color, 1F));

                renderGhosts(loops, context, sheet, loop, endTick, y + height / 2);
            }
        }
        context.batcher.unclip(context);
    }

    /** Drawn after the topmost keyframes, next to the pointer (BBS FS 3caff7848). */
    static void status(UIKeyframeLoops loops, UIContext context)
    {
        if (!loops.ziehtSchleife()) return;
        KeyframeLoop loop = loops.selectedChannel.getLoop(loops.selectedId);
        if (loop == null) return;
        String label = L10n.lang("bbs.ui.keyframes.loop.status").format(String.format(java.util.Locale.ROOT, "%.2f", loop.end()), String.format(java.util.Locale.ROOT, "%.2f", loop.passes())).get();
        int width = context.batcher.getFont().getWidth(label);
        int height = context.batcher.getFont().getHeight();
        int x = Math.max(loops.view.area.x + 4, Math.min(context.mouseX + 12, loops.view.area.ex() - width - 4));
        int y = context.mouseY + 12;
        if (y + height + 4 > loops.view.area.ey()) y = context.mouseY - height - 12;
        y = Math.max(loops.view.area.y + 4, y);
        context.batcher.textCard(label, x, y, Colors.WHITE, 0xE6181818);
    }

    private static void renderGhosts(UIKeyframeLoops loops, UIContext context, UIKeyframeSheet sheet, KeyframeLoop loop, float end, int rowY)
    {
        double period = loop.period();
        double firstTick = loops.view.fromGraphX(loops.view.graphArea.x - 6);
        double lastTick = Math.min(end, loops.view.fromGraphX(loops.view.graphArea.ex() + 6));
        double pixelPeriod = Math.abs(loops.view.toGraphX(loop.sourceEnd()) - loops.view.toGraphX(loop.start()));
        double stride = Math.max(1, Math.ceil(8 / Math.max(0.001, pixelPeriod)));
        Matrix4f matrix = new Matrix4f(context.batcher.pose().last().pose());
        int color = Colors.setA(sheet.color, 0.35F);

        /* Queued like UIKeyframeGraph's keyframe shapes; 26.3 has no immediate tessellator. */
        context.batcher.netz(FormPipelines.holen(PrimitiveTopology.QUADS, true, false, false), (builder) ->
        {
            int drawn = 0;

            for (Object entry : sheet.channel.getKeyframes())
            {
                Keyframe key = (Keyframe) entry;
                if (!loop.containsSource(key.getTick())) continue;
                double first = Math.max(1, Math.ceil((firstTick - key.getTick()) / period));
                int y = loops.view.getGraph() instanceof UIKeyframeGraph graph ? graph.toGraphY(key.getFactory().getY(key.getValue())) : rowY;
                for (double pass = first; drawn < 3000; pass += stride)
                {
                    double tick = key.getTick() + pass * period;
                    if (tick > lastTick) break;
                    if (tick <= loop.sourceEnd()) continue;
                    UIKeyframeDopeSheet.renderShape(key, context, builder, matrix, loops.view.toGraphX(tick), y, 3, key.isEnabled() ? color : Colors.mulRGB(color, 0.4F));
                    drawn++;
                }
            }
        }, UIKeyframeDopeSheet.flaeche(loops.view.graphArea));
    }
}
