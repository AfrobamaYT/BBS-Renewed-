// Coded by Afrobama/Claude — SUPERNOVA test effect for the new
// BBSTools FX pipeline. Visual reference: SN 1987A composite +
// Crab Nebula expanding shell, plus white-hot peak flash.
//
// Animation curve (90 ticks total = 4.5 s @ 20 TPS):
//   t 0-10  (0.0 → 0.3): build-up, faint glow at center
//   t 10-30 (0.3 → 1.0): explosive peak, white-out
//   t 30-90 (1.0 → 0.0): long decay, shell expansion
//
// Driven by BBSFxState.supernovaTimer + supernovaPos. The actual
// visual comes from the final.fsh shader reading those uniforms
// and overlaying a smooth radial whitening centered on supernovaPos.
package com.afrobama.bbstools.client.fx.effects;

import com.afrobama.bbstools.client.fracture.CameraShakeManager;
import com.afrobama.bbstools.client.fx.BBSFxState;
import com.afrobama.bbstools.diag.BBSDiag;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import org.joml.Vector3f;

public final class SupernovaEffect
{
    private static final int TICKS_BUILDUP = 10;
    private static final int TICKS_PEAK    = 30;
    private static final int TICKS_DECAY   = 90;

    private static int tick;
    private static boolean active;
    private static boolean registered;

    private SupernovaEffect() {}

    public static synchronized void register()
    {
        if (registered) return;
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(mc -> SupernovaEffect.tick());
        BBSDiag.feature("fx-supernova").event("register").emit();
    }

    /** Trigger a supernova at given world position. */
    public static void trigger(float x, float y, float z)
    {
        BBSFxState.supernovaPos = new Vector3f(x, y, z);
        tick = 0;
        active = true;
        BBSFxState.supernovaTimer = 0F;
        BBSDiag.feature("fx-supernova").event("trigger")
            .field("pos", "(" + x + "," + y + "," + z + ")")
            .emit();
    }

    public static void cancel()
    {
        active = false;
        BBSFxState.supernovaTimer = 0F;
        BBSFxState.flashTimer = 0F;
        tick = 0;
        BBSDiag.feature("fx-supernova").event("cancel").emit();
    }

    private static void tick()
    {
        if (!active) return;
        tick++;
        if (tick <= TICKS_BUILDUP)
        {
            float t = (float) tick / TICKS_BUILDUP;
            BBSFxState.supernovaTimer = t * 0.3F;
            /* Faint rumble during the build-up gathers tension before
             *  the burst. Small adds well below the shake-manager
             *  threshold floor wouldn't trigger anyway, so use 0.4
             *  per tick — visible micro-tremor but not motion-sick. */
            if (tick % 2 == 0) CameraShakeManager.addShake(0.4F);
        }
        else if (tick <= TICKS_PEAK)
        {
            float t = (float)(tick - TICKS_BUILDUP) / (TICKS_PEAK - TICKS_BUILDUP);
            BBSFxState.supernovaTimer = 0.3F + t * 0.7F;
            if (tick == TICKS_PEAK)
            {
                /* Bright flash overlay at peak */
                BBSFxState.flashTimer = 1.0F;
                /* Max shake at peak — same as nuke detonation. */
                CameraShakeManager.addShake(2.5F);
            }
        }
        else if (tick <= TICKS_DECAY)
        {
            float t = (float)(tick - TICKS_PEAK) / (TICKS_DECAY - TICKS_PEAK);
            BBSFxState.supernovaTimer = 1.0F - t;
            BBSFxState.flashTimer = Math.max(0F, BBSFxState.flashTimer - 0.05F);
            /* Aftershock taper. */
            if (tick % 8 == 0) CameraShakeManager.addShake(0.5F * (1F - t));
        }
        else
        {
            active = false;
            BBSFxState.supernovaTimer = 0F;
            BBSFxState.flashTimer = 0F;
            BBSDiag.feature("fx-supernova").event("done").emit();
        }

        if (tick % 10 == 0)
        {
            BBSDiag.feature("fx-supernova").event("tick")
                .field("t", tick)
                .field("timer", BBSFxState.supernovaTimer)
                .field("flash", BBSFxState.flashTimer)
                .emit();
        }
    }

    public static boolean isActive() { return active; }
    public static int getTick() { return tick; }

}
