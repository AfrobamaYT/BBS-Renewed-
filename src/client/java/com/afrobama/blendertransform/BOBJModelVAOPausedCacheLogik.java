package com.afrobama.blendertransform;

import com.afrobama.bbstools.perf.BBSPerfConfig;
import mchorse.bbs_mod.cubic.render.vao.BOBJModelVAO;
import mchorse.bbs_mod.cubic.render.vao.BobjPoseSnapshot;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import org.joml.Matrix4f;

/** Cancel only terminal updates whose complete evaluated inputs and actual native pose match. */
public final class BOBJModelVAOPausedCacheLogik
{
    private BOBJModelVAOPausedCacheLogik()
    {}

    public static boolean skipWhenPosesUnchanged(Object[] state, StencilMap stencil,
        BOBJModelVAO vao, Matrix4f[] matrices, boolean gpuAllowed)
    {
        boolean unchanged = BBSPerfConfig.ENABLE_PAUSED_BONE_CACHE && stencil == null
            && state[0] instanceof BobjPoseSnapshot snapshot
            && vao.pausedPoseReady(state[1], gpuAllowed)
            && snapshot.matches(matrices, vao.pausedPoseShapes());
        if (unchanged)
        {
            BBSPerfConfig.pausedBoneCacheHits++;
            return true;
        }

        /* Clear before the update: an exception or picking upload must never create a hit. */
        state[0] = null;
        state[1] = null;
        if (BBSPerfConfig.ENABLE_PAUSED_BONE_CACHE) BBSPerfConfig.pausedBoneCacheMisses++;
        return false;
    }

    public static void rememberCompletedPose(Object[] state, StencilMap stencil,
        BOBJModelVAO vao, Matrix4f[] matrices, boolean gpuAllowed)
    {
        if (!BBSPerfConfig.ENABLE_PAUSED_BONE_CACHE || stencil != null || matrices == null
            || !vao.pausedPoseReady(vao.pausedPoseIdentity(), gpuAllowed)) return;
        state[0] = new BobjPoseSnapshot(matrices, vao.pausedPoseShapes());
        state[1] = vao.pausedPoseIdentity();
    }
}
