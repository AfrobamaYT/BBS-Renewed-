// Coded by Afrobama/Claude — Tier 2-h GPU-instancing draw path.
//
// Final architecture step in the fast-path ladder. Replaces Tier 2-d's
// N back-to-back drawArrays + CPU skinning with ONE glDrawElementsInstanced
// per (model, texture) bucket; GPU vertex shader does the bone-skin via
// per-instance SSBO lookup.
//
// Pipeline:
//   PHASE A (serial)   — BBS animator + matrix extract via CubicMatrixRenderer
//                        (same as Tier 2-d). Produces per-instance bone palette.
//   PHASE U (serial)   — pack bone matrices into flat SSBO + per-instance
//                        (worldMat + light + color) into instance VBO.
//   PHASE G (serial)   — upload SSBO + instance VBO; bind VAO + program;
//                        1× glDrawElementsInstanced(indexCount, N).
//
// Per-modelId resource bundle (created once per session, cached forever):
//   - VAO  : captures static VBO + EBO + instance VBO attrib layout
//   - VBO  : pos(3f) + uv(2f) + norm(3f) + boneIdx(uint) = 36B/vertex
//   - EBO  : quadCount × 6 uint indices ([0,1,2, 0,2,3] per quad)
//   - SSBO : grown on demand to fit MAX_INSTANCES × boneCount mat4
//   - iVBO : MAX_INSTANCES × 72B (mat4 worldMat + uint light + uint color)
//
// No Iris-compat in this revision — fullbright + vanilla MC lightmap.
// Iris integration is a follow-up tier; current path is for users running
// without a shaderpack (or as the foundation we'll bolt Iris onto later).
//
// Default OFF — opt-in via /bbstools replay-fast-path tier2-h on.
// Auto-disable on first GL failure so a bad driver doesn't TDR-loop.
package com.afrobama.bbstools.client.bbs.fastpath;

