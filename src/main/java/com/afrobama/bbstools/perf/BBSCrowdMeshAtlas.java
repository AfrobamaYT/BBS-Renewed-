// Coded by Afrobama/Claude — Tier 2c indirect multi-draw.
//
// Shared-VBO+EBO atlas for the BBS crowd path. Lets us consolidate all
// unique crowd meshes onto a single VBO+EBO pair so a single
// glMultiDrawElementsBaseVertex covers every visible sub-mesh in ONE GL
// call (see BBSMultiDrawBatch).
//
// PER-SUB-MESH RECORDS: registerMesh() appends vertex + index data to
// the shared buffers and stores a SubMeshRecord (firstIndex / indexCount
// / baseVertex) keyed by mesh ID. lookup(id) retrieves it later.
//
// GL_STATIC_DRAW: crowd meshes don't change at runtime. We allocate
// buffers up front (lazy on first registerMesh call) at a generous
// initial size and grow as needed via re-alloc + copy. Production code
// would pre-size from a manifest scan; for now, geometric growth handles
// any size.
//
// VERTEX FORMAT: matches CrowdIrisGpuInstancedRenderer's 24-byte
// per-vertex layout exactly — pos(12) + uv(8) + boneId(4). Caller passes
// raw FloatBuffer-of-floats matching that interleave; we appendBytes.
//
// This is the KEY prerequisite Sodium relies on: without one shared VBO
// you can't multi-draw across mesh boundaries.
package com.afrobama.bbstools.perf;

import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;

