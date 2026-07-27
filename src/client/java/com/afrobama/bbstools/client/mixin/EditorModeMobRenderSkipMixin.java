// Coded by Afrobama/Claude — Phase 17: skip mob renders during editor mode
package com.afrobama.bbstools.client.mixin;

import com.afrobama.bbstools.editormode.EditorMode;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Companion to {@code EditorModeMobTickSkipMixin} on the client side.
 * Skips the vanilla mob renderer entirely when editor mode is on —
 * villagers + zombies + the rest don't take a draw call. BBS replays
 * are unaffected (they use BBS's form pipeline, not the vanilla entity
 * dispatcher).
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EditorModeMobRenderSkipMixin
{
    @Inject(
        method = "render(Lnet/minecraft/entity/Entity;DDDFFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void bbstools$skipMobRenderInEditorMode(Entity entity, double x, double y, double z,
                                                     float yaw, float tickDelta,
                                                     MatrixStack matrices,
                                                     VertexConsumerProvider vertexConsumers,
                                                     int light, CallbackInfo ci)
    {
        if (!(entity instanceof MobEntity mob)) return;
        if (EditorMode.shouldBypass(mob))
        {
            ci.cancel();
        }
    }

}
