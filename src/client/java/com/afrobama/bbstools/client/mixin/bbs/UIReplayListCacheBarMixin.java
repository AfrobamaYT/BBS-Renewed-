// Coded by Afrobama/Claude — Army System: per-replay cache bar in the LEFT list.
//
// Draws a thin DaVinci-style line at the bottom of every replay row in the
// replays list: full-width RED track = live (un-baked) frames, GREEN fill
// grows left→right covering the red as ticks cache. Reads progress LIVE from
// AnimationBakeCache via the editor's film controller (replay → entity →
// cachedCount) — no published snapshot, so it updates whether the frame went
// through the render-loop bypass or the normal fast path.
//
// Row height is the UIList default (Scroll(area, 20)); the form thumbnail
// occupies the right ~30px, so the bar stops short of it.
package com.afrobama.bbstools.client.mixin.bbs;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import com.afrobama.bbstools.perf.BBSPerfConfig;

import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.ui.film.replays.ReplayListEntry;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.replays.UIReplayList;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.invoke.MethodHandle;

@Mixin(value = UIReplayList.class, remap = false)
public abstract class UIReplayListCacheBarMixin
{
    @Shadow public UIFilmPanel panel;

    /* BRÜCKEN-MUSTER, PRO-ENTITY-VARIANTE (2026-07-26) — siehe ModelShadowTimingMixin für die
     * volle begründung. Griff pro frame, nie über frame-grenzen gecacht. */
    private static long bbstools$griffFrameStamp = Long.MIN_VALUE;
    private static MethodHandle bbstools$drawGriff;

    @Inject(method = "renderElementPart", at = @At("TAIL"), remap = false)
    /* AUTONOMIE 2026-07-27: fs' UIReplayList haelt ReplayListEntry (replay ODER ordner),
     * CMLs hielt direkt Replay. Der eintrag kann jetzt ein ordner sein — dann ist .replay
     * null und die logik ueberspringt ihn. */
    private void bbstools$cacheBar(UIContext context, ReplayListEntry element, int i, int x, int y,
                                   boolean hover, boolean selected, CallbackInfo ci)
    {
        bbstools$refreshGriff();

        /* IMPORTANT: the x/y PARAMS are MUTATED before this TAIL inject — BBS's
         * renderElementPart does `x += area.w - 30` and `y -= 10` to draw the form thumbnail
         * (UIReplayList.java:2570/2574). Recompute the row's true geometry from area + scroll +
         * index instead (BBS's own row formula, UIList.renderElement:671). */
        UIElement self = (UIElement) (Object) this;
        UIList<?> list = (UIList<?>) (Object) this;

        try
        {
            if (bbstools$drawGriff != null)
            {
                /* AUTONOMIE 2026-07-27: die logik erwartet einen Replay; ein ordner-eintrag
                 * traegt keinen (null) und wird von ihr uebersprungen. */
                bbstools$drawGriff.invoke(this.panel, context, element == null ? null : element.replay, i,
                    self.area.x, self.area.y, self.area.w,
                    list.scroll.scrollItemSize, list.scroll.getScroll());
            }
        }
        catch (Throwable ignored) { /* siehe MixinBruecke-javadoc: fehler = frame auslassen */ }
    }

    /** Holt den griff neu, sobald ein neuer render-frame begonnen hat. */
    private static void bbstools$refreshGriff()
    {
        long stamp = BBSPerfConfig.bbsRenderHeadNs;

        if (stamp != bbstools$griffFrameStamp)
        {
            bbstools$griffFrameStamp = stamp;
            bbstools$drawGriff = MixinBruecke.griff("UIReplayListCacheBarLogik", "draw", 9);
        }
    }

}
