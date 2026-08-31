// Coded by Afrobama/Claude — Tier 1c BBS vertex accessor finalization.
//
// Per-(CubicModel, Texture) GPU resource bundle for the cubic-form
// instanced replay path. Sister to VeilSkinnedBatch — that one carries
// per-vertex skinning weights + a 128-mat4 bones SSBO per instance.
// Cubic models have NO per-vertex weights: every vertex is rigid to its
// owning group (cube), so the per-instance encoding collapses to ONE
// model-matrix per instance. We store those matrices in a single SSBO
// that the vertex shader reads via gl_InstanceID, and the instance VBO
// carries only (ssboBase uint, packedLight uint, argbColor uint, pad).
//
// Concrete GL allocation matches the VeilSkinnedBatch pattern:
//   - GL_MAP_PERSISTENT_BIT | GL_MAP_COHERENT_BIT mapping (no flush)
//   - VAO via foundry.veil.api.client.render.vertex.VertexArray
//   - Draw via VertexArray.drawInstancedWithRenderType
//
// VERTEX-COUNT (2026-05-18): the cubic data API
// (mchorse.bbs_mod.cubic.data.model.{Model,ModelGroup,ModelMesh,ModelCube})
// is entirely public — no accessor mixin needed. We walk the model's
// ordered groups and sum:
//   - per ModelCube  : 6 quads × 6 triangle-vertices = 36 verts
//   - per ModelMesh  : baseData.vertices.size() (already a flat tri list)
// The static-VBO upload (allocateStaticVbo) is still a no-op placeholder
// until the actual interleaved pos|uv|norm|groupId streaming lands; once
// vertex-count > 0, build() proceeds and the VAO + SSBO + instance VBO
// are allocated. draw() will still no-op when staticVbo is zero (clean
// guard inside draw()).
package com.afrobama.bbstools.client.bbs.veil;

import com.afrobama.bbstools.perf.BBSPerfConfig;

import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelCube;
import mchorse.bbs_mod.cubic.data.model.ModelData;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.data.model.ModelMesh;
import mchorse.bbs_mod.cubic.data.model.ModelQuad;
import mchorse.bbs_mod.cubic.data.model.ModelVertex;

