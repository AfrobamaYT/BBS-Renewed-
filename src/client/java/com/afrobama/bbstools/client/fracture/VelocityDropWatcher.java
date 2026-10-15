package com.afrobama.bbstools.client.fracture;

import com.afrobama.bbstools.fracture.ModelFractureActionClip;

import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;

import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Velocity-drop fracture trigger (Phase 6 alternative). Watches the
 * actor's keyframe-derived velocity each tick. When the magnitude of
 * the velocity falls more than {@code velocityDropThreshold} between
 * consecutive ticks, fires a whole-model fracture.
 *
 * <p>Cheaper than {@link CollisionWatcher} (no per-bone AABB tests)
 * but coarser — fires for every sudden deceleration, not just bone-on-
 * block contact. Useful for shots where the actor "slams to a stop"
 * via a hand-keyframed velocity drop and you want fracture to coincide
 * automatically.</p>
 */
public final class VelocityDropWatcher
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/fracture/velodrop");
    private static final List<Watcher> ACTIVE = new ArrayList<>();

    private static final class Watcher
    {
        final ModelFractureActionClip clip;
        final IEntity actor;
        final Replay replay;
        final int startTick;
        final int endTickInclusive;
        float prevSpeedSquared = -1F;
        boolean fired;

        Watcher(ModelFractureActionClip clip, IEntity actor, Replay replay, int startTick, int endTickInclusive)
        {
            this.clip = clip;
            this.actor = actor;
            this.replay = replay;
            this.startTick = startTick;
            this.endTickInclusive = endTickInclusive;
        }
    }

    private VelocityDropWatcher()
    {
    }

    public static synchronized void startWatching(ModelFractureActionClip clip,
                                                  IEntity actor, Replay replay, int tick)
    {
        if (clip == null || actor == null || replay == null) return;
        int duration = clip.duration.get();
        int endTick = (duration > 0) ? tick + duration : tick + 600;
        ACTIVE.add(new Watcher(clip, actor, replay, tick, endTick));
        LOG.info("VelocityDropWatcher started: tick {} → {}", tick, endTick);
    }

    public static synchronized void onClientTick(int filmTick)
    {
        if (ACTIVE.isEmpty()) return;

        Iterator<Watcher> it = ACTIVE.iterator();
        while (it.hasNext())
        {
            Watcher w = it.next();

            if (w.fired || filmTick > w.endTickInclusive)
            {
                it.remove();
                continue;
            }

            try
            {
                Vector3f vel = computeActorVelocity(w.replay, filmTick);
                float speedSq = vel.lengthSquared();

                if (w.prevSpeedSquared >= 0F)
                {
                    float drop = w.prevSpeedSquared - speedSq;
                    float threshold = w.clip.velocityDropThreshold.get();

                    /* Convert threshold (blocks/tick) to threshold²
                     *  for direct comparison. */
                    float thresholdSq = threshold * threshold;

                    if (drop > thresholdSq)
                    {
                        triggerFracture(w, filmTick);
                        w.fired = true;
                        it.remove();
                        continue;
                    }
                }

                w.prevSpeedSquared = speedSq;
            }
            catch (Throwable t)
            {
                LOG.warn("VelocityDropWatcher tick failed, dropping: {}", t.toString());
                it.remove();
            }
        }
    }

    private static void triggerFracture(Watcher w, int filmTick)
    {
        double actorX = w.replay.keyframes.x.interpolate(filmTick);
        double actorY = w.replay.keyframes.y.interpolate(filmTick);
        double actorZ = w.replay.keyframes.z.interpolate(filmTick);

        Vector3f vel = computeActorVelocity(w.replay, filmTick);
        Vector3f spawnVel = w.clip.inheritVelocity.get() ? vel : null;

        java.util.Set<String> allowed = (w.clip.groupName.get() != null && !w.clip.groupName.get().isEmpty())
            ? FractureGroupConfig.lookupGroup(w.actor, w.clip.groupName.get())
            : null;

        int lifetimeTicks = w.clip.lifetime.get() * 20;
        FractureSpawner.Result result = FractureSpawner.fracture(
            w.actor, actorX, actorY, actorZ, spawnVel,
            w.clip.forceJitter.get(), allowed, filmTick, lifetimeTicks, w.clip);

        if (result.isEmpty()) return;

        /* Phase B: don't hide bones — the matrix-override mixin handles
         *  the visual side. Just track for rewind cleanup. */
        FractureRegistry.register(w.actor, result.spawnedBodies, w.startTick, lifetimeTicks);
        LOG.info("Velocity-drop fracture triggered at film tick {}: {} pieces",
            filmTick, result.spawnedBodies.size());
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

    public static synchronized void clearAll()
    {
        ACTIVE.clear();
    }

    public static synchronized int activeWatcherCount()
    {
        return ACTIVE.size();
    }

}
