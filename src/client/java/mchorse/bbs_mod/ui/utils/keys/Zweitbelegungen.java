package mchorse.bbs_mod.ui.utils.keys;

import mchorse.bbs_mod.BBSSettings;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Die ZWEITE Belegung einer Aktion — „Strg+X <b>ODER</b> Mouse4", nie „Strg+X <b>UND</b> Mouse4".
 *
 * <p><b>Warum eine Ebene darueber und nicht ein Eintrag mehr in {@link KeyCombo#keys}.</b>
 * Diese Liste IST der Akkord: jede Taste darin muss gleichzeitig gedrueckt sein
 * ({@link KeyCombo#isHeld()}, {@link Keybind#check}). Wer eine Alternative dort hineinschreibt,
 * baut aus einem ODER ein UND — die Aktion waere danach gar nicht mehr ausloesbar. Deshalb ist
 * die Zweitbelegung ein EIGENES {@link KeyCombo} und dieses Register die Ebene darueber —
 * dieselbe Architektur wie IntelliJ, VS Code und Blender (eine Liste von Bindungen je Aktion,
 * Eintrag 0 wird angezeigt; Recherche in werkbank/features/keybind-zweitbelegung/).</p>
 *
 * <p><b>Zwei Leser, und die Unterscheidung ist keine Feinheit.</b>
 * {@link #zweit(KeyCombo)} / {@link #alle()} sind am Schalter
 * {@link BBSSettings#editorKeybindSecondBinding} GESPERRT und dienen dem Ausloesen und der
 * Anzeige. {@link #hinterlegt(KeyCombo)} / {@link #alleHinterlegten()} sind UNGESPERRT und dienen
 * dem Speichern. ⛔ Wer beim Schreiben den gesperrten Leser nimmt, loescht dem Nutzer beim
 * naechsten Speichern alle Zweitbelegungen, sobald er den Schalter einmal ausmacht.</p>
 *
 * <p><b>Sperren und Reihenfolge.</b> Das Register ist eine {@link ConcurrentHashMap}: der
 * Tastendruck-Pfad liest sperrfrei, Schreiber sind selten (Editor, Preset-Wechsel, Laden).
 * Diese Klasse ruft NIE in {@code KeybindOverrides} zurueck — die Sperrreihenfolge ist damit
 * immer KeybindOverrides → Zweitbelegungen, ein Deadlock ist ausgeschlossen.</p>
 */
public final class Zweitbelegungen
{
    /**
     * Anhang, unter dem die Zweitbelegung in der Preset-Datei liegt: {@code "next"} → {@code "next#2"}.
     *
     * <p>Absichtlich ein zweiter FLACHER Eintrag und kein verschachteltes Objekt. Der Parser in
     * {@code KeybindOverrides.parseFlatJson} ist handgeschrieben und sucht nach
     * {@code "id": [zahlen]}; ein Objekt darin wuerde ihn zerlegen. So liest der BESTEHENDE
     * Parser das neue Format ohne eine Zeile Aenderung, und ein aelterer Build ueberspringt die
     * unbekannte id still ({@code registry.get(id) == null}) statt etwas zu verlieren.</p>
     *
     * <p>Kollisionsfrei, weil {@code #} in keiner BBS-Combo-id vorkommt — ids sind
     * {@code [a-z_.]}-Bezeichner wie {@code clip_duration} oder {@code bbstools.undo_history_open}.</p>
     */
    public static final String SUFFIX = "#2";

    /** primaere combo-id → ihre zweite Belegung. Nur ids, die WIRKLICH eine haben. */
    private static final Map<String, KeyCombo> nachId = new ConcurrentHashMap<>();

    private Zweitbelegungen()
    {}

    /* === Lesen: gesperrt am Schalter (Ausloesen + Anzeige) === */

    /** Ob das Feature ueberhaupt gilt. Aus heisst: nicht ausloesen, nicht anzeigen, nicht
     *  beschatten — aber auch NICHT loeschen. Der Schalter darf keine Daten kosten. */
    public static boolean aktiv()
    {
        return BBSSettings.editorKeybindSecondBinding == null
            || BBSSettings.editorKeybindSecondBinding.get();
    }

    /** Die wirksame Zweitbelegung dieser Aktion, oder {@code null}. Wird bei JEDEM Tastendruck
     *  je Keybind gefragt — deshalb sperrfrei und ohne Zwischenobjekte. */
    public static KeyCombo zweit(KeyCombo primaer)
    {
        return aktiv() ? hinterlegt(primaer) : null;
    }

    /**
     * Alle wirksamen Zweitbelegungen, fuer die Beschattungs-Regel im Tastendruck-Pfad
     * ({@code Keybind.istBeschattet}): sonst feuerte eine fremde Aktion mit blankem {@code E}
     * weiter, waehrend eine Zweitbelegung {@code Shift+E} gedrueckt ist. Bei ausgeschaltetem
     * Schalter leer — Ausloesen und Beschatten haengen so am SELBEN Tor.
     */
    public static Collection<KeyCombo> alle()
    {
        /* Typ ausgeschrieben: die Zeile liegt im Tastendruck-Pfad und soll ohne Nachdenken lesbar sein. */
        return aktiv() ? nachId.values() : Collections.<KeyCombo>emptyList();
    }

    /* === Lesen: ungesperrt (Speichern) === */

    /** Die hinterlegte Zweitbelegung, unabhaengig vom Schalter. NUR fuers Speichern. */
    public static KeyCombo hinterlegt(KeyCombo primaer)
    {
        if (primaer == null || primaer.id == null || primaer.id.isEmpty())
        {
            /* Namenlose combos gibt es wirklich: UIKeybind haelt intern eine mit id "".
             * Ohne diese Sperre wuerden alle namenlosen auf denselben Eintrag zeigen. */
            return null;
        }

        return nachId.get(primaer.id);
    }

    /** Alle hinterlegten Zweitbelegungen, unabhaengig vom Schalter. NUR fuers Speichern. */
    public static Collection<KeyCombo> alleHinterlegten()
    {
        return nachId.values();
    }

    /* === Schreiben === */

    /**
     * Setzt die Zweitbelegung dieser Aktion. Leere Tastenliste = die Zweitbelegung wird entfernt.
     *
     * <p>Die neue combo entsteht ueber den NAMENLOSEN {@link KeyCombo}-Konstruktor — Absicht:
     * {@code KeyComboMixin} haengt an {@code <init>(String,IKey,[I)V} und spiegelt jede so
     * gebaute combo in {@code KeybindOverrides.register}; der namenlose Weg haelt die
     * Zweitbelegung aus jener Registry heraus, sonst bekaeme der Keybind-Editor je Aktion eine
     * zweite Geisterzeile und {@code KeybindSettings} schriebe sie doppelt. Gleiche {@code id}
     * ist gewollt: die Konfliktpruefung ueberspringt gleiche ids, die zwei Belegungen EINER
     * Aktion melden sich nie gegenseitig. {@code categoryKey} braucht die kontextbewusste
     * Konfliktpruefung, {@code repeatable} die REPEAT-Abfrage in {@code Keybind.check}.</p>
     */
    public static KeyCombo setzen(KeyCombo primaer, List<Integer> tasten)
    {
        if (primaer == null || primaer.id == null || primaer.id.isEmpty())
        {
            return null;
        }

        if (tasten == null || tasten.isEmpty())
        {
            entfernen(primaer);

            return null;
        }

        KeyCombo zweit = new KeyCombo(primaer.label);

        zweit.id = primaer.id;
        zweit.category = primaer.category;
        zweit.categoryKey = primaer.categoryKey;
        zweit.repeatable = primaer.repeatable;

        for (int taste : tasten)
        {
            /* Tastencode 0 gibt es nicht — er entsteht aus einem Linksklick beim Aufnehmen
             * (-0 == 0) und traefe danach JEDEN Linksklick. Dieselbe Reinigung wie in
             * KeybindOverrides.applyKeys, damit eine schon verdorbene Datei sich beim naechsten
             * Speichern selbst aufraeumt. */
            if (taste != 0 && !zweit.keys.contains(taste))
            {
                zweit.keys.add(taste);
            }
        }

        if (zweit.keys.isEmpty())
        {
            entfernen(primaer);

            return null;
        }

        nachId.put(primaer.id, zweit);

        return zweit;
    }

    /** Bequemer Weg fuer die Ladepfade, die mit {@code int[]} arbeiten. */
    public static KeyCombo setzen(KeyCombo primaer, int[] tasten)
    {
        if (tasten == null)
        {
            return null;
        }

        List<Integer> liste = new ArrayList<>(tasten.length);

        for (int taste : tasten)
        {
            liste.add(taste);
        }

        return setzen(primaer, liste);
    }

    public static void entfernen(KeyCombo primaer)
    {
        if (primaer == null || primaer.id == null || primaer.id.isEmpty())
        {
            return;
        }

        nachId.remove(primaer.id);
    }

    /**
     * Alle Zweitbelegungen aus dem LAUFENDEN System nehmen. Pflicht beim Preset-Wechsel und
     * beim Zuruecksetzen: sonst blieben die des vorigen Presets stehen und kaemen beim
     * naechsten Speichern wieder auf die Platte. Betrifft nur den Speicher — die Preset-Dateien
     * behalten ihre Eintraege.
     */
    public static void alleEntfernen()
    {
        nachId.clear();
    }

    /* === Ausloesen === */

    /**
     * Ob die Aktion gerade GEHALTEN wird — Erst- oder Zweitbelegung. Fuer Halte-Gesten, die
     * nicht ueber {@code Keybind} laufen, sondern {@link KeyCombo#isHeld()} direkt fragen
     * (im Baum: das Gizmo-Ausblenden in {@code UIBaseMenu}). Ohne diesen Weg waere die
     * Zweitbelegung dort still wirkungslos.
     */
    public static boolean gehalten(KeyCombo primaer)
    {
        if (primaer != null && primaer.isHeld())
        {
            return true;
        }

        /* Ueber den GESPERRTEN Leser: eine Halte-Geste soll bei ausgeschaltetem Feature
         * genauso schweigen wie ein Tastendruck. */
        KeyCombo alternative = zweit(primaer);

        return alternative != null && alternative.isHeld();
    }

    /* === id-Umrechnung fuer die Preset-Datei === */

    public static boolean istZweitId(String id)
    {
        return id != null && id.endsWith(SUFFIX);
    }

    /** {@code "next#2"} → {@code "next"}. Gibt die id unveraendert zurueck, wenn kein Anhang da ist. */
    public static String primaerId(String zweitId)
    {
        return istZweitId(zweitId)
            ? zweitId.substring(0, zweitId.length() - SUFFIX.length())
            : zweitId;
    }

    /** {@code "next"} → {@code "next#2"}. */
    public static String zweitId(String primaerId)
    {
        return primaerId + SUFFIX;
    }
}
