package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.graphics.GehaltenesNetz;
import mchorse.bbs_mod.graphics.SofortZeichner;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import net.minecraft.client.renderer.RenderPipelines;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.client.FormPipelines;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.forms.forms.BillboardForm;
import mchorse.bbs_mod.forms.renderers.utils.FormColorBlend;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.Quad;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.function.Supplier;

public class BillboardFormRenderer extends FormRenderer<BillboardForm>
{
    private static final Quad quad = new Quad();
    private static final Quad uvQuad = new Quad();

    private static final Matrix4f matrix = new Matrix4f();

    public BillboardFormRenderer(BillboardForm form)
    {
        super(form);
    }

    @Override
    public void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        PoseStack stack = context.batcher.pose();

        stack.pushPose();

        Matrix4f uiMatrix = ModelFormRenderer.getUIMatrix(context, x1, y1, x2, y2);

        this.applyTransforms(uiMatrix, context.getTransition());
        MatrixStackUtils.multiply(stack, uiMatrix);
        stack.translate(0F, 1F, 0F);
        stack.scale(1.5F, 1.5F, 1.5F);
        stack.scale(this.form.uiScale.get(), this.form.uiScale.get(), this.form.uiScale.get());

        VertexFormat format = DefaultVertexFormat.ENTITY;

        /* ⛔ UI-LICHT SETZEN, NICHT ERBEN (2026-08-11, RenderDoc-Capture frame 28877).
         * Dieser Zug band die Lighting-Scheibe des GUI-Durchgangs (Light0 = (-0.933, -0.263,
         * -0.244) — beide Richtungen zeigen von der Kachel weg, es bleibt der Ambient-Boden
         * 0.4 aus light.glsl): Schachbrett-Mittel (47.8, 44.9, 52.8) statt 1.21.1s
         * (91.2, 88.7, 134.8). Dasselbe Muster wie FormSammler.zeichnenLassen (d2919ba01);
         * sichern und zuruecksetzen, weil der GUI-Durchgang danach weiterzeichnet. */
        com.mojang.blaze3d.buffers.GpuBufferSlice gesichertesLicht = RenderSystem.getShaderLights();

        mchorse.bbs_mod.graphics.UiLicht.ansichtLoeschen();
        mchorse.bbs_mod.graphics.UiLicht.setzen();

        try
        {
            /* beideSeiten=false: die Kachel zeigt genau eine Seite. Beide koplanaren Seiten
             * zu zeichnen hiesse, dem Tiefenvergleich zu ueberlassen, WELCHE Normale das
             * Licht sieht — gemessen gewann die abgewandte (Faktor 0.4 statt 0.825). */
            this.renderModel(format, () -> RenderPipelines.ENTITY_TRANSLUCENT,
                stack,
                OverlayTexture.NO_OVERLAY, LightCoordsUtil.FULL_BRIGHT, Colors.WHITE,
                context.getTransition(), false, false, -1
            );
        }
        finally
        {
            RenderSystem.setShaderLights(gesichertesLicht);
        }

