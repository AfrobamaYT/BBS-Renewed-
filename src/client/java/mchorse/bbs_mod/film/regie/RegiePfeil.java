package mchorse.bbs_mod.film.regie;

/**
 * Randpfeil fuer ein Ziel ausserhalb des Bildes (.rg-edge im Design): 64 px vom Rand, zeigt
 * in Zielrichtung. Hinter der Kamera stehen die projizierten Werte gespiegelt — ohne Umkehr
 * zeigte der Pfeil auf die falsche Seite.
 */
public final class RegiePfeil
{
    public int farbe;
    public String nummer;
    /** Countdown wie auf der Karte: "in 3.2 s" vor dem Fenster, Restzeit "4.1 s" darin. */
    public String zeit;
    public boolean aktiv;
    public float x;
    public float y;
    public float winkel;

    /** @return null, wenn das Ziel im Bild liegt */
    static RegiePfeil fuer(int farbe, String nummer, boolean aktiv, double zx, double zy, double zz,
        RegieKamera kamera, float breite, float hoehe, float k, float[] p)
    {
        boolean vorn = kamera.projizieren(zx, zy, zz, breite, hoehe, p);
        float rand = 64F * k;

        if (vorn && p[0] > rand && p[0] < breite - rand && p[1] > rand && p[1] < hoehe - rand)
        {
            return null;
        }

        float mx = breite / 2F;
        float my = hoehe / 2F;
        float dx = p[0] - mx;
        float dy = p[1] - my;

        if (!vorn)
        {
            dx = -dx;
            dy = -dy;

            if (Math.abs(dx) < 1F && Math.abs(dy) < 1F)
            {
                dy = 1F;
            }
        }

        float faktor = Math.min((breite / 2F - rand) / Math.max(0.001F, Math.abs(dx)),
            (hoehe / 2F - rand) / Math.max(0.001F, Math.abs(dy)));
        RegiePfeil pfeil = new RegiePfeil();

        pfeil.farbe = farbe;
        pfeil.nummer = nummer;
        pfeil.aktiv = aktiv;
        pfeil.x = mx + dx * faktor;
        pfeil.y = my + dy * faktor;
        pfeil.winkel = (float) (Math.atan2(dy, dx) + Math.PI / 2D);

        return pfeil;
    }
}
