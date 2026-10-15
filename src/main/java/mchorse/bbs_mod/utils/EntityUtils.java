package mchorse.bbs_mod.utils;

import net.minecraft.world.entity.Entity;

/**
 * Entity calls whose name differs between the supported Minecraft versions. Large classes
 * call these instead of carrying their own version guards.
 */
public class EntityUtils
{
    /**
     * Send the entity's velocity to the clients on the next tick.
     */
    public static void syncVelocity(Entity entity)
    {
        //? if >=26.3 {
        /*entity.syncVelocity = true;
        *///?} else {
        entity.hurtMarked = true;
        //?}
    }

    public static void setPermanentlyInvulnerable(Entity entity, boolean invulnerable)
    {
        //? if >=26.3 {
        /*entity.setPermanentlyInvulnerable(invulnerable);
        *///?} else {
        entity.setInvulnerable(invulnerable);
        //?}
    }
}
