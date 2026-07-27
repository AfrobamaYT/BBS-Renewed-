// Coded by Afrobama/Claude — Tier 2d CPU-side Hi-Z occlusion culling.
//
// SCAFFOLD STATUS: Stats interface + AABB query API in place. PBO
// readback + Hi-Z pyramid build is TODO. Currently every query returns
// true (no occlusion) — safe conservative default.
//
// Architecture: 64×64 depth pyramid (7 mip levels), regenerated from
// previous-frame depth via PBO (zero-stall readback). 1-frame latency
// acceptable for distant mobs.
package com.afrobama.bbstools.perf;

import com.afrobama.bbstools.debug.IFeatureDebugger;

import java.util.concurrent.atomic.AtomicLong;

public final class BBSCoarseOccluder implements IFeatureDebugger
{
    public static final BBSCoarseOccluder INSTANCE = new BBSCoarseOccluder();

    public static volatile boolean ENABLED = false;

    public final AtomicLong queries = new AtomicLong();
    public final AtomicLong cullPasses = new AtomicLong();
    public volatile long lastReadbackLatencyFrames;

    private BBSCoarseOccluder() {}

    /** Query whether the AABB around (cx,cy,cz) ± radius is potentially
     *  visible given the previous frame's depth pyramid.
     *  TODO impl: project to NDC, sample Hi-Z mip at radius-appropriate
     *  level, compare depth.
     *  Currently returns true (conservative — no occlusion). */
    public boolean queryAabb(double cx, double cy, double cz, float radius)
    {
        if (!ENABLED) return true;
        queries.incrementAndGet();
        /* TODO: actual occlusion test. */
        return true;
    }

    /** Called from WorldRenderEvents.AfterTranslucent to schedule PBO
     *  readback of the current depth buffer for next-frame use. */
    public void kickReadback()
    {
        if (!ENABLED) return;
        /* TODO: glReadPixels to PBO, swap PBO, build mip pyramid from
         * previous-frame PBO on render thread. */
    }

    @Override public String getName() { return "occluder"; }
    @Override public boolean isEnabled() { return ENABLED; }
    @Override public void setEnabled(boolean v) { ENABLED = v; }
    @Override public int priority() { return 60; }

    @Override
    public void writeStatus(StringBuilder sb)
    {
        sb.append("enabled=").append(ENABLED).append("\nscaffold=true (PBO+pyramid TODO)\n");
    }

    @Override
    public void writeStats(StringBuilder sb)
    {
        long q = queries.get(), c = cullPasses.get();
        sb.append("queries=").append(q).append('\n');
        sb.append("cullPasses=").append(c).append('\n');
        if (q > 0) sb.append("cullRate=").append(String.format("%.2f%%", 100.0 * c / q)).append('\n');
        sb.append("readbackLatencyFrames=").append(lastReadbackLatencyFrames).append('\n');
    }

}
