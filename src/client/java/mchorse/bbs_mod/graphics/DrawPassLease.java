package mchorse.bbs_mod.graphics;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.systems.CommandEncoder;
//? if >=26.1 {
import com.mojang.blaze3d.vulkan.VulkanCommandEncoder;
//?}
import com.mojang.blaze3d.systems.RenderPass;
import java.util.ArrayDeque;
import java.util.Deque;

import java.util.function.Supplier;

/** A newly owned pass or a borrowed matching world pass; only the owned pass is closed. */
public final class DrawPassLease implements AutoCloseable
{
    private final RenderPass pass;
    private final boolean owned;
    private final RenderPassDescriptor descriptor;
    private final boolean nativeFormats;
    private final Framebuffer.GlPassBinding glBinding;
    private final boolean scissored;
    private final RenderPass.RenderArea previousScissor;
    private boolean closed;
    private static final ThreadLocal<Deque<DrawPassLease>> stack = ThreadLocal.withInitial(ArrayDeque::new);

    private DrawPassLease(RenderPass pass, boolean owned, RenderPassDescriptor descriptor, boolean nativeFormats,
        RenderPass.RenderArea area, Framebuffer.GlPassBinding glBinding)
    {
        this.pass = pass;
        this.owned = owned;
        this.descriptor = descriptor;
        this.nativeFormats = nativeFormats;
        this.glBinding = glBinding;
        this.scissored = !owned && area != null;
        this.previousScissor = this.scissored ? PassScissorState.get(pass) : null;
        if (this.scissored) PassScissorState.apply(pass, area);
        stack.get().push(this);
    }

    public static DrawPassLease open(CommandEncoder encoder, Supplier<String> name,
        GpuTextureView color, GpuTextureView depth, RenderPass.RenderArea area)
    {
        //? if >=26.1 {
        boolean nativeFormats = encoder instanceof CommandEncoder frontend && frontend.backend() instanceof VulkanCommandEncoder;
        //?} else {
        /*boolean nativeFormats = false;
        *///?}
        RenderPassDescriptor descriptor = descriptor(name, color, depth, area);
        RenderPass borrowed = NativeDrawPassScope.matching(color, depth);
        if (borrowed != null)
        {
            return new DrawPassLease(borrowed, false, descriptor, nativeFormats, area, null);
        }
        if (NativeDrawPassScope.active())
            throw new IllegalStateException("BBS draws to another framebuffer must be prepared outside the active native world pass"
                + " (draw " + NativeDrawPassScope.beschreiben(color, depth) + ", world pass " + NativeDrawPassScope.aktivBeschreiben() + ")");
        RenderPassDescriptor framebuffer = Framebuffer.descriptorFor(name.get(), color, depth, area);
        if (framebuffer != null) descriptor = framebuffer;
        boolean gl = encoder instanceof CommandEncoder frontend
            && frontend.backend().getClass().getName().equals("com.mojang.blaze3d.opengl.GlCommandEncoder");
        Framebuffer.GlPassBinding glBinding = gl ? Framebuffer.glPassBindingFor(color, depth) : null;
        RenderPass created;
        try { created = encoder.createRenderPass(descriptor); }
        catch (RuntimeException | Error failure)
        {
            if (glBinding != null) glBinding.restore();
            throw failure;
        }
        if (glBinding != null) glBinding.bind();
        return new DrawPassLease(created, true, descriptor, nativeFormats, null, glBinding);
    }

    public static DrawPassLease borrowExisting(RenderPass pass, GpuTextureView color, GpuTextureView depth)
    {
        RenderPassDescriptor descriptor = Framebuffer.descriptorFor("bbs-shared", color, depth, null);
        if (descriptor == null) descriptor = descriptor(() -> "bbs-shared", color, depth, null);
        return new DrawPassLease(pass, false, descriptor, false, null, null);
    }

    private static RenderPassDescriptor descriptor(Supplier<String> name, GpuTextureView color,
        GpuTextureView depth, RenderPass.RenderArea area)
    {
        RenderPassDescriptor/*Builder*/ builder = RenderPassDescriptor.create(name).withColorAttachment(color);
        if (depth != null) builder.withDepthAttachment(depth);
        if (area != null) builder.withRenderArea(area);
        return builder.build();
    }

    public RenderPipeline pipeline(RenderPipeline original)
    {
        if (!(this.pass instanceof RenderPass)) return original;
        return this.nativeFormats ? NativeTargetPipeline.forAttachments(original, this.descriptor)
            : NativeTargetPipeline.forFormats(original, this.descriptor.colorAttachments().stream()
                .map(a -> a == null ? null : a.textureView().texture().getFormat()).toList());
    }

    public RenderPass pass()
    {
        return this.pass;
    }

    @Override
    public void close()
    {
        if (this.closed) return;
        if (stack.get().peek() != this) throw new IllegalStateException("DrawPassLease scopes closed out of order");
        stack.get().pop(); this.closed = true;
        if (this.owned)
        {
            try { this.pass.close(); }
            finally { if (this.glBinding != null) this.glBinding.restore(); }
        }
        else if (this.scissored) PassScissorState.apply(this.pass, this.previousScissor);
    }
}
