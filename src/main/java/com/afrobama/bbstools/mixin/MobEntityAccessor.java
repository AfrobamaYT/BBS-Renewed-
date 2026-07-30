package com.afrobama.bbstools.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;


@Mixin(Mob.class)
public interface MobEntityAccessor
{
    @Accessor("goalSelector")
    GoalSelector bbstools$getGoalSelector();

    @Accessor("targetSelector")
    GoalSelector bbstools$getTargetSelector();
}
