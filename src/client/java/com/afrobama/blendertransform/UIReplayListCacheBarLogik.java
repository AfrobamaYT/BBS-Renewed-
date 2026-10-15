package com.afrobama.blendertransform;

import com.afrobama.bbstools.client.bbs.fastpath.AnimationBakeCache;
import com.afrobama.bbstools.client.bbs.fastpath.ReplayBakeUtil;
import com.afrobama.bbstools.perf.BBSPerfConfig;

import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.controller.UIFilmController;
import mchorse.bbs_mod.ui.framework.UIContext;

/**
 * Logik des cache-bar-mixins — herausgelöst aus {@code UIReplayListCacheBarMixin} (2026-07-26),
 * damit sie hotswapbar ist.
 *
 * <p>PRO-SICHTBARER-ZEILE-PRO-FRAME-PFAD (siehe klassen-header des mixins). Zeichnet den
 * DaVinci-artigen bake-fortschrittsbalken unter jeder replay-zeile. Alle typen hier sind
 * BBS/JDK-typen (kein kernel-typ) — {@code panel} kommt als {@code @Shadow}-wert des aufrufers
 * herein, {@code self}/{@code list} als das gecastete {@code this}.</p>
 */
public final class UIReplayListCacheBarLogik
{
    private UIReplayListCacheBarLogik() {}

    private static final int BAR_H = 1;
    private static final int RED   = 0xFFD23C3C;
    private static final int GREEN = 0xFF3CDC5A;

    /**
     * @param panel        {@code @Shadow this.panel}
     * @param context      render-context
     * @param element      die replay-zeile
     * @param i            absoluter listen-index (BBS liefert ihn korrekt — kein indexOf nötig)
     * @param areaX        {@code self.area.x}
     * @param areaY        {@code self.area.y}
     * @param areaW        {@code self.area.w}
     * @param rowH         {@code list.scroll.scrollItemSize}
     * @param scroll       {@code list.scroll.getScroll()}
     */
    public static void draw(UIFilmPanel panel, UIContext context, Replay element, int i,
                             int areaX, int areaY, int areaW, int rowH, double scroll)
    {
        if (!BBSPerfConfig.ENABLE_ARMY_BAKE) return;
        if (element == null || element.isGroup.get() || !element.enabled.get()) return;
        if (panel == null) return;

        try
        {
            UIFilmController controller = panel.getController();
            Film film = panel.getData();
            if (controller == null || film == null) return;

            IEntity entity = controller.getEntities().get(i);
            if (entity == null) return;

            int cached = AnimationBakeCache.cachedCount(entity);
            int expected = ReplayBakeUtil.expectedTicks(element, film.camera.calculateDuration());

            float frac = expected > 0 ? (float) cached / (float) expected : 0F;
            if (frac < 0F) frac = 0F;
            if (frac > 1F) frac = 1F;

            int rowY = areaY + i * rowH - (int) scroll;
            int barY = rowY + rowH - BAR_H;

            int x0 = areaX;
            int x1 = areaX + areaW;

            context.batcher.box(x0, barY, x1, barY + BAR_H, RED);
            int fillW = (int) ((x1 - x0) * frac);
            if (fillW > 0)
            {
                context.batcher.box(x0, barY, x0 + fillW, barY + BAR_H, GREEN);
            }
        }
        catch (Throwable ignored) {}
    }
}
