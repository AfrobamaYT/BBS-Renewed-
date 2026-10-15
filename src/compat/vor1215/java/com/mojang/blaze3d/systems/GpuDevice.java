package com.mojang.blaze3d.systems;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import mchorse.bbs_mod.compat.GpuDeviceAlt;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.OptionalDouble;
import java.util.function.Supplier;

/* BBS-Shim fuer Minecraft vor 1.21.5: oeffentliche Form der 1.21.11-Fassung, eigene Implementierung
 * (siehe docs/MEHRVERSION.md "vor1215"). */
public interface GpuDevice extends GpuDeviceAlt
{
    CommandEncoder createCommandEncoder();
    GpuSampler createSampler(AddressMode u, AddressMode v, FilterMode min, FilterMode mag, int maxAnisotropy, OptionalDouble maxLod);
    GpuTexture createTexture(Supplier<String> label, int usage, TextureFormat format, int width, int height, int depthOrLayers, int mipLevels);
    GpuTexture createTexture(String label, int usage, TextureFormat format, int width, int height, int depthOrLayers, int mipLevels);
    GpuTextureView createTextureView(GpuTexture texture);
    GpuTextureView createTextureView(GpuTexture texture, int baseMipLevel, int mipLevels);
    GpuBuffer createBuffer(Supplier<String> label, int usage, long size);
    GpuBuffer createBuffer(Supplier<String> label, int usage, ByteBuffer data);
    String getImplementationInformation();
    List<String> getLastDebugMessages();
    boolean isDebuggingEnabled();
    String getVendor();
    String getBackendName();
    String getVersion();
    String getRenderer();
    int getMaxTextureSize();
    int getUniformOffsetAlignment();
    CompiledRenderPipeline precompilePipeline(RenderPipeline pipeline);
    void clearPipelineCache();
    List<String> getEnabledExtensions();
    int getMaxSupportedAnisotropy();
    void close();
}