public final class BBSCrowdMeshAtlas
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/perf/multidraw-atlas");

    /** Per-vertex stride (must match CrowdIrisGpuInstancedRenderer). */
    private static final int VERTEX_STRIDE_BYTES = 24;

    /** Bytes per index (GL_UNSIGNED_INT). */
    private static final int INDEX_BYTES = 4;

    /** Initial VBO size in bytes (4 MiB) — grows ×2 on overflow. */
    private static final int INITIAL_VBO_BYTES = 4 * 1024 * 1024;

    /** Initial EBO size in bytes (1 MiB) — grows ×2 on overflow. */
    private static final int INITIAL_EBO_BYTES = 1 * 1024 * 1024;

    /** Singleton (lazy). */
    private static volatile BBSCrowdMeshAtlas INSTANCE;

    public static BBSCrowdMeshAtlas get()
    {
        BBSCrowdMeshAtlas inst = INSTANCE;
        if (inst == null)
        {
            synchronized (BBSCrowdMeshAtlas.class)
            {
                inst = INSTANCE;
                if (inst == null)
                {
                    inst = new BBSCrowdMeshAtlas();
                    INSTANCE = inst;
                }
            }
        }
        return inst;
    }

    /** Per-sub-mesh draw parameters returned by registerMesh / lookup. */
    public static final class SubMeshRecord
    {
        public final int firstIndex;   // first-index OFFSET (in indices) into the shared EBO
        public final int indexCount;   // number of indices for this sub-mesh
        public final int baseVertex;   // base-vertex offset into the shared VBO (in vertices)

        public SubMeshRecord(int firstIndex, int indexCount, int baseVertex)
        {
            this.firstIndex = firstIndex;
            this.indexCount = indexCount;
            this.baseVertex = baseVertex;
        }
    }

    /** GL handles. 0 until initGl() runs. */
    private int vao;
    private int vbo;
    private int ebo;

    /** Bytes / indices currently consumed in each shared buffer. */
    private int vboCursorBytes;
    private int eboCursorIndices;

    /** Capacity (in bytes) of each buffer. */
    private int vboCapacityBytes;
    private int eboCapacityBytes;

    /** Keyed by mesh ID. */
    private final Map<String, SubMeshRecord> records = new HashMap<>();

    private boolean glReady;

    private BBSCrowdMeshAtlas() {}

    /** Lazily allocates the shared VBO+EBO+VAO. Must be called on the
     *  render thread (issues GL calls). */
    private void initGl()
    {
        if (glReady) return;
        vao = GL30.glGenVertexArrays();
        vbo = GL15.glGenBuffers();
        ebo = GL15.glGenBuffers();

        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, (long) INITIAL_VBO_BYTES, GL15.GL_STATIC_DRAW);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, (long) INITIAL_EBO_BYTES, GL15.GL_STATIC_DRAW);
        GL30.glBindVertexArray(0);

        vboCapacityBytes = INITIAL_VBO_BYTES;
        eboCapacityBytes = INITIAL_EBO_BYTES;
        vboCursorBytes = 0;
        eboCursorIndices = 0;
        glReady = true;
        LOG.info("BBSCrowdMeshAtlas init OK: vao={} vbo={} ebo={} vboCap={} eboCap={}",
            vao, vbo, ebo, INITIAL_VBO_BYTES, INITIAL_EBO_BYTES);
    }

    /** Append a mesh to the shared buffers, returning the
     *  SubMeshRecord that can later be used with BBSMultiDrawBatch.add(...).
     *  Idempotent — re-registering the same id returns the cached record.
     *  @param id   unique mesh identifier (BBS replay model path, villager skin, etc.)
     *  @param vertexData  24 B/vert interleaved buffer (matches CrowdIris layout)
     *  @param indexData   indices RELATIVE to this mesh (0..vertexCount-1) */
    public SubMeshRecord registerMesh(String id, FloatBuffer vertexData, IntBuffer indexData)
    {
        SubMeshRecord existing = records.get(id);
        if (existing != null) return existing;

        if (!glReady) initGl();

        int vertexCount = vertexData.remaining() / (VERTEX_STRIDE_BYTES / 4);
        int indexCount  = indexData.remaining();

        long needVboBytes = (long) (vboCursorBytes + vertexCount * VERTEX_STRIDE_BYTES);
        long needEboBytes = (long) (eboCursorIndices + indexCount) * INDEX_BYTES;
        while (needVboBytes > vboCapacityBytes) growVbo();
        while (needEboBytes > eboCapacityBytes) growEbo();

        /* Upload vertex data via glBufferSubData. */
        ByteBuffer vbuf = ByteBuffer.allocateDirect(vertexCount * VERTEX_STRIDE_BYTES)
            .order(ByteOrder.nativeOrder());
        FloatBuffer vbufF = vbuf.asFloatBuffer();
        vbufF.put(vertexData);
        vbuf.position(0);
        vbuf.limit(vertexCount * VERTEX_STRIDE_BYTES);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, (long) vboCursorBytes, vbuf);

        /* Upload index data RELATIVE to this sub-mesh — the base-vertex
         *  field is what makes the GL driver add the per-mesh vertex
         *  offset at fetch time, so we leave indices unchanged. */
        ByteBuffer ibuf = ByteBuffer.allocateDirect(indexCount * INDEX_BYTES)
            .order(ByteOrder.nativeOrder());
        IntBuffer ibufI = ibuf.asIntBuffer();
        ibufI.put(indexData);
        ibuf.position(0);
        ibuf.limit(indexCount * INDEX_BYTES);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
        GL15.glBufferSubData(GL15.GL_ELEMENT_ARRAY_BUFFER, (long) eboCursorIndices * INDEX_BYTES, ibuf);

        int baseVertex = vboCursorBytes / VERTEX_STRIDE_BYTES;
        int firstIndex = eboCursorIndices;
        vboCursorBytes  += vertexCount * VERTEX_STRIDE_BYTES;
        eboCursorIndices += indexCount;

        SubMeshRecord rec = new SubMeshRecord(firstIndex, indexCount, baseVertex);
        records.put(id, rec);
        LOG.info("BBSCrowdMeshAtlas registered id='{}' firstIndex={} indexCount={} baseVertex={} (now {} sub-meshes)",
            id, firstIndex, indexCount, baseVertex, records.size());
        return rec;
    }

    /** @return the SubMeshRecord for {@code id}, or {@code null} if not registered. */
    public SubMeshRecord lookup(String id)
    {
        return records.get(id);
    }

    /** @return shared VAO handle (0 until first registerMesh). */
    public int vao() { return vao; }

    /** @return shared VBO handle. */
    public int vbo() { return vbo; }

    /** @return shared EBO handle. */
    public int ebo() { return ebo; }

    /** @return number of distinct sub-meshes currently in the atlas. */
    public int size() { return records.size(); }

    private void growVbo()
    {
        int newCap = vboCapacityBytes * 2;
        int newVbo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, newVbo);
        GL15.glBufferData(GL31.GL_COPY_WRITE_BUFFER, (long) newCap, GL15.GL_STATIC_DRAW);
        GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, vbo);
        GL31.glCopyBufferSubData(GL31.GL_COPY_READ_BUFFER, GL31.GL_COPY_WRITE_BUFFER, 0L, 0L, (long) vboCursorBytes);
        GL15.glDeleteBuffers(vbo);
        vbo = newVbo;
        vboCapacityBytes = newCap;
        LOG.info("BBSCrowdMeshAtlas VBO grew to {} bytes", newCap);
    }

    private void growEbo()
    {
        int newCap = eboCapacityBytes * 2;
        int newEbo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, newEbo);
        GL15.glBufferData(GL31.GL_COPY_WRITE_BUFFER, (long) newCap, GL15.GL_STATIC_DRAW);
        GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, ebo);
        GL31.glCopyBufferSubData(GL31.GL_COPY_READ_BUFFER, GL31.GL_COPY_WRITE_BUFFER, 0L,
            0L, (long) eboCursorIndices * INDEX_BYTES);
        GL15.glDeleteBuffers(ebo);
        ebo = newEbo;
        eboCapacityBytes = newCap;
        LOG.info("BBSCrowdMeshAtlas EBO grew to {} bytes", newCap);
    }

}
