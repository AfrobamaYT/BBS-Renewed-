package com.afrobama.bbstools.client.bbs.fastpath;

import com.afrobama.bbstools.client.bbs.mesh.SkinnedMeshCache;
import com.afrobama.bbstools.client.bbs.mesh.SkinnedMeshIR;
import com.afrobama.bbstools.perf.BBSPerfConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.textures.FilterMode;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.resources.Link;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Native instance uploads before the pass, and actual indexed instance draws inside it. */
final class Tier2hNativeDraw
{
    private static final Map<Bucket, Tier2hNativeMesh> MESHES = new HashMap<>();
    private static final List<Draw> PREPARED = new ArrayList<>();
    private static final List<FastPathBatch.Submission> FALLBACK = new ArrayList<>();
    private static GpuBufferSlice transforms;

    private Tier2hNativeDraw()
    {}

    static void prepare(Map<String, List<FastPathBatch.Submission>> batches, boolean idle)
    {
        RenderSystem.assertOnRenderThread();
        Tier2hNativeGpuTimer.prepare();
        /* Compiles through Minecraft's asynchronous RenderPearl cache, before the pass. */
        RenderSystem.getCompiledPipeline(Tier2hNativePipeline.PIPELINE);
        transforms = RenderSystem.getDynamicUniforms().writeTransform(
            RenderSystem.getModelViewMatrixCopy(), new Vector4f(1F));
        if (idle && batches.isEmpty()) return;
        PREPARED.clear();
        FALLBACK.clear();
        List<List<FastPathBatch.Submission>> materials = new ArrayList<>();
        for (List<FastPathBatch.Submission> list : batches.values())
        {
            Map<Link, List<FastPathBatch.Submission>> textures = new java.util.LinkedHashMap<>();
            for (FastPathBatch.Submission s : list)
                textures.computeIfAbsent(texture(s), key -> new ArrayList<>()).add(s);
            materials.addAll(textures.values());
        }
        for (List<FastPathBatch.Submission> list : materials)
        {
            if (list.isEmpty()) continue;
            FastPathBatch.Submission first = list.get(0);
            SkinnedMeshIR ir = first.instance == null ? null : SkinnedMeshCache.getOrBuild(first.instance);
            if (ir == null || first.instance == null || !(first.instance.model instanceof Model)
                || ir.format != 'C' || ir.vertCount == 0 || ir.quadCount == 0)
            {
                FALLBACK.addAll(list);
                continue;
            }
            Bucket bucket = new Bucket(ir.modelId, texture(first));
            Tier2hNativeMesh mesh = MESHES.get(bucket);
            if (mesh == null || mesh.source != ir)
            {
                if (mesh != null) mesh.close();
                mesh = new Tier2hNativeMesh(ir);
                MESHES.put(bucket, mesh);
            }
            boolean vat = mesh.atlas.prepareAtlas(list);
            int maxRows = RenderSystem.getDevice().getDeviceInfo().limits().maxTextureSizeForFormat(GpuFormat.RGBA32_FLOAT);
            int perChunk = Math.min(Tier2hInstancedDraw.MAX_INSTANCES, maxRows);
            if (perChunk <= 0) throw new IllegalStateException("Device has no native palette rows");
            int chunkIndex = 0;
            for (int firstIndex = 0; firstIndex < list.size(); firstIndex += perChunk)
            {
                List<FastPathBatch.Submission> part = list.subList(firstIndex, Math.min(list.size(), firstIndex + perChunk));
                Tier2hNativeMesh.Chunk chunk = mesh.chunk(chunkIndex++);
                prepareChunk(mesh, chunk, part, vat);
                if (chunk.count > 0) PREPARED.add(new Draw(mesh, chunk));
            }
        }
    }

