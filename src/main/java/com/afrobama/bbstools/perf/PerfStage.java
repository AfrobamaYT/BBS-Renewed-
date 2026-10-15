// Coded by Afrobama/Claude — render-stage enum for Tier 4b perf HUD.
// Each tier renderer brackets its work with BBSPerfHud.begin(stage) /
// .end(stage) so the HUD shows where the frame budget goes.
package com.afrobama.bbstools.perf;

public enum PerfStage
{
    CROWD_BAKE,
    CROWD_SUBMIT,
    CROWD_GPU_INSTANCED,
    CROWD_ACCEL_RENDER,
    VEIL_SKINNED_BAKE,
    VEIL_SKINNED_SUBMIT,
    VEIL_CUBIC_BAKE,
    VEIL_CUBIC_SUBMIT,
    IRIS_SHADOW,
    IRIS_TRANSLUCENT,
    BBS_REPLAY_TICK,
    ANIMATOR_PARALLEL,
    TOTAL;

    public static final int COUNT = values().length;
}
