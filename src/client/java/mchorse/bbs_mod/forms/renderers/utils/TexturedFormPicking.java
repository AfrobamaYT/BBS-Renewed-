package mchorse.bbs_mod.forms.renderers.utils;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import mchorse.bbs_mod.graphics.PickZiel;
import mchorse.bbs_mod.graphics.SofortZeichner;
import mchorse.bbs_mod.graphics.texture.Texture;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/** Texture-aware24-bit picking over the same interleaved vertices as the visible quad. */
public final class TexturedFormPicking
{
    private static final RenderPipeline PIPELINE = RenderPipeline.builder()
        .withLocation(Identifier.fromNamespaceAndPath("bbs", "pipeline/picker_textured_form"))
        .withVertexShader(Identifier.fromNamespaceAndPath("bbs", "core/picker_textured_form"))
        .withFragmentShader(Identifier.fromNamespaceAndPath("bbs", "core/picker_textured_form"))
        .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
        .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
        .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
        .withBindGroupLayout(BindGroupLayouts.PROJECTION)
        .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
        .withDepthStencilState(DepthStencilState.DEFAULT)
        .withColorTargetState(ColorTargetState.DEFAULT)
        .withCull(false)
        .build();

    private TexturedFormPicking()
    {}

    public static RenderPipeline pipeline()
    {
        return PIPELINE;
    }

    public static void draw(MeshData mesh, Texture texture, Matrix4f modelView, int index)
    {
        if (index < 0 || index > 0xFFFFFF) throw new IllegalArgumentException("Invalid textured form picking ID");
        Vector4f id = new Vector4f((index & 255) / 255F, (index >> 8 & 255) / 255F,
            (index >> 16 & 255) / 255F, 1F);
        SofortZeichner.zeichnen(PIPELINE, mesh, modelView, id, texture.guiSetup(), PickZiel.pflicht(), PickZiel.tiefe());
    }
}
