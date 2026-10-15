package com.mojang.blaze3d.opengl;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.GpuFence;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuQuery;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import mchorse.bbs_mod.compat.alt.MappedView;
import mchorse.bbs_mod.compat.alt.RenderPassDescriptor;
import mchorse.bbs_mod.compat.gpu.DurchgangsBeschreibung;
import mchorse.bbs_mod.compat.gpu.GpuAbfragen;
import mchorse.bbs_mod.compat.gpu.PufferBefehle;
import mchorse.bbs_mod.compat.gpu.RahmenPuffer;
import mchorse.bbs_mod.compat.gpu.TexturBefehle;
import org.joml.Vector4fc;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Supplier;

/**
 * BBS-Shim fuer Minecraft vor 1.21.5: der Befehlsgeber der eigenen GL-Schicht. OpenGL fuehrt
 * sofort aus; Zeichendurchgaenge binden ein Framebuffer-Objekt je Farb-/Tiefen-Paar
 * (RahmenPuffer) und stellen danach Framebuffer und Viewport von Vanilla wieder her.
 */
public class GlCommandEncoder implements CommandEncoder
{
    private final RahmenPuffer rahmen = new RahmenPuffer();
    private final GpuAbfragen abfragen = new GpuAbfragen();
    private final TexturBefehle texturen = new TexturBefehle(this.rahmen, this.abfragen);
    private final int[] alterViewport = new int[4];
    private boolean imDurchgang;
    private int alterFramebuffer;

    GlCommandEncoder()
    {}

    private void ausserhalb()
    {
        if (this.imDurchgang) throw new IllegalStateException("Erst den offenen Durchgang schliessen");
    }

    @Override
    public RenderPass createRenderPass(Supplier<String> label, GpuTextureView color, OptionalInt clearColor)
    {
        return this.createRenderPass(label, color, clearColor, null, OptionalDouble.empty());
    }

    @Override
    public RenderPass createRenderPass(Supplier<String> label, GpuTextureView color, OptionalInt clearColor, GpuTextureView depth, OptionalDouble clearDepth)
    {
        this.ausserhalb();
        this.imDurchgang = true;
        this.alterFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, this.alterViewport);

