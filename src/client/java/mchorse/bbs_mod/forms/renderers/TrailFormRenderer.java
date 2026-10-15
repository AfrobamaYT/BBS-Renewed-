package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.graphics.InverseView;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.FormPipelines;
import mchorse.bbs_mod.forms.ITickable;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.TrailForm;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.graphics.SofortZeichner;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.ui.framework.UIContext;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class TrailFormRenderer extends FormRenderer<TrailForm> implements ITickable
{
    private int tick;
    private final Map<FormRenderType, ArrayDeque<Trail>> record = new HashMap<>();

    public TrailFormRenderer(TrailForm form)
    {
        super(form);
    }

    /**
     * ⛔ OFFENER FEHLER: DIESE VORSCHAU LANDET IM PORT AN DER FALSCHEN STELLE.
     *
     * <p>Gemessen 2026-08-10 im A/B gegen bbs-1211-ref, Trail-Form in den ersten Replay
     * gesetzt, Film-Panel, Eigenrauschen beider Seiten 0,00 %:
     *
     * <pre>
     *   1.21.1   Symbol klein in der Listenzelle   (x 113..148, y 196..232)
     *   26.2     Symbol riesig unten links        (x 180..330, y 360..460), Zelle leer
     *   A/B      3,32 % -- groesster Einzelposten des ganzen Sweeps
     * </pre>
     *
     * <p>Was BELEGT ist, damit der naechste Anlauf nicht dieselben Wege geht:
     * <ul>
     *   <li>Die Quelle ist diese Aufrufstelle: mit {@code BBSSettings.listModelPreview=false}
     *       verschwindet das Symbol vollstaendig (8950 gezaehlte Bildpunkte -> 0).</li>
     *   <li>Der Aufrufer uebergibt das RICHTIGE Rechteck: {@code Messpunkt} meldet
     *       {@code ~miniatur.TrailFormRenderer = 40x40 @43,87} -- dieselbe Groesse wie
     *       {@code ModelFormRenderer}, das korrekt zeichnet.</li>
     *   <li>Diese Methode und {@code Batcher2D.fullTexturedBox} sind zwischen beiden Baeumen
     *       BYTE-GLEICH; auch {@code UIReplayList#renderElementPart} samt {@code clip(...)}.</li>
     *   <li>Die Textur ist auf beiden Seiten dieselbe: {@code assets:textures/default_trail.png},
     *       1000x1000 -- die Formel oben liefert damit beidseits 36x36.</li>
     *   <li>Das Verhaeltnis 140/35 ist genau 4 -- der Verdacht auf eine fehlende
     *       GUI-Skalierung ist damit naheliegend, aber NICHT gemessen.</li>
     * </ul>
     *
     * <p>⛔ NICHT GEPRUEFT und der naechste Schritt: warum ausgerechnet Trail. Label,
     * Gegenstand, Partikel und Billboard zeichnen ebenfalls flach ueber den Batcher und
     * gehen durch dieselbe {@code Miniaturschlange} (die filtert nicht nach Typ) -- sie
     * liegen bei 0,20 bis 0,76 %. Der Unterschied muss also in dem liegen, was Trail
     * ZUSAETZLICH tut, nicht im Vorschauweg als solchem.
     */
    @Override
    protected boolean flacheVorschau()
    {
        return true;
    }

    protected void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        Texture texture = context.render.getTextures().getTexture(this.form.texture.get());

        float min = Math.min(texture.width, texture.height);
        int ow = (x2 - x1) - 4;
        int oh = (y2 - y1) - 4;

        int w = (int) ((texture.width / min) * ow);
        int h = (int) ((texture.height / min) * ow);

        int x = x1 + (ow - w) / 2 + 2;
        int y = y1 + (oh - h) / 2 + 2;

        context.batcher.fullTexturedBox(texture, x, y, w, h);
    }

    @Override
    protected void render3D(FormRenderingContext context)
    {
        super.render3D(context);

        if (BBSRendering.isIrisShadowPass() || context.type == FormRenderType.ITEM_INVENTORY)
        {
            return;
        }

        if (context.modelRenderer || context.ui)
        {
            PoseStack stack = context.stack;
            float scale = BBSSettings.axesScale.get();
            float axisSize = 1F;
            float axisOffset = 0.01F;
            float outlineSize = 1.01F;
            float outlineOffset = 0.02F;

            axisOffset *= scale;
            outlineOffset *= scale; int i = 10;


            /* 26.2: setShader und disableDepthTest sind entfallen — Shader, Mischung, Tiefe
             * und Cull stehen jetzt in der Pipeline. Uebersetzt ist der alte Zustand dieses
             * Blocks: kein enableBlend (mischen=false), disableDepthTest direkt vor dem Zug
             * (tiefePruefen=false, das schaltet auch das Tiefenschreiben ab), kein
             * disableCull (culling=true, der Vorgabezustand). */
            try (ByteBufferBuilder speicher = new ByteBufferBuilder(2048))
            {
                BufferBuilder builder = SofortZeichner.beginnen(speicher, VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

                Draw.fillBox(builder, stack, -outlineOffset, -outlineSize, -outlineOffset, outlineOffset, outlineSize, outlineOffset, 0, 0, 0);
                Draw.fillBox(builder, stack, -axisOffset, -axisSize, -axisOffset, axisOffset, axisSize, axisOffset, 0, 1, 0);

                /* Draw.fillBox backt stack.last().pose() schon in die Ecken. Als Modellansicht
                 * bleibt genau das, was der alte drawWithShader zusaetzlich auftrug: die
                 * globale. captureModelView waere hier falsch — es multipliziert die Pose ein
                 * zweites Mal. */
                SofortZeichner.zeichnen(
                    FormPipelines.holen(VertexFormat.Mode.TRIANGLES, false, false, true),
                    builder.build(),
                    new Matrix4f(new org.joml.Matrix4f(RenderSystem.getModelViewMatrix())),
                    null,
                    null);
            }

            return;
        }

        if (!BBSRendering.isRenderingWorld())
        {
            return;
        }

        PoseStack stack = context.stack;
        Matrix4f camInverse = new Matrix4f(InverseView.get());

        /* ⚠️ Das ist BBS' EIGENE Kamera (mchorse.bbs_mod.camera.Camera) aus dem
         * Formen-Zeichenkontext, NICHT Vanillas CameraRenderState. Sie traegt die Position als
         * Vector3d im Feld position; der Vanilla-Zustand haette pos als Vec3. Wer die beiden
         * verwechselt, verschiebt die aufgezeichnete Spur um die ganze Kameraposition. */
        Camera camera = context.camera;
        double baseX = camera.position.x;
        double baseY = camera.position.y;
        double baseZ = camera.position.z;

        float current = (float) this.tick + context.transition;
        ArrayDeque<Trail> trails = this.record.computeIfAbsent(context.type, (k) -> new ArrayDeque<>());

        if (!this.form.paused.get())
        {
            /* Since 1.21.1 the camera view lives in RenderSystem's global model-view and the
             * stack base is identity, so fold it back in to recover the FULL model-view
             * (view * translate(-cam) * formChain). Without it camInverse over-rotates the
             * sampled point and the recorded world position ends up depending on the camera,
             * which makes the trail smear whenever the camera moves rather than the object. */
            Matrix4f modelView = new Matrix4f(new org.joml.Matrix4f(RenderSystem.getModelViewMatrix())).mul(stack.last().pose());

            Vector4f top = new Vector4f(0F, 1F, 0F, 1F);
            Vector4f bottom = new Vector4f(0F, -1F, 0F, 1F);

            modelView.transform(top);
            modelView.transform(bottom);
            camInverse.transform(top);
            camInverse.transform(bottom);

            top.mul(1F / top.w);
            bottom.mul(1F / bottom.w);

            Trail record = new Trail();

            record.tick = current;
            record.top = new Vector3d(top.x + baseX, top.y + baseY, top.z + baseZ);
            record.bottom = new Vector3d(bottom.x + baseX, bottom.y + baseY, bottom.z + baseZ);
            record.stop = new Vector3f(top.x - bottom.x, top.y - bottom.y, top.z - bottom.z).lengthSquared() < 1.0E-4D;

            trails.addLast(record);
        }

        boolean loop = this.form.loop.get();
        float length = this.form.length.get();
        float end = current - length;
        Iterator<Trail> it = trails.iterator();
        boolean render = false;
        boolean lastStop = true;

        while (it.hasNext())
        {
            Trail trail = it.next();

            if (trail.tick < end)
            {
                it.remove();
            }
            else
            {
                render |= !trail.stop && !lastStop;
                lastStop = trail.stop;
            }
        }

        if (!render || trails.size() <= 1 || !(length > 0.001D))
        {
            return;
        }

        BBSModClient.getTextures().bindTexture(this.form.texture.get());

        stack.pushPose();

        Trail last = null;
        Trail trail;
        /* The vertices below are in camera-relative world space; the GPU then applies
         * RenderSystem's global model-view, which since 1.21.1 already holds the camera view.
         * Build m so that (globalModelView * m) collapses to the pure camera view:
         * m = inv(globalModelView) * view. In the form editor the global model-view is
         * identity, so m stays the plain view and nothing changes. */
        Matrix4f m = stack.last().pose();

        m.set(new org.joml.Matrix4f(RenderSystem.getModelViewMatrix())).invert();
        m.mul(new Matrix4f(camInverse).invert());

        /* Gross genug fuer alle Abschnitte dieses Bildes: acht Ecken je Abschnitt, POSITION_TEX
         * ist 20 Byte je Ecke (3 Fliesskomma Position + 2 Fliesskomma UV). Zu klein waere kein
         * Fehler, aber ein natives Umkopieren pro Bild. */
        try (ByteBufferBuilder speicher = new ByteBufferBuilder(Math.max(4096, trails.size() * 8 * 20)))
        {
            BufferBuilder builder = SofortZeichner.beginnen(speicher, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);

            for (it = trails.iterator(); it.hasNext(); last = trail)
            {
                trail = it.next();

                if (last != null && !last.stop && !trail.stop)
                {
                    double x1 = trail.top.x - baseX;
                    double x2 = trail.bottom.x - baseX;
                    double x3 = last.bottom.x - baseX;
                    double x4 = last.top.x - baseX;

                    double y1 = trail.top.y - baseY;
                    double y2 = trail.bottom.y - baseY;
                    double y3 = last.bottom.y - baseY;
                    double y4 = last.top.y - baseY;

                    double z1 = trail.top.z - baseZ;
                    double z2 = trail.bottom.z - baseZ;
                    double z3 = last.bottom.z - baseZ;
                    double z4 = last.top.z - baseZ;

                    if (loop)
                    {
                        float u1 = trail.tick / length;
                        float u2 = last.tick / length;

                        builder.addVertex(m, (float) x1, (float) y1, (float) z1).setUv(u1, 0F);
                        builder.addVertex(m, (float) x2, (float) y2, (float) z2).setUv(u1, 1F);
                        builder.addVertex(m, (float) x3, (float) y3, (float) z3).setUv(u2, 1F);
                        builder.addVertex(m, (float) x4, (float) y4, (float) z4).setUv(u2, 0F);
                        /* Other side */
                        builder.addVertex(m, (float) x4, (float) y4, (float) z4).setUv(u2, 0F);
                        builder.addVertex(m, (float) x3, (float) y3, (float) z3).setUv(u2, 1F);
                        builder.addVertex(m, (float) x2, (float) y2, (float) z2).setUv(u1, 1F);
                        builder.addVertex(m, (float) x1, (float) y1, (float) z1).setUv(u1, 0F);
                    }
                    else
                    {
                        float u1 = (current - trail.tick) / length;
                        float u2 = (current - last.tick) / length;

                        builder.addVertex(m, (float) x1, (float) y1, (float) z1).setUv(u1, 0F);
                        builder.addVertex(m, (float) x2, (float) y2, (float) z2).setUv(u1, 1F);
                        builder.addVertex(m, (float) x3, (float) y3, (float) z3).setUv(u2, 1F);
                        builder.addVertex(m, (float) x4, (float) y4, (float) z4).setUv(u2, 0F);
                        /* Other side */
                        builder.addVertex(m, (float) x4, (float) y4, (float) z4).setUv(u2, 0F);
                        builder.addVertex(m, (float) x3, (float) y3, (float) z3).setUv(u2, 1F);
                        builder.addVertex(m, (float) x2, (float) y2, (float) z2).setUv(u1, 1F);
                        builder.addVertex(m, (float) x1, (float) y1, (float) z1).setUv(u1, 0F);
                    }
                }
                else
                {
                    length = current - trail.tick;
                }
            }

            /* 26.2: setShader, defaultBlendFunc und enableBlend sind entfallen — sie stehen
             * jetzt in der Pipeline. Uebersetzt ist der alte Zustand: enableBlend +
             * defaultBlendFunc (mischen=true), kein disableDepthTest in diesem Zweig
             * (tiefePruefen=true, die Spur wird von Bloecken verdeckt), kein disableCull
             * (culling=true — darum baut die Schleife oben die Rueckseite von Hand).
             *
             * Die Ecken tragen m bereits (addVertex(m, ...)); als Modellansicht bleibt die
             * globale, die der alte drawWithShader zusaetzlich auftrug — genau die, gegen die
             * m oben invertiert gerechnet wurde.
             *
             * Die Textur ging frueher ueber bindTexture() als globale GL-Bindung; in 26.2
             * nennt der Durchgang seinen Sampler0 selbst. */
            SofortZeichner.zeichnen(
                FormPipelines.holen(DefaultVertexFormat.POSITION_TEX, "position_tex", VertexFormat.Mode.QUADS, true, true, true),
                builder.build(),
                new Matrix4f(new org.joml.Matrix4f(RenderSystem.getModelViewMatrix())),
                null,
                BBSModClient.getTextures().getTexture(this.form.texture.get()).guiSetup());
        }
        /* Der Tiefentest stand hier NACH dem Zeichnen — er stellte den globalen Zustand
         * fuer alles wieder her, was nach der Spur gezeichnet wird. Diesen globalen
         * Zustand gibt es in 26.2 nicht mehr: der Tiefenzustand steckt im
         * DepthStencilState der RenderPipeline des folgenden Zeichenaufrufs (Vanilla
         * ueber renderType.pipeline(), BBS-Modelle ueber ModelVAOPipeline.DURCHSICHTIG).
         * Der Aufruf hat keinen Empfaenger mehr und entfaellt ersatzlos. */

        stack.popPose();
    }

    @Override
    public void tick(IEntity entity)
    {
        this.tick += 1;
    }

    public static class Trail
    {
        public float tick;
        public Vector3d top;
        public Vector3d bottom;
        public boolean stop;
    }
}
