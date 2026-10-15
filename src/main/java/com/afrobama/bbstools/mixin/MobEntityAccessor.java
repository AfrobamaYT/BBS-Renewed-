package com.afrobama.bbstools.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes {@code MobEntity#goalSelector} and {@code MobEntity#targetSelector}
 * (protected in vanilla 1.21.1) so MobArmy can clear AI goals on bake/play
 * without needing an access widener.
 */
@Mixin(Mob.class)
public interface MobEntityAccessor
{
    @Accessor("goalSelector")
    GoalSelector bbstools$getGoalSelector();

    @Accessor("targetSelector")
    GoalSelector bbstools$getTargetSelector();
}
