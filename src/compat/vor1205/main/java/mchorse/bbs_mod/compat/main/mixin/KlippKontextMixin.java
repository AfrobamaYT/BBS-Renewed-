package mchorse.bbs_mod.compat.main.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * vor1205: ClipContext ohne Entitaet. 1.20.1 kennt nur den Konstruktor mit Entity und baut
 * daraus CollisionContext.of(entity) -- mit null eine NPE. Hier wird null zu empty() wie ab
 * 1.20.2 (dort gibt es den Konstruktor mit CollisionContext).
 */
@Mixin(ClipContext.class)
public abstract class KlippKontextMixin
{
    @Redirect(method = "<init>(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/level/ClipContext$Block;Lnet/minecraft/world/level/ClipContext$Fluid;Lnet/minecraft/world/entity/Entity;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/shapes/CollisionContext;of(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/world/phys/shapes/CollisionContext;"))
    private static CollisionContext bbs$ohneEntitaet(Entity entitaet)
    {
        return entitaet == null ? CollisionContext.empty() : CollisionContext.of(entitaet);
    }
}
