package mchorse.bbs_mod.mixin.client;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Before 26.1 (replaces the shared mixin on 26.2's prepareModel): the model pose is set up in
 * renderModel(ModelSubmit, ...). Same bridge calls, right after setupAnim and at the end.
 */
@Mixin(ModelFeatureRenderer.class)
public class ModelFeatureRendererMixin
{
    @Inject(method = "renderModel", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/model/Model;setupAnim(Ljava/lang/Object;)V", shift = At.Shift.AFTER))
    private void bbs$poseAuflegen(SubmitNodeStorage.ModelSubmit<?> auftrag, net.minecraft.client.renderer.rendertype.RenderType ebene,
        com.mojang.blaze3d.vertex.VertexConsumer verbraucher, net.minecraft.client.renderer.OutlineBufferSource umriss,
        net.minecraft.client.renderer.MultiBufferSource.BufferSource puffer, CallbackInfo info)
    {
        MixinBruecke.rufe("ModelFeatureRendererLogik", "auflegen", auftrag.state());
    }

    @Inject(method = "renderModel", at = @At("RETURN"))
    private void bbs$poseHerunternehmen(SubmitNodeStorage.ModelSubmit<?> auftrag, net.minecraft.client.renderer.rendertype.RenderType ebene,
        com.mojang.blaze3d.vertex.VertexConsumer verbraucher, net.minecraft.client.renderer.OutlineBufferSource umriss,
        net.minecraft.client.renderer.MultiBufferSource.BufferSource puffer, CallbackInfo info)
    {
        MixinBruecke.rufe("ModelFeatureRendererLogik", "zuruecksetzen");
    }
}
