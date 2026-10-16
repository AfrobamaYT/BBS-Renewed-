package mchorse.bbs_mod.mixin.client.vitrail;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import mchorse.bbs_mod.cubic.render.vao.IModelVAO;
import mchorse.bbs_mod.cubic.render.vao.ModelVAORenderer;
import mchorse.bbs_mod.graphics.texture.Texture;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelVAORenderer.class)
public abstract class BbsModelVaoCaptureMixin
{
    @Inject(method = "render(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lmchorse/bbs_mod/cubic/render/vao/IModelVAO;Lorg/joml/Matrix4f;Lorg/joml/Matrix3f;FFFFIIIILmchorse/bbs_mod/graphics/texture/Texture;F)V",
        at = @At("HEAD"), cancellable = true)
    private static void bbs$capture(RenderPipeline pipeline, IModelVAO vao, Matrix4f matrix, Matrix3f normal,
        float r, float g, float b, float a, int light, int overlay, int passMode, int target, Texture texture,
        float emission, CallbackInfo callback) throws Throwable
    {
        MixinBruecke.rufePflicht("VitrailFrameLogik", "capture", pipeline, vao, matrix,
            r, g, b, a, light, overlay, passMode, texture, emission, callback);
    }
}
