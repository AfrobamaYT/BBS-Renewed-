package com.afrobama.blendertransform;

import com.afrobama.bbstools.client.ui.ReplayCrowdModeToggle;
import com.afrobama.bbstools.client.util.CrowdModeRegistry;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;

/**
 * Logik hinter {@code UIReplaysOverlayPanelCrowdSyncMixin}, herausgeloest damit sie hotswapbar
 * ist.
 *
 * <p>Haelt den Crowd-Modus-Schalter im Replay-Panel mit dem gewaehlten Replay im Gleichschritt.
 * Ohne das zeigt der Schalter den Stand des zuletzt gewaehlten Replays — und wer ihn dann
 * umlegt, aendert den Modus des FALSCHEN Replays.</p>
 *
 * <p>⚠️ GRUPPEN SIND AUSGENOMMEN. Ein Gruppen-Replay hat keinen eigenen Crowd-Modus; die
 * Abfrage {@code isGroup} ist deshalb kein Beiwerk, sondern verhindert, dass der Schalter
 * einen Wert anzeigt, den es nicht gibt.</p>
 */
public final class CrowdModeSyncLogik
{
    private CrowdModeSyncLogik() {}

    public static void schalterAbgleichen(Replay replay)
    {
        UIToggle schalter = ReplayCrowdModeToggle.widget;

        if (schalter == null || replay == null || replay.isGroup.get())
        {
            return;
        }

        schalter.setValue(CrowdModeRegistry.isCrowdMode(replay));
    }
}
