package com.mojang.blaze3d.systems;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.GpuFence;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import mchorse.bbs_mod.compat.CommandEncoderAlt;
import mchorse.bbs_mod.compat.alt.MappedView;

import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Supplier;

/* BBS-Shim fuer Minecraft vor 1.21.5: oeffentliche Form der 1.21.11-Fassung, eigene Implementierung
 * (siehe docs/MEHRVERSION.md "vor1215"). */
public interface CommandEncoder extends CommandEncoderAlt
{
    RenderPass createRenderPass(Supplier<String> label, GpuTextureView color, OptionalInt clearColor);
    RenderPass createRenderPass(Supplier<String> label, GpuTextureView color, OptionalInt clearColor, GpuTextureView depth, OptionalDouble clearDepth);
    void clearColorTexture(GpuTexture texture, int argb);
    void clearColorAndDepthTextures(GpuTexture color, int argb, GpuTexture depth, double clearDepth);
    void clearColorAndDepthTextures(GpuTexture color, int argb, GpuTexture depth, double clearDepth, int x, int y, int width, int height);
    void clearDepthTexture(GpuTexture depth, double clearDepth);
    void writeToBuffer(GpuBufferSlice target, ByteBuffer data);
    /** In 1.21.11 GpuBuffer.MappedView; die Schicht reicht direkt BBS' MappedView. */
    MappedView mapBuffer(GpuBuffer buffer, boolean read, boolean write);
    MappedView mapBuffer(GpuBufferSlice slice, boolean read, boolean write);
    void copyToBuffer(GpuBufferSlice source, GpuBufferSlice target);
    void writeToTexture(GpuTexture texture, NativeImage image);
    void writeToTexture(GpuTexture texture, NativeImage image, int mipLevel, int depthOrLayer, int x, int y, int width, int height, int sourceX, int sourceY);
    void writeToTexture(GpuTexture texture, ByteBuffer data, NativeImage.Format format, int mipLevel, int depthOrLayer, int x, int y, int width, int height);
    void copyTextureToBuffer(GpuTexture texture, GpuBuffer buffer, long offset, Runnable done, int mipLevel);
    void copyTextureToBuffer(GpuTexture texture, GpuBuffer buffer, long offset, Runnable done, int mipLevel, int x, int y, int width, int height);
    void copyTextureToTexture(GpuTexture source, GpuTexture target, int mipLevel, int targetX, int targetY, int sourceX, int sourceY, int width, int height);
    void presentTexture(GpuTextureView view);
    GpuFence createFence();
    GpuQuery timerQueryBegin();
    void timerQueryEnd(GpuQuery query);
}
