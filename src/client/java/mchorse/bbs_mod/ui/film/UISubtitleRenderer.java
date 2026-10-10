package mchorse.bbs_mod.ui.film;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.clips.misc.Subtitle;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.FormPipelines;
import mchorse.bbs_mod.graphics.Framebuffer;
import mchorse.bbs_mod.graphics.Projektion;
import mchorse.bbs_mod.graphics.SofortZeichner;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.texture.TextureFormat;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Renders caption textures into the current world target before export readback. */
public class UISubtitleRenderer
{
    private static final RenderPipeline CAPTION = RenderPipeline.builder()
        .withLocation(Identifier.fromNamespaceAndPath("bbs", "pipeline/subtitles_world"))
        .withVertexShader(Identifier.fromNamespaceAndPath("bbs", "core/subtitles"))
        .withFragmentShader(Identifier.fromNamespaceAndPath("bbs", "core/subtitles"))
        .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
        .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
        .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
        .withBindGroupLayout(BindGroupLayouts.PROJECTION)
        .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
        .withColorTargetState(new ColorTargetState(com.mojang.blaze3d.pipeline.BlendFunction.TRANSLUCENT))
        .withDepthStencilState(Optional.empty()).withCull(false).build();

    private record Layout(List<String> lines, int textWidth, int textHeight, Texture image,
        float imageWidth, float imageHeight, float width, float height)
    {}

