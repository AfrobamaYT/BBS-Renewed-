// Coded by Afrobama/Claude — Phase 17: client-only editor-mode helpers
package com.afrobama.bbstools.client.editormode;

import com.afrobama.bbstools.editormode.EditorMode;

import mchorse.bbs_mod.client.BBSRendering;

/**
 * Client-side bridge for {@link EditorMode}. Sits in {@code src/client}
 * so we can freely import {@code mchorse.bbs_mod.client.BBSRendering}
 * (which is client-only and can't be referenced from server-side code).
 *
 * <p>Also drives {@link EditorMode#VIDEO_EXPORT_ACTIVE} each frame by
 * reading {@code BBSRendering.isCustomSize()}, so the editor-mode
 * bypass auto-disables during BBS video export.</p>
 */
public final class EditorModeClient
{
    private EditorModeClient() {}

    /** Phase 18 toggle — when true, the main render's terrain chunks
     *  are skipped whenever an Add-Camera clip is active and the user
     *  is in the BBS dashboard. Result: main preview matches the
     *  alt-tile's clean grid+replays-only look. */
    public static volatile boolean MAIN_PREVIEW_WORLD_SKIP = true;

    /** True iff one of our world-skip conditions fires AND we're not
     *  in the middle of a BBS video-export render. */
    public static boolean shouldBypassWorld()
    {
        try
        {
            /* Side-effect: keep the server-visible flag in sync so the
             *  mob-tick skip on the server also self-disables during
             *  export. */
            boolean exporting = BBSRendering.isCustomSize();
            EditorMode.VIDEO_EXPORT_ACTIVE = exporting;
            if (exporting) return false;
        }
        catch (Throwable ignored) {}

        if (EditorMode.ACTIVE) return true;

        if (MAIN_PREVIEW_WORLD_SKIP && isCameraClipActiveInDashboard()) return true;

        return false;
    }

    /** True when at least one Add-Camera clip is currently active AND
     *  the user is in a BBS UIScreen. This is the "user is viewing the
     *  cinematic in the editor" state. */
    private static boolean isCameraClipActiveInDashboard()
    {
        try
        {
            if (com.afrobama.bbstools.client.campreview.ActiveCameraClipRegistry
                    .snapshot().isEmpty())
            {
                return false;
            }
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc == null) return false;
            return mc.gui.screen() instanceof mchorse.bbs_mod.ui.framework.UIScreen;
        }
        catch (Throwable ignored) {}
        return false;
    }

}
