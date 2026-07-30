// Coded by Afrobama/Claude — Phase 17: skip mob renders during editor mode
package com.afrobama.bbstools.client.mixin;

import com.afrobama.bbstools.editormode.EditorMode;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
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
        method = "render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void bbstools$skipMobRenderInEditorMode(Entity entity, double x, double y, double z,
                                                     float yaw, float tickDelta,
                                                     PoseStack matrices,
                                                     SubmitNodeCollector vertexConsumers,
                                                     int light, CallbackInfo ci)
    {
        if (!(entity instanceof Mob mob)) return;
        if (EditorMode.shouldBypass(mob))
        {
            ci.cancel();
        }
    }

}
