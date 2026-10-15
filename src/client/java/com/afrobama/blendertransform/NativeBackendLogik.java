package com.afrobama.blendertransform;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.opengl.GlStateManager;
//? if >=26.1 {
/*import com.mojang.blaze3d.vulkan.VulkanTransientMemory;
*///?}
import mchorse.bbs_mod.client.WeltDrahtgitter;
import mchorse.bbs_mod.graphics.PassScissorState;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
//? if >=26.1 {
/*import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkWriteDescriptorSet;
*///?}
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
        //? if >=26.3 {
        /*GlStateManager._glReadBuffer(source.bbsGpuFormat().hasDepthAspect() ? GL11.GL_NONE : GL30.GL_COLOR_ATTACHMENT0);
        *///?} else {
        GL11.glReadBuffer(source.bbsGpuFormat().hasDepthAspect() ? GL11.GL_NONE : GL30.GL_COLOR_ATTACHMENT0);
        //?}
    }

    public static void scissor(RenderPass pass, int x, int y, int width, int height)
    {
        PassScissorState.set(pass, new mchorse.bbs_mod.compat.alt.RenderArea(x, y, width, height));
    }

    public static void defaultScissor(RenderPass pass)
    {
        PassScissorState.set(pass, null);
    }

    public static void closed(RenderPass pass)
    {
        PassScissorState.retire(pass);
    }

    //? if >=26.3 {
    /*public static void terrainPipelines(Args args)
    {
        args.set(4, WeltDrahtgitter.pipelineFor(args.<RenderPipeline>get(4)));
        args.set(5, WeltDrahtgitter.pipelineFor(args.<RenderPipeline>get(5)));
    }

    public static RenderPipeline terrainPipeline(RenderPipeline pipeline)
    {
        return WeltDrahtgitter.pipelineFor(pipeline);
    }
    *///?}

    /* Nur der Vulkan-Mixin ruft das; vor 26.1 gaebe es die Typen zur Laufzeit nicht, und
     * MixinBruecke liest per Reflection alle Methoden dieser Klasse. */
    //? if >=26.1 {
    /*public static void pushNonemptyDescriptors(VkCommandBuffer command, int bindPoint,
        long layout, int set, VkWriteDescriptorSet.Buffer writes, Operation<Void> original)
    {
        /^ The SDK still clears its dirty flag; Vulkan forbids a zero-write push. ^/
        if (writes.hasRemaining())
        {
            original.call(command, bindPoint, layout, set, writes);
        }
    }
    *///?}

    //? if >=26.1 {
    /*public static GpuBufferSlice texelAlignedStaging(VulkanTransientMemory memory, ByteBuffer bytes,
        long alignment, int usage, Operation<GpuBufferSlice> original, GpuTexture texture)
    {
        return original.call(memory, bytes, Math.max(alignment, texture.bbsGpuFormat().blockSize()), usage);
    }
    *///?}
}
