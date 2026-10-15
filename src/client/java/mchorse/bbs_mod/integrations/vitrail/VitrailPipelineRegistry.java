package mchorse.bbs_mod.integrations.vitrail;

import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;
import mchorse.bbs_mod.cubic.render.vao.ModelVAOPipeline;
import net.minecraft.resources.Identifier;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Real deformation formats keyed by pipeline identity; these objects are served only by Vitrail. */
public final class VitrailPipelineRegistry
{
    public record Entry(RenderPipeline original, RenderPipeline pipeline, VitrailVertexSource.Kind kind, String name) {}

    private static final Map<RenderPipeline, Entry> ORIGINAL = new IdentityHashMap<>();
    private static final Map<RenderPipeline, Entry> DEFORMED = new IdentityHashMap<>();
    private static final Map<RenderPipeline, Map<Integer, Entry>> PASSES = new IdentityHashMap<>();

    private VitrailPipelineRegistry() {}

    public static synchronized void bootstrap()
    {
        for (var pipeline : List.of(ModelVAOPipeline.DURCHSICHTIG, ModelVAOPipeline.DURCHSICHTIG_BEIDSEITIG,
            ModelVAOPipeline.DURCHSICHTIG_OHNE_TIEFENSCHREIBEN,
            ModelVAOPipeline.DURCHSICHTIG_OHNE_TIEFENSCHREIBEN_BEIDSEITIG, ModelVAOPipeline.UNDURCHSICHTIG))
        {
            register(pipeline, VitrailVertexSource.Kind.MODEL);
            register(ModelVAOPipeline.gpuVariante(pipeline), VitrailVertexSource.Kind.SKIN);
        }
        register(com.afrobama.bbstools.client.bbs.fastpath.Tier2hVitrail.pipeline(), VitrailVertexSource.Kind.CROWD);
    }

    public static synchronized Entry register(RenderPipeline original, VitrailVertexSource.Kind kind)
    {
        if (original == null || original.getPrimitiveTopology() != PrimitiveTopology.TRIANGLES)
            throw new IllegalArgumentException("Vitrail native geometry needs an actual triangle pipeline");
        Entry existing = ORIGINAL.get(original);
        if (existing != null)
        {
            if (existing.kind() != kind) throw new IllegalStateException("Native Vitrail pipeline kind changed");
            return existing;
        }
        String name = "bbs_" + kind.name().toLowerCase(java.util.Locale.ROOT) + "_" + ORIGINAL.size();
        Entry entry = new Entry(original, new DeformationPipeline(original, kind, name), kind, name);
        ORIGINAL.put(original, entry);
        DEFORMED.put(entry.pipeline(), entry);
        for (int passMode : new int[] {1, 2})
        {
            String passName = name + "_pass" + passMode;
            Entry pass = new Entry(original, new DeformationPipeline(original, kind, passName, passMode), kind, passName);
            PASSES.computeIfAbsent(original, ignored -> new java.util.HashMap<>()).put(passMode, pass);
            DEFORMED.put(pass.pipeline(), pass);
        }
        return entry;
    }

    public static synchronized Entry pass(RenderPipeline original, int mode)
    {
        return mode == 0 ? ORIGINAL.get(original) : PASSES.getOrDefault(original, Map.of()).get(mode);
    }

    public static synchronized Entry original(RenderPipeline pipeline) { return ORIGINAL.get(pipeline); }
    public static synchronized Entry deformed(RenderPipeline pipeline) { return DEFORMED.get(pipeline); }
    public static synchronized Entry named(String name)
    {
        if (name.startsWith("shadow_")) name = name.substring(7);
        else if (name.startsWith("block_")) name = name.substring(6);
        for (var entry : DEFORMED.values()) if (entry.name().equals(name)) return entry;
        return null;
    }
    public static synchronized List<Entry> entries() { return List.copyOf(DEFORMED.values()); }

    private static final class DeformationPipeline extends RenderPipeline
    {
        private DeformationPipeline(RenderPipeline source, VitrailVertexSource.Kind kind, String name)
        {
            this(source, kind, name, 0);
        }

        private DeformationPipeline(RenderPipeline source, VitrailVertexSource.Kind kind, String name, int mode)
        {
            super(Identifier.fromNamespaceAndPath("bbs", "vitrail/" + name), source.getVertexShader(), source.getFragmentShader(), source.getShaderDefines(),
                List.of(VitrailGpuLayout.bindings(kind)), colors(source, mode),
                depth(source, mode), source.getPolygonMode(), source.isCull(),
                kind == VitrailVertexSource.Kind.CROWD ? new VertexFormat[] {VitrailGpuLayout.CORNER, VitrailGpuLayout.INSTANCE}
                    : new VertexFormat[] {VitrailGpuLayout.CORNER},
                source.getPrimitiveTopology(), source.getSortKey());
        }

        private static ColorTargetState[] colors(RenderPipeline source, int mode)
        {
            return source.bbsColorTargetStates().stream().map(state -> mode == 0 ? state :
                new ColorTargetState(mode == 1 ? java.util.Optional.empty() : java.util.Optional.of(
                    com.mojang.blaze3d.pipeline.BlendFunction.TRANSLUCENT), state.format(), state.writeMask()))
                .toArray(ColorTargetState[]::new);
        }

        private static com.mojang.blaze3d.pipeline.DepthStencilState depth(RenderPipeline source, int mode)
        {
            com.mojang.blaze3d.pipeline.DepthStencilState state = source.getDepthStencilState();
            if (mode == 0 || state == null) return state;
            return new com.mojang.blaze3d.pipeline.DepthStencilState(state.depthTest(), mode == 1,
                state.depthBiasScaleFactor(), state.depthBiasConstant());
        }
    }
}