import com.afrobama.bbstools.client.bbs.mesh.SkinnedMeshCache;
import com.afrobama.bbstools.client.bbs.mesh.SkinnedMeshIR;
import com.afrobama.bbstools.client.mixin.bbs.ModelFormRendererAccessor;
import com.afrobama.bbstools.perf.BBSPerfConfig;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.animation.IAnimator;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.render.CubicMatrixRenderer;
import mchorse.bbs_mod.cubic.render.CubicRenderer;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.graphics.GpuBackend;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL12C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL31C;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.opengl.GL43C;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Tier2hInstancedDraw
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/fast-path/tier2h");

    private static final int VERTEX_STRIDE   = 36; /* pos12 + uv8 + norm12 + boneIdx4 */
    private static final int INSTANCE_STRIDE = 76; /* mat4(64) + light(4) + color(4) + vatPaletteRow(4) */
    public  static final int MAX_INSTANCES   = 2048;

    private static volatile boolean LOGGED_FIRST_FLUSH   = false;
    private static volatile boolean LOGGED_FIRST_FAILURE = false;
    private static volatile boolean AUTO_DISABLED        = false;

    /* Shared shader program — lazy compile on first flush (render thread). */

    /* Per-modelId GL bundle, cached forever once built. */
    private static final Map<String, MeshBundle> BUNDLES = new HashMap<>();

    /* Per-frame scratch — direct off-heap, grown as needed. */
    private static ByteBuffer  INSTANCE_SCRATCH = null;
    private static int         INSTANCE_SCRATCH_CAP = 0;
    private static ByteBuffer  BONE_SCRATCH = null;
    private static int         BONE_SCRATCH_CAP = 0;

    /* Diagnostic: log glGetError + glFinish for the first few flushes to
     * pinpoint a GPU-side fault. Set false once root cause found. */
    private static volatile boolean DIAG = true;
    private static volatile int DIAG_FRAMES = 0;
    private static final int DIAG_MAX_FRAMES = 4;

    /* Diagnostic isolation: when true, run the FULL pipeline (Phase A
     * extract, pack, buffer uploads, SSBO/VAO/shader binds, state restore)
     * but SKIP the glDrawElementsInstanced call. Splits the crash cause:
     *   crash persists → mid-pipeline GL setup/alloc is the poison
     *   crash gone     → the instanced draw call itself is the poison
     * Default true for the diagnostic build. */
    private static volatile boolean DIAG_SKIP_DRAW = false;

    /* Per-bucket extractor pool (single-threaded main thread). */
    private static CubicMatrixRenderer[] EXTRACTOR_POOL = new CubicMatrixRenderer[0];
    /** Per-submission CACHED flat bone array (column-major, == bone-buffer
     *  layout). When non-null for a slot, PHASE U arraycopies it straight into
     *  the bone buffer — skipping the cached-float→Matrix4f (fillExtractorFromCache)
     *  → Matrix4f→cached-float (PHASE U bm.get) round-trip that ran for every
     *  one of 3000 instances every frame. Byte-identical output (the round-trip
     *  was an identity), pure CPU saving on the hot draw path. */
    private static float[][] CACHED_FLAT_POOL = new float[0][];

    private static final ThreadLocal<HashMap<String, CubicMatrixRenderer>> EXTRACTOR_TL =
        ThreadLocal.withInitial(HashMap::new);

    /* Option 2 — deferred draw outside Iris's gbuffers pass. The replay
     * batch is captured during BBS render (inside Iris's pipeline) but the
     * raw-GL instanced draw is executed at LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN,
     * after Iris finished its geometry passes. Doing raw GL inside Iris's
     * pass corrupts Iris's state tracking → particle crash (proven). */
    private static final Map<String, List<FastPathBatch.Submission>> DEFERRED = new HashMap<>();
    private static volatile boolean EVENT_REGISTERED = false;

    /** Set by the render-loop bypass idle path; consumed at AFTER_ENTITIES. In an
     *  idle frame the bypass captures nothing (camera identical → reused GPU
     *  bundles), so it used to call redrawAllBundles() directly at the bypass point
     *  INSIDE BaseFilmController.render() — where the live modelView is the editor/UI
     *  matrix, not the world view, drawing the whole crowd at camera height. We now
     *  defer that redraw to the AFTER_ENTITIES hook (correct world modelView). */
    private static volatile boolean IDLE_REDRAW_PENDING = false;

    /** Request an idle redraw of the cached crowd bundles at the NEXT AFTER_ENTITIES
     *  (correct pipeline phase). Replaces the old bypass-point redrawAllBundles(). */
    public static void requestIdleRedraw()
    {
        IDLE_REDRAW_PENDING = true;
    }

    /** FIX B part 2 — did the last full flush have ANY CPU-fallback bucket (a model
     *  with no SkinnedMeshIR: equipment/shape-key/BOBJ)? The idle-reuse fast-skip
     *  only redraws the GPU instanced BUNDLES; fallback buckets have no bundle, so an
     *  idle skip would leave them undrawn = "crowd invisible when camera still, visible
     *  when moving". The render-loop bypass consults this to DISABLE the idle skip when
     *  fallback content is present (re-runs the full path every frame so the fallback
     *  re-renders; still fast because the expensive per-replay entity descent is gone). */
    private static volatile boolean LAST_FLUSH_HAD_FALLBACK = false;
    public static boolean lastFlushHadFallback() { return LAST_FLUSH_HAD_FALLBACK; }

    /** FIX B part 1 — the SELECTED replay's LIVE mesh render, deferred out of the
     *  render-loop-bypass point. The bypass cancels BaseFilmController.render at its
     *  HEAD, where the live modelView is the editor/UI matrix — rendering the selected
     *  replay there drew it at CAMERA HEIGHT, tracking the camera ("abbild"). We stash
     *  the render here and run it from the AFTER_ENTITIES hook, where the modelView is
     *  the world view (same phase the crowd draws correctly at). */
    public interface DeferredSelectedRender
    {
        void render(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext ctx);
    }
    private static volatile DeferredSelectedRender PENDING_SELECTED = null;
    public static void setPendingSelected(DeferredSelectedRender r) { PENDING_SELECTED = r; }
    public static void clearPendingSelected() { PENDING_SELECTED = null; }

    private Tier2hInstancedDraw() {}

    /** Register the AFTER_ENTITIES deferred-draw hook once. Called from
     *  BBSToolsModClient init.
     *
     *  AFTER_ENTITIES (not AFTER_TRANSLUCENT) is the integration point the
     *  working crowd renderers (CrowdBOBJGL/CrowdInstancedRenderer) use:
     *  Iris has the entity-pass gbuffer FBO bound with drawBuffers=[0..3],
     *  so our raw glDrawElementsInstanced lands in the same MRT attachments
     *  as vanilla entities and the composite pass picks it up with full
     *  shaderpack lighting. Without a shaderpack it's the vanilla main FBO,
     *  surplus outputs dropped — same code path works both ways. Crucially
     *  this is a clean Fabric event boundary OUTSIDE Iris's per-entity
     *  rendering, so raw GL here doesn't desync Iris's ImmediateState (which
     *  crashed the next particle draw when we drew inside the pass / at
     *  AFTER_TRANSLUCENT). */
    public static void register()
    {
        if (EVENT_REGISTERED) return;
        EVENT_REGISTERED = true;
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(client -> Tier2hNativeDraw.close());
        net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.COLLECT_SUBMITS.register(ctx ->
        {
            if (GpuBackend.isVulkan() && !AUTO_DISABLED && BBSPerfConfig.ENABLE_TIER2_H
                && (!DEFERRED.isEmpty() || IDLE_REDRAW_PENDING))
            {
                Tier2hNativeDraw.prepare(DEFERRED, IDLE_REDRAW_PENDING);
                LAST_FLUSH_HAD_FALLBACK = Tier2hNativeDraw.hasFallback();
            }
            if (nativeEnabled()) NativeCrowdFrame.prepared();
        });
        net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.AFTER_SOLID_FEATURES.register(ctx ->
        {
            try { drawDeferred(ctx); }
            catch (Throwable t)
            {
                if (!LOGGED_FIRST_FAILURE)
                {
                    LOGGED_FIRST_FAILURE = true;
                    LOG.warn("[tier2h] deferred draw threw — auto-disable. cause={}", t.toString(), t);
                }
                AUTO_DISABLED = true;
                BBSPerfConfig.ENABLE_TIER2_H = false;
                DEFERRED.clear();
            }
        });
        LOG.info("[tier2h] AFTER_ENTITIES deferred-draw hook registered (Option 2 — iris gbuffer integration point)");
    }

    /** Called from FastPathBatch.flush (inside BBS/Iris render). Snapshots
     *  the current batch so the actual GL draw can run later at
     *  AFTER_TRANSLUCENT. Submissions are shallow-copied into new lists;
     *  the live BBS objects they reference stay valid for this frame. */
    public static void captureForDeferred()
    {
        if (AUTO_DISABLED) return;
        Map<String, List<FastPathBatch.Submission>> batch = FastPathBatch.snapshotBatch();
        if (nativeEnabled())
        {
            if (!NativeCrowdFrame.collecting()) return;
            for (var entry : batch.entrySet())
                DEFERRED.computeIfAbsent(entry.getKey(), key -> new java.util.ArrayList<>()).addAll(entry.getValue());
            return;
        }
        DEFERRED.clear();
        for (Map.Entry<String, List<FastPathBatch.Submission>> e : batch.entrySet())
        {
            if (e.getValue() != null && !e.getValue().isEmpty())
            {
                DEFERRED.put(e.getKey(), new java.util.ArrayList<>(e.getValue()));
            }
        }
    }

    /** Fired at LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN — runs the raw-GL
     *  instanced draw on the captured batch, then clears it. */
    public static void drawDeferred(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext ctx)
    {
        if (AUTO_DISABLED) { DEFERRED.clear(); IDLE_REDRAW_PENDING = false; PENDING_SELECTED = null; return; }

        if (GpuBackend.isVulkan())
        {
            DeferredSelectedRender selected = PENDING_SELECTED;
            PENDING_SELECTED = null;
            if (selected != null && ctx != null) selected.render(ctx);
            return;
        }

        boolean idle = IDLE_REDRAW_PENDING;
        IDLE_REDRAW_PENDING = false;

        if (!DEFERRED.isEmpty())
        {
            /* Full frame: bypass captured fresh submissions this frame. */
            try { flushMap(DEFERRED); }
            finally { DEFERRED.clear(); }
        }
        else if (idle)
        {
            /* Idle frame: nothing captured (camera identical → reuse cached GPU
             * bundles). Redraw them HERE at AFTER_ENTITIES so the modelView is the
             * world view — drawing at the bypass point inside render() used the
             * editor/UI matrix and rendered the crowd at camera height. */
            redrawAllBundles();
        }

        /* FIX B part 1 — render the SELECTED replay's live mesh HERE (correct world
         * modelView) instead of at the bypass point (editor/UI matrix → abbild at
         * camera height). The bypass stashes it each frame; consume + clear so a
         * frame without a selection never re-fires a stale one. */
        DeferredSelectedRender sel = PENDING_SELECTED;
        PENDING_SELECTED = null;
        if (sel != null && ctx != null)
        {
            try
            {
                /* 26.2: RenderSystem fuehrt keinen globalen Tiefentest mehr. Diese Datei zeichnet
                 * mit eigenem GL-Programm, also schaltet sie ihn selbst -- wie alles andere hier. */
                Tier2hGlZustand.tiefeAn();
                sel.render(ctx);
            }
            catch (Throwable ig) { /* selected just won't draw this frame */ }
        }
    }

    /* ================================================================
     *                      Bundle definition
     * ================================================================ */

    private static final class MeshBundle
    {
        int vao;
        int vbo;
        int ebo;
        int instanceVbo;
        int boneTex;          /* RGBA32F texture holding per-instance bone palettes */

        int indexCount;   /* quadCount * 6 */
        int boneCount;
        int boneTexWidth;     /* texels per row = boneCount * 4 */
        int instanceVboCap;   /* in bytes */
        int boneTexRows;      /* allocated height (instances) */

        boolean disabled = false;

        /* Tier 2-h idle-reuse bookkeeping. The instance VBO + bone texture
         * persist on the GPU between frames, so when the bucket's signature is
         * unchanged we redraw them without re-packing/re-uploading. */
        boolean everDrawn = false;
        int     lastDrawableN = 0;   /* instances last uploaded → reuse count */
        long    reuseSigA = 0L;      /* world-matrix/light signature */
        long    reuseSigB = 0L;      /* sampled-bone/pose signature */
        long    lastPackMs = 0L;     /* wall-clock of last full pack+upload */
        int     lastDrawMode = 0;    /* 0 = per-frame bone tex, 1 = VAT atlas */
        Link    lastTexture = null;  /* diffuse texture of last draw → idle redraw */

        /* VAT (animation-texture) atlas: all unique-anim × tick palettes in one
         * RGBA32F texture uploaded once, indexed per-instance by PaletteRow. */
        int     atlasTex = 0;
        int     atlasRowsCap = 0;          /* allocated texture height */
        int     atlasUsedRows = 0;         /* groups × ticksPerGroup */
        int     atlasTicksPerGroup = 0;    /* uniform row stride per group */
        boolean atlasValid = false;        /* built this generation & fits */
        long    atlasFingerprint = -1L;    /* bake-progress fingerprint */
        java.util.IdentityHashMap<mchorse.bbs_mod.forms.entities.IEntity, Integer> atlasEntityBaseRow
            = new java.util.IdentityHashMap<>();
    }

    /* ================================================================
     *                      Public entry
     * ================================================================ */

    /** Direct entry (legacy — draws inside the BBS/Iris render pass).
     *  Kept for reference; the live path uses captureForDeferred +
     *  drawDeferred to dodge the Iris-pipeline crash. */
    public static void flushAll()
    {
        flushMap(FastPathBatch.snapshotBatch());
    }

    public static boolean nativeEnabled()
    {
        return BBSPerfConfig.ENABLE_TIER2_H && BBSPerfConfig.ENABLE_TIER2_DRAW && BBSPerfConfig.ENABLE_TIER2_GPU
            && BBSPerfConfig.ENABLE_FAST_PATH_RENDER && !AUTO_DISABLED && GpuBackend.isVulkan();
    }

    public static void beginNativeFrame()
    {
        if (!nativeEnabled()) return;
        DEFERRED.clear();
        NativeCrowdFrame.begin();
    }

    /** Called from the existing native world pass; preparation belongs to COLLECT_SUBMITS. */
    public static void drawNativeDeferred(com.mojang.renderpearl.api.commands.RenderPass pass)
    {
        if (AUTO_DISABLED || !BBSPerfConfig.ENABLE_TIER2_H) return;
        if (DEFERRED.isEmpty() && !IDLE_REDRAW_PENDING) return;
        try
        {
            Tier2hNativeDraw.draw(pass);
            if (IDLE_REDRAW_PENDING && DEFERRED.isEmpty()) BBSPerfConfig.tier2hIdleReuses++;
            BBSPerfConfig.tier2hFlushes++;
        }
        finally
        {
            DEFERRED.clear();
            IDLE_REDRAW_PENDING = false;
        }
    }

    static float[] nativePalette(FastPathBatch.Submission submission)
    {
        return NativeCrowdFrame.palette(submission, Tier2hInstancedDraw::extractMatricesFor);
    }

    private static void flushMap(Map<String, List<FastPathBatch.Submission>> batch)
    {
        if (GpuBackend.isVulkan())
        {
            throw new IllegalStateException("Native instancing must prepare before and draw inside the existing world pass");
        }
        if (AUTO_DISABLED) return;
        if (batch == null || batch.isEmpty()) return;

        long flushStartNs = System.nanoTime();
        BBSPerfConfig.tier2hFlushes++;

        /* Lazy program compile on render thread. On failure auto-disable;
         * the batch is the deferred copy (already drained from the live
         * path) so there's nothing to hand back to Tier 2-d here. */
        /* Das Programm uebersetzt sich beim ersten Zugriff selbst und meldet dort auch
         * seine Fehler (Tier2hProgramm). Bleibt 0 uebrig, schaltet sich dieser Zeichner
         * ab -- der Stapel ist die aufgeschobene Kopie, es gibt nichts zurueckzugeben. */
        if (Tier2hProgramm.nummer() == 0)
        {
            AUTO_DISABLED = true;
            BBSPerfConfig.ENABLE_TIER2_H = false;

            return;
        }

        int bucketsProcessed = 0;
        int bucketsFallback  = 0;
        int submissionsDrawn = 0;
        int instancedDraws   = 0;

        /* Minimal state — match the working CrowdBOBJGL renderer. Fullbright
         * shader, so NO lightmap/overlay enable (those bind textures to
         * units 1/2 + change state, an extra desync surface we don't need). */
        /* 26.2: enableBlend/defaultBlendFunc sind entfallen. Eigenes GL-Programm, also
         * eigener Zustand -- Standardmischung ist Quell-Alpha ueber Gegen-Alpha. */
        Tier2hGlZustand vorher = new Tier2hGlZustand();
        Tier2hGlZustand.mischenAn();

        try
        {
            for (Map.Entry<String, List<FastPathBatch.Submission>> entry : batch.entrySet())
            {
                List<FastPathBatch.Submission> list = entry.getValue();
                if (list.isEmpty()) continue;

                FastPathBatch.Submission first = list.get(0);
                if (first.instance == null) { bucketsFallback++; continue; }

                SkinnedMeshIR ir = SkinnedMeshCache.getOrBuild(first.instance);
                if (ir == null || ir.vertCount == 0 || ir.quadCount == 0
                    || !(first.instance.model instanceof Model))
                {
                    /* Not cubic — bail to the CPU fallback for this bucket. */
                    bbstools$renderFallbackBucket(list);
                    bucketsFallback++;
                    continue;
                }

                try
                {
                    int written = drawBucket(list, ir);
                    if (written > 0)
                    {
                        bucketsProcessed++;
                        submissionsDrawn += written;
                        instancedDraws++;
                    }
                    else
                    {
                        bucketsFallback++;
                    }
                }
                catch (Throwable t)
                {
                    bucketsFallback++;
                    BBSPerfConfig.tier2hFailures++;
                    if (!LOGGED_FIRST_FAILURE)
                    {
                        LOGGED_FIRST_FAILURE = true;
                        LOG.warn("[tier2h] drawBucket threw for model={} — auto-disable + fallback. cause={}",
                            first.instance.id, t.toString(), t);
                    }
                    /* First failure auto-disables the whole path to avoid
                     * GPU TDR loops. */
                    AUTO_DISABLED = true;
                    BBSPerfConfig.ENABLE_TIER2_H = false;

                    bbstools$renderFallbackBucket(list);
                }
            }
        }
        finally
        {
            vorher.zurueck();
        }

        BBSPerfConfig.tier2hBucketsProcessed += bucketsProcessed;
        BBSPerfConfig.tier2hBucketsFallback  += bucketsFallback;
        BBSPerfConfig.tier2hSubmissions      += submissionsDrawn;
        BBSPerfConfig.tier2hInstancedDraws   += instancedDraws;

        /* FIX B part 2 — record whether any bucket fell back to the CPU renderer this
         * flush. The bypass uses this to disable the idle fast-skip (which only redraws
         * GPU bundles) when fallback content is present, so the crowd stays visible when
         * the camera is still. */
        LAST_FLUSH_HAD_FALLBACK = bucketsFallback > 0;

        if (!LOGGED_FIRST_FLUSH && bucketsProcessed > 0)
        {
            LOGGED_FIRST_FLUSH = true;
            long elapsedNs = System.nanoTime() - flushStartNs;
            LOG.info("[tier2h] FIRST instanced flush — buckets={} subs={} draws={} ns={}",
                bucketsProcessed, submissionsDrawn, instancedDraws, elapsedNs);
        }
    }

    /**
     * CPU-renderer fallback for a bucket whose mesh can't be GPU-instanced (non-cubic IR,
     * or a drawBucket throw). The instanced path runs at LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN
     * (drawDeferred) and sets up ONLY blend — its own shader is fullbright and binds the bone
     * palette to texture unit 1, so it never enables the vanilla lightmap/overlay or touches
     * depth. FastPathRenderer.renderOneFallback drives the vanilla BBS entity render, which
     * REQUIRES that state: without the lightmap enabled the model samples black, and at the
     * post-translucent phase depth-test is not in the entity-pass configuration so the model
     * renders THROUGH world geometry. (FastPathRenderer.flushAll sets this up itself because
     * it runs inside the entity pass; here we must replicate it.) This was the "non-selected
     * replay renders black + visible through objects" bug. Set up full vanilla entity state
     * around the fallback, then restore so the next instanced bucket is unaffected. */
    private static void bbstools$renderFallbackBucket(List<FastPathBatch.Submission> list)
    {
        net.minecraft.client.renderer.GameRenderer gr = Minecraft.getInstance().gameRenderer;
        Tier2hGlZustand zustand = new Tier2hGlZustand();

        // 26.2: turnOn/OffLightLayer entfaellt -- die Lichtkarte ist kein globaler Zustand mehr, sondern Sampler2 des Zeichenaufrufs (siehe SofortZeichner.entityTextur). Wer hier zeichnet, muss sie binden; ein globaler Schalter erreicht die Pipeline nicht.
        // 26.2: setup/teardownOverlayColor entfaellt -- das Overlay ist kein globaler Zustand mehr, sondern Sampler1 des Zeichenaufrufs (SofortZeichner bindet alle drei).
        GL11C.glEnable(GL11C.GL_DEPTH_TEST);
        GL11C.glDepthFunc(GL11C.GL_GEQUAL);
        GL11C.glDepthMask(true);
        GL11C.glEnable(GL11C.GL_CULL_FACE);

        try
        {
            for (FastPathBatch.Submission s : list)
            {
                try { FastPathRenderer.renderOneFallback(s); } catch (Throwable ig) {}
            }
        }
        finally
        {
            zustand.zurueck();
        }
    }

    /* ================================================================
     *                      Per-bucket draw
     * ================================================================ */

    private static int drawBucket(List<FastPathBatch.Submission> list, SkinnedMeshIR ir)
    {
        int n = list.size();
        if (n > MAX_INSTANCES) n = MAX_INSTANCES;

        MeshBundle b = getOrBuildBundle(ir);
        if (b == null || b.disabled) return 0;

        /* ---------- IDLE-REUSE gate ----------
         * Compute a cheap signature of this bucket's camera-independent data
         * (per-instance world transform + light + sampled cached bones). If it
         * matches last frame AND the crowd is baked (pose sampled), the instance
         * VBO + bone texture already on the GPU are byte-identical — skip
         * Phase A + Phase U + both uploads and just re-set the camera uniforms
         * and re-issue the single instanced draw. This is the cinematic-camera
         * lever: orbiting a parked crowd costs one draw call, not a 4MB re-pack
         * + re-upload. Movable-safe (move/edit → signature mismatch → full path)
         * and playback-safe (age advances → sampled bones change → full path). */
        long nowMs = System.currentTimeMillis();
        bbstools$computeSig(list, n);
        boolean reuse =
               BBSPerfConfig.ENABLE_TIER2H_IDLE_REUSE
            && b.everDrawn
            && b.lastDrawableN > 0
            && bbstools$sigPoseSampled
            && bbstools$sigA == b.reuseSigA
            && bbstools$sigB == b.reuseSigB
            && (nowMs - b.lastPackMs) <= BBSPerfConfig.TIER2H_REUSE_SAFETY_MS;

        if (reuse)
        {
            BBSPerfConfig.tier2hIdleReuses++;
            bbstools$bindAndDraw(b, list, b.lastDrawableN, b.lastDrawMode);
            return b.lastDrawableN;
        }

        /* ---------- VAT (animation-texture) path ----------
         * If the whole baked animation fits an atlas texture, upload only the
         * tiny per-instance VBO (worldMat+light+color+paletteRow) and let the
         * vertex shader read bones from the persisted atlas — NO per-frame bone
         * pack/upload (the 6.8MB). Falls back to PHASE A/U/G when not baked yet,
         * an entity isn't bound, or the atlas doesn't fit (correct, no regress). */
        if (BBSPerfConfig.ENABLE_TIER2H_VAT)
        {
            bbstools$buildAtlasIfNeeded(b, list, n);
            if (b.atlasValid)
            {
                int vat = bbstools$drawBucketVat(b, list, n, nowMs);
                if (vat >= 0) return vat;
                /* vat < 0 → entity set changed; fall through to per-frame path. */
            }
            else
            {
                BBSPerfConfig.tier2hVatFallbacks++;
            }
        }

        /* ---------- PHASE A — animator + matrix extract ----------
         * Serial by default. When ENABLE_TIER2_PHASE_A_PARALLEL is on,
         * fan the per-replay animator+extract out across ForkJoinPool.
         * Safe ONLY because MatricesThreadSafeMixin made BBS's shared
         * static Matrices scratch ThreadLocal — without that, parallel
         * Phase A stretches meshes (the Tier 2-f failure). Each replay
         * has its own renderer + ModelInstance + extractor (ThreadLocal
         * pool), so no other shared mutable state. */
        final int fnA = n;
        long phaseANs = System.nanoTime();
        ensureExtractorPool(n);
        if (BBSPerfConfig.ENABLE_TIER2_PHASE_A_PARALLEL && n > 1)
        {
            final List<FastPathBatch.Submission> fList = list;
            try
            {
                java.util.stream.IntStream.range(0, fnA).parallel().forEach(i ->
                    bbstools$phaseAExtract(fList.get(i), i));
                BBSPerfConfig.tier2fPhaseATasks += n;
            }
            catch (Throwable t)
            {
                /* Fall back to serial on any worker failure. */
                BBSPerfConfig.tier2fPhaseAFallbacks++;
                for (int i = 0; i < n; i++)
                    bbstools$phaseAExtract(list.get(i), i);
            }
        }
        else
        {
            for (int i = 0; i < n; i++)
            {
                bbstools$phaseAExtract(list.get(i), i);
            }
        }
        BBSPerfConfig.tier2hPhaseANsAccum += System.nanoTime() - phaseANs;
        BBSPerfConfig.tier2fPhaseAParallelNs = BBSPerfConfig.tier2hPhaseANsAccum;

        /* ---------- PHASE U — pack SSBO + instance buffer ---------- */
        long phaseUNs = System.nanoTime();

        int bonesPerInstance = b.boneCount;
        int boneBytesPerInstance = bonesPerInstance * 16 * 4; /* mat4 = 64B */
        int neededSsbo = n * boneBytesPerInstance;
        int neededInst = n * INSTANCE_STRIDE;

        ByteBuffer iScratch = ensureInstanceScratch(neededInst);
        ByteBuffer bScratch = ensureBoneScratch(neededSsbo);
        iScratch.clear();
        bScratch.clear();

        float[] mat4Tmp = new float[16];

        int drawableN = 0;
        for (int i = 0; i < n; i++)
        {
            FastPathBatch.Submission s = list.get(i);
            CubicMatrixRenderer ex = EXTRACTOR_POOL[i];
            float[] cachedFlat = CACHED_FLAT_POOL[i];
            if (cachedFlat == null && (ex == null || ex.matrices == null || ex.matrices.isEmpty())) continue;

            iScratch.position(drawableN * INSTANCE_STRIDE);
            Tier2hVertexPacking.glInstance(iScratch, s, 0);
            bScratch.position(drawableN * boneBytesPerInstance);
            if (cachedFlat != null) Tier2hVertexPacking.palette(bScratch, cachedFlat, bonesPerInstance);
            else Tier2hVertexPacking.palette(bScratch, ex.matrices, bonesPerInstance);

            drawableN++;
        }

        if (drawableN == 0)
        {
            BBSPerfConfig.tier2hPhaseUNsAccum += System.nanoTime() - phaseUNs;
            for (int i = 0; i < n; i++) { EXTRACTOR_POOL[i] = null; CACHED_FLAT_POOL[i] = null; }
            return 0;
        }

        iScratch.position(0).limit(drawableN * INSTANCE_STRIDE);
        bScratch.position(0).limit(drawableN * boneBytesPerInstance);

        BBSPerfConfig.tier2hPhaseUNsAccum += System.nanoTime() - phaseUNs;

        /* ---------- PHASE G — upload + draw ---------- */
        long phaseGNs = System.nanoTime();

        /* Grow instance VBO if needed. */
        int wantedInstBytes = drawableN * INSTANCE_STRIDE;
        if (wantedInstBytes > b.instanceVboCap)
        {
            int newCap = Math.max(wantedInstBytes, b.instanceVboCap * 2);
            GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, b.instanceVbo);
            GL15C.glBufferData(GL15C.GL_ARRAY_BUFFER, newCap, GL15C.GL_DYNAMIC_DRAW);
            b.instanceVboCap = newCap;
        }
        /* Grow bone texture (more rows) if needed. */
        if (drawableN > b.boneTexRows)
        {
            int newRows = Math.max(drawableN, b.boneTexRows * 2);
            com.mojang.renderpearl.backend.opengl.GlStateManager._bindTexture(b.boneTex);
            GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL30C.GL_RGBA32F,
                b.boneTexWidth, newRows, 0, GL11C.GL_RGBA, GL11C.GL_FLOAT, (java.nio.ByteBuffer) null);
            b.boneTexRows = newRows;
            com.mojang.renderpearl.backend.opengl.GlStateManager._bindTexture(0);
        }

        /* ======================================================
         * GL handling MATCHES the proven-working CrowdBOBJGL renderer:
         * pure raw GL, sampled textures (NO SSBO — glBindBufferBase
         * mid-Iris-pipeline clobbers Iris's SSBO bindings → crash).
         * ====================================================== */

        /* Upload instance VBO (raw). */
        GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, b.instanceVbo);
        GL15C.glBufferSubData(GL15C.GL_ARRAY_BUFFER, 0L, iScratch);
        GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, 0);

        /* Upload bone palettes into the bone texture. bScratch is laid out
         * instance-major (one row = one instance's 4*boneCount texels). */
        Tier2hGlZustand.einheit(org.lwjgl.opengl.GL13C.GL_TEXTURE1);
        com.mojang.renderpearl.backend.opengl.GlStateManager._bindTexture(b.boneTex);
        /* Force tight pixel-store state. Another mod (or MC) may leave
         * GL_UNPACK_ROW_LENGTH / SKIP_* non-zero — then glTexSubImage2D
         * reads with the wrong stride and runs off the end of bScratch →
         * native EXCEPTION_ACCESS_VIOLATION inside nglTexSubImage2D. */
        GL11C.glPixelStorei(GL11C.GL_UNPACK_ALIGNMENT, 4);
        GL11C.glPixelStorei(GL12C.GL_UNPACK_ROW_LENGTH, 0);
        GL11C.glPixelStorei(GL12C.GL_UNPACK_SKIP_ROWS, 0);
        GL11C.glPixelStorei(GL12C.GL_UNPACK_SKIP_PIXELS, 0);
        /* Re-anchor the read window explicitly so it always covers exactly
         * boneTexWidth × drawableN RGBA32F texels (16 B each). */
        bScratch.position(0).limit(b.boneTexWidth * drawableN * 16);
        GL11C.glTexSubImage2D(GL11C.GL_TEXTURE_2D, 0, 0, 0,
            b.boneTexWidth, drawableN, GL11C.GL_RGBA, GL11C.GL_FLOAT, bScratch);

        /* Bind diffuse to unit 0 (shader reads Sampler0). */
        Tier2hGlZustand.einheit(org.lwjgl.opengl.GL13C.GL_TEXTURE0);
        Link texture = list.get(0).form.texture.get();
        if (texture == null) texture = list.get(0).instance.getTexture();
        if (texture != null)
        {
            try { BBSModClient.getTextures().bindTexture(texture); } catch (Throwable ig) {}
        }

        /* Depth/cull state — match CrowdBOBJGL. */
        Tier2hGlZustand zustand = new Tier2hGlZustand();
        GL11C.glEnable(GL11C.GL_DEPTH_TEST);
        GL11C.glDepthFunc(GL11C.GL_GEQUAL);
        GL11C.glDepthMask(true);
        GL11C.glDisable(GL11C.GL_CULL_FACE);

        try
        {
            GL20C.glUseProgram(Tier2hProgramm.nummer());

            Matrix4f mv  = RenderSystem.getModelViewMatrixCopy();
            Matrix4f prj = mchorse.bbs_mod.graphics.Projektion.lesen();
            mv.get(mat4Tmp);  GL20C.glUniformMatrix4fv(Tier2hProgramm.uModelViewMat, false, mat4Tmp);
            prj.get(mat4Tmp); GL20C.glUniformMatrix4fv(Tier2hProgramm.uProjMat, false, mat4Tmp);
            if (Tier2hProgramm.uSampler0 >= 0)    GL20C.glUniform1i(Tier2hProgramm.uSampler0, 0);
            if (Tier2hProgramm.uBoneTex >= 0)     GL20C.glUniform1i(Tier2hProgramm.uBoneTex, 1);
            if (Tier2hProgramm.uUseLightmap >= 0) GL20C.glUniform1i(Tier2hProgramm.uUseLightmap, 0);
            if (Tier2hProgramm.uUseVAT >= 0)      GL20C.glUniform1i(Tier2hProgramm.uUseVAT, 0); /* per-frame palette path */

            GL30C.glBindVertexArray(b.vao);
            GL31C.glDrawElementsInstanced(GL11C.GL_TRIANGLES, b.indexCount,
                GL11C.GL_UNSIGNED_INT, 0L, drawableN);
        }
        finally
        {
            /* Raw cleanup — match CrowdBOBJGL. Unbind both texture units
             * we touched (0 diffuse, 1 bone), restore active unit. */
            zustand.zurueck();
            GL20C.glUseProgram(0);
            GL30C.glBindVertexArray(0);
            GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, 0);
            com.mojang.renderpearl.backend.opengl.GlStateManager._bindTexture(0);
            Tier2hGlZustand.einheit(org.lwjgl.opengl.GL13C.GL_TEXTURE1);
            com.mojang.renderpearl.backend.opengl.GlStateManager._bindTexture(0);
        }

        BBSPerfConfig.tier2hPhaseGNsAccum += System.nanoTime() - phaseGNs;
        BBSPerfConfig.tier2hUploadedBytes += wantedInstBytes + (drawableN * boneBytesPerInstance);

        /* Record reuse state: the buffers now on the GPU correspond to this
         * frame's signature and instance count. Next frame may redraw them. */
        b.everDrawn     = true;
        b.lastDrawableN = drawableN;
        b.reuseSigA     = bbstools$sigA;
        b.reuseSigB     = bbstools$sigB;
        b.lastPackMs    = nowMs;
        b.lastDrawMode  = 0;
        /* FIX B part 3 — the idle redraw (redrawAllBundles) skips any bundle whose
         * lastTexture is null, so without this the crowd vanished whenever the camera
         * went still on a frame whose last draw used THIS per-frame path (the reuse +
         * VAT paths set it via bbstools$bindAndDraw, but the main path here never did).
         * That was "crowd invisible when still, visible when moving". */
        b.lastTexture   = texture;

        for (int i = 0; i < n; i++) { EXTRACTOR_POOL[i] = null; CACHED_FLAT_POOL[i] = null; }
        return drawableN;
    }

    /* ================================================================
     *                      Bundle build/cache
     * ================================================================ */

    private static MeshBundle getOrBuildBundle(SkinnedMeshIR ir)
    {
        MeshBundle b = BUNDLES.get(ir.modelId);
        if (b != null) return b;

        b = new MeshBundle();
        try
        {
            uploadStaticMesh(b, ir);
            allocateInstanceBuffers(b, ir);
            BUNDLES.put(ir.modelId, b);
            LOG.info("[tier2h] built bundle for model={} verts={} quads={} bones={} indexCount={}",
                ir.modelId, ir.vertCount, ir.quadCount, ir.boneCount, b.indexCount);
        }
        catch (Throwable t)
        {
            LOG.warn("[tier2h] bundle build failed for model={} — disabling bundle. cause={}",
                ir.modelId, t.toString(), t);
            b.disabled = true;
            BUNDLES.put(ir.modelId, b);
        }
        return b;
    }

    private static void uploadStaticMesh(MeshBundle b, SkinnedMeshIR ir)
    {
        /* Build interleaved VBO bytes. */
        int vbytes = ir.vertCount * VERTEX_STRIDE;
        ByteBuffer vbo = MemoryUtil.memAlloc(vbytes).order(ByteOrder.nativeOrder());
        try
        {
            Tier2hVertexPacking.mesh(vbo, ir);

            b.vbo = GL15C.glGenBuffers();
            GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, b.vbo);
            GL15C.glBufferData(GL15C.GL_ARRAY_BUFFER, vbo, GL15C.GL_STATIC_DRAW);
            GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, 0);
        }
        finally
        {
            MemoryUtil.memFree(vbo);
        }

        /* Build EBO — quad → 2 tri (indices [0,1,2, 0,2,3]). */
        b.indexCount = ir.quadCount * 6;
        int ibytes = b.indexCount * 4;
        ByteBuffer ebo = MemoryUtil.memAlloc(ibytes).order(ByteOrder.nativeOrder());
        try
        {
            Tier2hVertexPacking.indices(ebo, ir.quadCount);

            b.ebo = GL15C.glGenBuffers();
            GL15C.glBindBuffer(GL15C.GL_ELEMENT_ARRAY_BUFFER, b.ebo);
            GL15C.glBufferData(GL15C.GL_ELEMENT_ARRAY_BUFFER, ebo, GL15C.GL_STATIC_DRAW);
            GL15C.glBindBuffer(GL15C.GL_ELEMENT_ARRAY_BUFFER, 0);
        }
        finally
        {
            MemoryUtil.memFree(ebo);
        }

        b.boneCount = Math.max(1, ir.boneCount);
    }

    private static void allocateInstanceBuffers(MeshBundle b, SkinnedMeshIR ir)
    {
        /* Initial sizes — small, grown on demand. */
        int initialInst = 256 * INSTANCE_STRIDE;
        b.instanceVbo = GL15C.glGenBuffers();
        GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, b.instanceVbo);
        GL15C.glBufferData(GL15C.GL_ARRAY_BUFFER, initialInst, GL15C.GL_DYNAMIC_DRAW);
        b.instanceVboCap = initialInst;
        GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, 0);

        /* Bone-palette texture (RGBA32F). Each instance is one row;
         * each bone = 4 texels (its mat4 columns). NEAREST filter +
         * texelFetch in the shader. NO SSBO — glBindBufferBase mid-Iris
         * pipeline crashed; a sampled texture is Iris-safe (same approach
         * as the working CrowdBOBJGL anim atlas). */
        b.boneTexWidth = b.boneCount * 4;
        int initialRows = 256;
        b.boneTex = GL11C.glGenTextures();
        com.mojang.renderpearl.backend.opengl.GlStateManager._bindTexture(b.boneTex);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_NEAREST);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_NEAREST);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, GL12C.GL_CLAMP_TO_EDGE);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, GL12C.GL_CLAMP_TO_EDGE);
        GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL30C.GL_RGBA32F,
            b.boneTexWidth, initialRows, 0, GL11C.GL_RGBA, GL11C.GL_FLOAT, (java.nio.ByteBuffer) null);
        b.boneTexRows = initialRows;
        com.mojang.renderpearl.backend.opengl.GlStateManager._bindTexture(0);

        /* Build VAO with all attribs wired (matches shader layout). */
        b.vao = GL30C.glGenVertexArrays();
        GL30C.glBindVertexArray(b.vao);

        /* Static VBO @ binding 0 — loc 0..3, divisor 0. */
        GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, b.vbo);
        GL15C.glBindBuffer(GL15C.GL_ELEMENT_ARRAY_BUFFER, b.ebo);

        GL20C.glVertexAttribPointer(0, 3, GL11C.GL_FLOAT, false, VERTEX_STRIDE, 0L);
        GL20C.glEnableVertexAttribArray(0);
        GL33C.glVertexAttribDivisor(0, 0);

        GL20C.glVertexAttribPointer(1, 2, GL11C.GL_FLOAT, false, VERTEX_STRIDE, 12L);
        GL20C.glEnableVertexAttribArray(1);
        GL33C.glVertexAttribDivisor(1, 0);

        GL20C.glVertexAttribPointer(2, 3, GL11C.GL_FLOAT, false, VERTEX_STRIDE, 20L);
        GL20C.glEnableVertexAttribArray(2);
        GL33C.glVertexAttribDivisor(2, 0);

        GL30C.glVertexAttribIPointer(3, 1, GL11C.GL_UNSIGNED_INT, VERTEX_STRIDE, 32L);
        GL20C.glEnableVertexAttribArray(3);
        GL33C.glVertexAttribDivisor(3, 0);

        /* Instance VBO @ binding 1 — loc 4..9, divisor 1. */
        GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, b.instanceVbo);

        /* WorldMat columns 0..3 (loc 4..7), each vec4 at +0/+16/+32/+48. */
        GL20C.glVertexAttribPointer(4, 4, GL11C.GL_FLOAT, false, INSTANCE_STRIDE, 0L);
        GL20C.glEnableVertexAttribArray(4);
        GL33C.glVertexAttribDivisor(4, 1);

        GL20C.glVertexAttribPointer(5, 4, GL11C.GL_FLOAT, false, INSTANCE_STRIDE, 16L);
        GL20C.glEnableVertexAttribArray(5);
        GL33C.glVertexAttribDivisor(5, 1);

        GL20C.glVertexAttribPointer(6, 4, GL11C.GL_FLOAT, false, INSTANCE_STRIDE, 32L);
        GL20C.glEnableVertexAttribArray(6);
        GL33C.glVertexAttribDivisor(6, 1);

        GL20C.glVertexAttribPointer(7, 4, GL11C.GL_FLOAT, false, INSTANCE_STRIDE, 48L);
        GL20C.glEnableVertexAttribArray(7);
        GL33C.glVertexAttribDivisor(7, 1);

        /* PackedLight (loc 8) at offset 64, uint. */
        GL30C.glVertexAttribIPointer(8, 1, GL11C.GL_UNSIGNED_INT, INSTANCE_STRIDE, 64L);
        GL20C.glEnableVertexAttribArray(8);
        GL33C.glVertexAttribDivisor(8, 1);

        /* PackedColor (loc 9) at offset 68, uint. */
        GL30C.glVertexAttribIPointer(9, 1, GL11C.GL_UNSIGNED_INT, INSTANCE_STRIDE, 68L);
        GL20C.glEnableVertexAttribArray(9);
        GL33C.glVertexAttribDivisor(9, 1);

        /* VAT PaletteRow (loc 10) at offset 72, uint — atlas row for this
         * instance (animBaseRow + currentTick). Written 0 on the per-frame path. */
        GL30C.glVertexAttribIPointer(10, 1, GL11C.GL_UNSIGNED_INT, INSTANCE_STRIDE, 72L);
        GL20C.glEnableVertexAttribArray(10);
        GL33C.glVertexAttribDivisor(10, 1);

        GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, 0);
        GL30C.glBindVertexArray(0);
    }

    /* ================================================================
     *                      Helpers
     * ================================================================ */

    private static final Matrix4f IDENTITY = new Matrix4f();

    /** Army bake wrapper around the live extractor. When ENABLE_ARMY_BAKE,
     *  the per-tick bone matrices are recorded the first time a replay renders
     *  at a tick and replayed from cache afterwards — skipping the animator
     *  entirely (cached == live by construction; see AnimationBakeCache). */
    private static CubicMatrixRenderer extractMatricesFor(FastPathBatch.Submission s)
    {
        if (!BBSPerfConfig.ENABLE_ARMY_BAKE || s == null || s.entity == null)
        {
            return extractMatricesForLive(s);
        }

        int tick = Math.round(s.entity.getAge() + s.transition);
        float[] cached = AnimationBakeCache.get(s.entity, tick);
        if (cached != null)
        {
            CubicMatrixRenderer hit = fillExtractorFromCache(s, cached);
            if (hit != null) return hit;
            /* fall through to live if the model/extractor couldn't be built */
        }

        CubicMatrixRenderer ex = extractMatricesForLive(s);
        if (ex != null && ex.matrices != null && !ex.matrices.isEmpty())
        {
            AnimationBakeCache.put(s.entity, tick, AnimationBakeCache.flatten(ex.matrices));
        }
        return ex;
    }

    /** Background-baker entry: extract + record the bone matrices for this
     *  submission's (entity, tick) if not already cached. Public so the
     *  render-loop bypass can pre-fill the cache while idle. The entity must
     *  already be posed at the target tick (setAge + keyframes.apply) and the
     *  submission's transition must be 0 so the cache key == that tick.
     *  Returns true if a NEW frame was recorded. */
    public static boolean bakeRecord(FastPathBatch.Submission s)
    {
        if (s == null || s.entity == null) return false;
        int tick = Math.round(s.entity.getAge() + s.transition);
        if (AnimationBakeCache.get(s.entity, tick) != null) return false;
        CubicMatrixRenderer ex = extractMatricesForLive(s);
        if (ex != null && ex.matrices != null && !ex.matrices.isEmpty())
        {
            AnimationBakeCache.put(s.entity, tick, AnimationBakeCache.flatten(ex.matrices));
            return true;
        }
        return false;
    }

    /** Extract the current model-local bone matrices for a submission WITHOUT
     *  caching — used to fingerprint a replay's pose at sample ticks for the
     *  per-unique-animation signature. Entity must already be posed at the
     *  target tick. Returns flat column-major floats, or null. */
    public static float[] extractFlat(FastPathBatch.Submission s)
    {
        CubicMatrixRenderer ex = extractMatricesForLive(s);
        if (ex == null || ex.matrices == null || ex.matrices.isEmpty()) return null;
        return AnimationBakeCache.flatten(ex.matrices);
    }

    /** Build/reuse a pooled CubicMatrixRenderer for this model and fill its
     *  bone matrices from cached column-major floats (no animator run). */
    private static CubicMatrixRenderer fillExtractorFromCache(FastPathBatch.Submission s, float[] cached)
    {
        ModelInstance instance = s.instance;
        if (instance == null || !(instance.model instanceof Model cubic)) return null;

        int boneCount = cached.length / 16;
        String modelId = instance.id;
        HashMap<String, CubicMatrixRenderer> map = EXTRACTOR_TL.get();
        CubicMatrixRenderer ex = modelId == null ? null : map.get(modelId);
        if (ex == null)
        {
            ex = new CubicMatrixRenderer(cubic);
            if (modelId != null) map.put(modelId, ex);
        }

        while (ex.matrices.size() < boneCount) ex.matrices.add(new Matrix4f());

        float[] tmp = new float[16];
        for (int bi = 0; bi < boneCount; bi++)
        {
            System.arraycopy(cached, bi * 16, tmp, 0, 16);
            ex.matrices.get(bi).set(tmp);
        }
        return ex;
    }

    private static CubicMatrixRenderer extractMatricesForLive(FastPathBatch.Submission s)
    {
        ModelFormRenderer self = s.renderer;
        if (self == null) return null;
        ModelFormRendererAccessor selfA = (ModelFormRendererAccessor) (Object) self;

        ModelInstance instance = s.instance;
        if (instance == null) return null;
        if (!(instance.model instanceof Model cubic)) return null;

        selfA.bbstools$ensureAnimator(s.transition);
        IAnimator animator = selfA.bbstools$getAnimator();
        if (animator == null) return null;

        instance.model.resetPose();
        animator.applyActions(s.entity, instance, s.transition);
        instance.model.applyPose(selfA.bbstools$getPose());

        /* Bone matrices are extracted in MODEL-LOCAL space (NO world
         * matrix applied) — we apply world transform separately in
         * the vertex shader from the per-instance attribs. */
        PoseStack stack = new PoseStack();

        CubicMatrixRenderer ex;
        if (BBSPerfConfig.ENABLE_TIER3_POOLS)
        {
            String modelId = instance.id;
            HashMap<String, CubicMatrixRenderer> map = EXTRACTOR_TL.get();
            ex = modelId == null ? null : map.get(modelId);
            if (ex == null)
            {
                ex = new CubicMatrixRenderer(cubic);
                if (modelId != null) map.put(modelId, ex);
                BBSPerfConfig.tier3ExtractorFresh++;
            }
            else
            {
                BBSPerfConfig.tier3ExtractorReuses++;
            }
        }
        else
        {
            ex = new CubicMatrixRenderer(cubic);
        }
        CubicRenderer.processRenderModel(ex, null, stack, cubic);
        return ex;
    }

    private static void ensureExtractorPool(int needed)
    {
        if (EXTRACTOR_POOL.length < needed)
        {
            EXTRACTOR_POOL = new CubicMatrixRenderer[Math.max(needed, EXTRACTOR_POOL.length * 2)];
        }
        if (CACHED_FLAT_POOL.length < needed)
        {
            CACHED_FLAT_POOL = new float[Math.max(needed, CACHED_FLAT_POOL.length * 2)][];
        }
    }

    /** Cheap cache lookup for PHASE A: the column-major flat bone array for this
     *  submission's (entity, tick), or null on miss. No Matrix4f allocation. */
    private static float[] bbstools$cachedFlatFor(FastPathBatch.Submission s)
    {
        if (!BBSPerfConfig.ENABLE_TIER2H_FASTPACK) return null;
        if (!BBSPerfConfig.ENABLE_ARMY_BAKE || s == null || s.entity == null) return null;
        int tick = Math.round(s.entity.getAge() + s.transition);
        return AnimationBakeCache.get(s.entity, tick);
    }

    /** PHASE A per-slot: cache hit → keep the flat array (no Matrix4f build),
     *  miss → live extract (animates + fills the cache). */
    private static void bbstools$phaseAExtract(FastPathBatch.Submission s, int i)
    {
        float[] cf = bbstools$cachedFlatFor(s);
        CACHED_FLAT_POOL[i] = cf;
        EXTRACTOR_POOL[i] = (cf != null) ? null : extractMatricesFor(s);
    }

    /* ================================================================
     *                      Idle-reuse (FPS lever)
     * ================================================================ */

    /** Per-bucket signature output (render thread only → plain statics). */
    private static long    bbstools$sigA = 0L;
    private static long    bbstools$sigB = 0L;
    private static boolean bbstools$sigPoseSampled = false;

    /** Cheap, camera-INDEPENDENT signature of a bucket. Folds every replay's
     *  world translation + scale/rot diagonal + light (catches any move,
     *  membership/order change, light change), plus the cached bone palette of
     *  up to ~16 sampled instances (catches animation/scrub/edit because the
     *  cache is keyed by the entity's CURRENT age). sigPoseSampled is false if
     *  the crowd isn't fully baked (any sampled cache miss) → caller won't reuse
     *  (we only have an authoritative pose signal once baked). O(n) world scan +
     *  ~16 cache lookups — orders of magnitude cheaper than Phase U + upload. */
    private static void bbstools$computeSig(List<FastPathBatch.Submission> list, int n)
    {
        long h1 = 1125899906842597L ^ (long) n;
        long h2 = 1469598103934665603L;
        boolean poseSampled = n > 0;
        int step = Math.max(1, n / 16);

        for (int i = 0; i < n; i++)
        {
            FastPathBatch.Submission s = list.get(i);
            Matrix4f w = s.worldMatrix;
            h1 = 31L * h1 + Float.floatToRawIntBits(w.m30());
            h1 = 31L * h1 + Float.floatToRawIntBits(w.m31());
            h1 = 31L * h1 + Float.floatToRawIntBits(w.m32());
            h1 = 31L * h1 + Float.floatToRawIntBits(w.m00());
            h1 = 31L * h1 + Float.floatToRawIntBits(w.m11());
            h1 = 31L * h1 + Float.floatToRawIntBits(w.m22());
            h2 = 31L * h2 + s.light;

            if (poseSampled && (i % step) == 0)
            {
                float[] cf = bbstools$cachedFlatFor(s);
                if (cf == null || cf.length == 0)
                {
                    poseSampled = false;
                }
                else
                {
                    h2 = 31L * h2 + Float.floatToRawIntBits(cf[0]);
                    h2 = 31L * h2 + Float.floatToRawIntBits(cf[cf.length >> 1]);
                    h2 = 31L * h2 + Float.floatToRawIntBits(cf[cf.length - 1]);
                }
            }
        }

        bbstools$sigA = h1;
        bbstools$sigB = h2;
        bbstools$sigPoseSampled = poseSampled;
    }

    /** Reuse path: the instance VBO + bone texture already hold valid data from
     *  the last full pack. Bind them, push the (possibly new) camera uniforms,
     *  and re-issue the one instanced draw. NO Phase A, NO Phase U, NO upload.
     *  GL state handling mirrors the PHASE G block exactly minus the uploads. */
    private static void bbstools$bindAndDraw(MeshBundle b, List<FastPathBatch.Submission> list, int drawableN, int mode)
    {
        Link texture = list.get(0).form.texture.get();
        if (texture == null) texture = list.get(0).instance.getTexture();
        b.lastTexture = texture;   /* cache so an idle frame can redraw without a list */
        bbstools$drawCore(b, texture, drawableN, mode);
    }

    /* ---- Crowd GPU timer (ARB_timer_query, double-buffered, read 1 frame late;
     *  no glFinish → no pipeline stall). Wraps the idle crowd redraw so
     *  BBSPerfConfig.crowdGpuUs reports the crowd's pure GPU cost — the number
     *  that settles whether the idle "100fps loss" is GPU (fragment/shadow) or
     *  CPU. Self-disables on any GL error. ---- */
    private static int[]   bbstools$gpuQ = null;          /* 2 query object ids */
    private static int     bbstools$gpuQi = 0;            /* index issued this frame */
    private static final boolean[] bbstools$gpuQIssued = new boolean[2];
    private static boolean bbstools$gpuActive = false;    /* a glBeginQuery is open */
    private static boolean bbstools$gpuBroken = false;

    private static void bbstools$gpuTimerBegin()
    {
        if (!BBSPerfConfig.ENABLE_GPU_TIMER || bbstools$gpuBroken) return;
        try
        {
            if (bbstools$gpuQ == null)
            {
                bbstools$gpuQ = new int[]{ GL15C.glGenQueries(), GL15C.glGenQueries() };
                bbstools$gpuQIssued[0] = false;
                bbstools$gpuQIssued[1] = false;
            }
            /* Read the OTHER query (issued last frame → finished by now). */
            int prev = 1 - bbstools$gpuQi;
            if (bbstools$gpuQIssued[prev])
            {
                int avail = GL15C.glGetQueryObjecti(bbstools$gpuQ[prev], GL15C.GL_QUERY_RESULT_AVAILABLE);
                if (avail != GL11C.GL_FALSE)
                {
                    long ns = GL33C.glGetQueryObjecti64(bbstools$gpuQ[prev], GL15C.GL_QUERY_RESULT);
                    BBSPerfConfig.crowdGpuUs = ns / 1000L;
                    bbstools$gpuQIssued[prev] = false;
                }
            }
            GL15C.glBeginQuery(GL33C.GL_TIME_ELAPSED, bbstools$gpuQ[bbstools$gpuQi]);
            bbstools$gpuActive = true;
        }
        catch (Throwable t) { bbstools$gpuBroken = true; }
    }

    private static void bbstools$gpuTimerEnd()
    {
        if (!bbstools$gpuActive) return;
        try { GL15C.glEndQuery(GL33C.GL_TIME_ELAPSED); }
        catch (Throwable t) { bbstools$gpuBroken = true; }
        finally
        {
            bbstools$gpuQIssued[bbstools$gpuQi] = true;
            bbstools$gpuQi = 1 - bbstools$gpuQi;
            bbstools$gpuActive = false;
        }
    }

    /** Re-issue every cached bundle's last instanced draw with NO new submission /
     *  pack / upload — used when the bypass detects an idle frame (camera + film
     *  tick + crowd composition unchanged) and skips the whole snapshot+submit
     *  prep. The GL buffers + bone texture/atlas already hold last frame's data. */
    public static void redrawAllBundles()
    {
        if (GpuBackend.isVulkan())
        {
            requestIdleRedraw();
            return;
        }
        Tier2hGlZustand vorher = new Tier2hGlZustand();
        bbstools$gpuTimerBegin();
        try
        {
            for (MeshBundle b : BUNDLES.values())
            {
                if (b != null && b.everDrawn && b.lastDrawableN > 0 && b.lastTexture != null)
                {
                    bbstools$drawCore(b, b.lastTexture, b.lastDrawableN, b.lastDrawMode);
                }
            }
        }
        finally
        {
            bbstools$gpuTimerEnd();
            vorher.zurueck();
        }
    }

    private static void bbstools$drawCore(MeshBundle b, Link texture, int drawableN, int mode)
    {

        /* Bone palette → unit 1 (already populated; no upload). VAT mode reuses
         * the persisted atlas texture, per-frame mode the per-instance bone tex. */
        Tier2hGlZustand.einheit(org.lwjgl.opengl.GL13C.GL_TEXTURE1);
        com.mojang.renderpearl.backend.opengl.GlStateManager._bindTexture(mode == 1 ? b.atlasTex : b.boneTex);

        /* Diffuse → unit 0. */
        Tier2hGlZustand.einheit(org.lwjgl.opengl.GL13C.GL_TEXTURE0);
        if (texture != null)
        {
            try { BBSModClient.getTextures().bindTexture(texture); } catch (Throwable ig) {}
        }

        Tier2hGlZustand zustand = new Tier2hGlZustand();
        GL11C.glEnable(GL11C.GL_DEPTH_TEST);
        GL11C.glDepthFunc(GL11C.GL_GEQUAL);
        GL11C.glDepthMask(true);
        GL11C.glDisable(GL11C.GL_CULL_FACE);

        float[] mat4Tmp = new float[16];
        try
        {
            GL20C.glUseProgram(Tier2hProgramm.nummer());

            Matrix4f mv  = RenderSystem.getModelViewMatrixCopy();
            Matrix4f prj = mchorse.bbs_mod.graphics.Projektion.lesen();
            mv.get(mat4Tmp);  GL20C.glUniformMatrix4fv(Tier2hProgramm.uModelViewMat, false, mat4Tmp);
            prj.get(mat4Tmp); GL20C.glUniformMatrix4fv(Tier2hProgramm.uProjMat, false, mat4Tmp);
            if (Tier2hProgramm.uSampler0 >= 0)    GL20C.glUniform1i(Tier2hProgramm.uSampler0, 0);
            if (Tier2hProgramm.uBoneTex >= 0)     GL20C.glUniform1i(Tier2hProgramm.uBoneTex, 1);
            if (Tier2hProgramm.uUseLightmap >= 0) GL20C.glUniform1i(Tier2hProgramm.uUseLightmap, 0);
            if (Tier2hProgramm.uUseVAT >= 0)      GL20C.glUniform1i(Tier2hProgramm.uUseVAT, mode);

            GL30C.glBindVertexArray(b.vao);
            GL31C.glDrawElementsInstanced(GL11C.GL_TRIANGLES, b.indexCount,
                GL11C.GL_UNSIGNED_INT, 0L, drawableN);
        }
        finally
        {
            zustand.zurueck();
            GL20C.glUseProgram(0);
            GL30C.glBindVertexArray(0);
            GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, 0);
            com.mojang.renderpearl.backend.opengl.GlStateManager._bindTexture(0);
            Tier2hGlZustand.einheit(org.lwjgl.opengl.GL13C.GL_TEXTURE1);
            com.mojang.renderpearl.backend.opengl.GlStateManager._bindTexture(0);
        }
    }

    /* ================================================================
     *                  VAT (animation-texture) path
     * ================================================================ */

    /** (Re)build the per-bundle VAT atlas if the bake progressed or the drawn
     *  entity set changed. Atlas rows = uniqueAnimGroups × ticksPerGroup; each
     *  group's per-tick palette is uploaded once. Sets b.atlasValid=false (→
     *  caller falls back to the per-frame path) when: not baked yet, an entity
     *  isn't bound to a group, or the atlas would exceed GL_MAX_TEXTURE_SIZE.
     *  Cheap no-op when the fingerprint is unchanged (the common steady state). */
    private static void bbstools$buildAtlasIfNeeded(MeshBundle b, List<FastPathBatch.Submission> list, int n)
    {
        if (!BBSPerfConfig.ENABLE_TIER2H_VAT) { b.atlasValid = false; return; }

        int maxTick = AnimationBakeCache.maxTick();
        if (maxTick < 0) { b.atlasValid = false; return; } /* nothing baked */
        int ticksPerGroup = maxTick + 1;

        /* Fingerprint: rebuild only when the bake advanced, the unique-anim set
         * changed, or the drawn count changed. Stable once fully baked. */
        long fp = (((long) AnimationBakeCache.signatureGroups()) * 1000003L)
                ^ (BBSPerfConfig.armyBakeFrames * 2654435761L)
                ^ (((long) ticksPerGroup) << 20)
                ^ ((long) n << 40);
        if (b.atlasValid && fp == b.atlasFingerprint) return;

        /* Collect distinct anim groups across the drawn instances. */
        java.util.IdentityHashMap<Object, Integer> groupIdx = new java.util.IdentityHashMap<>();
        java.util.ArrayList<Object> groups = new java.util.ArrayList<>();
        java.util.IdentityHashMap<IEntity, Integer> entityBaseRow = new java.util.IdentityHashMap<>();
        int cap = Math.min(n, MAX_INSTANCES);
        for (int i = 0; i < cap; i++)
        {
            IEntity e = list.get(i).entity;
            if (e == null) { b.atlasValid = false; b.atlasFingerprint = fp; return; }
            Object g = AnimationBakeCache.groupOf(e);
            if (g == null) { b.atlasValid = false; b.atlasFingerprint = fp; return; }
            Integer gi = groupIdx.get(g);
            if (gi == null) { gi = groups.size(); groupIdx.put(g, gi); groups.add(g); }
            entityBaseRow.put(e, gi * ticksPerGroup);
        }

        int numGroups = groups.size();
        long totalRowsL = (long) numGroups * (long) ticksPerGroup;
        int maxTexSize = GL11C.glGetInteger(GL11C.GL_MAX_TEXTURE_SIZE);
        if (numGroups == 0 || totalRowsL > maxTexSize)
        {
            /* Too many unique long anims to atlas → keep the per-frame path. */
            b.atlasValid = false;
            b.atlasFingerprint = fp;
            BBSPerfConfig.tier2hVatFallbacks++;
            return;
        }
        int totalRows = (int) totalRowsL;

        /* Pack all group×tick palettes into a scratch buffer (row-major, one
         * palette per row, boneTexWidth texels wide). Missing ticks → identity. */
        int width = b.boneTexWidth;            /* = boneCount * 4 texels */
        int floatsPerRow = width * 4;          /* RGBA32F */
        ByteBuffer scratch = ensureBoneScratch(totalRows * floatsPerRow * 4);
        scratch.clear();
        FloatBuffer fb = scratch.asFloatBuffer();
        float[] identity = new float[16];
        IDENTITY.get(identity);
        int bonesPerInstance = b.boneCount;

        for (int gi = 0; gi < numGroups; gi++)
        {
            Object g = groups.get(gi);
            int baseRow = gi * ticksPerGroup;
            for (int t = 0; t < ticksPerGroup; t++)
            {
                int rowFloatBase = (baseRow + t) * floatsPerRow;
                float[] pal = AnimationBakeCache.paletteOf(g, t);
                fb.position(rowFloatBase);
                if (pal != null)
                {
                    int avail = pal.length / 16;
                    int copyBones = Math.min(avail, bonesPerInstance);
                    fb.put(pal, 0, copyBones * 16);
                    for (int bi = copyBones; bi < bonesPerInstance; bi++)
                        fb.put(identity);
                }
                else
                {
                    for (int bi = 0; bi < bonesPerInstance; bi++)
                        fb.put(identity);
                }
            }
        }

        /* Upload the whole atlas ONCE into the bundle's atlas texture. */
        if (b.atlasTex == 0) b.atlasTex = GL11C.glGenTextures();
        com.mojang.renderpearl.backend.opengl.GlStateManager._bindTexture(b.atlasTex);
        if (totalRows > b.atlasRowsCap || b.atlasRowsCap == 0)
        {
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_NEAREST);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_NEAREST);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, GL12C.GL_CLAMP_TO_EDGE);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, GL12C.GL_CLAMP_TO_EDGE);
            GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL30C.GL_RGBA32F,
                width, totalRows, 0, GL11C.GL_RGBA, GL11C.GL_FLOAT, (java.nio.ByteBuffer) null);
            b.atlasRowsCap = totalRows;
        }
        GL11C.glPixelStorei(GL11C.GL_UNPACK_ALIGNMENT, 4);
        GL11C.glPixelStorei(GL12C.GL_UNPACK_ROW_LENGTH, 0);
        GL11C.glPixelStorei(GL12C.GL_UNPACK_SKIP_ROWS, 0);
        GL11C.glPixelStorei(GL12C.GL_UNPACK_SKIP_PIXELS, 0);
        scratch.position(0).limit(width * totalRows * 16);
        GL11C.glTexSubImage2D(GL11C.GL_TEXTURE_2D, 0, 0, 0,
            width, totalRows, GL11C.GL_RGBA, GL11C.GL_FLOAT, scratch);
        com.mojang.renderpearl.backend.opengl.GlStateManager._bindTexture(0);

        b.atlasUsedRows       = totalRows;
        b.atlasTicksPerGroup  = ticksPerGroup;
        b.atlasEntityBaseRow  = entityBaseRow;
        b.atlasValid          = true;
        b.atlasFingerprint    = fp;
        BBSPerfConfig.tier2hVatRebuilds++;
        BBSPerfConfig.tier2hVatAtlasRows = totalRows;
        BBSPerfConfig.tier2hVatGroups    = numGroups;
        LOG.info("[tier2h-vat] atlas rebuilt groups={} ticksPerGroup={} rows={} ({}x{} RGBA32F, {} KB)",
            numGroups, ticksPerGroup, totalRows, width, totalRows, (width * totalRows * 16L) / 1024L);
    }

    /** VAT draw: pack only the per-instance VBO (worldMat+light+color+paletteRow,
     *  NO bones), upload it, bind the persisted atlas, and issue one instanced
     *  draw. Returns drawableN, or -1 if any instance lacks an atlas row this
     *  frame (caller falls back to the per-frame path and forces an atlas rebuild). */
    private static int bbstools$drawBucketVat(MeshBundle b, List<FastPathBatch.Submission> list, int n, long nowMs)
    {
        int ticksPerGroup = b.atlasTicksPerGroup;
        ByteBuffer iScratch = ensureInstanceScratch(n * INSTANCE_STRIDE);
        iScratch.clear();
        float[] mat4Tmp = new float[16];

        int drawableN = 0;
        for (int i = 0; i < n; i++)
        {
            FastPathBatch.Submission s = list.get(i);
            IEntity e = s.entity;
            Integer baseRow = (e == null) ? null : b.atlasEntityBaseRow.get(e);
            if (baseRow == null)
            {
                /* Entity set changed since the atlas was built → bail this frame,
                 * force a rebuild next frame, let the caller use the per-frame path. */
                b.atlasValid = false;
                b.atlasFingerprint = -1L;
                return -1;
            }
            int tick = Math.round(s.entity.getAge() + s.transition);
            if (tick < 0) tick = 0;
            else if (tick >= ticksPerGroup) tick = ticksPerGroup - 1;
            int paletteRow = baseRow + tick;

            iScratch.position(drawableN * INSTANCE_STRIDE);
            Tier2hVertexPacking.glInstance(iScratch, s, paletteRow);

            drawableN++;
        }

        if (drawableN == 0) return 0;

        iScratch.position(0).limit(drawableN * INSTANCE_STRIDE);

        /* Upload the tiny instance VBO (no bone data — bones live in the atlas). */
        int wantedInstBytes = drawableN * INSTANCE_STRIDE;
        if (wantedInstBytes > b.instanceVboCap)
        {
            int newCap = Math.max(wantedInstBytes, b.instanceVboCap * 2);
            GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, b.instanceVbo);
            GL15C.glBufferData(GL15C.GL_ARRAY_BUFFER, newCap, GL15C.GL_DYNAMIC_DRAW);
            b.instanceVboCap = newCap;
        }
        GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, b.instanceVbo);
        GL15C.glBufferSubData(GL15C.GL_ARRAY_BUFFER, 0L, iScratch);
        GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, 0);

        bbstools$bindAndDraw(b, list, drawableN, 1);

        BBSPerfConfig.tier2hUploadedBytes += wantedInstBytes;
        BBSPerfConfig.tier2hVatDraws++;

        b.everDrawn     = true;
        b.lastDrawableN = drawableN;
        b.reuseSigA     = bbstools$sigA;
        b.reuseSigB     = bbstools$sigB;
        b.lastPackMs    = nowMs;
        b.lastDrawMode  = 1;
        return drawableN;
    }

    private static ByteBuffer ensureInstanceScratch(int needed)
    {
        if (INSTANCE_SCRATCH == null || INSTANCE_SCRATCH_CAP < needed)
        {
            int newCap = Math.max(needed, INSTANCE_SCRATCH_CAP * 2);
            if (INSTANCE_SCRATCH != null) MemoryUtil.memFree(INSTANCE_SCRATCH);
            INSTANCE_SCRATCH = MemoryUtil.memAlloc(newCap).order(ByteOrder.nativeOrder());
            INSTANCE_SCRATCH_CAP = newCap;
        }
        return INSTANCE_SCRATCH;
    }

    private static ByteBuffer ensureBoneScratch(int needed)
    {
        if (BONE_SCRATCH == null || BONE_SCRATCH_CAP < needed)
        {
            int newCap = Math.max(needed, BONE_SCRATCH_CAP * 2);
            if (BONE_SCRATCH != null) MemoryUtil.memFree(BONE_SCRATCH);
            BONE_SCRATCH = MemoryUtil.memAlloc(newCap).order(ByteOrder.nativeOrder());
            BONE_SCRATCH_CAP = newCap;
        }
        return BONE_SCRATCH;
    }

    /* ================================================================
     *                      Counters / status
     * ================================================================ */

    public static String snapshot()
    {
        long flushes = BBSPerfConfig.tier2hFlushes;
        double avgPhaseAMs = flushes == 0 ? 0 : (BBSPerfConfig.tier2hPhaseANsAccum / (double) flushes) / 1_000_000.0;
        double avgPhaseUMs = flushes == 0 ? 0 : (BBSPerfConfig.tier2hPhaseUNsAccum / (double) flushes) / 1_000_000.0;
        double avgPhaseGMs = flushes == 0 ? 0 : (BBSPerfConfig.tier2hPhaseGNsAccum / (double) flushes) / 1_000_000.0;
        long avgUploadKB = flushes == 0 ? 0 : (BBSPerfConfig.tier2hUploadedBytes / flushes) / 1024;
        return String.format(
            "flushes=%d buckets=%d fallback=%d subs=%d draws=%d idleReuses=%d vat[on=%s draws=%d rebuilds=%d fallbacks=%d rows=%d groups=%d] phaseA=%.2fms phaseU=%.2fms phaseG=%.2fms avgUploadKB=%d disabled=%s failures=%d bundles=%d",
            flushes,
            BBSPerfConfig.tier2hBucketsProcessed,
            BBSPerfConfig.tier2hBucketsFallback,
            BBSPerfConfig.tier2hSubmissions,
            BBSPerfConfig.tier2hInstancedDraws,
            BBSPerfConfig.tier2hIdleReuses,
            BBSPerfConfig.ENABLE_TIER2H_VAT,
            BBSPerfConfig.tier2hVatDraws,
            BBSPerfConfig.tier2hVatRebuilds,
            BBSPerfConfig.tier2hVatFallbacks,
            BBSPerfConfig.tier2hVatAtlasRows,
            BBSPerfConfig.tier2hVatGroups,
            avgPhaseAMs, avgPhaseUMs, avgPhaseGMs,
            avgUploadKB, AUTO_DISABLED,
            BBSPerfConfig.tier2hFailures,
            BUNDLES.size());
    }

    public static void resetCounters()
    {
        LOGGED_FIRST_FLUSH = false;
        LOGGED_FIRST_FAILURE = false;
        BBSPerfConfig.tier2hFlushes = 0L;
        BBSPerfConfig.tier2hBucketsProcessed = 0L;
        BBSPerfConfig.tier2hBucketsFallback = 0L;
        BBSPerfConfig.tier2hSubmissions = 0L;
        BBSPerfConfig.tier2hInstancedDraws = 0L;
        BBSPerfConfig.tier2hPhaseANsAccum = 0L;
        BBSPerfConfig.tier2hPhaseUNsAccum = 0L;
        BBSPerfConfig.tier2hPhaseGNsAccum = 0L;
        BBSPerfConfig.tier2hUploadedBytes = 0L;
        BBSPerfConfig.tier2hFailures = 0L;
        BBSPerfConfig.tier2hIdleReuses = 0L;
        BBSPerfConfig.tier2hVatDraws = 0L;
        BBSPerfConfig.tier2hVatRebuilds = 0L;
        BBSPerfConfig.tier2hVatFallbacks = 0L;
    }

}
