package mchorse.bbs_mod.mixin.client;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.backend.vulkan.VulkanCommandEncoder;
import com.mojang.renderpearl.backend.vulkan.VulkanTransientMemory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.nio.ByteBuffer;

@Mixin(VulkanCommandEncoder.class)
public abstract class VulkanTextureStagingMixin
{
    @WrapOperation(method = "writeToTexture(Lcom/mojang/renderpearl/api/textures/GpuTexture;Ljava/nio/ByteBuffer;IIIIII)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/backend/vulkan/VulkanTransientMemory;uploadStaging(Ljava/nio/ByteBuffer;JI)Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;"))
    private GpuBufferSlice bbs$texelAlignedStaging(VulkanTransientMemory memory, ByteBuffer bytes,
        long alignment, int usage, Operation<GpuBufferSlice> original, GpuTexture texture,
        ByteBuffer input, int level, int layer, int x, int y, int width, int height) throws Throwable
    {
        return (GpuBufferSlice) MixinBruecke.rufePflicht("NativeBackendLogik", "texelAlignedStaging",
            memory, bytes, alignment, usage, original, texture);
    }
}
