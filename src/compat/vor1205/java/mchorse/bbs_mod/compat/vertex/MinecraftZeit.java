package mchorse.bbs_mod.compat.vertex;

import net.minecraft.client.DeltaTracker;

/** vor1205: Minecraft#getTimer (1.21.1-Name, DeltaTracker) an Minecraft injiziert. */
public interface MinecraftZeit
{
    default DeltaTracker getTimer()
    {
        return DeltaTracker.Timer.SPIEL;
    }
}
