// Coded by Afrobama/Claude — UI Overhaul Phase 8: fork branding identity
package com.afrobama.bbstools.client.ui.theme;

/**
 * Placeholder identity strings for the public-fork release. Tweak
 * these once the final fork name + version + tagline are decided
 * with the user; everywhere else in the codebase that wants to show
 * branding (launcher tab, README header, console boot message)
 * should pull from here so renaming the fork is a one-line change.
 *
 * <p>Defaults match BBSTools-Fabric internal name + Afrobama
 * authorship.</p>
 */
public final class BBSToolsBrand
{
    /** Display name shown in dashboard tab + window title. */
    public static final String NAME = "BBSTools";

    /** Optional subtitle / tagline shown next to the name in the
     *  launcher hauptseite header. */
    public static final String TAGLINE = "cinematic creation, refined";

    /** Mod version — keep in sync with fabric.mod.json's "version". */
    public static final String VERSION = "0.1.0-dev";

    /** Author / maintainer. */
    public static final String AUTHOR = "Afrobama";

    /** Accent palette descriptor for the brand (e.g. on splash
     *  screens). Mirrors the {@link BBSToolsTheme} primary. */
    public static final int BRAND_ACCENT = BBSToolsTheme.ACCENT_PRIMARY;

    private BBSToolsBrand() {}

}
