package mchorse.bbs_mod.cubic.render;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import mchorse.bbs_mod.compat.alt.ColorTargetState;
import mchorse.bbs_mod.compat.alt.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import mchorse.bbs_mod.compat.alt.CompareOp;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import mchorse.bbs_mod.graphics.GehaltenesNetz;
import net.minecraft.client.Minecraft;
import mchorse.bbs_mod.compat.alt.BindGroupLayouts;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.HashMap;
import java.util.Map;

/** Editor controls belong above shader-pack compositing, not inside its g-buffer. */
public final class RigOverlayPass
{
    private static Pending pending;
    private static Map<CompareOp, RenderPipeline> pipelines;

    private record Pending(GehaltenesNetz mesh, GpuBufferSlice projection,
        RenderTarget target, float opacity, float hidden, boolean depth, boolean reverse) {}

    private RigOverlayPass()
    {}

    /**
     * Whether a shader pack composites this frame -- Iris, or Vitrail with a pack loaded. Vitrail
     * shaded the controls like world geometry: grey, half lit, their colour gone, the face
     * controls not in the picture at all (measured 2026-10-14 with Somber: magenta controls
     * left no magenta pixel).
     */
    public static boolean packAktiv()
    {
        if (mchorse.bbs_mod.client.BBSRendering.isIrisShadersEnabled())
        {
            return true;
        }

        try
        {
            java.lang.reflect.Field aktiv = Class.forName("dev.vitrail.render.PackChain").getDeclaredField("active");

            aktiv.setAccessible(true);

            return aktiv.get(null) != null;
        }
        catch (ClassNotFoundException keinVitrail)
        {
            return false;
        }
        catch (ReflectiveOperationException fehler)
        {
            throw new IllegalStateException("Vitrail changed: cannot tell whether its pack is active", fehler);
        }
    }

    public static void clear()
    {
        if (pending != null)
        {
            pending.mesh.close();
            pending = null;
        }
    }

    public static void queue(MeshData mesh, float opacity, float hidden, boolean depth)
    {
        clear();
        pending = new Pending(GehaltenesNetz.kurzlebig(mesh), mchorse.bbs_mod.compat.gpu.Vorgaben.projektion(),
            Minecraft.getInstance().gameRenderer.mainRenderTarget(), opacity, hidden, depth,
            mchorse.bbs_mod.graphics.Projektion.lesen().m22() >= 0F);
    }

    public static void flush()
    {
        Pending draw = pending;
        pending = null;

        if (draw == null) return;

        try (GehaltenesNetz mesh = draw.mesh)
        {
            if (draw.depth && draw.hidden > 0F)
            {
                int visible = (int) (draw.opacity * 255F) & 255;
                int hidden = (int) (draw.opacity * draw.hidden * 255F) & 255;
                draw(mesh, draw, pipeline(draw.reverse ? CompareOp.LESS_THAN : CompareOp.GREATER_THAN),
                    visible == 0 ? 0F : hidden / (float) visible);
            }

            CompareOp compare = !draw.depth ? CompareOp.ALWAYS_PASS
                : draw.reverse ? CompareOp.GREATER_THAN_OR_EQUAL : CompareOp.LESS_THAN_OR_EQUAL;
            draw(mesh, draw, pipeline(compare), 1F);
        }
    }

    private static void draw(GehaltenesNetz mesh, Pending draw, RenderPipeline pipeline, float alpha)
    {
        mesh.zeichnen(pipeline, new Matrix4f(), new Vector4f(1F, 1F, 1F, alpha), null,
            draw.target, pass -> pass.setUniform("Projection", draw.projection));
    }

    private static RenderPipeline pipeline(CompareOp compare)
    {
        if (pipelines == null) pipelines = new HashMap<>();

        return pipelines.computeIfAbsent(compare, key -> RenderPipeline.builder()
            .withLocation(ResourceLocation.fromNamespaceAndPath("bbs", "pipeline/rig_overlay_" + key.name().toLowerCase()))
            .withVertexShader(ResourceLocation.withDefaultNamespace("core/position_color"))
            .withFragmentShader(ResourceLocation.withDefaultNamespace("core/position_color"))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(com.mojang.blaze3d.vertex.VertexFormat.Mode.TRIANGLES)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withCull(false)
            .withDepthStencilState(new DepthStencilState(key, false))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT)).build());
    }
}
