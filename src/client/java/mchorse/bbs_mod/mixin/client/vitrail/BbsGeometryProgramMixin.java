package mchorse.bbs_mod.mixin.client.vitrail;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "dev.vitrail.render.GeometryProgram", remap = false)
public abstract class BbsGeometryProgramMixin
{
    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/api/pipeline/RenderPipeline$Builder;withVertexBinding(ILcom/mojang/renderpearl/api/vertex/VertexFormat;)Lcom/mojang/renderpearl/api/pipeline/RenderPipeline$Builder;"))
    private RenderPipeline.Builder bbs$nativeInstances(RenderPipeline.Builder builder, int binding,
        VertexFormat format, Operation<RenderPipeline.Builder> original) throws Throwable
    { return (RenderPipeline.Builder) MixinBruecke.rufePflicht("VitrailGeometryLogik", "vertex", this, builder, binding, format, original); }
}
