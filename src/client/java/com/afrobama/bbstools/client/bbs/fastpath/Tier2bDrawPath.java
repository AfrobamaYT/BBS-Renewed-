// Coded by Afrobama/Claude — Tier 2-b real GPU-merged draw path.
//
// Replaces FastPathRenderer.flushAll() per-bucket loop with:
//   - one Tessellator BufferBuilder per (model.id) bucket
//   - CPU bone-skin via CubicMatrixRenderer's per-group world matrices
//   - rest-pose verts/UVs/normals fetched from the cached SkinnedMeshIR
//   - single BufferRenderer.drawWithGlobalProgram per bucket
//
// Collapses N {@code instance.render()} draws (each = its own Tessellator
// begin/end pair) into ONE drawArrays per (model.id) bucket. Iris-compat
// by construction: vanilla MC ENTITY format + entityTranslucentCullProgram
// is what Iris's gbuffers_entities pipeline already hooks.
//
// BOBJ models / models with meshes / models with shape keys → null IR →
// per-bucket fallback to FastPathRenderer.renderOne(). The router's
// strict gate already excludes most heavy cases.
package com.afrobama.bbstools.client.bbs.fastpath;

import com.afrobama.bbstools.client.bbs.mesh.SkinnedMeshCache;
import com.afrobama.bbstools.client.bbs.mesh.SkinnedMeshIR;
import com.afrobama.bbstools.client.mixin.bbs.ModelFormRendererAccessor;
import com.afrobama.bbstools.perf.BBSPerfConfig;

