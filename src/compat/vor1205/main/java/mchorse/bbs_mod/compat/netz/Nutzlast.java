package mchorse.bbs_mod.compat.netz;

import net.minecraft.resources.ResourceLocation;

/**
 * vor1205: steht fuer 1.20.5s CustomPacketPayload (in 1.20.4 gibt es ein gleichnamiges
 * Interface mit anderer Form: write/id statt type).
 */
public interface Nutzlast
{
    Type<? extends Nutzlast> type();

    record Type<T extends Nutzlast>(ResourceLocation id)
    {}
}
