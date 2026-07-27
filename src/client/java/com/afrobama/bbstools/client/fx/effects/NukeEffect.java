// Coded by Afrobama/Claude — NUKE catastrophe effect. Drives the
// mushroom-cloud shader (bbstools:fx/nuke) by ramping smokeRiseTimer
// 0 -> 1 over ~120 ticks (6s @ 20 TPS) and flashing the screen at
// detonation.
//
// Timing (destroying-mc-style):
//   t 0      : flashTimer set to 0.01 (instant peak — see note on
//              destroying-mc's inverted convention in nuke.fsh).
//   t 0-5    : flashTimer ramps 0.01 -> 1.0 (flash decays out).
//   t 0-120  : smokeRiseTimer ramps 0 -> 1.0 (cloud grows).
//   t 120-180: smokeRiseTimer sustains at 1.0 (cloud fully bloomed).
//   t >180   : effect done — both timers reset to 0.
package com.afrobama.bbstools.client.fx.effects;

import com.afrobama.bbstools.client.fracture.CameraShakeManager;
import com.afrobama.bbstools.client.fx.BBSFxState;
import com.afrobama.bbstools.diag.BBSDiag;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import org.joml.Vector3f;

public final class NukeEffect
{
    private static final int FLASH_RAMP_END   = 5;
    private static final int SMOKE_RISE_END   = 120;
    private static final int SUSTAIN_END      = 180;

    private static int tick;
    private static boolean active;
    private static boolean registered;

    private NukeEffect() {}

    public static synchronized void register()
    {
        if (registered) return;
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(mc -> NukeEffect.tick());
        BBSDiag.feature("fx-nuke").event("register").emit();
    }

    public static void trigger(float x, float y, float z)
    {
        BBSFxState.nukePos = new Vector3f(x, y, z);
        tick = 0;
        active = true;
        /* destroying-mc convention: flashTimer near 0 = peak flash.
         *  Set to 0.01 so the shader sees ~max flash on frame 0. */
        BBSFxState.flashTimer = 0.01F;
        BBSFxState.smokeRiseTimer = 0F;
        /* Maximum shake on detonation — 2.5 * 2.5 = 6.25 deg which the
         *  shake manager caps to MAX_AMPLITUDE_DEG=6, so we're at peak
         *  amplitude immediately. */
        CameraShakeManager.addShake(2.5F);
        BBSDiag.feature("fx-nuke").event("trigger")
            .field("pos", "(" + x + "," + y + "," + z + ")").emit();
    }

    public static void cancel()
    {
        active = false;
        BBSFxState.flashTimer = 0F;
        BBSFxState.smokeRiseTimer = 0F;
        BBSDiag.feature("fx-nuke").event("cancel").emit();
    }

    private static void tick()
    {
        if (!active) return;
        tick++;

        if (tick <= FLASH_RAMP_END)
        {
            /* Flash decay: 0.01 -> 1.0 (visually peak -> dark). */
            float t = (float) tick / FLASH_RAMP_END;
            BBSFxState.flashTimer = 0.01F + t * 0.99F;
        }
        else
        {
            BBSFxState.flashTimer = 1.0F;  /* >=1 means no flash overlay */
        }

        if (tick <= SMOKE_RISE_END)
        {
            float t = (float) tick / SMOKE_RISE_END;
            /* Ease-out: cloud rises fast then slows as it reaches full
             *  height. Matches the real-world physical curve where a
             *  mushroom cloud lifts violently then mushrooms slowly. */
            BBSFxState.smokeRiseTimer = 1F - (1F - t) * (1F - t);
            /* Sustained ground tremor every 10 ticks while the cloud
             *  rises. Intensity tapers as the cloud fully forms so the
             *  shake doesn't drag on past the visual peak. */
            if (tick % 10 == 0)
            {
                CameraShakeManager.addShake(0.6F * (1F - t));
            }
        }
        else if (tick <= SUSTAIN_END)
        {
            BBSFxState.smokeRiseTimer = 1F;
        }
        else
        {
            active = false;
            BBSFxState.smokeRiseTimer = 0F;
            BBSFxState.flashTimer = 0F;
            BBSDiag.feature("fx-nuke").event("done").emit();
        }

        if (tick % 20 == 0)
        {
            BBSDiag.feature("fx-nuke").event("tick")
                .field("t", tick)
                .field("smoke", BBSFxState.smokeRiseTimer)
                .field("flash", BBSFxState.flashTimer)
                .emit();
        }
    }

    public static boolean isActive() { return active; }

}
