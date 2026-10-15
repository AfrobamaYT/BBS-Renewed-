package mchorse.bbs_mod.mixin.client;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import com.mojang.blaze3d.vertex.PoseStack;
import mchorse.bbs_mod.compat.alt.Zustaende;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vor 1.21.2 (ersetzt die vor1215-Fassung): dieselben Aufrufe an 1.21.1s render(Entitaet, ...),
 * mit einem Zustand aus der Entitaet; dazu die Modellpose (ModelFeatureRendererLogik) nach setupAnim.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin
{
    private static final String RENDER = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V";

    @Inject(method = RENDER, at = @At("HEAD"))
    public void bbs$poseAnDenZustand(LivingEntity entitaet, float gierung, float teilTick, PoseStack matrixStack, MultiBufferSource puffer, int licht, CallbackInfo info)
    {
        var zustand = Zustaende.fuer(entitaet, teilTick, licht);

        MixinBruecke.rufe("EntitaetVersandLogik", "merken", zustand, entitaet);
        MixinBruecke.rufe("LivingEntityPoseLogik", "stempeln", zustand);
        this.bbs$zustand = zustand;
    }

    @Inject(method = RENDER, at = @At(value = "INVOKE", ordinal = 0, shift = At.Shift.AFTER,
        target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V"))
    public void onSetAngles(LivingEntity entitaet, float gierung, float teilTick, PoseStack matrixStack, MultiBufferSource puffer, int licht, CallbackInfo info)
    {
        MixinBruecke.rufe("LivingEntityPoseLogik", "auflegen", this.bbs$zustand);
        MixinBruecke.rufe("ModelFeatureRendererLogik", "auflegen", this.bbs$zustand);
    }

    @Inject(method = RENDER, at = @At("TAIL"))
    public void onRenderEnd(LivingEntity entitaet, float gierung, float teilTick, PoseStack matrixStack, MultiBufferSource puffer, int licht, CallbackInfo info)
    {
        MixinBruecke.rufe("ModelFeatureRendererLogik", "zuruecksetzen");
        MixinBruecke.rufe("LivingEntityPoseLogik", "zuruecksetzen");
        this.bbs$zustand = null;
    }

    @org.spongepowered.asm.mixin.Unique
    private Object bbs$zustand;
}
