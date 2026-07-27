package com.afrobama.blendertransform;

import com.afrobama.bbstools.client.dashboard.AnnouncementGate;
import com.afrobama.bbstools.client.dashboard.UIBBSToolsAnnouncementPanel;

import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;

/**
 * Logik des dashboard-announcement-mixins — im kernel-paket, damit sie hotswapbar ist.
 *
 * <p>Herausgelöst aus {@code UIDashboardAnnouncementMixin} (2026-07-26, brücken-umbau). Zeigt
 * das BBSTools-Fabric-announcement-popup einmal pro installation beim ersten dashboard-open.
 * Ersetzt den fork-seitigen UIFnafOverlayPanel-trigger, der früher in UIDashboard lag.</p>
 *
 * <p>⚠ KEINE KERNEL-TYPEN IN DER SIGNATUR — {@link UIDashboard} ist ein BBS-typ.</p>
 */
public final class UIDashboardAnnouncementLogik
{
    private UIDashboardAnnouncementLogik() {}

    public static void zeigeAnnouncement(UIDashboard self)
    {
        if (!AnnouncementGate.shouldShow()) return;

        try
        {
            UIBBSToolsAnnouncementPanel panel = new UIBBSToolsAnnouncementPanel();
            panel.setInitialOffset(40, 40);
            UIOverlay.addOverlay(self.context, panel);
            AnnouncementGate.markShown();
        }
        catch (Throwable ignored)
        {
        }
    }
}
