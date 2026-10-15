package com.afrobama.bbstools.client.ui.optimize;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.icons.Icons;

/**
 * Haengt den "Optimize"-eintrag ins Film-options-menue (drei striche, oben rechts).
 *
 * <p><b>Warum eine eigene datei fuer vier zeilen.</b> {@code UIFilmPanel} ist mit ueber
 * 3300 zeilen einer unserer monolithen; der commit-guard laesst ihn nur noch schrumpfen.
 * Der eintrag gehoert ohnehin zu uns und nicht in fremd-code — so bleibt in
 * {@code fillFilmContextMenu} eine einzige zeile stehen, und alles was den eintrag
 * betrifft (icon, groesse, begruendung) liegt hier.</p>
 *
 * <p><b>Was das menue soll.</b> Es ersetzt das "optimize"-dropdown, das CML oben im
 * dashboard hatte und das beim CML-cleanup wegfiel. Ohne es blieb der alte start-sweep
 * ({@code LegacyRendererDebuggers.flipAllToEnabledExceptUnsafe}) unbremsbar: er schaltete
 * fast jeden debugger an, darunter eingriffe die ins bild greifen. Gemessen 2026-07-27:
 * {@code paused-bone-cache} liess replays flackern, und niemand konnte es abschalten.
 * Seit 2026-07-28 startet nur noch, was in {@code StartPositivliste} genannt ist — das
 * menue bleibt der weg, die render-schalter waehrend der arbeit umzulegen.</p>
 */
public final class OptimizeMenuEintrag
{
    /** Breite in pixeln; hoehe als anteil der bildschirmhoehe. */
    private static final int BREITE = 320;

    /** 0.85 statt 0.6: bei 0.6 waren von 21 schaltern nur 5 sichtbar (gemessen). */
    private static final float HOEHE_ANTEIL = 0.85F;

    private OptimizeMenuEintrag()
    {
    }

    /** Den eintrag ans uebergebene kontextmenue haengen. */
    public static void anhaengen(ContextMenuManager menu, UIContext kontext)
    {
        menu.action(Icons.WRENCH, IKey.constant("Optimize"), () ->
            UIOverlay.addOverlay(kontext, new UIOptimizeOverlayPanel(), BREITE, HOEHE_ANTEIL));
    }
}
