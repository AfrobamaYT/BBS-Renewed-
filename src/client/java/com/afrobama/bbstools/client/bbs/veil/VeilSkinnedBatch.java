// Coded by Afrobama/Claude — Veil-batch diagnostic logging r29.
// Coded by Afrobama/Claude — per-(Model, Texture) GPU resource bundle for
// the Veil-instanced BBS replay batch renderer.
//
// Phase 2 IMPLEMENTATION: real GPU allocation against the Veil 4.0.1 API,
// verified by javap against libs/veil-fabric-1.21.1-4.0.1.jar:
//   - foundry.veil.api.client.render.vertex.VertexArray.create()
//   - VertexArray.editFormat()             ⇒ VertexArrayBuilder
//   - VertexArrayBuilder.defineVertexBuffer(bindingIdx, vbo, offsetBytes, stride, divisor)
//   - VertexArrayBuilder.setVertexAttribute (float),
//                       .setVertexIAttribute (uint), arguments:
//                       (attribIdx, bindingIdx, size, dataType, [normalized], relativeOffset)
//   - VertexArray.bind() / drawInstancedWithRenderType(RenderLayer, instanceCount)
//
// Per-vertex stride 60B: vec3 pos | vec2 uv | vec3 norm | vec4 weights | uvec4 boneIds
// Per-instance stride 96B (84 padded to 96): mat4 modelMat (64B) + uint boneBase (4B)
//                                            + uint pad     (4B) + vec4 color (16B)  + 8B pad
// Bone SSBO stride per instance: BONE_CAP * 64B (mat4 each)
//
// Reads CompiledData directly via BOBJModel.getMeshData() — that getter is
// public in BBS, so no accessor mixin is required for the data path. The
// optional VeilSkinnedBatchAccessor referenced by Agent VEIL-3 was for a
// faster path; the public getter works fine.
package com.afrobama.bbstools.client.bbs.veil;

import foundry.veil.api.client.render.vertex.VertexArray;
import foundry.veil.api.client.render.vertex.VertexArrayBuilder;

import mchorse.bbs_mod.bobj.BOBJArmature;
import mchorse.bbs_mod.bobj.BOBJLoader;
import mchorse.bbs_mod.cubic.model.bobj.BOBJModel;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL43C;
import org.lwjgl.opengl.GL44C;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.List;

