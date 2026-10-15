package mchorse.bbs_mod.client;

/**
 * vor261: der HUD liegt beim Export schon im gelesenen Bild (erst 26.x zeichnet die GUI nach dem
 * Bild ins Fenster, siehe die 26.x-Fassung); ein eigener GUI-Durchgang ist hier nicht noetig.
 */
public final class ExportHud
{
    private ExportHud()
    {}

    public static boolean aktiv()
    {
        return false;
    }

    public static void zeichnen()
    {}
}
