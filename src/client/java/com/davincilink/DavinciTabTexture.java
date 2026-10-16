package com.davincilink;

import com.mojang.blaze3d.pipeline.PipelineCache;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.pipeline.*;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.texture.TextureFormat;
import mchorse.bbs_mod.graphics.texture.TextureUpload;
import net.minecraft.resources.Identifier;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;

/** A GPU pass converts native BGRA and keys only the viewer rectangle. No pixel loop on the CPU. */
public final class DavinciTabTexture implements AutoCloseable
{
    private static final Map<Texture, DavinciTabTexture> frames = new IdentityHashMap<>();
    private static final String VERTEX = """
        #version 330
        #extension GL_ARB_separate_shader_objects : require
        layout(location=0) out vec2 uv;
        void main() {
            uv = vec2((gl_VertexIndex << 1) & 2, gl_VertexIndex & 2);
            gl_Position = vec4(uv * 2.0 - 1.0, 0.0, 1.0);
        }
        """;
    private static final String FRAGMENT = """
        #version 330
        #extension GL_ARB_separate_shader_objects : require
        uniform sampler2D Source;
        layout(std140) uniform KeyInfo { vec4 Rect; vec4 Key; };
        layout(location=0) in vec2 uv;
        layout(location=0) out vec4 fragColor;
        void main() {
            ivec2 p = clamp(ivec2(uv * vec2(textureSize(Source, 0))), ivec2(0), textureSize(Source, 0)-1);
            vec3 rgb = texelFetch(Source, p, 0).bgr;
            bool inside = all(greaterThanEqual(vec2(p), Rect.xy)) && all(lessThan(vec2(p), Rect.zw));
            bool keyed = all(lessThanEqual(abs(rgb - Key.rgb), vec3(Key.a)));
            fragColor = vec4(rgb, inside && keyed ? 0.0 : 1.0);
        }
        """;
    private final Texture raw = new Texture();
    private final GpuBuffer key;
    private final PipelineCache cache;
    private final RenderPipeline pipeline;
    private boolean received;
    private int[] previous;
    private int[] sourceViewer;

    private DavinciTabTexture()
    {
        this.raw.setFilter(GL11.GL_NEAREST);
        this.key = RenderSystem.getDevice().createBuffer(() -> "Resolve viewer key",
            GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, 32);
        BindGroupLayout layout = BindGroupLayout.builder()
            .withUniform("Source", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("KeyInfo", UniformType.UNIFORM_BUFFER).build();
        Identifier shader = Identifier.fromNamespaceAndPath("bbs", "resolve_bgra_key");
        this.pipeline = RenderPipeline.builder().withLocation(shader)
            .withVertexShader(shader).withFragmentShader(shader).withBindGroupLayout(layout)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withCull(false)
            .withColorTargetState(ColorTargetState.DEFAULT).build();
        this.cache = new PipelineCache(RenderSystem.getDevice(), new ShaderSource()
        {
            @Override public String getShader(Identifier id, ShaderType type)
            {
                if (!id.equals(shader)) throw new IllegalArgumentException("Unexpected Resolve shader " + id);
                return type == ShaderType.VERTEX ? VERTEX : FRAGMENT;
            }
            @Override public CachedIncludeSource getInclude(Identifier id)
            {
                throw new IllegalArgumentException("Resolve shader has no includes: " + id);
            }
            @Override public void close() {}
        });
    }

    static void accept(Texture target, ByteBuffer pixels, int width, int height, int[] viewer, boolean keyed)
    {
        DavinciTabTexture frame = frames.computeIfAbsent(target, unused -> new DavinciTabTexture());
        frame.upload(pixels, width, height);
        frame.sourceViewer = viewer == null ? null : viewer.clone();
        frame.draw(target, keyed ? frame.sourceViewer : null);
    }

    static void refresh(Texture target, boolean keyed)
    {
        DavinciTabTexture frame = frames.get(target);
        if (frame == null || !frame.received) return;
        int[] rect = keyed ? frame.sourceViewer : null;
        if (!Arrays.equals(rect, frame.previous)) frame.draw(target, rect);
    }

    /** Also used by the owned-client GPU readback check. Input remains BGRA/BGRX. */
    public static void apply(Texture target, ByteBuffer pixels, int width, int height, int[] viewer)
    {
        accept(target, pixels, width, height, viewer, true);
    }

    private void upload(ByteBuffer pixels, int width, int height)
    {
        this.raw.setSize(width, height);
        /* RGBA storage deliberately holds BGRA bytes; the shader swizzles the sample. */
        TextureUpload.write(this.raw.gpu(), TextureFormat.RGBA_U8, 0, width, height, pixels);
        this.received = true;
    }

    private void draw(Texture target, int[] viewer)
    {
        target.setSize(this.raw.width, this.raw.height);
        ByteBuffer data = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder());
        if (viewer == null) data.putFloat(0F).putFloat(0F).putFloat(0F).putFloat(0F);
        else data.putFloat(viewer[0]).putFloat(viewer[1]).putFloat(viewer[0] + viewer[2]).putFloat(viewer[1] + viewer[3]);
        data.putFloat(DavinciViewerFinder.KEY_R / 255F).putFloat(DavinciViewerFinder.KEY_G / 255F)
            .putFloat(DavinciViewerFinder.KEY_B / 255F).putFloat((DavinciViewerFinder.KEY_TOLERANCE + 0.01F) / 255F).flip();
        RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.key.slice(), data);
        CompiledRenderPipeline compiled = this.cache.get(this.pipeline);
        if (compiled == null) throw new IllegalStateException("Resolve BGRA/key shader did not compile");
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
            RenderPassDescriptor.builder(() -> "Resolve BGRA and viewer key").withColorAttachment(target.attachmentView()).build()))
        {
            pass.setPipeline(compiled);
            pass.setUniform("Source", this.raw.view(), this.raw.sampler());
            pass.setUniform("KeyInfo", this.key);
            pass.draw(3, 1, 0, 0);
        }
        this.previous = viewer == null ? null : viewer.clone();
    }

    public static void release(Texture target)
    {
        DavinciTabTexture frame = frames.remove(target);
        if (frame != null) frame.close();
    }

    @Override public void close()
    {
        this.raw.delete();
        this.key.close();
        this.cache.close();
    }
}
