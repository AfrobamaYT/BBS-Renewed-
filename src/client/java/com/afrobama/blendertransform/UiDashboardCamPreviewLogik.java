package com.afrobama.blendertransform;

import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.framework.elements.UIElement;

import com.afrobama.bbstools.client.mixin.UIBaseMenuAccessor;

/**
 * Logik hinter {@code UIDashboardCamPreviewMixin} — siehe
 * {@link com.afrobama.bbstools.client.blender.MixinBruecke} für das muster.
 *
 * <p>Mountet die kamera-vorschau-overlay-schicht in {@link UIDashboard} bei der konstruktion.
 * Bewusst mit BBS-typen in der signatur (UIDashboard, UIBaseMenuAccessor) — das ist erlaubt,
 * nur KERNEL-typen dürfen nicht über die brücke.</p>
 */
public final class UiDashboardCamPreviewLogik
{
    private UiDashboardCamPreviewLogik() {}

    private static final org.slf4j.Logger LOG =
        org.slf4j.LoggerFactory.getLogger("bbstools/cam-preview/mount");

    /**
     * Phase 15 v7.2: mount UIClipPreviewLayer into UIBaseMenu.main rather than root. The
     * dashboard's main panels live in main, but BBS's popup context menus (offerAdjacent /
     * offerHierarchy) attach via UIContext.menu → root, so when our layer was in root it
     * rendered AFTER the popups and covered them. Putting the layer in main keeps it visible
     * over film panels but BELOW any popup the user spawns from it.
     */
    public static void wireCamPreviewOverlay(UIDashboard self)
    {
        try
        {
            mchorse.bbs_mod.ui.framework.UIBaseMenu menu =
                (mchorse.bbs_mod.ui.framework.UIBaseMenu) (Object) self;
            UIElement attachTarget = menu.main;

            if (attachTarget == null)
            {
                attachTarget = ((UIBaseMenuAccessor) (Object) self).bbstools$getRoot();
            }

            if (attachTarget == null)
            {
                LOG.error("UIDashboard main + root both null — alt-camera preview layer NOT mounted");
                return;
            }

            com.afrobama.bbstools.client.campreview.UIClipPreviewLayer layer =
                new com.afrobama.bbstools.client.campreview.UIClipPreviewLayer();
            attachTarget.add(layer);

            LOG.info("Alt-camera preview layer mounted to UIDashboard.{} (Phase 15 v7.2)",
                attachTarget == menu.main ? "main" : "root");
        }
        catch (Throwable t)
        {
            LOG.error("Alt-camera preview layer mount FAILED: " + t, t);
        }
    }
}
