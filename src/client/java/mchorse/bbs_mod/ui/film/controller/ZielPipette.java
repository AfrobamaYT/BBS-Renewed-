package mchorse.bbs_mod.ui.film.controller;

import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.utils.Pair;

import java.util.function.BiConsumer;

/**
 * One-shot pick of a replay (and the bone under the pointer) in the film viewport, armed by an
 * eyedropper button: while armed, the viewport hovers other replays as with Alt, and the next click
 * on one goes to the button instead of selecting it. Clicking the button again disarms it. From BBS
 * FS fb6795eeb (Wemppy4/bbs-fs, MIT), there inside its FilmStencilPicker.
 */
public final class ZielPipette
{
    private static Object besitzer;
    private static BiConsumer<Integer, Pair<Form, String>> rueckruf;

    private ZielPipette()
    {}

    public static void umschalten(Object owner, BiConsumer<Integer, Pair<Form, String>> callback)
    {
        if (besitzer == owner)
        {
            abbrechen();
            return;
        }

        besitzer = owner;
        rueckruf = callback;
    }

    public static boolean aktiv()
    {
        return besitzer != null;
    }

    public static boolean aktiv(Object owner)
    {
        return besitzer != null && besitzer == owner;
    }

    public static void abbrechen()
    {
        besitzer = null;
        rueckruf = null;
    }

    /**
     * From the viewport's click while armed: the replay whose body holds the picked form, found by
     * its root as BBS FS does. Always takes the click while armed; disarms once it found one.
     */
    static boolean nehmen(io.netty.util.collection.IntObjectMap<mchorse.bbs_mod.forms.entities.IEntity> entities, Pair<Form, String> picked)
    {
        BiConsumer<Integer, Pair<Form, String>> callback = rueckruf;

        if (callback == null) return false;

        Form root = picked == null || picked.a == null ? null : mchorse.bbs_mod.forms.FormUtils.getRoot(picked.a);

        for (var entry : entities.entrySet())
        {
            if (root != null && entry.getValue() != null && entry.getValue().getForm() == root)
            {
                abbrechen();
                callback.accept(entry.getKey(), picked);
                break;
            }
        }

        return true;
    }
}
