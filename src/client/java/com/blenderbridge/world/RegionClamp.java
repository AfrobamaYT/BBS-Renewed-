package com.blenderbridge.world;

import net.minecraft.core.BlockPos;

/**
 * Export-scoped "the region boundary is air" rule.
 *
 * <p>Face culling asks the REAL world about neighbours, and the world keeps existing beyond the
 * export region — so a block sitting on the region border sees a solid neighbour outside and its
 * boundary face gets culled. The exported mesh then has no side walls and no floor: viewed from
 * below/outside it looks like a floating skin with most of the underground "missing" (user report
 * 2026-07-17; measured: 1.26M solid blocks in-game vs 85k emitted). Treating out-of-region
 * neighbours as air closes the box and gives the solid diorama look.</p>
 *
 * <p>Deliberately a SEPARATE class: {@link WorldExporter} is already loaded in the running game,
 * so adding fields there would need a reboot — a new class lazy-loads from the dev classpath and
 * the exporter only needs (hotswappable) body edits. State is set/cleared by
 * {@link WorldExporter#export} around one export run; the alt path runs on the render thread only,
 * so plain fields suffice.</p>
 */
public final class RegionClamp
{
    private static boolean active;
    private static int minX;
    private static int minY;
    private static int minZ;
    private static int maxX;
    private static int maxY;
    private static int maxZ;

    private RegionClamp()
    {}

    /** Arm the clamp for one export run over the inclusive region [min, max]. */
    public static void set(int x0, int y0, int z0, int x1, int y1, int z1)
    {
        minX = x0;
        minY = y0;
        minZ = z0;
        maxX = x1;
        maxY = y1;
        maxZ = z1;
        active = true;
    }

    /** Disarm — MUST run in a finally so a failed export can never leak the clamp. */
    public static void clear()
    {
        active = false;
    }

    /**
     * True if the neighbour position lies outside the armed export region — the caller must then
     * treat it as air and DRAW the face regardless of what the real world says.
     */
    public static boolean isBoundaryAir(BlockPos neighbour)
    {
        return active
            && (neighbour.getX() < minX || neighbour.getX() > maxX
                || neighbour.getY() < minY || neighbour.getY() > maxY
                || neighbour.getZ() < minZ || neighbour.getZ() > maxZ);
    }
}
