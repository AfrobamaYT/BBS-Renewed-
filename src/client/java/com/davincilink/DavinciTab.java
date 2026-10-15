// Coded by Afrobama/Claude — BBS <-> DaVinci Resolve live link (task 109, docs/DAVINCI-LINK-PLAN.md).
package com.davincilink;

import com.blenderbridge.BBSCompat;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.panels.TopbarReiter;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;

import java.util.List;

/**
 * Hangs the Resolve tab into BBS from outside: a "Resolve" tab in the top bar beside the
 * workspace tabs, a viewer switch beside it while the tab is open, and the overlay over the
 * film editor.
 *
 * <p>In the top bar because 26.3 hides the film panel's own tab row (its {@code tabBar} measured
 * invisible on 2026-10-11; buttons hung there never showed). From outside because UIFilmPanel is a
 * monolith of 3400 lines that may only shrink; its {@code editor} is public. The tab exists while
 * the link is connected or the tab is open ("when the bridge is active", the owner's words).</p>
 */
public final class DavinciTab
{
    private static UIFilmPanel attached;
    private static List<TopbarReiter> tabs = List.of();

    private DavinciTab()
    {}

    /** Called every client tick; attaches once to whichever film panel the dashboard has. */
    static void tick()
    {
        UIDashboard dashboard = BBSCompat.peekDashboard();
        UIFilmPanel panel = dashboard == null ? null : dashboard.getPanel(UIFilmPanel.class);

        if (panel != null && panel != attached && panel.editor != null)
        {
            attach(dashboard, panel);
            attached = panel;
        }
    }

    private static void attach(UIDashboard dashboard, UIFilmPanel panel)
    {
        UIDavinciOverlay overlay = new UIDavinciOverlay(panel);
        TopbarReiter resolve = new TopbarReiter(() -> "Resolve",
            () -> DavinciLinkClient.SERVER.connectionCount() > 0 || overlay.isVisible(), overlay::isVisible,
            () ->
            {
                dashboard.setPanel(panel);
                if (!overlay.isVisible())
                {
                    overlay.toggle();
                }
                else
                {
                    /* Open but empty (a start failed, or Resolve went away): clicking the tab
                     * again tries again -- before, it did nothing (2026-10-12). */
                    overlay.start();
                }
            },
            () ->
            {
                if (overlay.isVisible())
                {
                    overlay.toggle();
                }
            });
        TopbarReiter viewer = new TopbarReiter(() -> overlay.showsBbsViewport() ? "Viewer: BBS" : "Viewer: Resolve",
            overlay::isVisible, () -> false, overlay::switchViewport, () -> {});

        TopbarReiter tutorial = new TopbarReiter(() -> "? Tutorial",
            () -> overlay.isVisible() && mchorse.bbs_mod.ui.utils.Tutorials.link("resolve_tab") != null, () -> false,
            () -> mchorse.bbs_mod.ui.utils.Tutorials.open("resolve_tab"), () -> {});

        TopbarReiter.ALL.removeAll(tabs);
        tabs = List.of(resolve, viewer, tutorial);
        TopbarReiter.ALL.addAll(tabs);

        overlay.relative(panel.editor).w(1F).h(1F);
        panel.editor.add(overlay);
        /* Keys go to the last child first. The panel's own catchers (UIFilmPanelUndoKeys, full size,
         * added after the editor) took Ctrl+Z before Resolve saw it: BBS undid instead (2026-10-12).
         * This one comes last, so while the tab is open Resolve gets every key first. */
        UIElement keys = new UIElement()
        {
            @Override
            protected boolean subKeyPressed(UIContext context)
            {
                return overlay.isVisible() && overlay.subKeyPressed(context);
            }
        };
        panel.add(keys.full(panel));
        panel.resize();
    }
}
