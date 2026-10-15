package mchorse.bbs_mod.compat;

import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import mchorse.bbs_mod.compat.alt.RenderArea;
import mchorse.bbs_mod.compat.alt.RenderPassDescriptor;
import org.joml.Vector4fc;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;

/**
 * 26.2's CommandEncoder calls, for the versions before 26.1. Injected into CommandEncoder;
 * Mojang's GlCommandEncoder implements them ({@link mchorse.bbs_mod.compat.mixin.GlCommandEncoderAltMixin}).
 * Depth values arrive in 26.2's reversed depth (0 = far) and are mirrored to GL's (1 = far).
 */
public interface CommandEncoderAlt
{
    default RenderPass createRenderPass(RenderPassDescriptor descriptor)
    {
        throw new UnsupportedOperationException("compat-Mixin fehlt");
    }

    default RenderPass createRenderPass(Supplier<String> label, GpuTextureView color, Optional<Vector4fc> clearColor)
    {
        return this.createRenderPass(RenderPassDescriptor.create(label).withColorAttachment(color, clearColor).build());
    }

    default RenderPass createRenderPass(Supplier<String> label, GpuTextureView color, Optional<Vector4fc> clearColor, GpuTextureView depth, OptionalDouble clearDepth)
    {
        RenderPassDescriptor descriptor = RenderPassDescriptor.create(label).withColorAttachment(color, clearColor);

        if (depth != null) descriptor.withDepthAttachment(depth, clearDepth);

        return this.createRenderPass(descriptor.build());
    }

    default RenderPass createRenderPass(Supplier<String> label, GpuTextureView color, Optional<Vector4fc> clearColor, GpuTextureView depth, OptionalDouble clearDepth, RenderArea area)
    {
        RenderPassDescriptor descriptor = RenderPassDescriptor.create(label).withColorAttachment(color, clearColor);

        if (depth != null) descriptor.withDepthAttachment(depth, clearDepth);

        return this.createRenderPass(descriptor.withRenderArea(area).build());
    }

    default void clearColorTexture(GpuTexture texture, Vector4fc color)
    {
        throw new UnsupportedOperationException("compat-Mixin fehlt");
    }

    default void clearColorAndDepthTextures(GpuTexture color, Vector4fc clearColor, GpuTexture depth, double clearDepth)
    {
        throw new UnsupportedOperationException("compat-Mixin fehlt");
    }

    /** 26.2 order: mip level, layer, x, y, width, height; the pixel format comes from the texture. */
    default void writeToTexture(GpuTexture texture, ByteBuffer source, int mipLevel, int depthOrLayer, int x, int y, int width, int height)
    {
        throw new UnsupportedOperationException("compat-Mixin fehlt");
    }

    default boolean isInRenderPass()
    {
        throw new UnsupportedOperationException("compat-Mixin fehlt");
    }

    /** 26.2 splits encoder and backend; before 26.1 Mojang's GL encoder is both. */
    default com.mojang.blaze3d.systems.CommandEncoder backend()
    {
        return (com.mojang.blaze3d.systems.CommandEncoder) this;
    }

    default mchorse.bbs_mod.compat.alt.TransientMemory transientMemory()
    {
        return mchorse.bbs_mod.compat.alt.TransientMemory.INSTANZ;
    }

    /** 26.2 submits recorded commands; OpenGL executes them immediately. */
    default void submit()
    {}
}
