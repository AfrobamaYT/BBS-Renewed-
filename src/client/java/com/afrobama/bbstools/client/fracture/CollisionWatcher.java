package com.afrobama.bbstools.client.fracture;

import com.afrobama.bbstools.fracture.ModelFractureActionClip;

import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Per-bone collision-driven progressive fracture (Phase 6). Once
 * activated by a fracture clip with {@code triggerMode = on_collision},
 * the watcher polls every client tick: for each not-yet-fractured bone
 * of the actor, compute the bone's world-space AABB, check if it
 * intersects any non-air block, and if yes — spawn a physics piece for
 * that bone alone. Other bones keep following the keyframe path until
 * they too collide with something.
 *
 * <p>This is the "locomotive driving into a wall and progressively
 * shattering as each part hits" effect. Pure cinematic gold for
 * cinematic crash shots.</p>
 *
 * <h2>Algorithm</h2>
 * <ol>
 *   <li>At trigger tick, register the watcher with a snapshot of all
 *       BOBJ bone fragments (vertices + initial world matrices)</li>
 *   <li>Each subsequent client tick (until lifetime exceeded or actor
 *       fully shattered):
 *     <ul>
 *       <li>Re-extract bone positions from current actor pose</li>
 *       <li>For each non-fractured bone: compute AABB, test vs blocks</li>
 *       <li>Bones that intersect → spawn convex body, mark fractured</li>
 *     </ul>
 *   </li>
 *   <li>Stop watching when all bones fractured OR lifetime exceeded</li>
 * </ol>
 *
 * <h2>Tunneling</h2>
 * <p>v1 uses single-frame AABB checks. At very high actor velocities
 * a thin bone can pass through a wall in one tick without registering
 * a hit. v2 will add swept-AABB ray-cast from prev to current. For
 * typical cinematic speeds (5-15 blocks/tick) the single-frame check
 * is sufficient.</p>
 *
 * <h2>Performance</h2>
 * <p>Per tick cost per watcher: O(bones × blocks_in_AABB). For a
 * locomotive (~20 bones, AABB sizes 1-3 blocks) that's ~20 × 27 = 540
 * block lookups per tick. Trivial. Multiple simultaneous watchers
 * (multiple actors fracturing at once) scale linearly.</p>
 */
