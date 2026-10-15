// Coded by Afrobama/Claude — Phase 14 visibility-toggle state
package com.afrobama.bbstools.client.util;

/**
 * Global toggle for {@link com.afrobama.bbstools.client.campreview.CameraFrustumMeshRenderer}.
 * Lives in a non-mixin util class so mixin rules don't apply.
 * Default ON — Blender-style camera meshes are shown at every active
 * Add-Camera clip's pose, toggled off with the keybind.
 */
public final class CameraFrustumVisibility
{
    public static volatile boolean visible = true;

    private CameraFrustumVisibility() {}

    public static void toggle()
    {
        visible = !visible;
    }

}
