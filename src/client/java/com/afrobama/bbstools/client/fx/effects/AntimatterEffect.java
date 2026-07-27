// Coded by Afrobama/Claude — ANTIMATTER IMPLOSION catastrophe.
// Second original effect (not in destroying-mc lineup). Visual: a
// point starts pulling pixels inward + darkening them. Everything
// outside is normal; inside the implosion radius, light bends
// toward the center and dims toward black.
//
// Physical-ish two-phase concept:
//   Phase 1 (implosion): t 0-80   antimatter pulls light in, darkens
//   Phase 2 (collapse): t 80-100  peak darkness, deep void
//   Phase 3 (release):  t 100     hand off to NukeEffect at the SAME
//                                  point so the antimatter explodes
//                                  outward — the visual inverse of
//                                  the prior phase.
//
// Camera shake builds during implosion (organic dread), peaks at the
// collapse moment, then NukeEffect's own shake takes over for the
// release blast.
package com.afrobama.bbstools.client.fx.effects;

import com.afrobama.bbstools.client.fracture.CameraShakeManager;
import com.afrobama.bbstools.client.fx.BBSFxState;
import com.afrobama.bbstools.diag.BBSDiag;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import org.joml.Vector3f;

public final class AntimatterEffect
{
    /* iter 62 timing rework — user said "der beginnt alles zu shaken und
     *  verschwindet einfach". Previous 80+20=100 ticks (5s) with a
     *  snap-to-0 at end felt abrupt. New 100+60+40 = 200 ticks (10s)
     *  with proper fade-out phase replacing the snap. */
    private static final int TICKS_IMPLODE   = 100;  /* 5s build */
    private static final int TICKS_COLLAPSE  = 160;  /* +3s sustain */
    private static final int TICKS_FADE_OUT  = 200;  /* +2s fade */

    private static int tick;
    private static boolean active;
    private static boolean registered;
    private static final Vector3f anchor = new Vector3f();

    private AntimatterEffect() {}

    public static synchronized void register()
    {
        if (registered) return;
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(mc -> AntimatterEffect.tick());
        BBSDiag.feature("fx-antimatter").event("register").emit();
    }

    public static void trigger(float x, float y, float z)
    {
        anchor.set(x, y, z);
        BBSFxState.antimatterPos = new Vector3f(anchor);
        BBSFxState.antimatterIntensity = 0F;
        tick = 0;
        active = true;
        BBSDiag.feature("fx-antimatter").event("trigger")
            .field("pos", "(" + x + "," + y + "," + z + ")").emit();
    }

    public static void cancel()
    {
        active = false;
        BBSFxState.antimatterIntensity = 0F;
        BBSDiag.feature("fx-antimatter").event("cancel").emit();
    }

    private static void tick()
    {
        if (!active) return;
        tick++;

        if (tick <= TICKS_IMPLODE)
        {
            float t = (float) tick / TICKS_IMPLODE;
            /* Ease-in cubic so the implosion accelerates as it builds. */
            BBSFxState.antimatterIntensity = t * t * t;
            /* Gathering dread — shake builds quadratically with intensity. */
            if (tick % 4 == 0)
            {
                CameraShakeManager.addShake(0.3F + 0.8F * t * t);
            }
        }
        else if (tick <= TICKS_COLLAPSE)
        {
            /* Hold at peak so the void is readable on screen. */
            BBSFxState.antimatterIntensity = 1F;
            if (tick % 2 == 0) CameraShakeManager.addShake(0.7F);
        }
        else if (tick <= TICKS_FADE_OUT)
        {
            /* Smooth fade-out phase. iter 62 user feedback: previous
             *  snap-to-0 ("verschwindet einfach") was abrupt. Ease-out
             *  curve here so the implosion gently releases without
             *  another effect hijacking. */
            float t = (float)(tick - TICKS_COLLAPSE) / (TICKS_FADE_OUT - TICKS_COLLAPSE);
            float eased = 1F - t * t;
            BBSFxState.antimatterIntensity = eased;
            /* Gentle residual rumble during fade. */
            if (tick % 6 == 0) CameraShakeManager.addShake(0.4F * eased);
        }
        else
        {
            BBSFxState.antimatterIntensity = 0F;
            active = false;
            BBSDiag.feature("fx-antimatter").event("done")
                .field("pos", "(" + anchor.x + "," + anchor.y + "," + anchor.z + ")")
                .emit();
        }

        if (tick % 20 == 0 && active)
        {
            BBSDiag.feature("fx-antimatter").event("tick")
                .field("t", tick).field("i", BBSFxState.antimatterIntensity).emit();
        }
    }

    public static boolean isActive() { return active; }

}
