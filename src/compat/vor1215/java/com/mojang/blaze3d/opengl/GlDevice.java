package com.mojang.blaze3d.opengl;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import mchorse.bbs_mod.compat.gpu.GlProgramm;
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.function.Supplier;

/**
 * BBS-Shim fuer Minecraft vor 1.21.5: das GPU-Geraet der eigenen GL-Schicht. Es gibt genau
 * eines; RenderSystem.getDevice() fuehrt per Regel hierher. Programme entstehen beim ersten
 * Zeichnen einer Pipeline und fallen bei clearPipelineCache (Ressourcen neu geladen) weg.
 */
public class GlDevice implements GpuDevice
{
    private static GlDevice instanz;

    private final GlCommandEncoder encoder = new GlCommandEncoder();
    private final Map<RenderPipeline, GlProgramm> programme = new IdentityHashMap<>();

    public static GlDevice instanz()
    {
        if (instanz == null)
        {
            RenderSystem.assertOnRenderThread();
            instanz = new GlDevice();
        }

        return instanz;
    }

    public GlProgramm programm(RenderPipeline pipeline)
    {
        GlProgramm programm = this.programme.get(pipeline);

        if (programm == null)
        {
            programm = GlProgramm.bauen(pipeline);
            this.programme.put(pipeline, programm);
        }

        return programm;
    }

    public void bildEnde()
    {
        this.encoder.bildEnde();
    }

    @Override
    public CommandEncoder createCommandEncoder()
    {
        return this.encoder;
    }

    @Override
    public GpuSampler createSampler(AddressMode u, AddressMode v, FilterMode min, FilterMode mag, int maxAnisotropy, OptionalDouble maxLod)
    {
        return new GlSampler(u, v, min, mag, maxAnisotropy, maxLod);
    }

    @Override
    public GpuTexture createTexture(Supplier<String> label, int usage, TextureFormat format, int width, int height, int depthOrLayers, int mipLevels)
    {
        return this.createTexture(label.get(), usage, format, width, height, depthOrLayers, mipLevels);
    }

    @Override
    public GpuTexture createTexture(String label, int usage, TextureFormat format, int width, int height, int depthOrLayers, int mipLevels)
    {
        if (mipLevels < 1) throw new IllegalArgumentException("Mindestens eine Mip-Stufe: " + label);

        boolean wuerfel = (usage & GpuTexture.USAGE_CUBEMAP_COMPATIBLE) != 0;

        if (depthOrLayers > 1 && !(wuerfel && depthOrLayers == 6)) throw new UnsupportedOperationException("Texturschichten sind vor 1.21.5 nicht gebaut: " + label);

        return new GlTexture(usage, label, format, width, height, depthOrLayers, mipLevels);
    }

    @Override
    public GpuTextureView createTextureView(GpuTexture texture)
    {
        return this.createTextureView(texture, 0, texture.getMipLevels());
    }

    @Override
    public GpuTextureView createTextureView(GpuTexture texture, int baseMipLevel, int mipLevels)
    {
        if (texture.isClosed()) throw new IllegalArgumentException("Textur " + texture.getLabel() + " ist geschlossen");
        if (baseMipLevel < 0 || baseMipLevel + mipLevels > texture.getMipLevels()) throw new IllegalArgumentException("Mip-Bereich ausserhalb von " + texture.getLabel());

        return new GlTextureView((GlTexture) texture, baseMipLevel, mipLevels);
    }

    @Override
    public GpuBuffer createBuffer(Supplier<String> label, int usage, long size)
    {
        if (size <= 0 || size > Integer.MAX_VALUE) throw new IllegalArgumentException("Puffergroesse " + size + " fuer " + label.get());

        return new GlBuffer.Direct(label.get(), usage, (int) size);
    }

    @Override
    public GpuBuffer createBuffer(Supplier<String> label, int usage, ByteBuffer data)
    {
        if (!data.hasRemaining()) throw new IllegalArgumentException("Leerer Puffer: " + label.get());

        return new GlBuffer.Direct(label.get(), usage, data);
    }

    @Override
    public String getImplementationInformation()
    {
        return "BBS-GL-Schicht vor 1.21.5 auf " + this.getRenderer() + " / " + this.getVersion();
    }

    @Override
    public List<String> getLastDebugMessages()
    {
        return List.of();
    }

    @Override
    public boolean isDebuggingEnabled()
    {
        return false;
    }

    @Override
    public String getVendor()
    {
        return GL11.glGetString(GL11.GL_VENDOR);
    }

    @Override
    public String getBackendName()
    {
        return "OpenGL";
    }

    @Override
    public String getVersion()
    {
        return GL11.glGetString(GL11.GL_VERSION);
    }

    @Override
    public String getRenderer()
    {
        return GL11.glGetString(GL11.GL_RENDERER);
    }

    @Override
    public int getMaxTextureSize()
    {
        return RenderSystem.maxSupportedTextureSize();
    }

    @Override
    public int getUniformOffsetAlignment()
    {
        return GL11.glGetInteger(GL31.GL_UNIFORM_BUFFER_OFFSET_ALIGNMENT);
    }

    @Override
    public CompiledRenderPipeline precompilePipeline(RenderPipeline pipeline)
    {
        GlProgramm programm = this.programm(pipeline);

        return () -> programm.id != 0;
    }

    @Override
    public void clearPipelineCache()
    {
        this.programme.values().forEach(GlProgramm::schliessen);
        this.programme.clear();
    }

    @Override
    public List<String> getEnabledExtensions()
    {
        int anzahl = GL11.glGetInteger(GL30.GL_NUM_EXTENSIONS);
        List<String> liste = new ArrayList<>(anzahl);

        for (int i = 0; i < anzahl; i++)
        {
            liste.add(GL30.glGetStringi(GL11.GL_EXTENSIONS, i));
        }

        return liste;
    }

    @Override
    public int getMaxSupportedAnisotropy()
    {
        return GL.getCapabilities().GL_EXT_texture_filter_anisotropic
            ? (int) GL11.glGetFloat(EXTTextureFilterAnisotropic.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT) : 1;
    }

    @Override
    public void close()
    {
        this.clearPipelineCache();
    }
}
