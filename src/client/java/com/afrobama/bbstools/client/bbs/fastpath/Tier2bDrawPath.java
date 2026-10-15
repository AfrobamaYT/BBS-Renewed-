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

import mchorse.bbs_mod.graphics.SofortZeichner;
import net.minecraft.client.renderer.RenderPipelines;

import com.afrobama.bbstools.client.bbs.mesh.SkinnedMeshCache;
import com.afrobama.bbstools.client.bbs.mesh.SkinnedMeshIR;
import com.afrobama.bbstools.client.mixin.bbs.ModelFormRendererAccessor;
import com.afrobama.bbstools.perf.BBSPerfConfig;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
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
         * FastPathRenderer.flushAll. lightmap/overlay set ONCE for the
         * entire fast-path flush.
         *
         * 26.2: enableBlend + defaultBlendFunc entfallen ersatzlos. Mischen ist
         * kein Schalter mehr, sondern Teil der Pipeline. Den Zustand traegt hier
         * RenderPipelines.ENTITY_TRANSLUCENT_CULL — dieselbe Wahl, die der
         * Zeichenweg unten ohnehin schon ausspricht (der gesetzte Shader ist
         * getRendertypeEntityTranslucentCullShader). Deren
         * BlendFunction.TRANSLUCENT ist genau das alte defaultBlendFunc.
         * ⚠️ Wer den Zeichenaufruf portiert, muss GENAU diese Pipeline waehlen —
         * mit einer ungemischten werden die Modelle still undurchsichtig. */
        GameRenderer gr = Minecraft.getInstance().gameRenderer;
        // 26.2: turnOn/OffLightLayer entfaellt -- die Lichtkarte ist kein globaler Zustand mehr, sondern Sampler2 des Zeichenaufrufs (siehe SofortZeichner.entityTextur). Wer hier zeichnet, muss sie binden; ein globaler Schalter erreicht die Pipeline nicht.
        // 26.2: setup/teardownOverlayColor entfaellt -- das Overlay ist kein globaler Zustand mehr, sondern Sampler1 des Zeichenaufrufs (SofortZeichner bindet alle drei).

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
                /* 26.2: setShader ist entfallen -- der Shader steht in der Pipeline, die der
                 * Zeichenaufruf waehlt. Frueher galt er fuer alles Folgende. */

                /* ⛔ HALB OFFEN 26.2 — Ecken-Aufbau und Zeichenaufruf sind umgestellt, die
                 *  TEXTURBINDUNG ist es NICHT.
                 *
                 *  Der Zug unten laeuft ueber RenderPipelines.ENTITY_TRANSLUCENT_CULL, und der
                 *  Entity-Shader liest DREI Sampler: Sampler0 (Modelltextur), Sampler1
                 *  (Overlay), Sampler2 (Lichtkarte). Gebunden wird hier keiner davon --
                 *  bindTexture() oben ist rohes GL und erreicht den Durchgang nicht mehr, und
                 *  SofortZeichner.zeichnen bekommt unten null als TextureSetup.
                 *
                 *  ⚠️ Folge: ein Durchgang mit unvollstaendigen Bindungen zeichnet nicht
                 *  falsch, sondern GAR NICHT. Dieser schnelle Pfad ist damit bis auf Weiteres
                 *  wirkungslos; der Fallback (FastPathRenderer.renderOneFallback) zeichnet
                 *  weiterhin richtig. Zu beheben mit SofortZeichner.entityTextur(textur) --
                 *  dieselbe Bindung, die Billboard- und Framebuffer-Form schon benutzen --,
                 *  zusammen mit den Geschwistern Tier2c/2d/2e, die dieselbe Kombination
                 *  brauchen. Hier NICHT nachgezogen, weil der Zeichenaufruf einem anderen
                 *  Umbau gehoert. */

                /* Begin merged buffer for this bucket.
                 *
                 * 26.2: Tesselator ist entfallen -- sein Eckenspeicher war ein GETEILTER
                 * globaler Zwischenspeicher, den sich alle Sofortzeichner reihum liehen. Jetzt
                 * gehoert der Speicher dem Aufruf, und mit ihm die Pflicht, ihn wieder
                 * freizugeben: ByteBufferBuilder haelt nativen Speicher, den kein Sammler
                 * einzieht. Darum steht close() an JEDEM Ausgang dieser Eimer-Schleife --
                 * ein vergessener Ausgang leckt pro Bild und Eimer. */
                ByteBufferBuilder eckenSpeicher;
                BufferBuilder builder;
                try
                {
                    eckenSpeicher = new ByteBufferBuilder(4096);
                    builder = SofortZeichner.beginnen(
                        eckenSpeicher,
                        com.mojang.blaze3d.PrimitiveTopology.TRIANGLES,
                        DefaultVertexFormat.ENTITY);
                }
                catch (Throwable t)
                {
                    failures++;
                    if (!LOGGED_FIRST_FAILURE)
                    {
                        LOGGED_FIRST_FAILURE = true;
                        LOG.warn("[tier2b] BufferBuilder-Aufbau warf — falling back. cause={}", t.toString(), t);
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
                    try { builder.buildOrThrow(); } catch (Throwable ig) {}
                    eckenSpeicher.close();
                    continue;
                }

                /* Single draw per bucket. */
                try
                {
                    /* Der Shader stand frueher in einem setShader davor: ENTITY_TRANSLUCENT_CULL.
                     * Jetzt waehlt ihn der Zeichenaufruf, und mit ihm Blend, Tiefe und Cull. */
                    SofortZeichner.zeichnen(net.minecraft.client.renderer.RenderPipelines.ENTITY_TRANSLUCENT_CULL, builder.buildOrThrow(),
                        com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrixCopy(), null, null, null);
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
                finally
                {
                    eckenSpeicher.close();
                }
            }
        }
        finally
        {
            /* 26.2: disableBlend entfaellt ersatzlos. Der Aufruf nahm nur den
             * globalen Schalter von oben zurueck; den gibt es nicht mehr. Jede
             * folgende Zeichnung bringt ihr Mischverhalten in ihrer eigenen
             * Pipeline mit — es ist nichts zurueckzusetzen. */
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
        PoseStack stack = new PoseStack();
        stack.mulPose(s.worldMatrix);
        stack.rotate(Axis.YP.rotation((float) MathUtils.PI));

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

        builder.addVertex(pTmp.x, pTmp.y, pTmp.z)
            .setColor(cr, cg, cb, ca)
            .setUv(tu, tv)
            .setOverlay(overlay)
            .setUv2(lightU, lightV)
            .setNormal(nTmp.x, nTmp.y, nTmp.z);
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
