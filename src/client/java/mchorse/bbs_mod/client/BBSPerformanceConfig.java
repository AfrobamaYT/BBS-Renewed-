package mchorse.bbs_mod.client;

/**
 * Static performance tuning knobs for BBS film rendering.
 *
 * <p>These are runtime-mutable plain fields so an addon, the in-game console,
 * or a future BBSSettings hook can adjust them without rebuilding. Distances
 * are stored as squared values so the per-actor cull check avoids a square
 * root.</p>
 */
public class BBSPerformanceConfig
{
    /* Distance culling */

    /**
     * When true, actors farther than {@link #MAX_RENDER_DISTANCE_SQ} from the
     * camera are skipped entirely in BaseFilmController.renderEntity.
     */
    public static boolean ENABLE_DISTANCE_CULLING = true;

    /**
     * Squared distance (blocks^2) beyond which an actor is not rendered.
     * Default: 256 blocks → 65536.
     */
    public static double MAX_RENDER_DISTANCE_SQ = 256.0D * 256.0D;

    /**
     * Squared distance beyond which an actor is skipped during Iris shadow
     * passes only. Default: 128 blocks → 16384.
     */
    public static double SHADOW_CULLING_DISTANCE_SQ = 128.0D * 128.0D;

    /* Shadow pass */

    /**
     * When true, ALL form rendering is skipped during Iris shadow passes,
     * regardless of the per-form shaderShadow setting. Trades shader shadow
     * fidelity for raw FPS — actors will not cast shader-pack shadows at all.
     */
    public static boolean SKIP_ALL_SHADOW_PASSES = false;

    /* Coded by Afrobama/Claude — LOD body-parts skip
     * When true, body-part stack of distant actors is dropped (>LOD radius).
     * Main mesh still renders — only the cosmetic body-part stack is skipped.
     * Default radius 64² ≈ keeps full detail at typical cinematic framing
     * while saving work for very-distant LOD.
     */
    public static boolean ENABLE_LOD = false;
    public static double LOD_DETAIL_RADIUS_SQ = 64.0D * 64.0D;

    /* Coded by Afrobama/Claude — verbose chat for fracture/destruction events.
     * Default OFF — was on during the 2026-05-10 duplicate-spawn hunt.
     * LOG.info side stays active regardless. */
    public static boolean VERBOSE_DESTRUCTION_CHAT = false;

    /* Coded by Afrobama/Claude — debug counters surfaced in /bbs-renewed report.
     * Volatile-equivalent (these are racy reads; brief inaccuracies are
     * acceptable for diagnostic output). */
    public static long formRendererCallCount;
    public static long formRendererSkipCount;
    public static long shadowPassDetectedCount;
    public static long distanceCullCount;
    public static long shadowCullCount;
    public static long lodBodyPartSkipCount;

    public static void resetCounters()
    {
        formRendererCallCount = 0L;
        formRendererSkipCount = 0L;
        shadowPassDetectedCount = 0L;
        distanceCullCount = 0L;
        shadowCullCount = 0L;
        lodBodyPartSkipCount = 0L;
    }

    public static String dump()
    {
        return String.format(
            "distance_cull=%s  max=%.0f blocks  shadow_cull=%.0f blocks  skip_all_shadow=%s  lod=%s  lod_radius=%.0f blocks",
            ENABLE_DISTANCE_CULLING,
            Math.sqrt(MAX_RENDER_DISTANCE_SQ),
            Math.sqrt(SHADOW_CULLING_DISTANCE_SQ),
            SKIP_ALL_SHADOW_PASSES,
            ENABLE_LOD,
            Math.sqrt(LOD_DETAIL_RADIUS_SQ)
        );
    }

    public static String stats()
    {
        return String.format(
            "form_calls=%d  shadow_detected=%d  shadow_skip=%d  dist_cull=%d  shadow_cull=%d  lod_bp_skip=%d  campreview=%s",
            formRendererCallCount, shadowPassDetectedCount, formRendererSkipCount,
            distanceCullCount, shadowCullCount, lodBodyPartSkipCount,
            camPreviewDump()
        );
    }

    /**
     * Die cam-preview-zahlen von BBSTools, falls der mod da ist — sonst ein platzhalter.
     *
     * <p>⚠ WARUM REFLECTION UND NICHT DER DIREKTE AUFRUF (2026-07-26): diese klasse gehört
     * BBS ({@code mchorse.bbs_mod.client}). Ein direkter aufruf von
     * {@code com.afrobama.bbstools.client.campreview.CameraPreviewPerf} macht den BBS-fork
     * von BBSTools abhängig — dann kompiliert BBS nicht mehr ohne unseren mod, und ein
     * upstream-merge von ElGatoPro300 bricht.</p>
     *
     * <p>Das war eine von acht solchen stellen; die anderen sieben waren falsch einsortierte
     * eigene dateien und sind verschoben. Diese hier ist ein echter aufruf und braucht die
     * indirektion.</p>
     */
    private static String camPreviewDump()
    {
        try
        {
            return String.valueOf(Class.forName("com.afrobama.bbstools.client.campreview.CameraPreviewPerf")
                .getMethod("dump").invoke(null));
        }
        catch (Throwable ohneBBSTools)
        {
            /* Mod nicht da oder umbenannt — die diagnose-zeile bleibt lesbar statt zu werfen. */
            return "campreview=n/a";
        }
    }

    /* Convenience helpers */

    /**
     * Update the maximum render distance using a plain block radius. Stores
     * the squared value internally.
     */
    public static void setMaxRenderDistance(double blocks)
    {
        MAX_RENDER_DISTANCE_SQ = blocks * blocks;
    }

    /**
     * Update the shadow culling distance using a plain block radius. Stores
     * the squared value internally.
     */
    public static void setShadowCullingDistance(double blocks)
    {
        SHADOW_CULLING_DISTANCE_SQ = blocks * blocks;
    }

    private BBSPerformanceConfig()
    {}
}