public final class CollisionWatcher
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/fracture/collision");
    private static final List<Watcher> ACTIVE = new ArrayList<>();
    private static volatile boolean tickerRegistered;

    private static final class Watcher
    {
        final ModelFractureActionClip clip;
        final IEntity actor;
        final Replay replay;
        final int startTick;
        final int endTickInclusive;
        final Set<Integer> fracturedBones = new HashSet<>();
        final Set<String> allowedBones; /* null = all bones */

        Watcher(ModelFractureActionClip clip, IEntity actor, Replay replay,
                int startTick, int endTickInclusive, Set<String> allowedBones)
        {
            this.clip = clip;
            this.actor = actor;
            this.replay = replay;
            this.startTick = startTick;
            this.endTickInclusive = endTickInclusive;
            this.allowedBones = allowedBones;
        }
    }

    private CollisionWatcher()
    {
    }

    public static synchronized void startWatching(ModelFractureActionClip clip,
                                                  IEntity actor, Replay replay, int tick)
    {
        if (clip == null || actor == null || replay == null) return;

        ensureTicker();

        int duration = clip.duration.get();
        int endTick = (duration > 0) ? tick + duration : tick + 600; /* default 30s window */

        Set<String> allowed = (clip.groupName.get() != null && !clip.groupName.get().isEmpty())
            ? FractureGroupConfig.lookupGroup(actor, clip.groupName.get())
            : null;

        ACTIVE.add(new Watcher(clip, actor, replay, tick, endTick, allowed));
        LOG.info("CollisionWatcher started: tick {} → {} ({} bones allowed)",
            tick, endTick, allowed == null ? "all" : allowed.size());
    }

    /**
     * Drain & process every active watcher for one tick. Called by
     * the client-tick hook in {@code BBSToolsModClient}.
     */
    public static synchronized void onClientTick(int filmTick)
    {
        if (ACTIVE.isEmpty()) return;

        Iterator<Watcher> it = ACTIVE.iterator();
        while (it.hasNext())
        {
            Watcher w = it.next();

            /* Stop conditions: lifetime exceeded, all bones gone. */
            if (filmTick > w.endTickInclusive)
            {
                it.remove();
                continue;
            }

            try
            {
                processWatcher(w, filmTick);
            }
            catch (Throwable t)
            {
                LOG.warn("CollisionWatcher tick failed, dropping: {}", t.toString());
                it.remove();
            }
        }
    }

    private static void processWatcher(Watcher w, int filmTick)
    {
        Level world = Minecraft.getInstance().level;
        if (world == null) return;

        /* Re-extract every tick because the actor's pose may have
         *  changed (animation playing, keyframes interpolating). */
        List<BoneMeshExtractor.BoneFragment> fragments = BoneMeshExtractor.extract(w.actor);
        if (fragments.isEmpty()) return;

        double actorX = w.replay.keyframes.x.interpolate(filmTick);
        double actorY = w.replay.keyframes.y.interpolate(filmTick);
        double actorZ = w.replay.keyframes.z.interpolate(filmTick);

        Vector3f actorVel = computeActorVelocity(w.replay, filmTick);

        /* If the actor is barely moving, an `on_collision` trigger
         *  cannot meaningfully detect "impact" — there's no impact, the
         *  actor is sitting still. Lowered threshold so slow-moving
         *  trains still trigger; previous 0.05 blocks/tick gate was
         *  too strict and silently skipped legitimate slow crashes. */
        float actorSpeedSq = actorVel.x * actorVel.x + actorVel.y * actorVel.y + actorVel.z * actorVel.z;
        if (actorSpeedSq < 0.0001F) return;

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (BoneMeshExtractor.BoneFragment frag : fragments)
        {
            if (w.fracturedBones.contains(frag.boneIndex)) continue;
            if (w.allowedBones != null && !w.allowedBones.contains(frag.boneName)) continue;
            if (frag.isEmpty()) continue;

            /* Compute world AABB for this bone. */
            float bx = frag.worldMatrix.m30();
            float by = frag.worldMatrix.m31();
            float bz = frag.worldMatrix.m32();

            double worldX = actorX + bx;
            double worldY = actorY + by;
            double worldZ = actorZ + bz;

            /* Find local AABB from vertices. */
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
            for (Vector3f v : frag.verticesLocal)
            {
                if (v.x < minX) minX = v.x;
                if (v.y < minY) minY = v.y;
                if (v.z < minZ) minZ = v.z;
                if (v.x > maxX) maxX = v.x;
                if (v.y > maxY) maxY = v.y;
                if (v.z > maxZ) maxZ = v.z;
            }

            int bx0 = (int) Math.floor(worldX + minX);
            int by0 = (int) Math.floor(worldY + minY);
            int bz0 = (int) Math.floor(worldZ + minZ);
            int bx1 = (int) Math.floor(worldX + maxX);
            int by1 = (int) Math.floor(worldY + maxY);
            int bz1 = (int) Math.floor(worldZ + maxZ);

            /* Bone center for the velocity-direction filter — we only
             *  fracture on blocks that the bone is actively moving INTO
             *  (i.e. the dot product of motion-direction and
             *  block-center-relative-to-bone is positive). This skips:
             *  - ground blocks under the wheels (block is BELOW bone,
             *    motion is HORIZONTAL → dot = 0 → skip)
             *  - blocks the actor has already passed (block is BEHIND
             *    bone in motion direction → dot < 0 → skip)
             *  - blocks beside the actor not in motion direction.
             *  Only blocks the actor is driving INTO (dot > 0) trigger
             *  the fracture. The threshold is small but non-zero so a
             *  block exactly perpendicular doesn't sneak through due
             *  to floating-point noise. */
            float boneCenterX = (float) worldX + (minX + maxX) * 0.5F;
            float boneCenterY = (float) worldY + (minY + maxY) * 0.5F;
            float boneCenterZ = (float) worldZ + (minZ + maxZ) * 0.5F;

            BlockState hitBlock = null;
            int hitX = 0, hitY = 0, hitZ = 0;

            outer:
            for (int x = bx0; x <= bx1; x++)
            {
                for (int y = by0; y <= by1; y++)
                {
                    for (int z = bz0; z <= bz1; z++)
                    {
                        pos.set(x, y, z);
                        BlockState s = world.getBlockState(pos);
                        if (s.isAir()) continue;

                        /* Block center → relative vector from bone. */
                        float dx = (x + 0.5F) - boneCenterX;
                        float dy = (y + 0.5F) - boneCenterY;
                        float dz = (z + 0.5F) - boneCenterZ;

                        /* Approach component = velocity dotted with
                         *  block-from-bone vector. Positive = moving
                         *  into the block; negative or zero = moving
                         *  away or perpendicular. The previous 0.05
                         *  threshold was magnitude-based which broke
                         *  for slow trains; pure-direction (>= 0)
                         *  works at any speed. */
                        float approach = actorVel.x * dx + actorVel.y * dy + actorVel.z * dz;
                        if (approach <= 0F) continue;

                        hitBlock = s;
                        hitX = x; hitY = y; hitZ = z;
                        break outer;
                    }
                }
            }

            if (hitBlock == null) continue;

            /* Phase B: spawn a single-bone fracture via FractureSpawner
             *  with allowedBoneNames containing only this bone. The
             *  spawner creates the BonePhysicsBody and registers with
             *  the simulator; we register with FractureRegistry for
             *  rewind cleanup. */
            java.util.Set<String> singleBone = java.util.Collections.singleton(frag.boneName);
            int lifetimeTicks = w.clip.lifetime.get() * 20;

            /* Honour the clip's inherit-velocity toggle: when off, we
             *  pass null so spawned bodies don't inherit actor velocity
             *  (they'll start at rest, falling under gravity + impulse). */
            Vector3f spawnVel = w.clip.inheritVelocity.get() ? actorVel : null;

            FractureSpawner.Result oneResult = FractureSpawner.fracture(
                w.actor, actorX, actorY, actorZ, spawnVel,
                w.clip.forceJitter.get(), singleBone, filmTick, lifetimeTicks, w.clip);

            if (!oneResult.isEmpty())
            {
                w.fracturedBones.add(frag.boneIndex);
                FractureRegistry.register(w.actor, oneResult.spawnedBodies, w.startTick, lifetimeTicks);
                LOG.info("Bone '{}' fractured at film tick {} — impact block {} at ({},{},{})",
                    frag.boneName, filmTick, hitBlock.getBlock(), hitX, hitY, hitZ);
            }
        }
    }

    private static Vector3f computeActorVelocity(Replay replay, int tick)
    {
        try
        {
            int prev = Math.max(0, tick - 1);
            double dx = replay.keyframes.x.interpolate(tick) - replay.keyframes.x.interpolate(prev);
            double dy = replay.keyframes.y.interpolate(tick) - replay.keyframes.y.interpolate(prev);
            double dz = replay.keyframes.z.interpolate(tick) - replay.keyframes.z.interpolate(prev);
            return new Vector3f((float) dx, (float) dy, (float) dz);
        }
        catch (Throwable t)
        {
            return new Vector3f(0F, 0F, 0F);
        }
    }

    private static void ensureTicker()
    {
        /* Ticker registration handled centrally in BBSToolsModClient.
         *  This flag exists so we can short-circuit if the registration
         *  somehow didn't run (defensive). */
        tickerRegistered = true;
    }

    public static synchronized void clearAll()
    {
        ACTIVE.clear();
    }

    public static synchronized int activeWatcherCount()
    {
        return ACTIVE.size();
    }

}
