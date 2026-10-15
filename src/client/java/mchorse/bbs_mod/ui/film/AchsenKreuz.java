package mchorse.bbs_mod.ui.film;

import com.mojang.math.Axis;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Das kleine XYZ-Kreuz unten links in der Filmvorschau.
 *
 * <p>Bis 1.21.1 war das ein Aufruf: {@code RenderSystem.renderCrosshair(10)} im Sofortmodus.
 * Den Sofortmodus gibt es in 26.2 nicht mehr, und Vanillas Nachfolger {@code Gizmos} reicht
 * WELT-Linien beim Bildgraphen ein — er weiss nichts vom Modelview-Stapel einer Oberflaeche.
 *
 * <p>Also selbst gezeichnet, aber nicht selbst erfunden: Geometrie und Farben stammen aus
 * {@code GLX._renderCrosshair} der 1.21.1-Fassung (Bytecode gelesen) — schwarze Kontur mit
 * Linienbreite 4, darueber X {@code 0xFFFF0000}, Y {@code 0xFF00FF00}, Z {@code 0xFF8080FF}.
 * Das Z ist absichtlich blauviolett und nicht rein blau.
 *
 * <p>⚠️ Die Tiefensortierung der drei Linien geht dabei verloren — bei 10 px Laenge ist das
 * nicht sichtbar, und der Sofortmodus zeichnete ohnehin ohne Tiefentest.
 */
public final class AchsenKreuz
{
    private static final int[] FARBEN = {0xFFFF0000, 0xFF00FF00, 0xFF8080FF};
    private static final int KONTUR = 0xFF000000;

    private AchsenKreuz()
    {}

    /**
     * @param neigung Kameraneigung in Grad, @param gierung Kameragierung in Grad —
     *                beide so herum wie in 1.21.1: rotateX(-neigung), dann rotateY(+gierung).
     */
    public static void zeichne(Batcher2D batcher, float x, float y, float neigung,
        float gierung, float groesse)
    {
        Matrix4f dreh = new Matrix4f()
            .rotate(Axis.XN.rotationDegrees(neigung))
            .rotate(Axis.YP.rotationDegrees(gierung))
            .scale(-1F, -1F, -1F);

        Vector3f[] enden = {
            dreh.transformPosition(new Vector3f(groesse, 0F, 0F)),
            dreh.transformPosition(new Vector3f(0F, groesse, 0F)),
            dreh.transformPosition(new Vector3f(0F, 0F, groesse))
        };

        /* Erst alle Konturen, dann alle Farben — sonst deckt die Kontur der dritten Achse
         * die Farbe der ersten zu. Genau diese Reihenfolge hatte auch der Sofortmodus. */
        for (Vector3f ende : enden)
        {
            /* 1F, nicht 2F: gemessen quer durch die gruene Achse trug 1.21.1 dort
             * 2 px Schwarz, mit 2F wurden es 6 px. */
            strich(batcher, x, y, ende, 1F, KONTUR);
        }

        for (int i = 0; i < enden.length; i++)
        {
            strich(batcher, x, y, enden[i], 0.5F, FARBEN[i]);
        }
    }

    /**
     * Eine Linie vom Ursprung nach {@code ende} — als Kasten in einem gedrehten Pose.
     *
     * <p>⛔ ES IST DER 2D-STAPEL, NICHT DER PoseStack (gemessen 2026-08-10: erster Anlauf
     * ueber {@code batcher.pose()} zeichnete nichts). {@code Batcher2D.quad} liest
     * {@code context.pose()} — eine {@link Matrix3x2fStack}. Der {@code PoseStack} daneben
     * ist ein anderer Stapel und geht in den Zug gar nicht ein.
     */
    private static void strich(Batcher2D batcher, float x, float y, Vector3f ende,
        float halbeBreite, int farbe)
    {
        float laenge = (float) Math.sqrt(ende.x * ende.x + ende.y * ende.y);

        if (laenge < 0.01F)
        {
            return;
        }

        Matrix3x2fStack pose = batcher.getContext().bbsPose();

        pose.pushMatrix();
        pose.translate(x, y);
        pose.rotate((float) Math.atan2(ende.y, ende.x));
        batcher.box(0F, -halbeBreite, laenge, halbeBreite, farbe);
        pose.popMatrix();
    }
}
