// Coded by Afrobama/Claude — BBS <-> DaVinci Resolve live link (task 109, docs/DAVINCI-LINK-PLAN.md).
package com.davincilink;

import java.util.ArrayDeque;

/**
 * Where Resolve's viewer is: the largest connected patch of the placeholder colour.
 *
 * <p>Camera clips are cut from the link's placeholder, so the viewer shows that colour wherever it
 * shows BBS' film. Thumbnails of the placeholder in the media pool are small and lose against the
 * viewer. The colour arrives slightly shifted by H.264: #2b3440 became #2b3542 (measured).</p>
 */
public final class DavinciViewerFinder
{
    public static final int KEY_R = 43, KEY_G = 53, KEY_B = 66, KEY_TOLERANCE = 8;
    private static final int STEP = 4;

    private DavinciViewerFinder()
    {}

    /** {x, y, w, h} in pixels, or null if no viewer-sized patch is visible. {@code rgba}: 4 bytes a pixel. */
    public static int[] find(byte[] rgb, int width, int height)
    {
        int gw = width / STEP, gh = height / STEP;
        boolean[] key = new boolean[gw * gh];

        for (int gy = 0; gy < gh; gy++)
        {
            for (int gx = 0; gx < gw; gx++)
            {
                int o = ((gy * STEP) * width + gx * STEP) * 4;

                key[gy * gw + gx] = Math.abs((rgb[o] & 255) - KEY_R) <= KEY_TOLERANCE
                    && Math.abs((rgb[o + 1] & 255) - KEY_G) <= KEY_TOLERANCE
                    && Math.abs((rgb[o + 2] & 255) - KEY_B) <= KEY_TOLERANCE;
            }
        }

        int best = 0;
        int[] box = null;
        boolean[] seen = new boolean[key.length];
        ArrayDeque<Integer> todo = new ArrayDeque<>();

        for (int start = 0; start < key.length; start++)
        {
            if (!key[start] || seen[start])
            {
                continue;
            }

            int count = 0, x0 = gw, y0 = gh, x1 = 0, y1 = 0;

            seen[start] = true;
            todo.add(start);
            while (!todo.isEmpty())
            {
                int cell = todo.poll(), x = cell % gw, y = cell / gw;

                count++;
                x0 = Math.min(x0, x); y0 = Math.min(y0, y); x1 = Math.max(x1, x); y1 = Math.max(y1, y);
                for (int n : new int[] {cell - 1, cell + 1, cell - gw, cell + gw})
                {
                    if (n >= 0 && n < key.length && key[n] && !seen[n] && Math.abs(n % gw - x) <= 1)
                    {
                        seen[n] = true;
                        todo.add(n);
                    }
                }
            }
            if (count > best)
            {
                best = count;
                box = new int[] {x0 * STEP, y0 * STEP, (x1 - x0 + 1) * STEP, (y1 - y0 + 1) * STEP};
            }
        }

        /* A real viewer is large: at least 3 % of the screen. Measured at 950x936, the smallest
         * screen the tab gets: the viewer (cut off at the edge, dual-viewer layout) 3.6 %, a project
         * thumbnail in Resolve's project manager -- the placeholder colour too -- 2.3 %; taking that
         * for the viewer swallowed the click meant for Resolve. Resolve's side panels have fixed
         * pixel widths, so on larger screens the viewer's share only grows and a thumbnail's shrinks. */
        return best * STEP * STEP >= width * height / 33 ? box : null;
    }
}