    private static void prepareChunk(Tier2hNativeMesh mesh, Tier2hNativeMesh.Chunk chunk,
        List<FastPathBatch.Submission> list, boolean vat)
    {
        long phaseA = System.nanoTime();
        float[][] extracted = vat ? null : new float[list.size()][];
        if (!vat)
        {
            final float[][] poses = extracted;
            if (BBSPerfConfig.ENABLE_TIER2_PHASE_A_PARALLEL && list.size() > 1)
            {
                try
                {
                    java.util.stream.IntStream.range(0, list.size()).parallel()
                        .forEach(i -> poses[i] = Tier2hInstancedDraw.nativePalette(list.get(i)));
                    BBSPerfConfig.tier2fPhaseATasks += list.size();
                }
                catch (RuntimeException | Error failure)
                {
                    BBSPerfConfig.tier2fPhaseAFallbacks++;
                    for (int i = 0; i < list.size(); i++) poses[i] = Tier2hInstancedDraw.nativePalette(list.get(i));
                }
            }
            else for (int i = 0; i < list.size(); i++) poses[i] = Tier2hInstancedDraw.nativePalette(list.get(i));
        }
        BBSPerfConfig.tier2hPhaseANsAccum += System.nanoTime() - phaseA;
        long phaseU = System.nanoTime();
        ByteBuffer instances = MemoryUtil.memAlloc(Math.multiplyExact(list.size(), Tier2hNativePipeline.INSTANCE_BYTES))
            .order(ByteOrder.nativeOrder());
        ByteBuffer palettes = vat ? null : MemoryUtil.memAlloc(Math.multiplyExact(list.size(), Math.multiplyExact(mesh.bones, 64)))
            .order(ByteOrder.nativeOrder());
        try
        {
            int count = 0;
            for (int i = 0; i < list.size(); i++)
            {
                FastPathBatch.Submission s = list.get(i);
                float[] pose = vat ? null : extracted[i];
                if (!vat && pose == null)
                {
                    FALLBACK.add(s);
                    continue;
                }
                Tier2hVertexPacking.instance(instances, s, vat ? mesh.atlas.row(s) : count);
                if (!vat) Tier2hVertexPacking.palette(palettes, pose, mesh.bones);
                count++;
            }
            chunk.count = count;
            BBSPerfConfig.tier2hPhaseUNsAccum += System.nanoTime() - phaseU;
            if (count == 0) return;
            long phaseG = System.nanoTime();
            instances.flip();
            chunk.upload(instances, count);
            if (!vat)
            {
                palettes.flip();
                chunk.palette.upload(palettes, count);
            }
            chunk.vat = vat;
            Link texture = texture(list.get(0));
            chunk.texture = texture == null ? BBSModClient.getTextures().getLastBound()
                : BBSModClient.getTextures().getTexture(texture);
            if (chunk.texture == null) throw new IllegalStateException("Native instance mesh has no diffuse texture: " + mesh.source.modelId);
            /* Load and allocate the texture before the pass, including asynchronous resources. */
            chunk.diffuse = chunk.texture.view();
            chunk.sampler = chunk.texture.sampler();
            BBSPerfConfig.tier2hUploadedBytes += instances.remaining() + (palettes == null ? 0 : palettes.remaining());
            BBSPerfConfig.tier2hPhaseGNsAccum += System.nanoTime() - phaseG;
        }
        finally
        {
            MemoryUtil.memFree(instances);
            if (palettes != null) MemoryUtil.memFree(palettes);
        }
    }

    static void draw(RenderPass pass)
    {
        if (transforms == null) throw new IllegalStateException("Native instancing was not prepared before the solid pass");
        var shader = RenderSystem.getCompiledPipeline(Tier2hNativePipeline.PIPELINE);
        var nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        var linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        var game = net.minecraft.client.Minecraft.getInstance().gameRenderer;
        long phaseG = System.nanoTime();
        var drawnMeshes = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Tier2hNativeMesh, Boolean>());
        Tier2hNativeGpuTimer.begin(pass);
        for (Draw draw : PREPARED)
        {
            Tier2hNativeMesh mesh = draw.mesh;
            Tier2hNativeMesh.Chunk chunk = draw.chunk;
            pass.setPipeline(shader);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            pass.setUniform("Sampler0", chunk.diffuse, chunk.sampler);
            pass.setUniform("Sampler1", game.overlayTexture().getTextureView(), linear);
            pass.setUniform("Sampler2", game.lightmap(), linear);
            pass.setUniform("BoneTex", chunk.vat ? mesh.atlas.view() : chunk.palette.view(), nearest);
            pass.setVertexBuffer(0, mesh.vertices.slice());
            pass.setVertexBuffer(1, chunk.instances.slice());
            pass.setIndexBuffer(mesh.indices, IndexType.INT);
            pass.drawIndexed(mesh.indexCount, chunk.count, 0, 0, 0);
            drawnMeshes.add(mesh);
            BBSPerfConfig.tier2hSubmissions += chunk.count;
            BBSPerfConfig.tier2hInstancedDraws++;
            if (chunk.vat) BBSPerfConfig.tier2hVatDraws++;
        }
        BBSPerfConfig.tier2hBucketsProcessed += drawnMeshes.size();
        if (!FALLBACK.isEmpty()) BBSPerfConfig.tier2hBucketsFallback++;
        BBSPerfConfig.tier2hPhaseGNsAccum += System.nanoTime() - phaseG;
        Tier2hNativeGpuTimer.end(pass);
        /* Preserve the actual BBS renderer for mesh families outside rigid cubic instancing. */
        for (FastPathBatch.Submission s : FALLBACK) FastPathRenderer.renderOneFallback(s);
    }

    static boolean hasFallback()
    {
        return !FALLBACK.isEmpty();
    }

    static void close()
    {
        MESHES.values().forEach(Tier2hNativeMesh::close);
        MESHES.clear();
        PREPARED.clear();
        FALLBACK.clear();
        transforms = null;
        Tier2hNativeGpuTimer.close();
    }

    private record Draw(Tier2hNativeMesh mesh, Tier2hNativeMesh.Chunk chunk)
    {}

    private record Bucket(String model, Link texture)
    {}

    private static Link texture(FastPathBatch.Submission s)
    {
        var captured = NativeCrowdFrame.appearance(s);
        if (captured != null) return captured.texture();
        Link value = s.form.texture.get();
        return value == null && s.instance != null ? s.instance.getTexture() : value;
    }
}
