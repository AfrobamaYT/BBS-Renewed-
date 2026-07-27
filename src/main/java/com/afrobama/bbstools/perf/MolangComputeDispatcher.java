// Coded by Afrobama/Claude — Tier 3a Molang compute-shader eval skeleton.
//
// SCAFFOLD STATUS: API in place. AST walker → bytecode emit + GLSL
// compute shader + SSBO setup is TODO (5 days work). Only engages at
// replayCount >= MOLANG_GPU_MIN_REPLAYS threshold.
package com.afrobama.bbstools.perf;

import com.afrobama.bbstools.debug.IFeatureDebugger;

import java.util.concurrent.atomic.AtomicLong;

public final class MolangComputeDispatcher implements IFeatureDebugger
{
    public static final MolangComputeDispatcher INSTANCE = new MolangComputeDispatcher();

    public static volatile boolean ENABLED = false;
    public static volatile int MOLANG_GPU_MIN_REPLAYS = 100;

    public final AtomicLong gpuEvalReplays = new AtomicLong();
    public final AtomicLong cpuFallback = new AtomicLong();
    public final AtomicLong opcodesUnknown = new AtomicLong();

    private MolangComputeDispatcher() {}

    @Override public String getName() { return "molang"; }
    @Override public boolean isEnabled() { return ENABLED; }
    @Override public void setEnabled(boolean v) { ENABLED = v; }
    @Override public int priority() { return 110; }

    @Override
    public void writeStatus(StringBuilder sb)
    {
        sb.append("enabled=").append(ENABLED).append("\nscaffold=true (compute shader + AST walker TODO)\n");
        sb.append("minReplaysThreshold=").append(MOLANG_GPU_MIN_REPLAYS).append('\n');
    }

    @Override
    public void writeStats(StringBuilder sb)
    {
        sb.append("gpuEvalReplays=").append(gpuEvalReplays.get()).append('\n');
        sb.append("cpuFallback=").append(cpuFallback.get()).append('\n');
        sb.append("opcodesUnknown=").append(opcodesUnknown.get()).append('\n');
    }

}
