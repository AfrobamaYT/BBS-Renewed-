// Coded by Afrobama/Claude — one-keystroke showcase of all FX
// catastrophes. Designed for two use-cases: (1) the user wants
// B-roll footage of all the effects in sequence without manually
// retyping commands, (2) a quick sanity-check that everything
// still works after an MC restart.
//
// Sequence (~24 seconds total):
//   t  0s : enable pipelines (if not already)
//   t  1s : trigger meteor strike at player pos
//   t  4s : meteor impacts → NukeEffect takes over
//   t 12s : (nuke smoke is sustaining) → trigger supernova above
//   t 20s : supernova completes naturally
//   t 24s : done — auto-cancel any lingering effects
package com.afrobama.bbstools.client.fx.effects;

import com.afrobama.bbstools.client.fx.BBSFxPipelineRegistrar;
import com.afrobama.bbstools.diag.BBSDiag;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

public final class DemoSequencer
{
    private static final int TICK_ENABLE   =   0;
    private static final int TICK_METEOR   =  20;  /* 1s in */
    private static final int TICK_SUPERNOVA = 240; /* 12s in — mid-nuke */
    private static final int TICK_DONE     = 480;  /* 24s in */

    private static int tick;
    private static boolean active;
    private static boolean registered;
    private static double anchorX, anchorY, anchorZ;

    private DemoSequencer() {}

    public static synchronized void register()
    {
        if (registered) return;
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(mc -> DemoSequencer.tick());
        BBSDiag.feature("fx-demo").event("register").emit();
    }

    public static void start()
    {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        Vec3 p = mc.player.position();
        anchorX = p.x;
        anchorY = p.y;
        anchorZ = p.z;
        tick = 0;
        active = true;
        BBSDiag.feature("fx-demo").event("start")
            .field("anchor", "(" + anchorX + "," + anchorY + "," + anchorZ + ")").emit();
    }

    public static void cancel()
    {
        if (!active) return;
        active = false;
        SupernovaEffect.cancel();
        NukeEffect.cancel();
        MeteorEffect.cancel();
        BBSDiag.feature("fx-demo").event("cancel").emit();
    }

    private static void tick()
    {
        if (!active) return;

        if (tick == TICK_ENABLE)
        {
            if (!BBSFxPipelineRegistrar.ENABLED)
            {
                BBSFxPipelineRegistrar.setEnabled(true);
                BBSDiag.feature("fx-demo").event("enable").emit();
            }
        }
        else if (tick == TICK_METEOR)
        {
            MeteorEffect.trigger((float) anchorX, (float) anchorY, (float) anchorZ);
            BBSDiag.feature("fx-demo").event("meteor").emit();
        }
        else if (tick == TICK_SUPERNOVA)
        {
            /* Supernova lifts 60 blocks above the anchor so the burst
             *  is visible against the sky rather than competing with
             *  the still-rising mushroom cloud at ground level. */
            SupernovaEffect.trigger((float) anchorX, (float) anchorY + 60F, (float) anchorZ);
            BBSDiag.feature("fx-demo").event("supernova").emit();
        }
        else if (tick >= TICK_DONE)
        {
            active = false;
            BBSDiag.feature("fx-demo").event("done").emit();
        }

        tick++;
    }

    public static boolean isActive() { return active; }

}
