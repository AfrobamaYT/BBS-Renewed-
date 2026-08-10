package com.afrobama.bbstools.client.mixin.physicsmod;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import net.diebuddies.config.ConfigMobs;
import net.diebuddies.physics.settings.mobs.MobSetting;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Anti-poof: PhysicsMod's MixinLivingEntity reads MobSetting.getType()
 * on death and routes the entity into BreakableRagdoll when the type
 * is anything but OFF. For BBS ActorEntity that path produces a
 * particle "poof" because the model is null. We force OFF for actors
 * so PhysicsMod skips them entirely and BBS's own verlet ragdoll
 * system handles the visual.
 *
 * <p>Loaded only when PhysicsMod is present — see
 * {@link BBSPhysicsModMixinPlugin}.</p>
 */
@Mixin(value = ConfigMobs.class, remap = false)
public abstract class MixinConfigMobs
{
    @Inject(method = "getMobSetting(Lnet/minecraft/class_1297;)Lnet/diebuddies/physics/settings/mobs/MobSetting;",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void bbstools$skipActorEntity(Entity entity, CallbackInfoReturnable<MobSetting> cir)
    {
        MobSetting erzwungen = (MobSetting) MixinBruecke.rufe("PhysikModLogik", "einstellungFuer", entity);

        if (erzwungen != null)
        {
            cir.setReturnValue(erzwungen);
        }
    }

}
