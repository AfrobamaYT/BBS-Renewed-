package mchorse.bbs_mod.ui.utils;

import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
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
import mchorse.bbs_mod.graphics.Projektion;
import org.joml.Matrix4f;
import org.joml.Vector4f;

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

        if (BBSSettings.transformBlenderFeedback.get() && System.nanoTime() - zweiDZuletzt < 500_000_000L)
        {
            /* Screen-space lines like Blender's (transform_constraints.cc drawLine with the
             * polyline shader: a fixed pixel width): the ends are projected here, the viewport
             * draws them anti-aliased on top (zeichnen2D). The 3D boxes before scaled with the
             * distance to the pivot -- they grew and shrank while the model moved -- and as
             * immediate draws they no longer reached the picture in 26.3. */
            Matrix4f gesamt = new Matrix4f(Projektion.lesen()).mul(RenderSystem.getModelViewMatrixCopy()).mul(stack.last().pose());

            if (alleDrei || debugIndex == Gizmo.STENCIL_X || debugIndex == Gizmo.STENCIL_XZ || debugIndex == Gizmo.STENCIL_XY) strecke(gesamt, 1, 0, 0, size, 0);
            if (alleDrei || debugIndex == Gizmo.STENCIL_Y || debugIndex == Gizmo.STENCIL_XY || debugIndex == Gizmo.STENCIL_ZY) strecke(gesamt, 0, 1, 0, size, 1);
            if (alleDrei || debugIndex == Gizmo.STENCIL_Z || debugIndex == Gizmo.STENCIL_XZ || debugIndex == Gizmo.STENCIL_ZY) strecke(gesamt, 0, 0, 1, size, 2);

            stack.popPose();

            return;
        }

        try (ByteBufferBuilder speicher = new ByteBufferBuilder(4096))
        {
            BufferBuilder builder = SofortZeichner.beginnen(speicher, PrimitiveTopology.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

            if (alleDrei || debugIndex == Gizmo.STENCIL_X || debugIndex == Gizmo.STENCIL_XZ || debugIndex == Gizmo.STENCIL_XY)
            {
                achse(builder, stack, 0, size, t, Colors.RED);
            }

            if (alleDrei || debugIndex == Gizmo.STENCIL_Y || debugIndex == Gizmo.STENCIL_XY || debugIndex == Gizmo.STENCIL_ZY)
            {
                achse(builder, stack, 1, size, t, Colors.GREEN);
            }

            if (alleDrei || debugIndex == Gizmo.STENCIL_Z || debugIndex == Gizmo.STENCIL_XZ || debugIndex == Gizmo.STENCIL_ZY)
            {
                achse(builder, stack, 2, size, t, Colors.BLUE);
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

    /**
     * Eine Achsenlinie — im Blender-Modus (P5) mit weicher Kante: deckender Kern plus zwei
     * Alpha-Huellen (Faux-AA ohne eigenen Shader, blickwinkelfest, Blend ist im Zug an).
     * Alt-Modus zeichnet die eine harte Box wie immer.
     */
    private static void achse(BufferBuilder builder, PoseStack stack, int richtung, float size, float t, int rgb)
    {
        if (!BBSSettings.transformBlenderFeedback.get())
        {
            box(builder, stack, richtung, size, t, rgb);

            return;
        }

        box(builder, stack, richtung, size, t * 2.2F, mitAlpha(rgb, 0.14F));
        box(builder, stack, richtung, size, t * 1.5F, mitAlpha(rgb, 0.4F));
        box(builder, stack, richtung, size, t, mitAlpha(rgb, 1F));
    }

    private static void box(BufferBuilder builder, PoseStack stack, int richtung, float size, float t, int farbe)
    {
        switch (richtung)
        {
            case 0 -> Draw.fillBox(builder, stack, -size, -t, -t, size, t, t, farbe);
            case 1 -> Draw.fillBox(builder, stack, -t, -size, -t, t, size, t, farbe);
            default -> Draw.fillBox(builder, stack, -t, -t, -size, t, t, size, farbe);
        }
    }

    private static int mitAlpha(int rgb, float a)
    {
        return ((int) (a * 255F) << 24) | (rgb & 0xFFFFFF);
    }

    /** Blender's default theme axis colours. */
    private static final int X_FARBE = 0xFFFF3352;
    private static final int Y_FARBE = 0xFF8BDC00;
    private static final int Z_FARBE = 0xFF2890FF;
    /** Line width in physical pixels; Blender's constraint line is about this wide at 1x UI scale. */
    private static final float BREITE_PX = 4F;

    /** NDC segments of this frame: x0, y0, x1, y1, axis (0..2, not the colour: X's and Y's ARGB
     *  are NaN bit patterns as a float and came back as 0x7FC00000, a half-transparent dark red).
     *  Created on use, not in the static initialiser: a class redefined into a running game gets
     *  new statics as null (measured, NullPointerException on the first frame after a live swap). */
    private static java.util.List<float[]> strecken;

    private static java.util.List<float[]> strecken()
    {
        if (strecken == null)
        {
            strecken = new java.util.ArrayList<>();
        }

        return strecken;
    }
    private static long zweiDZuletzt;

    /** Clips the infinite line through the pivot to the near plane and keeps its NDC ends. */
    private static void strecke(Matrix4f gesamt, float dx, float dy, float dz, float laenge, int achse)
    {
        Vector4f a = gesamt.transform(new Vector4f(-dx * laenge, -dy * laenge, -dz * laenge, 1F));
        Vector4f b = gesamt.transform(new Vector4f(dx * laenge, dy * laenge, dz * laenge, 1F));
        float nah = 1e-4F;

        if (a.w < nah && b.w < nah)
        {
            return;
        }

        if (a.w < nah || b.w < nah)
        {
            Vector4f hinten = a.w < nah ? a : b;
            Vector4f vorn = a.w < nah ? b : a;
            float t = (nah - vorn.w) / (hinten.w - vorn.w);

            hinten.set(vorn.x + (hinten.x - vorn.x) * t, vorn.y + (hinten.y - vorn.y) * t, vorn.z + (hinten.z - vorn.z) * t, nah);
        }

        strecken().add(new float[] {a.x / a.w, a.y / a.w, b.x / b.w, b.y / b.w, achse});
    }

    /**
     * Draws this frame's constraint lines over the viewport image (UIFilmPreview, after the
     * viewport texture), anti-aliased and BREITE_PX physical pixels wide whatever the distance.
     */
    public static void zeichnen2D(mchorse.bbs_mod.ui.framework.UIContext context, int x, int y, int w, int h)
    {
        zweiDZuletzt = System.nanoTime();

        if (strecken().isEmpty())
        {
            return;
        }

        float breite = BREITE_PX / mchorse.bbs_mod.BBSModClient.getGUIScale();
        net.minecraft.client.gui.navigation.ScreenRectangle bereich = new net.minecraft.client.gui.navigation.ScreenRectangle(x, y, w, h);

        context.batcher.clip(x, y, w, h, context);

        for (float[] s : strecken())
        {
            /* Far ends sit thousands of viewport widths out: pull them in along the line so the
             * tessellator works with sane numbers (the visible part does not change). */
            float[] p = einkuerzen(x + (s[0] + 1F) * 0.5F * w, y + (1F - s[1]) * 0.5F * h, x + (s[2] + 1F) * 0.5F * w, y + (1F - s[3]) * 0.5F * h, x, y, w, h);

            if (p != null)
            {
                mchorse.bbs_mod.ui.framework.elements.utils.vektor.VektorZeichner.linie(context.batcher, p, breite, s[4] == 0F ? X_FARBE : s[4] == 1F ? Y_FARBE : Z_FARBE, bereich);
            }
        }

        context.batcher.unclip(context);
        strecken().clear();
    }

    /** Cuts the segment to a box a little larger than the viewport (Liang-Barsky). */
    private static float[] einkuerzen(float x0, float y0, float x1, float y1, int x, int y, int w, int h)
    {
        float rand = 8F;
        float minX = x - rand, minY = y - rand, maxX = x + w + rand, maxY = y + h + rand;
        float dx = x1 - x0, dy = y1 - y0, t0 = 0F, t1 = 1F;
        float[] p = {-dx, dx, -dy, dy};
        float[] q = {x0 - minX, maxX - x0, y0 - minY, maxY - y0};

        for (int i = 0; i < 4; i++)
        {
            if (p[i] == 0F)
            {
                if (q[i] < 0F) return null;
                continue;
            }

            float r = q[i] / p[i];

            if (p[i] < 0F) t0 = Math.max(t0, r);
            else t1 = Math.min(t1, r);
        }

        if (t0 > t1)
        {
            return null;
        }

        return new float[] {x0 + dx * t0, y0 + dy * t0, x0 + dx * t1, y0 + dy * t1};
    }
}
