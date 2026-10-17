package com.afrobama.bbstools.client.clips;

import com.afrobama.bbstools.client.clip.BlockAnimationClientClip;
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
import net.fabricmc.api.ClientModInitializer;

/** Old saved IDs retain distinct class identities while using the implemented client actions. */
public final class LegacyActionClients implements ClientModInitializer
{
    @Override
    public void onInitializeClient()
    {
        register("model_fracture", Fracture.class);
        register("block_destruction", Destruction.class);
        register("ragdoll_proxy", Ragdoll.class);
        register("block_animation", BlockAnimation.class);
        UIClip.register(Fracture.class, UIModelFractureClip::new);
        UIClip.register(Destruction.class, UIPhysicsBlockDestructionClip::new);
        UIClip.register(Ragdoll.class, UIModelRagdollProxyClip::new);
        UIClip.register(BlockAnimation.class, UIBlockAnimationClip::new);
        UIClip.register(BlockAnimationClientClip.class, UIBlockAnimationClip::new);
    }

    private static void register(String path, Class<? extends Clip> implementation)
    {
        Link id = Link.create("bbstools:" + path);
        if (!BBSMod.getFactoryActionClips().getKeys().contains(id))
            throw new IllegalStateException("Missing common action registration: " + id);
        BBSMod.getFactoryActionClips().register(id, implementation, BBSMod.getFactoryActionClips().getData(id));
    }

    public static final class Fracture extends ModelFractureClientClip
    {
        @Override
        protected Clip create()
        {
            return new Fracture();
        }
    }

    public static final class Destruction extends PhysicsBlockDestructionClientClip
    {
        @Override
        protected Clip create()
        {
            return new Destruction();
        }
    }

    public static final class Ragdoll extends ModelRagdollProxyClientClip
    {
        @Override
        protected Clip create()
        {
            return new Ragdoll();
        }
    }

    public static final class BlockAnimation extends BlockAnimationClientClip
    {
        @Override
        protected Clip create()
        {
            return new BlockAnimation();
        }
    }
}
