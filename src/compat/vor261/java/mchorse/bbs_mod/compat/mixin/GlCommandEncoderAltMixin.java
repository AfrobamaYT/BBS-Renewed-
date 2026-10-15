package mchorse.bbs_mod.compat.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.opengl.GlBuffer;
import com.mojang.blaze3d.opengl.GlCommandEncoder;
import com.mojang.blaze3d.opengl.GlRenderPass;
import com.mojang.blaze3d.opengl.GlRenderPipeline;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.opengl.VertexArrayCache;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import mchorse.bbs_mod.compat.CommandEncoderAlt;
import mchorse.bbs_mod.compat.GpuTextureAlt;
import mchorse.bbs_mod.compat.RenderPassAlt;
import mchorse.bbs_mod.compat.alt.GlVertexBindungen;
import mchorse.bbs_mod.compat.alt.GpuFormat;
import mchorse.bbs_mod.compat.alt.RenderPassDescriptor;
import net.minecraft.util.ARGB;
import org.joml.Vector4fc;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * 26.2's encoder calls on Mojang's GL encoder before 26.1 ({@link CommandEncoderAlt}) and the
 * hook that feeds 26.2's vertex bindings ({@link GlVertexBindungen}).
 */
@Mixin(GlCommandEncoder.class)
public abstract class GlCommandEncoderAltMixin implements CommandEncoderAlt
{
    @Shadow
    private boolean inRenderPass;
    @Shadow
    @org.spongepowered.asm.mixin.Final
    private int readFbo;