        GlTexture tiefe = depth == null ? null : (GlTexture) depth.texture();

        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.rahmen.framebuffer((GlTexture) color.texture(), color.baseMipLevel(), tiefe));
        GlStateManager._viewport(0, 0, color.getWidth(0), color.getHeight(0));
        RahmenPuffer.loeschen(clearColor, clearDepth, tiefe != null);

        return new GlRenderPass(this, label);
    }

    void durchgangEnde()
    {
        this.imDurchgang = false;
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.alterFramebuffer);
        GlStateManager._viewport(this.alterViewport[0], this.alterViewport[1], this.alterViewport[2], this.alterViewport[3]);
    }

    void texturWeg(GlTexture textur)
    {
        this.rahmen.texturWeg(textur);
    }

    public void bildEnde()
    {
        this.abfragen.bildEnde();
    }

    @Override
    public void clearColorTexture(GpuTexture texture, int argb)
    {
        this.ausserhalb();
        this.rahmen.auf((GlTexture) texture, null, () -> RahmenPuffer.loeschen(OptionalInt.of(argb), OptionalDouble.empty(), false));
    }

    @Override
    public void clearColorAndDepthTextures(GpuTexture color, int argb, GpuTexture depth, double clearDepth)
    {
        this.ausserhalb();
        this.rahmen.auf((GlTexture) color, (GlTexture) depth, () -> RahmenPuffer.loeschen(OptionalInt.of(argb), OptionalDouble.of(clearDepth), true));
    }

    @Override
    public void clearColorAndDepthTextures(GpuTexture color, int argb, GpuTexture depth, double clearDepth, int x, int y, int width, int height)
    {
        this.ausserhalb();
        this.rahmen.auf((GlTexture) color, (GlTexture) depth, () -> RahmenPuffer.loeschenIn(argb, clearDepth, x, y, width, height));
    }

    @Override
    public void clearDepthTexture(GpuTexture depth, double clearDepth)
    {
        this.ausserhalb();
        this.rahmen.auf((GlTexture) depth, null, () -> RahmenPuffer.loeschen(OptionalInt.empty(), OptionalDouble.of(clearDepth), true));
    }

    @Override
    public void writeToBuffer(GpuBufferSlice target, ByteBuffer data)
    {
        PufferBefehle.schreiben(target, data);
    }

    @Override
    public MappedView mapBuffer(GpuBuffer buffer, boolean read, boolean write)
    {
        return PufferBefehle.abbilden(buffer.slice(), read, write);
    }

    @Override
    public MappedView mapBuffer(GpuBufferSlice slice, boolean read, boolean write)
    {
        return PufferBefehle.abbilden(slice, read, write);
    }

    @Override
    public void copyToBuffer(GpuBufferSlice source, GpuBufferSlice target)
    {
        PufferBefehle.kopieren(source, target);
    }

    @Override
    public void writeToTexture(GpuTexture texture, NativeImage image)
    {
        this.writeToTexture(texture, image, 0, 0, 0, 0, image.getWidth(), image.getHeight(), 0, 0);
    }

    @Override
    public void writeToTexture(GpuTexture texture, NativeImage image, int mipLevel, int depthOrLayer, int x, int y, int width, int height, int sourceX, int sourceY)
    {
        this.ausserhalb();
        this.texturen.bild(texture, image, mipLevel, x, y, width, height, sourceX, sourceY);
    }

    @Override
    public void writeToTexture(GpuTexture texture, ByteBuffer data, NativeImage.Format format, int mipLevel, int depthOrLayer, int x, int y, int width, int height)
    {
        this.ausserhalb();
        this.texturen.bytes(texture, data, format, mipLevel, x, y, width, height);
    }

    @Override
    public void writeToTexture(GpuTexture texture, ByteBuffer source, int mipLevel, int depthOrLayer, int x, int y, int width, int height)
    {
        this.ausserhalb();
        this.texturen.bytes262(texture, source, mipLevel, depthOrLayer, x, y, width, height);
    }

    @Override
    public void copyTextureToBuffer(GpuTexture texture, GpuBuffer buffer, long offset, Runnable done, int mipLevel)
    {
        this.copyTextureToBuffer(texture, buffer, offset, done, mipLevel, 0, 0, texture.getWidth(mipLevel), texture.getHeight(mipLevel));
    }

    @Override
    public void copyTextureToBuffer(GpuTexture texture, GpuBuffer buffer, long offset, Runnable done, int mipLevel, int x, int y, int width, int height)
    {
        this.ausserhalb();
        this.texturen.lesen(texture, buffer, offset, done, mipLevel, x, y, width, height);
    }

    @Override
    public void copyTextureToTexture(GpuTexture source, GpuTexture target, int mipLevel, int targetX, int targetY, int sourceX, int sourceY, int width, int height)
    {
        this.ausserhalb();
        this.texturen.kopieren(source, target, mipLevel, targetX, targetY, sourceX, sourceY, width, height);
    }

    @Override
    public void presentTexture(GpuTextureView view)
    {
        this.texturen.zeigen(view);
    }

    @Override
    public GpuFence createFence()
    {
        return this.abfragen.fence();
    }

    @Override
    public GpuQuery timerQueryBegin()
    {
        return this.abfragen.zeitBeginn();
    }

    @Override
    public void timerQueryEnd(GpuQuery query)
    {
        this.abfragen.zeitEnde();
    }

    /* 26.2-Ergaenzungen (CommandEncoderAlt) */

    @Override
    public RenderPass createRenderPass(RenderPassDescriptor descriptor)
    {
        return DurchgangsBeschreibung.erzeugen(this, descriptor);
    }

    @Override
    public void clearColorTexture(GpuTexture texture, Vector4fc color)
    {
        this.clearColorTexture(texture, DurchgangsBeschreibung.argb(color));
    }

    @Override
    public void clearColorAndDepthTextures(GpuTexture color, Vector4fc clearColor, GpuTexture depth, double clearDepth)
    {
        this.clearColorAndDepthTextures(color, DurchgangsBeschreibung.argb(clearColor), depth, 1D - clearDepth);
    }

    @Override
    public boolean isInRenderPass()
    {
        return this.imDurchgang;
    }

}
