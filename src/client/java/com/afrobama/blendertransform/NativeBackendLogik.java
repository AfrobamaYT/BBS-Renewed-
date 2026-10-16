package com.afrobama.blendertransform;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.backend.opengl.GlStateManager;
import com.mojang.renderpearl.backend.vulkan.VulkanTransientMemory;
import mchorse.bbs_mod.client.WeltDrahtgitter;
import mchorse.bbs_mod.graphics.PassScissorState;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkWriteDescriptorSet;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.nio.ByteBuffer;

/** Mandatory native backend corrections, kept outside the woven SDK classes. */
public final class NativeBackendLogik
{
    private NativeBackendLogik()
    {}

    public static void selectReadBuffer(GpuTexture source)
    {
        /* The SDK reuses its read FBO after depth reads, which select GL_NONE. */
        GlStateManager._glReadBuffer(source.getFormat().hasDepthAspect() ? GL11.GL_NONE : GL30.GL_COLOR_ATTACHMENT0);
    }

    public static void scissor(RenderPass pass, int x, int y, int width, int height)
    {
        PassScissorState.set(pass, new RenderPass.RenderArea(x, y, width, height));
    }

    public static void defaultScissor(RenderPass pass)
    {
        PassScissorState.set(pass, null);
    }

    public static void closed(RenderPass pass)
    {
        PassScissorState.retire(pass);
    }

    public static void terrainPipelines(Args args)
    {
        args.set(4, WeltDrahtgitter.pipelineFor(args.<RenderPipeline>get(4)));
        args.set(5, WeltDrahtgitter.pipelineFor(args.<RenderPipeline>get(5)));
    }

    public static RenderPipeline terrainPipeline(RenderPipeline pipeline)
    {
        return WeltDrahtgitter.pipelineFor(pipeline);
    }

    public static void pushNonemptyDescriptors(VkCommandBuffer command, int bindPoint,
        long layout, int set, VkWriteDescriptorSet.Buffer writes, Operation<Void> original)
    {
        /* The SDK still clears its dirty flag; Vulkan forbids a zero-write push. */
        if (writes.hasRemaining())
        {
            original.call(command, bindPoint, layout, set, writes);
        }
    }

    public static GpuBufferSlice texelAlignedStaging(VulkanTransientMemory memory, ByteBuffer bytes,
        long alignment, int usage, Operation<GpuBufferSlice> original, GpuTexture texture)
    {
        return original.call(memory, bytes, Math.max(alignment, texture.getFormat().blockSize()), usage);
    }
}
