// Coded by Afrobama/Claude — UI Overhaul Phase 13: injects BBSTools chrome into BBS dashboard
package com.afrobama.bbstools.client.mixin;

import com.afrobama.bbstools.client.ui.chrome.BBSToolsBrandHeader;
import com.afrobama.bbstools.client.ui.chrome.BBSToolsPageNavigator;
import com.afrobama.bbstools.client.ui.chrome.BBSToolsStatusBar;
import com.afrobama.bbstools.client.ui.theme.BBSToolsTheme;

import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanels;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.elements.UIElement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Phase 13 — wire BBSTools chrome into the BBS dashboard.
 *
 * <p>Adds three components in the constructor TAIL: brand header at top
 * (24 px), bottom strip with page navigator (36 px) and status bar
 * (22 px). The panels container is reflexively re-relativized to leave
 * the 24 px top + 58 px bottom (36 + 22) free.</p>
 *
 * <p>This mixin is non-destructive: BBS-CML's own taskbar / pinned icon
 * row remains intact and continues to drive panel switching. The new
 * page navigator is currently a visual placeholder; Phase 13b wires
 * its click handlers to {@code UIDashboardPanels.setPanel(...)} once
 * the panels-registry event finishes.</p>
 */
@Mixin(value = UIDashboard.class, remap = false)
public abstract class UIDashboardChromeMixin extends UIBaseMenu
{
    private static final Logger BBSTOOLS$LOG = LoggerFactory.getLogger("bbstools/dashboard-chrome");

    @Shadow
    @Final
    private UIDashboardPanels panels;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void bbstools$injectChrome(CallbackInfo ci)
    {
        /* Logik → com.afrobama.blendertransform.UiDashboardChromeLogik (bruecken-muster).
         * "panels" ist @Shadow @Final und wird dort ueber MixinBruecke.feldLesen gelesen;
         * "main" ist ein geerbtes UIBaseMenu-feld und wird direkt durchgereicht. */
        com.afrobama.bbstools.client.blender.MixinBruecke.rufe(
            "UiDashboardChromeLogik", "injectChrome", this, this.main);
    }

}
