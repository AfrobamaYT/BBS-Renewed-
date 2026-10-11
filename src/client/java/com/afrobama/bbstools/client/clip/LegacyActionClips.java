package com.afrobama.bbstools.client.clip;

import com.afrobama.bbstools.client.fracture.ModelFractureClientClip;
import com.afrobama.bbstools.client.fracture.ModelRagdollProxyClientClip;
import com.afrobama.bbstools.client.fracture.PhysicsBlockDestructionClientClip;
import com.afrobama.bbstools.client.fracture.UIModelFractureClip;
import com.afrobama.bbstools.client.fracture.UIModelRagdollProxyClip;
import com.afrobama.bbstools.client.fracture.UIPhysicsBlockDestructionClip;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.film.clips.UIClip;
import mchorse.bbs_mod.utils.clips.Clip;

/** Distinct client classes preserve the legacy ids in the factory's inverse class map. */
public final class LegacyActionClips
{
    public static void register()
    {
        register("model_fracture", Fracture.class);
        register("block_destruction", Destruction.class);
        register("ragdoll_proxy", Ragdoll.class);
        register("block_animation", Blocks.class);
        UIClip.register(Fracture.class, UIModelFractureClip::new);
        UIClip.register(Destruction.class, UIPhysicsBlockDestructionClip::new);
        UIClip.register(Ragdoll.class, UIModelRagdollProxyClip::new);
        UIClip.register(Blocks.class, UIBlockAnimationClip::new);
        UIClip.register(BlockAnimationClientClip.class, UIBlockAnimationClip::new);
    }

    private static void register(String name, Class<? extends Clip> type)
    {
        Link id = Link.create("bbstools:" + name);
        BBSMod.getFactoryActionClips().register(id, type, BBSMod.getFactoryActionClips().getData(id));
    }

    public static class Fracture extends ModelFractureClientClip
    {
        @Override
        protected Clip create()
        {
            return new Fracture();
        }
    }

    public static class Destruction extends PhysicsBlockDestructionClientClip
    {
        @Override
        protected Clip create()
        {
            return new Destruction();
        }
    }

    public static class Ragdoll extends ModelRagdollProxyClientClip
    {
        @Override
        protected Clip create()
        {
            return new Ragdoll();
        }
    }

    public static class Blocks extends BlockAnimationClientClip
    {
        @Override
        protected Clip create()
        {
            return new Blocks();
        }
    }

    private LegacyActionClips()
    {}
}
