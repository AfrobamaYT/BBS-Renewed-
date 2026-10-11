package mchorse.bbs_mod.forms.renderers;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import mchorse.bbs_mod.graphics.GehaltenesNetz;
import mchorse.bbs_mod.graphics.SofortZeichner;
import net.minecraft.client.renderer.RenderPipelines;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.forms.forms.FramebufferForm;
import mchorse.bbs_mod.graphics.Framebuffer;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.Quad;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.joml.Vectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

import java.util.function.Supplier;

public class FramebufferFormRenderer extends FormRenderer<FramebufferForm>
{
    private static final Quad quad = new Quad();
    private static final Quad uvQuad = new Quad();

    private final Link framebufferId = Link.bbs("framebuffer_form_" + java.util.UUID.randomUUID());
    private static final RenderPipeline SHADED = shadedPipeline();

    private IEntity entity = new StubEntity();

    public FramebufferFormRenderer(FramebufferForm form)
    {
        super(form);
    }

    @Override
    protected void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        if (this.form.parts.getAll().isEmpty())
        {
            context.batcher.icon(Icons.CAMERA, (x1 + x2) / 2, (y1 + y2) / 2, 0.5F, 0.5F);
        }
        else
        {
            PoseStack stack = context.batcher.pose();
            Matrix4f uiMatrix = ModelFormRenderer.getUIMatrix(context, x1, y1, x2, y2);

            /* 26.2: der Tiefentest ist kein Schalter mehr, er steckt in der Pipeline des Zuges.
             * Fuer die Formen darunter traegt ihn ModelVAOPipeline bzw. die jeweilige
             * RenderType-Pipeline -- GL_LEQUAL heisst dort GREATER_THAN_OR_EQUAL, weil 26.2
             * die Tiefe umgekehrt rechnet. Gleicher Umbau wie in ExtrudedFormRenderer. */
            stack.pushPose();

            this.applyTransforms(uiMatrix, context.getTransition());
            MatrixStackUtils.multiply(stack, uiMatrix);
            stack.mulPose(Axis.YN.rotationDegrees(180F));
            stack.last().normal().getScale(Vectors.EMPTY_3F);
            stack.last().normal().scale(1F / Vectors.EMPTY_3F.x, -1F / Vectors.EMPTY_3F.y, 1F / Vectors.EMPTY_3F.z);

            this.renderBodyParts(new FormRenderingContext()
                .set(FormRenderType.ENTITY, this.entity, stack, net.minecraft.util.LightCoordsUtil.pack(15, 15), OverlayTexture.NO_OVERLAY, context.getTransition())
                .inUI());

            stack.popPose();
            /* Das GL_ALWAYS danach setzte den Schalter fuer die Oberflaeche zurueck. Es
             * entfaellt ersatzlos: der GUI-Raum hat keine Tiefe mehr, die Ueberdeckung
             * entscheidet GuiGraphicsExtractor.nextStratum(). */
        }
    }

    @Override
    public void renderBodyParts(FormRenderingContext context)
    {
        Framebuffer framebuffer = BBSModClient.getFramebuffers().getFramebuffer(this.framebufferId, (target) ->
        {
            Texture color = new Texture();
            color.setSize(2, 2);
            color.setFilter(GL11.GL_NEAREST);
            color.setWrap(GL13.GL_CLAMP_TO_EDGE);
            Texture depth = new Texture();
            depth.setFormat(mchorse.bbs_mod.graphics.texture.TextureFormat.DEPTH_F24);
            depth.setSize(2, 2);
            target.deleteTextures().attach(color, GL30.GL_COLOR_ATTACHMENT0);
            target.attach(depth, GL30.GL_DEPTH_ATTACHMENT);
            target.unbind();
        });
        int w = MathUtils.clamp(this.form.width.get(), 2, 4096);
        int h = MathUtils.clamp(this.form.height.get(), 2, 4096);
        if (w != framebuffer.getMainTexture().width || h != framebuffer.getMainTexture().height)
        {
            framebuffer.resize(w, h);
        }
        this.renderPartsToTexture(context, framebuffer);
        boolean picking = context.isPicking();
        /* The base renderer has already registered this form before rendering its parts. */
        int pickingIndex = context.getPickingIndex() - (picking && context.stencilMap.increment ? 1 : 0);
        VertexFormat format = picking ? DefaultVertexFormat.POSITION_TEX_COLOR : DefaultVertexFormat.ENTITY;
        Supplier<RenderPipeline> shader = picking
            ? () -> TexturedFormPicking.pipeline(PrimitiveTopology.TRIANGLES) : () -> SHADED;
        this.renderModel(framebuffer.getMainTexture(), format, shader, context.stack, context.overlay,
            context.light, context.color, context.getTransition(), !picking, pickingIndex);
    }

    private void renderPartsToTexture(FormRenderingContext context, Framebuffer framebuffer)
    {
        com.mojang.blaze3d.textures.GpuTextureView previousColor = RenderSystem.outputColorTextureOverride;
        com.mojang.blaze3d.textures.GpuTextureView previousDepth = RenderSystem.outputDepthTextureOverride;
        com.mojang.blaze3d.textures.GpuTextureView previousModelColor = mchorse.bbs_mod.graphics.Zeichenziel.farbe();
        com.mojang.blaze3d.textures.GpuTextureView previousModelDepth = mchorse.bbs_mod.graphics.Zeichenziel.tiefe();
        com.mojang.blaze3d.textures.GpuTextureView previousPickColor = mchorse.bbs_mod.graphics.PickZiel.aktiv();
        com.mojang.blaze3d.textures.GpuTextureView previousPickDepth = mchorse.bbs_mod.graphics.PickZiel.tiefe();
        com.mojang.blaze3d.systems.RenderPass.RenderArea previousArea = mchorse.bbs_mod.graphics.Zeichenbereich.aktiv();
        com.mojang.blaze3d.buffers.GpuBufferSlice previousLights = RenderSystem.getShaderLights();
        com.mojang.blaze3d.buffers.GpuBufferSlice previousProjection = RenderSystem.getProjectionMatrixBuffer();
        com.mojang.blaze3d.ProjectionType previousType = RenderSystem.getProjectionType();
        com.mojang.blaze3d.systems.ScissorState previousScissor =
            new com.mojang.blaze3d.systems.ScissorState(RenderSystem.getScissorStateForRenderTypeDraws());
        mchorse.bbs_mod.ui.framework.elements.utils.StencilMap previousStencil = context.stencilMap;
        int previousColorValue = context.color;
        boolean queueWasActive = FormTranslucentQueue.suspend();
        Texture color = framebuffer.getMainTexture();
        Texture depth = framebuffer.textures.get(1);
        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(color.gpu(),
            new Vector4f(0F, 0F, 0F, 0F), depth.gpu(), 0D);
        RenderSystem.outputColorTextureOverride = color.view();
        RenderSystem.outputDepthTextureOverride = depth.view();
        mchorse.bbs_mod.graphics.Zeichenziel.setzen(color.view(), depth.view());
        mchorse.bbs_mod.graphics.PickZiel.abmelden();
        mchorse.bbs_mod.graphics.Zeichenbereich.freigeben();
        RenderSystem.disableScissorForRenderTypeDraws();
        Minecraft.getInstance().gameRenderer.lighting().setupFor(com.mojang.blaze3d.platform.Lighting.Entry.ITEMS_FLAT);
        RenderSystem.getModelViewStack().pushMatrix().identity();
        context.stack.pushPose();
        context.stack.last().pose().identity();
        context.stack.last().normal().identity();
        context.stack.scale(this.form.scale.get(), this.form.scale.get(), this.form.scale.get());
        context.stencilMap = null;
        context.color = previousStencil == null ? previousColorValue : 0xffffffff;
        try (net.minecraft.client.renderer.ProjectionMatrixBuffer projection =
            new net.minecraft.client.renderer.ProjectionMatrixBuffer("bbs-framebuffer-form"))
        {
            RenderSystem.setProjectionMatrix(projection.getBuffer(mchorse.bbs_mod.graphics.Projektion.umgekehrt(
                new Matrix4f().setOrtho(-1F, 1F, -1F, 1F, -500F, 500F))), com.mojang.blaze3d.ProjectionType.ORTHOGRAPHIC);
            net.minecraft.client.renderer.SubmitNodeStorage collector = new net.minecraft.client.renderer.SubmitNodeStorage();
            mchorse.bbs_mod.forms.FormSammler.mitSammler(collector, () -> super.renderBodyParts(context));
            Minecraft client = Minecraft.getInstance();
            /* The world dispatcher owns a prepared frame during world rendering. */
            try (net.minecraft.client.renderer.feature.FeatureRenderDispatcher dispatcher =
                new net.minecraft.client.renderer.feature.FeatureRenderDispatcher(client.gameRenderer.renderBuffers(),
                    client.getModelManager(), client.getAtlasManager(), client.font, client.gameRenderer.gameRenderState()))
            {
                dispatcher.renderAllFeatures(collector);
            }
        }
        finally
        {
            context.stencilMap = previousStencil;
            context.color = previousColorValue;
            context.stack.popPose();
            RenderSystem.getModelViewStack().popMatrix();
            RenderSystem.setProjectionMatrix(previousProjection, previousType);
            RenderSystem.setShaderLights(previousLights);
            RenderSystem.getScissorStateForRenderTypeDraws().setFrom(previousScissor);
            RenderSystem.outputColorTextureOverride = previousColor;
            RenderSystem.outputDepthTextureOverride = previousDepth;
            mchorse.bbs_mod.graphics.Zeichenziel.setzen(previousModelColor, previousModelDepth);
            mchorse.bbs_mod.graphics.PickZiel.anmelden(previousPickColor, previousPickDepth);
            if (previousArea != null)
            {
                mchorse.bbs_mod.graphics.Zeichenbereich.setzen(previousArea.x(), previousArea.y(), previousArea.width(), previousArea.height());
            }
            FormTranslucentQueue.restore(queueWasActive);
        }
    }

    private static RenderPipeline shadedPipeline()
    {
        RenderPipeline source = RenderPipelines.ENTITY_TRANSLUCENT;
        RenderPipeline.Builder builder = RenderPipeline.builder()
            .withLocation(net.minecraft.resources.Identifier.fromNamespaceAndPath("bbs", "pipeline/framebuffer_entity"))
            .withVertexShader(source.getVertexShader()).withFragmentShader(source.getFragmentShader())
            .withVertexBinding(0, DefaultVertexFormat.ENTITY).withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withDepthStencilState(source.getDepthStencilState()).withColorTargetState(source.getColorTargetState())
            .withShaderDefine("ALPHA_CUTOUT", 0.1F).withShaderDefine("PER_FACE_LIGHTING").withCull(false);
        for (com.mojang.blaze3d.pipeline.BindGroupLayout layout : source.getBindGroupLayouts())
        {
            builder.withBindGroupLayout(layout);
        }
        return builder.build();
    }

    private void renderModel(Texture texture, VertexFormat format, Supplier<RenderPipeline> shader, PoseStack matrices, int overlay, int light, int overlayColor, float transition, boolean defer, int pickingIndex)
    {
        float w = texture.width;
        float h = texture.height;

        /* TL = top left, BR = bottom right*/
        Vector4f crop = new Vector4f(0, 0, 0, 0);
        float uvTLx = crop.x / w;
        float uvTLy = crop.y / h;
        float uvBRx = 1 - crop.z / w;
        float uvBRy = 1 - crop.w / h;

        uvQuad.p1.set(uvTLx, 1F - uvTLy, 0);
        uvQuad.p2.set(uvBRx, 1F - uvTLy, 0);
        uvQuad.p3.set(uvTLx, 1F - uvBRy, 0);
        uvQuad.p4.set(uvBRx, 1F - uvBRy, 0);

        /* Calculate quad's size (vertices, not UV) */
        float ratioX = w > h ? h / w : 1F;
        float ratioY = h > w ? w / h : 1F;
        float TLx = (uvTLx - 0.5F) * ratioY;
        float TLy = -(uvTLy - 0.5F) * ratioX;
        float BRx = (uvBRx - 0.5F) * ratioY;
        float BRy = -(uvBRy - 0.5F) * ratioX;

        quad.p1.set(TLx, TLy, 0);
        quad.p2.set(BRx, TLy, 0);
        quad.p3.set(TLx, BRy, 0);
        quad.p4.set(BRx, BRy, 0);

        this.renderQuad(format, texture, shader, matrices, overlay, light, overlayColor, transition, defer, pickingIndex);
    }

    private void renderQuad(VertexFormat format, Texture texture, Supplier<RenderPipeline> shader, PoseStack matrices, int overlay, int light, int overlayColor, float transition, boolean defer, int pickingIndex)
    {
        Color color = Color.white();
        Matrix4f matrix = matrices.last().pose();
        PoseStack.Pose entry = matrices.last();

        color.mul(overlayColor);
        if (pickingIndex >= 0)
        {
            color.set(TexturedFormPicking.color(pickingIndex, (int) (color.a * 255F)), true);
        }

        GameRenderer gameRenderer = Minecraft.getInstance().gameRenderer;

        // 26.2: turnOn/OffLightLayer entfaellt -- die Lichtkarte ist kein globaler Zustand mehr, sondern Sampler2 des Zeichenaufrufs (siehe SofortZeichner.entityTextur). Wer hier zeichnet, muss sie binden; ein globaler Schalter erreicht die Pipeline nicht.
        // 26.2: setup/teardownOverlayColor entfaellt -- das Overlay ist kein globaler Zustand mehr, sondern Sampler1 des Zeichenaufrufs (SofortZeichner bindet alle drei).

        /* 26.2: setShader entfaellt -- der Shader steht in der Pipeline, die weiter unten
         * gewaehlt wird. Der Filter bleibt am Texturobjekt; er gehoert zur Textur, nicht
         * zum Zeichenzustand. */
        texture.setFilterMipmap(false, false);

        ByteBufferBuilder eckenSpeicher = new ByteBufferBuilder(4096);
        BufferBuilder builder = SofortZeichner.beginnen(eckenSpeicher, PrimitiveTopology.TRIANGLES, format);

        /* Front */
        this.fill(format, builder, matrix, quad.p3.x, quad.p3.y, color, uvQuad.p3.x, uvQuad.p3.y, overlay, light, entry, 1F);
        this.fill(format, builder, matrix, quad.p2.x, quad.p2.y, color, uvQuad.p2.x, uvQuad.p2.y, overlay, light, entry, 1F);
        this.fill(format, builder, matrix, quad.p1.x, quad.p1.y, color, uvQuad.p1.x, uvQuad.p1.y, overlay, light, entry, 1F);

        this.fill(format, builder, matrix, quad.p3.x, quad.p3.y, color, uvQuad.p3.x, uvQuad.p3.y, overlay, light, entry, 1F);
        this.fill(format, builder, matrix, quad.p4.x, quad.p4.y, color, uvQuad.p4.x, uvQuad.p4.y, overlay, light, entry, 1F);
        this.fill(format, builder, matrix, quad.p2.x, quad.p2.y, color, uvQuad.p2.x, uvQuad.p2.y, overlay, light, entry, 1F);

        /* Back */
        this.fill(format, builder, matrix, quad.p1.x, quad.p1.y, color, uvQuad.p1.x, uvQuad.p1.y, overlay, light, entry, -1F);
        this.fill(format, builder, matrix, quad.p2.x, quad.p2.y, color, uvQuad.p2.x, uvQuad.p2.y, overlay, light, entry, -1F);
        this.fill(format, builder, matrix, quad.p3.x, quad.p3.y, color, uvQuad.p3.x, uvQuad.p3.y, overlay, light, entry, -1F);

        this.fill(format, builder, matrix, quad.p2.x, quad.p2.y, color, uvQuad.p2.x, uvQuad.p2.y, overlay, light, entry, -1F);
        this.fill(format, builder, matrix, quad.p4.x, quad.p4.y, color, uvQuad.p4.x, uvQuad.p4.y, overlay, light, entry, -1F);
        this.fill(format, builder, matrix, quad.p3.x, quad.p3.y, color, uvQuad.p3.x, uvQuad.p3.y, overlay, light, entry, -1F);

        /* 26.2: Mischen ist kein globaler Schalter mehr -- die gewaehlte Pipeline bringt
         * ihre Mischfunktion mit. Die Absicht (normales Alpha-Mischen) steckt jetzt in
         * der Wahl unten und nicht mehr in diesen zwei Zeilen. */

        if (defer && FormTranslucentQueue.isActive())
        {
            /* Each form owns its texture until the sorted translucent draw completes. */
            RenderPipeline finalShader = shader.get();
            GehaltenesNetz netz = new GehaltenesNetz(builder.buildOrThrow());

            Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrixCopy());
            Vector3f origin = modelView.transformPosition(matrix.getTranslation(new Vector3f()));

            net.minecraft.client.gui.render.TextureSetup bindings = this.textureSetup(texture, false);
            FormTranslucentQueue.add(new FormTranslucentQueue.DrawCommand(origin, false, false)
            {
                @Override
                public void draw()
                {
                    netz.zeichnen(finalShader, modelView, null, bindings,
                        (com.mojang.blaze3d.pipeline.RenderTarget) null, null);
                }

                @Override
                public void release()
                {
                    netz.close();
                }
            });
        }
        else
        {
            /* build() gibt null zurueck, wenn nichts gezeichnet wurde -- SofortZeichner
             * kommt damit zurecht und zeichnet dann nichts. */
            net.minecraft.client.gui.render.TextureSetup bindings = this.textureSetup(texture, pickingIndex >= 0);
            SofortZeichner.zeichnen(shader.get(), builder.build(),
                new Matrix4f(RenderSystem.getModelViewMatrixCopy()), null, bindings,
                pickingIndex >= 0 ? mchorse.bbs_mod.graphics.PickZiel.pflicht() : RenderSystem.outputColorTextureOverride,
                pickingIndex >= 0 ? mchorse.bbs_mod.graphics.PickZiel.tiefe() : RenderSystem.outputDepthTextureOverride);
        }

    }

    private net.minecraft.client.gui.render.TextureSetup textureSetup(Texture texture, boolean picking)
    {
        if (picking)
        {
            return net.minecraft.client.gui.render.TextureSetup.singleTexture(texture.view(), texture.sampler());
        }
        GameRenderer renderer = Minecraft.getInstance().gameRenderer;
        com.mojang.blaze3d.textures.GpuSampler sampler = RenderSystem.getSamplerCache()
            .getClampToEdge(com.mojang.blaze3d.textures.FilterMode.NEAREST);
        return new net.minecraft.client.gui.render.TextureSetup(texture.view(),
            renderer.overlayTexture().getTextureView(), renderer.lightmap(), texture.sampler(), sampler, sampler);
    }

    private VertexConsumer fill(VertexFormat format, VertexConsumer consumer, Matrix4f matrix, float x, float y, Color color, float u, float v, int overlay, int light, PoseStack.Pose entry, float nz)
    {
        if (format == DefaultVertexFormat.POSITION_TEX_COLOR)
        {
            /* Ohne setLight: das Format hat keine Lichtkoordinate mehr, und der Shader
             * core/position_tex_color hat sie nie gelesen. Siehe renderBodyParts. */
            return consumer.addVertex(matrix, x, y, 0F).setUv(u, v).setColor(color.r, color.g, color.b, color.a);
        }

        return consumer.addVertex(matrix, x, y, 0F).setColor(color.r, color.g, color.b, color.a).setUv(u, v).setOverlay(overlay).setLight(light).setNormal(entry, 0F, 0F, nz);
    }
}
