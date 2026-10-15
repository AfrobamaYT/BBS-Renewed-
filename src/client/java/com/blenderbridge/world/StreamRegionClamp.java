package com.blenderbridge.world;

import net.minecraft.core.BlockPos;

/**
 * Task 3.3c, streaming half: "the export-region boundary is air" for the STREAMING path —
 * the exact mirror of {@link RegionClamp} (alt path). The {@link ColumnSnapshot} rim hands the
 * mesh workers REAL out-of-region neighbours, so boundary faces get culled and the export is an
 * open skin (same user-visible bug the alt path had: underground looks missing from below).
 *
 * <p>Deliberately a SEPARATE class from {@link RegionClamp}: the two paths have different
 * lifecycles (alt = synchronous, armed/cleared around one render-thread call; streaming = armed
 * per job on the render thread, then read from WORKER threads until the async job ends — hence
 * volatile). Sharing one static slot would let an alt export's {@code finally}-clear disarm a
 * streaming job mid-flight.</p>
 *
 * <p>Armed by {@code WorldExportBridge.export} (body-only edit there — this class is new and
 * lazy-loads, the SkyReachability/FaceMerger "DEPLOY-WINDOW-FREE WIRING" pattern). No disarm on
 * job end needed: snapshot reads only happen during a job, and the next job re-arms before any
 * read. {@code fields.openBounds=true} restores the old open-skin behaviour.</p>
 */
public final class StreamRegionClamp
{
    private static volatile boolean active;
    private static volatile int minX;
    private static volatile int minY;
    private static volatile int minZ;
    private static volatile int maxX;
    private static volatile int maxY;
    private static volatile int maxZ;

    private StreamRegionClamp()
    {}

    /** Arm (or disarm, {@code close=false}) for the job about to start. Render thread only. */
    public static void armNextJob(boolean close, int x0, int y0, int z0, int x1, int y1, int z1)
    {
        minX = x0;
        minY = y0;
        minZ = z0;
        maxX = x1;
        maxY = y1;
        maxZ = z1;
        active = close;
    }

    /**
     * True if the position lies outside the armed export region — the snapshot must then report
     * air so the boundary face gets DRAWN. Worker-thread safe (volatile reads only).
     */
    public static boolean isBoundaryAir(BlockPos pos)
    {
        return active
            && (pos.getX() < minX || pos.getX() > maxX
                || pos.getY() < minY || pos.getY() > maxY
                || pos.getZ() < minZ || pos.getZ() > maxZ);
    }
}