import com.mojang.blaze3d.systems.RenderSystem;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.animation.IAnimator;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.render.CubicMatrixRenderer;
import mchorse.bbs_mod.cubic.render.CubicRenderer;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Color;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public final class Tier2bDrawPath
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/fast-path/tier2b");

    private static volatile boolean LOGGED_FIRST_BUCKET = false;
    private static volatile boolean LOGGED_FIRST_FAILURE = false;

    /* ThreadLocal scratch — reused across submissions in the same flush.
     * Tier 2-b runs on the MC render thread only, but ThreadLocal keeps
     * the door open for Tier 2.5 parallel-skin without re-allocating. */
    private static final ThreadLocal<Vector4f> POS_TMP = ThreadLocal.withInitial(Vector4f::new);
    private static final ThreadLocal<Vector3f> NRM_TMP = ThreadLocal.withInitial(Vector3f::new);

    private Tier2bDrawPath() {}

    /**
     * Main entry — called by FastPathBatch.flush() when ENABLE_TIER2_DRAW
     * is on. Iterates the per-model-id batch, merges all instances of the
     * same model into one BufferBuilder, single drawArrays per bucket.
     *
     * Buckets without a cached IR (BOBJ, mesh-only, shape-keys) fall back
     * to FastPathRenderer.renderOne for each submission so visual parity
     * stays intact during the migration.
     */
    public static void flushAll()
    {
        Map<String, List<FastPathBatch.Submission>> batch = FastPathBatch.snapshotBatch();
        if (batch.isEmpty()) return;

        long startNs = System.nanoTime();
        int drawCalls = 0;
        int submissionsDrawn = 0;
        int vertsDrawn = 0;
        int fallbacks = 0;
        int failures = 0;

        /* Hoist render state outside per-bucket loop — same pattern as
         * FastPathRenderer.flushAll. blend/lightmap/overlay set ONCE
         * for the entire fast-path flush. */
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        GameRenderer gr = MinecraftClient.getInstance().gameRenderer;
        gr.getLightmapTextureManager().enable();
        gr.getOverlayTexture().setupOverlayColor();

        try
        {
            for (Map.Entry<String, List<FastPathBatch.Submission>> entry : batch.entrySet())
            {
                List<FastPathBatch.Submission> list = entry.getValue();
                if (list.isEmpty()) continue;

                FastPathBatch.Submission first = list.get(0);
                if (first.instance == null) { fallbacks += list.size(); continue; }

                SkinnedMeshIR ir = SkinnedMeshCache.getOrBuild(first.instance);
                if (ir == null || ir.vertCount == 0 || !(first.instance.model instanceof Model))
                {
                    /* Fallback: BOBJ / mesh / unsupported. Call invoker
                     * fallback once per submission. Slower but correct. */
                    for (FastPathBatch.Submission s : list)
                    {
                        try { FastPathRenderer.renderOneFallback(s); }
                        catch (Throwable t) { failures++; }
                        fallbacks++;
                    }
                    continue;
                }

                /* Resolve texture from the first submission's form. For now
                 * we assume all instances in this bucket share the same
                 * texture — same model.id implies same default texture in
                 * BBS's typical usage. Per-instance texture overrides via
                 * form.texture are honored: the bucket is keyed by
                 * instance.id, not by form, so different forms with the
                 * same model would land in same bucket. Edge case left
                 * for Tier 2-c (texture-keyed sub-bucketing). */
                Link texture = first.form.texture.get();
                if (texture == null) texture = first.instance.getTexture();
                if (texture != null)
                {
                    try { BBSModClient.getTextures().bindTexture(texture); }
                    catch (Throwable t) { /* keep going — texture may already be bound */ }
                }

                /* Set the entity translucent-cull shader — vanilla MC,
                 * Iris-compat by construction. */
                RenderSystem.setShader(GameRenderer::getRenderTypeEntityTranslucentCullProgram);

                /* Begin merged buffer for this bucket. */
                BufferBuilder builder;
                try
                {
                    builder = Tessellator.getInstance().begin(
                        VertexFormat.DrawMode.TRIANGLES,
                        VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
                }
                catch (Throwable t)
                {
                    failures++;
                    if (!LOGGED_FIRST_FAILURE)
                    {
                        LOGGED_FIRST_FAILURE = true;
                        LOG.warn("[tier2b] Tessellator.begin threw — falling back. cause={}", t.toString(), t);
                    }
                    for (FastPathBatch.Submission s : list)
                    {
                        try { FastPathRenderer.renderOneFallback(s); } catch (Throwable ig) {}
                        fallbacks++;
                    }
                    continue;
                }

                /* Per-submission: animate + extract bone matrices + skin
                 * into the shared builder. */
                int bucketSubsWritten = 0;
                int bucketVertsWritten = 0;
                for (FastPathBatch.Submission s : list)
                {
                    try
                    {
                        if (writeInstance(s, ir, builder))
                        {
                            bucketSubsWritten++;
                            bucketVertsWritten += ir.vertCount;
                        }
                        else
                        {
                            fallbacks++;
                        }
                    }
                    catch (Throwable t)
                    {
                        failures++;
                        if (!LOGGED_FIRST_FAILURE)
                        {
                            LOGGED_FIRST_FAILURE = true;
                            LOG.warn("[tier2b] writeInstance threw for model={} — cause={}",
                                s.instance == null ? "?" : s.instance.id, t.toString(), t);
                        }
                    }
                }

                if (bucketSubsWritten == 0)
                {
                    /* Nothing written — end the builder without drawing to
                     * release the resources. BufferBuilder.end() on an
                     * empty buffer throws IllegalStateException; swallow. */
                    try { builder.end(); } catch (Throwable ig) {}
                    continue;
                }

                /* Single draw per bucket. */
                try
                {
                    BufferRenderer.drawWithGlobalProgram(builder.end());
                    drawCalls++;
                    submissionsDrawn += bucketSubsWritten;
                    vertsDrawn += bucketVertsWritten;
                }
                catch (Throwable t)
                {
                    failures++;
                    if (!LOGGED_FIRST_FAILURE)
                    {
                        LOGGED_FIRST_FAILURE = true;
                        LOG.warn("[tier2b] drawWithGlobalProgram threw — cause={}", t.toString(), t);
                    }
                }
            }
        }
        finally
        {
            gr.getLightmapTextureManager().disable();
            gr.getOverlayTexture().teardownOverlayColor();
            RenderSystem.disableBlend();
        }

        long elapsedNs = System.nanoTime() - startNs;
        BBSPerfConfig.tier2DrawCalls += drawCalls;
        BBSPerfConfig.tier2DrawSubmissions += submissionsDrawn;
        BBSPerfConfig.tier2DrawVerts += vertsDrawn;
        BBSPerfConfig.tier2DrawFallbacks += fallbacks;
        BBSPerfConfig.tier2DrawFailures += failures;
        BBSPerfConfig.tier2DrawNsAccum += elapsedNs;

        if (!LOGGED_FIRST_BUCKET && drawCalls > 0)
        {
            LOGGED_FIRST_BUCKET = true;
            LOG.info("[tier2b] FIRST merged-flush — buckets={} drawCalls={} subs={} verts={} fallbacks={} failures={} ns={}",
                batch.size(), drawCalls, submissionsDrawn, vertsDrawn, fallbacks, failures, elapsedNs);
        }
    }

    /**
     * Per-submission: run the BBS animator chain, extract per-group world
     * matrices via CubicMatrixRenderer, then CPU-skin the rest-pose IR
     * into the shared BufferBuilder. Returns true on success.
     */
    private static boolean writeInstance(FastPathBatch.Submission s,
                                         SkinnedMeshIR ir,
                                         BufferBuilder builder)
    {
        ModelFormRenderer self = s.renderer;
        if (self == null) return false;
        ModelFormRendererAccessor selfA = (ModelFormRendererAccessor) (Object) self;

        ModelInstance instance = s.instance;
        if (instance == null) return false;
        if (!(instance.model instanceof Model cubic)) return false;

        /* Animator chain — replicate FastPathRenderer.renderOne lines
         * 146-152 so group.current transforms reflect the current pose. */
        selfA.bbstools$ensureAnimator(s.transition);
        IAnimator animator = selfA.bbstools$getAnimator();
        if (animator == null) return false;

        instance.model.resetPose();
        animator.applyActions(s.entity, instance, s.transition);
        instance.model.applyPose(selfA.bbstools$getPose());

        /* Build root matrix stack — must match what BBS does at
         * render3D line 625 (world matrix + 180° Y rotation). */
        MatrixStack stack = new MatrixStack();
        stack.multiplyPositionMatrix(s.worldMatrix);
        stack.multiply(RotationAxis.POSITIVE_Y.rotation((float) MathUtils.PI));

        /* Extract per-group world-space Matrix4f via BBS's own
         * CubicMatrixRenderer. Index matches IR.boneIndices entries. */
        CubicMatrixRenderer extractor = new CubicMatrixRenderer(cubic);
        CubicRenderer.processRenderModel(extractor, null, stack, cubic);
        if (extractor.matrices == null) return false;
        int boneSlots = extractor.matrices.size();

        /* Color resolution — formColor × instanceColor × s.color (the
         * per-submission tint passed from BBS). Group color reset to
         * white every frame (ModelGroup.reset called inside resetPose),
         * so we drop the per-group multiplier. */
        Color formColor = s.form.color.get().copy();
        formColor.mul(s.color);
        Color instColor = new Color().set(instance.config.color.get());
        float fr = formColor.r * instColor.r;
        float fg = formColor.g * instColor.g;
        float fb = formColor.b * instColor.b;
        float fa = formColor.a * instColor.a;

        /* Per-bone combined matrix is just the bone matrix itself for
         * cubic rigid skin (weight = 1). Precomputing the camera
         * multiplication would help, but the MatrixStack already lives
         * inside the bone matrices because CubicMatrixRenderer pushed
         * stack at its root. */
        int verts = ir.vertCount;
        int quads = ir.quadCount;
        int u = s.light & 0xFFFF;
        int v = (s.light >> 16) & 0xFFFF;
        int overlay = s.overlay;

        Vector4f pTmp = POS_TMP.get();
        Vector3f nTmp = NRM_TMP.get();

        /* Emit 2 triangles per quad: (v0,v1,v2) + (v0,v2,v3). Matches
         * BBS CubicCubeRenderer.renderCube winding so backface culling
         * stays correct. */
        for (int q = 0; q < quads; q++)
        {
            int base = q * 4;
            int b0 = base;
            int b1 = base + 1;
            int b2 = base + 2;
            int b3 = base + 3;

            emitVertex(builder, ir, b0, extractor, boneSlots,
                fr, fg, fb, fa, overlay, u, v, pTmp, nTmp);
            emitVertex(builder, ir, b1, extractor, boneSlots,
                fr, fg, fb, fa, overlay, u, v, pTmp, nTmp);
            emitVertex(builder, ir, b2, extractor, boneSlots,
                fr, fg, fb, fa, overlay, u, v, pTmp, nTmp);

            emitVertex(builder, ir, b0, extractor, boneSlots,
                fr, fg, fb, fa, overlay, u, v, pTmp, nTmp);
            emitVertex(builder, ir, b2, extractor, boneSlots,
                fr, fg, fb, fa, overlay, u, v, pTmp, nTmp);
            emitVertex(builder, ir, b3, extractor, boneSlots,
                fr, fg, fb, fa, overlay, u, v, pTmp, nTmp);
        }

        return verts > 0;
    }

    private static void emitVertex(BufferBuilder builder, SkinnedMeshIR ir, int vIdx,
                                   CubicMatrixRenderer extractor, int boneSlots,
                                   float cr, float cg, float cb, float ca,
                                   int overlay, int lightU, int lightV,
                                   Vector4f pTmp, Vector3f nTmp)
    {
        int bi = ir.boneIndices[vIdx * 4];
        if (bi < 0 || bi >= boneSlots) bi = 0;
        Matrix4f bone = extractor.matrices.get(bi);

        int pi = vIdx * 3;
        pTmp.set(ir.positions[pi], ir.positions[pi + 1], ir.positions[pi + 2], 1F);
        bone.transform(pTmp);

        nTmp.set(ir.normals[pi], ir.normals[pi + 1], ir.normals[pi + 2]);
        bone.transformDirection(nTmp);

        int ui = vIdx * 2;
        float tu = ir.uvs[ui];
        float tv = ir.uvs[ui + 1];

        builder.vertex(pTmp.x, pTmp.y, pTmp.z)
            .color(cr, cg, cb, ca)
            .texture(tu, tv)
            .overlay(overlay)
            .light(lightU, lightV)
            .normal(nTmp.x, nTmp.y, nTmp.z);
    }

    public static String snapshot()
    {
        double avgMsPerFlush = BBSPerfConfig.tier2DrawCalls == 0 ? 0
            : (BBSPerfConfig.tier2DrawNsAccum / (double) BBSPerfConfig.tier2DrawCalls) / 1_000_000.0;
        double avgVertsPerDraw = BBSPerfConfig.tier2DrawCalls == 0 ? 0
            : BBSPerfConfig.tier2DrawVerts / (double) BBSPerfConfig.tier2DrawCalls;
        return String.format(
            "draws=%d subs=%d verts=%d fallbacks=%d failures=%d avgMs/draw=%.2f avgVerts/draw=%.0f",
            BBSPerfConfig.tier2DrawCalls,
            BBSPerfConfig.tier2DrawSubmissions,
            BBSPerfConfig.tier2DrawVerts,
            BBSPerfConfig.tier2DrawFallbacks,
            BBSPerfConfig.tier2DrawFailures,
            avgMsPerFlush,
            avgVertsPerDraw);
    }

    public static void resetCounters()
    {
        LOGGED_FIRST_BUCKET = false;
        LOGGED_FIRST_FAILURE = false;
        BBSPerfConfig.tier2DrawCalls = 0L;
        BBSPerfConfig.tier2DrawSubmissions = 0L;
        BBSPerfConfig.tier2DrawVerts = 0L;
        BBSPerfConfig.tier2DrawFallbacks = 0L;
        BBSPerfConfig.tier2DrawFailures = 0L;
        BBSPerfConfig.tier2DrawNsAccum = 0L;
    }

}
