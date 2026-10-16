// Coded by Afrobama/Claude — BBS <-> DaVinci Resolve live link (task 79, docs/DAVINCI-LINK-PLAN.md).
package com.davincilink;

/**
 * What the Resolve tab knows right now, in a class of its own: new state in a new class can be
 * swapped into the owner's running game (the classes already loaded only change their bodies).
 */
public final class DavinciTabState
{
    /** BBS plays only because Resolve plays (DavinciPlayhead); its sound comes from Resolve then. */
    public static volatile boolean followsResolve;

    /** The tab shows the BBS viewport through the placeholder (set by the overlay each frame). */
    static volatile boolean keyed;

    /** When the tab was last drawn. */
    static volatile long lastShown;

    private DavinciTabState()
    {}

    /** Whether the Resolve tab is on screen right now (drawn within the last frames). */
    public static boolean shownNow()
    {
        return System.currentTimeMillis() - lastShown < 250L;
    }

    /**
     * A chroma key on the placeholder colour inside Resolve's viewer: those pixels become
     * transparent and the live BBS viewport drawn under the picture shows through. Whatever Resolve
     * lays over the placeholder -- a video on a higher track, a title -- stays on top, as in Resolve
     * (owner, 2026-10-15: a video above the camera clip was hidden behind the BBS picture).
     */
    static void keyHoles(byte[] rgba, int width, int[] viewer)
    {
        if (viewer == null)
        {
            return;
        }

        int tolerance = DavinciViewerFinder.KEY_TOLERANCE;

        for (int y = viewer[1]; y < viewer[1] + viewer[3]; y++)
        {
            for (int x = viewer[0], i = (y * width + x) * 4; x < viewer[0] + viewer[2]; x++, i += 4)
            {
                if (Math.abs((rgba[i] & 255) - DavinciViewerFinder.KEY_R) <= tolerance
                    && Math.abs((rgba[i + 1] & 255) - DavinciViewerFinder.KEY_G) <= tolerance
                    && Math.abs((rgba[i + 2] & 255) - DavinciViewerFinder.KEY_B) <= tolerance)
                {
                    rgba[i + 3] = 0;
                }
            }
        }
    }
}
