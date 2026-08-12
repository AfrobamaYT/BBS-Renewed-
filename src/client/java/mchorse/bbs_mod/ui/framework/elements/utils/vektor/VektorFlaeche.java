package mchorse.bbs_mod.ui.framework.elements.utils.vektor;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3x2f;

/**
 * CPU-Tessellation fuer weiche UI-Flaechen (Programm P2), ImGuis Kanon-Technik:
 * innen ein voll deckender Faecher, aussen ein schmaler Saum, dessen Aussenkante auf
 * Alpha 0 faellt — Kantenglaettung ohne eigenen Shader, durch die normale
 * GUI-Farb-Pipeline und damit Scissor-/Batching-kompatibel.
 */
public final class VektorFlaeche
{
    private VektorFlaeche()
    {}

    /**
     * Rundeck-Quad. {@code radius} wird auf die halbe kurze Kante begrenzt,
     * {@code saum} ist die Saumbreite in GUI-Einheiten (~1 physisches Pixel).
     */
    public static void rundeck(VertexConsumer ecken, Matrix3x2f pose, float x, float y, float w, float h, float radius, int color, float saum)
    {
        float r = Math.min(radius, Math.min(w, h) / 2F);
        int segmente = Math.max(3, Math.round(r));
        int n = (segmente + 1) * 4;
        float[] px = new float[n];
        float[] py = new float[n];
        float[] nx = new float[n];
        float[] ny = new float[n];

        /* Vier Viertelboegen um die Eckzentren; Bildschirm-Y waechst nach unten,
         * die Winkel laufen darum oben-rechts (-90°..0°) im Uhrzeigersinn weiter.
         * Arc-Normalen sind exakt (p - zentrum)/r — auf den geraden Kanten dazwischen
         * teilen sich die Endpunkte die achsparallele Normale, der Saum bleibt stetig. */
        float[][] zentren = {
            {x + w - r, y + r},
            {x + w - r, y + h - r},
            {x + r, y + h - r},
            {x + r, y + r},
        };

        int i = 0;

        for (int ecke = 0; ecke < 4; ecke++)
        {
            float start = (float) (-Math.PI / 2D + ecke * Math.PI / 2D);

            for (int s = 0; s <= segmente; s++, i++)
            {
                float a = start + (float) (Math.PI / 2D) * s / segmente;

                nx[i] = (float) Math.cos(a);
                ny[i] = (float) Math.sin(a);
                px[i] = zentren[ecke][0] + nx[i] * r;
                py[i] = zentren[ecke][1] + ny[i] * r;
            }
        }

        float cx = x + w / 2F;
        float cy = y + h / 2F;
        int durchsichtig = color & 0x00FFFFFF;

        for (int a = 0; a < n; a++)
        {
            int b = (a + 1) % n;

            /* Innen: Faecher vom Mittelpunkt (Rundeck ist konvex). */
            ecken.addVertexWith2DPose(pose, cx, cy).setColor(color);
            ecken.addVertexWith2DPose(pose, px[a], py[a]).setColor(color);
            ecken.addVertexWith2DPose(pose, px[b], py[b]).setColor(color);

            /* Aussen: Saum-Quad, Aussenkante gleiche Farbe mit Alpha 0 —
             * gleiches RGB, sonst zieht das Mischen einen dunklen Rand. */
            float ax = px[a] + nx[a] * saum;
            float ay = py[a] + ny[a] * saum;
            float bx = px[b] + nx[b] * saum;
            float by = py[b] + ny[b] * saum;

            ecken.addVertexWith2DPose(pose, px[a], py[a]).setColor(color);
            ecken.addVertexWith2DPose(pose, ax, ay).setColor(durchsichtig);
            ecken.addVertexWith2DPose(pose, bx, by).setColor(durchsichtig);

            ecken.addVertexWith2DPose(pose, px[a], py[a]).setColor(color);
            ecken.addVertexWith2DPose(pose, bx, by).setColor(durchsichtig);
            ecken.addVertexWith2DPose(pose, px[b], py[b]).setColor(color);
        }
    }
}
