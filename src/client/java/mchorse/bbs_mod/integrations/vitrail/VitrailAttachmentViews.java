package mchorse.bbs_mod.integrations.vitrail;

import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import java.util.IdentityHashMap;
import java.util.Map;

/** Reuses Vitrail's owned base-level view for attachments; sampled views keep their full chain. */
public final class VitrailAttachmentViews
{
    private static final Map<GpuTextureView, GpuTextureView> BASE = new IdentityHashMap<>();
    private VitrailAttachmentViews() {}

    public static synchronized void register(GpuTextureView sampled, GpuTextureView base)
    {
        if (sampled == null || sampled.mipLevels() == 1) return;
        if (base == null || base.mipLevels() != 1 || base.baseMipLevel() != sampled.baseMipLevel()
            || base.texture() != sampled.texture()) throw new IllegalStateException("Vitrail base-level view ABI changed");
        BASE.put(sampled, base);
    }

    public static synchronized void forget(GpuTextureView sampled)
    {
        BASE.remove(sampled);
    }

    public static synchronized RenderPassDescriptor descriptor(RenderPassDescriptor descriptor)
    {
        java.util.ArrayList<RenderPassDescriptor.Attachment<java.util.Optional<org.joml.Vector4fc>>> colors = null;
        for (int index = 0; index < descriptor.colorAttachments().size(); index++)
        {
            RenderPassDescriptor.Attachment<java.util.Optional<org.joml.Vector4fc>> original = descriptor.colorAttachments().get(index);
            if (original == null) continue;
            GpuTextureView base = BASE.get(original.textureView());
            if (base == null) continue;
            if (base.isClosed()) throw new IllegalStateException("Vitrail attachment's owned base-level view is closed");
            if (colors == null) colors = new java.util.ArrayList<>(descriptor.colorAttachments());
            colors.set(index, new RenderPassDescriptor.Attachment<>(base, original.clearValue()));
        }
        //? if >=26.3 {
        return colors == null ? descriptor : new RenderPassDescriptor(descriptor.label(),
            java.util.Collections.unmodifiableList(colors), descriptor.depthAttachment(), descriptor.renderArea());
        //?} else {
        /*if (colors == null) return descriptor;
        RenderPassDescriptor copy = RenderPassDescriptor.builder(descriptor.label());
        copy.colorAttachments = java.util.Collections.unmodifiableList(colors);
        copy.depthAttachment = descriptor.depthAttachment;
        copy.renderArea = descriptor.renderArea;
        return copy;
        *///?}
    }
}
