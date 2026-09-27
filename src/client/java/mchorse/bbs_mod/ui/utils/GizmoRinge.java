package mchorse.bbs_mod.ui.utils;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.FormPipelines;
import mchorse.bbs_mod.graphics.GehaltenesNetz;
import mchorse.bbs_mod.graphics.PickZiel;
import mchorse.bbs_mod.graphics.Projektion;
import mchorse.bbs_mod.graphics.SofortZeichner;
import mchorse.bbs_mod.graphics.Zeichenziel;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.utils.Axis;
import mchorse.bbs_mod.utils.MathUtils;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Die vorgebauten Netze des Rotations-Gizmos: die drei Achsenringe und die Mittelkugel.
 *
 * <h2>Warum das aus Gizmo herausgeloest ist</h2>
 *
 * <p>{@code Gizmo} war ueber 2000 Zeilen lang und mischte Eingabe, Zustand, Auswahl-Durchgang
 * und Zeichnen. Die Ringgeometrie ist davon der Teil mit der klarsten eigenen Aufgabe: sie
 * wird EINMAL gebaut, bei geaenderter Groesse neu gebaut und danach nur noch gezeichnet.
 * Getrennt ist auch sichtbar, was frueher unterging — dass hier zwei Netze GEHALTEN werden
 * und wer sie freigibt.
 *
 * <h2>26.2: von VertexBuffer auf gehaltene Netze</h2>
 *
 * <p>Frueher lagen die beiden Netze in {@code VertexBuffer}; die Klasse gibt es nicht mehr.
 * Die Aufgabe ist dieselbe geblieben (einmal hochladen, viele Bilder lang zeichnen), also
 * uebernimmt {@link GehaltenesNetz}.
 *
 * <p>Zwei Zustandsschalter von damals sind ersatzlos weg, weil ihre Wirkung jetzt woanders
 * steht:
 *
 * <ul>
 *   <li>{@code RenderSystem.setShaderColor(r, g, b, a)} vor dem Zug und das Zuruecksetzen
 *       auf Weiss danach. Die Farbe ist jetzt Parameter des Zuges. Das Zuruecksetzen war
 *       kein Schoenheitsfehler: wer es vergass, faerbte alles ein, was danach kam.</li>
 *   <li>{@code GameRenderer.getPositionColorShader()}. Der Shader steht in der Pipeline,
 *       und mit ihm Mischen, Tiefentest und Culling.</li>
 * </ul>
 */
public class GizmoRinge
{
    private static final int BOGEN_CACHE_MAX = 16;

    /** Ein Ring liegt in der XZ-Ebene; fuer die anderen Achsen wird er gedreht. */
    private GehaltenesNetz ring;

    private GehaltenesNetz kugel;

    /**
     * Visible ring arcs in the gizmo's LOCAL space, tessellated by the unchanged
     * {@code Draw.arc3D} and vertex-coloured exactly like before (same UNORM quantisation,
     * so picking IDs stay identical). The pose is not part of the key: it goes to the GPU
     * as the model-view matrix, the way {@link #ringZeichnen} draws the full ring. A moving
     * bone or camera therefore reuses the uploaded arc instead of re-tessellating,
     * re-transforming and re-uploading 13824 vertices per ring and pass. Arcs are quantised
     * to {@code Gizmo.RING_OCCLUSION_SAMPLES}, so only a few keys are live at once.
     */
    private final LinkedHashMap<BogenSchluessel, GehaltenesNetz> boegen =
        new LinkedHashMap<>(16, 0.75F, true);

    private float letzteSkalierung = -1F;
    private float letzteDicke = -1F;

