// Coded by Afrobama/Claude
package com.afrobama.bbstools.client.mixin.physicsmod;


import com.afrobama.bbstools.client.blender.MixinBruecke;
import net.diebuddies.physics.BoxRigidBody;
import net.diebuddies.physics.PhysicsEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Same role as {@link ConvexRigidBodyMaskMixin} — applies our
 * thread-local {@code physicsMask} override at body construction.
 * BoxRigidBody is the dispatch path for some PhysicsMod debris types
 * (notably BLOCKY mode for AABB-shaped blocks).
 */
@Mixin(BoxRigidBody.class)
public abstract class BoxRigidBodyMaskMixin
{
    @Inject(method = "create(Lnet/diebuddies/physics/PhysicsEntity;Z)Lnet/diebuddies/physics/BoxRigidBody;",
            at = @At("HEAD"),
            require = 0)
    private static void bbstools$applyMaskOverride(PhysicsEntity entity, boolean kinematic,
                                                    CallbackInfoReturnable<BoxRigidBody> cir)
    {
        MixinBruecke.rufe("PhysikModLogik", "maskeSetzen", entity);
    }

}
