package mchorse.bbs_mod.ui.framework.elements.utils.vorschau;

import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.FormPipelines;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.graphics.SofortZeichner;
import mchorse.bbs_mod.graphics.Zeichenziel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.Axis;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Der Weg der Gizmo-Zuege: einreihen statt sofort zeichnen, und in WELCHES Bild.
 *
 * <p>Warum das Gizmo ueberhaupt ueber Bild-im-Bild geht, steht in {@link GizmoZustand}.
 * Diese Klasse haelt die beiden Teile, die im Gizmo selbst keinen Platz mehr haben:
 * das Einreichen des Zustands und den Ziel-Trichter, durch den JEDER sichtbare Zug muss.
 */
public final class GizmoZug
{
    /**
     * Reiht das Gizmo fuer die Zeichenphase ein.
     *
     * <p>⚠️ Projektion und Ansicht werden KOPIERT und reisen GETRENNT — der Stapel des
     * Aufrufers laeuft weiter, und zusammenmultipliziert laege die Perspektive im Stapel
     * (siehe {@link GizmoZustand}).
     */
    public static void einreihen(UIContext kontext, Matrix4f projektion, Matrix4f ansicht,
        Area flaeche, float viewportSkalierung)
    {
        /* Bild-im-Bild liegt in Minecrafts GUI-Raum: BBS-Einheiten mal Rest (UISkala). */
        float rest = mchorse.bbs_mod.ui.framework.UISkala.rest();
        int x0 = Math.round(flaeche.x * rest);
        int y0 = Math.round(flaeche.y * rest);
        int x1 = Math.round(flaeche.ex() * rest);
        int y1 = Math.round(flaeche.ey() * rest);
        ScreenRectangle rechteck = new ScreenRectangle(x0, y0, x1 - x0, y1 - y0);

        kontext.batcher.bildImBild(new GizmoBildZustand(
            new Matrix4f(projektion), new Matrix4f(ansicht), viewportSkalierung,
            x0, y0, x1, y1, rechteck, rechteck, mchorse.bbs_mod.ui.utils.GizmoGimbal.sichern()));
    }

    /**
     * EIN sichtbarer Zug des Gizmos — ins Hauptbild oder in die Bild-im-Bild-Textur.
     *
     * <h2>Warum jeder Zug hier durchmuss</h2>
     *
     * <p>{@link SofortZeichner} zeichnet ohne Zielangabe ins HAUPTBILD; das ist der Normalfall
     * und in der Welt richtig. In der Oberflaeche von 26.2 ist es falsch: dort muss derselbe
     * Zug in die Textur des Bild-im-Bild-Zeichners, sonst landet er unter dem GUI und wird vom
     * Panel-Hintergrund uebermalt. Welches von beiden gilt, sagt {@link Zeichenziel} — die
     * Bruecke, die {@link GizmoRenderer} setzt und in seinem {@code finally} freigibt.
     *
     * <p>Es steht bewusst an EINER Stelle: die sichtbaren Zuege des Gizmos sind ueber vier
     * Methoden verstreut (Ringe, Kuchenscheiben, Achsenlinie, Griffe), und ein einzelner
     * vergessener Zug faellt nicht als Fehler auf, sondern nur als ein Teil, der fehlt.
     *
     * <p>⚠️ NICHT fuer den Auswahl-Durchgang. Dessen Ziel kommt von {@code PickZiel}, und
     * seine Kennungsfarben duerfen weder gemischt noch von der Tiefe verschluckt werden.
     */
    public static void zeichnen(RenderPipeline pipeline, MeshData netz, Matrix4f modellAnsicht,
        Vector4f modulator)
    {
        GpuTextureView farbZiel = Zeichenziel.farbe();

        if (farbZiel == null)
        {
            SofortZeichner.zeichnen(pipeline, netz, modellAnsicht, modulator, null);

            return;
        }

        SofortZeichner.zeichnen(pipeline, netz, modellAnsicht, modulator, null,
            farbZiel, Zeichenziel.tiefe());
    }

    /**
     * Der Blickring (Drehung um die Sichtachse), sofort aufgebaut statt aus dem gehaltenen
     * Netz von {@code GizmoRinge}.
     *
     * <h2>Warum es diese zweite Fassung gibt</h2>
     *
     * <p>{@code GizmoRinge} haelt den Ring als fertiges Netz auf der Grafikkarte und zeichnet
     * ihn ohne Zielangabe — also fest ins Hauptbild. In der Bild-im-Bild-Phase waere der
     * Blickring damit das EINZIGE Teil des Gizmos, das fehlt. Ein Ziel-Parameter dort waere
     * der kuerzere Weg; {@code GizmoRinge} wird gerade an anderer Stelle geaendert und bleibt
     * darum unangetastet. Ist das durch, gehoert diese Methode dorthin zurueck.
     *
     * <p>Die Ausrichtung ist dieselbe Rechnung wie in {@code GizmoRinge.billboardZeichnen}:
     * die Kamerarichtung im lokalen Rahmen des Gizmos, und der Ring wird mit seiner
     * Y-Achse dorthin gedreht.
     *
     * @param a Deckkraft; sie reist im Modulator mit, weil {@link Draw#arc3D} jede Ecke
     *          undurchsichtig schreibt.
     */
    public static void blickring(PoseStack stapel, float r, float g, float b, float a, float skalierung)
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
            stapel.rotate(new Quaternionf().rotationTo(0F, 1F, 0F, zurKamera.x, zurKamera.y, zurKamera.z));
        }

        stapel.scale(skalierung, skalierung, skalierung);

        float groesse = BBSSettings.axesScale.get();
        float radius = 0.22F * groesse;
        float dicke = 0.02F * groesse * BBSSettings.axesThickness.get();

        try (ByteBufferBuilder speicher = new ByteBufferBuilder(65536))
        {
            BufferBuilder bauer = SofortZeichner.beginnen(speicher,
                PrimitiveTopology.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

            Draw.arc3D(bauer, stapel, Axis.Y, radius, dicke, r, g, b, 0F, 360F);

            /* Mischen an, Tiefentest an, kein Culling — der Ring ist eine duenne Roehre und
             * wird von beiden Seiten gesehen. Dieselbe Wahl wie GizmoRinge.pipeline(). */
            zeichnen(FormPipelines.holen(PrimitiveTopology.TRIANGLES, true, true, false),
                bauer.build(), RenderSystem.getModelViewMatrixCopy(), new Vector4f(1F, 1F, 1F, a));
        }

        stapel.popPose();
    }

    private GizmoZug()
    {}
}
