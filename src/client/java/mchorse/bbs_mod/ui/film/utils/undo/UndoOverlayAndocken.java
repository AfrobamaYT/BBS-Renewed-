package mchorse.bbs_mod.ui.film.utils.undo;

import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.dock.DropZonen;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.undo.UndoManager;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Baut das Undo-History-Overlay des FILM-Editors samt Andock-Regler zusammen.
 *
 * <p>Aus {@code UIFilmPanel} herausgeloest (die Datei darf nur schrumpfen); das Panel
 * reicht seine privaten Zutaten als Funktionen herein. Der Regler merkt Andock-Wuensche
 * nur VOR ({@code wunsch}) — nie sofort umbauen, der Editor arbeitet den Wunsch am
 * Anfang seines naechsten Frames ab (CME-Klasse dieser Codebasis).</p>
 */
public final class UndoOverlayAndocken
{
    private UndoOverlayAndocken()
    {}

    /**
     * @param zielSucher  (mx, my) → Dock-Ziel oder null; bereits Lock-gefiltert
     * @param panelFlaeche panelId → Bildschirmflaeche, fuer die Drop-Vorschau
     * @param wunsch      nimmt den vorgemerkten Andock-Wunsch entgegen
     * @param x           Titel-Zielposition, {@code Integer.MIN_VALUE} = zentriert
     */
    public static void oeffnen(UIContext context, UndoManager<ValueGroup> manager,
        Supplier<ValueGroup> data, BiFunction<Integer, Integer, DropZonen.Ziel> zielSucher,
        Function<String, Area> panelFlaeche, Consumer<DropZonen.Ziel> wunsch, int x, int y)
    {
        UIUndoHistoryOverlay overlay = new UIUndoHistoryOverlay(
            UIKeys.FILM_HISTORY_TITLE, manager, data, null);

        overlay.andockRegler(new AndockRegler()
        {
            @Override
            public boolean vorschauZeichnen(UIContext ctx, int mouseX, int mouseY)
            {
                DropZonen.Ziel ziel = zielSucher.apply(mouseX, mouseY);

                if (ziel == null)
                {
                    return false;
                }

                DropZonen.zeichnen(ctx, panelFlaeche.apply(ziel.panelId), ziel.zone);

                return true;
            }

            @Override
            public boolean zugEnde(int mouseX, int mouseY)
            {
                DropZonen.Ziel ziel = zielSucher.apply(mouseX, mouseY);

                if (ziel == null)
                {
                    return false;
                }

                wunsch.accept(ziel);

                return true;
            }
        });

        UIOverlay.addOverlay(context, overlay, 200, 0.6F);

        if (x != Integer.MIN_VALUE)
        {
            overlay.getParent().resize();
            overlay.titelZuPosition(x, y);
        }
    }
}
