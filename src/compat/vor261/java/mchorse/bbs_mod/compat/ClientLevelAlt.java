package mchorse.bbs_mod.compat;

import net.minecraft.client.multiplayer.ClientLevel;

/** 26.2's world clocks (NeoForge primer 26.1, "world clocks"); before 26.1 the overworld time is the day time. Injected into ClientLevel. */
public interface ClientLevelAlt
{
    default long getOverworldClockTime()
    {
        return ((ClientLevel) this).getDayTime();
    }
}