    /**
     * 26.2 checks a region readback against the region; Mojang's encoder before 26.1 against the
     * whole texture (w*h of the mip), so BBS' small picking readbacks failed. Same GL steps as
     * Mojang's copyTextureToBuffer, checked against the region and with the real format (also
     * the formats the compat layer creates itself); depth goes through the depth attachment.
     */
    @org.spongepowered.asm.mixin.injection.Inject(method = "copyTextureToBuffer(Lcom/mojang/blaze3d/textures/GpuTexture;Lcom/mojang/blaze3d/buffers/GpuBuffer;JLjava/lang/Runnable;IIIII)V",
        at = @At("HEAD"), cancellable = true)
    private void bbs$ausschnittLesen(GpuTexture textur, com.mojang.blaze3d.buffers.GpuBuffer puffer, long versatz, Runnable fertig,
        int mip, int x, int y, int breite, int hoehe, org.spongepowered.asm.mixin.injection.callback.CallbackInfo info)
    {
        if (this.inRenderPass) throw new IllegalStateException("Close the existing render pass before performing additional commands");
        if (mip < 0 || mip >= textur.getMipLevels()) throw new IllegalArgumentException("Invalid mipLevel " + mip);

        GpuFormat format = ((GpuTextureAlt) textur).bbsGpuFormat();

        if ((long) breite * hoehe * format.blockSize() + versatz > puffer.size()) throw new IllegalArgumentException("Buffer of size " + puffer.size() + " is not large enough for " + breite + "x" + hoehe + " of " + format);
        if ((textur.usage() & GpuTexture.USAGE_COPY_SRC) == 0) throw new IllegalArgumentException("Texture needs USAGE_COPY_SRC to be a source for a copy");
        if ((puffer.usage() & com.mojang.blaze3d.buffers.GpuBuffer.USAGE_COPY_DST) == 0) throw new IllegalArgumentException("Buffer needs USAGE_COPY_DST to be a destination for a copy");
        if (x + breite > textur.getWidth(mip) || y + hoehe > textur.getHeight(mip)) throw new IllegalArgumentException("Copy region outside of texture " + textur.getLabel());
        if (textur.isClosed() || puffer.isClosed()) throw new IllegalStateException("Source texture or destination buffer is closed");

        boolean tiefe = format.hasDepthAspect();
        int anhang = tiefe ? org.lwjgl.opengl.GL30.GL_DEPTH_ATTACHMENT : org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT0;

        GlStateManager.clearGlErrors();
        GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER, this.readFbo);
        org.lwjgl.opengl.GL30.glFramebufferTexture2D(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER, anhang, GL11.GL_TEXTURE_2D, ((GlTexture) textur).id, mip);
        GL11.glReadBuffer(tiefe ? GL11.GL_NONE : org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT0);
        GlStateManager._glBindBuffer(org.lwjgl.opengl.GL21.GL_PIXEL_PACK_BUFFER, ((com.mojang.blaze3d.opengl.GlBuffer) puffer).handle);
        GlStateManager._pixelStore(GL11.GL_PACK_ROW_LENGTH, breite);
        GlStateManager._pixelStore(GL11.GL_PACK_ALIGNMENT, format.blockSize() % 4 == 0 ? 4 : 1);
        GlStateManager._readPixels(x, y, breite, hoehe, format.glExternal, format.glType, versatz);
        com.mojang.blaze3d.systems.RenderSystem.queueFencedTask(fertig);
        org.lwjgl.opengl.GL30.glFramebufferTexture2D(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER, anhang, GL11.GL_TEXTURE_2D, 0, mip);
        GL11.glReadBuffer(org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT0);
        GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER, 0);
        GlStateManager._glBindBuffer(org.lwjgl.opengl.GL21.GL_PIXEL_PACK_BUFFER, 0);
        GlStateManager._pixelStore(GL11.GL_PACK_ROW_LENGTH, 0);

        int fehler = GlStateManager._getError();

        if (fehler != 0) throw new IllegalStateException("Couldn't perform copyToBuffer for texture " + textur.getLabel() + ": GL error " + fehler);

        info.cancel();
    }

    @WrapOperation(method = "drawFromBuffers", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/opengl/VertexArrayCache;bindVertexArray(Lcom/mojang/blaze3d/vertex/VertexFormat;Lcom/mojang/blaze3d/opengl/GlBuffer;)V"))
    private void bbs$bindungen(VertexArrayCache cache, VertexFormat format, GlBuffer buffer, Operation<Void> original,
        @Local(argsOnly = true) GlRenderPass pass, @Local(argsOnly = true) GlRenderPipeline pipeline)
    {
        RenderPassAlt alt = (RenderPassAlt) pass;

        if (GlVertexBindungen.noetig(pipeline.info(), alt)) GlVertexBindungen.binden(pipeline.info(), alt);
        else original.call(cache, format, buffer);
    }

    @Override
    public RenderPass createRenderPass(RenderPassDescriptor descriptor)
    {
        RenderPassDescriptor.Attachment<Optional<Vector4fc>> farbe = null;
        int benutzt = 0;

        for (RenderPassDescriptor.Attachment<Optional<Vector4fc>> anhang : descriptor.colorAttachments())
        {
            if (anhang != null)
            {
                if (farbe == null) farbe = anhang;

                benutzt++;
            }
        }

        /* Mojang's GL pass before 26.1 binds exactly one colour texture; MRT and depth-only
         * passes need the raw framebuffer path, which is not built yet. */
        if (farbe == null) throw new UnsupportedOperationException("Render pass without colour attachment is not built before 26.1: " + descriptor.label().get());
        if (benutzt > 1) throw new UnsupportedOperationException("Render pass with " + benutzt + " colour attachments is not built before 26.1: " + descriptor.label().get());

        OptionalInt farbeLoeschen = farbe.clearValue().map(c -> OptionalInt.of(argb(c))).orElse(OptionalInt.empty());
        GpuTextureView tiefe = descriptor.depthAttachment() == null ? null : descriptor.depthAttachment().textureView();
        OptionalDouble tiefeLoeschen = descriptor.depthAttachment() == null ? OptionalDouble.empty() : spiegeln(descriptor.depthAttachment().clearValue());

        RenderPass pass = ((CommandEncoder) (Object) this).createRenderPass(descriptor.label(), farbe.textureView(), farbeLoeschen, tiefe, tiefeLoeschen);

        /* 26.2's GL backend limits a pass to its render area with the scissor box. */
        if (descriptor.renderArea() != null && !descriptor.renderArea().fillsTexture(farbe.textureView()))
        {
            pass.enableScissor(descriptor.renderArea().x(), descriptor.renderArea().y(), descriptor.renderArea().width(), descriptor.renderArea().height());
        }

        return pass;
    }

    @Override
    public void clearColorTexture(GpuTexture texture, Vector4fc color)
    {
        ((CommandEncoder) (Object) this).clearColorTexture(texture, argb(color));
    }

    @Override
    public void clearColorAndDepthTextures(GpuTexture color, Vector4fc clearColor, GpuTexture depth, double clearDepth)
    {
        ((CommandEncoder) (Object) this).clearColorAndDepthTextures(color, argb(clearColor), depth, 1D - clearDepth);
    }

    @Override
    public void writeToTexture(GpuTexture texture, ByteBuffer source, int mipLevel, int depthOrLayer, int x, int y, int width, int height)
    {
        if (this.inRenderPass) throw new IllegalStateException("Close the existing render pass before performing additional commands");
        if (mipLevel < 0 || mipLevel >= texture.getMipLevels()) throw new IllegalArgumentException("Invalid mipLevel " + mipLevel);
        if (x + width > texture.getWidth(mipLevel) || y + height > texture.getHeight(mipLevel)) throw new IllegalArgumentException("Write outside of texture " + texture.getLabel());
        if (depthOrLayer >= texture.getDepthOrLayers()) throw new UnsupportedOperationException("Layer " + depthOrLayer + " out of range");

        GpuFormat format = ((GpuTextureAlt) texture).bbsGpuFormat();

        if ((long) width * height * format.blockSize() > source.remaining()) throw new IllegalArgumentException("Copy would overrun the source buffer");

        int ziel;

        if ((texture.usage() & GpuTexture.USAGE_CUBEMAP_COMPATIBLE) != 0)
        {
            ziel = 34069 + depthOrLayer % 6;
            GL11.glBindTexture(34067, ((GlTexture) texture).id);
        }
        else
        {
            ziel = GL11.GL_TEXTURE_2D;
            GlStateManager._bindTexture(((GlTexture) texture).id);
        }

        int ausrichtung = format.blockSize() % 8 == 0 ? 8 : format.blockSize() % 4 == 0 ? 4 : format.blockSize() % 2 == 0 ? 2 : 1;

        GlStateManager._pixelStore(GL11.GL_UNPACK_ROW_LENGTH, width);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, ausrichtung);
        GL11.glTexSubImage2D(ziel, mipLevel, x, y, width, height, format.glExternal, format.glType, source);
    }

    @Override
    public boolean isInRenderPass()
    {
        return this.inRenderPass;
    }

    private static int argb(Vector4fc farbe)
    {
        return ARGB.colorFromFloat(farbe.w(), farbe.x(), farbe.y(), farbe.z());
    }

    /** 26.2 clears depth reversed (0 = far), GL before 26.1 the standard way (1 = far). */
    private static OptionalDouble spiegeln(OptionalDouble tiefe)
    {
        return tiefe.isPresent() ? OptionalDouble.of(1D - tiefe.getAsDouble()) : tiefe;
    }
}
