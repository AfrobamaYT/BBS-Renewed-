package mchorse.bbs_mod.graphics;

/** Last size NativeWorldTargets resized the level renderer to. A class of its own so the
 * resize guard swaps into a running game without adding fields to a loaded class. */
public final class NativeWorldTargetsGroesse
{
    private static Object renderer;
    private static int breite = -1;
    private static int hoehe = -1;

    public static boolean gleich(Object renderer, int breite, int hoehe)
    {
        return NativeWorldTargetsGroesse.renderer == renderer && NativeWorldTargetsGroesse.breite == breite && NativeWorldTargetsGroesse.hoehe == hoehe;
    }

    public static void merken(Object renderer, int breite, int hoehe)
    {
        NativeWorldTargetsGroesse.renderer = renderer;
        NativeWorldTargetsGroesse.breite = breite;
        NativeWorldTargetsGroesse.hoehe = hoehe;
    }

    private NativeWorldTargetsGroesse()
    {}
}
