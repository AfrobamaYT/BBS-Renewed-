// Coded by Afrobama/Claude — CRYSTALLIZATION WAVE catastrophe.
// Third original effect (not in destroying-mc lineup). Visual: a
// crystal nucleus spawns at a world point, then a sphere of
// crystallization expands outward — turning the interior into a
// faceted icy-blue glassy surface with sparkle. A bright cyan-white
// refractive ring rides the leading edge as light bends through
// the crystal boundary.
//
// Boundary ring technique inspired by SpacePotato's vhs_post.fsh
// chromatic-separation pattern (research turned up via the user
// sharing their GitHub profile this session).
//
// Timing curve (~12 seconds @ 20 TPS = 240 ticks):
//   t   0-60   : grow phase — radius 0 → MAX (50b), intensity 0 → 1.
//                 Boundary-passage shake pulses as the leading edge
//                 sweeps past common viewing distances.
//   t  60-180  : sustain — full crystal, periodic stutter shake.
//   t 180-240  : dissolve — radius shrinks, intensity fades to 0.
package com.afrobama.bbstools.client.fx.effects;

import com.afrobama.bbstools.client.fracture.CameraShakeManager;
import com.afrobama.bbstools.client.fx.BBSFxState;
import com.afrobama.bbstools.diag.BBSDiag;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import org.joml.Vector3f;

public final class CrystalEffect
{
    private static final float MAX_RADIUS  = 50F;
    private static final int TICKS_GROW    =  60;
    private static final int TICKS_SUSTAIN = 180;
    private static final int TICKS_END     = 240;

    private static int tick;
    private static boolean active;
    private static boolean registered;

    private CrystalEffect() {}

    public static synchronized void register()
    {
        if (registered) return;
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(mc -> CrystalEffect.tick());
        BBSDiag.feature("fx-crystal").event("register").emit();
    }

    public static void trigger(float x, float y, float z)
    {
        BBSFxState.crystalPos = new Vector3f(x, y, z);
        BBSFxState.crystalRadius = 0F;
        BBSFxState.crystalIntensity = 0F;
        tick = 0;
        active = true;
        /* Crisp pulse at nucleation. */
        CameraShakeManager.addShake(1.0F);
        BBSDiag.feature("fx-crystal").event("trigger")
            .field("pos", "(" + x + "," + y + "," + z + ")").emit();
    }

    public static void cancel()
    {
        active = false;
        BBSFxState.crystalRadius = 0F;
        BBSFxState.crystalIntensity = 0F;
        BBSDiag.feature("fx-crystal").event("cancel").emit();
    }

    private static void tick()
    {
        if (!active) return;
        tick++;

        if (tick <= TICKS_GROW)
        {
            float t = (float) tick / TICKS_GROW;
            /* Linear radial growth — wave-front advances at constant
             *  speed for a uniform "wash" feel. */
            BBSFxState.crystalRadius    = MAX_RADIUS * t;
            BBSFxState.crystalIntensity = t;
            /* Continuous ringing as the wave-front expands. */
            if (tick % 5 == 0) CameraShakeManager.addShake(0.3F);
        }
        else if (tick <= TICKS_SUSTAIN)
        {
            BBSFxState.crystalRadius    = MAX_RADIUS;
            BBSFxState.crystalIntensity = 1F;
            /* Periodic chime-like pulses while crystallized. */
            if (tick % 16 == 0) CameraShakeManager.addShake(0.4F);
        }
        else if (tick <= TICKS_END)
        {
            float t = (float)(tick - TICKS_SUSTAIN) / (TICKS_END - TICKS_SUSTAIN);
            /* Dissolve fades intensity faster than radius — boundary
             *  shrinks while the inside becomes less saturated. */
            BBSFxState.crystalRadius    = MAX_RADIUS * (1F - t);
            BBSFxState.crystalIntensity = 1F - t;
        }
        else
        {
            active = false;
            BBSFxState.crystalRadius = 0F;
            BBSFxState.crystalIntensity = 0F;
            BBSDiag.feature("fx-crystal").event("done").emit();
        }

        if (tick % 20 == 0 && active)
        {
            BBSDiag.feature("fx-crystal").event("tick")
                .field("t", tick).field("r", BBSFxState.crystalRadius)
                .field("i", BBSFxState.crystalIntensity).emit();
        }
    }

    public static boolean isActive() { return active; }

}
