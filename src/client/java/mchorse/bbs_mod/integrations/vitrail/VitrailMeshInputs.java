package mchorse.bbs_mod.integrations.vitrail;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Actual current VAO pose streams. Capture before another actor changes the shared GPU buffers. */
public record VitrailMeshInputs(VitrailVertexSource.Kind kind, GpuBuffer corners, int count,
    Map<String, GpuBufferSlice> texels, GpuBufferSlice bones)
{
    public VitrailMeshInputs
    {
        if (kind == null || count < 1 || count % 3 != 0 || corners == null || corners.isClosed()
            || corners.size() < (long) count * 4 || (corners.usage() & GpuBuffer.USAGE_VERTEX) == 0)
            throw new IllegalArgumentException("Native Vitrail triangles need live sequential UINT32 corners");
        texels = Collections.unmodifiableMap(new LinkedHashMap<>(texels));
        if ((kind == VitrailVertexSource.Kind.SKIN) != (bones != null))
            throw new IllegalArgumentException("Native Vitrail skin streams and actual palette disagree");
        var required = new java.util.HashSet<>(java.util.Set.of("BbsIndexBuffer", "BbsPositionBuffer",
            "BbsNormalBuffer", "BbsUvBuffer"));
        if (kind == VitrailVertexSource.Kind.SKIN)
            required.addAll(java.util.Set.of("BbsJoints0", "BbsJoints1", "BbsWeights0", "BbsWeights1"));
        if (kind == VitrailVertexSource.Kind.CROWD || !texels.keySet().equals(required))
            throw new IllegalArgumentException("Native model/skin input streams do not match their declared shader schema");
    }

    public static VitrailMeshInputs model(GpuBuffer corner, int count, int vertices,
        GpuBuffer positions, GpuBuffer normals, GpuBuffer uvs, GpuBuffer indices)
    {
        return skin(corner, count, vertices, positions, normals, uvs, indices, null, null, null, null, null);
    }

    public static VitrailMeshInputs skin(GpuBuffer corner, int count, int vertices,
        GpuBuffer positions, GpuBuffer normals, GpuBuffer uvs, GpuBuffer indices,
        GpuBuffer palette, GpuBuffer joints0, GpuBuffer joints1, GpuBuffer weights0, GpuBuffer weights1)
    {
        if (vertices < 1) throw new IllegalArgumentException("Native Vitrail mesh has no source vertices");
        Map<String, GpuBufferSlice> streams = new LinkedHashMap<>();
        streams.put("BbsIndexBuffer", (indices == null ? corner : indices).slice(0, Math.multiplyExact((long) count, 4)));
        streams.put("BbsPositionBuffer", positions.slice(0, Math.multiplyExact((long) vertices, 12)));
        streams.put("BbsNormalBuffer", normals.slice(0, Math.multiplyExact((long) vertices, 12)));
        streams.put("BbsUvBuffer", uvs.slice(0, Math.multiplyExact((long) vertices, 8)));
        if (palette != null)
        {
            long influenceBytes = Math.multiplyExact((long) vertices, 16);
            streams.put("BbsJoints0", joints0.slice(0, influenceBytes));
            streams.put("BbsJoints1", joints1.slice(0, influenceBytes));
            streams.put("BbsWeights0", weights0.slice(0, influenceBytes));
            streams.put("BbsWeights1", weights1.slice(0, influenceBytes));
        }
        return new VitrailMeshInputs(palette == null ? VitrailVertexSource.Kind.MODEL : VitrailVertexSource.Kind.SKIN,
            corner, count, streams, palette == null ? null : palette.slice());
    }

    /** Owns copies until close after both camera and afterLevel shadow draws, even across encoder submissions. */
    public Snapshot capture(GpuDevice device, CommandEncoder encoder, long texelAlignment, long maximumTexels,
        long uniformAlignment, long maximumUniformBytes)
    {
        if (encoder instanceof com.mojang.blaze3d.systems.CommandEncoder frontend
            && frontend.isInRenderPass()) throw new IllegalStateException("Capture BBS Vitrail geometry before opening its pass");
        var owned = Collections.newSetFromMap(new java.util.IdentityHashMap<GpuBuffer, Boolean>());
        try
        {
            var cornerSource = this.corners.slice(0, (long) this.count * 4);
            var cornerCopy = copy(device, encoder, cornerSource, owned,
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_INDEX | GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER);
            var copies = new LinkedHashMap<String, GpuBufferSlice>();
            for (var stream : this.texels.entrySet())
            {
                var source = stream.getValue();
                var format = stream.getKey().equals("BbsIndexBuffer") || stream.getKey().startsWith("BbsJoints")
                    ? mchorse.bbs_mod.compat.alt.GpuFormat.R32_UINT : mchorse.bbs_mod.compat.alt.GpuFormat.R32_FLOAT;
                VitrailGpuLayout.validateTexels(source, format, source.length() / 4, texelAlignment, maximumTexels);
                copies.put(stream.getKey(), source.equals(cornerSource) ? cornerCopy
                    : copy(device, encoder, source, owned, GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER));
            }
            GpuBufferSlice palette = null;
            if (this.bones != null)
            {
                if (this.bones.length() != 49152 || this.bones.length() > maximumUniformBytes || uniformAlignment < 1)
                    throw new IllegalArgumentException("Native Vitrail requires the actual 1024x3-vec4 bone block and device range");
                palette = copy(device, encoder, this.bones, owned, GpuBuffer.USAGE_UNIFORM);
            }
            return new Snapshot(this.kind, cornerCopy, this.count,
                Collections.unmodifiableMap(copies), palette);
        }
        catch (Throwable error)
        {
            for (var buffer : owned)
            {
                try { buffer.close(); }
                catch (Throwable cleanup) { error.addSuppressed(cleanup); }
            }
            throw error;
        }
    }

    private static GpuBufferSlice copy(GpuDevice device, CommandEncoder encoder, GpuBufferSlice source,
        java.util.Set<GpuBuffer> owned, int usage)
    {
        if (source.buffer().isClosed() || (source.buffer().usage() & GpuBuffer.USAGE_COPY_SRC) == 0)
            throw new IllegalArgumentException("Native Vitrail capture source lacks COPY_SRC or is closed");
        var buffer = device.createBuffer(() -> "bbs-vitrail-actor-pose", usage | GpuBuffer.USAGE_COPY_DST, source.length());
        owned.add(buffer);
        var target = buffer.slice();
        encoder.copyToBuffer(source, target);
        return target;
    }

    public record Snapshot(VitrailVertexSource.Kind kind, GpuBufferSlice corners, int count,
        Map<String, GpuBufferSlice> texels, GpuBufferSlice bones) implements AutoCloseable
    {
        @Override
        public void close()
        {
            var unique = Collections.newSetFromMap(new java.util.IdentityHashMap<GpuBuffer, Boolean>());
            unique.add(this.corners.buffer());
            this.texels.values().forEach(slice -> unique.add(slice.buffer()));
            if (this.bones != null) unique.add(this.bones.buffer());
            for (var buffer : unique) buffer.close();
        }
    }
}
