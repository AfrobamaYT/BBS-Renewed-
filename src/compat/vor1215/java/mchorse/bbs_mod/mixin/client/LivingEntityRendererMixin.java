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

/** Vor 1.21.5 (ersetzt den gemeinsamen Haken auf submit): dieselben Aufrufe an 1.21.4s render. */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin
{
    private static final String RENDER = "render(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V";

    @Inject(method = RENDER, at = @At("HEAD"))
    public void bbs$poseAnDenZustand(LivingEntityRenderState zustand, PoseStack matrixStack, MultiBufferSource puffer, int licht, CallbackInfo info)
    {
        MixinBruecke.rufe("LivingEntityPoseLogik", "stempeln", zustand);
    }

    @Inject(method = RENDER, at = @At(value = "INVOKE", ordinal = 0, shift = At.Shift.AFTER,
        target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;)V"))
    public void onSetAngles(LivingEntityRenderState zustand, PoseStack matrixStack, MultiBufferSource puffer, int licht, CallbackInfo info)
    {
        MixinBruecke.rufe("LivingEntityPoseLogik", "auflegen", zustand);
    }

    @Inject(method = RENDER, at = @At("TAIL"))
    public void onRenderEnd(LivingEntityRenderState zustand, PoseStack matrixStack, MultiBufferSource puffer, int licht, CallbackInfo info)
    {
        MixinBruecke.rufe("LivingEntityPoseLogik", "zuruecksetzen");
    }
}
