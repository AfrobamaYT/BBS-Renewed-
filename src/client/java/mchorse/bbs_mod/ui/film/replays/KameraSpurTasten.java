package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.film.UIClips;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.keys.Keybind;
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

    /**
     * Hängt den Horcher als LETZTES Kind ein — dort wird er zuerst gefragt.
     *
     * <p>⛔ Warum nicht einfach {@code subKeyPressed} im Replay-Editor überschreiben: das läuft
     * NACH den Kindern, und eines davon verbraucht den Tastendruck. Gemessen 2026-07-29: beim
     * Drücken kam gar nichts an, nur beim Loslassen — und ein losgelassener Knopf löst
     * grundsätzlich kein Kürzel aus. {@code UIElement#childrenKeyPressed} läuft die Kinder
     * RÜCKWÄRTS durch, das zuletzt hinzugefügte hat also den ersten Zugriff.</p>
     *
     * <p>⚠ „Letztes Kind" heißt: der Aufruf muss NACH {@code bringBarToFront()} stehen. Stand er
     * davor, landete der Horcher auf Platz 2 von 6 — und dasselbe Symptom war zurück, der
     * Tastendruck erreichte ihn erst beim Loslassen (gemessen, docs/REIBUNGEN.md R12).</p>
     *
     * <p>Damit der Replay-Editor trotzdem seine eigenen Kürzel behält, greift der Horcher nur,
     * solange ein Kamera-Clip gewählt ist — genau die Bedingung, die der User genannt hat
     * („wenn man ein camera clip wie keyframe clip selected dann …"). Ohne gewählten Clip
     * verhält sich der Editor wie zuvor.</p>
     */
    public static void anhaengen(UIElement editor, UIFilmPanel panel)
    {
        /* Die Spurliste wird bei jedem Replay-Wechsel neu gebaut, und der Aufruf sitzt darin.
         * Ohne dieses Aufräumen sammeln sich Horcher an — gemessen lagen nach drei Wechseln
         * drei Stück im Baum. Sie stören nicht sichtbar, aber jeder bekommt jeden Tastendruck. */
        for (Object kind : new java.util.ArrayList<>(editor.getChildren()))
        {
            if (kind instanceof UIElement && kind.getClass().getEnclosingClass() == KameraSpurTasten.class)
            {
                ((UIElement) kind).removeFromParent();
            }
        }

        UIElement horcher = new UIElement()
        {
            @Override
            protected boolean subKeyPressed(UIContext context)
            {
                return weiterreichen(panel, context);
            }
        };

        horcher.keys().register(Keys.FORMS_EDIT, () -> panel.showPanel(panel.cameraEditor))
            .category(UIClips.KEYS_CATEGORY)
            .active(() -> bereit(panel));

        editor.add(horcher);
    }

    /**
     * Die Kürzel der Kamera-Zeitleiste hier feuern lassen.
     *
     * <p>⛔ NICHT über {@code keys().check(...)} — das überspringt jedes Kürzel, dessen
     * {@code isActive()} false ist, und genau das ist hier der Fall: die Clip-Kürzel hängen an
     * {@code canUseKeybinds} der Kamera-Leiste, also an DEREN Sichtbarkeit. Im Replay-Editor
     * ist sie unsichtbar, damit wäre kein einziges Kürzel je aktiv. Zwei Anläufe sind daran
     * gescheitert, bevor es gemessen war (docs/REIBUNGEN.md R12).</p>
     *
     * <p>Die Bedingung ersetzen wir durch unsere eigene: es gilt, solange ein Kamera-Clip
     * gewählt ist. Deshalb wird {@link Keybind#feuern} benutzt, das {@code isActive()} bewusst
     * übergeht — die Entscheidung ist schon in {@link #bereit} gefallen.</p>
     *
     * <p>⚠ „Gewählt" muss dabei die AUSWAHL von {@code UIClips} meinen, nicht bloß den
     * angezeigten Clip: die Kürzel arbeiten sämtlich über {@code getClipsFromSelection()}.
     * Deshalb wählt {@link KameraSpuren#klick} über {@code clips.pickClip} aus.</p>
     */
    public static boolean weiterreichen(UIFilmPanel panel, UIContext context)
    {
        if (!bereit(panel))
        {
            return false;
        }

        for (Keybind keybind : panel.cameraEditor.clips.keys().keybinds)
        {
            if (keybind.feuern(context.getKeyCode(), context.getKeyAction(), true))
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Nur mit gewähltem Kamera-Clip mischt sich die Kamera in die Tastatur ein.
     *
     * <p>⛔ „Gewählt" heißt die AUSWAHL der Zeitleiste, NICHT der bloß angezeigte Clip —
     * genau wie der Kommentar an {@link #weiterreichen} es verlangt. Die alte Fassung
     * prüfte {@code getClip() != null}, und ein Panel zeigt fast immer einen Clip:
     * damit fraß {@code clip_duration} (Left-Hand: G) den Grab des POSE-Editors, obwohl
     * der Nutzer nie einen Kamera-Clip angefasst hatte (Akte 06, Symptom 3 — „G
     * funktioniert nicht"). Mit leerer Auswahl gehört die Tastatur wieder dem Editor.</p>
     */
    private static boolean bereit(UIFilmPanel panel)
    {
        return an() && panel != null && panel.cameraEditor != null
            && !panel.cameraEditor.clips.getSelection().isEmpty();
    }

}
