package mchorse.bbs_mod.forms.renderers;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import mchorse.bbs_mod.graphics.GehaltenesNetz;
import mchorse.bbs_mod.graphics.SofortZeichner;
import net.minecraft.client.renderer.RenderPipelines;

import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.vertex.VertexFormat;
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
import mchorse.bbs_mod.graphics.FramebufferUtils;
import mchorse.bbs_mod.graphics.GpuBackend;
import mchorse.bbs_mod.graphics.Zeichenziel;
import mchorse.bbs_mod.graphics.PickZiel;
import mchorse.bbs_mod.graphics.texture.TextureFormat;
import mchorse.bbs_mod.forms.FormSammler;
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
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FramebufferFormRenderer extends FormRenderer<FramebufferForm>
{
    private static final Quad quad = new Quad();
    private static final Quad uvQuad = new Quad();

    /* Nested framebuffer forms must each render into their own framebuffer */
    private static int depth;

    private IEntity entity = new StubEntity();
    private final List<Framebuffer> nativePool = new ArrayList<>();
    private final Map<NativeKey, Framebuffer> prepared = new HashMap<>();
    private long preparedEpoch = Long.MIN_VALUE;
    private int poolCursor;
    private boolean registered;
    private boolean preparing;
    private static long nativeTargetNumber;

    private record NativeKey(IEntity entity, Object picking, boolean ui, boolean shadow, FormRenderType type,
        int light, int overlay, int color, int transition)
    {
        @Override public boolean equals(Object other)
        {
            return other instanceof NativeKey key && this.entity == key.entity && this.picking == key.picking
                && this.ui == key.ui && this.shadow == key.shadow && this.type == key.type && this.light == key.light
                && this.overlay == key.overlay && this.color == key.color && this.transition == key.transition;
        }
        @Override public int hashCode()
        {
            return java.util.Objects.hash(System.identityHashCode(this.entity), System.identityHashCode(this.picking),
                this.ui, this.shadow, this.type, this.light, this.overlay, this.color, this.transition);
        }
    }

    private static NativeKey key(FormRenderingContext context)
    {
        return new NativeKey(context.entity, context.stencilMap, context.ui, BBSRendering.isIrisShadowPass(), context.type,
            context.light, context.overlay, context.color, Float.floatToIntBits(context.getTransition()));
    }

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
            stack.rotate(Axis.YN.rotationDegrees(180F));
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
        if (GpuBackend.isOpenGl())
        {
            this.renderLegacyBodyParts(context);
            return;
        }
        if (mchorse.bbs_mod.integrations.vitrail.VitrailAttachmentCapture.earlyWorld()
            && !mchorse.bbs_mod.integrations.vitrail.VitrailRetainedMesh.active())
            throw new IllegalStateException("Native framebuffer attachment has no available retained mesh family");
        if ((mchorse.bbs_mod.integrations.vitrail.VitrailAttachmentCapture.earlyWorld()
            || mchorse.bbs_mod.integrations.vitrail.VitrailAttachmentCapture.replaying()
                && mchorse.bbs_mod.integrations.vitrail.VitrailAttachmentCapture.ownedWorld())
            && mchorse.bbs_mod.integrations.vitrail.VitrailAttachmentCapture.mesh("framebuffer")) return;
        Framebuffer framebuffer;
        if (FramebufferUtils.inPass())
        {
            framebuffer = this.preparedEpoch == FramebufferFormPreparation.epoch() ? this.prepared.get(key(context)) : null;
            if (framebuffer == null || framebuffer.isDeleted())
                throw new IllegalStateException("Framebuffer form was not prepared before the native world pass");
        }
        else framebuffer = this.prepareNative(context);
        boolean shading = !context.isPicking();
        VertexFormat format = shading ? DefaultVertexFormat.ENTITY : DefaultVertexFormat.POSITION_TEX_COLOR;
        Supplier<RenderPipeline> shader = shading ? () -> RenderPipelines.ENTITY_TRANSLUCENT
            : () -> mchorse.bbs_mod.client.FormPipelines.holen(DefaultVertexFormat.POSITION_TEX_COLOR,
                "position_tex_color", PrimitiveTopology.TRIANGLES, true, true, false);
        this.renderModel(framebuffer.getMainTexture(), format, shader, context.stack, context.overlay,
            context.light, context.color, context.getTransition(), !context.isPicking());
    }

    void beginPreparationFrame(long epoch)
    {
        this.preparedEpoch = epoch;
        this.prepared.clear();
        this.poolCursor = 0;
    }

    Framebuffer prepareNative(FormRenderingContext context)
    {
        FramebufferUtils.outsidePass("Framebuffer form preparation");
        if (this.preparing) throw new IllegalStateException("Cyclic framebuffer form rendering");
        long epoch = FramebufferFormPreparation.epoch();
        if (epoch != this.preparedEpoch)
        {
            this.beginPreparationFrame(epoch);
        }
        NativeKey key = key(context);
        Framebuffer existing = this.prepared.get(key);
        if (!context.isPicking() && !context.ui && existing != null && !existing.isDeleted()) return existing;
        if (!this.registered)
        {
            FramebufferFormPreparation.register(this, this.nativePool); this.registered = true;
        }
        int slot = this.poolCursor++;
        Framebuffer framebuffer = slot < this.nativePool.size() ? this.nativePool.get(slot) : null;
        if (framebuffer == null || framebuffer.isDeleted())
        {
            framebuffer = BBSModClient.getFramebuffers().getFramebuffer(Link.bbs("framebuffer_form_native_" + ++nativeTargetNumber), f ->
            {
                Texture color = new Texture(); color.setSize(2, 2);
                color.setFilter(GL11.GL_NEAREST); color.setWrap(GL13.GL_CLAMP_TO_EDGE);
                Texture depth = new Texture(); depth.setFormat(TextureFormat.DEPTH_F24); depth.setSize(2, 2);
                depth.setFilter(GL11.GL_NEAREST); depth.setWrap(GL13.GL_CLAMP_TO_EDGE);
                f.deleteTextures().attach(color, GL30.GL_COLOR_ATTACHMENT0).attach(depth, GL30.GL_DEPTH_ATTACHMENT);
            });
            if (slot < this.nativePool.size()) this.nativePool.set(slot, framebuffer); else this.nativePool.add(framebuffer);
        }
        int w = MathUtils.clamp(this.form.width.get(), 2, 4096);
        int h = MathUtils.clamp(this.form.height.get(), 2, 4096);
        if (framebuffer.getMainTexture().width != w || framebuffer.getMainTexture().height != h) framebuffer.resize(w, h);
        var previousLight = RenderSystem.getShaderLights();
        Matrix4f previousProjection = new Matrix4f(mchorse.bbs_mod.graphics.Projektion.lesen());
        var previousProjectionType = RenderSystem.getProjectionType();
        var previousPick = PickZiel.aktiv(); var previousPickDepth = PickZiel.tiefe();
        boolean previousCollecting = mchorse.bbs_mod.film.FilmControllerContext.sammelDurchgang;
        boolean previousWorldLabel = context.worldLabel;
        boolean queueWasActive = FormTranslucentQueue.suspend();
        /* A form-list cell scissors Mojang's draws to its screen rectangle; inside this buffer that rectangle is
         * out of bounds, the block draw threw mid-pass and the next frame died on the open pass (2026-10-15). */
        var previousScissor = new com.mojang.blaze3d.systems.ScissorState(RenderSystem.getScissorStateForRenderTypeDraws());
        RenderSystem.disableScissorForRenderTypeDraws();
        RenderSystem.getModelViewStack().pushMatrix();
        context.stack.pushPose();
        boolean bound = false;
        this.preparing = true;
        try
        {
            framebuffer.apply(); bound = true;
            framebuffer.clear();
            if (context.isPicking()) PickZiel.anmelden(framebuffer.colorView(), framebuffer.depthView());
            Minecraft.getInstance().gameRenderer.lighting().setupFor(com.mojang.blaze3d.platform.Lighting.Entry.ITEMS_FLAT);
            mchorse.bbs_mod.graphics.Projektion.setzenUndUmkehren(new Matrix4f().setOrtho(-1F, 1F, 1F, -1F, -500F, 500F),
                com.mojang.blaze3d.ProjectionType.ORTHOGRAPHIC);
            RenderSystem.getModelViewStack().identity();
            context.stack.last().pose().identity(); context.stack.last().normal().identity();
            float scale = this.form.scale.get(); context.stack.scale(scale, scale, scale);
            mchorse.bbs_mod.film.FilmControllerContext.sammelDurchgang = false;
            context.worldLabel = false;
            FormSammler.mitEigenemSammler(() -> super.renderBodyParts(context));
        }
        finally
        {
            if (bound) framebuffer.unbind();
            RenderSystem.getScissorStateForRenderTypeDraws().setFrom(previousScissor);
            PickZiel.anmelden(previousPick, previousPickDepth);
            context.stack.popPose();
            RenderSystem.getModelViewStack().popMatrix();
            RenderSystem.setShaderLights(previousLight);
            mchorse.bbs_mod.graphics.Projektion.setzen(previousProjection, previousProjectionType);
            FormTranslucentQueue.restore(queueWasActive);
            mchorse.bbs_mod.film.FilmControllerContext.sammelDurchgang = previousCollecting;
            context.worldLabel = previousWorldLabel;
            this.preparing = false;
        }
        this.prepared.put(key, framebuffer);
        return framebuffer;
    }

    private void renderLegacyBodyParts(FormRenderingContext context)
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
        /* 26.2: die Lichtrichtungen sind kein Feld-Paar mehr, sondern ein Uniform-Block
         * auf der Grafikkarte. Gesichert wird der Ausschnitt selbst. */
        com.mojang.renderpearl.api.buffers.GpuBufferSlice gesichertesLicht = RenderSystem.getShaderLights();
        Matrix4f projectionMatrix = new Matrix4f(mchorse.bbs_mod.graphics.Projektion.lesen());

        GL30.glCullFace(GL30.GL_FRONT);
        /* Beide Lichter frontal -- die Form soll flach und gleichmaessig ausgeleuchtet in
         * ihren Puffer, ohne Schattierung von der Weltbeleuchtung. Vanilla hat genau dafuer
         * eine Voreinstellung; die beiden Richtungsvektoren von Hand gibt es nicht mehr. */
        Minecraft.getInstance().gameRenderer.lighting().setupFor(com.mojang.blaze3d.platform.Lighting.Entry.ITEMS_FLAT);
        /* Umkehren: BBS-eigene Matrix in 26.2s GREATER_EQUAL-Test, Beleg an Projektion#setzenUndUmkehren. In BILDPUNKTEN an dieser Stelle ungemessen. */
        mchorse.bbs_mod.graphics.Projektion.setzenUndUmkehren(new Matrix4f().setOrtho(-1F, 1F, 1F, -1F, -500F, 500F), com.mojang.blaze3d.ProjectionType.ORTHOGRAPHIC);
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

        framebuffer.unbind();
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDraw);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevRead);
        GL30.glViewport(0, 0, width, height);

        RenderSystem.setShaderLights(gesichertesLicht);
        RenderSystem.getModelViewStack().popMatrix();
        mchorse.bbs_mod.graphics.Projektion.setzen(projectionMatrix, com.mojang.blaze3d.ProjectionType.ORTHOGRAPHIC);
        GL30.glCullFace(GL30.GL_BACK);

        boolean shading = !context.isPicking();
        /* ⚠️ 26.2: der unbeschattete Zweig schreibt POSITION_TEX_COLOR statt
         * POSITION_TEX_LIGHTMAP_COLOR -- gleicher Grund wie in BillboardFormRenderer: der
         * Shader dahinter (core/position_tex_color) kennt nur Position, UV0 und Color, die
         * Lichtkoordinate wurde nie gelesen. In 26.2 wird das Format zur Pipeline-Bindung,
         * und ein Attribut, das der Shader nicht kennt, ist dort ein Widerspruch. */
        VertexFormat format = shading ? DefaultVertexFormat.ENTITY : DefaultVertexFormat.POSITION_TEX_COLOR;
        Supplier<RenderPipeline> shader = shading
            ? () -> RenderPipelines.ENTITY_TRANSLUCENT
            /* getPositionTexColorShader ist entfallen: Shader, Blend, Tiefe und Cull stehen in
             * der Pipeline. Gemischt (das Bild hat einen durchsichtigen Hintergrund), Tiefe an,
             * beidseitig (der Quad wird vorn UND hinten geschrieben). */
            : () -> mchorse.bbs_mod.client.FormPipelines.holen(DefaultVertexFormat.POSITION_TEX_COLOR, "position_tex_color", PrimitiveTopology.TRIANGLES, true, true, false);

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

        /* 26.2: setShader entfaellt -- der Shader steht in der Pipeline, die weiter unten
         * gewaehlt wird. Der Filter bleibt am Texturobjekt; er gehoert zur Textur, nicht
         * zum Zeichenzustand. */
        texture.setFilterMipmap(false, false);

        /* Shader engines extend the entity binding; their builder mixin writes its extra fields. */
        RenderPipeline selectedPipeline = shader.get();
        format = selectedPipeline.getVertexFormatBinding(0);
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

        boolean early = defer && mchorse.bbs_mod.integrations.vitrail.VitrailAttachmentCapture.earlyWorld();
        if (defer && (FormTranslucentQueue.isActive() || early))
        {
            /* The framebuffer's content is transparent-background by nature, so the whole quad
             * defers into the sorted translucent pass. The command binds the framebuffer's live
             * texture at flush — with several framebuffer forms at the same nesting depth they
             * share one framebuffer, so their deferred quads would all show the last-rendered
             * content; a known trade-off of the shared framebuffer scheme. */
            RenderPipeline finalShader = selectedPipeline;
            GehaltenesNetz netz = new GehaltenesNetz(builder.buildOrThrow());

            Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrixCopy());
            Vector3f origin = modelView.transformPosition(matrix.getTranslation(new Vector3f()));

            FormTranslucentQueue.NetzBefehl command = new FormTranslucentQueue.NetzBefehl(
                netz, () -> finalShader, texture, modelView, null, origin, true, null, null
            );
            if (early) mchorse.bbs_mod.integrations.vitrail.VitrailAttachmentCapture.retain(command);
            else FormTranslucentQueue.add(command);
        }
        else
        {
            /* build() gibt null zurueck, wenn nichts gezeichnet wurde -- SofortZeichner
             * kommt damit zurecht und zeichnet dann nichts. */
            SofortZeichner.zeichnen(selectedPipeline, builder.build(),
                new Matrix4f(RenderSystem.getModelViewMatrixCopy()), null,
                format == DefaultVertexFormat.ENTITY ? SofortZeichner.modellTextur(texture) : SofortZeichner.entityTextur(texture), null); /* the entity pipeline declares Sampler1 too: Vulkan refused the draw without it */
        }

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
