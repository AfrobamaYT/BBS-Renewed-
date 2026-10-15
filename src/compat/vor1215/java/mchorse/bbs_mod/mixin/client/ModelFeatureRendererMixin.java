package mchorse.bbs_mod.mixin.client;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vor 1.21.5 (ersetzt die vor261-Fassung auf ModelFeatureRenderer.renderModel): 1.21.4 stellt
 * das Modell in LivingEntityRenderer.render ein. Dieselben Brueckenaufrufe direkt nach
 * setupAnim und am Ende.
 */
@Mixin(LivingEntityRenderer.class)
public class ModelFeatureRendererMixin
{
    private static final String RENDER = "render(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V";

    @Inject(method = RENDER, at = @At(value = "INVOKE", shift = At.Shift.AFTER,
        target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;)V"))
    private void bbs$poseAuflegen(LivingEntityRenderState zustand, PoseStack pose, MultiBufferSource puffer, int licht, CallbackInfo info)
    {
        MixinBruecke.rufe("ModelFeatureRendererLogik", "auflegen", zustand);
    }

    @Inject(method = RENDER, at = @At("RETURN"))
    private void bbs$poseHerunternehmen(LivingEntityRenderState zustand, PoseStack pose, MultiBufferSource puffer, int licht, CallbackInfo info)
    {
        MixinBruecke.rufe("ModelFeatureRendererLogik", "zuruecksetzen");
    }
}
