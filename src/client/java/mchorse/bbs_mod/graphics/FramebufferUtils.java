package mchorse.bbs_mod.graphics;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.systems.CommandEncoder;

/** Attachment and preparation contracts shared by offscreen forms and picking. */
public final class FramebufferUtils
{
    private FramebufferUtils() {}

    public static boolean inPass()
    {
        return RenderSystem.getDevice().createCommandEncoder() instanceof CommandEncoder frontend
            && frontend.isInRenderPass();
    }

    public static CommandEncoder outsidePass(String operation)
    {
        RenderSystem.assertOnRenderThread();
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        if (encoder instanceof CommandEncoder frontend && frontend.isInRenderPass())
            throw new IllegalStateException(operation + " must be prepared before the open RenderPearl pass");
        return encoder;
    }

    public static void validateAttachment(GpuTextureView view, int width, int height)
    {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Framebuffer dimensions must be positive");
        if (view == null || view.isClosed() || view.texture().isClosed())
            throw new IllegalStateException("Framebuffer attachment is absent or closed");
        if ((view.texture().usage() & com.mojang.blaze3d.textures.GpuTexture.USAGE_RENDER_ATTACHMENT) == 0)
            throw new IllegalStateException("Framebuffer texture lacks RENDER_ATTACHMENT usage");
        if (view.baseMipLevel() != 0 || view.mipLevels() != 1 || view.getWidth(0) != width || view.getHeight(0) != height)
            throw new IllegalStateException("Framebuffer attachments require one matching mip level: "
                + width + "x" + height + ", found " + view.getWidth(0) + "x" + view.getHeight(0)
                + " mips=" + view.mipLevels());
    }
}
