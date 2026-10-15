// Coded by Afrobama/Claude — METEOR catastrophe. Animates meteorPos
// from a high sky position down to a configurable impact point over
// ~50 ticks (2.5s). On impact: hands off to NukeEffect for the
// on-ground flash + mushroom + sustained tremor.
//
// Sky tracer = HDR-bright cool-white spot emitted from shadows.fsh
// at meteorPos with a tight 5-block exp-falloff. Bloom picks it up
// as a soft halo, so the user sees a small glowing object descending
// before the world ends.
package com.afrobama.bbstools.client.fx.effects;

import com.afrobama.bbstools.client.fracture.CameraShakeManager;
import com.afrobama.bbstools.client.fx.BBSFxState;
import com.afrobama.bbstools.diag.BBSDiag;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import org.joml.Vector3f;

public final class MeteorEffect
{
    /** Tracer flight duration in ticks (50 = 2.5s @ 20 TPS). */
    private static final int TICKS_FLIGHT = 50;

    /** Sky spawn height above the impact point, world blocks. */
    private static final float SPAWN_HEIGHT = 220F;

    /** Horizontal offset of the spawn from the impact point. Gives
     *  the tracer a visible angled trajectory rather than dropping
     *  straight down (which would only be a single bright dot from
     *  the player's view if they're at ground level). */
    private static final float SPAWN_HORIZONTAL = 180F;

    private static int tick;
    private static boolean active;
    private static boolean registered;
    private static final Vector3f spawn  = new Vector3f();
    private static final Vector3f impact = new Vector3f();

    private MeteorEffect() {}

    public static synchronized void register()
    {
        if (registered) return;
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(mc -> MeteorEffect.tick());
        BBSDiag.feature("fx-meteor").event("register").emit();
    }

    public static void trigger(float x, float y, float z)
    {
        impact.set(x, y, z);
        spawn.set(x + SPAWN_HORIZONTAL, y + SPAWN_HEIGHT, z + SPAWN_HORIZONTAL * 0.5F);
        BBSFxState.meteorPos = new Vector3f(spawn);
        BBSFxState.meteorIntensity = 0.3F;
        tick = 0;
        active = true;
        BBSDiag.feature("fx-meteor").event("trigger")
            .field("impact", "(" + x + "," + y + "," + z + ")")
            .field("spawn", "(" + spawn.x + "," + spawn.y + "," + spawn.z + ")")
            .emit();
    }

    public static void cancel()
    {
        active = false;
        BBSFxState.meteorIntensity = 0F;
        BBSDiag.feature("fx-meteor").event("cancel").emit();
    }

    private static void tick()
    {
        if (!active) return;
        tick++;

        if (tick <= TICKS_FLIGHT)
        {
            /* Linear lerp from spawn to impact. The y-axis lerps too
             *  so the meteor visibly descends through the air column. */
            float t = (float) tick / TICKS_FLIGHT;
            float x = spawn.x + (impact.x - spawn.x) * t;
            float y = spawn.y + (impact.y - spawn.y) * t;
            float z = spawn.z + (impact.z - spawn.z) * t;
            BBSFxState.meteorPos = new Vector3f(x, y, z);
            /* Intensity ramps quadratically — barely visible at first,
             *  growing bright as it nears the ground. */
            BBSFxState.meteorIntensity = 0.3F + 0.6F * t * t;
            /* Anticipation tremor in the last ~1 second before impact. */
            if (t > 0.6F && tick % 4 == 0)
            {
                CameraShakeManager.addShake(0.3F * (t - 0.6F) * 2.5F);
            }
        }
        else
        {
            /* Impact: hand off to NukeEffect at the impact point.
             *  NukeEffect handles the flash, mushroom cloud, and
             *  sustained tremor. */
            BBSFxState.meteorIntensity = 0F;
            NukeEffect.trigger(impact.x, impact.y, impact.z);
            active = false;
            BBSDiag.feature("fx-meteor").event("impact")
                .field("at", "(" + impact.x + "," + impact.y + "," + impact.z + ")")
                .emit();
        }

        if (tick % 10 == 0 && active)
        {
            BBSDiag.feature("fx-meteor").event("tick")
                .field("t", tick)
                .field("intensity", BBSFxState.meteorIntensity)
                .emit();
        }
    }

    public static boolean isActive() { return active; }

}
