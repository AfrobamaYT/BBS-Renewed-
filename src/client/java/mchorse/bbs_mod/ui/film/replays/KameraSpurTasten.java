package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.film.UIClips;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;

/**
 * Die Tastenkürzel der Kamera-Zeitleiste auch im Replay-Editor wirksam machen.
 *
 * <p>USER-WUNSCH 2026-07-29: „wenn man ein camera clip wie keyframe clip selected dann soll
 * man e drücken können für die camera timeline außerdem sollten dann die camera bedingten
 * keybinds wie add clip at cursor und so weiter im replay editor funktionieren (nur wenn die
 * funktion an ist) default aber an."</p>
 *
 * <p>Statt zwanzig Kürzel nachzubauen, wird der KeybindManager der Kamera-Zeitleiste selbst
 * gefragt. Das hat zwei Vorteile: die Belegungen bleiben ein einziges Mal definiert (der
 * Nutzer stellt sie im Keybind-Editor um, und es gilt hier mit), und jedes künftige Kürzel
 * der Kamera-Leiste wirkt hier automatisch. Die Kürzel bringen ihre eigenen
 * {@code active}-Bedingungen mit — „Clip löschen" ohne Auswahl macht also weiterhin nichts.</p>
 *
 * <p>Reihenfolge: gefragt wird erst, wenn die Kinder des Replay-Editors den Tastendruck nicht
 * schon verbraucht haben. Der Editor behält also Vorrang vor der Kamera.</p>
 */
public final class KameraSpurTasten
{
    private KameraSpurTasten()
    {
    }

    /** ⚠ Neues statisches Feld: nach einem Hotswap null, bis die Instanz neu startet. */
    public static boolean an()
    {
        try
        {
            return BBSSettings.editorCameraClipsInReplays.get();
        }
        catch (Throwable t)
        {
            return true;
        }
    }

    /** E öffnet die Kamera-Zeitleiste mit dem Clip, der gerade gewählt ist. */
    public static void verdrahten(UIElement editor, UIFilmPanel panel)
    {
        editor.keys().register(Keys.FORMS_EDIT, () -> panel.showPanel(panel.cameraEditor))
            .category(UIClips.KEYS_CATEGORY)
            .active(() -> an() && panel.cameraEditor.getClip() != null);
    }

    /** Den Tastendruck an die Kamera-Zeitleiste weiterreichen. */
    public static boolean weiterreichen(UIFilmPanel panel, UIContext context)
    {
        if (!an() || panel == null || panel.cameraEditor == null)
        {
            return false;
        }

        return panel.cameraEditor.clips.keys().check(context, true);
    }
}
