// Coded by Afrobama/Claude — REALITY GLITCH catastrophe. An
// original world-ending effect not in destroying-mc's lineup
// (which has black-hole / nuke / supernova / orbital-laser /
// cracks). Visual concept: a "tear in reality" that eats outward
// from a world point, corrupting everything it touches (RGB
// chromatic separation, pixel-mosaic, tear lines, color invert,
// static noise). The corruption boundary wobbles organically
// because the shader modulates it with FBM-ish noise.
//
// Timing curve (~10 seconds @ 20 TPS = 200 ticks):
//   t  0-60   : grow phase — radius 0 → MAX (60b), intensity
//               ramps 0 → 1.0 quadratically. Camera shake builds.
//   t 60-120  : sustain — corruption holds at full size + intensity,
//               periodic stutter shake every 8 ticks.
//   t 120-180 : dissolve — radius shrinks, intensity fades to 0.
//   t 180+    : done; state reset.
package com.afrobama.bbstools.client.fx.effects;

import com.afrobama.bbstools.client.fracture.CameraShakeManager;
import com.afrobama.bbstools.client.fx.BBSFxState;
import com.afrobama.bbstools.diag.BBSDiag;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import org.joml.Vector3f;

public final class GlitchEffect
{
    /** Peak corruption radius in world blocks. */
    private static final float MAX_RADIUS = 60F;

    private static final int TICKS_GROW    =  60;
    private static final int TICKS_SUSTAIN = 120;
    private static final int TICKS_END     = 180;

    private static int tick;
    private static boolean active;
    private static boolean registered;

    private GlitchEffect() {}

    public static synchronized void register()
    {
        if (registered) return;
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(mc -> GlitchEffect.tick());
        BBSDiag.feature("fx-glitch").event("register").emit();
    }

    public static void trigger(float x, float y, float z)
    {
        BBSFxState.glitchPos = new Vector3f(x, y, z);
        BBSFxState.glitchRadius = 0F;
        BBSFxState.glitchIntensity = 0F;
        tick = 0;
        active = true;
        /* Initial shock-shake when the tear opens. */
        CameraShakeManager.addShake(1.5F);
        BBSDiag.feature("fx-glitch").event("trigger")
            .field("pos", "(" + x + "," + y + "," + z + ")").emit();
    }

    public static void cancel()
    {
        active = false;
        BBSFxState.glitchRadius = 0F;
        BBSFxState.glitchIntensity = 0F;
        BBSDiag.feature("fx-glitch").event("cancel").emit();
    }

    private static void tick()
    {
        if (!active) return;
        tick++;

        if (tick <= TICKS_GROW)
        {
            float t = (float) tick / TICKS_GROW;
            BBSFxState.glitchRadius    = MAX_RADIUS * t * t;
            BBSFxState.glitchIntensity = t;
            /* Continuous low-amplitude rumble during growth. */
            if (tick % 4 == 0) CameraShakeManager.addShake(0.4F * t);
        }
        else if (tick <= TICKS_SUSTAIN)
        {
            BBSFxState.glitchRadius    = MAX_RADIUS;
            BBSFxState.glitchIntensity = 1F;
            /* Periodic stutter during sustain — like the world is
             *  losing sync. */
            if (tick % 8 == 0) CameraShakeManager.addShake(0.5F);
        }
        else if (tick <= TICKS_END)
        {
            float t = (float)(tick - TICKS_SUSTAIN) / (TICKS_END - TICKS_SUSTAIN);
            BBSFxState.glitchRadius    = MAX_RADIUS * (1F - t);
            BBSFxState.glitchIntensity = 1F - t;
        }
        else
        {
            active = false;
            BBSFxState.glitchRadius = 0F;
            BBSFxState.glitchIntensity = 0F;
            BBSDiag.feature("fx-glitch").event("done").emit();
        }

        if (tick % 20 == 0 && active)
        {
            BBSDiag.feature("fx-glitch").event("tick")
                .field("t", tick)
                .field("r", BBSFxState.glitchRadius)
                .field("i", BBSFxState.glitchIntensity)
                .emit();
        }
    }

    public static boolean isActive() { return active; }

}
