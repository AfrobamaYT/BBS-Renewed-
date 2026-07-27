/* Coded by Afrobama/Claude — Army System: replay-menu cache-progress bar.
 *
 * DaVinci-style render-cache indicator, shown INSIDE the replay properties
 * panel (UIReplaysOverlayPanel) — not floating over the world. A thin
 * horizontal bar: dark track + green fill = fraction of the selected
 * replay's animation that is baked into AnimationBakeCache. Fills as the
 * film plays AND (when ENABLE_BG_BAKE) while paused via the background baker.
 *
 * Progress is read by replay UUID from AnimationBakeCache.publishProgress,
 * which the render-loop bypass writes each frame — so this element needs no
 * handle to the film controller, just the panel's current selection.
 *
 * Multi-select: aggregates cached/expected across all selected replays
 * (overall % = total cached ticks / total expected ticks).
 */
package com.afrobama.bbstools.client.ui;

import com.afrobama.bbstools.client.bbs.fastpath.AnimationBakeCache;
import com.afrobama.bbstools.perf.BBSPerfConfig;

import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.ui.film.replays.overlays.UIReplaysOverlayPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.List;

public class ReplayCacheBar extends UIElement
{
    /* Thin DaVinci-style bar: full-width RED track = live (un-baked) frames,
     * GREEN fill grows left→right covering the red as ticks cache. */
    private static final int RED   = 0xFFD23C3C;
    private static final int GREEN = 0xFF3CDC5A;
    private static final int GREY  = 0xFF555555;
    private static final int TEXT  = Colors.WHITE;

    private static final int BAR_H = 4;

    private final UIReplaysOverlayPanel panel;

    public ReplayCacheBar(UIReplaysOverlayPanel panel)
    {
        this.panel = panel;
        this.h(16);
        this.tooltip(mchorse.bbs_mod.l10n.keys.IKey.constant(
            "Animation cache: green = baked frames, red = still live. " +
            "Fills as the film plays; with background-bake on it also fills while paused."));
    }

    @Override
    public void render(UIContext context)
    {
        int x = this.area.x;
        int y = this.area.y;
        int w = this.area.w;

        long cached = 0L;
        long expected = 0L;
        int withData = 0;
        int total = 0;

        try
        {
            List<Replay> selected = this.panel.replays.getSelectedReplays();
            if (selected != null)
            {
                for (Replay r : selected)
                {
                    if (r == null || r.isGroup.get()) continue;
                    total++;
                    int[] p = AnimationBakeCache.getProgress(r.uuid.get());
                    if (p != null && p[1] > 0)
                    {
                        cached += p[0];
                        expected += p[1];
                        withData++;
                    }
                }
            }
        }
        catch (Throwable ignored) {}

        float frac = expected > 0L ? (float) cached / (float) expected : 0F;
        if (frac < 0F) frac = 0F;
        if (frac > 1F) frac = 1F;

        boolean off = !BBSPerfConfig.ENABLE_ARMY_BAKE;

        /* Label line. */
        FontRenderer font = context.batcher.getFont();
        String label;
        if (total == 0)        label = "Cache —";
        else if (off)          label = "Cache off";
        else if (withData == 0) label = "Cache 0% (play or pause to bake)";
        else
        {
            label = "Cache " + Math.round(frac * 100F) + "%";
            if (total > 1) label += "  (" + withData + "/" + total + ")";
        }
        context.batcher.text(label, x, y, TEXT, true);

        /* Thin bar below the label. */
        int barY = y + font.getHeight() + 1;
        if (off || total == 0)
        {
            context.batcher.box(x, barY, x + w, barY + BAR_H, GREY);
        }
        else
        {
            context.batcher.box(x, barY, x + w, barY + BAR_H, RED);
            int fillW = (int) (w * frac);
            if (fillW > 0)
            {
                context.batcher.box(x, barY, x + fillW, barY + BAR_H, GREEN);
            }
        }

        super.render(context);
    }

}
