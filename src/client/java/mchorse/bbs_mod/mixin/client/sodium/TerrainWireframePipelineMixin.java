package mchorse.bbs_mod.mixin.client.sodium;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import me.jellysquid.mods.sodium.client.render.chunk.DefaultChunkRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(DefaultChunkRenderer.class)
public abstract class TerrainWireframePipelineMixin
{
    @ModifyArg(method = "render", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;getCompiledPipeline(Lcom/mojang/blaze3d/pipeline/RenderPipeline;)Lcom/mojang/blaze3d/pipeline/CompiledRenderPipeline;"), index = 0)
    private RenderPipeline bbs$terrainPipeline(RenderPipeline pipeline) throws Throwable
    {
        return (RenderPipeline) MixinBruecke.rufePflicht("NativeBackendLogik", "terrainPipeline", pipeline);
    }
}
