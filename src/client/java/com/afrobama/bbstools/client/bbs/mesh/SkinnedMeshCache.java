// Coded by Afrobama/Claude — Tier 2-a per-model.id mesh cache.
//
// Lazy-builds SkinnedMeshIR on first encounter of a model, caches by
// ModelInstance.id (stable across instance copies — same model.id =
// same rest-pose geometry).
//
// Cache eviction: NONE in Tier 2-a (small set of unique models per film).
// Tier 3 may add LRU eviction if memory grows unbounded. Worst case for
// the user's content: 100 unique models × ~100 KB IR each = 10 MB. Fits.
//
// Thread-safety: ConcurrentHashMap so future parallel-animator (Tier 2.5)
// can probe without locking.
package com.afrobama.bbstools.client.bbs.mesh;

import com.afrobama.bbstools.perf.BBSPerfConfig;

import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.model.bobj.BOBJModel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;

public final class SkinnedMeshCache
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/mesh/cache");

    private static final ConcurrentHashMap<String, SkinnedMeshIR> CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> FAILED = new ConcurrentHashMap<>();

    private SkinnedMeshCache() {}

    /**
     * Lazy-fetch or build. Returns null if conversion failed (BOBJ in
     * Tier 2-a, unsupported model type, or build threw).
     */
    public static SkinnedMeshIR getOrBuild(ModelInstance instance)
    {
        if (instance == null || instance.id == null || instance.model == null) return null;
        String id = instance.id;

        SkinnedMeshIR existing = CACHE.get(id);
        if (existing != null) return existing;

        if (FAILED.getOrDefault(id, Boolean.FALSE)) return null;

        long startNs = System.nanoTime();
        try
        {
            SkinnedMeshIR built = build(id, instance.model);
            if (built == null)
            {
                FAILED.put(id, Boolean.TRUE);
                return null;
            }
            CACHE.put(id, built);
            BBSPerfConfig.tier2MeshesBuilt++;
            BBSPerfConfig.tier2MeshBuildNsAccum += (System.nanoTime() - startNs);
            return built;
        }
        catch (Throwable t)
        {
            LOG.warn("[mesh-cache] build threw for id={}: {}", id, t.toString(), t);
            FAILED.put(id, Boolean.TRUE);
            BBSPerfConfig.tier2MeshBuildFailures++;
            return null;
        }
    }

    private static SkinnedMeshIR build(String id, IModel model)
    {
        if (model instanceof Model cubic)
        {
            return CubicToSkinnedConverter.convert(id, cubic);
        }
        if (model instanceof BOBJModel bobj)
        {
            /* Tier 2-b will provide BOBJToSkinnedConverter. For Tier 2-a
             * BOBJ models cleanly fall through to vanilla render. */
            return null;
        }
        return null;
    }

    public static int cacheSize()
    {
        return CACHE.size();
    }

    public static int failedCount()
    {
        return FAILED.size();
    }

    public static void clear()
    {
        CACHE.clear();
        FAILED.clear();
        BBSPerfConfig.tier2MeshesBuilt = 0L;
        BBSPerfConfig.tier2MeshBuildFailures = 0L;
        BBSPerfConfig.tier2MeshBuildNsAccum = 0L;
    }

    public static String snapshot()
    {
        long avgBuildMs = BBSPerfConfig.tier2MeshesBuilt == 0 ? 0
            : (BBSPerfConfig.tier2MeshBuildNsAccum / BBSPerfConfig.tier2MeshesBuilt) / 1_000_000L;
        long totalVerts = 0;
        long totalBytes = 0;
        for (SkinnedMeshIR ir : CACHE.values())
        {
            totalVerts += ir.vertCount;
            totalBytes += ir.byteSize();
        }
        return String.format(
            "cached=%d failed=%d builds=%d avgBuildMs=%d totalVerts=%d totalKB=%d",
            CACHE.size(), FAILED.size(), BBSPerfConfig.tier2MeshesBuilt, avgBuildMs,
            totalVerts, totalBytes / 1024);
    }

}
