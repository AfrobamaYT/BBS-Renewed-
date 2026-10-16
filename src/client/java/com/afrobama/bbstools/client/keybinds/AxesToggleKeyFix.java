// Coded by Afrobama/Claude — fix F8 conflict between BBS axes-toggle + editor cam
package com.afrobama.bbstools.client.keybinds;

import com.mojang.blaze3d.platform.InputConstants;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.utils.keys.KeyCombo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * BBS's {@code Keys.TRANSFORMATIONS_TOGGLE_AXES} defaults to plain F8.
 * Our {@code BBSToolsKeys.EDITOR_CAM_CYCLE} also defaults to F8.
 * Pressing F8 in the dashboard fires BBS's axes-toggle (the dashboard
 * has focus); pressing F8 in-world fires our editor cam. Net effect for
 * the user: F8 in dashboard silently toggles axes off → user thinks
 * the form-edit gizmo broke. We fixed this in BBS-Renewed source by
 * making axes-toggle require Ctrl+F8. With CML 2.0-dev jar back as the
 * runtime, the same fix has to live in BBSTools — done here.
 *
 * <p>Approach: at client init, reflectively mutate
 * {@code TRANSFORMATIONS_TOGGLE_AXES.keys} from {@code [F8]} to
 * {@code [F8, LEFT_CONTROL]}. The {@code keys} field is public and
 * mutable on {@code KeyCombo}, so no @Mixin / @Accessor needed.</p>
 *
 * <p>Idempotent: if the keys list already contains Ctrl, leave it
 * alone. Safe on re-init / reload.</p>
 */
public final class AxesToggleKeyFix
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/axes-key-fix");

    /** Latched once the rebind has run (or permanently failed) so the tick hook stops retrying. */
    private static boolean done = false;

    private AxesToggleKeyFix() {}

    /**
     * Registers the rebind to run on the FIRST client tick instead of at client-init.
     *
     * WHY NOT at init: touching {@code Keys.TRANSFORMATIONS_TOGGLE_AXES} forces
     * {@code UIKeys.<clinit>}, which calls {@code L10n.lang()}. With this BBS-CML jar as the
     * runtime, L10n's singleton is not up yet during onInitializeClient — the static init threw an
     * NPE, and (crucially) that POISONED UIKeys: every later access died with
     * {@code NoClassDefFoundError: Could not initialize class UIKeys}, taking the whole game down on
     * the title screen (crash-2026-07-24_20.17). By the first client tick the dashboard/L10n stack
     * is fully initialised, so the same touch is safe.
     */
    public static void register()
    {
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client ->
        {
            if (!done)
            {
                done = true;
                apply();
            }
        });
    }

    public static void apply()
    {
        try
        {
            KeyCombo combo = Keys.TRANSFORMATIONS_TOGGLE_AXES;
            if (combo == null) return;

            List<Integer> ks = combo.keys;
            if (ks == null) return;

            if (ks.contains(InputConstants.KEY_LCONTROL) || ks.contains(InputConstants.KEY_RCONTROL))
            {
                /* Already has Ctrl — either user-rebound or already fixed. */
                return;
            }

            if (!ks.contains(InputConstants.KEY_F8))
            {
                /* User rebound to something else entirely — don't touch. */
                return;
            }

            List<Integer> rebuilt = new ArrayList<>(ks);
            rebuilt.add(InputConstants.KEY_LCONTROL);
            combo.keys = rebuilt;

            LOG.info("Rebound BBS TRANSFORMATIONS_TOGGLE_AXES from F8 to Ctrl+F8 (resolves conflict with EDITOR_CAM_CYCLE)");
        }
        catch (Throwable t)
        {
            LOG.warn("Failed to rebind axes-toggle: {}", t.toString());
        }
    }

}
