package com.afrobama.bbstools.client.fracture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Stores BlockState snapshots so the impact-propagation feature can
 * actually destroy blocks during a cinematic take and restore them on
 * cleanup (rewind, world unload, /bbstools fracture-clear).
 *
 * <h2>How it's used</h2>
 * <ol>
 *   <li>{@link BlockShatterPropagator} processes a block under a
 *       destructive event → calls {@link #snapshotAndRemove}, which
 *       saves the original BlockState and sets the world block to
 *       air. Debris particles spawn separately.</li>
 *   <li>When the user rewinds, {@link FractureRegistry#clearAll}
 *       fires which calls {@link #restoreAll} → all snapshotted
 *       blocks get put back in their original BlockState.</li>
 *   <li>World unload also restores (via the BBSTools client lifecycle
 *       hook) so user's build isn't accidentally damaged.</li>
 * </ol>
 *
 * <h2>Multiplayer caveat</h2>
 * <p>Client-side {@code World.setBlockState} only takes effect in
 * singleplayer (where the integrated server accepts the change) or
 * with op privileges. In a regular multiplayer client, the server
 * will revert the modification next tick. BBS is overwhelmingly used
 * single-player for cinematic recording — the destructive path is
 * the whole feature, so we attempt it regardless and gracefully fail
 * back to particle-only effect when it doesn't take.</p>
 */
public final class BlockSnapshotRegistry
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/fracture/snapshot");

    private static final Map<Long, BlockState> SNAPSHOTS = new HashMap<>();

    private BlockSnapshotRegistry()
    {
    }

    /**
     * Snapshot the given block's current state and replace it with air.
     * Idempotent — calling on an already-snapshotted position keeps the
     * earliest snapshot and only attempts the air swap once.
     *
     * <p>Returns the snapshotted BlockState (could be air if the block
     * was already empty when first seen).</p>
     */
    public static synchronized BlockState snapshotAndRemove(BlockPos pos)
    {
        if (pos == null) return null;
        Level world = Minecraft.getInstance().level;
        if (world == null) return null;

        long key = pos.asLong();
        if (SNAPSHOTS.containsKey(key))
        {
            /* Already removed — nothing more to do. */
            return SNAPSHOTS.get(key);
        }

        BlockState original;
        try { original = world.getBlockState(pos); }
        catch (Throwable t) { return null; }

        if (original == null || original.isAir())
        {
            /* Air originally — nothing to remove or restore. */
            return original;
        }

        SNAPSHOTS.put(key, original);

        try
        {
            /* Flag 3 = update neighbours + send to listeners. Without
             *  flag 3 the change isn't visible to the client renderer. */
            world.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        }
        catch (Throwable t)
        {
            LOG.debug("setBlockState(air) failed at {}: {}", pos, t.toString());
        }
        return original;
    }

    /**
     * Restore every snapshotted block to its original state. Cheap
     * if no snapshots are tracked.
     */
    public static synchronized int restoreAll()
    {
        if (SNAPSHOTS.isEmpty()) return 0;
        Level world = Minecraft.getInstance().level;
        if (world == null)
        {
            int n = SNAPSHOTS.size();
            SNAPSHOTS.clear();
            return n;
        }

        int restored = 0;
        for (Map.Entry<Long, BlockState> e : SNAPSHOTS.entrySet())
        {
            BlockPos pos = BlockPos.of(e.getKey());
            try
            {
                world.setBlock(pos, e.getValue(), 3);
                restored++;
            }
            catch (Throwable t)
            {
                LOG.debug("restore failed at {}: {}", pos, t.toString());
            }
        }
        SNAPSHOTS.clear();
        LOG.info("Restored {} fracture-destroyed blocks", restored);
        return restored;
    }

    /** Drop snapshots without restoring — used when the world has
     *  unloaded and there's no point trying to write back. */
    public static synchronized void dropAll()
    {
        SNAPSHOTS.clear();
    }

    public static synchronized int snapshotCount()
    {
        return SNAPSHOTS.size();
    }

}
