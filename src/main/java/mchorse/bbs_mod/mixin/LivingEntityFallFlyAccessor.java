package mchorse.bbs_mod.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Ticks into a glide or a riptide, and the lean into a swim. Vanilla counts it up while flying and the elytra's dive is
 * driven off it; an entity placed from a replay isn't flown, so the recorded count is handed
 * back instead (BBS FS e383f1930).
 */
@Mixin(LivingEntity.class)
public interface LivingEntityFallFlyAccessor
{
    @Accessor("fallFlyTicks")
    public int bbs$getFallFlyTicks();

    @Accessor("fallFlyTicks")
    public void bbs$setFallFlyTicks(int ticks);

    /** How far the body leans into a swim; vanilla grows it per tick, an unticked body is told. */
    @Accessor("swimAmount")
    public void bbs$setSwimAmount(float amount);

    @Accessor("swimAmountO")
    public void bbs$setSwimAmountO(float amount);
}
