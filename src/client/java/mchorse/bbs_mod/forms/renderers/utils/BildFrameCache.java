package mchorse.bbs_mod.forms.renderers.utils;

/**
 * Ein {@link FormFrameCache}, den mehrere Aufrufe DESSELBEN Bildes teilen.
 *
 * <p>⛔ Warum es das braucht: {@code BaseFilmController.getBoneCompositeMatrix} legt je
 * Aufruf einen frischen Cache an, der damit nie greift — jeder Aufruf baut die
 * Armature-Matrizen komplett neu. Der Control-Rig-Zeichner ruft einmal JE CONTROL auf:
 * beim Rigify-Menschen bis zu 220 volle Aufbauten pro Bild. Gemessen waren 23 von 30
 * Render-Stapelabzuegen unter diesem Pfad, der Editor lief mit 3 FPS.
 *
 * <p>Static-Holder statt neuem Parameter, wie {@code RegionClamp}: die Zielmethode liegt
 * in einer 1552-Zeilen-Klasse, die laut Monolith-Waechter nur noch schrumpfen darf, und
 * ein reiner Body-Edit ist ausserdem hotswapbar.
 *
 * <p>⛔ {@link #frei()} gehoert in ein {@code finally}. Ein liegengebliebener Cache
 * liefert im naechsten Bild veraltete Matrizen — ein Fehler, der wie ein Ruckler aussieht
 * und keiner ist. Nur Render-Thread, darum ein einfaches Feld.
 */
public final class BildFrameCache
{
    private static final FormFrameCache WIEDERVERWENDET = new FormFrameCache();
    private static FormFrameCache aktuell;

    private BildFrameCache()
    {}

    /** Ab hier teilen sich alle Aufrufe einen Cache. Immer mit {@link #frei()} paaren. */
    public static FormFrameCache anlegen()
    {
        WIEDERVERWENDET.reset();
        aktuell = WIEDERVERWENDET;

        return aktuell;
    }

    public static FormFrameCache aktuell()
    {
        return aktuell;
    }

    /**
     * Der geteilte Cache — oder ein frischer, wenn keiner armiert ist.
     *
     * <p>Genau EINE Zeile am Aufrufer: {@code BaseFilmController} ist 1534 Zeilen lang und
     * darf laut Monolith-Waechter nur noch schrumpfen, also traegt diese Klasse die
     * Fallunterscheidung statt der dortigen Methode.
     */
    public static FormFrameCache aktuellOderNeu()
    {
        return aktuell != null ? aktuell : new FormFrameCache();
    }

    public static void frei()
    {
        aktuell = null;
    }
}
