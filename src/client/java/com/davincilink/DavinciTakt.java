// Coded by Afrobama/Claude — BBS <-> DaVinci Resolve live link (task 109, docs/DAVINCI-LINK-PLAN.md).
package com.davincilink;

/** How often Hyprland lets the hidden Resolve window draw while the tab runs. */
final class DavinciTakt
{
    private DavinciTakt()
    {}

    private static String unfocusedFps;

    /**
     * While the tab shows Resolve, Hyprland drives windows nobody looks at at 60 fps instead of 15.
     * The option is global and reaches only windows with render_unfocused. Other tools set it too
     * (2026-10-12: the demo recorder of another session set 60 while the tab ran), so BBS changes
     * it only when it is below 60 and puts it back only while its own 60 still stands.
     */
    static synchronized void smooth(boolean on)
    {
        if (System.getenv("HYPRLAND_INSTANCE_SIGNATURE") == null)
        {
            return;
        }

        int jetzt = unfocusedFps();

        if (on && unfocusedFps == null && jetzt >= 0 && jetzt < 60)
        {
            unfocusedFps = String.valueOf(jetzt);
            DavinciWindowing.hyprctl("eval", "hl.config({ misc = { render_unfocused_fps = 60 } })");
        }
        else if (!on && unfocusedFps != null)
        {
            if (jetzt == 60)
            {
                DavinciWindowing.hyprctl("eval", "hl.config({ misc = { render_unfocused_fps = " + unfocusedFps + " } })");
            }
            unfocusedFps = null;
        }
    }

    /** Hyprland's misc:render_unfocused_fps, or -1 if it cannot be read. */
    private static int unfocusedFps()
    {
        String[] option = DavinciWindowing.hyprctl("getoption", "misc:render_unfocused_fps").trim().split("\\s+");

        return option.length >= 2 && option[1].matches("\\d+") ? Integer.parseInt(option[1]) : -1;
    }
}
