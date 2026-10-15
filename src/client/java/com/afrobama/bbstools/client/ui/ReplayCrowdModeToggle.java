/* Coded by Afrobama/Claude — Tier 1d per-replay crowdMode UI toggle.
 *
 * Installs a "Crowd Mode" checkbox into the BBS Replays overlay panel via
 * the public UIReplaysOverlayPanel.extensions hook (no mixin needed for
 * widget insertion). When toggled, mutates Replay.crowdMode on every
 * selected replay — matching the actor/shadow/etc. multi-edit pattern.
 *
 * A tiny companion mixin (UIReplaysOverlayPanelCrowdSyncMixin) drives the
 * widget's displayed value from setReplay so switching between replays
 * shows the correct state. Static field on this class so the mixin can
 * reach the widget without a per-panel registry.
 */
package com.afrobama.bbstools.client.ui;

import com.afrobama.bbstools.client.util.CrowdModeRegistry;

import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.film.replays.overlays.UIReplaysOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;

import java.util.List;

public final class ReplayCrowdModeToggle
{
    /** Last-installed widget. Static so the setReplay sync mixin can
     *  reach it; gets overwritten each time a new replays panel opens,
     *  which is fine because only one panel exists at a time. */
    public static UIToggle widget;

    private ReplayCrowdModeToggle() {}

    public static void install()
    {
        UIReplaysOverlayPanel.extensions.add((panel) ->
        {
            UIToggle toggle = new UIToggle(IKey.constant("Crowd Mode"), (b) ->
            {
                List<Replay> selected = panel.replays.getSelectedReplays();

                for (Replay r : selected)
                {
                    CrowdModeRegistry.setCrowdMode(r, b.getValue());
                }
            });
            toggle.tooltip(IKey.constant("Visual-only replay: no server actor, GPU-instanced crowd render. For 200+ replay crowd shots."));

            panel.properties.add(toggle);

            /* Cache-progress bar moved into the LEFT replay list (one thin
             * line per row, under each name) — see UIReplayListCacheBarMixin. */

            widget = toggle;
        });
    }

}
