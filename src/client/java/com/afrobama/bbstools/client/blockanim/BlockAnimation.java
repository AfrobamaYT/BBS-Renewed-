// Coded by Afrobama/Claude — Block Animation foundation.
// Captures the current Block Selection into an animated-block set:
// each block in the region becomes an AnimatedBlock with start/end
// world position + interpolation timer. Renderer interpolates over
// duration, drawing each block at its current animated position.
//
// MVP this iteration: data model + capture API + simple linear-translate
// preset. Per-block keyframes (full After-Effects-style) come in a
// future iter via BBS's KeyframeChannel infrastructure.
package com.afrobama.bbstools.client.blockanim;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BlockAnimation
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/blockanim");

    /** Single animated block. Position interpolates start -> end over duration. */
    public static final class AnimatedBlock
    {
        public final BlockState state;
        public final Vec3 startPos;
        public final Vec3 endPos;
        public final float duration;  /* seconds */
        public final long startTimeNanos;
        private float filmSeconds = Float.NaN;

        public AnimatedBlock(BlockState state, Vec3 startPos, Vec3 endPos, float duration, long startTimeNanos)
        {
            this.state = state;
            this.startPos = startPos;
            this.endPos = endPos;
            this.duration = duration;
            this.startTimeNanos = startTimeNanos;
        }

        /** Current world position interpolated by elapsed time. */
        public Vec3 currentPos()
        {
            float elapsed = Float.isNaN(this.filmSeconds)
                ? (System.nanoTime() - this.startTimeNanos) / 1_000_000_000F : this.filmSeconds;
            float t = duration <= 0.0F ? 1.0F : Math.min(1.0F, elapsed / duration);
            return new Vec3(
                startPos.x + (endPos.x - startPos.x) * t,
                startPos.y + (endPos.y - startPos.y) * t,
                startPos.z + (endPos.z - startPos.z) * t);
        }

        public boolean isFinished()
        {
            float elapsed = Float.isNaN(this.filmSeconds)
                ? (System.nanoTime() - this.startTimeNanos) / 1_000_000_000F : this.filmSeconds;
            return elapsed >= duration;
        }
    }

    private static volatile List<AnimatedBlock> activeBlocks = new ArrayList<>();
    private record Configuration(BlockPos min, BlockPos max, Vec3 offset, float seconds) {}
    private record Capture(Configuration configuration, List<AnimatedBlock> blocks) {}
    private static final java.util.Map<com.afrobama.bbstools.clip.BlockAnimationActionClip, Capture> filmCaptures = new java.util.WeakHashMap<>();

    private BlockAnimation() {}

    public static List<AnimatedBlock> getActiveBlocks()
    {
        List<AnimatedBlock> result = new ArrayList<>(activeBlocks);
        Minecraft client = Minecraft.getInstance();
        mchorse.bbs_mod.film.Film edited = null;
        if (client.gui.screen() instanceof mchorse.bbs_mod.ui.framework.UIScreen
            && mchorse.bbs_mod.BBSModClient.peekDashboard() != null
            && mchorse.bbs_mod.BBSModClient.peekDashboard().panels.panel instanceof mchorse.bbs_mod.ui.film.UIFilmPanel panel
            && panel.getData() != null)
        {
            edited = panel.getData();
            result.addAll(sampleFilm(edited, panel.getCursor(), panel.getRunner().getContext().transition));
        }
        for (mchorse.bbs_mod.film.BaseFilmController controller : mchorse.bbs_mod.BBSModClient.getFilms().getControllers())
        {
            if (controller.film != edited)
            {
                result.addAll(sampleFilm(controller.film, controller.getTick(), controller.paused ? 0F
                    : client.getDeltaTracker().getGameTimeDeltaPartialTick(false)));
            }
        }
        return result;
    }

    /* (Removed 2026-05-31) captureAndAnimate(offset, duration) read the old TAB-hold
     * BlockSelection region — that selection feature was deleted (inaccurate/annoying,
     * superseded by WorldBlockCapture). Use captureAndAnimateRegion(min, max, ...) with
     * explicit coords instead (driven by BlockAnimationClientClip). */

    public static void clear()
    {
        activeBlocks = new ArrayList<>();
        filmCaptures.clear();
        LOG.info("Cleared block animation");
    }

    /** Region-AABB variant called by BlockAnimationClientClip. Iterates
     *  the inclusive AABB from min to max, captures non-air blocks,
     *  starts the animation along offset over duration. */
    public static int captureAndAnimateRegion(BlockPos min, BlockPos max, Vec3 offset, float duration)
    {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return 0;
        Level world = mc.level;
        long now = System.nanoTime();
        List<AnimatedBlock> captured = new ArrayList<>();
        for (int x = min.getX(); x <= max.getX(); x++)
        {
            for (int y = min.getY(); y <= max.getY(); y++)
            {
                for (int z = min.getZ(); z <= max.getZ(); z++)
                {
                    BlockPos p = new BlockPos(x, y, z);
                    BlockState s = world.getBlockState(p);
                    if (s.isAir()) continue;
                    Vec3 start = new Vec3(x, y, z);
                    Vec3 end = start.add(offset);
                    captured.add(new AnimatedBlock(s, start, end, duration, now));
                }
            }
        }
        activeBlocks = captured;
        LOG.info("captureAndAnimateRegion: {} blocks, offset={}, duration={}s", captured.size(), offset, duration);
        return captured.size();
    }

    public static void prepareClip(com.afrobama.bbstools.clip.BlockAnimationActionClip clip)
    {
        capture(clip);
    }

    public static List<AnimatedBlock> sampleFilm(mchorse.bbs_mod.film.Film film, int tick, float transition)
    {
        List<AnimatedBlock> result = new ArrayList<>();
        for (mchorse.bbs_mod.film.replays.Replay replay : film.replays.getList())
        {
            if (!replay.enabled.get())
            {
                continue;
            }
            float time = replay.getTick(tick) + transition;
            for (mchorse.bbs_mod.utils.clips.Clip value : replay.actions.get())
            {
                if (value instanceof com.afrobama.bbstools.clip.BlockAnimationActionClip clip
                    && clip.enabled.get() && time >= clip.tick.get())
                {
                    List<AnimatedBlock> blocks = capture(clip).blocks();
                    for (AnimatedBlock block : blocks)
                    {
                        block.filmSeconds = (time - clip.tick.get()) / 20F;
                    }
                    result.addAll(blocks);
                }
            }
        }
        return result;
    }

    private static Capture capture(com.afrobama.bbstools.clip.BlockAnimationActionClip clip)
    {
        Configuration config = new Configuration(
            new BlockPos(Math.min(clip.minX.get(), clip.maxX.get()), Math.min(clip.minY.get(), clip.maxY.get()), Math.min(clip.minZ.get(), clip.maxZ.get())),
            new BlockPos(Math.max(clip.minX.get(), clip.maxX.get()), Math.max(clip.minY.get(), clip.maxY.get()), Math.max(clip.minZ.get(), clip.maxZ.get())),
            new Vec3(clip.offsetX.get(), clip.offsetY.get(), clip.offsetZ.get()), clip.animDuration.get());
        Capture previous = filmCaptures.get(clip);
        if (previous != null && previous.configuration().equals(config))
        {
            return previous;
        }
        List<AnimatedBlock> blocks = new ArrayList<>();
        for (BlockPos position : BlockPos.betweenClosed(config.min(), config.max()))
        {
            BlockState state = Minecraft.getInstance().level.getBlockState(position);
            if (!state.isAir())
            {
                Vec3 start = new Vec3(position.getX(), position.getY(), position.getZ());
                blocks.add(new AnimatedBlock(state, start, start.add(config.offset()), config.seconds(), 0L));
            }
        }
        Capture captured = new Capture(config, blocks);
        filmCaptures.put(clip, captured);
        return captured;
    }

}
