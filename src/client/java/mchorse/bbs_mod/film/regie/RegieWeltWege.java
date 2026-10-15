package mchorse.bbs_mod.film.regie;

import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Wege-Baender im Editor: vom Startpunkt durch die Ort-Marker, gedimmt (Design: Breite 1,05,
 * Helligkeit 0,8, Lauf 0,6). Die gefundenen Wege liefern auch Laenge und Mitte der Wege-Chips.
 */
final class RegieWeltWege
{
    private static final RegiePfadsuche SUCHE = new RegiePfadsuche();

    private static String wegeSchluessel;
    private static final List<RegieBand> WEGE = new ArrayList<>();
    private static final List<Integer> WEGFARBEN = new ArrayList<>();
    private static final Map<String, RegieBand> WEG_NACH_ENDEN = new HashMap<>();
    private static float wegePhase;
    private static float wegeAlter;

    private RegieWeltWege()
    {
    }

    /** Im Editor: Abschnitte vom Startpunkt durch die Ort-Marker. */
    static void zeichnen(Level level, Regie regie, float dt, CameraRenderState kamera)
    {
        List<double[]> punkte = new ArrayList<>();
        List<Integer> farben = new ArrayList<>();
        StringBuilder schluessel = new StringBuilder();
        float anfang = regie.anfang.get();

        if (regie.startGesetzt.get())
        {
            punkte.add(new double[] {regie.startX.get(), regie.startY.get(), regie.startZ.get()});
            farben.add(0);
        }

        for (RegieMarker m : RegieAnsicht.sortiert(regie))
        {
            if (m.istOrt() && m.ende.get() > anfang)
            {
                punkte.add(new double[] {m.x.get(), m.y.get(), m.z.get()});
                farben.add(m.farbe.get());
            }
        }

        for (double[] p : punkte)
        {
            schluessel.append(p[0]).append(',').append(p[1]).append(',').append(p[2]).append(';');
        }

        wegeAlter += dt;

        /* Neu suchen, wenn Start oder Marker sich bewegen — und jede Sekunde, weil sich die
         * Welt aendern kann (eine gebaute Mauer, eine abgebaute Treppe). */
        if (!schluessel.toString().equals(wegeSchluessel) || wegeAlter > 1F)
        {
            wegeSchluessel = schluessel.toString();
            wegeAlter = 0F;
            WEGE.clear();
            WEGFARBEN.clear();
            WEG_NACH_ENDEN.clear();

            for (int i = 1; i < punkte.size(); i++)
            {
                double[] a = punkte.get(i - 1);
                double[] b = punkte.get(i);
                List<double[]> weg = SUCHE.weg(level, a[0], a[1], a[2], b[0], b[1], b[2]);

                RegieBand band = RegieBand.bauen(weg != null ? weg : List.of(a, b), 1.05D, (x, z, bezug) -> RegiePfadsuche.boden(level, x, z, bezug));

                WEGE.add(band);
                WEG_NACH_ENDEN.put(enden(a, b), band);
                WEGFARBEN.add(farben.get(i));
            }
        }

        wegePhase += dt * 0.6F;

        for (int i = 0; i < WEGE.size(); i++)
        {
            RegieWeltZug.band(WEGE.get(i), WEGFARBEN.get(i), new float[] {WEGE.get(i).laenge, wegePhase, 0.8F, 1F}, 0.6F, kamera);
        }
    }

    /** Fuer die Wege-Chips: Laenge und Mitte des gefundenen Weges zwischen zwei Punkten, oder null. */
    static float[] weg(double[] von, double[] nach)
    {
        RegieBand band = WEG_NACH_ENDEN.get(enden(von, nach));

        if (band == null || band.anzahl < 2)
        {
            return null;
        }

        int i = 0;

        while (i < band.anzahl - 1 && band.s[i] < band.laenge / 2F)
        {
            i++;
        }

        return new float[] {band.laenge, (band.kanteA[i * 3] + band.kanteB[i * 3]) / 2F,
            (band.kanteA[i * 3 + 1] + band.kanteB[i * 3 + 1]) / 2F, (band.kanteA[i * 3 + 2] + band.kanteB[i * 3 + 2]) / 2F};
    }

    private static String enden(double[] a, double[] b)
    {
        return a[0] + "," + a[1] + "," + a[2] + ">" + b[0] + "," + b[1] + "," + b[2];
    }
}