        stack.popPose();
    }

    @Override
    public void render3D(FormRenderingContext context)
    {
        boolean shading = !context.isPicking() && this.form.shading.get();

        if (!context.isPicking() && BBSRendering.isIrisShadersEnabled())
        {
            shading = true;
        }

        /* ⚠️ 26.2: der unbeschattete Zweig schreibt jetzt POSITION_TEX_COLOR statt
         * POSITION_TEX_LIGHTMAP_COLOR. Das ist KEIN Verlust: der Shader dahinter war schon
         * immer core/position_tex_color, und der kennt nur Position, UV0 und Color -- die
         * Lichtkoordinate wurde also nie gelesen. In 26.2 wird das Format aber zur
         * Pipeline-Bindung, und ein Attribut, das der Shader nicht kennt, ist dort ein
         * Widerspruch statt eines stillen Ueberhangs. Darum steht jetzt genau das Format da,
         * das der Shader auch liest. */
        VertexFormat format = shading ? DefaultVertexFormat.ENTITY : DefaultVertexFormat.POSITION_TEX_COLOR;
        Supplier<RenderPipeline> shader = this.getShader(context,
            shading
                ? () -> RenderPipelines.ENTITY_TRANSLUCENT
                /* getPositionTexColorShader ist entfallen -- der Shader steht in der Pipeline,
                 * und mit ihm Blend, Tiefe und Cull. Die drei Schalter sind die des alten
                 * Zustands: gemischt (weiche Raender, Deckkraft unter 1), Tiefe an,
                 * beidseitig (render3D rief disableCull, und der Quad wird vorn UND hinten
                 * geschrieben). */
                : () -> FormPipelines.holen(DefaultVertexFormat.POSITION_TEX_COLOR, "position_tex_color", PrimitiveTopology.TRIANGLES, true, true, false),
            shading ? BBSShaders::getPickerBillboardProgram : BBSShaders::getPickerBillboardNoShadingProgram
        );

        if (context.isPicking())
        {
            shader = () -> TexturedFormPicking.pipeline(PrimitiveTopology.TRIANGLES);
        }

        this.renderModel(format, shader, context.stack, context.overlay, context.light, context.color, context.getTransition(), !context.isPicking(), true, context.getPickingIndex());
    }

    private void renderModel(VertexFormat format, Supplier<RenderPipeline> shader, PoseStack matrices, int overlay, int light, int overlayColor, float transition, boolean defer, boolean beideSeiten, int pickingIndex)
    {
        Link t = this.form.texture.get();

        if (t == null)
        {
            return;
        }

        Texture texture = BBSModClient.getTextures().getTexture(t);

        float w = texture.width;
        float h = texture.height;
        float ow = w;
        float oh = h;

        /* TL = top left, BR = bottom right*/
        Vector4f crop = this.form.crop.get();
        float uvTLx = crop.x / w;
        float uvTLy = crop.y / h;
        float uvBRx = 1 - crop.z / w;
        float uvBRy = 1 - crop.w / h;

        uvQuad.p1.set(uvTLx, uvTLy, 0);
        uvQuad.p2.set(uvBRx, uvTLy, 0);
        uvQuad.p3.set(uvTLx, uvBRy, 0);
        uvQuad.p4.set(uvBRx, uvBRy, 0);

        float uvFinalTLx = uvTLx;
        float uvFinalTLy = uvTLy;
        float uvFinalBRx = uvBRx;
        float uvFinalBRy = uvBRy;

        if (this.form.resizeCrop.get())
        {
            uvFinalTLx = uvFinalTLy = 0F;
            uvFinalBRx = uvFinalBRy = 1F;

            w = w - crop.x - crop.z;
            h = h - crop.y - crop.w;
        }

        /* Calculate quad's size (vertices, not UV) */
        float ratioX = w > h ? h / w : 1F;
        float ratioY = h > w ? w / h : 1F;
        float TLx = (uvFinalTLx - 0.5F) * ratioY;
        float TLy = -(uvFinalTLy - 0.5F) * ratioX;
        float BRx = (uvFinalBRx - 0.5F) * ratioY;
        float BRy = -(uvFinalBRy - 0.5F) * ratioX;

        quad.p1.set(TLx, TLy, 0);
        quad.p2.set(BRx, TLy, 0);
        quad.p3.set(TLx, BRy, 0);
        quad.p4.set(BRx, BRy, 0);

        float offsetX = this.form.offsetX.get();
        float offsetY = this.form.offsetY.get();
        float rotation = this.form.rotation.get();

        if (offsetX != 0F || offsetY != 0F || rotation != 0F)
        {
            float centerX = (crop.x + (ow - crop.z)) / 2F / ow;
            float centerY = (crop.y + (oh - crop.w)) / 2F / ow;

            matrix.identity()
                .translate(centerX, centerY, 0)
                .rotateZ(MathUtils.toRad(rotation))
                .translate(offsetX / ow, offsetY / oh, 0)
                .translate(-centerX, -centerY, 0);

            uvQuad.transform(matrix);
        }

        this.renderQuad(format, texture, shader, matrices, overlay, light, overlayColor, transition, defer, beideSeiten, pickingIndex);
    }

    private void renderQuad(VertexFormat format, Texture texture, Supplier<RenderPipeline> shader, PoseStack matrices, int overlay, int light, int overlayColor, float transition, boolean defer, boolean beideSeiten, int pickingIndex)
    {
        Color color = new Color().set(overlayColor, true);
        Matrix4f matrix = matrices.last().pose();
        PoseStack.Pose entry = matrices.last();

        FormColorBlend.blend(color, this.form.color.get(), this.form.additiveColor.get());

        if (pickingIndex >= 0)
        {
            color.set(TexturedFormPicking.color(pickingIndex, (int) (color.a * 255F)), true);
        }

        if (this.form.billboard.get())
        {
            MatrixStackUtils.billboard(matrices);
        }

        GameRenderer gameRenderer = Minecraft.getInstance().gameRenderer;

        // 26.2: turnOn/OffLightLayer entfaellt -- die Lichtkarte ist kein globaler Zustand mehr, sondern Sampler2 des Zeichenaufrufs (siehe SofortZeichner.entityTextur). Wer hier zeichnet, muss sie binden; ein globaler Schalter erreicht die Pipeline nicht.
        // 26.2: setup/teardownOverlayColor entfaellt -- das Overlay ist kein globaler Zustand mehr, sondern Sampler1 des Zeichenaufrufs (SofortZeichner bindet alle drei).

        RenderPipeline finalShader = shader.get();

        BBSModClient.getTextures().bindTexture(texture);
        /* 26.2: setShader ist entfallen -- der Shader steht in der Pipeline, die der
         * Zeichenaufruf waehlt. Frueher galt er fuer alles Folgende. */

        texture.bind();
        texture.setFilterMipmap(this.form.linear.get(), this.form.mipmap.get());
        /* 26.2: Tesselator ist entfallen -- sein geteilter Eckenspeicher war ein
         * globaler Zwischenspeicher. Jetzt gehoert der Speicher dem Aufruf. */
        ByteBufferBuilder eckenSpeicher = new ByteBufferBuilder(4096);
        BufferBuilder builder = SofortZeichner.beginnen(eckenSpeicher, PrimitiveTopology.TRIANGLES, format);

        /* Front — in der UI-Kachel weggelassen: ihre Normale zeigt nach der (spiegelnden)
         * UI-Matrix von der Kamera WEG (gemessen (-0.38,-0.35,-0.86), RenderDoc frame 69493).
         * Zwei koplanare Seiten liessen den Tiefenvergleich entscheiden, welche Normale das
         * Licht sieht — und der wählte die dunkle (0.4 statt 0.825). */
        if (beideSeiten)
        {
            this.fill(format, builder, matrix, quad.p3.x, quad.p3.y, color, uvQuad.p3.x, uvQuad.p3.y, overlay, light, entry, 1F);
            this.fill(format, builder, matrix, quad.p2.x, quad.p2.y, color, uvQuad.p2.x, uvQuad.p2.y, overlay, light, entry, 1F);
            this.fill(format, builder, matrix, quad.p1.x, quad.p1.y, color, uvQuad.p1.x, uvQuad.p1.y, overlay, light, entry, 1F);

            this.fill(format, builder, matrix, quad.p3.x, quad.p3.y, color, uvQuad.p3.x, uvQuad.p3.y, overlay, light, entry, 1F);
            this.fill(format, builder, matrix, quad.p4.x, quad.p4.y, color, uvQuad.p4.x, uvQuad.p4.y, overlay, light, entry, 1F);
            this.fill(format, builder, matrix, quad.p2.x, quad.p2.y, color, uvQuad.p2.x, uvQuad.p2.y, overlay, light, entry, 1F);
        }

        /* Back — die eine Seite, die die UI-Kachel zeichnet. Ihre Normale wird von der
         * (spiegelnden) UI-Matrix auf (+0.38,+0.35,+0.85) gedreht (RenderDoc frame 69493),
         * das Winding ist CCW=frontfacing: PER_FACE_LIGHTING nimmt mix(+licht) = 0.82 —
         * derselbe Wert, den 1.21.1s Kachel zeigt. */
        this.fill(format, builder, matrix, quad.p1.x, quad.p1.y, color, uvQuad.p1.x, uvQuad.p1.y, overlay, light, entry, -1F);
        this.fill(format, builder, matrix, quad.p2.x, quad.p2.y, color, uvQuad.p2.x, uvQuad.p2.y, overlay, light, entry, -1F);
        this.fill(format, builder, matrix, quad.p3.x, quad.p3.y, color, uvQuad.p3.x, uvQuad.p3.y, overlay, light, entry, -1F);

        this.fill(format, builder, matrix, quad.p2.x, quad.p2.y, color, uvQuad.p2.x, uvQuad.p2.y, overlay, light, entry, -1F);
        this.fill(format, builder, matrix, quad.p4.x, quad.p4.y, color, uvQuad.p4.x, uvQuad.p4.y, overlay, light, entry, -1F);
        this.fill(format, builder, matrix, quad.p3.x, quad.p3.y, color, uvQuad.p3.x, uvQuad.p3.y, overlay, light, entry, -1F);

        /* 26.2: enableBlend + defaultBlendFunc sind entfallen. Beides zusammen ist genau
         * BlendFunction.TRANSLUCENT, und die traegt jetzt die Pipeline des Zuges: der
         * beschattete Zweig ueber RenderPipelines.ENTITY_TRANSLUCENT, der unbeschattete ueber
         * FormPipelines.holen(..., mischen = true, ...) in render3D. Ersatzlos zu streichen
         * waere still falsch gewesen -- jede Textur mit weichem Rand und jede Deckkraft unter
         * 1 waere undurchsichtig geworden.
         * The picker uses the interleaved POSITION_TEX_COLOR pipeline and unblended ids. */

        boolean linear = this.form.linear.get();
        boolean mipmap = this.form.mipmap.get();
        boolean translucent = texture.hasTranslucency() || color.a < 1F || linear || mipmap;

        if (defer && translucent && FormTranslucentQueue.isActive())
        {
            /* The whole quad defers to the end-of-frame translucent pass: depth testing still
             * hides it behind opaque geometry, while the far-to-near sort blends it correctly
             * against other translucent forms — and it never occludes models behind it.
             * Linear/mipmap sampling manufactures fractional alpha at the edges even from a
             * binary-alpha texture, hence those flags count as translucency. */
            /* 26.2: VertexBuffer ist entfallen, die Aufgabe nicht -- das Netz muss bis zum
             * Leeren der Warteschlange auf der Karte liegenbleiben. GehaltenesNetz macht
             * genau das; der Befehl gibt es in release() frei. */
            GehaltenesNetz netz = new GehaltenesNetz(builder.buildOrThrow());

            Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrixCopy());
            Vector3f origin = modelView.transformPosition(matrix.getTranslation(new Vector3f()));

            FormTranslucentQueue.add(new FormTranslucentQueue.NetzBefehl(
                netz, () -> finalShader, texture, modelView, null, origin, true,
                () ->
                {
                    texture.bind();
                    texture.setFilterMipmap(linear, mipmap);
                },
                () -> texture.setFilterMipmap(false, false)
            ));
        }
        else
        {
            /* 26.2: BufferUploader ist entfallen. Abgeschickt wird ueber einen eigenen
             * Durchgang, und der braucht drei Dinge, die frueher globaler Zustand waren:
             * die Pipeline (Shader + Blend + Tiefe + Cull), die Modellansicht und die
             * Texturbindung. build() gibt null, wenn nichts geschrieben wurde --
             * SofortZeichner zeichnet dann nichts. */

            /* ⛔ IN DAS GESETZTE ZIEL, NICHT INS HAUPTBILD (2026-08-11, PixelHistory frame
             * 84718): mit ziel=null landete die UI-Kachel im Hauptbild UND VOR dem dunklen
             * Panel-Hintergrund (ev 6305, schwarz mit alpha 0.482) — sie wurde von ihm
             * ueberblendet, waehrend die PiP-Miniaturen NACH ihm komponiert werden. Genau
             * der Fund aus Blockvorschau (2026-08-04). Ausserhalb der PiP-Phase ist das
             * Ziel null und es bleibt beim Hauptbild. */
            com.mojang.blaze3d.textures.GpuTextureView zielFarbe = pickingIndex >= 0
                ? mchorse.bbs_mod.graphics.PickZiel.pflicht() : mchorse.bbs_mod.graphics.Zeichenziel.farbe();
            com.mojang.blaze3d.textures.GpuTextureView zielTiefe = pickingIndex >= 0
                ? mchorse.bbs_mod.graphics.PickZiel.tiefe() : mchorse.bbs_mod.graphics.Zeichenziel.tiefe();

            /* ⛔ ALLE DREI SAMPLER BINDEN, KEINEN ERBEN (2026-08-11, Restfall Akte 01/1):
             * entityTextur (singleTextureWithLightmap) laesst Sampler1 (Overlay) leer, und
             * GehaltenesNetz bindet nur, was da ist — der Slot behaelt die Textur des
             * VORHERIGEN Zugs. In run_6 lag dort zufaellig ein Texel mit Alpha 1 (neutral),
             * in run_2 mit anderen gewachsenen Daten eines mit Alpha 0: der Shader rechnet
             * mix(fremdTexel.rgb, farbe.rgb, 0) = fremdes RGB — die Kachel wurde SCHWARZ,
             * bei identischen renderQuad-Eingaengen (Messfuehler bb.zweig, kalter Boot).
             * Gebunden wird das echte Overlay (NO_OVERLAY-Texel dort ist belegt neutral,
             * die Modell-Kacheln zeichnen damit) — dieselbe Haertung wie Blockvorschau. */
            net.minecraft.client.gui.render.TextureSetup bindung = this.textureSetup(texture, pickingIndex >= 0);

            if (zielFarbe != null)
            {
                SofortZeichner.zeichnen(finalShader, builder.build(),
                    new Matrix4f(RenderSystem.getModelViewMatrixCopy()), null,
                    bindung, zielFarbe, zielTiefe);
            }
            else
            {
                SofortZeichner.zeichnen(finalShader, builder.build(),
                    new Matrix4f(RenderSystem.getModelViewMatrixCopy()), null,
                    bindung);
            }
        }

        texture.setFilterMipmap(false, false);

    }

    private net.minecraft.client.gui.render.TextureSetup textureSetup(Texture texture, boolean picking)
    {
        if (picking)
        {
            return net.minecraft.client.gui.render.TextureSetup.singleTexture(texture.view(), texture.sampler());
        }

        Minecraft client = Minecraft.getInstance();
        com.mojang.blaze3d.textures.GpuSampler sampler = RenderSystem.getSamplerCache()
            .getClampToEdge(com.mojang.blaze3d.textures.FilterMode.NEAREST);
        return new net.minecraft.client.gui.render.TextureSetup(texture.view(),
            client.gameRenderer.overlayTexture().getTextureView(), client.gameRenderer.lightmap(),
            texture.sampler(), sampler, sampler);
    }

    private VertexConsumer fill(VertexFormat format, VertexConsumer consumer, Matrix4f matrix, float x, float y, Color color, float u, float v, int overlay, int light, PoseStack.Pose entry, float nz)
    {
        if (format == DefaultVertexFormat.POSITION_TEX_COLOR)
        {
            /* Ohne setLight: das Format hat keine Lichtkoordinate mehr, und der Shader
             * core/position_tex_color hat sie nie gelesen. Siehe render3D. */
            return consumer.addVertex(matrix, x, y, 0F).setUv(u, v).setColor(color.r, color.g, color.b, color.a);
        }

        return consumer.addVertex(matrix, x, y, 0F).setColor(color.r, color.g, color.b, color.a).setUv(u, v).setOverlay(overlay).setLight(light).setNormal(entry, 0F, 0F, nz);
    }
}
