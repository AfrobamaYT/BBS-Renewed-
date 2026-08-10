// Coded by Afrobama/Claude
package com.afrobama.bbstools.client.mixin.physicsmod;


import com.afrobama.bbstools.client.blender.MixinBruecke;
import net.diebuddies.physics.ConvexRigidBody;
import net.diebuddies.physics.PhysicsEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Override {@code entity.physicsMask} at the HEAD of
 * {@link ConvexRigidBody#create(PhysicsEntity, boolean)} when our
 * thread-local override is set, so PhysicsMod's PhysX-actor filter
 * setup picks up our chosen mask.
 *
 * <p>Used by the block-destruction clip's {@code noInterCollision}
 * toggle: the post-spawn applyNoCollisionMask path writes too late
 * (PhysX has already baked the filter from the original mask).</p>
 *
 * <p>Targets ConvexRigidBody because it's the dispatch path for
 * BLOCKY/FRACTURED debris created via PhysicsMod's renderBlockIntoEntity
 * → addBlockParticle pipeline that BBS's spawnPhysicsModFracture
 * uses.</p>
 */
@Mixin(ConvexRigidBody.class)
public abstract class ConvexRigidBodyMaskMixin
{
    @Inject(method = "create(Lnet/diebuddies/physics/PhysicsEntity;Z)Lnet/diebuddies/physics/ConvexRigidBody;",
            at = @At("HEAD"),
            require = 0)
    private static void bbstools$applyMaskOverride(PhysicsEntity entity, boolean kinematic,
                                                    CallbackInfoReturnable<ConvexRigidBody> cir)
    {
        MixinBruecke.rufe("PhysikModLogik", "maskeSetzen", entity);
    }

}
