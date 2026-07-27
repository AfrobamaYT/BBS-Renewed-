package com.afrobama.blendertransform;

import com.afrobama.bbstools.client.blender.MixinBruecke;

/**
 * Logik des stencil-pick-throttles — im kernel-paket, damit sie hotswapbar ist.
 *
 * <p>Herausgelöst aus {@code UIFilmControllerStencilThrottleMixin} (2026-07-26). BBS'
 * {@code UIFilmController.renderStencil} läuft JEDEN frame, in dem die maus über dem
 * 3D-viewport steht, und macht dabei einen {@code glReadPixels}-GPU→CPU-sync-stall. Das
 * pick-ergebnis kann sich nur ändern, wenn sich die maus bewegt — steht sie still (und kein
 * alt-wechsel, und die 1s-sicherheitsfrist ist nicht um), wird der re-run übersprungen und
 * das letzte pick weiterverwendet.</p>
 *
 * <p>⚠ ZUSTAND: die vier {@code bbstools$...}-felder sind {@code @Unique}-felder der
 * ZIELKLASSE ({@code UIFilmController}) — echte instanzfelder nach dem weben. Gelesen/
 * geschrieben über {@link MixinBruecke#feldLesen}/{@link MixinBruecke#feldSchreiben} auf der
 * ziel-instanz, NICHT in einer kernel-globalen map (mehrere film-controller-instanzen
 * möglich, z.B. mehrere geöffnete filme).</p>
 *
 * <p>⚠ KEINE KERNEL-TYPEN IN DER SIGNATUR. {@code context} ist ein BBS-typ, {@code aktiv} ein
 * primitiver flag-wert aus {@code BBSPerfConfig}.</p>
 */
public final class UIFilmControllerStencilThrottleLogik
{
    private UIFilmControllerStencilThrottleLogik() {}

    /**
     * @return {@code true}, wenn der aufrufende handler {@code ci.cancel()} rufen soll
     *         (cursor stand still, letztes pick bleibt gültig).
     */
    public static boolean throttlePick(Object zielObjekt, mchorse.bbs_mod.ui.framework.UIContext context,
                                       boolean altPressed)
    {
        if (!com.afrobama.bbstools.perf.BBSPerfConfig.ENABLE_STENCIL_PICK_THROTTLE) return false;
        if (context == null) return false;

        int mx = context.mouseX;
        int my = context.mouseY;
        long now = System.currentTimeMillis();

        Object lastXo = MixinBruecke.feldLesen(zielObjekt, "bbstools$lastMouseX");
        Object lastYo = MixinBruecke.feldLesen(zielObjekt, "bbstools$lastMouseY");
        Object lastAltO = MixinBruecke.feldLesen(zielObjekt, "bbstools$lastAlt");
        Object lastRunO = MixinBruecke.feldLesen(zielObjekt, "bbstools$lastRunMs");

        int lastX = lastXo instanceof Integer ? (Integer) lastXo : Integer.MIN_VALUE;
        int lastY = lastYo instanceof Integer ? (Integer) lastYo : Integer.MIN_VALUE;
        boolean lastAlt = lastAltO instanceof Boolean && (Boolean) lastAltO;
        long lastRunMs = lastRunO instanceof Long ? (Long) lastRunO : 0L;

        boolean moved = mx != lastX || my != lastY;
        boolean altChanged = altPressed != lastAlt;

        if (!moved && !altChanged && (now - lastRunMs) < 1000L)
        {
            /* Cursor stationär → letztes pick bleibt gültig → stencil-render + glReadPixels-
             * stall überspringen. */
            com.afrobama.bbstools.perf.BBSPerfConfig.stencilPickSkips++;
            return true;
        }

        MixinBruecke.feldSchreiben(zielObjekt, "bbstools$lastMouseX", mx);
        MixinBruecke.feldSchreiben(zielObjekt, "bbstools$lastMouseY", my);
        MixinBruecke.feldSchreiben(zielObjekt, "bbstools$lastAlt", altPressed);
        MixinBruecke.feldSchreiben(zielObjekt, "bbstools$lastRunMs", now);

        return false;
    }
}
