package mchorse.bbs_mod.forms.renderers.utils;


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
