package mchorse.bbs_mod.crowd;

/**
 * Command-tag identifiers used by crowd-mode tooling.
 *
 * Both server-side spawn (BenchmarkSpawn, future MobArmy port) and client-side
 * LOD logic reference these constants — keep them in one place to avoid drift.
 */
public final class CrowdTags
{
    /** Marks an entity as eligible for client-side crowd-LOD culling. */
    public static final String LOD = "bbs_crowd";

    /** Marks an entity as belonging to an active /bbs benchmark session (cleanup target). */
    public static final String BENCHMARK = "bbs_benchmark";

    private CrowdTags()
    {
    }

}
