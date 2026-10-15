// Coded by Afrobama/Claude — Tier 2a Sodium-style worker pool.
//
// Per-worker scratch context. Mirrors Sodium's ChunkBuildContext: one
// instance per worker thread, owned by that thread for its full lifetime,
// reset between jobs. Holds the off-heap vertex bake buffer + small
// per-job scratch arrays + reusable JOML matrices so the worker can run
// CPU-bake math without per-job allocations.
//
// STRIDE: vertex stride in bytes (defaults to 54 — the Iris-extended
// entity format CrowdIrisWrappedRenderer bakes into). Total off-heap
// size = STRIDE * 4096 = 216 KiB / worker. With max-10 workers that's
// ~2 MiB pool-wide off-heap — negligible.
//
// Lifetime: created lazily by BBSEntityWorkerPool when the worker thread
// starts. Released via close() on pool shutdown.
package com.afrobama.bbstools.perf;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

public final class BBSEntityBuildContext implements AutoCloseable
{
    public static final int STRIDE = 54;
    public static final int DEFAULT_CAPACITY_VERTS = 4096;

    /** Off-heap scratch — direct ByteBuffer wrapping a memAlloc'd block. */
    public final ByteBuffer scratch;

    /** Bone-pivot scratch — 6 floats per CrowdMeshLoader.BONE_COUNT slot. */
    public final float[] boneAng = new float[6];

    /** Reusable per-job transform matrix. */
    public final Matrix4f tmpMat = new Matrix4f();

    /** Reusable per-job normal matrix. */
    public final Matrix3f tmpNormal = new Matrix3f();

    /** Worker id (for log tagging). */
    public final int workerId;

    private boolean closed;

    public BBSEntityBuildContext(int workerId)
    {
        this.workerId = workerId;
        /* memAlloc → direct ByteBuffer backed by an LWJGL-tracked
         * malloc. Must be freed via memFree on shutdown. */
        this.scratch = MemoryUtil.memAlloc(STRIDE * DEFAULT_CAPACITY_VERTS);
    }

    /** Called between jobs by the worker loop. Cheap — just clears
     *  the small scratch state. The big off-heap buffer is rewritten
     *  in-place per job and does not need explicit clearing. */
    public void reset()
    {
        for (int i = 0; i < boneAng.length; i++) boneAng[i] = 0F;
        tmpMat.identity();
        tmpNormal.identity();
        scratch.clear();
    }

    @Override
    public void close()
    {
        if (closed) return;
        closed = true;
        if (scratch != null) MemoryUtil.memFree(scratch);
    }

}
