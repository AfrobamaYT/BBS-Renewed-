package com.afrobama.bbstools.fracture;

import mchorse.bbs_mod.actions.types.ActionClip;
import mchorse.bbs_mod.utils.clips.Clip;

/**
 * Action clip that triggers a PhysicsMod entity-proxy ragdoll on a
 * BBS actor. On clip start, an invisible Villager is spawned at the
 * actor's world position and killed; PhysicsMod creates a ragdoll
 * for it; our hook captures the ragdoll; the actor's BOBJ bone
 * matrices are then overridden each render frame from the ragdoll's
 * PhysicsEntity body transforms.
 *
 * <p>Texture and body parts of the BBS actor are preserved — only
 * the BONE TRANSFORMS come from PhysicsMod. The villager itself is
 * invisible, so visually you only see the BBS actor falling/tumbling
 * with PhysX-driven ragdoll motion.</p>
 *
 * <p><b>Bone mapping</b> uses default villager → Steve-rig aliases
 * (head, body, arm_left, arm_right, leg_left, leg_right). If your
 * BOBJ uses different names, the override silently skips bones it
 * can't match.</p>
 *
 * <p><b>Singleplayer only</b>. Multiplayer servers reject client-
 * spawned entities so the proxy spawn fails silently and the actor
 * stays intact (no error, just no ragdoll).</p>
 */
public class ModelRagdollProxyActionClip extends ActionClip
{
    public ModelRagdollProxyActionClip()
    {
        super();
    }

    @Override
    public boolean isClient()
    {
        return true;
    }

    @Override
    protected Clip create()
    {
        return new ModelRagdollProxyActionClip();
    }

}