public final class VeilSkinnedBatch implements AutoCloseable
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/bbs-veil/batch");

    /* Veil-batch diagnostic logging r29 — one-time flags so the WARN/INFO
     * lines fire once per session per scenario instead of spamming. Reset
     * inside close()/releaseAll() so a fresh batch can re-log. */
    private static boolean bbstools$drawSkipLoggedNotBuilt   = false;
    private static boolean bbstools$drawSkipLoggedNoVao      = false;
    private static boolean bbstools$drawSkipLoggedRtNotRl    = false;
    private static boolean bbstools$drawSkipLoggedZeroInst   = false;
    private static boolean bbstools$drawSuccessLogged        = false;

    /* Capacity assumed at build-time. Hard-capped instance count drives
     * the persistent-mapped allocation sizes. */
    public  static final int MAX_INSTANCES   = 3000;
    private static final int INSTANCE_STRIDE = 96;          /* see header   */
    private static final int VERTEX_STRIDE   = 60;          /* see header   */
    private static final int MAT4_BYTES      = 16 * Float.BYTES; /* 64       */

    /* Bone-array hard-cap — BBS skeletons we've seen top out around 80.
     * Allocating for 128 keeps the SSBO size predictable and never grows. */
    public static final int BONE_CAP = 128;

    /* Persistent-mapped buffer flags (GL 4.4 ARB_buffer_storage). */
    private static final int PERSISTENT_FLAGS =
        GL44C.GL_MAP_WRITE_BIT | GL44C.GL_MAP_PERSISTENT_BIT | GL44C.GL_MAP_COHERENT_BIT;

    /* GL state (all 0 until built()). */
    private VertexArray vertexArray;
    private int staticVbo;
    private int instanceVbo;
    private int bonesSsbo;
    private ByteBuffer instanceMap;
    private ByteBuffer bonesMap;

    private int vertexCount;
    private int boneCount;
    private VeilReplayBatchRenderer.GroupKey key;
    private boolean built;

    private VeilSkinnedBatch() {}

    /** One-time: builds VAO + static mesh VBO from the BOBJModel's compiled
     *  data, allocates persistent-mapped instance VBO + bones SSBO. */
    public static VeilSkinnedBatch build(VeilReplayBatchRenderer.GroupKey key)
    {
        VeilSkinnedBatch b = new VeilSkinnedBatch();
        b.key = key;
        try
        {
            if (!(key.model() instanceof BOBJModel bobj))
            {
                LOG.warn("VeilSkinnedBatch.build: key.model() is {}, expected BOBJModel — batch disabled",
                    key.model() == null ? "null" : key.model().getClass().getName());
                return b;
            }
            BOBJLoader.CompiledData data = bobj.getMeshData();
            BOBJArmature arm = bobj.getArmature();
            if (data == null || data.posData == null || data.normData == null)
            {
                LOG.warn("VeilSkinnedBatch.build: CompiledData missing for {} — batch disabled", key);
                return b;
            }
            b.vertexCount = data.normData.length / 3;
            b.boneCount   = arm == null ? 1 : Math.max(1, arm.orderedBones.size());
            if (b.boneCount > BONE_CAP)
            {
                LOG.warn("VeilSkinnedBatch.build: skeleton has {} bones (cap {}); clamping",
                    b.boneCount, BONE_CAP);
                b.boneCount = BONE_CAP;
            }

            b.allocateStaticVbo(data);
            b.allocateInstanceVbo();
            b.allocateBonesSsbo();
            b.buildVertexArray();

            b.built = true;
            LOG.info("VeilSkinnedBatch.build OK: model={} verts={} bones={} (cap {})",
                key.model(), b.vertexCount, b.boneCount, BONE_CAP);
        }
        catch (Throwable t)
        {
            LOG.error("VeilSkinnedBatch.build failed for {}", key, t);
            b.releaseAll();
            b.built = false;
        }
        return b;
    }

    /* ------------- allocation helpers ------------- */

    private void allocateStaticVbo(BOBJLoader.CompiledData data)
    {
        long staticSize = (long) vertexCount * VERTEX_STRIDE;
        ByteBuffer interleaved = MemoryUtil.memAlloc((int) staticSize);
        try
        {
            for (int v = 0; v < vertexCount; v++)
            {
                int p3 = v * 3, p2 = v * 2, p4 = v * 4;
                /* pos */
                interleaved.putFloat(data.posData[p3]);
                interleaved.putFloat(data.posData[p3 + 1]);
                interleaved.putFloat(data.posData[p3 + 2]);
                /* uv */
                interleaved.putFloat(safe(data.texData, p2));
                interleaved.putFloat(safe(data.texData, p2 + 1));
                /* norm */
                interleaved.putFloat(data.normData[p3]);
                interleaved.putFloat(data.normData[p3 + 1]);
                interleaved.putFloat(data.normData[p3 + 2]);
                /* weights */
                interleaved.putFloat(safe(data.weightData, p4));
                interleaved.putFloat(safe(data.weightData, p4 + 1));
                interleaved.putFloat(safe(data.weightData, p4 + 2));
                interleaved.putFloat(safe(data.weightData, p4 + 3));
                /* bone ids — packed as 4 uints. The vsh reads them as uvec4. */
                interleaved.putInt(safeInt(data.boneIndexData, p4));
                interleaved.putInt(safeInt(data.boneIndexData, p4 + 1));
                interleaved.putInt(safeInt(data.boneIndexData, p4 + 2));
                interleaved.putInt(safeInt(data.boneIndexData, p4 + 3));
            }
            interleaved.flip();

            staticVbo = GL15C.glGenBuffers();
            GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, staticVbo);
            GL15C.glBufferData(GL15C.GL_ARRAY_BUFFER, interleaved, GL15C.GL_STATIC_DRAW);
            GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, 0);
        }
        finally
        {
            MemoryUtil.memFree(interleaved);
        }
    }

    private void allocateInstanceVbo()
    {
        long size = (long) MAX_INSTANCES * INSTANCE_STRIDE;
        instanceVbo = GL15C.glGenBuffers();
        GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, instanceVbo);
        GL44C.glBufferStorage(GL15C.GL_ARRAY_BUFFER, size, PERSISTENT_FLAGS);
        instanceMap = GL30C.glMapBufferRange(GL15C.GL_ARRAY_BUFFER, 0, size, PERSISTENT_FLAGS);
        GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, 0);
        if (instanceMap == null) throw new IllegalStateException("instance VBO map returned null");
    }

    private void allocateBonesSsbo()
    {
        long size = (long) MAX_INSTANCES * BONE_CAP * MAT4_BYTES;
        bonesSsbo = GL15C.glGenBuffers();
        GL15C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, bonesSsbo);
        GL44C.glBufferStorage(GL43C.GL_SHADER_STORAGE_BUFFER, size, PERSISTENT_FLAGS);
        bonesMap = GL30C.glMapBufferRange(GL43C.GL_SHADER_STORAGE_BUFFER, 0, size, PERSISTENT_FLAGS);
        GL15C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, 0);
        if (bonesMap == null) throw new IllegalStateException("bones SSBO map returned null");
    }

    private void buildVertexArray()
    {
        vertexArray = VertexArray.create();
        vertexArray.bind();
        VertexArrayBuilder b = vertexArray.editFormat();

        /* Binding 0 → static interleaved per-vertex VBO, divisor 0. */
        b.defineVertexBuffer(0, staticVbo, 0, VERTEX_STRIDE, 0);
        b.setVertexAttribute (0, 0, 3, VertexArrayBuilder.DataType.FLOAT,        false,  0);  /* pos      */
        b.setVertexAttribute (1, 0, 2, VertexArrayBuilder.DataType.FLOAT,        false, 12);  /* uv       */
        b.setVertexAttribute (2, 0, 3, VertexArrayBuilder.DataType.FLOAT,        false, 20);  /* norm     */
        b.setVertexAttribute (3, 0, 4, VertexArrayBuilder.DataType.FLOAT,        false, 32);  /* weights  */
        b.setVertexIAttribute(4, 0, 4, VertexArrayBuilder.DataType.UNSIGNED_INT,        48);  /* boneIds  */

        /* Binding 1 → per-instance VBO, divisor 1. */
        b.defineVertexBuffer(1, instanceVbo, 0, INSTANCE_STRIDE, 1);
        b.setVertexAttribute (5, 1, 4, VertexArrayBuilder.DataType.FLOAT,        false,  0);  /* mat col0 */
        b.setVertexAttribute (6, 1, 4, VertexArrayBuilder.DataType.FLOAT,        false, 16);  /* mat col1 */
        b.setVertexAttribute (7, 1, 4, VertexArrayBuilder.DataType.FLOAT,        false, 32);  /* mat col2 */
        b.setVertexAttribute (8, 1, 4, VertexArrayBuilder.DataType.FLOAT,        false, 48);  /* mat col3 */
        b.setVertexIAttribute(9, 1, 1, VertexArrayBuilder.DataType.UNSIGNED_INT,        64);  /* boneBase */
        b.setVertexAttribute (10,1, 4, VertexArrayBuilder.DataType.UNSIGNED_BYTE, true,  72);  /* color    */

        VertexArray.unbind();
    }

    /** Stream this frame's per-instance + bone-matrix data into the
     *  persistent-mapped buffers. With GL_MAP_COHERENT_BIT no flush. */
    public void uploadInstanceData(List<VeilReplayBatchRenderer.BatchEntry> entries)
    {
        if (!built) return;
        final int n = Math.min(entries.size(), MAX_INSTANCES);

        /* Tier 3b — Java-side prep. If the texture-atlas feature is on,
         * make sure this batch's source texture is registered in the
         * atlas so a follow-up shader change can read the per-instance
         * UV directly. We DO NOT write the atlasUV into the existing
         * 96-byte instance stride yet — the trailing 8-byte pad is left
         * alone so the current vsh continues to function unchanged. The
         * lookup itself just primes the atlas and bumps counters. */
        if (com.afrobama.bbstools.perf.CrowdTextureAtlas.ENABLED && key != null)
        {
            try
            {
                Object tex = key.texture();
                if (tex != null)
                {
                    com.afrobama.bbstools.perf.CrowdTextureAtlas.AtlasTile tile =
                        com.afrobama.bbstools.perf.CrowdTextureAtlas.INSTANCE.lookup(tex);
                    if (tile == null)
                    {
                        tile = com.afrobama.bbstools.perf.CrowdTextureAtlas.INSTANCE.addTexture(tex);
                    }
                    /* tile may still be null (overflow / animated / blit fail) —
                     * caller's existing per-batch glBindTexture path remains
                     * valid; this is non-fatal. */
                    if (tile != null)
                    {
                        org.joml.Vector4f uv = tile.atlasUV;
                        /* Mirror to BBSPerfConfig counters so the grand-
                         * dashboard sees activity without poking the atlas. */
                        com.afrobama.bbstools.perf.BBSPerfConfig.atlasTilesUsed =
                            com.afrobama.bbstools.perf.CrowdTextureAtlas.INSTANCE.atlasTilesUsed.get();
                        com.afrobama.bbstools.perf.BBSPerfConfig.atlasLookupHits =
                            com.afrobama.bbstools.perf.CrowdTextureAtlas.INSTANCE.atlasLookupHits.get();
                        /* Keep a reference local so a future shader-side
                         * change can read it without reshaping callers. */
                        lastAtlasU0 = uv.x; lastAtlasV0 = uv.y;
                        lastAtlasU1 = uv.z; lastAtlasV1 = uv.w;
                    }
                    else
                    {
                        com.afrobama.bbstools.perf.BBSPerfConfig.atlasOverflows =
                            com.afrobama.bbstools.perf.CrowdTextureAtlas.INSTANCE.atlasOverflows.get();
                    }
                }
            }
            catch (Throwable t)
            {
                LOG.warn("VeilSkinnedBatch atlas-prime failed (non-fatal): {}", t.toString());
            }
        }

        /* Instance buffer. */
        instanceMap.clear();
        for (int i = 0; i < n; i++)
        {
            VeilReplayBatchRenderer.BatchEntry e = entries.get(i);
            int base = i * INSTANCE_STRIDE;
            instanceMap.position(base);

            /* mat4 column-major (joml's get(FloatBuffer) writes column-major) */
            FloatBuffer fb = instanceMap.asFloatBuffer();
            e.modelTransform().get(fb);

            instanceMap.position(base + 64);
            instanceMap.putInt(i * BONE_CAP);              /* uint boneBase  */
            instanceMap.putInt(0);                          /* uint pad       */
            instanceMap.putInt(e.argbColor());              /* RGBA8 packed   */
            /* remaining 8B pad — leave zero */
        }

        /* Bones buffer. */
        bonesMap.clear();
        for (int i = 0; i < n; i++)
        {
            VeilReplayBatchRenderer.BatchEntry e = entries.get(i);
            Matrix4f[] bones = e.boneMatrices();
            int slot = i * BONE_CAP * MAT4_BYTES;
            int boneN = bones == null ? 0 : Math.min(bones.length, BONE_CAP);
            for (int j = 0; j < boneN; j++)
            {
                bonesMap.position(slot + j * MAT4_BYTES);
                bones[j].get(bonesMap.asFloatBuffer());
            }
            /* trailing bones in this slot stay identity-or-stale; shader
             * indexes only [0..boneCount) so no visual artifact. */
        }
    }

    /** Issue the instanced draw through Veil's RenderType. The RenderType
     *  resolves to assets/bbstools/pinwheel/rendertypes/bbs_replay.json
     *  ⇒ Iris auto-patches gbuffers_entities on top. */
    public void draw(Matrix4f viewMatrix, Matrix4f projMatrix)
    {
        /* Veil-batch diagnostic logging r29 — instrument every silent
         * early-return so the user can grep `[veil-batch] draw skipped`
         * to discover which gate is firing on the first batch frame. */
        if (!built)
        {
            if (!bbstools$drawSkipLoggedNotBuilt)
            {
                bbstools$drawSkipLoggedNotBuilt = true;
                LOG.warn("[veil-batch] draw skipped: reason=not-built, built={}, vao={}, lastN={}, key={}",
                    built, vertexArray == null ? "null" : "set", lastN, key);
            }
            return;
        }
        if (vertexArray == null)
        {
            if (!bbstools$drawSkipLoggedNoVao)
            {
                bbstools$drawSkipLoggedNoVao = true;
                LOG.warn("[veil-batch] draw skipped: reason=no-vao, built={}, vao=null, lastN={}, key={}",
                    built, lastN, key);
            }
            return;
        }

        Object rl = VeilReplayRenderType.get(key.texture());
        if (!(rl instanceof RenderType renderLayer))
        {
            /* Veil hasn't activated the JSON-defined RenderType yet — skip
             * silently; caller already logs a one-time warning. */
            if (!bbstools$drawSkipLoggedRtNotRl)
            {
                bbstools$drawSkipLoggedRtNotRl = true;
                LOG.warn("[veil-batch] draw skipped: reason=rt-not-renderlayer, built={}, instanceCount={}, rl-type={}, key-texture={}",
                    built, lastN, rl == null ? "null" : rl.getClass().getName(), key.texture());
            }
            return;
        }

        /* Bind bones SSBO to binding=0 (matches shader's layout). */
        GL43C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, 0, bonesSsbo);

        /* drawInstancedWithRenderType binds the VAO + sets the RenderLayer
         * state internally, so we don't bind() ourselves. */
        int instanceCount = Math.min(currentInstanceCount(), MAX_INSTANCES);
        if (instanceCount <= 0)
        {
            if (!bbstools$drawSkipLoggedZeroInst)
            {
                bbstools$drawSkipLoggedZeroInst = true;
                LOG.warn("[veil-batch] draw skipped: reason=zero-instances, built={}, instanceCount={}, lastN={}, MAX={}, rl-type={}, key-texture={}",
                    built, instanceCount, lastN, MAX_INSTANCES, renderLayer.getClass().getSimpleName(), key.texture());
            }
            GL43C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, 0, 0);
            return;
        }

        vertexArray.setDrawMode(com.mojang.blaze3d.vertex.com.mojang.blaze3d.PrimitiveTopology.TRIANGLES);
        vertexArray.setIndexCount(vertexCount, VertexArray.IndexType.INT);
        /* Non-indexed draw: VertexArray.drawInstancedWithRenderType uses
         * the configured drawMode + indexCount internally — for TRIANGLES
         * with no element-array bound this falls back to glDrawArrays. */
        vertexArray.drawInstancedWithRenderType(renderLayer, instanceCount);

        if (!bbstools$drawSuccessLogged)
        {
            bbstools$drawSuccessLogged = true;
            LOG.info("[veil-batch] FIRST SUCCESSFUL DRAW: instances={}, vertexCount={}, boneCount={}, groupKey-texture={}, renderLayer={}",
                instanceCount, vertexCount, boneCount, key.texture(), renderLayer.getClass().getSimpleName());
        }

        GL43C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, 0, 0);
    }

    /** Snapshot the last upload count — VeilReplayBatchRenderer drives this
     *  through the upload step, so we recover it from the instance buffer's
     *  position which uploadInstanceData() left at n*stride. */
    private int currentInstanceCount()
    {
        /* We bookkeep this externally: upload always processes min(entries, MAX).
         * Cheapest correct value is the most recently passed list size, but to
         * avoid stale state we store it in lastN. */
        return lastN;
    }

    private int lastN;

    /* Tier 3b — last successful atlas UV for this batch's source texture.
     * Updated inside uploadInstanceData. Not yet read by the shader (that's
     * the deferred follow-up); exposed so a later patch can fold this into
     * the per-instance VBO write without reshaping any callers. */
    private float lastAtlasU0;
    private float lastAtlasV0;
    private float lastAtlasU1 = 1F;
    private float lastAtlasV1 = 1F;

    public float lastAtlasU0() { return lastAtlasU0; }
    public float lastAtlasV0() { return lastAtlasV0; }
    public float lastAtlasU1() { return lastAtlasU1; }
    public float lastAtlasV1() { return lastAtlasV1; }

    /** Called by uploadInstanceData via a wrapper so draw() knows the count. */
    public void uploadInstanceDataAndCount(List<VeilReplayBatchRenderer.BatchEntry> entries)
    {
        uploadInstanceData(entries);
        lastN = Math.min(entries.size(), MAX_INSTANCES);
    }

    /** Frees the GPU buffers + VAO. */
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
            if (bonesSsbo != 0)
            {
                GL15C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, bonesSsbo);
                GL30C.glUnmapBuffer(GL43C.GL_SHADER_STORAGE_BUFFER);
                GL15C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, 0);
                GL15C.glDeleteBuffers(bonesSsbo);
            }
            if (staticVbo != 0) GL15C.glDeleteBuffers(staticVbo);
            if (vertexArray != null) vertexArray.free();
        }
        catch (Throwable t)
        {
            LOG.warn("VeilSkinnedBatch.releaseAll partial failure", t);
        }
        finally
        {
            instanceVbo = bonesSsbo = staticVbo = 0;
            instanceMap = bonesMap = null;
            vertexArray = null;
            /* Veil-batch diagnostic logging r29 — reset one-time flags so
             * the next batch (post /bbsdebug toggle or world reload) gets
             * a fresh skip/success log line. */
            bbstools$drawSkipLoggedNotBuilt = false;
            bbstools$drawSkipLoggedNoVao    = false;
            bbstools$drawSkipLoggedRtNotRl  = false;
            bbstools$drawSkipLoggedZeroInst = false;
            bbstools$drawSuccessLogged      = false;
        }
    }

    private static float safe(float[] a, int i)
    {
        return (a != null && i < a.length) ? a[i] : 0f;
    }
    private static int safeInt(int[] a, int i)
    {
        return (a != null && i < a.length) ? a[i] : 0;
    }

}
