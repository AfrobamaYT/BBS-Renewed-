package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.clips.Clip;

import java.util.List;

/**
 * Die kontextmenue-eintraege, die es nur bei AUSGEWAEHLTEN clips gibt.
 *
 * <p>WARUM EIGENE DATEI: {@link UIClips} liegt mit ueber 2000 zeilen weit ueber der
 * 1500-zeilen-grenze und darf nur noch schrumpfen. Der ganze block ist deshalb hierher
 * gewandert statt um einen eintrag zu wachsen — dort steht jetzt ein aufruf statt vier
 * zeilen.</p>
 *
 * <p>Die aktionen kommen als {@link Runnable} herein, weil die zugehoerigen methoden in
 * UIClips privat sind und es bleiben sollen: dieser typ kennt das MENUE, nicht die
 * timeline.</p>
 */
public final class UIClipSelectionActions
{
    private UIClipSelectionActions()
    {}

    /**
     * @param selected        die aktuell ausgewaehlten clips (leere liste = gar kein eintrag)
     * @param toggleEnabled   {@code UIClips#toggleEnabled} — kippt JEDEN ausgewaehlten clip
     *                        einzeln, exakt wie der keybind (Keys.CLIP_ENABLE, heute J).
     *                        Deshalb traegt der eintrag auch dessen beschriftung und keine
     *                        zustands-behauptung: bei gemischter auswahl gaebe es kein
     *                        ehrliches "alle an" oder "alle aus".
     */
    public static void add(ContextMenuManager menu, List<Clip> selected,
                           Runnable cut, Runnable shift, Runnable shiftDuration, Runnable toggleEnabled)
    {
        if (selected.isEmpty())
        {
            return;
        }

        menu.action(Icons.CUT, UIKeys.CAMERA_TIMELINE_CONTEXT_CUT, cut);
        menu.action(Icons.MOVE_TO, UIKeys.CAMERA_TIMELINE_CONTEXT_SHIFT, shift);
        menu.action(Icons.SHIFT_TO, UIKeys.CAMERA_TIMELINE_CONTEXT_SHIFT_DURATION, shiftDuration);

        /* Bewusst Icons.DISABLED: mit genau diesem icon zeichnet UIClipRenderer einen
         * ausgeschalteten clip in der timeline — der wiedererkennungswert ist der punkt.
         *
         * WARUM dieser eintrag ueberhaupt: das deaktivieren war bisher nur ueber den keybind
         * und den schalter in den clip-eigenschaften erreichbar. Beides findet nur, wer das
         * feature schon kennt; das kontextmenue ist die stelle, an der man nach clip-aktionen
         * sucht. */
        menu.action(Icons.DISABLED, UIKeys.CAMERA_TIMELINE_KEYS_ENABLED, toggleEnabled);
    }
}
