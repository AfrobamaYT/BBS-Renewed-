package mchorse.bbs_mod.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Gliding has no public setter, only a tracked flag, and the flag is what tells every client to
 * spread an entity's wings; a replay needs the raw one (BBS FS e383f1930).
 */
@Mixin(Entity.class)
public interface EntityFlagInvoker
{
    @Invoker("setSharedFlag")
    public void bbs$setSharedFlag(int index, boolean value);
}
