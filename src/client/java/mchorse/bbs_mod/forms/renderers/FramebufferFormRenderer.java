package mchorse.bbs_mod.forms.renderers;

import net.minecraft.client.renderer.RenderPipelines;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.forms.forms.FramebufferForm;
import mchorse.bbs_mod.graphics.Framebuffer;
import mchorse.bbs_mod.graphics.Renderbuffer;
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
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;
import java.util.function.Supplier;

public class FramebufferFormRenderer extends FormRenderer<FramebufferForm>
{
    private static final Quad quad = new Quad();
    private static final Quad uvQuad = new Quad();

    /* Nested framebuffer forms must each render into their own framebuffer */
    private static int depth;

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
            PoseStack stack = context.batcher.getContext().pose();
            Matrix4f uiMatrix = ModelFormRenderer.getUIMatrix(context, x1, y1, x2, y2);

            RenderSystem.depthFunc(GL11.GL_LEQUAL);
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
            RenderSystem.depthFunc(GL11.GL_ALWAYS);
        }
    }

    @Override
    public void renderBodyParts(FormRenderingContext context)
    {
        Framebuffer framebuffer = BBSModClient.getFramebuffers().getFramebuffer(Link.bbs("framebuffer_form_" + depth), (f) ->
        {
            Texture texture = new Texture();

            texture.setSize(2, 2);
            texture.setFilter(GL11.GL_NEAREST);
            texture.setWrap(GL13.GL_CLAMP_TO_EDGE);

            Renderbuffer renderbuffer = new Renderbuffer();

            renderbuffer.resize(2, 2);

            f.deleteTextures().attach(texture, GL30.GL_COLOR_ATTACHMENT0);
            f.attach(renderbuffer);
            f.unbind();
        });

        int width;
        int height;

        try (MemoryStack stack = MemoryStack.stackPush())
        {
            IntBuffer viewport = stack.mallocInt(4);

            GL30.glGetIntegerv(GL30.GL_VIEWPORT, viewport);

            width = viewport.get(2);
            height = viewport.get(3);
        }

        Texture mainTexture = framebuffer.getMainTexture();
        int w = MathUtils.clamp(this.form.width.get(), 2, 4096);
        int h = MathUtils.clamp(this.form.height.get(), 2, 4096);
        int prevDraw = GL30.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int prevRead = GL30.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        Vector3f light0 = RenderSystem.shaderLightDirections[0];
        Vector3f light1 = RenderSystem.shaderLightDirections[1];
        Matrix4f projectionMatrix = new Matrix4f(RenderSystem.getProjectionMatrix());

        GL30.glCullFace(GL30.GL_FRONT);
        RenderSystem.setShaderLights(new Vector3f(0F, 0F, 1F), new Vector3f(0F, 0F, 1F));
        RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(-1F, 1F, 1F, -1F, -500F, 500F), VertexSorting.ORTHOGRAPHIC_Z);
        RenderSystem.getModelViewStack().pushMatrix();
        RenderSystem.getModelViewStack().identity();

        framebuffer.apply();

        if (w != mainTexture.width || h != mainTexture.height)
        {
            framebuffer.resize(w, h);
        }

        framebuffer.clear();

        float scale = this.form.scale.get();

        context.stack.pushPose();
        context.stack.last().pose().identity();
        context.stack.last().normal().identity();
        context.stack.scale(scale, scale, scale);

        depth += 1;

        if (depth == 1)
        {
            BBSRendering.setIrisMainBound(false);
        }

        /* The nested forms render under an ortho projection into this framebuffer — deferring
         * their translucent pixels into the world's queue would replay them with the wrong
         * projection, so they render single-pass as before. */
        boolean queueWasActive = FormTranslucentQueue.suspend();

        try
        {
            super.renderBodyParts(context);
        }
        finally
        {
            depth -= 1;

            if (depth == 0)
            {
                BBSRendering.setIrisMainBound(true);
            }

            FormTranslucentQueue.restore(queueWasActive);
        }

        context.stack.popPose();

        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDraw);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevRead);
        GL30.glViewport(0, 0, width, height);

        RenderSystem.setShaderLights(light0, light1);
        RenderSystem.getModelViewStack().popMatrix();
        RenderSystem.setProjectionMatrix(projectionMatrix, VertexSorting.ORTHOGRAPHIC_Z);
        GL30.glCullFace(GL30.GL_BACK);

        boolean shading = !context.isPicking();
        VertexFormat format = shading ? DefaultVertexFormat.ENTITY : DefaultVertexFormat.POSITION_TEX_LIGHTMAP_COLOR;
        Supplier<RenderPipeline> shader = shading ? () -> RenderPipelines.ENTITY_TRANSLUCENT : GameRenderer::getPositionTexColorShader;

        this.renderModel(framebuffer.getMainTexture(), format, shader, context.stack, context.overlay, context.light, context.color, context.getTransition(), !context.isPicking());
    }

    private void renderModel(Texture texture, VertexFormat format, Supplier<RenderPipeline> shader, PoseStack matrices, int overlay, int light, int overlayColor, float transition, boolean defer)
    {
        float w = texture.width;
        float h = texture.height;

        /* TL = top left, BR = bottom right*/
        Vector4f crop = new Vector4f(0, 0, 0, 0);
        float uvTLx = crop.x / w;
        float uvTLy = crop.y / h;
        float uvBRx = 1 - crop.z / w;
        float uvBRy = 1 - crop.w / h;

        uvQuad.p1.set(uvTLx, uvTLy, 0);
        uvQuad.p2.set(uvBRx, uvTLy, 0);
        uvQuad.p3.set(uvTLx, uvBRy, 0);
        uvQuad.p4.set(uvBRx, uvBRy, 0);

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

        this.renderQuad(format, texture, shader, matrices, overlay, light, overlayColor, transition, defer);
    }

    private void renderQuad(VertexFormat format, Texture texture, Supplier<RenderPipeline> shader, PoseStack matrices, int overlay, int light, int overlayColor, float transition, boolean defer)
    {
        Color color = Color.white();
        Matrix4f matrix = matrices.last().pose();
        PoseStack.Pose entry = matrices.last();

        color.mul(overlayColor);

        GameRenderer gameRenderer = Minecraft.getInstance().gameRenderer;

        // 26.2: turnOn/OffLightLayer entfaellt -- die Lichtkarte ist kein globaler Zustand mehr, sondern Sampler2 des Zeichenaufrufs (siehe SofortZeichner.entityTextur). Wer hier zeichnet, muss sie binden; ein globaler Schalter erreicht die Pipeline nicht.
        // 26.2: setup/teardownOverlayColor entfaellt -- das Overlay ist kein globaler Zustand mehr, sondern Sampler1 des Zeichenaufrufs (SofortZeichner bindet alle drei).

        BBSModClient.getTextures().bindTexture(texture);
        RenderSystem.setShader(shader);

        texture.bind();
        texture.setFilterMipmap(false, false);
        BufferBuilder builder = Tesselator.getInstance().begin(PrimitiveTopology.TRIANGLES, format);

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

        RenderSystem.defaultBlendFunc();
        RenderSystem.enableBlend();

        if (defer && FormTranslucentQueue.isActive())
        {
            /* The framebuffer's content is transparent-background by nature, so the whole quad
             * defers into the sorted translucent pass. The command binds the framebuffer's live
             * texture at flush — with several framebuffer forms at the same nesting depth they
             * share one framebuffer, so their deferred quads would all show the last-rendered
             * content; a known trade-off of the shared framebuffer scheme. */
            RenderPipeline finalShader = RenderSystem.getShader();
            VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);

            buffer.bind();
            buffer.upload(builder.buildOrThrow());
            VertexBuffer.unbind();

            Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrixCopy());
            Vector3f origin = modelView.transformPosition(matrix.getTranslation(new Vector3f()));

            FormTranslucentQueue.add(new FormTranslucentQueue.VertexBufferCommand(
                buffer, () -> finalShader, texture, modelView, null, origin, true, null, null
            ));
        }
        else
        {
            { com.mojang.blaze3d.vertex.MeshData __bbsBuilt = builder.build();
                if (__bbsBuilt != null) BufferUploader.drawWithShader(__bbsBuilt); }
            /* MERGE fs-2.5: ihr translucent-queue-zweig uebernommen, unsere
             * null-absicherung darueber. builder.end() wirft, wenn nichts gezeichnet
             * wurde; endNullable() gibt dann null. Beides wird gebraucht. */
        }

    }

    private VertexConsumer fill(VertexFormat format, VertexConsumer consumer, Matrix4f matrix, float x, float y, Color color, float u, float v, int overlay, int light, PoseStack.Pose entry, float nz)
    {
        if (format == DefaultVertexFormat.POSITION_TEX_LIGHTMAP_COLOR)
        {
            return consumer.addVertex(matrix, x, y, 0F).setUv(u, v).setLight(light).setColor(color.r, color.g, color.b, color.a);
        }

        return consumer.addVertex(matrix, x, y, 0F).setColor(color.r, color.g, color.b, color.a).setUv(u, v).setOverlay(overlay).setLight(light).setNormal(entry, 0F, 0F, nz);
    }
}