package mchorse.bbs_mod.film.markers;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;

import java.util.Set;

/** Query-only native walking plan; no movement or entity tick is ever requested. */
final class TimerPlanningNavigation extends GroundPathNavigation
{
    private int regionRadius = TimerGroundPath.MAX_DISTANCE + 8;
    private PathFinder planner;

    TimerPlanningNavigation(Mob mob, Level level)
    {
        super(mob, level);
    }

    void regionRadius(int radius)
    {
        this.regionRadius = radius;
    }

    @Override
    protected PathFinder createPathFinder(int nodes)
    {
        this.planner = super.createPathFinder(nodes);

        return this.planner;
    }

    @Override
    protected Path createPath(Set<BlockPos> targets, int padding, boolean head, int accuracy, float distance)
    {
        BlockPos center = head ? this.mob.blockPosition().above() : this.mob.blockPosition();
        PathNavigationRegion region = new PathNavigationRegion(this.level,
            center.offset(-this.regionRadius, -this.regionRadius, -this.regionRadius),
            center.offset(this.regionRadius, this.regionRadius, this.regionRadius));

        /* Reuse the native evaluator/search and full travel/node budgets. Only its terrain
         * view is bounded here; shortening travel distance can discard valid detours. */
        return this.planner.findPath(region, this.mob, targets, distance, accuracy, 1F);
    }

    @Override
    protected boolean canUpdatePath()
    {
        /* Native WalkNodeEvaluator still projects an airborne start onto actual terrain.
         * The real player's grounded flag and all walking capabilities remain unchanged. */
        return true;
    }
}
