// Coded by Afrobama/Claude — BBS Renewed Camera Preview Phase 3 perf
package com.afrobama.bbstools.client.campreview;

import net.minecraft.client.Minecraft;

/**
 * Per-frame skip-decision helpers for camera previews. Public fork has
 * a wide hardware range so we early-return aggressively when there's
 * nobody looking at the previews. The cheapest preview is the one we
 * never render.
 *
 * <p>All toggles default to "saver mode" — user can dial them off via
 * {@code /bbs-renewed cam-preview perf <flag>} (planned). Counters are
 * exposed for {@code /bbs-renewed report}.</p>
 */
public final class CameraPreviewPerf
{
    /** Master kill-switch. When false the pre-pass mixin returns
     *  immediately — useful for verifying whether the camera-preview
     *  pre-pass is responsible for a perf regression. Tile UI still
     *  draws; framebuffers just don't get refreshed. */
    public static boolean ENABLED = true;

    /** Skip preview rendering entirely when dashboard is closed. Saves
     *  100% of preview cost during in-world play. */
    public static boolean SKIP_WHEN_DASHBOARD_CLOSED = true;

    /** Skip preview rendering when the window is offscreen / minimized
     *  (window outside the visible area). Saves the framebuffer fill +
     *  camera-swap when the user can't see it. */
    public static boolean SKIP_WHEN_OFFSCREEN = true;

    /** Render every Nth frame. Phase 15 v3 dropped the world-render
     *  pass entirely (grid + replays only) so per-frame cost is now
     *  &lt; 0.5 ms/camera and stride=1 (every frame) is affordable.
     *  User can dial up (12, 30) if many cameras are active and they
     *  want to save more. */
    public static int FRAME_SKIP_STRIDE = 1;

    /** Internal frame counter for stride logic. */
    private static long FRAME_COUNTER;

    /* Counters surfaced in /bbs-renewed report. */
    public static long previewsRendered;
    public static long previewsSkippedDashboard;
    public static long previewsSkippedOffscreen;
    public static long previewsSkippedStride;
    public static long previewsSkippedShadowPass;

    private CameraPreviewPerf() {}

    /** Whole-frame gate: if true, the LAST hook returns immediately for
     *  all previews. */
    public static boolean shouldSkipFrame()
    {
        if (!ENABLED) return true;

        FRAME_COUNTER++;

        if (FRAME_SKIP_STRIDE > 1 && (FRAME_COUNTER % FRAME_SKIP_STRIDE) != 0)
        {
            previewsSkippedStride++;
            return true;
        }

        if (SKIP_WHEN_DASHBOARD_CLOSED)
        {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && !(mc.gui.screen() instanceof mchorse.bbs_mod.ui.framework.UIScreen))
            {
                previewsSkippedDashboard++;
                return true;
            }
        }

        return false;
    }

    /** Per-preview gate: cheap checks for "is this window even visible". */
    public static boolean shouldSkipPreview(CameraPreview p)
    {
        if (SKIP_WHEN_OFFSCREEN)
        {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getWindow() != null)
            {
                int sw = mc.getWindow().getGuiScaledWidth();
                int sh = mc.getWindow().getGuiScaledHeight();
                if (p.windowX + p.windowW < 0 || p.windowY + p.windowH < 0
                    || p.windowX > sw || p.windowY > sh)
                {
                    previewsSkippedOffscreen++;
                    return true;
                }
            }
        }

        previewsRendered++;
        return false;
    }

    public static void resetCounters()
    {
        previewsRendered = 0;
        previewsSkippedDashboard = 0;
        previewsSkippedOffscreen = 0;
        previewsSkippedStride = 0;
        previewsSkippedShadowPass = 0;
    }

    public static String dump()
    {
        return String.format(
            "render=%d skip_dash=%d skip_offscreen=%d skip_stride=%d skip_shadow=%d  (stride=%d, skip_closed=%s, skip_off=%s)",
            previewsRendered, previewsSkippedDashboard, previewsSkippedOffscreen,
            previewsSkippedStride, previewsSkippedShadowPass,
            FRAME_SKIP_STRIDE, SKIP_WHEN_DASHBOARD_CLOSED, SKIP_WHEN_OFFSCREEN);
    }

}
