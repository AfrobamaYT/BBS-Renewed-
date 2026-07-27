package com.afrobama.bbstools.client.mixin;

import com.afrobama.bbstools.client.blender.MixinBruecke;

import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Redirects every {@code this.getTexture(entity)} call inside
 * {@link LivingEntityRenderer#render}. When the entity has a per-UUID texture
 * override registered (via RagdollSwap or the Texture Staff), the override is
 * substituted; otherwise the renderer's own texture wins.
 *
 * Migrated from BBS-CML-EDITION fork's
 * mchorse.bbs_mod.mixin.client.LivingEntityRendererTextureMixin.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererTextureMixin
{
    @Redirect(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/render/entity/LivingEntityRenderer;getTexture(Lnet/minecraft/entity/Entity;)Lnet/minecraft/util/Identifier;"),
        require = 0)
    private Identifier bbs$redirectGetTexture(LivingEntityRenderer<?, ?> self, Entity entity)
    {
        /* BRÜCKEN-MUSTER (2026-07-26): dieser handler wird beim klassenladen in
         * LivingEntityRenderer gewoben und liegt damit im KnotClassLoader — vom kernel-swap
         * unerreichbar. Die override-auswahl liegt jetzt in
         * `com.afrobama.blendertransform.LivingEntityTextureLogik` und wird bei jedem swap
         * mitgetauscht. */
        @SuppressWarnings({"unchecked", "rawtypes"})
        Identifier vanilla = ((LivingEntityRenderer) self).getTexture((LivingEntity) entity);

        Object ergebnis = MixinBruecke.rufe("LivingEntityTextureLogik", "wähleTextur", entity, vanilla);

        return ergebnis instanceof Identifier ? (Identifier) ergebnis : vanilla;
    }

}
