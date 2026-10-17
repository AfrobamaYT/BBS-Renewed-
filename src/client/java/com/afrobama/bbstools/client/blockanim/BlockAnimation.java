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
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
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
            float elapsed = (System.nanoTime() - startTimeNanos) / 1_000_000_000F;
            float t = duration <= 0.0F ? 1.0F : Math.min(1.0F, elapsed / duration);
            return new Vec3(
                startPos.x + (endPos.x - startPos.x) * t,
                startPos.y + (endPos.y - startPos.y) * t,
                startPos.z + (endPos.z - startPos.z) * t);
        }

        public boolean isFinished()
        {
            float elapsed = (System.nanoTime() - startTimeNanos) / 1_000_000_000F;
            return elapsed >= duration;
        }
    }

    private static volatile List<AnimatedBlock> activeBlocks = new ArrayList<>();

    private BlockAnimation() {}

    public static List<AnimatedBlock> getActiveBlocks() { return activeBlocks; }

    /* (Removed 2026-05-31) captureAndAnimate(offset, duration) read the old TAB-hold
     * BlockSelection region — that selection feature was deleted (inaccurate/annoying,
     * superseded by WorldBlockCapture). Use captureAndAnimateRegion(min, max, ...) with
     * explicit coords instead (driven by BlockAnimationClientClip). */

    public static void clear()
    {
        activeBlocks = new ArrayList<>();
        LOG.info("Cleared block animation");
    }

    /** Region-AABB variant called by BlockAnimationClientClip. Iterates
     *  the inclusive AABB from min to max, captures non-air blocks,
     *  starts the animation along offset over duration. */
    public static int captureAndAnimateRegion(BlockPos min, BlockPos max, Vec3 offset, float duration)
    {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return 0;
        ClientLevel world = mc.level;
        /* ClientLevel.hasChunk always returns true; query actual storage without an empty fallback. */
        for (int chunkX = min.getX() >> 4; chunkX <= max.getX() >> 4; chunkX++)
        {
            for (int chunkZ = min.getZ() >> 4; chunkZ <= max.getZ() >> 4; chunkZ++)
            {
                if (world.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) == null)
                    throw new IllegalArgumentException("Load the complete animation region before playback.");
            }
        }
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

}
