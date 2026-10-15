package mchorse.bbs_mod.integrations.vitrail;

import com.mojang.blaze3d.buffers.GpuBuffer;

/* Vitrail laeuft auf Vulkan, das Minecraft erst ab 26.1 hat (vor 26.1 ist die Integration
 * ausgelassen, build.gradle nurAb261). Diese Fassade beantwortet die Fragen des gemeinsamen Codes
 * so, wie es die echte ohne geladenes Vitrail tut ("nicht aktiv"); Handlungen, die nur bei
 * aktivem Vitrail vorkommen, scheitern laut. */
public final class VitrailMeshInputs
{
    private VitrailMeshInputs() {}
    public static VitrailMeshInputs model(GpuBuffer corner, int count, int vertices,
        GpuBuffer positions, GpuBuffer normals, GpuBuffer uvs, GpuBuffer indices) { throw new IllegalStateException("Vitrail gibt es vor Minecraft 26.1 nicht"); }
    public static VitrailMeshInputs skin(GpuBuffer corner, int count, int vertices,
        GpuBuffer positions, GpuBuffer normals, GpuBuffer uvs, GpuBuffer indices,
        GpuBuffer palette, GpuBuffer joints0, GpuBuffer joints1, GpuBuffer weights0, GpuBuffer weights1) { throw new IllegalStateException("Vitrail gibt es vor Minecraft 26.1 nicht"); }
}
