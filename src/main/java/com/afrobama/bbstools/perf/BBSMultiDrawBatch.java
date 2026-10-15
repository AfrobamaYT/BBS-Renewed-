// Coded by Afrobama/Claude — Tier 2c indirect multi-draw.
//
// Sodium-style SoA multi-draw batcher. Three parallel off-heap arrays
// hold per-sub-mesh draw parameters; a single
// glMultiDrawElementsBaseVertex(GL_TRIANGLES, ...) flushes them all in
// ONE GL call.
//
// API note (LWJGL 3.3.x): GL43.glMultiDrawElementsBaseVertex accepts an
// IntBuffer of counts, a PointerBuffer of EBO byte-offsets, and an
// IntBuffer of base-vertex offsets. We back all three by raw off-heap
// memory (MemoryUtil.nmemAlignedAlloc, 32-byte aligned) and wrap views
// over them at flush() time via MemoryUtil.memIntBuffer / memPointerBuffer.
//
// Why nmemAlignedAlloc(32, ...): keeps each SoA array on its own cache
// line and gives the GL driver well-aligned pointers (Sodium uses the
// same trick). Survives JVM lifetime — we allocate once, free never
// (single static instance per renderer, lives for the process).
//
// Capacity 64 is plenty: a BBS film with 64 distinct visible meshes is
// already extreme; if we ever overflow we grow geometrically.
//
// For the single-mesh villager crowd this batch holds exactly ONE entry
// → multi-draw degenerates to a normal draw. The big win shows up at
// 3000+ BBS replays with varied meshes.
package com.afrobama.bbstools.perf;

import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL43;
import org.lwjgl.system.MemoryUtil;

import java.nio.IntBuffer;

public final class BBSMultiDrawBatch
{
    /** Initial capacity. Grows ×2 on overflow. */
    private static final int INITIAL_CAPACITY = 64;

    /** Cache-line alignment for the SoA arrays. */
    private static final int ALIGNMENT = 32;

    /** Number of entries currently buffered (0..capacity). */
    private int n;

    /** Slots allocated. */
    private int capacity;

    /** Pointer to int[capacity] of per-draw index byte-offsets into the EBO.
     *  Stored as native int values; the GL call interprets them as void*. */
    private long pElementPointer;

    /** Pointer to int[capacity] of per-draw index counts. */
    private long pElementCount;

    /** Pointer to int[capacity] of per-draw base-vertex offsets. */
    private long pBaseVertex;

    public BBSMultiDrawBatch()
    {
        this.capacity = INITIAL_CAPACITY;
        this.n = 0;
        /* pElementPointer slots are pointer-sized (8 bytes on x86_64);
         *  the other two are 4-byte ints. */
        this.pElementPointer = MemoryUtil.nmemAlignedAlloc(ALIGNMENT, (long) capacity * PointerBuffer.POINTER_SIZE);
        this.pElementCount   = MemoryUtil.nmemAlignedAlloc(ALIGNMENT, (long) capacity * Integer.BYTES);
        this.pBaseVertex     = MemoryUtil.nmemAlignedAlloc(ALIGNMENT, (long) capacity * Integer.BYTES);
    }

    /** Append one sub-mesh's draw parameters to the batch.
     *  @param firstIndex byte-offset into the EBO of the first index
     *  @param indexCount number of indices for this sub-mesh
     *  @param baseVertex value to add to every index when fetching the vertex */
    public void add(int firstIndex, int indexCount, int baseVertex)
    {
        if (n >= capacity)
        {
            grow();
        }
        long off = (long) n * Integer.BYTES;
        /* GL_UNSIGNED_INT path → each EBO index is 4 bytes, so the
         *  driver-visible "indices" entry is the BYTE offset firstIndex × 4.
         *  Stored as a native int (we slot it where the PointerBuffer view
         *  will read it as a void* — on x86_64 LWJGL's PointerBuffer treats
         *  4-byte slots transparently for tightly-packed arrays). For full
         *  64-bit cleanliness we use memPutAddress below in the flush path. */
        MemoryUtil.memPutInt(pElementCount + off, indexCount);
        MemoryUtil.memPutInt(pBaseVertex + off, baseVertex);
        /* indices are void* — write a pointer-sized slot. */
        long byteOff = (long) firstIndex * Integer.BYTES;
        /* PointerBuffer view assumes POINTER_SIZE per slot. Re-base
         *  pElementPointer indexing accordingly. */
        MemoryUtil.memPutAddress(pElementPointer + (long) n * PointerBuffer.POINTER_SIZE, byteOff);
        n++;
    }

    /** Issue one glMultiDrawElementsBaseVertex spanning every queued sub-mesh.
     *  Caller is responsible for binding the appropriate VAO + program. */
    public void flush()
    {
        if (n == 0) return;
        IntBuffer counts        = MemoryUtil.memIntBuffer(pElementCount, n);
        IntBuffer baseVertices  = MemoryUtil.memIntBuffer(pBaseVertex, n);
        PointerBuffer indices   = MemoryUtil.memPointerBuffer(pElementPointer, n);
        GL43.glMultiDrawElementsBaseVertex(GL11.GL_TRIANGLES, counts, GL11.GL_UNSIGNED_INT, indices, baseVertices);
        BBSPerfConfig.multiDrawCalls++;
        BBSPerfConfig.multiDrawCommandsConsolidated += n;
    }

    /** Reset n to 0; reuses the off-heap arrays. */
    public void clear()
    {
        n = 0;
    }

    /** Current number of queued sub-mesh draws. */
    public int size()
    {
        return n;
    }

    private void grow()
    {
        int newCap = capacity * 2;
        long newElemPtr = MemoryUtil.nmemAlignedAlloc(ALIGNMENT, (long) newCap * PointerBuffer.POINTER_SIZE);
        long newElemCnt = MemoryUtil.nmemAlignedAlloc(ALIGNMENT, (long) newCap * Integer.BYTES);
        long newBaseVtx = MemoryUtil.nmemAlignedAlloc(ALIGNMENT, (long) newCap * Integer.BYTES);
        MemoryUtil.memCopy(pElementPointer, newElemPtr, (long) capacity * PointerBuffer.POINTER_SIZE);
        MemoryUtil.memCopy(pElementCount,   newElemCnt, (long) capacity * Integer.BYTES);
        MemoryUtil.memCopy(pBaseVertex,     newBaseVtx, (long) capacity * Integer.BYTES);
        MemoryUtil.nmemAlignedFree(pElementPointer);
        MemoryUtil.nmemAlignedFree(pElementCount);
        MemoryUtil.nmemAlignedFree(pBaseVertex);
        pElementPointer = newElemPtr;
        pElementCount   = newElemCnt;
        pBaseVertex     = newBaseVtx;
        capacity = newCap;
    }

}
