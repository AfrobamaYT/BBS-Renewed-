package mchorse.bbs_mod.mixin.client;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import mchorse.bbs_mod.compat.gpu.SofortSammler;
import mchorse.bbs_mod.compat.gpu.ZustandsLicht;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Vor 1.21.5 (ersetzt den gemeinsamen Haken): 1.21.4 sammelt und zeichnet in einem Zug im
 * privaten render(...). Beide Haelften des gemeinsamen Hakens sitzen dort: createRenderState
 * (merken) und EntityRenderer.render (gezeichnet), mit dem Licht als ZustandsLicht.
 */
@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin
{
    private static final String RENDER = "render(Lnet/minecraft/world/entity/Entity;DDDFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/renderer/entity/EntityRenderer;)V";

    @WrapOperation(method = RENDER, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;createRenderState(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;"))
    private EntityRenderState bbs$merkeEntity(EntityRenderer<?, ?> renderer, Entity entity, float teilTick, Operation<EntityRenderState> original)
    {
        EntityRenderState zustand = original.call(renderer, entity, teilTick);

        MixinBruecke.rufe("EntitaetVersandLogik", "merken", zustand, entity);

        return zustand;
    }

    @WrapOperation(method = RENDER, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private void bbs$umschliesseRender(EntityRenderer<?, ?> renderer, EntityRenderState zustand, PoseStack matrices, MultiBufferSource puffer, int licht, Operation<Void> original)
    {
        ZustandsLicht.aktuell = licht;

        if (!MixinBruecke.rufeUndAbbrechen("EntitaetVersandLogik", "gezeichnet", zustand, matrices, new SofortSammler(puffer)))
        {
            original.call(renderer, zustand, matrices, puffer, licht);
        }
    }
}
