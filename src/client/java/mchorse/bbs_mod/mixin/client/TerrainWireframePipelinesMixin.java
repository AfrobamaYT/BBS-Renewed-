package mchorse.bbs_mod.mixin.client;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(ChunkSectionsToRender.class)
public abstract class TerrainWireframePipelinesMixin
{
    @ModifyArgs(method = "renderLayers", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;render(Lnet/minecraft/client/renderer/chunk/ChunkSectionLayer;Lcom/mojang/blaze3d/systems/RenderPass;Lcom/mojang/blaze3d/buffers/GpuBuffer;Lcom/mojang/blaze3d/IndexType;Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lcom/mojang/blaze3d/pipeline/RenderPipeline;)V"))
    private void bbs$terrainPipelines(Args args) throws Throwable
    {
        MixinBruecke.rufePflicht("NativeBackendLogik", "terrainPipelines", args);
    }
}
