// Coded by Afrobama/Claude — extracted state for cam-preview pre-pass mixin
package com.afrobama.bbstools.client.util;

/**
 * Holds the {@code PREVIEW_PASS_ACTIVE} guard flag used by
 * {@code GameRendererPreviewPrePassMixin} to prevent infinite recursion
 * when it re-enters {@code renderWorld} for each preview camera.
 *
 * <p>Mixin disallows non-private static fields/methods inside {@code @Mixin}
 * classes (throws {@code InvalidMixinException} at apply-time). Routing
 * the flag through this plain class lets external code (the preview
 * blit fallback in {@code CameraPreviewRenderHook}) check it.</p>
 */
public final class PreviewPassState
{
    public static volatile boolean PREVIEW_PASS_ACTIVE;

    private PreviewPassState() {}

    public static boolean isActive()
    {
        return PREVIEW_PASS_ACTIVE;
    }

}
