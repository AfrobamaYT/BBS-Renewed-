package mchorse.bbs_mod.ui.framework.elements.utils.vektor;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3x2f;

/**
 * Geglaettete Polylinie (Programm P2): Kernband in voller Farbe plus beidseitiger
 * 1-px-Saum auf Alpha 0. Gelenke ueber gemittelte Punktnormalen (Miter, auf 2×
 * begrenzt) — bei den dichten Kurven des Graph-Editors sind die Winkel klein und
 * die Naht unsichtbar. Gleiche Pipeline wie {@link VektorFlaeche}, kein Shader.
 */
public final class VektorLinie
{
    private VektorLinie()
    {}

    /** @param punkte x/y-Paare in GUI-Einheiten (mindestens 2 Punkte = 4 Werte). */
    public static void zug(VertexConsumer ecken, Matrix3x2f pose, float[] punkte, float breite, int color, float saum)
    {
        int n = punkte.length / 2;

        if (n < 2)
        {
            return;
        }

        float halb = Math.max(breite, 0.1F) / 2F;
        int durchsichtig = color & 0x00FFFFFF;
        float[] nx = new float[n];
        float[] ny = new float[n];

        for (int i = 0; i < n; i++)
        {
            /* Punktnormale = normierte Summe der Segmentnormalen (am Ende: die eine). */
            float sx = 0F, sy = 0F;

            if (i > 0)
            {
                float dx = punkte[i * 2] - punkte[(i - 1) * 2];
                float dy = punkte[i * 2 + 1] - punkte[(i - 1) * 2 + 1];
                float l = (float) Math.sqrt(dx * dx + dy * dy);

                if (l > 1e-6F)
                {
                    sx += -dy / l;
                    sy += dx / l;
                }
            }

            if (i < n - 1)
            {
                float dx = punkte[(i + 1) * 2] - punkte[i * 2];
                float dy = punkte[(i + 1) * 2 + 1] - punkte[i * 2 + 1];
                float l = (float) Math.sqrt(dx * dx + dy * dy);

                if (l > 1e-6F)
                {
                    sx += -dy / l;
                    sy += dx / l;
                }
            }

            float l = (float) Math.sqrt(sx * sx + sy * sy);

            if (l < 1e-6F)
            {
                nx[i] = 0F;
                ny[i] = 0F;

                continue;
            }

            /* Miter-Laenge 1/cos(θ/2), fuer spitze Winkel auf 2 begrenzt — nur zwischen ZWEI
             * Segmenten. Ein Endpunkt hat eine Normale (l = 1) und bekam trotzdem den Faktor 2:
             * jede Linie aus zwei Punkten war doppelt so breit, ein geschlossener Ring hatte eine
             * Kerbe am Anfang (gemessen 2026-10-14 an der Orbit-Achse, 4 -> 8 px). */
            float miter = i > 0 && i < n - 1 ? Math.min(2F / l, 2F) : 1F;

            nx[i] = sx / l * miter;
            ny[i] = sy / l * miter;
        }

        for (int i = 0; i < n - 1; i++)
        {
            int j = i + 1;
            float aKx = nx[i] * halb, aKy = ny[i] * halb;
            float bKx = nx[j] * halb, bKy = ny[j] * halb;
            float aSx = nx[i] * (halb + saum), aSy = ny[i] * (halb + saum);
            float bSx = nx[j] * (halb + saum), bSy = ny[j] * (halb + saum);
            float ax = punkte[i * 2], ay = punkte[i * 2 + 1];
            float bx = punkte[j * 2], by = punkte[j * 2 + 1];

            /* Kernband. */
            quad(ecken, pose, ax - aKx, ay - aKy, bx - bKx, by - bKy, bx + bKx, by + bKy, ax + aKx, ay + aKy, color, color, color, color);
            /* Saum beidseits. */
            quad(ecken, pose, ax + aKx, ay + aKy, bx + bKx, by + bKy, bx + bSx, by + bSy, ax + aSx, ay + aSy, color, color, durchsichtig, durchsichtig);
            quad(ecken, pose, ax - aSx, ay - aSy, bx - bSx, by - bSy, bx - bKx, by - bKy, ax - aKx, ay - aKy, durchsichtig, durchsichtig, color, color);
        }
    }

    private static void quad(VertexConsumer ecken, Matrix3x2f pose, float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4, int c1, int c2, int c3, int c4)
    {
        ecken.addVertexWith2DPose(pose, x1, y1).setColor(c1);
        ecken.addVertexWith2DPose(pose, x2, y2).setColor(c2);
        ecken.addVertexWith2DPose(pose, x3, y3).setColor(c3);

        ecken.addVertexWith2DPose(pose, x1, y1).setColor(c1);
        ecken.addVertexWith2DPose(pose, x3, y3).setColor(c3);
        ecken.addVertexWith2DPose(pose, x4, y4).setColor(c4);
    }
}
