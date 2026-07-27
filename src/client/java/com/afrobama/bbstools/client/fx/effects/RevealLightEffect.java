// Coded by Afrobama/Claude — REVEAL LIGHT. User-requested for
// cinematic reveal-scenes:
//
//   "ein gate aus blöcken öffnet sich und man sieht nicht was da
//    hinter ist weil da ein helles licht durch das gate leuchtet
//    und wenn man weitergeht sieht man erst was auf der anderen
//    seite ist"
//
// A sustained HDR-bright spot at a world position, fade-in over
// ~1s, then HELD at full intensity until the user manually
// /bbstools fx reveal-light-off (or /bbstools fx cancel). After
// cancel, fade-out over ~1s.
//
// Implementation reuses the existing shadows.fsh supernova HDR-emit
// path (writes vec3(2.0, 1.8, 1.5) * intensity³ at supernovaPos with
// distance-based exp falloff) but drives the timer manually instead
// of with an automatic curve. Bloom catches the >1.0 luminance and
// gives it a halo, which is exactly the "Tor mit hellem Licht
// dahinter" cinematic look the user described.
package com.afrobama.bbstools.client.fx.effects;

import com.afrobama.bbstools.client.fx.BBSFxState;
import com.afrobama.bbstools.diag.BBSDiag;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import org.joml.Vector3f;

public final class RevealLightEffect
{
    private static final int FADE_IN_TICKS  = 20;   /* 1s */
    private static final int FADE_OUT_TICKS = 20;   /* 1s */
    private static final float TARGET_INTENSITY = 0.6F;  /* sustained level */

    private static int phase;          /* 0 idle, 1 fading-in, 2 sustained, 3 fading-out */
    private static int phaseTick;
    private static boolean registered;

    private RevealLightEffect() {}

    public static synchronized void register()
    {
        if (registered) return;
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(mc -> RevealLightEffect.tick());
        BBSDiag.feature("fx-reveal-light").event("register").emit();
    }

    public static void start(float x, float y, float z)
    {
        BBSFxState.revealLightPos = new Vector3f(x, y, z);
        phase = 1;
        phaseTick = 0;
        BBSFxState.revealLightIntensity = 0F;
        BBSDiag.feature("fx-reveal-light").event("start")
            .field("pos", "(" + x + "," + y + "," + z + ")").emit();
    }

    /** User-triggered fade-out (separate from a hard cancel). */
    public static void stop()
    {
        if (phase == 0 || phase == 3) return;
        phase = 3;
        phaseTick = 0;
        BBSDiag.feature("fx-reveal-light").event("stop-fade").emit();
    }

    /** Hard cancel — no fade-out, snap to 0. */
    public static void cancel()
    {
        phase = 0;
        phaseTick = 0;
        BBSFxState.revealLightIntensity = 0F;
        BBSDiag.feature("fx-reveal-light").event("cancel").emit();
    }

    private static void tick()
    {
        if (phase == 0) return;
        phaseTick++;

        if (phase == 1)
        {
            float t = (float) phaseTick / FADE_IN_TICKS;
            BBSFxState.revealLightIntensity = TARGET_INTENSITY * t;
            if (phaseTick >= FADE_IN_TICKS)
            {
                phase = 2;
                phaseTick = 0;
                BBSFxState.revealLightIntensity = TARGET_INTENSITY;
            }
        }
        else if (phase == 2)
        {
            /* Sustained — held until stop()/cancel(). */
            BBSFxState.revealLightIntensity = TARGET_INTENSITY;
        }
        else if (phase == 3)
        {
            float t = (float) phaseTick / FADE_OUT_TICKS;
            BBSFxState.revealLightIntensity = TARGET_INTENSITY * (1F - t);
            if (phaseTick >= FADE_OUT_TICKS)
            {
                phase = 0;
                BBSFxState.revealLightIntensity = 0F;
                BBSDiag.feature("fx-reveal-light").event("done").emit();
            }
        }
    }

    public static boolean isActive() { return phase != 0; }

}
