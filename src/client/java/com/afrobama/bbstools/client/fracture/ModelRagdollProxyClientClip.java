package com.afrobama.bbstools.client.fracture;

import com.afrobama.bbstools.client.ragdoll.ActorRagdollProxy;
import com.afrobama.bbstools.fracture.ModelRagdollProxyActionClip;

import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.utils.clips.Clip;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client subclass of {@link ModelRagdollProxyActionClip}. On clip
 * start, calls {@link ActorRagdollProxy#spawnFor(IEntity)} which
 * spawns + kills an invisible Villager at the actor's position and
 * triggers PhysicsMod's ragdoll-creation pathway.
 */
public class ModelRagdollProxyClientClip extends ModelRagdollProxyActionClip
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/ragdoll/clip");

    @Override
    protected void applyClientAction(IEntity entity, Film film, Replay replay, int tick)
    {
        if (entity == null)
        {
            LOG.warn("[ragdoll-clip] entity is null — skipping");
            return;
        }

        boolean spawned = ActorRagdollProxy.spawnFor(entity);
        LOG.info("[ragdoll-clip] tick {} — proxy spawn for {}: {}",
            tick, entity.getClass().getSimpleName(), spawned ? "ok" : "failed");
    }

    @Override
    protected Clip create()
    {
        return new ModelRagdollProxyClientClip();
    }

}
