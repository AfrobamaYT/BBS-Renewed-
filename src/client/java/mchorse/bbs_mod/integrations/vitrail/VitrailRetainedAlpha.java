package mchorse.bbs_mod.integrations.vitrail;

import mchorse.bbs_mod.compat.alt.BindGroupLayout;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import mchorse.bbs_mod.graphics.texture.Texture;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.List;

/** BBS-owned retained rows separate texture coverage from the form's continuous colour fade. */
public final class VitrailRetainedAlpha
{
    public record Row(RenderPipeline pipeline, String name) {}
    public static final BindGroupLayout BINDINGS = BindGroupLayout.builder()
        .withSampler("BbsAlphaTexture").build();
    private static final Row TWO_SIDED = new Row(new RetainedPipeline(RenderPipelines.ENTITY_TRANSLUCENT,
        "bbs_retained_translucent"), "bbs_retained_translucent");
    private static final Row CULLED = new Row(new RetainedPipeline(mchorse.bbs_mod.compat.alt.RenderPipelinesAlt.ENTITY_TRANSLUCENT_CULL,
        "bbs_retained_translucent_cull"), "bbs_retained_translucent_cull");
    private static Texture recording;

    private VitrailRetainedAlpha()
    {}

    public static List<Row> rows()
    {
        return List.of(TWO_SIDED, CULLED);
    }

    public static boolean own(RenderPipeline pipeline)
    {
        return pipeline == TWO_SIDED.pipeline() || pipeline == CULLED.pipeline();
    }

    public static boolean named(String name)
    {
        if (name.startsWith("shadow_")) name = name.substring(7);
        else if (name.startsWith("block_")) name = name.substring(6);
        return name.equals(TWO_SIDED.name()) || name.equals(CULLED.name());
    }

    public static RenderPipeline forOriginal(RenderPipeline pipeline)
    {
        if (pipeline == RenderPipelines.ENTITY_TRANSLUCENT) return TWO_SIDED.pipeline();
        if (pipeline == mchorse.bbs_mod.compat.alt.RenderPipelinesAlt.ENTITY_TRANSLUCENT_CULL) return CULLED.pipeline();
        throw new IllegalArgumentException("Unsupported retained BBS entity pipeline");
    }

    public static void recording(Texture texture)
    {
        recording = texture;
    }

    public static void bind(com.mojang.blaze3d.systems.RenderPass pass)
    {
        if (recording != null) pass.setUniform("BbsAlphaTexture", recording.view(), recording.sampler());
    }

    public static String vertex(String source)
    {
        if (source.contains("BbsAlphaCoordinate") || source.contains("bbs_retained_pack_main"))
            throw new IllegalArgumentException("Shaderpack collides with retained BBS alpha names");
        StringBuilder renamed = new StringBuilder(source.length());
        int entries = 0;
        for (dev.vitrail.glsl.GlslLexer.Token token : dev.vitrail.glsl.GlslLexer.lex(source))
        {
            if (token.directive() == null && token.identifier("main"))
            {
                renamed.append("bbs_retained_pack_main"); entries++;
            }
            else renamed.append(token.text());
        }
        if (entries == 0) throw new IllegalArgumentException("Retained entity vertex stage has no entry point");
        return renamed + "\nout vec3 BbsAlphaCoordinate;\nflat out int BbsPassMode;\n"
            + "void main() { BbsAlphaCoordinate = vec3(UV0, Color.a); BbsPassMode = 0; bbs_retained_pack_main(); }\n";
    }

    private static final class RetainedPipeline extends RenderPipeline
    {
        private RetainedPipeline(RenderPipeline source, String name)
        {
            super(Identifier.fromNamespaceAndPath("bbs", "vitrail/" + name), source.getVertexShader(), source.getFragmentShader(), source.getShaderDefines(),
                source.getBindGroupLayouts(), source.bbsColorTargetStates().toArray(mchorse.bbs_mod.compat.alt.ColorTargetState[]::new),
                source.getDepthStencilState(), source.getPolygonMode(), source.isCull(),
                new VertexFormat[] {DefaultVertexFormat.NEW_ENTITY}, source.getPrimitiveTopology(), source.getSortKey());
        }
    }
}
