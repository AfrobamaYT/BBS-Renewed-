package mchorse.bbs_mod.graphics;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.minecraft.resources.Identifier;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/** Real target-format variants; shader compilation/lifetime remains with Mojang's PipelineCache. */
public final class NativeTargetPipeline
{
    private record VariantKey(List<GpuFormat> formats, boolean noDepth) {}
    private static final Map<RenderPipeline, Map<VariantKey, WeakReference<RenderPipeline>>> variants = new WeakHashMap<>();
    private static final Map<RenderPipeline, RenderPipeline> originals = new WeakHashMap<>();

    private NativeTargetPipeline() {}

    //? if >=26.1 {
    private static final class TargetVariant extends RenderPipeline
    {
        TargetVariant(RenderPipeline original, Identifier location, ColorTargetState[] targets, DepthStencilState depth)
        {
            // Official constructor preserves the exact layout list; Builder.withSnippet goes through a Set.
            super(location, original.getShaders(), original.getShaderDefines(), original.getBindGroupLayouts(),
                targets, depth, original.getPolygonMode(), original.isCull(),
                original.getVertexFormatBindings().toArray(VertexFormat[]::new), original.getPrimitiveTopology(),
                original.pushConstantSize(), original.getSortKey());
        }
    }
    //?}

    public static RenderPipeline forAttachments(RenderPipeline pipeline, RenderPassDescriptor descriptor)
    {
        if (descriptor.depthAttachment() != null
            && descriptor.depthAttachment().textureView().texture().getFormat() != GpuFormat.D32_FLOAT)
            throw new IllegalArgumentException("Minecraft 26.3 Vulkan pipelines require D32_FLOAT depth; "
                + "other depth/stencil attachments need their own verified pipeline");
        List<GpuFormat> formats = new ArrayList<>();
        for (var attachment : descriptor.colorAttachments())
            formats.add(attachment == null ? null : attachment.textureView().texture().getFormat());
        return forFormats(pipeline, formats, descriptor.depthAttachment() == null);
    }

    /** Metadata only: the draw caller uses RenderSystem.getCompiledPipeline on the returned definition. */
    public static synchronized RenderPipeline forFormats(RenderPipeline pipeline, List<GpuFormat> formats)
    {
        return forFormats(pipeline, formats, false);
    }

    private static synchronized RenderPipeline forFormats(RenderPipeline pipeline, List<GpuFormat> formats, boolean noDepth)
    {
        RenderPipeline original = original(pipeline);
        /* RPE builds a depth-free Vulkan handle only when the depth state is absent. */
        noDepth &= original.getDepthStencilState() != null;
        if (formats.size() > ColorTargetState.MAX_COLOR_TARGETS)
            throw new IllegalArgumentException("Attachment count exceeds RenderPearl pipeline limit");
        List<ColorTargetState> source = original.getColorTargetStates();
        ColorTargetState[] targets = new ColorTargetState[formats.size()];
        boolean unchanged = !noDepth && source.size() == formats.size();
        for (int i = 0; i < targets.length; i++)
        {
            ColorTargetState state = i < source.size() ? source.get(i) : null;
            GpuFormat format = formats.get(i);
            if (format == null)
            {
                unchanged &= state == null;
                continue;
            }
            if (!format.hasColorAspect()) throw new IllegalArgumentException("Depth/stencil used as color target: " + format);
            if (state != null && shaderClass(state.format()) != shaderClass(format))
                throw new IllegalArgumentException("Changing fragment output numeric type requires a shader change: "
                    + state.format() + " -> " + format);
            targets[i] = state == null
                ? new ColorTargetState(Optional.empty(), format, ColorTargetState.WRITE_NONE)
                : new ColorTargetState(state.blendFunction(), format, state.writeMask());
            unchanged &= state != null && state.format() == format;
        }
        if (unchanged) return original;
        VariantKey key = new VariantKey(Collections.unmodifiableList(new ArrayList<>(formats)), noDepth);
        Map<VariantKey, WeakReference<RenderPipeline>> cache = variants.computeIfAbsent(original, unused -> new HashMap<>());
        WeakReference<RenderPipeline> cached = cache.get(key);
        RenderPipeline existing = cached == null ? null : cached.get();
        if (existing != null) return existing;
        String suffix = key.formats().stream().map(f -> f == null ? "unused" : f.name().toLowerCase(Locale.ROOT))
            .collect(java.util.stream.Collectors.joining("_")) + (noDepth ? "_no_depth" : "");
        Identifier location = Identifier.fromNamespaceAndPath("bbs", "target_variants/"
            + original.getLocation().getNamespace() + "/" + original.getLocation().getPath() + "/" + suffix);
        //? if >=26.1 {
        RenderPipeline variant = new TargetVariant(original, location, targets, noDepth ? null : original.getDepthStencilState());
        //?} else {
        /*/^* Vor 26.1 gibt es nur OpenGL: ein GL-Programm haengt nicht vom Zielformat ab, und ohne
         * Tiefenanhang wirkt der Tiefentest nicht -- die Pipeline selbst passt. *^/
        RenderPipeline variant = original;
        *///?}
        cache.put(key, new WeakReference<>(variant));
        originals.put(variant, original);
        return variant;
    }

    public static synchronized RenderPipeline original(RenderPipeline pipeline)
    {
        RenderPipeline value = originals.get(pipeline);
        return value == null ? pipeline : value;
    }

    /** Call alongside shader/resource cache clearing; this owns definitions, never compiled GPU pipelines. */
    public static synchronized void clear()
    {
        variants.clear(); // live variant origins remain available; weak keys retire them with their users
    }

    private static int shaderClass(GpuFormat format)
    {
        if (format == GpuFormat.RGB10A2_UINT) return 1;
        return switch (format.componentType())
        {
            case UINT_8, UINT_16, UINT_32 -> 1;
            case SINT_8, SINT_16, SINT_32 -> 2;
            default -> 0;
        };
    }
}
