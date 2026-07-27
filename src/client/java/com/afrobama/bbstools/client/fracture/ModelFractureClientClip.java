package com.afrobama.bbstools.client.fracture;

import com.afrobama.bbstools.fracture.ModelFractureActionClip;

import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.utils.clips.Clip;

import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Client-side companion of {@link ModelFractureActionClip}. The action
 * clip's data fields (group name, trigger mode, force jitter, etc.) are
 * inherited from the parent; we override
 * {@link #applyClientAction(IEntity, Film, Replay, int)} to actually
 * spawn the fracture pieces via {@link FractureSpawner} and register
 * the result in {@link FractureRegistry} for lifecycle management.
 *
 * <p><b>Manual mode</b> (default): spawns the fracture once at the
 * clip's start tick. All eligible bones become physics objects in one
 * frame.</p>
 *
 * <p><b>on_collision mode</b> (Phase 6): hands off to
 * {@link CollisionWatcher} which polls per-bone AABB intersections
 * with world blocks each tick from clip start until clip end. Bones
 * fracture individually as they collide. (NOTE: stub — pending
 * Phase-6 implementation.)</p>
 *
 * <p><b>on_velocity_drop mode</b>: similar to on_collision but watches
 * actor velocity for sudden drops (which happens when the actor crashes
 * into something keyframe-driven). Slower than the per-bone collision
 * approach but cheaper. (NOTE: stub — pending Phase-6 implementation.)</p>
 */
public class ModelFractureClientClip extends ModelFractureActionClip
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/fracture/clip");

    public ModelFractureClientClip()
    {
        super();
        LOG.debug("[FRACTURE-DIAG] ModelFractureClientClip constructed (instance={}, isClient={})",
            System.identityHashCode(this), this.isClient());
    }

    @Override
    protected void applyClientAction(IEntity entity, Film film, Replay replay, int tick)
    {
        LOG.debug("[FRACTURE-DIAG] applyClientAction FIRED at tick {} — entity={}, replay={}, mode={}",
            tick,
            entity == null ? "NULL" : entity.getClass().getSimpleName(),
            replay == null ? "NULL" : "ok",
            this.triggerMode.get());

        if (entity == null || replay == null)
        {
            LOG.warn("[FRACTURE-DIAG] applyClientAction aborted — entity or replay is null");
            return;
        }

        if (!FracturePhysicsBridge.isAvailable())
        {
            LOG.warn("[FRACTURE-DIAG] applyClientAction aborted — FracturePhysicsBridge not available");
            return;
        }

        String mode = this.triggerMode.get();
        if (mode == null) mode = TRIGGER_MANUAL;

        switch (mode)
        {
            case TRIGGER_ON_COLLISION:
                CollisionWatcher.startWatching(this, entity, replay, tick);
                LOG.debug("[FRACTURE-DIAG] Collision-driven fracture watcher started at tick {}", tick);
                break;

            case TRIGGER_ON_VELOCITY_DROP:
                VelocityDropWatcher.startWatching(this, entity, replay, tick);
                LOG.debug("[FRACTURE-DIAG] Velocity-drop fracture watcher started at tick {}", tick);
                break;

            case TRIGGER_MANUAL:
            default:
                LOG.debug("[FRACTURE-DIAG] Manual fracture starting at tick {}", tick);
                fractureWholeModel(entity, replay, tick);
                break;
        }
    }

    /**
     * Spawn fracture pieces for the entire model (or for the configured
     * fracture group) in a single shot. Used by manual trigger mode.
     */
    private void fractureWholeModel(IEntity actor, Replay replay, int tick)
    {
        double actorX = replay.keyframes.x.interpolate(tick);
        double actorY = replay.keyframes.y.interpolate(tick);
        double actorZ = replay.keyframes.z.interpolate(tick);

        LOG.debug("[FRACTURE-DIAG] fractureWholeModel: actor pos=({}, {}, {}), groupName='{}'",
            actorX, actorY, actorZ, this.groupName.get());

        Vector3f actorVel = null;
        if (this.inheritVelocity.get())
        {
            actorVel = computeActorVelocity(replay, tick);
            LOG.debug("[FRACTURE-DIAG] inherited velocity = ({}, {}, {})",
                actorVel.x, actorVel.y, actorVel.z);
        }

        Set<String> allowedBones = resolveAllowedBones(actor, this.groupName.get());
        LOG.debug("[FRACTURE-DIAG] allowedBones = {}",
            allowedBones == null ? "ALL" : allowedBones.toString());

        int lifetimeTicks = this.lifetime.get() * 20;

        FractureSpawner.Result result = FractureSpawner.fracture(
            actor, actorX, actorY, actorZ, actorVel,
            this.forceJitter.get(), allowedBones, tick, lifetimeTicks, this);

        LOG.debug("[FRACTURE-DIAG] FractureSpawner returned {} bone-bodies",
            result.spawnedBodies.size());

        if (result.isEmpty())
        {
            LOG.warn("[FRACTURE-DIAG] Fracture spawn produced 0 bodies — actor unchanged. " +
                "Check earlier log lines for [FRACTURE-DIAG] from BoneMeshExtractor / FractureSpawner.");
            return;
        }

        /* Phase B: do NOT mark bones as fractured in
         *  FracturedBoneRegistry — we WANT them to keep rendering
         *  (the BOBJArmatureFractureOverrideMixin will override their
         *  matrix each frame with the simulated body transform). */
        FractureRegistry.register(actor, result.spawnedBodies, tick, lifetimeTicks);
        LOG.debug("[FRACTURE-DIAG] Fracture committed: {} bone-bodies tracked", result.spawnedBodies.size());
    }

    /**
     * Compute the actor's instantaneous velocity in blocks/tick by
     * differentiating the x/y/z keyframe channels around the trigger
     * tick. Falls back to zero if interpolation fails.
     */
    private static Vector3f computeActorVelocity(Replay replay, int tick)
    {
        try
        {
            int prevTick = Math.max(0, tick - 1);
            double dx = replay.keyframes.x.interpolate(tick) - replay.keyframes.x.interpolate(prevTick);
            double dy = replay.keyframes.y.interpolate(tick) - replay.keyframes.y.interpolate(prevTick);
            double dz = replay.keyframes.z.interpolate(tick) - replay.keyframes.z.interpolate(prevTick);
            return new Vector3f((float) dx, (float) dy, (float) dz);
        }
        catch (Throwable t)
        {
            return new Vector3f(0F, 0F, 0F);
        }
    }

    /**
     * Resolve the configured group name to a set of bone names via
     * {@link FractureGroupConfig}. Empty group name → null (= all bones
     * fracture).
     */
    private static Set<String> resolveAllowedBones(IEntity actor, String groupName)
    {
        if (groupName == null || groupName.isEmpty()) return null;
        return FractureGroupConfig.lookupGroup(actor, groupName);
    }

    @Override
    protected Clip create()
    {
        return new ModelFractureClientClip();
    }

}
