package mchorse.bbs_mod.film.regie;

/**
 * Fadenkreuz eines Blick-Markers (.rg-ret im Design): vier Ecken um den Blickpunkt, Groesse
 * nach Entfernung (0,55 Bloecke Radius, beim Spielen mindestens 56 px, im Editor 40 px).
 */
public final class RegieKreuz
{
    public int farbe;
    public RegieAnsicht.Phase phase;
    public boolean fang;
    public float x;
    public float y;
    public float groesse;

    /** @return null, wenn der Punkt hinter der Kamera oder (im Editor) ausserhalb liegt */
    static RegieKreuz fuer(RegieMarker marker, RegieAnsicht.Phase phase, boolean fang, boolean spielen,
        RegieKamera kamera, float breite, float hoehe, float k, float[] p)
    {
        boolean vorn = kamera.projizieren(marker.x.get(), marker.y.get(), marker.z.get(), breite, hoehe, p);

        if (!vorn || !spielen && !RegieAnsicht.innen(p[0], p[1], breite, hoehe, 0F))
        {
            return null;
        }

        RegieKreuz kreuz = new RegieKreuz();

        kreuz.farbe = marker.farbe.get();
        kreuz.phase = phase;
        kreuz.fang = fang;
        kreuz.x = p[0];
        kreuz.y = p[1];
        kreuz.groesse = RegieAnsicht.klemmen(0.55F / Math.max(0.5F, p[2]) * kamera.brennweite(hoehe) * 2F,
            (spielen ? 56F : 40F) * k, 190F * k);

        return kreuz;
    }
}
