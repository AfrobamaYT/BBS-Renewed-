package com.afrobama.bbstools.client.mixin;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Vor 1.21.2 (ersetzt die gemeinsame Fassung): getRenderType fragt die Textur direkt mit der
 * Entitaet ab, kein Render-Zustand. Dieselbe Bruecke (LivingEntityTextureLogik).
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererTextureMixin
{
    @Redirect(method = "getRenderType", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;getTextureLocation(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/resources/ResourceLocation;"))
    @SuppressWarnings({"unchecked", "rawtypes"})
    private ResourceLocation bbs$redirectGetTexture(LivingEntityRenderer self, Entity entity)
    {
        ResourceLocation vanilla = self.getTextureLocation(entity);

        if (entity == null)
        {
            return vanilla;
        }

        Object ergebnis = MixinBruecke.rufe("LivingEntityTextureLogik", "wähleTextur", entity, vanilla);

        return ergebnis instanceof ResourceLocation ? (ResourceLocation) ergebnis : vanilla;
    }
}
