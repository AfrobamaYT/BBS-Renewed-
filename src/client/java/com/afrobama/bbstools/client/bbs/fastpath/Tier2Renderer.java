// Coded by Afrobama/Claude — Tier 2-a renderer scaffold.
//
// Tier 2-a (this file): validates the SkinnedMeshIR + CubicToSkinnedConverter
// + SkinnedMeshCache pipeline by walking each submission's model and
// triggering lazy IR build. Actual draw stays on Tier 1.5b-2 direct path
// — Tier 2-a is a measurement step proving the IR foundation is correct.
//
// Tier 2-b: replaces the FastPathRenderer.renderOne fallback inside the
// per-model-id loop with a real GPU-instanced draw using the IR.
//
// Tier 2-c: lifts the per-instance CPU skin step into a compute shader
// prepass (Wicked Engine pattern), then issues a single
// glMultiDrawArraysIndirect under standard RenderType.entityCutoutNoCull
// so Iris sees a vanilla VBO.
package com.afrobama.bbstools.client.bbs.fastpath;

import com.afrobama.bbstools.client.bbs.mesh.SkinnedMeshCache;
import com.afrobama.bbstools.client.bbs.mesh.SkinnedMeshIR;
import com.afrobama.bbstools.perf.BBSPerfConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public final class Tier2Renderer
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/fast-path/tier2");

    private static volatile boolean LOGGED_FIRST = false;

    private Tier2Renderer() {}

    /**
     * Tier 2-a entry: walk the per-model-id batch, build IR for cubic
     * models lazily, count hits/misses, then DELEGATE actual draw to
     * FastPathRenderer (Tier 1.5b-2 direct path).
     *
     * Tier 2-b will replace the delegate with the real GPU draw inside
     * the per-model-id loop after the cache hit.
     */
    public static void runScaffold()
    {
        Map<String, List<FastPathBatch.Submission>> batch = FastPathBatch.snapshotBatch();
        if (batch.isEmpty()) return;

        int hit = 0;
        int miss = 0;
        int delegated = 0;
        int totalVerts = 0;

        for (Map.Entry<String, List<FastPathBatch.Submission>> entry : batch.entrySet())
        {
            List<FastPathBatch.Submission> list = entry.getValue();
            if (list.isEmpty()) continue;

            /* Probe the cache with the FIRST submission's instance — all
             * instances in this bucket share the same model.id by
             * construction (FastPathBatch keys by instance.id). */
            FastPathBatch.Submission first = list.get(0);
            SkinnedMeshIR ir = SkinnedMeshCache.getOrBuild(first.instance);
            if (ir != null && ir.vertCount > 0)
            {
                hit++;
                totalVerts += ir.vertCount;
                BBSPerfConfig.tier2CacheHits += list.size();
            }
            else
            {
                miss++;
                BBSPerfConfig.tier2CacheMisses += list.size();
            }
            delegated += list.size();
        }

        BBSPerfConfig.tier2ScaffoldFlushes++;

        if (!LOGGED_FIRST)
        {
            LOGGED_FIRST = true;
            LOG.info("[tier2/scaffold] FIRST flush — modelGroups={} hits={} misses={} totalVertsThisFrame={} delegatedSubs={}",
                batch.size(), hit, miss, totalVerts, delegated);
        }
    }

    public static String snapshot()
    {
        return String.format(
            "scaffoldFlushes=%d cacheHits=%d cacheMisses=%d",
            BBSPerfConfig.tier2ScaffoldFlushes,
            BBSPerfConfig.tier2CacheHits,
            BBSPerfConfig.tier2CacheMisses)
            + "  mesh{" + SkinnedMeshCache.snapshot() + "}";
    }

    public static void resetCounters()
    {
        LOGGED_FIRST = false;
        BBSPerfConfig.tier2ScaffoldFlushes = 0L;
        BBSPerfConfig.tier2CacheHits = 0L;
        BBSPerfConfig.tier2CacheMisses = 0L;
        SkinnedMeshCache.clear();
    }

}
