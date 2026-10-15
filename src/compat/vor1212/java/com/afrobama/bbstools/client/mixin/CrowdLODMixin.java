package com.afrobama.bbstools.client.mixin;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/* Vor 1.21.2 zeichnet LivingEntityRenderer#render die Entitaet direkt, ohne Renderzustand. */
@Mixin(LivingEntityRenderer.class)
public abstract class CrowdLODMixin
{
    @Shadow protected EntityModel<?> model;

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"), cancellable = true)
    private void bbs$crowdLODCullAndHide(LivingEntity lebendig, float gier, float teilbild, PoseStack matrices, MultiBufferSource puffer, int licht, CallbackInfo ci)
    {
        if (MixinBruecke.rufeUndAbbrechen("CrowdLodLogik", "crowdLODCullAndHide", lebendig, this.model))
        {
            ci.cancel();
        }
    }

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("TAIL"))
    private void bbs$crowdLODRestore(LivingEntity lebendig, float gier, float teilbild, PoseStack matrices, MultiBufferSource puffer, int licht, CallbackInfo ci)
    {
        MixinBruecke.rufe("CrowdLodLogik", "crowdLODRestore");
    }
}