    /**
     * Baut die Netze neu, wenn sich Groesse oder Dicke geaendert haben.
     *
     * <p>⚠️ Der Eckenspeicher wird erst NACH dem Hochladen geschlossen. Wird er vorher
     * freigegeben, liest das Hochladen aus fremdem Speicher -- ein Fehler, der nicht
     * abstuerzt, sondern Muell zeichnet.
     */
    public void auffrischen()
    {
        float skalierung = BBSSettings.axesScale.get();
        float dicke = BBSSettings.axesThickness.get();

        if (this.ring != null && skalierung == this.letzteSkalierung && dicke == this.letzteDicke)
        {
            return;
        }

        this.freigeben();

        float radius = 0.22F * skalierung;
        float ringDicke = 0.02F * skalierung * dicke;

        try (ByteBufferBuilder speicher = new ByteBufferBuilder(65536))
        {
            BufferBuilder bauer = SofortZeichner.beginnen(speicher, PrimitiveTopology.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

            Draw.arc3D(bauer, new PoseStack(), Axis.Y, radius, ringDicke, 1F, 1F, 1F, 0F, 360F);
            this.ring = new GehaltenesNetz(bauer.buildOrThrow());

            bauer = SofortZeichner.beginnen(speicher, PrimitiveTopology.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
            Draw.sphere(bauer, new PoseStack(), radius, 24, 24, 1F, 1F, 1F, 1F);
            this.kugel = new GehaltenesNetz(bauer.buildOrThrow());
        }

        this.letzteSkalierung = skalierung;
        this.letzteDicke = dicke;
    }

    public boolean bereit()
    {
        return this.ring != null && this.kugel != null;
    }

    public GehaltenesNetz kugel()
    {
        return this.kugel;
    }

    private GehaltenesNetz bogen(Axis achse, float radius, float dicke,
        float startDeg, float sweepDeg, float r, float g, float b)
    {
        BogenSchluessel schluessel = new BogenSchluessel(achse, radius, dicke, startDeg, sweepDeg, r, g, b);
        GehaltenesNetz netz = this.boegen.get(schluessel);

        if (netz != null)
        {
            return netz;
        }

        try (ByteBufferBuilder speicher = new ByteBufferBuilder(262144))
        {
            BufferBuilder bauer = SofortZeichner.beginnen(
                speicher, PrimitiveTopology.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

            /* The original tessellator with an identity pose: axis rotation and colour are
             * baked in, the caller's pose is applied as the model-view matrix. */
            Draw.arc3D(bauer, new PoseStack(), achse, radius, dicke, r, g, b, startDeg, sweepDeg);
            netz = new GehaltenesNetz(bauer.buildOrThrow());
        }

        this.boegen.put(schluessel, netz);

        if (this.boegen.size() > BOGEN_CACHE_MAX)
        {
            Map.Entry<BogenSchluessel, GehaltenesNetz> aeltester =
                this.boegen.entrySet().iterator().next();

            aeltester.getValue().close();
            this.boegen.remove(aeltester.getKey());
        }

        return netz;
    }

    public void freigeben()
    {
        if (this.ring != null)
        {
            this.ring.close();
            this.ring = null;
        }

        if (this.kugel != null)
        {
            this.kugel.close();
            this.kugel = null;
        }

        for (GehaltenesNetz netz : this.boegen.values())
        {
            netz.close();
        }

        this.boegen.clear();
    }

    /**
     * Zeichnet den Ring einer Achse.
     *
     * <p>Der Ring liegt in der XZ-Ebene; X und Z bekommen ihn um eine Vierteldrehung
     * gekippt.
     */
    public void ringZeichnen(PoseStack stapel, Axis achse, float r, float g, float b, float a)
    {
        if (this.ring == null)
        {
            return;
        }

        stapel.pushPose();

        if (achse == Axis.X)
        {
            stapel.mulPose(com.mojang.math.Axis.ZP.rotation(MathUtils.PI / 2F));
        }

        if (achse == Axis.Z)
        {
            stapel.mulPose(com.mojang.math.Axis.XP.rotation(MathUtils.PI / 2F));
        }

        zeichnen(this.ring, stapel, r, g, b, a, null);

        stapel.popPose();
    }

    /**
     * Draw the arc produced by {@code Draw.arc3D}, reusing its uploaded local-space geometry
     * across frames, poses and the visible and picking passes. The bounded cache keeps the
     * recently visible quantised arcs without retaining an unbounded GPU history.
     */
    public void abschnittZeichnen(PoseStack stapel, Axis achse, float radius, float dicke,
        float startDeg, float sweepDeg, float r, float g, float b)
    {
        /* Keep the native fallback beside the cache, not in Gizmo's input/state class. */
        if (Boolean.getBoolean("bbs.gizmo.immediateRings"))
        {
            try (ByteBufferBuilder speicher = new ByteBufferBuilder(4096))
            {
                BufferBuilder builder = SofortZeichner.beginnen(
                    speicher, PrimitiveTopology.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

                Draw.arc3D(builder, stapel, achse, radius, dicke, r, g, b, startDeg, sweepDeg);
                GpuTextureView ziel = PickZiel.aktiv();

                if (ziel == null)
                {
                    mchorse.bbs_mod.ui.framework.elements.utils.vorschau.GizmoZug.zeichnen(
                        FormPipelines.holen(PrimitiveTopology.TRIANGLES, true, true, true),
                        builder.build(), RenderSystem.getModelViewMatrixCopy(), null);
                }
                else
                {
                    SofortZeichner.zeichnen(FormPipelines.holen(PrimitiveTopology.TRIANGLES, false, false, true),
                        builder.build(), RenderSystem.getModelViewMatrixCopy(), null, null, ziel, null);
                }
            }

            return;
        }

        GehaltenesNetz netz = this.bogen(achse, radius, dicke, startDeg, sweepDeg, r, g, b);
        Matrix4f modellAnsicht = modellAnsicht(stapel);
        GpuTextureView pickZiel = PickZiel.aktiv();

        if (pickZiel != null)
        {
            netz.zeichnen(FormPipelines.holen(PrimitiveTopology.TRIANGLES, false, false, true),
                modellAnsicht, null, null, pickZiel, null, null);
        }
        else
        {
            GpuTextureView farbZiel = Zeichenziel.farbe();

            if (farbZiel == null)
            {
                netz.zeichnen(FormPipelines.holen(PrimitiveTopology.TRIANGLES, true, true, true),
                    modellAnsicht, null, null, (com.mojang.blaze3d.pipeline.RenderTarget) null, null);
            }
            else
            {
                netz.zeichnen(FormPipelines.holen(PrimitiveTopology.TRIANGLES, true, true, true),
                    modellAnsicht, null, null, farbZiel, Zeichenziel.tiefe(), null);
            }
        }
    }

    /**
     * Zeichnet den Ring dem Betrachter zugewandt -- der Ring, mit dem man um die Sichtachse
     * dreht.
     *
     * <p>⚠️ Derselbe Aufruf dient ZWEI Durchgaengen: sichtbar ins Bild und als Kennung in
     * BBS' Auswahl-Puffer ({@code GizmoAuswahl}, Handle.VIEW). Welcher gerade laeuft, sagt
     * {@link PickZiel} -- angemeldet von {@code StencilFormFramebuffer.apply()}. Genau so
     * macht es {@code Gizmo.drawOccludedRing} fuer die drei Achsenringe.
     *
     * <p>Fehlte dieses Durchreichen, landete die Kennung im Hauptbild: ein dunkelroter Fleck
     * SICHTBAR im Bild und ein Blickring, den der Klick nicht findet -- ein Fehler, der
     * beides zugleich verursacht und nichts meldet.
     */
    public void billboardZeichnen(PoseStack stapel, float r, float g, float b, float a, float skalierung)
    {
        if (this.ring == null)
        {
            return;
        }

        stapel.pushPose();

        Matrix4f matrix = stapel.last().pose();
        Vector3f zurKamera = matrix.getTranslation(new Vector3f()).negate();
        Matrix3f basis = matrix.get3x3(new Matrix3f());

        if (Math.abs(basis.determinant()) > 1.0E-8F)
        {
            basis.invert().transform(zurKamera);
        }

        if (zurKamera.lengthSquared() > 1.0E-8F)
        {
            zurKamera.normalize();
            stapel.mulPose(new Quaternionf().rotationTo(0F, 1F, 0F, zurKamera.x, zurKamera.y, zurKamera.z));
        }

        stapel.scale(skalierung, skalierung, skalierung);

        zeichnen(this.ring, stapel, r, g, b, a, PickZiel.aktiv());

        stapel.popPose();
    }

    /** Exact cached counterpart of {@code GizmoZug.blickring} for the PIP target. */
    public void billboardGecachtZeichnen(PoseStack stapel, float r, float g, float b, float a, float skalierung)
    {
        stapel.pushPose();

        Matrix4f matrix = stapel.last().pose();
        Vector3f zurKamera = matrix.getTranslation(new Vector3f()).negate();
        Matrix3f basis = matrix.get3x3(new Matrix3f());

        if (Math.abs(basis.determinant()) > 1.0E-8F)
        {
            basis.invert().transform(zurKamera);
        }

        if (zurKamera.lengthSquared() > 1.0E-8F)
        {
            zurKamera.normalize();
            stapel.mulPose(new Quaternionf().rotationTo(
                0F, 1F, 0F, zurKamera.x, zurKamera.y, zurKamera.z));
        }

        stapel.scale(skalierung, skalierung, skalierung);

        float groesse = BBSSettings.axesScale.get();
        float radius = 0.22F * groesse;
        float dicke = 0.02F * groesse * BBSSettings.axesThickness.get();
        GehaltenesNetz netz = this.bogen(Axis.Y, radius, dicke, 0F, 360F, r, g, b);
        Matrix4f modellAnsicht = modellAnsicht(stapel);
        org.joml.Vector4f alpha = new org.joml.Vector4f(1F, 1F, 1F, a);
        GpuTextureView farbZiel = Zeichenziel.farbe();

        if (farbZiel == null)
        {
            netz.zeichnen(pipeline(), modellAnsicht, alpha, null,
                (com.mojang.blaze3d.pipeline.RenderTarget) null, null);
        }
        else
        {
            netz.zeichnen(pipeline(), modellAnsicht, alpha, null,
                farbZiel, Zeichenziel.tiefe(), null);
        }

        stapel.popPose();
    }

    /**
     * Zeichnet die Mittelkugel als MASKE in einen BBS-eigenen Puffer.
     *
     * <p>Ohne Tiefenanhang und mit ausgeschaltetem Tiefentest: die Maske soll die ganze
     * Silhouette der Kugel treffen, auch wo Geometrie davorsteht. Der alte Weg erreichte
     * dasselbe mit disableDepthTest davor und enableDepthTest danach.
     */
    public void kugelMaskeZeichnen(Matrix4f modellAnsicht, Matrix4f projektion,
        float r, float g, float b, float a, GpuTextureView farbZiel)
    {
        if (this.kugel == null)
        {
            return;
        }

        /* ⛔ DIE PROJEKTION MUSS MIT (2026-08-11, am leeren Puffer gemessen).
         *
         * {@code GehaltenesNetz#zeichnen} hat keinen Projektionsparameter -- es nimmt die
         * gerade in RenderSystem gebundene. Dieser Zug laeuft im GUI-Durchgang, dort steht
         * also die Oberflaechen-Projektion, waehrend {@code modellAnsicht} eine 3D-Ansicht
         * ist. Die Kugel landete damit ausserhalb des Sichtvolumens: der Zug wurde abgesetzt
         * (im RenderDoc-Mitschnitt sichtbar, richtiges Ziel, Culling aus, Vollbild-Scissor)
         * und rasterisierte trotzdem nichts.
         *
         * Gemessen ueber /shot/puffer im selben Bild, direkt nach diesem Aufruf:
         *     Kugelmaske summeRGB = 0     Pick-Stencil zur Gegenprobe = 170229
         *
         * In 1.21.1 ging die Projektion noch mit:
         *     rotateSphereVbo.draw(lastSphereMatrix, projection, ...)
         * Beim Portieren fiel sie weg, weil der neue Zeichenweg sie nicht mehr als Argument
         * fuehrt. ORTHOGRAPHIC wie in UIModelRenderer, das dieselbe camera.projection setzt. */
        Projektion.sichern();
        Projektion.setzen(projektion, ProjectionType.ORTHOGRAPHIC);

        try
        {
            this.kugel.zeichnen(
                FormPipelines.holen(PrimitiveTopology.TRIANGLES, true, false, false),
                modellAnsicht, new org.joml.Vector4f(r, g, b, a), null, farbZiel, null, null);
        }
        finally
        {
            Projektion.zurueck();
        }
    }

    /**
     * Faltet die globale Modellansicht ein.
     *
     * <p>Gehaltene Netze erben sie nicht -- anders als die sofort gezeichneten Griffe, die
     * den gerade gueltigen Stapel mitbekommen. Im Formeditor ist sie die Einheitsmatrix
     * (die Kamera steckt im Stapel), im Filmeditor traegt sie die Weltkamera. Ohne diese
     * Zeile trieben die Ringe dort davon.
     */
    public static Matrix4f modellAnsicht(PoseStack stapel)
    {
        return new Matrix4f(RenderSystem.getModelViewMatrixCopy()).mul(stapel.last().pose());
    }

    /**
     * @param farbZiel {@code null} = sichtbar ins Hauptbild, mit Mischung und Tiefentest.
     *                 Sonst BBS' Auswahl-Puffer -- und dann OHNE beides, wie bei
     *                 {@link #kugelMaskeZeichnen} und {@code Gizmo.drawOccludedRing}: eine
     *                 gemischte Kennung ist eine Zahl, die auf nichts zeigt, und eine von
     *                 der Tiefe verschluckte gar keine. Kein Tiefenanhang, darum auch kein
     *                 Tiefenziel.
     */
    private static void zeichnen(GehaltenesNetz netz, PoseStack stapel, float r, float g, float b, float a, GpuTextureView farbZiel)
    {
        Matrix4f modellAnsicht = modellAnsicht(stapel);
        org.joml.Vector4f farbe = new org.joml.Vector4f(r, g, b, a);

        if (farbZiel == null)
        {
            GpuTextureView sichtZiel = Zeichenziel.farbe();

            if (sichtZiel == null)
            {
                netz.zeichnen(pipeline(), modellAnsicht, farbe, null,
                    (com.mojang.blaze3d.pipeline.RenderTarget) null, null);
            }
            else
            {
                netz.zeichnen(pipeline(), modellAnsicht, farbe, null,
                    sichtZiel, Zeichenziel.tiefe(), null);
            }

            return;
        }

        /* Culling bleibt aus wie im sichtbaren Zug -- der Ring ist eine duenne Roehre, und
         * die Kennung muss dieselbe Silhouette treffen wie das, was der Nutzer sieht. */
        netz.zeichnen(FormPipelines.holen(PrimitiveTopology.TRIANGLES, false, false, false),
            modellAnsicht, farbe, null, farbZiel, null, null);
    }

    /**
     * Mischen an, Tiefentest an, kein Culling -- die Ringe sind duenne Roehren und werden
     * von beiden Seiten gesehen.
     */
    private static com.mojang.blaze3d.pipeline.RenderPipeline pipeline()
    {
        return FormPipelines.holen(PrimitiveTopology.TRIANGLES, true, true, false);
    }

    private static final class BogenSchluessel
    {
        private final int achse;
        private final int radius;
        private final int dicke;
        private final int start;
        private final int sweep;
        private final int r;
        private final int g;
        private final int b;

        private BogenSchluessel(Axis achse, float radius, float dicke,
            float start, float sweep, float r, float g, float b)
        {
            this.achse = achse.ordinal();
            this.radius = Float.floatToRawIntBits(radius);
            this.dicke = Float.floatToRawIntBits(dicke);
            this.start = Float.floatToRawIntBits(start);
            this.sweep = Float.floatToRawIntBits(sweep);
            this.r = Float.floatToRawIntBits(r);
            this.g = Float.floatToRawIntBits(g);
            this.b = Float.floatToRawIntBits(b);
        }

        @Override
        public boolean equals(Object object)
        {
            if (!(object instanceof BogenSchluessel other))
            {
                return false;
            }

            return this.achse == other.achse
                && this.radius == other.radius && this.dicke == other.dicke
                && this.start == other.start && this.sweep == other.sweep
                && this.r == other.r && this.g == other.g && this.b == other.b;
        }

        @Override
        public int hashCode()
        {
            int result = this.achse;
            result = 31 * result + this.radius;
            result = 31 * result + this.dicke;
            result = 31 * result + this.start;
            result = 31 * result + this.sweep;
            result = 31 * result + this.r;
            result = 31 * result + this.g;

            return 31 * result + this.b;
        }
    }
}
