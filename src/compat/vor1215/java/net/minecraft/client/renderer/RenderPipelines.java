package net.minecraft.client.renderer;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

/**
 * BBS-Shim fuer Minecraft vor 1.21.5: die Vanilla-Pipelines, die BBS benutzt, mit denselben
 * Shader-Pfaden wie 1.21.11. Unter diesen Pfaden liegen in 1.21.4 dessen eigene Kern-Shader
 * (Einzel-Uniforms statt Bloecke); die GL-Schicht fuellt sie aus den Bloecken (UniformDaten).
 * Linien: 1.21.4 kennt kein LineWidth-Attribut, die Breite kommt als Uniform.
 */
public final class RenderPipelines
{
    public static final RenderPipeline.Snippet MATRICES_PROJECTION_SNIPPET = RenderPipeline.builder()
        .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER).withUniform("Projection", UniformType.UNIFORM_BUFFER).buildSnippet();
    public static final RenderPipeline.Snippet FOG_SNIPPET = RenderPipeline.builder().withUniform("Fog", UniformType.UNIFORM_BUFFER).buildSnippet();
    public static final RenderPipeline.Snippet GLOBALS_SNIPPET = RenderPipeline.builder().withUniform("Globals", UniformType.UNIFORM_BUFFER).buildSnippet();
    public static final RenderPipeline.Snippet MATRICES_FOG_SNIPPET = RenderPipeline.builder(MATRICES_PROJECTION_SNIPPET, FOG_SNIPPET).buildSnippet();
    public static final RenderPipeline.Snippet MATRICES_FOG_LIGHT_DIR_SNIPPET = RenderPipeline.builder(MATRICES_PROJECTION_SNIPPET, FOG_SNIPPET)
        .withUniform("Lighting", UniformType.UNIFORM_BUFFER).buildSnippet();

    public static final RenderPipeline.Snippet ENTITY_SNIPPET = RenderPipeline.builder(MATRICES_FOG_LIGHT_DIR_SNIPPET)
        .withVertexShader("core/entity").withFragmentShader("core/entity").withSampler("Sampler0").withSampler("Sampler2")
        .withVertexFormat(DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS).buildSnippet();
    public static final RenderPipeline.Snippet LINES_SNIPPET = RenderPipeline.builder(MATRICES_FOG_SNIPPET, GLOBALS_SNIPPET)
        .withVertexShader("core/rendertype_lines").withFragmentShader("core/rendertype_lines").withBlend(BlendFunction.TRANSLUCENT).withCull(false)
        .withVertexFormat(DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES).buildSnippet();
    public static final RenderPipeline.Snippet GUI_SNIPPET = RenderPipeline.builder(MATRICES_PROJECTION_SNIPPET)
        .withVertexShader("core/gui").withFragmentShader("core/gui").withBlend(BlendFunction.TRANSLUCENT)
        .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS).withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).buildSnippet();
    public static final RenderPipeline.Snippet GUI_TEXTURED_SNIPPET = RenderPipeline.builder(MATRICES_PROJECTION_SNIPPET)
        .withVertexShader("core/position_tex_color").withFragmentShader("core/position_tex_color").withSampler("Sampler0").withBlend(BlendFunction.TRANSLUCENT)
        .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS).withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).buildSnippet();
    public static final RenderPipeline.Snippet PARTICLE_SNIPPET = RenderPipeline.builder(MATRICES_FOG_SNIPPET)
        .withVertexShader("core/particle").withFragmentShader("core/particle").withSampler("Sampler0").withSampler("Sampler2")
        .withVertexFormat(DefaultVertexFormat.PARTICLE, VertexFormat.Mode.QUADS).buildSnippet();

    public static final RenderPipeline ENTITY_TRANSLUCENT = RenderPipeline.builder(ENTITY_SNIPPET).withLocation("pipeline/entity_translucent")
        .withShaderDefine("ALPHA_CUTOUT", 0.1F).withShaderDefine("PER_FACE_LIGHTING").withSampler("Sampler1").withBlend(BlendFunction.TRANSLUCENT).withCull(false).build();
    public static final RenderPipeline ENTITY_CUTOUT = RenderPipeline.builder(ENTITY_SNIPPET).withLocation("pipeline/entity_cutout")
        .withShaderDefine("ALPHA_CUTOUT", 0.1F).withSampler("Sampler1").build();
    public static final RenderPipeline TRANSLUCENT_PARTICLE = RenderPipeline.builder(PARTICLE_SNIPPET).withLocation("pipeline/translucent_particle")
        .withBlend(BlendFunction.TRANSLUCENT).build();
    public static final RenderPipeline GUI = RenderPipeline.builder(GUI_SNIPPET).withLocation("pipeline/gui").build();
    public static final RenderPipeline GUI_TEXTURED = RenderPipeline.builder(GUI_TEXTURED_SNIPPET).withLocation("pipeline/gui_textured").build();
    public static final RenderPipeline GUI_TEXTURED_PREMULTIPLIED_ALPHA = RenderPipeline.builder(GUI_TEXTURED_SNIPPET)
        .withLocation("pipeline/gui_textured_premultiplied_alpha").withBlend(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA).build();
    public static final RenderPipeline LINES = RenderPipeline.builder(LINES_SNIPPET).withLocation("pipeline/lines").build();

    private RenderPipelines()
    {}
}
