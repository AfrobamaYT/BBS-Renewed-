// Coded by Afrobama/Claude
package com.afrobama.bbstools.client.qol;

import com.afrobama.bbstools.perf.BBSPerfConfig;

import mchorse.bbs_mod.BBSSettings;

/**
 * Bridges the BBS top-bar "Bake" menu to BBSTools' perf config.
 *
 * <p>The Bake menu (a CML source patch in {@code UIMainMenuBar}) flips
 * {@code BBSSettings.bake*} flags. This class mirrors those flags into
 * {@link BBSPerfConfig} once per client tick. It applies the saved settings
 * on the first tick (so toggles persist across restarts), then only pushes a
 * value when its setting actually changes — so manual {@code /bbstools}
 * commands made between menu toggles are not stomped every tick.</p>
 */
public final class BakeMenuSync
{
    private static boolean primed = false;

    private static boolean lastFastPath;
    private static boolean lastFastPathRender;
    private static boolean lastCrowdMode;
    private static boolean lastLod;
    private static boolean lastDistanceCulling;
    private static boolean lastArmyBake;

    private BakeMenuSync()
    {
    }

    public static void tick()
    {
        try
        {
            boolean fastPath = BBSSettings.bakeFastPath.get();
            boolean fastPathRender = BBSSettings.bakeFastPathRender.get();
            boolean crowdMode = BBSSettings.bakeCrowdMode.get();
            boolean lod = BBSSettings.bakeLod.get();
            boolean distanceCulling = BBSSettings.bakeDistanceCulling.get();
            boolean armyBake = BBSSettings.bakeArmyBake.get();

            if (!primed)
            {
                /* First tick: honor whatever was saved last session. */
                BBSPerfConfig.ENABLE_FAST_PATH = fastPath;
                BBSPerfConfig.ENABLE_FAST_PATH_RENDER = fastPathRender;
                BBSPerfConfig.UI_REPLAY_LIST_CROWD_MODE = crowdMode;
                BBSPerfConfig.ENABLE_LOD = lod;
                BBSPerfConfig.ENABLE_DISTANCE_CULLING = distanceCulling;
                /* r76: Army-bake toggle = the WHOLE Tier-2h stack (the old working
                 * "/bbstools replay-fast-path bench" flag set), not just ARMY_BAKE.
                 * Army-bake alone left the crowd un-instanced + bar empty; the user
                 * confirmed bench (19 flags) was what actually worked. Prime the
                 * full stack from the saved setting on first tick. */
                if (armyBake) BBSPerfConfig.applyFullStack(true);
                else BBSPerfConfig.ENABLE_ARMY_BAKE = false;

                lastFastPath = fastPath;
                lastFastPathRender = fastPathRender;
                lastCrowdMode = crowdMode;
                lastLod = lod;
                lastDistanceCulling = distanceCulling;
                lastArmyBake = armyBake;
                primed = true;
                return;
            }

            if (fastPath != lastFastPath)
            {
                BBSPerfConfig.ENABLE_FAST_PATH = fastPath;
                lastFastPath = fastPath;
            }
            if (fastPathRender != lastFastPathRender)
            {
                BBSPerfConfig.ENABLE_FAST_PATH_RENDER = fastPathRender;
                lastFastPathRender = fastPathRender;
            }
            if (crowdMode != lastCrowdMode)
            {
                BBSPerfConfig.UI_REPLAY_LIST_CROWD_MODE = crowdMode;
                lastCrowdMode = crowdMode;
            }
            if (lod != lastLod)
            {
                BBSPerfConfig.ENABLE_LOD = lod;
                lastLod = lod;
            }
            if (distanceCulling != lastDistanceCulling)
            {
                BBSPerfConfig.ENABLE_DISTANCE_CULLING = distanceCulling;
                lastDistanceCulling = distanceCulling;
            }
            if (armyBake != lastArmyBake)
            {
                /* r76: toggling Army-bake flips the WHOLE Tier-2h stack (bench flag
                 * set), so one menu click = full optimized + baked path. */
                BBSPerfConfig.applyFullStack(armyBake);
                lastArmyBake = armyBake;
            }
        }
        catch (Throwable ignored)
        {
            /* Running against a BBS build without the bake flags — no-op. */
        }
    }

}
