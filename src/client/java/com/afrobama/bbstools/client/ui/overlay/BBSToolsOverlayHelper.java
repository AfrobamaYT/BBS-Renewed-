// Coded by Afrobama/Claude — UI Overhaul Phase 10 cleanup: shared overlay boilerplate
package com.afrobama.bbstools.client.ui.overlay;

import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import java.util.List;

/**
 * Single source of truth for "open a panel as an overlay with no
 * BBS-style dimmer behind it". Phase 9 and Phase 10 each copied 30+
 * lines of `UIOverlay.addOverlay + UIBaseMenu{canHideHUD/canPause}`
 * boilerplate; Phase 11 would have copied it a third time. Extracted
 * here as part of the M2 review item.
 *
 * <p>Callers still own the panel construction + close-callback wiring;
 * this helper only takes care of attaching to a no-pause/no-hide
 * menu and adding the overlay at the chosen geometry.</p>
 */
public final class BBSToolsOverlayHelper
{
    private BBSToolsOverlayHelper() {}

    /** Open the panel in a fresh transparent menu. {@code width} is in
     *  pixels; {@code heightPct} is the fraction of screen height to
     *  reserve (0.35F-0.6F is the existing convention). */
    public static void open(UIOverlayPanel panel, int width, float heightPct)
    {
        panel.onClose((event) -> Minecraft.getInstance().gui.setScreen(null));

        UIScreen.open(new UIBaseMenu()
        {
            @Override
            public boolean canHideHUD() { return false; }

            @Override
            public boolean canPause() { return false; }

            @Override
            public void onOpen(UIBaseMenu oldMenu)
            {
                super.onOpen(oldMenu);
                UIOverlay.addOverlay(this.context, panel, width, heightPct).noBackground();
            }
        });
    }

    /** Generic lookup: returns the first instance of {@code cls} on
     *  the active UIScreen's root, or null. Replaces the per-class
     *  {@code getOpened()} copies in each overlay. */
    public static <T extends UIOverlayPanel> T getOpened(Class<T> cls)
    {
        Screen currentScreen = Minecraft.getInstance().gui.screen();
        if (!(currentScreen instanceof UIScreen uiScreen)) return null;
        List<T> panels = uiScreen.getMenu().getRoot().getChildren(cls);
        return panels.isEmpty() ? null : panels.get(0);
    }

    public static boolean isOpened(Class<? extends UIOverlayPanel> cls)
    {
        return getOpened(cls) != null;
    }

}
