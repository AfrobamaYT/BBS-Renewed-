package mchorse.bbs_mod.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PolygonMode;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.WeakHashMap;

/** Terrain wireframe for planning shots; actors and editor UI keep their own fill mode. */
public final class WeltDrahtgitter
{
    private static boolean an;
    private static final Map<RenderPipeline, RenderPipeline> variants = new WeakHashMap<>();

    private WeltDrahtgitter() {}

    public static boolean istAn() { return an; }

    public static void umschalten()
    {
        RenderSystem.assertOnRenderThread();
        if (!an && !RenderSystem.isWireframeAvailable())
            throw new IllegalStateException("The current graphics device does not support terrain wireframe");
        an = !an;
    }

    /** Called only by vanilla and Sodium terrain submissions, never by GUI/model draws. */
    public static RenderPipeline pipelineFor(RenderPipeline pipeline)
    {
        if (pipeline == null || !an || pipeline.getPolygonMode() == PolygonMode.WIREFRAME) return pipeline;
        Identifier location = pipeline.getLocation();
        String namespace = location.getNamespace();
        String path = location.getPath();
        boolean terrain = (namespace.equals("minecraft") || namespace.equals("sodium"))
            && path.startsWith("pipeline/") && path.contains("terrain");
        if (!terrain) return pipeline;
        if (!RenderSystem.isWireframeAvailable())
            throw new IllegalStateException("Terrain wireframe requires an enabled device wireframe feature");
        RenderPipeline existing = variants.get(pipeline);
        if (existing != null) return existing;
        RenderPipeline variant = new WireVariant(pipeline);
        variants.put(pipeline, variant);
        return variant;
    }

    private static final class WireVariant extends RenderPipeline
    {
        WireVariant(RenderPipeline original)
        {
            super(Identifier.fromNamespaceAndPath("bbs", "wireframe/"
                    + original.getLocation().getNamespace() + "/" + original.getLocation().getPath()),
                original.getShaders(), original.getShaderDefines(), original.getBindGroupLayouts(),
                original.getColorTargetStates().toArray(ColorTargetState[]::new),
                original.getDepthStencilState(), PolygonMode.WIREFRAME, original.isCull(),
                original.getVertexFormatBindings().toArray(VertexFormat[]::new),
                original.getPrimitiveTopology(), original.pushConstantSize(), original.getSortKey());
        }
    }
}
