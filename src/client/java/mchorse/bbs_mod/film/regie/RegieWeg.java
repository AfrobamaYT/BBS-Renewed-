package mchorse.bbs_mod.film.regie;

import java.util.ArrayList;
import java.util.List;

/**
 * Wege-Chip (.rg-leg im Design): ein Abschnitt vom Startpunkt durch die Ort-Marker mit
 * Distanz, Gehzeit und Urteil, ob er in der verfuegbaren Zeit zu schaffen ist. Laenge und
 * Mitte kommen vom gefundenen Weg der Weltschicht; solange keiner da ist, Luftlinie.
 */
public final class RegieWeg
{
    /** Minecraft: Gehen 4,317 m/s, Rennen 5,612 m/s (im Design auf 4,3/5,6 gerundet). */
    public static final float GEHEN = 4.3F;
    public static final float RENNEN = 5.6F;

    public enum Urteil { MACHBAR, KNAPP, ZU_WEIT }

    /** Gefundener Weg zwischen zwei Punkten: {Laenge, x, y, z der Wegmitte} oder null. */
    public interface Bekannt
    {
        float[] weg(double[] von, double[] nach);
    }

    public int farbe;
    public String distanz;
    public String zeit;
    public String status;
    public Urteil urteil;
    public float x;
    public float y;

    /** Alle Abschnitte, deren Mitte im Bild liegt; jeder belegt seinen Platz fuer die Karten. */
    static void alle(Regie regie, List<RegieMarker> sortiert, RegieKamera kamera, float breite, float hoehe,
        float k, float[] p, Bekannt bekannt, List<RegieWeg> chips, List<float[]> belegt)
    {
        float anfang = regie.anfang.get() / 20F;
        /* x, y, z, Ankunft, Abgang, Farbe */
        List<double[]> punkte = new ArrayList<>();

        if (regie.startGesetzt.get())
        {
            punkte.add(new double[] {regie.startX.get(), regie.startY.get(), regie.startZ.get(), anfang, anfang, 0});
        }

        for (RegieMarker m : sortiert)
        {
            if (m.istOrt() && m.ende.get() / 20F > anfang)
            {
                punkte.add(new double[] {m.x.get(), m.y.get(), m.z.get(), m.start.get() / 20F, m.ende.get() / 20F, m.farbe.get()});
            }
        }

        for (int i = 1; i < punkte.size(); i++)
        {
            double[] von = punkte.get(i - 1);
            double[] nach = punkte.get(i);
            float[] weg = bekannt == null ? null : bekannt.weg(von, nach);
            float distanz = weg != null ? weg[0] : (float) Math.hypot(nach[0] - von[0], nach[2] - von[2]);
            float gehen = distanz / GEHEN;
            float rennen = distanz / RENNEN;
            float verfuegbar = (float) (nach[3] - von[3]);
            float los = (float) (nach[3] - gehen);
            Urteil urteil = Urteil.MACHBAR;

            if (gehen > verfuegbar - 0.2F || los < von[4] - 2.5F)
            {
                urteil = rennen <= verfuegbar - 0.1F ? Urteil.KNAPP : Urteil.ZU_WEIT;
            }

            if (gehen > verfuegbar - 0.2F && rennen > verfuegbar - 0.1F)
            {
                urteil = Urteil.ZU_WEIT;
            }

            /* Standing still is always walkable: a new marker starts at 0 s, so with the
             * safety margins above the first location read "0 m - too far". */
            if (distanz < 0.5F)
            {
                urteil = Urteil.MACHBAR;
            }

            boolean vorn = weg != null ? kamera.projizieren(weg[1], weg[2] + 0.3D, weg[3], breite, hoehe, p)
                : kamera.projizieren((von[0] + nach[0]) / 2D, (von[1] + nach[1]) / 2D + 0.3D, (von[2] + nach[2]) / 2D, breite, hoehe, p);

            if (!vorn || !RegieAnsicht.innen(p[0], p[1], breite, hoehe, 10F * k))
            {
                continue;
            }

            RegieWeg chip = new RegieWeg();

            chip.farbe = (int) nach[5];
            chip.distanz = Math.round(distanz) + " m";
            chip.zeit = RegieAnsicht.zahl(gehen) + " s";
            chip.urteil = urteil;
            chip.status = urteil == Urteil.MACHBAR ? "walkable" : urteil == Urteil.KNAPP ? "sprint only" : "too far";
            chip.x = p[0];
            chip.y = p[1];
            chips.add(chip);
            belegt.add(new float[] {chip.x - 80F * k, chip.x + 80F * k, chip.y - 16F * k, chip.y + 16F * k});
        }
    }
}
