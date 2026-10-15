package mchorse.bbs_mod.graphics;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.GpuTextureView;

/** Existing native pass and its actual targets. This scope never owns or closes that pass. */
public final class NativeDrawPassScope implements AutoCloseable
{
    private static NativeDrawPassScope active;
    private final NativeDrawPassScope previous;
    private final RenderPass pass;
    private final GpuTextureView color;
    private final GpuTextureView depth;

    private NativeDrawPassScope(RenderPass pass, GpuTextureView color, GpuTextureView depth)
    {
        this.previous = active;
        this.pass = java.util.Objects.requireNonNull(pass);
        this.color = java.util.Objects.requireNonNull(color);
        this.depth = depth;
        active = this;
    }

    /** Caller supplies the attachments used to create this pass, not a guessed global FBO. */
    public static NativeDrawPassScope enter(RenderPass pass, GpuTextureView color, GpuTextureView depth)
    {
        RenderSystem.assertOnRenderThread();
        return new NativeDrawPassScope(pass, color, depth);
    }

    public static boolean active()
    {
        return active != null;
    }

    /** Null means another target (or no existing pass); caller must prepare a separate draw. */
    public static RenderPass matching(GpuTextureView color, GpuTextureView depth)
    {
        return active != null && same(active.color, color) && same(active.depth, depth) ? active.pass : null;
    }

    /** Labels of a colour/depth pair, for refusals that would otherwise not say which targets differ. */
    public static String beschreiben(GpuTextureView color, GpuTextureView depth)
    {
        return name(color) + " + " + name(depth);
    }

    public static String aktivBeschreiben()
    {
        return active == null ? "none" : beschreiben(active.color, active.depth);
    }

    private static String name(GpuTextureView view)
    {
        return view == null ? "null" : view.texture().getLabel() + " " + view.texture().getWidth(0) + "x" + view.texture().getHeight(0);
    }

    private static boolean same(GpuTextureView a, GpuTextureView b)
    {
        return a == b || a != null && b != null && a.texture() == b.texture()
            && a.baseMipLevel() == b.baseMipLevel() && a.mipLevels() == b.mipLevels();
    }

    @Override
    public void close()
    {
        RenderSystem.assertOnRenderThread();
        if (active != this) throw new IllegalStateException("Native BBS pass scopes closed out of order");
        active = this.previous;
    }
}
