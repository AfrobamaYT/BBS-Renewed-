package mchorse.bbs_mod.compat;

import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.textures.GpuTextureView;

/**
 * Before Minecraft 26.3 a RenderPassDescriptor is its own builder: create(...) and the same
 * with* calls, but no build() and no renderArea() accessor, and its render area must be
 * set by hand. build() fills it the way 26.3's builder does: the size of the first used
 * colour attachment, otherwise of the depth attachment.
 */
public interface RenderPassDescriptorBuilder
{
    default RenderPassDescriptor build()
    {
        RenderPassDescriptor descriptor = (RenderPassDescriptor) this;

        if (descriptor.renderArea == null)
        {
            GpuTextureView view = null;

            for (RenderPassDescriptor.Attachment<?> attachment : descriptor.colorAttachments)
            {
                if (attachment != null)
                {
                    view = attachment.textureView();

                    break;
                }
            }

            if (view == null && descriptor.depthAttachment != null)
            {
                view = descriptor.depthAttachment.textureView();
            }

            if (view != null)
            {
                descriptor.renderArea = new RenderPass.RenderArea(0, 0, view.getWidth(0), view.getHeight(0));
            }
        }

        return descriptor;
    }

    default RenderPass.RenderArea renderArea()
    {
        return ((RenderPassDescriptor) this).renderArea;
    }
}
