package com.afrobama.bbstools.client.fracture;

import com.afrobama.bbstools.fracture.PhysicsBlockDestructionActionClip;

import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.utils.clips.Clip;
import net.minecraft.core.BlockPos;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client subclass of {@link PhysicsBlockDestructionActionClip}. On
 * clip start, fires a single region-bounded propagation event into
 * {@link BlockShatterPropagator} centred at the configured impact
 * point. Wave expands through the region's blocks at the configured
 * speed, removing them (if destroyBlocks) and spawning PhysicsMod
 * debris with outward velocity scaled by scatterForce.
 */
public class PhysicsBlockDestructionClientClip extends PhysicsBlockDestructionActionClip
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/fracture/block-destruction");

    /** Last fire timestamp + tick — used to throttle rapid re-fires
     *  when the user scrubs through the clip's start tick or the film
     *  is looping. Without this guard, scrubbing across tick=0 a few
     *  times in a second triggers 10+ destruction events that all pile
     *  up debris on top of each other (= "structure duplicates" visual
     *  the user reported). */
    private long lastFireWallclockMs;
    private int lastFireTick = Integer.MIN_VALUE;

    /** Globally throttle the "empty region" chat warning to once per
     *  5 seconds across ALL clip instances. Without this, a film with
     *  multiple misconfigured clips at tick=0 + auto-loop spams chat
     *  several times per second. */
    private static volatile long lastEmptyRegionWarnMs;

    @Override
    protected void applyClientAction(IEntity entity, Film film, Replay replay, int tick)
    {
        if (!FracturePhysicsBridge.isAvailable())
        {
            LOG.warn("[block-destruction] PhysicsMod bridge unavailable — clip is a no-op");
            return;
        }

        /* Throttle: skip if the same tick already fired within the last
         *  500ms. Allows refire when the user genuinely re-plays the
         *  clip from start, but blocks the scrub-storm pattern. */
        long now = System.currentTimeMillis();
        if (tick == this.lastFireTick && (now - this.lastFireWallclockMs) < 500L)
        {
            return;
        }
        this.lastFireTick = tick;
        this.lastFireWallclockMs = now;

        /* === User-facing diagnostic dump ===
         *
         * Logs every clip setting that affects the destruction visual
         * so we can verify whether the user's editor configuration
         * matches their intent. Fires once per actual fire (after the
         * throttle gate). Logs to BOTH the bbstools/fracture logger
         * AND the in-game chat as §6[BBSTools-debug] so the user can
         * verify mid-game without alt-tabbing. */
        String dump = String.format(
            "tick=%d  blockMode=%s  destructionMode=%s  destroyBlocks=%s  chunksPerBlock=%d  scatterForce=%.2f  upwardForce=%.2f  directionForce=%.2f  dir=(%.2f,%.2f,%.2f)  scatterJitter=%.2f  gravityBias=%.2f  propagationSpeed=%d  tickInterval=%d  maxRadius=%d  physicsScale=%.2f  debrisLifetime=%d  noInterCollision=%s  region=(%d,%d,%d)→(%d,%d,%d)  impact=(%d,%d,%d)  impactReplay='%s'",
            tick,
            this.blockMode.get(),
            this.destructionMode.get(),
            this.destroyBlocks.get(),
            this.chunksPerBlock.get(),
            this.scatterForce.get(),
            this.upwardForce.get(),
            this.directionForce.get(),
            this.directionX.get(), this.directionY.get(), this.directionZ.get(),
            this.scatterJitter.get(),
            this.gravityBias.get(),
            this.propagationSpeed.get(),
            this.tickInterval.get(),
            this.maxRadius.get(),
            this.physicsScale.get(),
            this.debrisLifetime.get(),
            this.noInterCollision.get(),
            Math.min(this.minX.get(), this.maxX.get()),
            Math.min(this.minY.get(), this.maxY.get()),
            Math.min(this.minZ.get(), this.maxZ.get()),
            Math.max(this.minX.get(), this.maxX.get()),
            Math.max(this.minY.get(), this.maxY.get()),
            Math.max(this.minZ.get(), this.maxZ.get()),
            this.impactX.get(), this.impactY.get(), this.impactZ.get(),
            this.impactReplayName.get());

        LOG.info("[block-destruction] FIRE: {}", dump);

        if (com.afrobama.bbstools.perf.BBSPerfConfig.VERBOSE_DESTRUCTION_CHAT)
        {
            net.minecraft.client.Minecraft mcDbg = net.minecraft.client.Minecraft.getInstance();
            if (mcDbg != null && mcDbg.player != null)
            {
                mcDbg.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§6[BBSTools-debug] §fblockMode=§e" + this.blockMode.get()
                    + " §fchunks/blk=§e" + this.chunksPerBlock.get()
                    + " §fmode=§e" + this.destructionMode.get()
                    + " §fforce=§e" + String.format("%.1f", this.scatterForce.get())
                    + " §fdirF=§e" + String.format("%.1f", this.directionForce.get())
                    + " §fdestroy=§e" + this.destroyBlocks.get()));
            }
        }

        /* Body-count snapshot before the BFS starts. Used in the
         *  delayed delta log below to verify spawn-vs-blocks ratio. */
        final int bodiesBefore;
        try
        {
            net.minecraft.world.level.Level w = net.minecraft.client.Minecraft.getInstance().level;
            bodiesBefore = (w != null) ? FracturePhysicsBridge.snapshotBodyCount(w) : -1;
        }
        catch (Throwable t) { /* unreachable, but keep variable final */ throw new RuntimeException(t); }

        /* Schedule a one-shot post-fire body-count log ~2 seconds after
         *  the fire (= 40 client ticks). By then the BFS has finished
         *  and PhysicsMod has flushed any deferred-queue spawns. */
        com.afrobama.bbstools.client.fracture.BlockShatterPropagator.scheduleBodyCountReport(bodiesBefore, 40);

        /* Normalize the corners so min < max regardless of how the
         *  user typed them. */
        int x0 = Math.min(this.minX.get(), this.maxX.get());
        int y0 = Math.min(this.minY.get(), this.maxY.get());
        int z0 = Math.min(this.minZ.get(), this.maxZ.get());
        int x1 = Math.max(this.minX.get(), this.maxX.get());
        int y1 = Math.max(this.minY.get(), this.maxY.get());
        int z1 = Math.max(this.minZ.get(), this.maxZ.get());

        BlockPos rmin = new BlockPos(x0, y0, z0);
        BlockPos rmax = new BlockPos(x1, y1, z1);

        /* If a previous run of this clip destroyed blocks, restore
         *  them before the new propagation starts. Lets the user
         *  re-take the same shot (loop, scrub-restart) without
         *  manually running /bbstools fracture-restore-blocks first. */
        com.afrobama.bbstools.client.fracture.BlockSnapshotRegistry.restoreAll();
        com.afrobama.bbstools.client.fracture.BlockShatterPropagator.clearAll();

        /* Impact point — either from the static XYZ fields, or, if the
         *  user named an impact_replay, read that replay's live X/Y/Z
         *  keyframes at the trigger tick. Replay-driven mode lets the
         *  user place + animate a marker entity in the world and have
         *  the destruction follow it. */
        BlockPos impact = resolveImpactPoint(film, replay, tick);

        LOG.info("[block-destruction] resolved impact point: {} (replay-driven: {})",
            impact, !this.impactReplayName.get().isEmpty());

        /* Sanity check — when min == max == impact, the user has a
         *  default-initialised clip and forgot to mark a region.
         *  Log a clear hint to chat so they know what to do. */
        if (x0 == x1 && y0 == y1 && z0 == z1)
        {
            long nowWarn = System.currentTimeMillis();
            if (nowWarn - lastEmptyRegionWarnMs > 5000L)
            {
                lastEmptyRegionWarnMs = nowWarn;
                LOG.warn("[block-destruction] region is a single block at ({},{},{}) — did you forget to set Min/Max corners? Use the 'Set from looked-at' buttons in the clip editor.",
                    x0, y0, z0);
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                if (mc != null && mc.player != null)
                {
                    mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§e[BBSTools] §cBlock-destruction clip fired but region is empty — "
                        + "use 'Set Min/Max from looked-at' in the clip editor to mark a region. §7(throttled: once per 5s)"));
                }
            }
            return;
        }

        /* Impact direction = from region centre to impact point —
         *  gives debris a slight outward bias along the "force axis"
         *  the user implies by where they placed impact relative to
         *  region centre. If impact is dead-centre, dir is zero and
         *  scatter is purely radial. */
        Vector3f impactDir = new Vector3f(
            impact.getX() - (x0 + x1) * 0.5F,
            impact.getY() - (y0 + y1) * 0.5F,
            impact.getZ() - (z0 + z1) * 0.5F);
        float len = impactDir.length();
        if (len > 1e-4F) impactDir.div(len);

        /* Resolve the direction vector → unit direction × directionForce
         *  magnitude. Treat (directionX, Y, Z) as the AXIS and let the
         *  user dial intensity with a single force slider. */
        float dx = this.directionX.get();
        float dy = this.directionY.get();
        float dz = this.directionZ.get();
        float dlen = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float force = this.directionForce.get();
        float fx = 0F, fy = 0F, fz = 0F;
        if (dlen > 1e-4F && force > 0F)
        {
            fx = (dx / dlen) * force;
            fy = (dy / dlen) * force;
            fz = (dz / dlen) * force;
        }
        else if (force == 0F)
        {
            /* Backwards compat: if user hasn't set directionForce, fall
             *  back to using the raw direction values directly so old
             *  saved clips keep their behavior. */
            fx = dx; fy = dy; fz = dz;
        }

        BlockShatterPropagator.triggerInRegion(
            impact, impactDir,
            this.maxRadius.get(),
            this.propagationSpeed.get(),
            this.chunksPerBlock.get(),
            this.destroyBlocks.get(),
            rmin, rmax,
            this.scatterForce.get(),
            this.upwardForce.get(),
            fx, fy, fz,
            this.scatterJitter.get(),
            this.gravityBias.get(),
            this.tickInterval.get(),
            this.destructionMode.get(),
            this.blockMode.get(),
            this.physicsScale.get(),
            this.debrisLifetime.get(),
            this.noInterCollision.get());

        LOG.info("[block-destruction] fired at tick {} — impact {} region {} → {} (force {})",
            tick, impact, rmin, rmax, this.scatterForce.get());
    }

    /**
     * Resolve the impact point — replay-driven if {@code impactReplayName}
     * names an existing replay in this film, otherwise the static
     * {@code impactX/Y/Z} fields.
     */
    private BlockPos resolveImpactPoint(Film film, Replay clipReplay, int tick)
    {
        String name = this.impactReplayName.get();
        if (name == null || name.isEmpty() || film == null)
        {
            return new BlockPos(this.impactX.get(), this.impactY.get(), this.impactZ.get());
        }

        try
        {
            for (Replay r : film.replays.getList())
            {
                if (r == null) continue;
                String rn;
                try { rn = r.getName(); } catch (Throwable t) { rn = null; }
                if (rn == null || !rn.equalsIgnoreCase(name)) continue;

                double x = r.keyframes.x.interpolate(tick);
                double y = r.keyframes.y.interpolate(tick);
                double z = r.keyframes.z.interpolate(tick);
                return new BlockPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
            }
            LOG.warn("[block-destruction] impact_replay_name '{}' not found among film replays — falling back to static XYZ", name);
        }
        catch (Throwable t)
        {
            LOG.debug("[block-destruction] resolveImpactPoint failed: {}", t.toString());
        }
        return new BlockPos(this.impactX.get(), this.impactY.get(), this.impactZ.get());
    }

    @Override
    protected Clip create()
    {
        return new PhysicsBlockDestructionClientClip();
    }

}
