// Coded by Afrobama/Claude — zweites Aufnahmefeld je Zeile im Keybind-Editor
package com.afrobama.bbstools.client.keybinds;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.elements.input.UIKeybind;
import mchorse.bbs_mod.ui.utils.keys.KeyCombo;
import mchorse.bbs_mod.ui.utils.keys.Zweitbelegungen;

import com.afrobama.bbstools.client.ui.undo.BBSToolsUndoEntry;
import com.afrobama.bbstools.client.ui.undo.BBSToolsUndoStack;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Das ZWEITE Aufnahmefeld einer Zeile im {@link UIKeybindEditor} — die alternative Belegung
 * derselben Aktion.
 *
 * <p>Eigene Datei, weil {@code UIKeybindEditor} mit 342 Zeilen schon an der Grenze steht und
 * der Monolith-Waechter sonst zu Recht blockt. Der Editor holt sich hier ein fertiges Element
 * und haengt es in die Zeile — mehr aendert sich dort nicht.</p>
 *
 * <p><b>Warum das Feld immer da ist, statt hinter einem „+"-Knopf.</b> Die Aktion hat kein
 * eigenes Bedienelement zum Loeschen noetig: {@link UIKeybind} raeumt beim Aufnehmen auf ESC die
 * Tastenliste und meldet eine LEERE combo — genau die Geste, mit der man in diesem Editor schon
 * heute eine Erstbelegung abwirft. Ein leeres Feld heisst also „keine Zweitbelegung", und die
 * Geste zum Entfernen ist die, die der Nutzer hier ohnehin kennt.</p>
 *
 * <p><b>Eine leere Zweitbelegung ist ueberall unschaedlich</b> (nachgelesen, nicht angenommen):
 * {@code KeyCombo.getMainKey()} liefert dann -1, was keine Tastatur-Taste je ist;
 * {@code Keybind.checkMouse} weist leere combos vorab ab (sonst traefe die -1 den Rechtsklick);
 * {@code Keybind.istBeschattet} ueberspringt kuerzere combos; {@code isHeld()} ist auf einer
 * leeren Liste false. Deshalb speichern wir sie gar nicht erst.</p>
 */
public final class UIZweitbelegungFeld
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/keybind-zweit");

    private UIZweitbelegungFeld()
    {
    }

    /**
     * Baut das zweite Aufnahmefeld fuer diese Aktion.
     *
     * @param primaer      die Aktion, deren zweite Belegung bearbeitet wird
     * @param nachAenderung wird nach jeder Aenderung gerufen; der Editor baut damit die Liste neu,
     *                      damit die Konflikt-Markierung beider betroffener Zeilen stimmt
     * @return das Feld, oder {@code null} wenn das Feature ausgeschaltet ist — dann sieht die
     *         Zeile exakt so aus wie vorher, ohne leeren Platzhalter
     */
    public static UIKeybind bauen(KeyCombo primaer, Runnable nachAenderung)
    {
        if (!Zweitbelegungen.aktiv() || primaer == null || primaer.id == null || primaer.id.isEmpty())
        {
            return null;
        }

        UIKeybind feld = new UIKeybind((neu) -> uebernehmen(primaer, neu, nachAenderung));

        /* mouse() schaltet das Aufnehmen von Maustasten frei — ohne den Aufruf ignoriert
         * UIKeybind jeden Klick, der nicht die Aufnahme startet. Genau daran scheiterte
         * Aufgabe #6, und der ganze Sinn dieses Features ist fuer den Nutzer „linke Hand auf
         * der Tastatur, rechte auf der Maus": ohne mouse() waere die zweite Belegung nie eine
         * Maustaste, also nutzlos.
         *
         * escape() bleibt BEWUSST ungesetzt — gleiche Begruendung wie beim ersten Feld: ESC
         * bricht die Aufnahme ab und raeumt die Liste, und genau das ist hier zusaetzlich die
         * Geste zum Entfernen der Zweitbelegung. */
        feld.mouse();
        feld.w(100);

        KeyCombo zweit = Zweitbelegungen.hinterlegt(primaer);

        if (zweit != null)
        {
            feld.setKeyCombo(zweit);
        }

        feld.tooltip(IKey.constant(beschriftung(zweit)));

        return feld;
    }

    /** Tooltip: was das Feld ist, wie man es leert, und mit wem es kollidiert. */
    private static String beschriftung(KeyCombo zweit)
    {
        StringBuilder text = new StringBuilder("§7Second binding for this action — either one triggers it.\n§8Press ESC while recording to remove it.");

        if (zweit == null || zweit.keys.isEmpty())
        {
            return text.toString();
        }

        List<KeyCombo> konflikte = KeybindOverrides.findConflicts(zweit);

        if (konflikte.isEmpty())
        {
            return text.toString();
        }

        text.append("\n§cKey conflict with: §f");

        for (int i = 0; i < konflikte.size(); i++)
        {
            if (i > 0)
            {
                text.append(", ");
            }

            KeyCombo andere = konflikte.get(i);

            text.append(andere.label == null ? andere.id : andere.label.get());
        }

        return text.toString();
    }

    /**
     * Uebernimmt die aufgenommene Tastenfolge als Zweitbelegung, schreibt sie ins aktive Preset
     * und legt einen Undo-Schritt ab.
     *
     * <p>⛔ Die aufgenommene combo gehoert dem Widget und wird beim naechsten Aufnehmen
     * weiterbenutzt — es wird KOPIERT, nie die Referenz gehalten.</p>
     */
    private static void uebernehmen(KeyCombo primaer, KeyCombo aufgenommen, Runnable nachAenderung)
    {
        List<Integer> vorher = abzug(Zweitbelegungen.hinterlegt(primaer));
        List<Integer> nachher = abzug(aufgenommen);

        if (vorher.equals(nachher))
        {
            return;
        }

        anwenden(primaer, nachher);

        BBSToolsUndoStack.push(new BBSToolsUndoEntry(
            "Second binding " + name(primaer),
            () -> anwenden(primaer, vorher),
            () -> anwenden(primaer, nachher)));

        if (nachAenderung != null)
        {
            nachAenderung.run();
        }
    }

    /**
     * Setzt die Zweitbelegung im laufenden System UND auf der Platte.
     *
     * <p>Beides zusammen, weil der Undo-Weg dieselbe Wirkung braucht wie das Bearbeiten —
     * ein Undo, das nur den Speicher zuruecksetzt, waere nach dem naechsten Start wieder weg.</p>
     */
    private static void anwenden(KeyCombo primaer, List<Integer> tasten)
    {
        try
        {
            Zweitbelegungen.setzen(primaer, tasten);
            KeybindOverrides.onZweitbelegungEdited(primaer);
        }
        catch (Throwable t)
        {
            LOG.warn("Zweitbelegung fuer '{}' konnte nicht gesetzt werden",
                primaer == null ? "?" : primaer.id, t);
        }
    }

    private static List<Integer> abzug(KeyCombo combo)
    {
        return combo == null ? new ArrayList<>() : new ArrayList<>(combo.keys);
    }

    private static String name(KeyCombo combo)
    {
        if (combo == null)
        {
            return "?";
        }

        if (combo.label != null)
        {
            try
            {
                return combo.label.get();
            }
            catch (Throwable t)
            {
                LOG.warn("combo.label resolve failed", t);
            }
        }

        return combo.id == null ? "?" : combo.id;
    }
}