import org.joml.Matrix4f;
import org.joml.Matrix3f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL31C;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.opengl.GL43C;
import org.lwjgl.opengl.GL44C;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class VeilCubicBatch implements AutoCloseable
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/bbs-veil/cubic-batch");

    /** Hard cap per (model, texture) group. 3000 matches VeilSkinnedBatch
     *  so /bbsdebug reports are directly comparable. */
    public  static final int MAX_INSTANCES   = 3000;
    /** Per-instance VBO stride: uint ssboBase (4) + uint packedLight (4)
     *  + uint argbColor (4) + uint pad (4) = 16 bytes. Compact vs
     *  VeilSkinnedBatch's 96-byte stride — the win is bandwidth saved
     *  per instance across the upload + vertex-fetch path. */
    private static final int INSTANCE_STRIDE = 16;
    private static final int MAT4_BYTES      = 16 * Float.BYTES;   /* 64 */

    /* Persistent-mapped flags — GL 4.4 ARB_buffer_storage. */
    private static final int PERSISTENT_FLAGS =
        GL44C.GL_MAP_WRITE_BIT | GL44C.GL_MAP_PERSISTENT_BIT | GL44C.GL_MAP_COHERENT_BIT;

    /* One-shot diagnostic gate: log the first cubic flush observed across
     * ALL batches, then stay silent so 200-replay films don't spam. */
    private static volatile boolean BBSTOOLS$FIRST_FLUSH_LOGGED = false;

    /* One-shot diagnostic gate for the mesh-upload path. */
    private static volatile boolean BBSTOOLS$FIRST_MESH_UPLOAD_LOGGED = false;

    /* Mesh-upload identity cache: keyed by the cubic Model instance.
     * If we get the same Model object on a subsequent build (same group
     * key being re-resolved after evict), the VBO bytes don't need
     * re-uploading. WeakHashMap so models can be GC'd. Per-VeilCubicBatch
     * is also fine, but a single static map lets us share across re-
     * builds of the SAME group key if the renderer ever swaps batches. */
    private static final Map<Object, Boolean> MESH_UPLOADED =
        java.util.Collections.synchronizedMap(new WeakHashMap<>());

    /* Index buffer object for the static mesh. Sequential 0..vertexCount-1
     * since we emit triangle lists (no shared verts). Kept around so the
     * EBO is available if we ever switch the draw call from glDrawArrays
     * to glDrawElementsInstanced; today VertexArray.drawInstancedWithRenderType
     * uses drawArrays for unbound element-buffers (matches VeilSkinnedBatch). */
    private int staticEbo;

    /* GL state — Tier 1c r31: fully raw GL, no Veil VertexArray wrapper.
     * Reason: Veil's VertexArray.drawInstancedWithRenderType binds its own
     * VAO internally, dropping our static attribs (loc 0..3) and producing
     * garbage SSBO base indices → GPU TDR. Owning one VAO ourselves with
     * BOTH static (loc 0..3, divisor 0) and per-instance (loc 4..6,
     * divisor 1) attribs avoids the conflict. */
    private int staticVbo;
    private int instanceVbo;
    private int matrixBankSsbo;
    private ByteBuffer instanceMap;
    private ByteBuffer matrixBankMap;

    // Coded by Afrobama/Claude — Tier 1c VAO fix r31 (Option A: raw GL throughout).
    /* Single raw VAO that captures BOTH the static-mesh VBO+EBO binding
     * (loc 0..3, divisor 0) AND the per-instance VBO (loc 4..6, divisor 1).
     * We never hand this VAO to Veil — draw() binds it, issues
     * glDrawElementsInstanced directly, unbinds. */
    private int vao = 0;

    /* Shader program compiled from disk (r31). Static — shared across
     * every VeilCubicBatch instance. 0 until first build. */
    private static volatile int cubicShaderProgram = 0;
    /* Cached uniform locations for the shared program. */
    private static int uProjMat = -1;
    private static int uModelViewMat = -1;
    private static int uNormalMat = -1;
    private static int uSampler0 = -1;

    /* r31 auto-disable: on first draw-time throw, flip
     * BBSPerfConfig.ENABLE_VEIL_CUBIC_BATCH = false so subsequent frames
     * skip this path entirely (prevents TDR loops). */
    private static volatile boolean BBSTOOLS$AUTO_DISABLED = false;

    /* r34: one-shot gate so BBSGpuDebug.captureNow only fires once before the
     *  first TDR-causing draw. Coded by Afrobama/Claude. */
    private static volatile boolean BBSTOOLS$R34_AUTO_CAPTURED = false;

    private int vertexCount;
    private VeilCubicBatchRenderer.GroupKey key;
    private boolean built;
    private int lastN;

    private VeilCubicBatch() {}

    /** One-time per (CubicModel, Texture). Allocates SSBO + instance VBO
     *  + VAO. The static mesh VBO is allocated only if a vertex source
     *  can be derived from the cubic model; otherwise built stays false
     *  and draw() no-ops cleanly. */
    public static VeilCubicBatch build(VeilCubicBatchRenderer.GroupKey key)
    {
        VeilCubicBatch b = new VeilCubicBatch();
        b.key = key;
        try
        {
            int verts = extractCubicVertexCount(key.cubicModel);
            if (verts <= 0)
            {
                /* Vertex source not yet available — leave built=false,
                 * caller flushes to vanilla path. Don't spam logs every
                 * frame; the dispatcher already throttles. */
                return b;
            }
            b.vertexCount = verts;

            b.allocateInstanceVbo();
            b.allocateMatrixBankSsbo();
            /* allocateStaticVbo also builds the unified VAO with per-instance
             * attribs wired in (r31 — Veil VertexArray removed). It needs
             * instanceVbo to exist first, hence the call order swap. */
            b.allocateStaticVbo(key.cubicModel);

            /* Resolve shader program lazily (first build pays the cost). */
            getCubicShaderProgram();

            b.built = true;
            LOG.info("VeilCubicBatch.build OK: model={} verts={} cap={}",
                key.cubicModel, b.vertexCount, MAX_INSTANCES);
        }
        catch (Throwable t)
        {
            LOG.error("VeilCubicBatch.build failed for {}", key, t);
            b.releaseAll();
            b.built = false;
        }
        return b;
    }

    /* ------------- allocation helpers ------------- */

    /** Returns the total triangle-vertex count for a BBS cubic Model.
     *  The cubic data API exposes every relevant field as public, so this
     *  is a direct walk — no @Accessor mixin needed.
     *
     *  Per group:
     *    - Each ModelCube → 6 quad faces × 6 triangle-verts = 36 verts
     *      (matches the layout produced by ModelCube.generateQuads()).
     *    - Each ModelMesh → baseData.vertices.size(): the loader already
     *      stores a flat triangle list (positions, uvs, normals all keyed
     *      by index, see ModelMesh.fromData triangle-normal computation
     *      that walks vertices in groups of 3).
     *
     *  Returns 0 for null or non-Model inputs so build() bails cleanly
     *  back to the vanilla render path. */
    private static int extractCubicVertexCount(Object cubicModel)
    {
        if (!(cubicModel instanceof Model model)) return 0;
        int total = 0;
        try
        {
            for (ModelGroup group : model.getOrderedGroups())
            {
                if (group == null) continue;
                /* Cubes — each one's 6-face quad layout = 36 tri verts. */
                List<ModelCube> cubes = group.cubes;
                if (cubes != null) total += cubes.size() * 36;
                /* Meshes — baseData.vertices is already flat triangles. */
                List<ModelMesh> meshes = group.meshes;
                if (meshes != null)
                {
                    for (ModelMesh mesh : meshes)
                    {
                        if (mesh == null || mesh.baseData == null) continue;
                        if (mesh.baseData.vertices != null)
                        {
                            total += mesh.baseData.vertices.size();
                        }
                    }
                }
            }
        }
        catch (Throwable t)
        {
            LOG.warn("extractCubicVertexCount walk threw — treating model as empty", t);
            return 0;
        }
        return total;
    }

    private void allocateStaticVbo(Object cubicModel)
    {
        // Coded by Afrobama/Claude — Tier 1c cubic mesh upload (real body, not scaffold).
        /* Companion to extractCubicVertexCount: build the interleaved
         * pos|uv|norm|groupId stream (36 B/vertex) and the matching
         * sequential index buffer, then glBufferData both. Caches per
         * Model instance — re-build of the same key is a no-op upload
         * (returns early; the staticVbo/staticEbo remain valid). */
        if (!(cubicModel instanceof Model model))
        {
            staticVbo = 0;
            staticEbo = 0;
            return;
        }
        buildCubicMeshVBO(model);
    }

    /** Walks the cubic Model's groups, packs all cube quads + mesh
     *  triangles into a single interleaved VBO at 36-byte stride,
     *  generates a sequential index buffer, and uploads both. The
     *  vertex layout is identical to what buildVertexArray() declares:
     *    [0]  vec3 pos      offset 0
     *    [1]  vec2 uv       offset 12
     *    [2]  vec3 normal   offset 20
     *    [3]  uint groupId  offset 32
     *  Total: 36 bytes. Uses static-draw — the mesh is rigid relative
     *  to its groups; per-frame bone transforms are applied in the
     *  vertex shader from the matrix-bank SSBO indexed by groupId. */
    private void buildCubicMeshVBO(Model model)
    {
        if (this.vertexCount <= 0)
        {
            staticVbo = 0;
            staticEbo = 0;
            return;
        }

        /* Allocate native buffers off-heap so we can pass them directly
         * to glBufferData without a JVM heap-copy intermediate. */
        final int vbBytes = this.vertexCount * 36;
        final int ibBytes = this.vertexCount * 4;
        ByteBuffer vbo = MemoryUtil.memAlloc(vbBytes).order(ByteOrder.nativeOrder());
        ByteBuffer ibo = MemoryUtil.memAlloc(ibBytes).order(ByteOrder.nativeOrder());
        try
        {
            int written = packModelVertices(model, vbo);
            if (written != this.vertexCount)
            {
                /* Walk landed fewer verts than the count probe — adjust
                 * down so the draw call doesn't read garbage. Cubic
                 * loaders sometimes leave a cube with all-null UV faces
                 * (no quads generated) and our 36-per-cube probe was
                 * optimistic. The actual emitted count is authoritative. */
                LOG.warn("[tier1c] vertex-count probe ({}) > emitted ({}) for {} — clamping",
                    this.vertexCount, written, model);
                this.vertexCount = written;
            }
            /* Sequential index list 0..vertexCount-1. */
            ibo.clear();
            for (int i = 0; i < this.vertexCount; i++) ibo.putInt(i);
            ibo.flip();
            vbo.position(0).limit(this.vertexCount * 36);

            /* r39 fine-grained syncCheck per step — Coded by Afrobama/Claude.
             *  Each glGetError after a discrete operation pinpoints which
             *  exact call (if any) generates a driver error. Tags read
             *  directly in latest.log; zero install needed. */
            com.afrobama.bbstools.client.debug.master.gl.GlIntrospect.syncCheck("build:before-VBO-gen");

            /* Upload VBO. */
            staticVbo = GL15C.glGenBuffers();
            com.afrobama.bbstools.client.debug.master.gl.GlIntrospect.syncCheck("build:after-VBO-gen vbo=" + staticVbo);
            GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, staticVbo);
            com.afrobama.bbstools.client.debug.master.gl.GlIntrospect.syncCheck("build:after-VBO-bind");
            GL15C.glBufferData(GL15C.GL_ARRAY_BUFFER, vbo, GL15C.GL_STATIC_DRAW);
            com.afrobama.bbstools.client.debug.master.gl.GlIntrospect.syncCheck("build:after-VBO-bufferdata bytes=" + vbBytes);
            GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, 0);

            /* Upload EBO. */
            staticEbo = GL15C.glGenBuffers();
            com.afrobama.bbstools.client.debug.master.gl.GlIntrospect.syncCheck("build:after-EBO-gen ebo=" + staticEbo);
            GL15C.glBindBuffer(GL15C.GL_ELEMENT_ARRAY_BUFFER, staticEbo);
            GL15C.glBufferData(GL15C.GL_ELEMENT_ARRAY_BUFFER, ibo, GL15C.GL_STATIC_DRAW);
            com.afrobama.bbstools.client.debug.master.gl.GlIntrospect.syncCheck("build:after-EBO-bufferdata bytes=" + ibBytes);
            GL15C.glBindBuffer(GL15C.GL_ELEMENT_ARRAY_BUFFER, 0);

            /* r31 unified VAO setup (Option A — fully raw GL, no Veil
             * wrapper). The VAO captures:
             *   - static VBO @ binding 0, divisor 0, loc 0..3
             *   - element buffer
             *   - per-instance VBO @ binding 1, divisor 1, loc 4..6
             * After this, draw() just binds the VAO and issues
             * glDrawElementsInstanced — no other attrib setup needed.
             * Coded by Afrobama/Claude — Tier 1c VAO fix r31. */
            if (vao == 0) {
                vao = GL30C.glGenVertexArrays();
                com.afrobama.bbstools.client.debug.master.gl.GlIntrospect.syncCheck("build:after-VAO-gen vao=" + vao);
            }
            GL30C.glBindVertexArray(vao);
            com.afrobama.bbstools.client.debug.master.gl.GlIntrospect.syncCheck("build:after-VAO-bind");

            /* --- Static attribs (loc 0..3) bound to staticVbo. --- */
            GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, staticVbo);
            GL15C.glBindBuffer(GL15C.GL_ELEMENT_ARRAY_BUFFER, staticEbo);
            com.afrobama.bbstools.client.debug.master.gl.GlIntrospect.syncCheck("build:after-attribs-buf-bind");
            // Position (loc 0): 3 floats at offset 0
            GL20C.glVertexAttribPointer(0, 3, GL11C.GL_FLOAT, false, 36, 0L);
            GL20C.glEnableVertexAttribArray(0);
            GL33C.glVertexAttribDivisor(0, 0);
            com.afrobama.bbstools.client.debug.master.gl.GlIntrospect.syncCheck("build:after-loc0-Position");
            // UV0 (loc 1): 2 floats at offset 12
            GL20C.glVertexAttribPointer(1, 2, GL11C.GL_FLOAT, false, 36, 12L);
            GL20C.glEnableVertexAttribArray(1);
            GL33C.glVertexAttribDivisor(1, 0);
            com.afrobama.bbstools.client.debug.master.gl.GlIntrospect.syncCheck("build:after-loc1-UV");
            // Normal (loc 2): 3 floats at offset 20
            GL20C.glVertexAttribPointer(2, 3, GL11C.GL_FLOAT, false, 36, 20L);
            GL20C.glEnableVertexAttribArray(2);
            GL33C.glVertexAttribDivisor(2, 0);
            com.afrobama.bbstools.client.debug.master.gl.GlIntrospect.syncCheck("build:after-loc2-Normal");
            // GroupId (loc 3): uint at offset 32 (integer attrib, NOT normalized)
            GL30C.glVertexAttribIPointer(3, 1, GL11C.GL_UNSIGNED_INT, 36, 32L);
            GL20C.glEnableVertexAttribArray(3);
            GL33C.glVertexAttribDivisor(3, 0);
            com.afrobama.bbstools.client.debug.master.gl.GlIntrospect.syncCheck("build:after-loc3-GroupId");

            /* --- Per-instance attribs (loc 4..6) bound to instanceVbo,
             * stride INSTANCE_STRIDE=16, divisor 1. instanceVbo must
             * already exist — build() now allocates it before this call. --- */
            if (instanceVbo != 0) {
                GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, instanceVbo);
                com.afrobama.bbstools.client.debug.master.gl.GlIntrospect.syncCheck("build:after-instanceVBO-bind id=" + instanceVbo);
                // SsboBase (loc 4): uint at offset 0
                GL30C.glVertexAttribIPointer(4, 1, GL11C.GL_UNSIGNED_INT, INSTANCE_STRIDE, 0L);
                GL20C.glEnableVertexAttribArray(4);
                GL33C.glVertexAttribDivisor(4, 1);
                com.afrobama.bbstools.client.debug.master.gl.GlIntrospect.syncCheck("build:after-loc4-SsboBase");
                // PackedLight (loc 5): uint at offset 4
                GL30C.glVertexAttribIPointer(5, 1, GL11C.GL_UNSIGNED_INT, INSTANCE_STRIDE, 4L);
                GL20C.glEnableVertexAttribArray(5);
                GL33C.glVertexAttribDivisor(5, 1);
                com.afrobama.bbstools.client.debug.master.gl.GlIntrospect.syncCheck("build:after-loc5-PackedLight");
                // InstanceColor (loc 6): 4 unsigned-bytes at offset 8, UNORM8 normalized
                GL20C.glVertexAttribPointer(6, 4, GL11C.GL_UNSIGNED_BYTE, true, INSTANCE_STRIDE, 8L);
                GL20C.glEnableVertexAttribArray(6);
                GL33C.glVertexAttribDivisor(6, 1);
                com.afrobama.bbstools.client.debug.master.gl.GlIntrospect.syncCheck("build:after-loc6-InstanceColor");
            }

            GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, 0);
            GL30C.glBindVertexArray(0);
            com.afrobama.bbstools.client.debug.master.gl.GlIntrospect.syncCheck("build:after-cleanup-unbind");

            MESH_UPLOADED.put(model, Boolean.TRUE);
            if (!BBSTOOLS$FIRST_MESH_UPLOAD_LOGGED)
            {
                BBSTOOLS$FIRST_MESH_UPLOAD_LOGGED = true;
                LOG.info("[tier1c] first cubic mesh VBO uploaded: model={} verts={} bytes={} (vbo+ebo)",
                    model, this.vertexCount, vbBytes + ibBytes);
                LOG.info("[r33-vertex-stats] NaN={}, Inf={}, out-of-range={} (out of {} verts) — if non-zero, GPU TDR is from bad vertex data",
                    BBSTOOLS$R33_NAN_COUNT, BBSTOOLS$R33_INF_COUNT, BBSTOOLS$R33_OUT_OF_RANGE, this.vertexCount);

                /* r39 2026-05-18 — Coded by Afrobama/Claude. Sync-check
                 *  after upload + dump full GL state on this first build.
                 *  If the upload triggers a driver-side validation issue
                 *  it'll surface here, attributed to the right tag,
                 *  instead of cascading into the next vanilla draw. */
                try
                {
                    com.afrobama.bbstools.client.debug.master.gl.GlIntrospect.syncCheck("after-cubic-vbo-upload");
                    com.afrobama.bbstools.client.debug.master.gl.GlIntrospect.dumpFullGlState("after-cubic-vbo-upload");
                    com.afrobama.bbstools.client.debug.master.external.ApiTraceBridge.mark("[bbstools] cubic VBO+EBO uploaded");
                }
                catch (Throwable ig) {}
            }
        }
        catch (Throwable t)
        {
            LOG.error("[tier1c] buildCubicMeshVBO failed for {} — falling back to vanilla path", model, t);
            if (staticVbo != 0) { GL15C.glDeleteBuffers(staticVbo); staticVbo = 0; }
            if (staticEbo != 0) { GL15C.glDeleteBuffers(staticEbo); staticEbo = 0; }
        }
        finally
        {
            MemoryUtil.memFree(vbo);
            MemoryUtil.memFree(ibo);
        }
    }

    /** Pack every group's cubes + meshes into the interleaved byte buffer.
     *  Returns the number of vertices actually written. Position stride
     *  in the buffer is assumed to be 36 bytes; the caller sized vbo
     *  exactly for vertexCount verts. Rotation/pivot are NOT baked in
     *  here — the shader applies the per-group bone matrix from the
     *  SSBO, which already contains the group's accumulated transform
     *  including the local rotation. Vertices land in cube-local space
     *  (post divide-by-16) which is what the shader expects. */
    private static int packModelVertices(Model model, ByteBuffer vbo)
    {
        int written = 0;
        final List<ModelGroup> groups = model.getOrderedGroups();
        if (groups == null) return 0;

        for (int gi = 0; gi < groups.size(); gi++)
        {
            ModelGroup group = groups.get(gi);
            if (group == null) continue;
            /* Group index drives the per-vertex groupId attribute, which
             * the vsh uses to look up matrixBank[ssboBase + groupId]. */
            final int groupId = (group.index >= 0) ? group.index : gi;

            /* ---- Cubes: walk pre-generated quads (already in render
             * space, UVs already in 0..1 because generateQuads divides
             * by tw/th). Each quad → 2 triangles → 6 verts. */
            List<ModelCube> cubes = group.cubes;
            if (cubes != null)
            {
                for (ModelCube cube : cubes)
                {
                    if (cube == null || cube.quads == null) continue;
                    for (ModelQuad quad : cube.quads)
                    {
                        if (quad == null || quad.vertices == null) continue;
                        if (quad.vertices.size() != 4) continue;
                        Vector3f n = quad.normal;
                        ModelVertex v0 = quad.vertices.get(0);
                        ModelVertex v1 = quad.vertices.get(1);
                        ModelVertex v2 = quad.vertices.get(2);
                        ModelVertex v3 = quad.vertices.get(3);
                        /* Triangle 1: v0, v1, v2. */
                        writeVertex(vbo, v0.vertex, v0.uv, n, groupId);
                        writeVertex(vbo, v1.vertex, v1.uv, n, groupId);
                        writeVertex(vbo, v2.vertex, v2.uv, n, groupId);
                        /* Triangle 2: v0, v2, v3. Matches CubicVAOBuilder
                         * winding so back-face culling stays correct. */
                        writeVertex(vbo, v0.vertex, v0.uv, n, groupId);
                        writeVertex(vbo, v2.vertex, v2.uv, n, groupId);
                        writeVertex(vbo, v3.vertex, v3.uv, n, groupId);
                        written += 6;
                    }
                }
            }

            /* ---- Meshes: baseData is already a flat triangle list,
             * but positions are still in 16x model space (NOT divided
             * by 16) and UVs are in 0..texWidth (NOT normalized). We
             * normalize here to match the cube path. Compute normals
             * if missing; ModelMesh.fromData adds one per vertex so
             * usually they're already there. */
            List<ModelMesh> meshes = group.meshes;
            if (meshes != null)
            {
                final float texW = model.textureWidth > 0 ? model.textureWidth : 1F;
                final float texH = model.textureHeight > 0 ? model.textureHeight : 1F;
                Vector3f tmpPos = new Vector3f();
                Vector2f tmpUv  = new Vector2f();
                Vector3f tmpN   = new Vector3f();
                for (ModelMesh mesh : meshes)
                {
                    if (mesh == null || mesh.baseData == null) continue;
                    ModelData d = mesh.baseData;
                    if (d.vertices == null) continue;
                    final int n = d.vertices.size();
                    final boolean hasUv  = d.uvs     != null && d.uvs.size()     == n;
                    final boolean hasNor = d.normals != null && d.normals.size() == n;
                    for (int i = 0; i < n; i++)
                    {
                        Vector3f vp = d.vertices.get(i);
                        tmpPos.set(vp.x / 16F, vp.y / 16F, vp.z / 16F);
                        if (hasUv)
                        {
                            Vector2f vu = d.uvs.get(i);
                            tmpUv.set(vu.x / texW, vu.y / texH);
                        }
                        else
                        {
                            tmpUv.set(0F, 0F);
                        }
                        if (hasNor)
                        {
                            tmpN.set(d.normals.get(i));
                        }
                        else
                        {
                            /* Compute per-triangle normal from winding. */
                            int triBase = (i / 3) * 3;
                            if (triBase + 2 < d.vertices.size())
                            {
                                Vector3f a = d.vertices.get(triBase);
                                Vector3f b = d.vertices.get(triBase + 1);
                                Vector3f c = d.vertices.get(triBase + 2);
                                Vector3f e1 = new Vector3f(b).sub(a);
                                Vector3f e2 = new Vector3f(c).sub(a);
                                e1.cross(e2, tmpN);
                                if (tmpN.lengthSquared() > 1e-8F) tmpN.normalize();
                                else tmpN.set(0F, 1F, 0F);
                            }
                            else
                            {
                                tmpN.set(0F, 1F, 0F);
                            }
                        }
                        writeVertex(vbo, tmpPos, tmpUv, tmpN, groupId);
                        written++;
                    }
                }
            }
        }
        return written;
    }

    /** Append one interleaved vertex (36 bytes) to vbo at its current
     *  position. Caller pre-sized the buffer; we don't bounds-check.
     *  r33 diagnostic: dump first 3 vertices + count NaN/Inf occurrences. */
    private static int BBSTOOLS$R33_DUMP_COUNT = 0;
    private static int BBSTOOLS$R33_NAN_COUNT = 0;
    private static int BBSTOOLS$R33_INF_COUNT = 0;
    private static int BBSTOOLS$R33_OUT_OF_RANGE = 0;
    private static void writeVertex(ByteBuffer vbo, Vector3f pos, Vector2f uv, Vector3f n, int groupId)
    {
        // r33: detect bad floats that cause GPU rasterizer to TDR
        if (Float.isNaN(pos.x) || Float.isNaN(pos.y) || Float.isNaN(pos.z) ||
            Float.isNaN(uv.x) || Float.isNaN(uv.y) ||
            Float.isNaN(n.x) || Float.isNaN(n.y) || Float.isNaN(n.z))
        {
            BBSTOOLS$R33_NAN_COUNT++;
        }
        if (Float.isInfinite(pos.x) || Float.isInfinite(pos.y) || Float.isInfinite(pos.z) ||
            Float.isInfinite(uv.x) || Float.isInfinite(uv.y) ||
            Float.isInfinite(n.x) || Float.isInfinite(n.y) || Float.isInfinite(n.z))
        {
            BBSTOOLS$R33_INF_COUNT++;
        }
        /* Reasonable range check: positions should be -100..+100 blocks, UVs 0..1, normals -1..+1 */
        if (Math.abs(pos.x) > 1000F || Math.abs(pos.y) > 1000F || Math.abs(pos.z) > 1000F ||
            Math.abs(uv.x) > 100F || Math.abs(uv.y) > 100F ||
            Math.abs(n.x) > 10F || Math.abs(n.y) > 10F || Math.abs(n.z) > 10F)
        {
            BBSTOOLS$R33_OUT_OF_RANGE++;
        }

        if (BBSTOOLS$R33_DUMP_COUNT < 3)
        {
            LOG.info("[r33-vertex-dump] vert#{} pos=({},{},{}) uv=({},{}) norm=({},{},{}) groupId={}",
                BBSTOOLS$R33_DUMP_COUNT, pos.x, pos.y, pos.z, uv.x, uv.y, n.x, n.y, n.z, groupId);
            BBSTOOLS$R33_DUMP_COUNT++;
        }

        vbo.putFloat(pos.x).putFloat(pos.y).putFloat(pos.z);
        vbo.putFloat(uv.x).putFloat(uv.y);
        vbo.putFloat(n.x).putFloat(n.y).putFloat(n.z);
        vbo.putInt(groupId);
    }

    private void allocateInstanceVbo()
    {
        long size = (long) MAX_INSTANCES * INSTANCE_STRIDE;
        instanceVbo = GL15C.glGenBuffers();
        GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, instanceVbo);
        GL44C.glBufferStorage(GL15C.GL_ARRAY_BUFFER, size, PERSISTENT_FLAGS);
        instanceMap = GL30C.glMapBufferRange(GL15C.GL_ARRAY_BUFFER, 0, size, PERSISTENT_FLAGS);
        GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, 0);
        if (instanceMap == null) throw new IllegalStateException("cubic instance VBO map returned null");
    }

    private void allocateMatrixBankSsbo()
    {
        /* One mat4 per instance — that's the cubic compactness win:
         * 64 B/instance instead of 128*64=8192 B/instance. */
        long size = (long) MAX_INSTANCES * MAT4_BYTES;
        matrixBankSsbo = GL15C.glGenBuffers();
        GL15C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, matrixBankSsbo);
        GL44C.glBufferStorage(GL43C.GL_SHADER_STORAGE_BUFFER, size, PERSISTENT_FLAGS);
        matrixBankMap = GL30C.glMapBufferRange(GL43C.GL_SHADER_STORAGE_BUFFER, 0, size, PERSISTENT_FLAGS);
        GL15C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, 0);
        if (matrixBankMap == null) throw new IllegalStateException("cubic matrix-bank SSBO map returned null");
    }

    /* ---------------- Shader program (r31, raw GL) ----------------
     * We compile our own program from disk so we don't depend on Veil's
     * shader manager. Iris's auto-patcher still rewrites at runtime via
     * the .json render-type when Veil's path is active; here we run the
     * raw shader directly, accepting that Iris patching won't apply to
     * THIS draw call (acceptable for an opt-in perf path). */
    private static String loadResource(String path)
    {
        try (InputStream in = VeilCubicBatch.class.getResourceAsStream(path))
        {
            if (in == null) return null;
            byte[] bytes = in.readAllBytes();
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        }
        catch (Throwable t)
        {
            LOG.error("[tier1c-r31] loadResource failed for {}", path, t);
            return null;
        }
    }

    private static int compile(int type, String src)
    {
        int sh = GL20C.glCreateShader(type);
        GL20C.glShaderSource(sh, src);
        GL20C.glCompileShader(sh);
        if (GL20C.glGetShaderi(sh, GL20C.GL_COMPILE_STATUS) == 0)
        {
            String log = GL20C.glGetShaderInfoLog(sh);
            LOG.error("[tier1c-r31] shader compile failed (type={}): {}", type, log);
            GL20C.glDeleteShader(sh);
            return 0;
        }
        return sh;
    }

    static int getCubicShaderProgram()
    {
        if (cubicShaderProgram != 0) return cubicShaderProgram;
        synchronized (VeilCubicBatch.class)
        {
            if (cubicShaderProgram != 0) return cubicShaderProgram;
            String vshSrc = loadResource("/assets/bbstools/pinwheel/shaders/program/bbs_cubic.vsh");
            String fshSrc = loadResource("/assets/bbstools/pinwheel/shaders/program/bbs_cubic.fsh");
            if (vshSrc == null || fshSrc == null)
            {
                LOG.error("[tier1c-r31] cubic shader sources missing — disabling veil-cubic batch path");
                BBSPerfConfig.ENABLE_VEIL_CUBIC_BATCH = false;
                BBSTOOLS$AUTO_DISABLED = true;
                return 0;
            }
            int vsh = compile(GL20C.GL_VERTEX_SHADER, vshSrc);
            int fsh = compile(GL20C.GL_FRAGMENT_SHADER, fshSrc);
            if (vsh == 0 || fsh == 0)
            {
                if (vsh != 0) GL20C.glDeleteShader(vsh);
                if (fsh != 0) GL20C.glDeleteShader(fsh);
                BBSPerfConfig.ENABLE_VEIL_CUBIC_BATCH = false;
                BBSTOOLS$AUTO_DISABLED = true;
                return 0;
            }
            int prog = GL20C.glCreateProgram();
            GL20C.glAttachShader(prog, vsh);
            GL20C.glAttachShader(prog, fsh);
            GL20C.glLinkProgram(prog);
            /* Always detach + delete the shaders post-link to free GL state. */
            GL20C.glDetachShader(prog, vsh);
            GL20C.glDetachShader(prog, fsh);
            GL20C.glDeleteShader(vsh);
            GL20C.glDeleteShader(fsh);
            if (GL20C.glGetProgrami(prog, GL20C.GL_LINK_STATUS) == 0)
            {
                String log = GL20C.glGetProgramInfoLog(prog);
                LOG.error("[tier1c-r31] cubic shader link failed: {}", log);
                GL20C.glDeleteProgram(prog);
                BBSPerfConfig.ENABLE_VEIL_CUBIC_BATCH = false;
                BBSTOOLS$AUTO_DISABLED = true;
                return 0;
            }
            uProjMat      = GL20C.glGetUniformLocation(prog, "ProjMat");
            uModelViewMat = GL20C.glGetUniformLocation(prog, "ModelViewMat");
            uNormalMat    = GL20C.glGetUniformLocation(prog, "NormalMat");
            uSampler0     = GL20C.glGetUniformLocation(prog, "Sampler0");
            cubicShaderProgram = prog;
            LOG.info("[tier1c-r31] cubic shader program compiled OK: prog={} uniforms(ProjMat={}, MV={}, N={}, Sampler0={})",
                prog, uProjMat, uModelViewMat, uNormalMat, uSampler0);

            /* r39 2026-05-18 — Coded by Afrobama/Claude.
             *  Capture post-link shader binary + full introspection so
             *  we can SEE what Iris's TransformPatcher actually built
             *  from our source. The recurring GPU TDR happens AFTER
             *  shader compile reports OK, suggesting the patched binary
             *  has a subtle issue our compile-success check can't catch
             *  (e.g. mismatched gbuffers_entities expectations). */
            try
            {
                com.afrobama.bbstools.client.debug.master.gl.GlIntrospect.dumpShaderBinary("bbs_cubic", prog);
                com.afrobama.bbstools.client.debug.master.gl.GlIntrospect.syncCheck("after-cubic-shader-compile");
                com.afrobama.bbstools.client.debug.master.external.ApiTraceBridge.mark("[bbstools] bbs_cubic compiled prog=" + prog);
            }
            catch (Throwable ig) {}

            return prog;
        }
    }

    private static final float[] MAT4_SCRATCH = new float[16];
    private static final float[] MAT3_SCRATCH = new float[9];

    private static void pushMat4(int loc, Matrix4f m)
    {
        if (loc < 0) return;
        m.get(MAT4_SCRATCH);
        GL20C.glUniformMatrix4fv(loc, false, MAT4_SCRATCH);
    }

    private static void pushMat3(int loc, Matrix3f m)
    {
        if (loc < 0) return;
        m.get(MAT3_SCRATCH);
        GL20C.glUniformMatrix3fv(loc, false, MAT3_SCRATCH);
    }

    /** Flatten per-frame entries into the persistent-mapped buffers.
     *  Coherent flag means no explicit flush. */
    public void uploadInstanceData(List<VeilCubicBatchRenderer.CubicBatchEntry> entries)
    {
        if (!built) { lastN = 0; return; }
        final int n = Math.min(entries.size(), MAX_INSTANCES);
        lastN = n;

        /* Tier 1c diagnostic: log the first cubic mesh upload so we can
         * confirm the vertex-accessor walk landed real data. One-shot. */
        if (n > 0 && !BBSTOOLS$FIRST_FLUSH_LOGGED)
        {
            BBSTOOLS$FIRST_FLUSH_LOGGED = true;
            LOG.info("[tier1c] first cubic mesh flushed: {} vertices, {} instances", vertexCount, n);
        }

        /* Matrix bank: one mat4 per instance. */
        matrixBankMap.clear();
        FloatBuffer mFb = matrixBankMap.asFloatBuffer();
        for (int i = 0; i < n; i++)
        {
            VeilCubicBatchRenderer.CubicBatchEntry e = entries.get(i);
            Matrix4f m = e.modelTransform != null ? e.modelTransform : new Matrix4f();
            mFb.position(i * 16);
            m.get(mFb);
        }

        /* Instance VBO: (ssboBase, packedLight, argbColor, pad). */
        instanceMap.clear();
        for (int i = 0; i < n; i++)
        {
            VeilCubicBatchRenderer.CubicBatchEntry e = entries.get(i);
            int base = i * INSTANCE_STRIDE;
            instanceMap.position(base);
            instanceMap.putInt(i);                /* ssboBase = instance idx */
            instanceMap.putInt(e.packedLight);    /* packed sky+block light  */
            instanceMap.putInt(e.color);          /* argb tint               */
            instanceMap.putInt(0);                /* pad                     */
        }
    }

    /** Dispatch one instanced draw entirely via raw GL (r31 Option A).
     *  No Veil VertexArray, no RenderLayer setup — we own the program,
     *  the VAO, the SSBO binding, and the depth/cull state. No-ops cleanly
     *  if build never produced GL state. Defensive try/catch wraps the
     *  whole body; on first throw we auto-disable
     *  BBSPerfConfig.ENABLE_VEIL_CUBIC_BATCH so MC doesn't freeze
     *  repeatedly. */
    public void draw(Matrix4f viewMatrix, Matrix4f projMatrix)
    {
        if (!built || vao == 0 || staticVbo == 0 || vertexCount <= 0 || lastN <= 0) return;
        if (BBSTOOLS$AUTO_DISABLED) return;

        /* r39 ISOLATION TEST 2026-05-18 — Coded by Afrobama/Claude.
         *  If BBSPerfConfig.VEILCUBIC_SKIP_DRAW is true, return BEFORE any
         *  GL state change. This isolates: is the crash from our actual
         *  glDrawElementsInstanced + state, or from the BUILD/COMPILE
         *  phase that ran when veil-cubic was first ON-toggled?
         *    skip=true + still crashes  → bug is in build/compile/setup
         *    skip=true + no crash       → bug is in the draw path itself
         *  Toggle via /bbsdebug feature veilcubic-skip-draw on|off
         *  (added wire below in BBSPerfConfig). Default ON so user's
         *  next test answers the question. */
        if (BBSPerfConfig.VEILCUBIC_SKIP_DRAW)
        {
            if (!BBSTOOLS$FIRST_DRAW_LOGGED)
            {
                BBSTOOLS$FIRST_DRAW_LOGGED = true;
                LOG.info("[r39-isolation] VEILCUBIC_SKIP_DRAW=true — bypassing draw(). vao={} prog={} verts={} instances={}",
                    vao, getCubicShaderProgram(), vertexCount, lastN);
            }
            return;
        }

        int program = getCubicShaderProgram();
        if (program <= 0) return;

        /* r31 pre-draw diagnostic — this MUST be the last log line in the
         * file if the GPU TDRs at the draw call, confirming the freeze is
         * in glDrawElementsInstanced (driver), not Java. One-shot so
         * 200-replay films don't spam. */
        if (!BBSTOOLS$FIRST_DRAW_LOGGED)
        {
            BBSTOOLS$FIRST_DRAW_LOGGED = true;
            LOG.info("[tier1c-r31] about to glDrawElementsInstanced: vao={} prog={} vertexCount={} instanceCount={}",
                vao, program, vertexCount, lastN);
        }

        /* r38 GL-state save/restore — Coded by Afrobama/Claude.
         *  Previous versions modified program/VAO/depth/blend/cull/etc
         *  without restoring them. MC's GlStateManager kept its CACHED
         *  view of "blend enabled" / "depth func ALWAYS" / etc, while
         *  raw GL state was something else. Next vanilla draw used the
         *  cached state, mismatch caused EXCEPTION_ACCESS_VIOLATION
         *  inside nglDrawElements during particle render
         *  (hs_err_pid22332.log + hs_err_pid35984.log). This block
         *  captures the relevant state, lets us mutate freely, then
         *  restores everything in `finally` so callers see no change. */
        final int savedProgram      = org.lwjgl.opengl.GL11C.glGetInteger(0x8B8D); // GL_CURRENT_PROGRAM
        final int savedVao          = org.lwjgl.opengl.GL11C.glGetInteger(0x85B5); // GL_VERTEX_ARRAY_BINDING
        final int savedArrayBuffer  = org.lwjgl.opengl.GL11C.glGetInteger(0x8894); // GL_ARRAY_BUFFER_BINDING
        final int savedActiveTex    = org.lwjgl.opengl.GL11C.glGetInteger(0x84E0); // GL_ACTIVE_TEXTURE
        final int savedDrawFbo      = org.lwjgl.opengl.GL11C.glGetInteger(0x8CA6); // GL_DRAW_FRAMEBUFFER_BINDING
        final boolean savedDepthTest = org.lwjgl.opengl.GL11C.glIsEnabled(org.lwjgl.opengl.GL11C.GL_DEPTH_TEST);
        final boolean savedBlend     = org.lwjgl.opengl.GL11C.glIsEnabled(org.lwjgl.opengl.GL11C.GL_BLEND);
        final boolean savedCullFace  = org.lwjgl.opengl.GL11C.glIsEnabled(org.lwjgl.opengl.GL11C.GL_CULL_FACE);
        final int savedDepthFunc    = org.lwjgl.opengl.GL11C.glGetInteger(org.lwjgl.opengl.GL11C.GL_DEPTH_FUNC);
        final int savedDepthMask    = org.lwjgl.opengl.GL11C.glGetInteger(org.lwjgl.opengl.GL11C.GL_DEPTH_WRITEMASK);

        try
        {
            GL20C.glUseProgram(program);

            /* Uniforms — names match bbs_cubic.vsh exactly (NOT iris-prefixed). */
            if (projMatrix != null) pushMat4(uProjMat, projMatrix);
            if (viewMatrix != null) pushMat4(uModelViewMat, viewMatrix);
            /* NormalMat: 3x3 from inverse-transpose of view's rotation.
             * For now: just upper-3x3 of view, normalized rows. The shader
             * tolerates approximate normals (only used for MRT attachment 2). */
            if (uNormalMat >= 0 && viewMatrix != null)
            {
                Matrix3f nm = new Matrix3f().set(viewMatrix).normal();
                pushMat3(uNormalMat, nm);
            }
            /* Sampler0: unit 0 (caller responsible for the bound texture;
             * replays sample whatever MC bound for the entity render pass). */
            if (uSampler0 >= 0) GL20C.glUniform1i(uSampler0, 0);

            /* r32 hello-world test: SSBO bind DROPPED — the vsh no longer
             * references MatrixBank[] so binding 1 is unused. Re-enable
             * once we move past the TDR. Original line was:
             *   GL43C.glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, matrixBankSsbo);
             * Coded by Afrobama/Claude — r32. */

            /* Render state. */
            GL11C.glEnable(GL11C.GL_DEPTH_TEST);
            GL11C.glDepthFunc(GL11C.GL_LEQUAL);
            GL11C.glDepthMask(true);
            GL11C.glDisable(GL11C.GL_BLEND);
            GL11C.glEnable(GL11C.GL_CULL_FACE);

            /* r32 hello-world test: hardcap instance count to 1 to verify the
             * draw call itself works. If this renders, the per-instance VBO +
             * SSBO are the culprits. If still TDR, mesh/VAO setup is wrong.
             * Coded by Afrobama/Claude — r32. */
            int r32InstanceCount = Math.min(lastN, 1);
            LOG.info("[r32-test] forcing instanceCount={} (original={}) for hello-world test", r32InstanceCount, lastN);

            /* r34: auto-fire BBSGpuDebug capture ONCE before the TDR-causing
             * draw so we get full pipeline state on disk before MC freezes.
             * Coded by Afrobama/Claude. */
            if (!BBSTOOLS$R34_AUTO_CAPTURED)
            {
                BBSTOOLS$R34_AUTO_CAPTURED = true;
                try
                {
                    com.afrobama.bbstools.client.debug.BBSGpuDebug.captureNow("veil-cubic-pre-draw-r34");
                }
                catch (Throwable t)
                {
                    LOG.warn("[r34] auto-capture threw: {}", t.toString());
                }
            }

            /* The unified VAO is the only attrib/EBO state needed. */
            GL30C.glBindVertexArray(vao);
            /* Module 2: auto pre-draw capture hook (no-op when gpu-debug disabled). */
            com.afrobama.bbstools.client.debug.BBSGpuDebug.beforeDraw(
                "VeilCubicBatch.draw", program, vao, GL11C.GL_TRIANGLES, vertexCount, r32InstanceCount);
            GL31C.glDrawElementsInstanced(GL11C.GL_TRIANGLES, vertexCount, GL11C.GL_UNSIGNED_INT, 0L, r32InstanceCount);
            com.afrobama.bbstools.client.debug.BBSGpuDebug.checkError("VeilCubicBatch.draw");
        }
        catch (Throwable t)
        {
            /* r31 safety rail: kill the path on first failure so we don't
             * TDR the GPU repeatedly. User can re-enable via /bbsdebug. */
            BBSTOOLS$AUTO_DISABLED = true;
            built = false;
            BBSPerfConfig.ENABLE_VEIL_CUBIC_BATCH = false;
            LOG.error("[tier1c-r31] draw() threw — auto-disabling veil-cubic batch path", t);
        }
        finally
        {
            /* r38 restore EVERY piece of GL state we touched, so the caller's
             *  GlStateManager-cached view stays consistent with raw GL. The
             *  next vanilla draw (particles, GUI, anything) sees its
             *  expected state instead of our leftovers. Wrapped per-op in
             *  try/catch so a bad save value can't break the rest. */
            try { GL20C.glUseProgram(savedProgram); } catch (Throwable ig) {}
            try { GL30C.glBindVertexArray(savedVao); } catch (Throwable ig) {}
            try { GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, savedArrayBuffer); } catch (Throwable ig) {}
            try { com.mojang.blaze3d.opengl.GlStateManager._activeTexture(savedActiveTex); } catch (Throwable ig) {}
            try { org.lwjgl.opengl.GL30C.glBindFramebuffer(0x8CA9 /* GL_DRAW_FRAMEBUFFER */, savedDrawFbo); } catch (Throwable ig) {}
            try
            {
                if (savedDepthTest) GL11C.glEnable(GL11C.GL_DEPTH_TEST);
                else                GL11C.glDisable(GL11C.GL_DEPTH_TEST);
                if (savedBlend)     GL11C.glEnable(GL11C.GL_BLEND);
                else                GL11C.glDisable(GL11C.GL_BLEND);
                if (savedCullFace)  GL11C.glEnable(GL11C.GL_CULL_FACE);
                else                GL11C.glDisable(GL11C.GL_CULL_FACE);
                GL11C.glDepthFunc(savedDepthFunc);
                GL11C.glDepthMask(savedDepthMask != 0);
            }
            catch (Throwable ig) {}

            /* MC's GlStateManager keeps a JVM-side cache of every GL toggle
             *  (so identical glEnable calls become no-ops). Our raw glEnable
             *  / glDisable above bypass that cache. Force-resync the cache by
             *  going through GlStateManager so MC's next call to e.g.
             *  RenderSystem.disableBlend() doesn't skip "because already
             *  disabled" — even though raw GL state diverged. We re-apply
             *  the saved values; GlStateManager is idempotent + cheap. */
            try
            {
                com.mojang.blaze3d.opengl.GlStateManager._glUseProgram(savedProgram);
                com.mojang.blaze3d.opengl.GlStateManager._glBindVertexArray(savedVao);
                if (savedBlend)    com.mojang.blaze3d.systems.RenderSystem.enableBlend();
                else               com.mojang.blaze3d.systems.RenderSystem.disableBlend();
                if (savedCullFace) com.mojang.blaze3d.systems.RenderSystem.enableCull();
                else               com.mojang.blaze3d.systems.RenderSystem.disableCull();
                if (savedDepthTest) com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
                else                com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
                com.mojang.blaze3d.systems.RenderSystem.depthFunc(savedDepthFunc);
                com.mojang.blaze3d.systems.RenderSystem.depthMask(savedDepthMask != 0);
            }
            catch (Throwable ig) {}
        }
    }

    /* One-shot diagnostic gate for the first draw call (r31). */
    private static volatile boolean BBSTOOLS$FIRST_DRAW_LOGGED = false;

    /** Number of instances submitted on the last upload. */
    public int lastInstanceCount() { return lastN; }

    @Override
    public void close()
    {
        releaseAll();
        built = false;
    }

    private void releaseAll()
    {
        try
        {
            if (instanceVbo != 0)
            {
                GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, instanceVbo);
                GL30C.glUnmapBuffer(GL15C.GL_ARRAY_BUFFER);
                GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, 0);
                GL15C.glDeleteBuffers(instanceVbo);
            }
            if (matrixBankSsbo != 0)
            {
                GL15C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, matrixBankSsbo);
                GL30C.glUnmapBuffer(GL43C.GL_SHADER_STORAGE_BUFFER);
                GL15C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, 0);
                GL15C.glDeleteBuffers(matrixBankSsbo);
            }
            if (staticVbo != 0) GL15C.glDeleteBuffers(staticVbo);
            if (staticEbo != 0) GL15C.glDeleteBuffers(staticEbo);
            // Coded by Afrobama/Claude — Tier 1c VAO fix r31 (single unified VAO).
            if (vao != 0) { GL30C.glDeleteVertexArrays(vao); vao = 0; }
        }
        catch (Throwable t)
        {
            LOG.warn("VeilCubicBatch.releaseAll partial failure", t);
        }
        finally
        {
            instanceVbo = matrixBankSsbo = staticVbo = staticEbo = 0;
            instanceMap = matrixBankMap = null;
        }
    }

}
