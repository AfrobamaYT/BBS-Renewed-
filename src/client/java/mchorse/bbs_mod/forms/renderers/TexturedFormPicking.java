package mchorse.bbs_mod.forms.renderers;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import mchorse.bbs_mod.graphics.PickZiel;
import mchorse.bbs_mod.graphics.SofortZeichner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import org.joml.Matrix4f;

import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;

/** Alpha-masked object ids for interleaved textured geometry. */
public final class TexturedFormPicking
{
    private static final Map<PrimitiveTopology, RenderPipeline> PIPELINES = new EnumMap<>(PrimitiveTopology.class);

    private TexturedFormPicking()
    {}

    public static RenderPipeline pipeline(PrimitiveTopology topology)
    {
        return PIPELINES.computeIfAbsent(topology, (key) -> RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("bbs", "pipeline/pick_texture_" + key.name().toLowerCase(Locale.ROOT)))
            .withVertexShader(Identifier.fromNamespaceAndPath("bbs", "core/pick_texture"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("bbs", "core/pick_texture"))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(key)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withCull(false)
            .build());
    }

    public static int color(int index, int alpha)
    {
        return (alpha << 24) | ((index & 255) << 16) | (index & 65280) | ((index >> 16) & 255);
    }

    public static void text(FormRenderingContext context, FormattedCharSequence sequence, float x, float y, int visibleColor)
    {
        int encoded = color(context.getPickingIndex(), visibleColor >>> 24);
        FormattedCharSequence picking = (sink) -> sequence.accept((index, style, point) ->
            sink.accept(index, style.withColor(encoded & 16777215), point));
        Map<GpuTextureView, ByteBufferBuilder> memory = new IdentityHashMap<>();
        Map<GpuTextureView, BufferBuilder> batches = new IdentityHashMap<>();

        try
        {
            Minecraft.getInstance().font.prepareText(picking, x, y, encoded, false, false, 0).visit(new Font.GlyphVisitor()
            {
                @Override
                public void acceptRenderable(TextRenderable glyph)
                {
                    GpuTextureView texture = glyph.textureView();
                    BufferBuilder builder = batches.computeIfAbsent(texture, (key) ->
                    {
                        ByteBufferBuilder buffer = new ByteBufferBuilder(4096);
                        memory.put(key, buffer);
                        return SofortZeichner.beginnen(buffer, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                    });
                    glyph.render(context.stack.last().pose(), builder, LightCoordsUtil.FULL_BRIGHT, false);
                }
            });

            for (Map.Entry<GpuTextureView, BufferBuilder> entry : batches.entrySet())
            {
                TextureSetup texture = TextureSetup.singleTexture(entry.getKey(), RenderSystem.getSamplerCache()
                    .getSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.NEAREST, FilterMode.NEAREST, false));
                SofortZeichner.zeichnen(pipeline(PrimitiveTopology.QUADS), entry.getValue().build(),
                    new Matrix4f(RenderSystem.getModelViewMatrixCopy()), null, texture, PickZiel.pflicht(), PickZiel.tiefe());
            }
        }
        finally
        {
            for (ByteBufferBuilder buffer : memory.values())
            {
                buffer.close();
            }
        }
    }
}
