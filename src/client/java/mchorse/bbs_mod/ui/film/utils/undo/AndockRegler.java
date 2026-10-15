package mchorse.bbs_mod.ui.film.utils.undo;

import mchorse.bbs_mod.ui.framework.UIContext;

/**
 * Bruecke vom frei schwebenden Undo-History-Overlay ins Dock-System des Film-Editors.
 *
 * <p>Opt-in: NUR der Film-Editor setzt einen Regler; der Modell-Editor nutzt dasselbe
 * Overlay ohne Docks und darf von alledem nichts merken. Beide Methoden sind vom
 * Overlay aus gerufen — der Regler darf dabei NIE sofort Baeume umbauen oder Elemente
 * entfernen (ConcurrentModification im Render-/Event-Lauf); {@link #zugEnde} merkt den
 * Wunsch nur vor, der Editor arbeitet ihn am Anfang seines naechsten Frames ab.</p>
 */
public interface AndockRegler
{
    /**
     * Waehrend des Titel-Zugs, aus dem Render des Overlays: Drop-Vorschau zeichnen,
     * wenn die Maus eine Dock-Zone trifft. Seiteneffektfrei ausser Zeichnen.
     *
     * @return true, wenn eine Zone getroffen und gezeichnet wurde
     */
    boolean vorschauZeichnen(UIContext context, int mouseX, int mouseY);

    /**
     * Der Titel-Zug endete bei (mouseX, mouseY).
     *
     * @return true, wenn das Andocken VORGEMERKT wurde — das Overlay schliesst sich dann
     */
    boolean zugEnde(int mouseX, int mouseY);
}
