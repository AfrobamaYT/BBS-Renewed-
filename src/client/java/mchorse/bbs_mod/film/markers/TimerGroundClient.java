package mchorse.bbs_mod.film.markers;

import net.fabricmc.api.ClientModInitializer;

/** Registers world guidance even when the lazy Timers sidebar has never been opened. */
public final class TimerGroundClient implements ClientModInitializer
{
    @Override
    public void onInitializeClient()
    {
        TimerGroundGuidance.register();
    }
}
