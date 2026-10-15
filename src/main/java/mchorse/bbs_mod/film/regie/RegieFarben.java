package mchorse.bbs_mod.film.regie;

import java.util.List;

/**
 * Farben der Regie-Marker — Palette und Vergabe genau wie im Design „Regiehilfe“.
 *
 * <p>Vergabe: Marker, die zeitlich nah beieinanderliegen (±4 s), sollen sich farblich am
 * staerksten unterscheiden. Gemessen wird der Abstand in OKLab (wahrnehmungsgleich), dann
 * verlieren schon oft benutzte Farben leicht. Formel und Gewichte sind die des Designs.</p>
 */
public final class RegieFarben
{
    /** Magenta, Bernstein, Violett, Aqua, Koralle, Zitrone, Flieder. */
    public static final int[] PALETTE = {0xFF4FA6, 0xFFB21F, 0xA57DFF, 0x3DE3EE, 0xFF5E4D, 0xF4EC57, 0xE68CFF};

    /** Zeitlicher Abstand, unter dem zwei Marker als „nah“ gelten: 4 s in Ticks. */
    private static final int NAH = 80;

    private RegieFarben()
    {}

    /**
     * @param start Beginn des neuen Markers in Ticks
     * @param ende Ende des neuen Markers in Ticks
     * @param ausser dieser Marker zaehlt nicht mit (der gerade umgefaerbte), darf null sein
     * @return Farbe als 0xAARRGGBB
     */
    public static int naechste(List<RegieMarker> marker, int start, int ende, RegieMarker ausser)
    {
        int beste = 0;
        double besteWertung = Double.NEGATIVE_INFINITY;

        for (int c = 0; c < PALETTE.length; c++)
        {
            double[] kandidat = oklab(PALETTE[c]);
            double naechster = 10;
            int benutzt = 0;

            for (RegieMarker m : marker)
            {
                if (m == ausser)
                {
                    continue;
                }

                int farbe = m.farbe.get() & 0xFFFFFF;

                if (m.start.get() < ende + NAH && m.ende.get() > start - NAH)
                {
                    naechster = Math.min(naechster, abstand(kandidat, oklab(farbe)));
                }

                if (farbe == PALETTE[c])
                {
                    benutzt++;
                }
            }

            double wertung = naechster * 10 - benutzt * 0.6 - c * 0.01;

            if (wertung > besteWertung)
            {
                besteWertung = wertung;
                beste = c;
            }
        }

        return 0xFF000000 | PALETTE[beste];
    }

    static double[] oklab(int rgb)
    {
        double r = linear(((rgb >> 16) & 255) / 255.0);
        double g = linear(((rgb >> 8) & 255) / 255.0);
        double b = linear((rgb & 255) / 255.0);
        double l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
        double m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
        double s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);

        return new double[] {
            0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
        };
    }

    static double abstand(double[] a, double[] b)
    {
        return Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2]));
    }

    private static double linear(double v)
    {
        return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }
}