    public static void renderSubtitles(PoseStack unusedStack, Batcher2D unusedBatcher, List<Subtitle> subtitles)
    {
        if (subtitles.isEmpty())
        {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        RenderTarget target = BBSRendering.isCustomSize() ? BBSRendering.getFramebuffer() : client.gameRenderer.mainRenderTarget();
        float width = target.width / 2F;
        float height = target.height / 2F;
        com.mojang.blaze3d.buffers.GpuBufferSlice previousProjection = RenderSystem.getProjectionMatrixBuffer();
        ProjectionType previousType = RenderSystem.getProjectionType();
        RenderSystem.getModelViewStack().pushMatrix().identity();
        try (ProjectionMatrixBuffer projection = new ProjectionMatrixBuffer("bbs-subtitle-world"))
        {
            RenderSystem.setProjectionMatrix(projection.getBuffer(Projektion.umgekehrt(
                new Matrix4f().setOrtho(0F, width, height, 0F, -100F, 100F))), ProjectionType.ORTHOGRAPHIC);
            int index = 0;
            for (Subtitle subtitle : subtitles)
            {
                float alpha = Colors.getA(subtitle.color);
                if (alpha <= 0F)
                {
                    continue;
                }
                Layout layout = layout(subtitle);
                Texture texture = renderTexture(subtitle, layout, index++);
                PoseStack pose = new PoseStack();
                pose.translate(width * subtitle.windowX + subtitle.x, height * subtitle.windowY + subtitle.y, 0F);
                Transform transform = new Transform();
                transform.lerp(subtitle.transform, 1F - subtitle.factor);
                MatrixStackUtils.applyTransform(pose, transform);
                float x = -texture.width * subtitle.anchorX;
                float y = -texture.height * subtitle.anchorY;
                drawQuad(texture, CAPTION, pose, x, y, x + texture.width, y + texture.height,
                    untertitelFarbe(subtitle.shadow, subtitle.shadowOpaque, alpha), target.getColorTextureView(), null);
            }
        }
        finally
        {
            RenderSystem.setProjectionMatrix(previousProjection, previousType);
            RenderSystem.getModelViewStack().popMatrix();
        }
    }

    private static Layout layout(Subtitle subtitle)
    {
        Font font = Minecraft.getInstance().font;
        List<String> lines = new ArrayList<>();
        for (String line : StringUtils.processColoredText(subtitle.label).split("\\n", -1))
        {
            lines.addAll(subtitle.maxWidth <= 10 ? List.of(line) : FontRenderer.wrap(font, line, subtitle.maxWidth));
        }
        int w = lines.stream().mapToInt(line -> font.width(line.trim())).max().orElse(0);
        int h = (lines.size() - 1) * subtitle.lineHeight + font.lineHeight;
        Texture image = subtitle.image == null || !BBSModClient.getTextures().has(subtitle.image)
            ? null : BBSModClient.getTextures().getTexture(subtitle.image);
        if (image == BBSModClient.getTextures().getError())
        {
            image = null;
        }
        float imageH = image == null ? 0F : Math.max(0F, (subtitle.lineHeight > 0 ? subtitle.lineHeight : font.lineHeight) * subtitle.imageScale);
        float imageW = image == null || image.height <= 0 ? 0F : image.width * imageH / image.height;
        return new Layout(lines, w, h, image, imageW, imageH,
            w + (imageH > 0F ? 6F + imageW : 0F), Math.max(h, imageH));
    }

    private static Texture renderTexture(Subtitle subtitle, Layout layout, int index)
    {
        Framebuffer framebuffer = BBSModClient.getFramebuffers().getFramebuffer(Link.bbs("camera_subtitles_" + index), f ->
        {
            Texture color = new Texture();
            color.setSize(2, 2);
            color.setFilter(GL11.GL_NEAREST);
            color.setWrap(org.lwjgl.opengl.GL13.GL_CLAMP_TO_EDGE);
            Texture depth = new Texture();
            depth.setFormat(TextureFormat.DEPTH_F24);
            depth.setSize(2, 2);
            f.deleteTextures().attach(color, GL30.GL_COLOR_ATTACHMENT0);
            f.attach(depth, GL30.GL_DEPTH_ATTACHMENT);
            f.unbind();
        });
        int width = Math.max(2, (int) ((layout.width + 10F) * subtitle.size));
        int height = Math.max(2, (int) ((layout.height + 10F) * subtitle.size));
        if (framebuffer.getMainTexture().width != width || framebuffer.getMainTexture().height != height)
        {
            framebuffer.resize(width, height);
        }
        Texture color = framebuffer.getMainTexture();
        Texture depth = framebuffer.textures.get(1);
        com.mojang.blaze3d.textures.GpuTextureView previousColor = RenderSystem.outputColorTextureOverride;
        com.mojang.blaze3d.textures.GpuTextureView previousDepth = RenderSystem.outputDepthTextureOverride;
        com.mojang.blaze3d.buffers.GpuBufferSlice previousProjection = RenderSystem.getProjectionMatrixBuffer();
        ProjectionType previousType = RenderSystem.getProjectionType();
        com.mojang.blaze3d.systems.ScissorState previousScissor = new com.mojang.blaze3d.systems.ScissorState(RenderSystem.getScissorStateForRenderTypeDraws());
        com.mojang.blaze3d.systems.RenderPass.RenderArea previousArea = mchorse.bbs_mod.graphics.Zeichenbereich.aktiv();
        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(color.gpu(), new Vector4f(0F, 0F, 0F, 0F), depth.gpu(), 0D);
        RenderSystem.outputColorTextureOverride = color.view();
        RenderSystem.outputDepthTextureOverride = depth.view();
        RenderSystem.disableScissorForRenderTypeDraws();
        mchorse.bbs_mod.graphics.Zeichenbereich.freigeben();
        try (ProjectionMatrixBuffer projection = new ProjectionMatrixBuffer("bbs-subtitle-text"))
        {
            RenderSystem.setProjectionMatrix(projection.getBuffer(Projektion.umgekehrt(new Matrix4f()
                .setOrtho(0F, layout.width + 10F, layout.height + 10F, 0F, -100F, 100F))), ProjectionType.ORTHOGRAPHIC);
            PoseStack pose = new PoseStack();
            float yy = 5F + (layout.height - layout.textHeight) / 2F;
            float textLeft = 5F + (layout.imageHeight > 0F && !subtitle.imageRight ? layout.imageWidth + 6F : 0F);
            if (Colors.getA(subtitle.backgroundColor) > 0F)
            {
                float offset = subtitle.backgroundOffset;
                drawQuad(null, FormPipelines.holen(PrimitiveTopology.TRIANGLES, true, false, false), pose,
                    5F - offset, yy - offset, 5F + layout.width + offset - 1F, yy + layout.textHeight + offset,
                    Colors.mulA(subtitle.backgroundColor, Colors.getA(subtitle.color)), color.view(), null);
            }
            if (layout.imageHeight > 0F)
            {
                float x = subtitle.imageRight ? 5F + layout.width - layout.imageWidth : 5F;
                float y = 5F + (layout.height - layout.imageHeight) / 2F;
                drawQuad(layout.image, FormPipelines.holen(DefaultVertexFormat.POSITION_TEX_COLOR, "position_tex_color",
                    PrimitiveTopology.TRIANGLES, true, false, false), pose, x, y, x + layout.imageWidth, y + layout.imageHeight,
                    Colors.WHITE, color.view(), null);
            }
            SubmitNodeStorage collector = new SubmitNodeStorage();
            Font font = Minecraft.getInstance().font;
            for (String line : layout.lines)
            {
                String text = line.trim();
                float x = textLeft + (layout.textWidth - font.width(text)) / 2F;
                collector.submitText(pose, x, yy, sink -> net.minecraft.util.StringDecomposer.iterateFormatted(text, Style.EMPTY, sink),
                    subtitle.textShadow, Font.DisplayMode.NORMAL, LightCoordsUtil.FULL_BRIGHT, Colors.setA(subtitle.color, 1F), 0, 0);
                yy += subtitle.lineHeight;
            }
            Minecraft client = Minecraft.getInstance();
            try (FeatureRenderDispatcher dispatcher = new FeatureRenderDispatcher(client.gameRenderer.renderBuffers(),
                client.getModelManager(), client.getAtlasManager(), client.font, client.gameRenderer.gameRenderState()))
            {
                dispatcher.renderAllFeatures(collector);
            }
        }
        finally
        {
            RenderSystem.outputColorTextureOverride = previousColor;
            RenderSystem.outputDepthTextureOverride = previousDepth;
            RenderSystem.setProjectionMatrix(previousProjection, previousType);
            RenderSystem.getScissorStateForRenderTypeDraws().setFrom(previousScissor);
            if (previousArea != null)
            {
                mchorse.bbs_mod.graphics.Zeichenbereich.setzen(previousArea.x(), previousArea.y(), previousArea.width(), previousArea.height());
            }
        }
        return color;
    }

    private static void drawQuad(Texture texture, RenderPipeline pipeline, PoseStack pose,
        float x1, float y1, float x2, float y2, int color,
        com.mojang.blaze3d.textures.GpuTextureView target, com.mojang.blaze3d.textures.GpuTextureView depth)
    {
        try (ByteBufferBuilder memory = new ByteBufferBuilder(256))
        {
            BufferBuilder builder = new BufferBuilder(memory, PrimitiveTopology.QUADS,
                texture == null ? DefaultVertexFormat.POSITION_COLOR : DefaultVertexFormat.POSITION_TEX_COLOR);
            float[][] points = {{x1, y1, 0F, 1F}, {x1, y2, 0F, 0F}, {x2, y2, 1F, 0F}, {x2, y1, 1F, 1F}};
            for (float[] point : points)
            {
                com.mojang.blaze3d.vertex.VertexConsumer vertex = builder.addVertex(pose.last().pose(), point[0], point[1], 0F).setColor(color);
                if (texture != null)
                {
                    vertex.setUv(point[2], point[3]);
                }
            }
            TextureSetup binding = texture == null ? null : TextureSetup.singleTexture(texture.view(), texture.sampler());
            SofortZeichner.zeichnen(pipeline, builder.build(), RenderSystem.getModelViewMatrixCopy(), null, binding, target, depth);
        }
    }

    private static int untertitelFarbe(float blur, boolean opaque, float alpha)
    {
        int r = Math.round(Math.min(Math.max(blur, 0F), 25.5F) * 10F);
        int g = opaque ? 10 : 0;
        int a = Math.round(Math.min(Math.max(alpha, 0F), 1F) * 255F);
        return (a << 24) | (r << 16) | (g << 8);
    }
}
