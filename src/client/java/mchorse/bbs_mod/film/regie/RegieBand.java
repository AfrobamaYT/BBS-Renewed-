package mchorse.bbs_mod.film.regie;

import java.util.List;

/**
 * Die Geometrie eines Trail-Bandes: eine Wegpunktfolge wird geglaettet, in festen Abstaenden
 * abgetastet und auf den Boden gelegt. Werte wie im Design (Band-Klasse des Exports):
 * Catmull-Rom mit 8 Unterteilungen, alle 0,22 m ein Querschnitt, Bodenhoehe mit Maximum
 * ueber ±0,4 m und Mittel ueber ±0,45 m (nie unter den Boden), 6 cm darueber.
 *
 * <p>Je Querschnitt zwei Ecken: Kante A (V = +1) und Kante B (V = -1); S ist die Strecke
 * entlang des Weges. Der Shader zeichnet daraus Rand, Kern und Chevrons (nur |V| zaehlt).</p>
 */
public final class RegieBand
{
    /** Hoehe des Bodens an (x, z), gesucht nahe der Bezugshoehe. */
    public interface Boden
    {
        double hoehe(double x, double z, double bezug);
    }

    private static final int UNTERTEILUNG = 8;
    private static final double ABSTAND = 0.22D;
    private static final int HOECHSTENS = 1400;
    private static final double UEBER_BODEN = 0.06D;

    public float laenge;
    public int anzahl;
    /** Je Querschnitt x, y, z der Ecke an Kante A (V = +1) und Kante B (V = -1). */
    public float[] kanteA;
    public float[] kanteB;
    /** Strecke entlang des Weges je Querschnitt. */
    public float[] s;

    /** @param punkte x, y, z je Wegpunkt; y ist nur die Bezugshoehe fuer die Bodensuche */
    public static RegieBand bauen(List<double[]> punkte, double breite, Boden boden)
    {
        RegieBand band = new RegieBand();

        if (punkte == null || punkte.size() < 2)
        {
            return band;
        }

        /* Catmull-Rom in xz, Bezugshoehe linear mit */
        int n = punkte.size();
        int glatt = (n - 1) * UNTERTEILUNG + 1;
        double[] gx = new double[glatt];
        double[] gy = new double[glatt];
        double[] gz = new double[glatt];
        double[] summe = new double[glatt];

        for (int i = 0; i < n - 1; i++)
        {
            double[] p0 = punkte.get(Math.max(0, i - 1));
            double[] p1 = punkte.get(i);
            double[] p2 = punkte.get(i + 1);
            double[] p3 = punkte.get(Math.min(n - 1, i + 2));

            for (int c = 0; c < UNTERTEILUNG; c++)
            {
                double t = c / (double) UNTERTEILUNG;
                int k = i * UNTERTEILUNG + c;

                gx[k] = catmullRom(p0[0], p1[0], p2[0], p3[0], t);
                gz[k] = catmullRom(p0[2], p1[2], p2[2], p3[2], t);
                gy[k] = p1[1] + (p2[1] - p1[1]) * t;
            }
        }

        double[] letzter = punkte.get(n - 1);

        gx[glatt - 1] = letzter[0];
        gy[glatt - 1] = letzter[1];
        gz[glatt - 1] = letzter[2];

        for (int i = 1; i < glatt; i++)
        {
            summe[i] = summe[i - 1] + Math.hypot(gx[i] - gx[i - 1], gz[i] - gz[i - 1]);
        }

        double gesamt = summe[glatt - 1];
        int r = (int) Math.min(HOECHSTENS, Math.max(2, Math.ceil(gesamt / ABSTAND) + 1));
        double[] x = new double[r];
        double[] z = new double[r];
        double[] boden0 = new double[r];

        for (int i = 0, j = 0; i < r; i++)
        {
            double ziel = i / (double) (r - 1) * gesamt;

            while (j < glatt - 2 && summe[j + 1] < ziel)
            {
                j++;
            }

            double t = (ziel - summe[j]) / Math.max(1.0E-9D, summe[j + 1] - summe[j]);

            t = Math.max(0D, Math.min(1D, t));
            x[i] = gx[j] + (gx[j + 1] - gx[j]) * t;
            z[i] = gz[j] + (gz[j + 1] - gz[j]) * t;
            boden0[i] = boden.hoehe(x[i], z[i], gy[j] + (gy[j + 1] - gy[j]) * t);
        }

        /* Bodenhoehe glaetten: erst Maximum ueber ±0,4 m (Kanten nicht anschneiden), dann Mittel */
        double[] hoch = fenster(boden0, (int) Math.max(1, Math.round(0.4D / ABSTAND)), true);
        double[] mittel = fenster(hoch, (int) Math.max(1, Math.round(0.45D / ABSTAND)), false);
        double halb = breite / 2D;

        band.laenge = (float) gesamt;
        band.anzahl = r;
        band.kanteA = new float[r * 3];
        band.kanteB = new float[r * 3];
        band.s = new float[r];

        for (int i = 0; i < r; i++)
        {
            int a = Math.max(0, i - 1);
            int b = Math.min(r - 1, i + 1);
            double tx = x[b] - x[a];
            double tz = z[b] - z[a];
            double l = Math.hypot(tx, tz);

            tx = l > 0 ? tx / l : 1D;
            tz = l > 0 ? tz / l : 0D;

            double y = Math.max(mittel[i], boden0[i]) + UEBER_BODEN;

            /* Kante A liegt auf der Normalen (-tz, tx), Kante B gegenueber */
            band.kanteA[i * 3] = (float) (x[i] - tz * halb);
            band.kanteA[i * 3 + 1] = (float) y;
            band.kanteA[i * 3 + 2] = (float) (z[i] + tx * halb);
            band.kanteB[i * 3] = (float) (x[i] + tz * halb);
            band.kanteB[i * 3 + 1] = (float) y;
            band.kanteB[i * 3 + 2] = (float) (z[i] - tx * halb);
            band.s[i] = (float) (i / (double) (r - 1) * gesamt);
        }

        return band;
    }

    static double catmullRom(double p0, double p1, double p2, double p3, double t)
    {
        double t2 = t * t;
        double t3 = t2 * t;

        return 0.5D * (2D * p1 + (-p0 + p2) * t + (2D * p0 - 5D * p1 + 4D * p2 - p3) * t2 + (-p0 + 3D * p1 - 3D * p2 + p3) * t3);
    }

    /** Gleitendes Maximum bzw. Mittel ueber ±breite Nachbarn (Rand wiederholt). */
    private static double[] fenster(double[] werte, int breite, boolean maximum)
    {
        double[] aus = new double[werte.length];

        for (int i = 0; i < werte.length; i++)
        {
            double wert = maximum ? -Double.MAX_VALUE : 0D;

            for (int d = -breite; d <= breite; d++)
            {
                double w = werte[Math.min(werte.length - 1, Math.max(0, i + d))];

                wert = maximum ? Math.max(wert, w) : wert + w;
            }

            aus[i] = maximum ? wert : wert / (2 * breite + 1);
        }

        return aus;
    }
}
