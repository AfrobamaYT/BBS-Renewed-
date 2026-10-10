package mchorse.bbs_mod.film.markers;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;

/** Query-only native walking plan; no movement or entity tick is ever requested. */
final class TimerPlanningNavigation extends GroundPathNavigation
{
    TimerPlanningNavigation(Mob mob, Level level)
    {
        super(mob, level);
    }

    @Override
    protected boolean canUpdatePath()
    {
        /* Native WalkNodeEvaluator still projects an airborne start onto actual terrain.
         * The real player's grounded flag and all walking capabilities remain unchanged. */
        return true;
    }
}
