package mchorse.bbs_mod.ui.utils;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.FormPipelines;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.graphics.SofortZeichner;
import mchorse.bbs_mod.ui.framework.elements.utils.vorschau.GizmoZug;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * Die bildschirmweiten Achsenlinien einer laufenden Transform-Bearbeitung — aus
 * {@code Gizmo.drawInfiniteLine} herausgeloest (die Datei darf nur schrumpfen) und fuer
 * Akte 07 auf Blenders Rueckmeldung gebracht:
 *
 * <ul>
 *   <li><b>Dicke skaliert mit dem Kameraabstand</b> statt fix 0.005 Bloecke — die alte
 *       Linie war auf Arbeitsdistanz 1-2 Pixel duenn ("pixelig, minecraft-artig").</li>
 *   <li><b>MMB-Voransicht zeigt ALLE DREI Achsen</b> in Rot/Gruen/Blau, wie Blenders
 *       Achsenwahl beim gehaltenen Mittelklick.</li>
 * </ul>
 *
 * <p>Mit ausgeschaltetem {@code transformBlenderFeedback} zeichnet alles wie vor der
 * Aenderung (duenne Linie, keine Voransicht).</p>
 */
public final class GizmoAchsenLinien
{
    /** Blender-Dicke relativ zum Abstands-Massstab; die alte fixe Dicke war 0.005. */
    private static final float DICKE_BLENDER = 0.011F;
    private static final float DICKE_ALT = 0.005F;

    /** MMB-Achsenwahl laeuft: alle drei Achsen zeigen. Je Bild vom UITransformHandler gesetzt. */
    private static boolean voransicht;

    /** Frischer Achsenwahl-Schnappschuss DIESES Bildes (aktuelle Kamera UND Ansichtsdrehung),
     *  je Frame vom UITransformHandler gesetzt; {@code null} (auch nach einem Hotswap) faellt
     *  auf den Griff-Schnappschuss zurueck. Ohne aktuelle Kamera driftet der Anker um die
     *  Kamerabewegung seit dem Griff (Akte 14), ohne Ansichtsdrehung sitzt er
     *  blickrichtungsabhaengig daneben (Akte 15). */
    private static GizmoDrag frisch;

    private GizmoAchsenLinien()
    {}

    public static void setFrisch(GizmoDrag zug)
    {
        frisch = zug;
    }

    public static void setVoransicht(boolean an)
    {
        voransicht = an;
    }

    public static boolean istVoransicht()
    {
        return voransicht && BBSSettings.transformBlenderFeedback.get();
    }

    /**
     * @param debugIndex    Gizmo-Stencil-Index der gesperrten Achse/Ebene, oder ein Wert
     *                      ausserhalb — dann zeichnet nur die Voransicht (alle drei).
     * @param abstandsSkala {@code Gizmo.getAxesDistanceScale}-Wert am Pivot, fuer die
     *                      bildschirm-konstante Dicke.
     * @param griff         Zug-Schnappschuss vom Bearbeitungsbeginn, oder {@code null} —
     *                      dann zeichnen die Linien wie frueher am Live-Gizmo.
     */
    public static void zeichnen(PoseStack stack, int debugIndex, float abstandsSkala, GizmoDrag griff)
    {
        boolean alleDrei = istVoransicht();
        boolean gesperrt = debugIndex >= Gizmo.STENCIL_X && debugIndex <= Gizmo.STENCIL_ZY;

        if (!alleDrei && !gesperrt)
        {
            return;
        }

        float size = 10000F;
        float t = BBSSettings.transformBlenderFeedback.get() ? DICKE_BLENDER * Math.max(abstandsSkala, 0.2F) : DICKE_ALT;

        stack.pushPose();

        /* Blender ankert die Sperrlinien am STARTPUNKT der Transformation
         * (transform_constraints.cc: drawLine am fixen t->center_global) — das Objekt
         * gleitet auf der benutzten Linie entlang, die Linien wandern nicht mit
         * (Akte 12, Nutzerbilder). Der Stack ist kameralativ: Translation wird durch
         * start − kamera aus dem Griff-Schnappschuss ersetzt (beide vom Griff — die
         * Kamera steht waehrend eines Modal-Zugs), die Space-Rotation bleibt. */
        if (griff != null && BBSSettings.transformBlenderFeedback.get())
        {
            /* start - kamera_JETZT, durch die ANSICHTSDREHUNG des Bildes gedreht: der
             * Zeichen-Stack ist view-rotiert (Gizmo.computeWorldOrigin: "stack = view *
             * translate(-cam) * chain") — ein unrotiertes Welt-Delta sass blickrichtungs-
             * und abstandsabhaengig daneben (Akte 15, Nutzerbild mit rotem Pfeil). Kamera
             * und Drehung kommen aus DEMSELBEN Schnappschuss: dem frischen vom Handler,
             * sonst dem Griff (steht die Kamera, sind beide identisch). */
            GizmoDrag quelle = frisch != null ? frisch : griff;

            org.joml.Vector3f rel = new org.joml.Vector3f(
                (float) (griff.gizmoOrigin.x - quelle.cameraOrigin.x),
                (float) (griff.gizmoOrigin.y - quelle.cameraOrigin.y),
                (float) (griff.gizmoOrigin.z - quelle.cameraOrigin.z));

            quelle.view.transformDirection(rel);

            if (mchorse.bbs_mod.graphics.Messpunkt.istAn())
            {
                org.joml.Vector3f st = stack.last().pose().getTranslation(new org.joml.Vector3f());

                mchorse.bbs_mod.graphics.Messpunkt.zaehle("achsenlinien.anker", String.format(
                    "stack=%.3f,%.3f,%.3f gesetzt=%.3f,%.3f,%.3f frisch=%s",
                    st.x, st.y, st.z, rel.x, rel.y, rel.z, frisch != null));
            }

            stack.last().pose().setTranslation(rel.x, rel.y, rel.z);
        }

        try (ByteBufferBuilder speicher = new ByteBufferBuilder(4096))
        {
            BufferBuilder builder = SofortZeichner.beginnen(speicher, PrimitiveTopology.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

            if (alleDrei || debugIndex == Gizmo.STENCIL_X || debugIndex == Gizmo.STENCIL_XZ || debugIndex == Gizmo.STENCIL_XY)
            {
                Draw.fillBox(builder, stack, -size, -t, -t, size, t, t, Colors.RED);
            }

            if (alleDrei || debugIndex == Gizmo.STENCIL_Y || debugIndex == Gizmo.STENCIL_XY || debugIndex == Gizmo.STENCIL_ZY)
            {
                Draw.fillBox(builder, stack, -t, -size, -t, t, size, t, Colors.GREEN);
            }

            if (alleDrei || debugIndex == Gizmo.STENCIL_Z || debugIndex == Gizmo.STENCIL_XZ || debugIndex == Gizmo.STENCIL_ZY)
            {
                Draw.fillBox(builder, stack, -t, -t, -size, t, t, size, Colors.BLUE);
            }

            /* Pipeline wie im alten Gizmo-Rumpf: mischen an, KEINE Tiefenpruefung (die Linie
             * soll durch Geometrie sichtbar bleiben), Cull an — die Linien-Boxen zeichnen
             * hier nachweislich (die duenne Fassung war immer sichtbar). */
            GizmoZug.zeichnen(
                FormPipelines.holen(PrimitiveTopology.TRIANGLES, true, false, true),
                builder.build(),
                RenderSystem.getModelViewMatrixCopy(),
                null);
        }

        stack.popPose();
    }
}
