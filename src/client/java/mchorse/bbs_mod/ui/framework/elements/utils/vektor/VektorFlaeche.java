package mchorse.bbs_mod.ui.framework.elements.utils.vektor;

import com.mojang.blaze3d.vertex.VertexConsumer;
import mchorse.bbs_mod.utils.colors.Colors;
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
     * Umriss eines Rundecks: je Punkt Position und exakte Aussennormale.
     * Arc-Normalen kommen aus den Eckzentren; auf den geraden Kanten dazwischen
     * teilen sich die Endpunkte die achsparallele Normale — Saeume bleiben stetig.
     * Rueckgabe: {px[], py[], nx[], ny[]}.
     */
    static float[][] umriss(float x, float y, float w, float h, float radius)
    {
        float r = Math.min(radius, Math.min(w, h) / 2F);
        int segmente = Math.max(3, Math.round(r));
        int n = (segmente + 1) * 4;
        float[] px = new float[n];
        float[] py = new float[n];
        float[] nx = new float[n];
        float[] ny = new float[n];

        /* Bildschirm-Y waechst nach unten; die Boegen laufen ab oben-rechts (-90°..0°)
         * im Uhrzeigersinn um die vier Eckzentren weiter. */
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

        return new float[][] {px, py, nx, ny};
    }

    /**
     * Rundeck-Quad. {@code radius} wird auf die halbe kurze Kante begrenzt,
     * {@code saum} ist die Saumbreite in GUI-Einheiten (~1 physisches Pixel).
     */
    public static void rundeck(VertexConsumer ecken, Matrix3x2f pose, float x, float y, float w, float h, float radius, int color, float saum)
    {
        float[][] u = umriss(x, y, w, h, radius);
        float[] px = u[0], py = u[1], nx = u[2], ny = u[3];
        int n = px.length;
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

            /* Aussen: Saum-Quad — gleiche Farbe mit Alpha 0, sonst dunkler Rand. */
            saumQuad(ecken, pose, px[a], py[a], px[b], py[b],
                px[a] + nx[a] * saum, py[a] + ny[a] * saum,
                px[b] + nx[b] * saum, py[b] + ny[b] * saum,
                color, durchsichtig, durchsichtig);
        }
    }

    /**
     * Konvexes Polygon mit AA-Saum — dieselbe Technik wie {@link #rundeck}, nur mit frei
     * gegebenen Ecken statt eines Rechteck-Umrisses.
     *
     * <p>⛔ WOFUER (2026-08-21). Die Merkstellen-Fahne auf der Zeitleiste zeichnete ihre
     * Spitze als Schleife von 1-Pixel-Kaesten — eine sichtbare Treppe. Der Vektor-Zeichner
     * konnte bis dahin nur Rundecke und Linien; fuer eine glatte Pfeilform fehlte das
     * Polygon. Nutzer: „das war ein grauer pfeil der smooth war anstatt den alten blauen
     * pixel optischen marker."</p>
     *
     * <p>Erwartet ein KONVEXES Polygon im Uhrzeigersinn oder dagegen — der Faecher laeuft
     * vom Schwerpunkt, das traegt nur konvexe Formen. Die Aussennormale je Ecke wird aus
     * den beiden anliegenden Kanten gemittelt.</p>
     */
    public static void polygon(VertexConsumer ecken, Matrix3x2f pose, float[] px, float[] py, int color, float saum)
    {
        int n = px.length;

        if (n < 3)
        {
            return;
        }

        float cx = 0F;
        float cy = 0F;

        for (int a = 0; a < n; a++)
        {
            cx += px[a];
            cy += py[a];
        }

        cx /= n;
        cy /= n;

        /* Aussennormale je Ecke: Mittel der beiden Kantennormalen, vom Schwerpunkt weg. */
        float[] nx = new float[n];
        float[] ny = new float[n];

        for (int a = 0; a < n; a++)
        {
            int v = (a - 1 + n) % n;
            int b = (a + 1) % n;
            float sx = kantenNormalX(px[v], py[v], px[a], py[a]) + kantenNormalX(px[a], py[a], px[b], py[b]);
            float sy = kantenNormalY(px[v], py[v], px[a], py[a]) + kantenNormalY(px[a], py[a], px[b], py[b]);
            float len = (float) Math.sqrt(sx * sx + sy * sy);

            if (len < 1e-5F)
            {
                sx = px[a] - cx;
                sy = py[a] - cy;
                len = Math.max(1e-5F, (float) Math.sqrt(sx * sx + sy * sy));
            }

            /* Richtung pruefen: die Normale muss vom Schwerpunkt WEG zeigen. */
            float rx = sx / len;
            float ry = sy / len;

            if (rx * (px[a] - cx) + ry * (py[a] - cy) < 0F)
            {
                rx = -rx;
                ry = -ry;
            }

            nx[a] = rx;
            ny[a] = ry;
        }

        int durchsichtig = color & 0x00FFFFFF;

        for (int a = 0; a < n; a++)
        {
            int b = (a + 1) % n;

            ecken.addVertexWith2DPose(pose, cx, cy).setColor(color);
            ecken.addVertexWith2DPose(pose, px[a], py[a]).setColor(color);
            ecken.addVertexWith2DPose(pose, px[b], py[b]).setColor(color);

            saumQuad(ecken, pose, px[a], py[a], px[b], py[b],
                px[a] + nx[a] * saum, py[a] + ny[a] * saum,
                px[b] + nx[b] * saum, py[b] + ny[b] * saum,
                color, durchsichtig, durchsichtig);
        }
    }

    private static float kantenNormalX(float ax, float ay, float bx, float by)
    {
        float dy = by - ay;
        float dx = bx - ax;
        float len = Math.max(1e-5F, (float) Math.sqrt(dx * dx + dy * dy));

        return dy / len;
    }

    private static float kantenNormalY(float ax, float ay, float bx, float by)
    {
        float dy = by - ay;
        float dx = bx - ax;
        float len = Math.max(1e-5F, (float) Math.sqrt(dx * dx + dy * dy));

        return -dx / len;
    }

    /**
     * Weicher Schein um ein Rundeck — Ersatz fuer die dropShadow-Trapeze: das Innere
     * fuellt {@code innen}, nach aussen laufen Ringe ueber {@code ausdehnung} auf
     * {@code aussen} zu (quadratisches Abklingen ≈ Gauss-Optik).
     */
    public static void schein(VertexConsumer ecken, Matrix3x2f pose, float x, float y, float w, float h, float radius, float ausdehnung, int innen, int aussen, float saum)
    {
        rundeck(ecken, pose, x, y, w, h, radius, innen, saum);

        float[][] u = umriss(x, y, w, h, radius);
        float[] px = u[0], py = u[1], nx = u[2], ny = u[3];
        int n = px.length;
        int ringe = 4;

        for (int ring = 0; ring < ringe; ring++)
        {
            float t0 = ring / (float) ringe;
            float t1 = (ring + 1) / (float) ringe;
            int f0 = Colors.lerp(innen, aussen, 1F - (1F - t0) * (1F - t0));
            int f1 = Colors.lerp(innen, aussen, 1F - (1F - t1) * (1F - t1));

            for (int a = 0; a < n; a++)
            {
                int b = (a + 1) % n;

                saumQuad(ecken, pose,
                    px[a] + nx[a] * ausdehnung * t0, py[a] + ny[a] * ausdehnung * t0,
                    px[b] + nx[b] * ausdehnung * t0, py[b] + ny[b] * ausdehnung * t0,
                    px[a] + nx[a] * ausdehnung * t1, py[a] + ny[a] * ausdehnung * t1,
                    px[b] + nx[b] * ausdehnung * t1, py[b] + ny[b] * ausdehnung * t1,
                    f0, f1, f1);
            }
        }
    }

    /** Zwei Dreiecke zwischen Innenkante (ia..ib, Farbe fi) und Aussenkante (aa..ab, Farben fa/fb). */
    private static void saumQuad(VertexConsumer ecken, Matrix3x2f pose, float iax, float iay, float ibx, float iby, float aax, float aay, float abx, float aby, int fi, int fa, int fb)
    {
        ecken.addVertexWith2DPose(pose, iax, iay).setColor(fi);
        ecken.addVertexWith2DPose(pose, aax, aay).setColor(fa);
        ecken.addVertexWith2DPose(pose, abx, aby).setColor(fb);

        ecken.addVertexWith2DPose(pose, iax, iay).setColor(fi);
        ecken.addVertexWith2DPose(pose, abx, aby).setColor(fb);
        ecken.addVertexWith2DPose(pose, ibx, iby).setColor(fi);
    }
}
