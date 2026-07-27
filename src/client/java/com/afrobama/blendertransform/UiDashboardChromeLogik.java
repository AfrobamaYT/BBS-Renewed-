package com.afrobama.blendertransform;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import com.afrobama.bbstools.client.ui.chrome.BBSToolsBrandHeader;
import com.afrobama.bbstools.client.ui.chrome.BBSToolsPageNavigator;
import com.afrobama.bbstools.client.ui.chrome.BBSToolsStatusBar;
import com.afrobama.bbstools.client.ui.theme.BBSToolsTheme;

import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanels;
import mchorse.bbs_mod.ui.framework.elements.UIElement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logik hinter {@code UIDashboardChromeMixin} — siehe
 * {@link MixinBruecke} für das muster.
 *
 * <p>{@code panels} ist ein {@code @Shadow @Final}-feld der zielklasse — gelesen über
 * {@link MixinBruecke#feldLesen}, weil der kernel den typ {@code UIDashboard} nicht in
 * einer @Shadow-annotation kennt. {@code main} ist ein GEERBTES feld von
 * {@code UIBaseMenu} (public/protected) und wird deshalb direkt durchgereicht statt
 * über feldLesen — es ist kein @Shadow-feld, sondern normaler vererbter zustand.</p>
 */
public final class UiDashboardChromeLogik
{
    private UiDashboardChromeLogik() {}

    private static final Logger LOG = LoggerFactory.getLogger("bbstools/dashboard-chrome");

    /**
     * @param self  die UIDashboard-instanz (als Object, um kernel-typ-freiheit zu wahren)
     * @param main  {@code this.main} (UIElement), geerbtes feld von UIBaseMenu
     */
    public static void injectChrome(Object self, UIElement main)
    {
        try
        {
            BBSToolsBrandHeader header = new BBSToolsBrandHeader();
            header.relative(main).x(0).y(0).w(1F).h(BBSToolsBrandHeader.HEIGHT);

            BBSToolsPageNavigator navigator = new BBSToolsPageNavigator();
            navigator.relative(main)
                .x(0)
                .y(1F, -(BBSToolsPageNavigator.HEIGHT + BBSToolsStatusBar.HEIGHT))
                .w(1F)
                .h(BBSToolsPageNavigator.HEIGHT);

            /* Seed the page navigator with our standard set. Click handlers
             * are wired in Phase 13b after we can reliably look up the
             * registered UIDashboardPanel instances from this dashboard. */
            navigator
                .addPage("films", "Films", BBSToolsTheme.ACCENT_PRIMARY)
                .addPage("models", "Models", BBSToolsTheme.ACCENT_PRIMARY)
                .addPage("replays", "Replays", BBSToolsTheme.ACCENT_CAMERA)
                .addPage("particles", "Particles", BBSToolsTheme.ACCENT_PRIMARY)
                .addPage("audio", "Audio", BBSToolsTheme.ACCENT_FREEFLY)
                .addPage("texture", "Texture", BBSToolsTheme.ACCENT_PAINT)
                .addPage("scene", "Scene", BBSToolsTheme.ACCENT_OK)
                .setActive("films");

            BBSToolsStatusBar status = new BBSToolsStatusBar();
            status.relative(main)
                .x(0)
                .y(1F, -BBSToolsStatusBar.HEIGHT)
                .w(1F)
                .h(BBSToolsStatusBar.HEIGHT);

            /* Make room for our chrome by carving the panels area down to
             *  the middle band: 24 px top reserved, 58 px bottom reserved
             *  (36 navigator + 22 status). */
            int topReserved = BBSToolsBrandHeader.HEIGHT;
            int bottomReserved = BBSToolsPageNavigator.HEIGHT + BBSToolsStatusBar.HEIGHT;

            Object panelsObj = MixinBruecke.feldLesen(self, "panels");

            if (panelsObj instanceof UIDashboardPanels panels)
            {
                panels.resetFlex()
                    .relative(main)
                    .x(0).y(topReserved)
                    .w(1F).h(1F, -(topReserved + bottomReserved));
            }

            main.add(header, navigator, status);

            LOG.info("Dashboard chrome injected — header {} px / navigator {} px / status {} px",
                BBSToolsBrandHeader.HEIGHT, BBSToolsPageNavigator.HEIGHT, BBSToolsStatusBar.HEIGHT);
        }
        catch (Throwable t)
        {
            LOG.warn("Failed to inject dashboard chrome — falling back to vanilla BBS layout", t);
        }
    }
}
