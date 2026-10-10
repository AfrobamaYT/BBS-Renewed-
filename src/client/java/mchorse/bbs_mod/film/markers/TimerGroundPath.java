package mchorse.bbs_mod.film.markers;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Vanilla walking navigation on an unspawned proxy; never moves the player or edits the level. */
public final class TimerGroundPath
{
    public static final int MAX_DISTANCE = 48;
    private static final int SEARCH_RADIUS = MAX_DISTANCE + 8;

    public record Result(List<Vec3> points, String error)
    {
        public Result
        {
            points = List.copyOf(points);
        }
    }

    private TimerGroundPath()
    {}

    public static Result find(ClientLevel level, Player player, Vec3 goal)
    {
        Vec3 start = player.position();

        if (!Double.isFinite(goal.x) || !Double.isFinite(goal.y) || !Double.isFinite(goal.z))
        {
            return error("Invalid marker position");
        }
        if (start.distanceTo(goal) > MAX_DISTANCE)
        {
            return error("Marker is beyond 48 blocks; move closer");
        }
        BlockPos center = player.blockPosition();

        /* PathNavigationRegion otherwise substitutes EmptyLevelChunk and hides missing terrain. */
        for (int x = (center.getX() - SEARCH_RADIUS) >> 4; x <= (center.getX() + SEARCH_RADIUS) >> 4; x++)
        {
            for (int z = (center.getZ() - SEARCH_RADIUS) >> 4; z <= (center.getZ() + SEARCH_RADIUS) >> 4; z++)
            {
                if (level.getChunkSource().getChunkNow(x, z) == null)
                {
                    return error("Route needs loaded terrain; wait or increase render distance");
                }
            }
        }

        Walker walker = new Walker(level, player);
        GroundPathNavigation navigation = (GroundPathNavigation) walker.getNavigation();

        navigation.setRequiredPathLength(MAX_DISTANCE);
        navigation.setCanFloat(false);
        navigation.setCanOpenDoors(false);
        navigation.getNodeEvaluator().setCanPassDoors(true);
        Path path = navigation.createPath(BlockPos.containing(goal), 0);

        if (path == null || !path.canReach())
        {
            return error("No safe walking route to marker (or search budget exhausted)");
        }
        List<Vec3> points = new ArrayList<>();

        for (int index = 0; index < path.getNodeCount(); index++)
        {
            BlockPos node = path.getNodePos(index);
            double floor = WalkNodeEvaluator.getFloorLevel(level, node);
            Vec3 point = path.getEntityPosAtNode(walker, index);

            points.add(new Vec3(point.x, floor + 0.04D, point.z));
        }

        return new Result(points, "");
    }

    private static Result error(String error)
    {
        return new Result(List.of(), error);
    }

    /** The proxy is never added to any entity manager, ticked or given a movement command. */
    private static final class Walker extends Mob
    {
        private EntityDimensions dimensions;
        private float step;
        private int fall;

        private Walker(ClientLevel level, Player player)
        {
            super(EntityTypes.PIG, level);
            this.dimensions = player.getDimensions(player.getPose());
            this.step = player.maxUpStep();
            this.fall = player.getMaxFallDistance();
            this.setNoAi(true);
            this.refreshDimensions();
            this.setPos(player.position());
            this.setOnGround(player.onGround());

            for (PathType type : PathType.values())
            {
                if (type.getMalus() > 0F || type == PathType.WATER || type == PathType.WATER_BORDER)
                {
                    this.setPathfindingMalus(type, -1F);
                }
            }
        }

        @Override
        protected EntityDimensions getDefaultDimensions(Pose pose)
        {
            return this.dimensions == null ? EntityDimensions.scalable(0.6F, 1.8F) : this.dimensions;
        }

        @Override
        protected PathNavigation createNavigation(Level level)
        {
            return new TimerPlanningNavigation(this, level);
        }

        @Override
        public float maxUpStep()
        {
            return this.step;
        }

        @Override
        public int getMaxFallDistance()
        {
            return this.fall;
        }
    }
}
