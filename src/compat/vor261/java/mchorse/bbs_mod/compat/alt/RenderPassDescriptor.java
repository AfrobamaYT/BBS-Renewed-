package mchorse.bbs_mod.compat.alt;

import com.mojang.blaze3d.textures.GpuTextureView;
import org.joml.Vector4fc;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;

/**
 * Minecraft 26.2's RenderPassDescriptor (with the build()/renderArea() the 26.3 code expects,
 * see ab261's RenderPassDescriptorBuilder), for the versions before 26.1. Before 26.1 a pass
 * is opened with plain arguments; {@link mchorse.bbs_mod.compat.CommandEncoderAlt} turns the
 * descriptor into them.
 */
public class RenderPassDescriptor
{
    private final Supplier<String> label;
    public List<Attachment<Optional<Vector4fc>>> colorAttachments = new ArrayList<>();
    public Attachment<OptionalDouble> depthAttachment;
    public RenderArea renderArea;

    private RenderPassDescriptor(Supplier<String> label)
    {
        this.label = label;
    }

    public static RenderPassDescriptor create(Supplier<String> label)
    {
        return new RenderPassDescriptor(label);
    }

    public RenderPassDescriptor withColorAttachment(GpuTextureView view)
    {
        return this.withColorAttachment(view, Optional.empty());
    }

    public RenderPassDescriptor withColorAttachment(GpuTextureView view, Optional<Vector4fc> clear)
    {
        this.colorAttachments.add(new Attachment<>(view, clear));

        return this;
    }

    public RenderPassDescriptor withUnusedColorAttachment()
    {
        this.colorAttachments.add(null);

        return this;
    }

    public RenderPassDescriptor withDepthAttachment(GpuTextureView view)
    {
        return this.withDepthAttachment(view, OptionalDouble.empty());
    }

    public RenderPassDescriptor withDepthAttachment(GpuTextureView view, OptionalDouble clear)
    {
        this.depthAttachment = new Attachment<>(view, clear);

        return this;
    }

    public RenderPassDescriptor withRenderArea(RenderArea area)
    {
        this.renderArea = area;

        return this;
    }

    /** As 26.3's builder: without an explicit area, the first used colour attachment, else depth. */
    public RenderPassDescriptor build()
    {
        if (this.renderArea == null)
        {
            GpuTextureView view = null;

            for (Attachment<?> attachment : this.colorAttachments)
            {
                if (attachment != null)
                {
                    view = attachment.textureView();

                    break;
                }
            }

            if (view == null && this.depthAttachment != null)
            {
                view = this.depthAttachment.textureView();
            }

            if (view != null)
            {
                this.renderArea = new RenderArea(0, 0, view.getWidth(0), view.getHeight(0));
            }
        }

        return this;
    }

    public RenderArea renderArea()
    {
        return this.renderArea;
    }

    public Supplier<String> label()
    {
        return this.label;
    }

    public List<Attachment<Optional<Vector4fc>>> colorAttachments()
    {
        return this.colorAttachments;
    }

    public Attachment<OptionalDouble> depthAttachment()
    {
        return this.depthAttachment;
    }

    public record Attachment<T>(GpuTextureView textureView, T clearValue)
    {}
}
