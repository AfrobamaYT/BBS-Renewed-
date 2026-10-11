package com.afrobama.bbstools.perf;

/**
 * Live-tweakable performance knobs for BBS rendering, read from the render
 * thread (mixins) and written from the command thread. Volatile is sufficient
 * — racy reads are harmless (briefly missing one frame's cull doesn't matter).
 * Values reset to defaults on game restart.
 *
 * Migrated 2026-05-08 from the BBS-CML-EDITION fork's
 * {@code mchorse.bbs_mod.perf.BBSPerfConfig}.
 */
public final class BBSPerfConfig
{
    private BBSPerfConfig()
    {
    }

    /** Build stamp — bumped every deploy so the loaded jar can be confirmed via
     *  {@code /eval?expr=BBSPerfConfig.BUILD_STAMP} without guessing whether MC
     *  picked up a hot-overwritten jar (it never does — needs a restart). */
    public static final String BUILD_STAMP = "2026-05-24-undo-memo-inject-r39";

    /** Whole-world-render GPU time (GL_TIMESTAMP at WorldRenderEvents START/END),
     *  microseconds, updated each frame with ~1-frame latency. Compared against the
     *  world-phase WALL time (FrameTimeline) settles GPU-bound vs CPU-bound:
     *    worldGpuUs ~= world-wall  -> GPU-bound (vertex/fill) ; reduce geometry/res
     *    worldGpuUs <<  world-wall -> CPU-bound (draw submission/buffer upload) */
    public static volatile boolean ENABLE_WORLD_GPU_TIMER = true;
    public static volatile long worldGpuUs = 0L;
    /** GPU timestamp of the most recent full frame (END - START of GameRenderer
     *  if wired) — currently world-render only. */
    public static volatile long worldGpuFrames = 0L;

    /** Tier 1a (AccelRender) safety cap: maximum instances per frame the
     *  AccelRender GPU compute path will dispatch. Even though our CPU
     *  Phase 1a culls to MAX_VISIBLE_BAKE (20k), GPU compute pipeline
     *  can't sustain 20k same-mesh instances per frame at 30+ FPS without
     *  driver TDR / freeze on camera-pan. Cap to 5000 = safe sweet spot.
     *  Beyond this, fall back to CPU bake path (slower but stable). */
    /* REVERTED to 500 after cap=5000 PC-freeze regression 2026-05-17 r3:
     *  even with Layer 1 + Layer 2 mixins ACTIVE (confirmed via log
     *  "sharingBuffer pre-sized 4 MB"), 5000 same-mesh instances under
     *  Iris shaderpack still froze the PC. Java heap hit 96% allocated
     *  and GPU 100% simultaneously — combined CPU+GPU exhaustion that
     *  Layer 1's Sync timeout couldn't recover from. 500 is the
     *  empirically-proven stable ceiling for our (RTX 3080, 6GB
     *  allocated, Iris Complementary) configuration. */
    public static volatile int ACCEL_RENDER_MAX_VISIBLE = 500;

    /** Circuit-breaker for AccelRender path: if a single frame's accel
     *  dispatch time exceeds this threshold (ms), the path is auto-
     *  disabled for the rest of the session. Protects against GPU
     *  driver TDR / Windows freeze that we've observed when AccelRender's
     *  internal compute pipeline cannot keep up. User can re-enable
     *  via /bbstools accelrender on after addressing root cause. */
    /* TIGHTENED 2026-05-17 r3: 30ms threshold + 2-strike. Less false-
     *  trip-resistant than 60ms/3-strike, but at cap=500 a healthy
     *  frame should be 3-5ms — 30ms is already 6× slower than steady-
     *  state, indicates cascade beginning. 2 strikes = disable in
     *  ~67ms instead of waiting 100ms+ which can already initiate
     *  driver TDR. */
    public static volatile double ACCEL_RENDER_SLOW_FRAME_MS = 30.0;
    public static volatile int ACCEL_RENDER_SLOW_FRAME_STRIKES = 2;

    public static volatile boolean ENABLE_DISTANCE_CULLING = true;
    /** Effectively infinite (4096² = 16.7M, beyond MC's 32-chunk render
     *  distance × 16 b = 512 b view distance anyway). User explicitly
     *  said BBS replays must NEVER disappear because they move them
     *  around the world freely during shots — any distance cap risks
     *  ruining a take. The cull only activates if the user manually
     *  dials this back via /bbstools cinematic-distance. */
    public static volatile double MAX_RENDER_DISTANCE_SQ = 4096.0D * 4096.0D;
    public static volatile double SHADOW_CULLING_DISTANCE_SQ = 48.0D * 48.0D;
    public static volatile boolean SKIP_ALL_SHADOW_PASSES = false;

    public static volatile boolean ENABLE_LOD = true;
    /** Bumped from 32² to 64² on 2026-05-10. The 32-block radius was
     *  silently dropping helmets / props / equipment for actors >32
     *  blocks away in actual cinematic shots (renderBodyParts recurses
     *  into the entire form-stack — not just cosmetic detail). 64²
     *  keeps full detail for any actor visible at typical cinematic
     *  framing while still saving the work for very-distant LOD. */
    public static volatile double LOD_DETAIL_RADIUS_SQ = 64.0D * 64.0D;

    /** When true, the block-destruction clip prints per-fire diagnostics
     *  to the in-game chat. Default OFF — was on during the 2026-05-10
     *  duplicate-spawn hunt, no longer needed for normal recording.
     *  LOG.info side stays active regardless for record-keeping.
     *  Toggle via /bbstools debug-everything settings. */
    public static volatile boolean VERBOSE_DESTRUCTION_CHAT = false;

    /** Master switch for the Veil-instanced replay batch renderer
     *  ({@code BaseFilmControllerVeilMixin}). When true AND every replay
     *  in the current film passes the conservative-fallback gate
     *  (bone count, no stencil map, BOBJ-backed), BBS's per-replay
     *  render loop is cancelled and the batch path is taken instead.
     *  Enabled by user request 2026-05-16. If replays disappear or
     *  glitch, falls back to vanilla on a per-frame basis automatically
     *  (try/catch around the flush); also revertable via
     *  {@code BBSToolsConfig.enable_veil_batch = false}.
     *
     *  DEFAULT FLIPPED 2026-05-18: was true, broke replay visibility
     *  after the Tier 3b atlas-prime step + Tier 1c classification
     *  changes. The batch path now silently drops replays when picker
     *  is not active. User reported "im echten spiel sind die replays
     *  unsichtbar". Reverted to OFF; vanilla render path is the safe
     *  default. Opt-in via /bbsdebug feature veil-batch on once the
     *  Tier 3b/1c interaction is debugged. */
    public static volatile boolean ENABLE_VEIL_BATCH = false;

    /** Hard cap on bones-per-model the batch renderer will accept.
     *  Anything heavier falls back to vanilla BBS rendering for that
     *  frame. Matches an SSBO mat4[128] layout (8 KiB / instance). */
    public static volatile int VEIL_BATCH_MAX_BONES = 128;

    /** iter 76 — when true, replays with equipment (items/armor) are
     *  still allowed into the batch path. The batched render path
     *  does NOT render the items themselves (the GPU shader only does
     *  the body mesh), but the body is GPU-instanced.
     *
     *  Default ON (2026-05-17): user reported all 201 test replays
     *  failing the equipment gate, blocking 100% of the batch path.
     *  Items-not-rendering trade-off accepted to unlock the FPS gain.
     *  Toggle false to restore strict equipment-gating. */
    public static volatile boolean VEIL_BATCH_ALLOW_EQUIPPED = true;

    /** iter 77 — when true, `FormProperties.applyProperty` is cancelled
     *  at HEAD for any KeyframeChannel that has zero keyframes. Targets
     *  the per-tick reflective `FormUtils.getProperty` walk that runs
     *  even on empty channels (verified via 2026-05-17 source audit).
     *
     *  At 30 bone channels × 200 replays × 20 TPS = 120 k reflective
     *  property walks/sec just for empty pose-track bones. Cancelling
     *  at HEAD skips both the lookup AND the per-tick
     *  `setRuntimeValue(null)` call (defensive overkill — actual reset
     *  goes through `resetProperties()` on real reset paths).
     *
     *  Toggle false if anything regresses around channel reset behaviour. */
    public static volatile boolean ENABLE_EMPTY_CHANNEL_SKIP = true;

    /** Tier 3f — observability + lazy first-touch tracking for
     *  KeyframeChannel. When true, every constructed KeyframeChannel
     *  is counted (constructor TAIL inject); the first time
     *  findSegment/interpolate is called on a channel, we bump
     *  {@link #lazyKeyframeChannelBuilds} and log the channel id +
     *  first-touch tick (one line per channel, ever). The delta
     *  {@code totalChannels - lazyKeyframeChannelBuilds} tells us
     *  how many channels never get read — i.e., how much the per-tick
     *  FormProperties walk wastes on empty pose tracks even AFTER
     *  Tier 3e's ENABLE_EMPTY_CHANNEL_SKIP.
     *
     *  Default OFF — opt-in until verified stable. The book-keeping
     *  itself is cheap (one WeakHashMap.containsKey + put per channel
     *  for exactly the first read), but log spam on huge films could
     *  be annoying. Toggle via /bbsdebug keyframes on. */
    public static volatile boolean ENABLE_LAZY_KEYFRAME_CHANNEL = false;

    /** Per first-touch build counter. Incremented exactly once per
     *  KeyframeChannel instance (the first time findSegment or
     *  interpolate is invoked on it). Compare to
     *  {@code LazyKeyframeChannel.INSTANCE.totalChannels} to see
     *  wasted-channel ratio. */
    public static volatile long lazyKeyframeChannelBuilds = 0L;


    public static volatile boolean CROWD_IRIS_FAST_PATH = false;

    /** Crowd frustum culling. DEFAULT OFF per user: everything must stay
     *  fully visible regardless of camera angle (mirrors the no-distance-cull
     *  rule for BBS replays). No LODs ever (user rule). */
    public static volatile boolean ENABLE_CROWD_FRUSTUM_CULL = false;

    /** afrobama 2026-05-31 — pool the per-frame instance ByteBuffers (gbuffer + shadow) instead of
     *  allocateDirect every frame (~4MB at 200k). Default ON (pure native-mem/GC win, no behavior
     *  change). Toggle off to A/B. */
    public static volatile boolean ENABLE_CROWD_BUFFER_POOL = true;

    /** Tier 1d — when true, the UIReplayList panel collapses to a compact
     *  "crowd strip" (one row per actor type with a count) and skips the
     *  per-replay thumbnail bake inside renderElementPart. Massively
     *  reduces UI render cost when a film has 100+ replays loaded
     *  (each row otherwise calls FormUtilsClient.renderUI per frame =
     *  hundreds of mini-Form-renders).
     *
     *  Default OFF (opt-in). Toggle via /bbstools ui crowdmode on|off or
     *  /bbsdebug ui-crowdmode on. Aggregation uses Form.getDisplayName(). */
    public static volatile boolean UI_REPLAY_LIST_CROWD_MODE = false;

    public static volatile long veilBatchFramesBatched = 0L;
    public static volatile long veilBatchFramesFallback = 0L;
    public static volatile long veilBatchReplaysSubmitted = 0L;

    /** Tier 1c master switch for the Veil-instanced CUBIC replay path
     *  ({@link com.afrobama.bbstools.client.bbs.veil.VeilCubicBatchRenderer}).
     *  Sister to {@link #ENABLE_VEIL_BATCH} which handles BOBJ-skinned
     *  replays. Default OFF: the cubic vertex-extraction accessor is
     *  still pending, so until then the path no-ops cleanly (replays
     *  fall back to vanilla BBS render). Flip on via /bbsdebug veil-cubic
     *  once the accessor mixin lands. */
    public static volatile boolean ENABLE_VEIL_CUBIC_BATCH = false;

    /* ------------------------------------------------------------------
     * Tier 1 — Replay fast-path. Hook ModelFormRenderer.render3D at HEAD
     * to classify + submit eligible crowd replays into FastPathBatch.
     * Tier 1 stays in measure-only mode: counters + JFR events fire but
     * render is NOT cancelled. Tier 1.5 will flip to render-replace.
     * Coded by Afrobama/Claude — Tier 1 fast-path scaffolding.
     * ------------------------------------------------------------------ */

    /** Master switch for the replay fast-path router + batch. Default ON
     *  as of 2026-05-20 r1 — Tier 2-d shipped clean at 200@80fps and
     *  500@40fps stable. Flip via /bbstools replay-fast-path off if any
     *  regression observed. */
    public static volatile boolean ENABLE_FAST_PATH = false;   /* MERGE 2026-07-24: default AUS. Im alten fork getunt+erprobt, frisch gemergt griff es ungefragt ins playback (emoticon bewegte sich nicht, erst beim pausieren sichtbar = gecachtes bild). Opt-in via /bbstools perf. */

    /** Hard cap on bones per replay accepted by the fast-path router.
     *  Replays with deeper rigs fall through to vanilla. Conservative
     *  default = 64 (humanoid + props headroom). Tier 2 SSBO sizing
     *  depends on this. */
    public static volatile int FAST_PATH_MAX_BONES = 64;

    /** Tier 1 counters — /bbsdebug replay-fast-path. */
    public static volatile long fastPathSubmissions = 0L;
    public static volatile long fastPathFlushes = 0L;
    public static volatile int  fastPathLastFrameSubmissions = 0;
    public static volatile int  fastPathLastFrameUniqueModels = 0;

    /** Tier 1.5a — Cancel-only mode. When true AND a replay is routed
     *  by CrowdRouter.ACCEPTED, render3D is cancelled BEFORE BBS renders
     *  the replay. Replays go invisible. Sole purpose: measure the max
     *  FPS ceiling with replay render cost = 0, so we can size Tier 1.5b
     *  (actual render replacement) properly. Default OFF — opt-in via
     *  /bbstools replay-fast-path render on. Requires ENABLE_FAST_PATH
     *  to also be on (routing depends on it). Coded by Afrobama/Claude.
     *  Default flipped to TRUE 2026-05-20 r1. */
    public static volatile boolean ENABLE_FAST_PATH_RENDER = true;

    /** Tier 1.5a counter — cancellations performed by the fast-path mixin
     *  when render flag is on AND classify returned ACCEPTED. */
    public static volatile long fastPathCancellations = 0L;

    /** Tier 1.5b counters — FastPathRenderer telemetry. */
    public static volatile long fastPathRendered      = 0L;
    public static volatile long fastPathRenderFailed  = 0L;
    public static volatile long fastPathRenderNsAccum = 0L;

    /** Tier 1.5b-2 — direct ModelInstance.render() call mode. When ON,
     *  FastPathRenderer skips BBS's renderModel(...) private wrapper and
     *  calls model.render() directly with state already hoisted to flush
     *  level. Saves: per-call GL state churn, per-call captureMatrices
     *  (no body parts in fast-path), per-call renderItems (no equipment).
     *  Default ON — opt-out via /bbstools replay-fast-path direct off
     *  if visual regression observed. Coded by Afrobama/Claude. */
    public static volatile boolean ENABLE_FAST_PATH_DIRECT = true;

    /* ------------------------------------------------------------------
     * Tier 2 — GPU-instanced render path. Foundation for the 1500-replay
     * target via Wicked-Engine compute-prepass pattern.
     *
     * Tier 2-a (current): builds SkinnedMeshIR + cache on each flush,
     * validates pipeline. Draw stays on Tier 1.5 fallback.
     * Tier 2-b: real GPU draw via the cached IR.
     * Tier 2-c: compute-shader bone skin prepass.
     *
     * Coded by Afrobama/Claude.
     * ------------------------------------------------------------------ */

    /** Master switch for the Tier 2 GPU-instanced path. Default ON
     *  2026-05-20 r1 — Tier 2-a IR cache + Tier 2-d parallel emit ride
     *  on this. Disable via /bbstools replay-fast-path tier2 off. */
    public static volatile boolean ENABLE_TIER2_GPU = true;

    /** Tier 2-a IR cache + scaffold counters. */
    public static volatile long tier2MeshesBuilt        = 0L;
    public static volatile long tier2MeshBuildFailures  = 0L;
    public static volatile long tier2MeshBuildNsAccum   = 0L;
    public static volatile long tier2CacheHits          = 0L;
    public static volatile long tier2CacheMisses        = 0L;
    public static volatile long tier2ScaffoldFlushes    = 0L;

    /** Coded by Afrobama/Claude — Tier 2-b real GPU-merged draw path.
     *
     *  When ON, each per-model-id bucket is rendered as ONE merged draw:
     *    - shared BufferBuilder (TRIANGLES + ENTITY format)
     *    - CPU bone-skin via CubicMatrixRenderer's per-group matrices
     *    - rest-pose verts/UVs/normals fetched from the cached SkinnedMeshIR
     *    - single Tessellator end + drawWithGlobalProgram per bucket
     *
     *  Collapses N {@code instance.render()} draws into 1 drawArrays per
     *  (model.id, texture) bucket. Iris-compat by construction (vanilla
     *  MC ENTITY format + GameRenderer::getRendertypeEntityTranslucentCullShader).
     *
     *  Falls back to FastPathRenderer.flushAll path per-bucket when IR
     *  is null (BOBJ models, models with meshes, etc).
     *
     *  Default ON 2026-05-20 r1. Disable via
     *  /bbstools replay-fast-path tier2-draw off if any regression. */
    public static volatile boolean ENABLE_TIER2_DRAW = true;

    /** Tier 2-b counters. */
    public static volatile long tier2DrawCalls          = 0L;
    public static volatile long tier2DrawSubmissions    = 0L;
    public static volatile long tier2DrawVerts          = 0L;
    public static volatile long tier2DrawFallbacks      = 0L;
    public static volatile long tier2DrawNsAccum        = 0L;
    public static volatile long tier2DrawFailures       = 0L;

    /** Coded by Afrobama/Claude — Tier 2-c parallel CPU skin path.
     *
     *  Three-phase pipeline replacing Tier 2-b's per-instance serial skin:
     *    A. SERIAL — animator chain + CubicMatrixRenderer bone-matrix
     *       extract (touches MC entity world, can't go parallel).
     *    B. PARALLEL — pure float math: per-instance skin into reusable
     *       flat arrays via ForkJoinPool.commonPool(). No MC state
     *       touched, so race-free.
     *    C. SERIAL — BufferBuilder vertex emit + single drawArrays per
     *       bucket. BufferBuilder is single-threaded by design.
     *
     *  Expected win at 99 replays: ~2× over Tier 2-b (the skin math is
     *  ~50% of the per-instance cost; parallelizing it on 8-16 threads
     *  collapses ~5ms to ~1ms). Phase A + C stay sequential so the
     *  absolute floor is animator + builder cost.
     *
     *  Iris-compat by construction: same BufferBuilder + MC ENTITY format
     *  as Tier 2-b — Iris's gbuffers_entities pipeline sees a vanilla
     *  entity draw. No extended vertex format, no compute shader.
     *
     *  Default ON 2026-05-20 r1. */
    public static volatile boolean ENABLE_TIER2_PARALLEL_SKIN = true;

    /** Tier 2-c parallel-skin counters. */
    public static volatile long tier2cFlushes           = 0L;
    public static volatile long tier2cBucketsProcessed  = 0L;
    public static volatile long tier2cBucketsFallback   = 0L;
    public static volatile long tier2cSubmissions       = 0L;
    public static volatile long tier2cParallelTasks     = 0L;
    public static volatile long tier2cPhaseANsAccum     = 0L;
    public static volatile long tier2cPhaseBNsAccum     = 0L;
    public static volatile long tier2cPhaseCNsAccum     = 0L;
    public static volatile long tier2cFailures          = 0L;

    /** Coded by Afrobama/Claude — Tier 2-d parallel BufferBuilder emit.
     *
     *  Tier 2-c parallelized PHASE B (math) but Phase C (BufferBuilder
     *  emit) stayed serial — at 200 replays Phase C dominates at 14ms /
     *  17ms total. Tier 2-d fixes that:
     *
     *    PHASE A (serial)  — animator chain + matrix extract.
     *    PHASE B+C (parallel, N workers) — each worker owns its own
     *      BufferAllocator + BufferBuilder, skins + emits ONE chunk of
     *      submissions into its private buffer. Iris's MixinBufferBuilder
     *      extends the format to 54B transparently (per-thread is fine —
     *      the mixin hooks the BufferBuilder instance, not a global).
     *    PHASE D (serial) — main thread issues N back-to-back
     *      BufferRenderer.drawWithGlobalProgram calls under one
     *      shader/texture/state. Iris's gbuffers_entities runs N times
     *      but state stays bound → driver pipelines efficiently.
     *
     *  Expected at 200 replays: Phase C 14ms → Phase B+C parallel ~1.2ms
     *  + Phase D ~0.7ms = ~2ms vs 14ms. Total flush 17ms → ~4ms = 4× win.
     *
     *  Iris-compat by construction: same BufferBuilder + same vanilla
     *  ENTITY format + same shader. Per-worker MixinBufferBuilder
     *  applies separately.
     *
     *  Default ON 2026-05-20 r1. */
    public static volatile boolean ENABLE_TIER2_PARALLEL_EMIT = true;

    /** Tier 2-d counters. */
    public static volatile long tier2dFlushes           = 0L;
    public static volatile long tier2dBucketsProcessed  = 0L;
    public static volatile long tier2dBucketsFallback   = 0L;
    public static volatile long tier2dSubmissions       = 0L;
    public static volatile long tier2dParallelTasks     = 0L;
    public static volatile long tier2dTotalDraws        = 0L;
    public static volatile long tier2dPhaseANsAccum     = 0L;
    public static volatile long tier2dPhaseBCNsAccum    = 0L;
    public static volatile long tier2dPhaseDNsAccum     = 0L;
    public static volatile long tier2dFailures          = 0L;

    /** Coded by Afrobama/Claude — Tier 2-e merged-buffer draw.
     *
     *  Tier 2-d shipped parallel emit but kept N back-to-back drawArrays
     *  (~250µs/draw × 15 = 3.65ms). Tier 2-e merges all worker
     *  BuiltBuffers into ONE master ByteBuffer + ONE drawArrays.
     *
     *    PHASE A   (serial)   — animator chain + matrix extract.
     *    PHASE B+C (parallel) — per-worker BufferBuilder emit (same as Tier 2-d).
     *    PHASE E   (serial)   — collect worker BuiltBuffers, memcpy
     *      bytes into master BufferAllocator, construct synthetic
     *      BuiltBuffer with summed vertexCount, single drawArrays.
     *
     *  Expected: Phase D 3.65ms → Phase E ~1.5ms (memcpy + 1 draw) at
     *  200 replays. Scales flat with N (single draw regardless).
     *
     *  Iris-compat: per-worker BufferBuilder still triggers Iris's
     *  MixinBufferBuilder (54B extension). Merged bytes inherit format.
     *  Format consistency across workers is guaranteed (same
     *  VertexFormat.Mode.TRIANGLES + ENTITY format).
     *
     *  Default OFF — opt-in via /bbstools replay-fast-path tier2-merge on.
     *  Requires all Tier 2-c/d gates on. */
    public static volatile boolean ENABLE_TIER2_MERGE = false;

    /** Tier 2-e counters. */
    public static volatile long tier2eFlushes           = 0L;
    public static volatile long tier2eBucketsProcessed  = 0L;
    public static volatile long tier2eBucketsFallback   = 0L;
    public static volatile long tier2eSubmissions       = 0L;
    public static volatile long tier2eMergedDraws       = 0L;
    public static volatile long tier2ePhaseANsAccum     = 0L;
    public static volatile long tier2ePhaseBCNsAccum    = 0L;
    public static volatile long tier2ePhaseENsAccum     = 0L;
    public static volatile long tier2eMergedBytes       = 0L;
    public static volatile long tier2eFailures          = 0L;

    /** Coded by Afrobama/Claude — Tier 2-e v2 persistent-buffer merged draw.
     *
     *  Tier 2-e v1 regressed (200@50fps vs Tier 2-d's 70fps) because the
     *  per-frame `new BufferAllocator(N)` + per-call VBO creation inside
     *  BufferRenderer.drawWithGlobalProgram burned more cycles in native
     *  malloc/free than the single-drawArrays saved.
     *
     *  v2 fix: ONE persistent BufferAllocator + ONE persistent
     *  VertexBuffer (Usage.DYNAMIC). MASTER_ALLOC.allocate() returns the
     *  same native slot each frame (cursor pops back to 0 via
     *  CloseableBuffer.close() on BuiltBuffer dispose); VertexBuffer.upload
     *  goes through glBufferData(DYNAMIC_DRAW) which the driver implements
     *  as orphaning + reuse against the same VBO id. Result: no per-frame
     *  native alloc/free, no per-frame VBO churn, while still collapsing
     *  N drawArrays into 1.
     *
     *  Expected at 1000 replays: Phase D 17ms → Phase F ~3-5ms = 12-14ms
     *  saved, lifting projected 1500-replay FPS from 19 to 30+.
     *
     *  Iris-compat: identical to v1 — per-worker BufferBuilder triggers
     *  Iris's MixinBufferBuilder extension; refParams.format() carries the
     *  extended layout into VertexBuffer.upload.
     *
     *  Default OFF — opt-in via /bbstools replay-fast-path tier2-e-v2 on.
     *  Sits at top of dispatch ladder when on (highest priority). */
    public static volatile boolean ENABLE_TIER2_MERGE_V2 = false;

    /** Tier 2-e v2 counters. */
    public static volatile long tier2eV2Flushes           = 0L;
    public static volatile long tier2eV2BucketsProcessed  = 0L;
    public static volatile long tier2eV2BucketsFallback   = 0L;
    public static volatile long tier2eV2Submissions       = 0L;
    public static volatile long tier2eV2MergedDraws       = 0L;
    public static volatile long tier2eV2PhaseANsAccum     = 0L;
    public static volatile long tier2eV2PhaseBCNsAccum    = 0L;
    public static volatile long tier2eV2PhaseFNsAccum     = 0L;
    public static volatile long tier2eV2MergedBytes       = 0L;
    public static volatile long tier2eV2Failures          = 0L;

    /** Coded by Afrobama/Claude — Tier 2-h GPU-instancing draw path.
     *
     *  Final architectural step: ONE glDrawElementsInstanced per
     *  (model, texture) bucket. GPU vertex shader does the bone-skin
     *  via per-instance SSBO lookup. Replaces Tier 2-d's parallel-emit
     *  + N drawArrays with custom shader + 1 instanced draw.
     *
     *  Per-frame upload: per-instance VBO (worldMat + light + color =
     *  72B/instance) + bone SSBO (boneCount × 64B per instance).
     *  At 1500 instances × 30 bones ≈ 110 KB inst VBO + 2.8 MB SSBO.
     *
     *  Expected at 1500 replays: phaseBC (14ms) + phaseD (26ms) of
     *  Tier 2-d collapse to phaseU (pack, ~3ms) + phaseG (upload+draw,
     *  ~2ms) = ~5ms total. Plus Phase A (~10ms serial animator) = 15ms
     *  total ≈ 60fps theoretical. Real-world 30-40fps expected.
     *
     *  No Iris-compat in this revision — custom shader bypasses Iris's
     *  gbuffers_entities. Use with shaderpacks OFF. Iris integration is
     *  a follow-up tier.
     *
     *  Default ON (2026-05-26) — gated behind ARMY_REPLAY_THRESHOLD (small scenes vanilla)
     *  AND auto-disabled during video export (VideoRecorder.isRecording() guard in the
     *  bypass + fast-path mixins) so the final MP4 renders the full original model. The
     *  simplified base-mesh crowd is only seen while editing (fast preview), which the user
     *  accepts. Auto-disables on first GL failure to avoid TDR loops. */
    public static volatile boolean ENABLE_TIER2_H = true;

    /** Tier 2-h counters. */
    public static volatile long tier2hFlushes           = 0L;
    public static volatile long tier2hBucketsProcessed  = 0L;
    public static volatile long tier2hBucketsFallback   = 0L;
    public static volatile long tier2hSubmissions       = 0L;
    public static volatile long tier2hInstancedDraws    = 0L;
    public static volatile long tier2hPhaseANsAccum     = 0L;
    public static volatile long tier2hPhaseUNsAccum     = 0L;
    public static volatile long tier2hPhaseGNsAccum     = 0L;
    public static volatile long tier2hUploadedBytes     = 0L;
    public static volatile long tier2hFailures          = 0L;

    /** Coded by Afrobama/Claude — Tier 2-h IDLE-REUSE (the cinematic-camera FPS
     *  lever). The per-instance world matrix + the per-instance bone palette
     *  (bone texture) are CAMERA-INDEPENDENT: Tier2h passes the camera as the
     *  ModelViewMat shader uniform, NOT baked into the instance/bone buffers.
     *  So orbiting/panning the camera over a PARKED crowd produces byte-identical
     *  instance + bone data every frame — yet the renderer re-runs PHASE A
     *  (animate), PHASE U (pack ~4MB of bones), and re-uploads the instance VBO
     *  + the ~4MB bone texture EVERY frame for nothing. When ON, drawBucket
     *  computes a cheap per-bucket signature (every replay's world translation/
     *  scale + light, plus sampled cached bone values) and, if it matches last
     *  frame AND the crowd is baked, SKIPS Phase A/U + both uploads and only
     *  re-sets the camera uniforms + re-issues the one instanced draw against
     *  the persisted buffers. Fully movable-safe: moving/editing a replay changes
     *  its world matrix or sampled bones → signature mismatch → full re-pack next
     *  frame. Playback advances each entity's age → sampled bones change → full
     *  path (crowd never freezes). A safety window forces a periodic full re-pack
     *  so an unsampled parked edit still shows within TIER2H_REUSE_SAFETY_MS. */
    public static volatile boolean ENABLE_TIER2H_IDLE_REUSE = true;
    public static volatile long    TIER2H_REUSE_SAFETY_MS   = 500L;
    public static volatile long    tier2hIdleReuses         = 0L;

    /** Coded by Afrobama/Claude — Tier 2-h VAT (animation-texture / GPU skinning):
     *  the ANIMATING-crowd FPS lever. The bake already stores every tick's bone
     *  palette. Instead of re-packing + re-uploading the current-tick palette for
     *  all instances EVERY frame (6.8MB), upload the WHOLE baked animation ONCE as
     *  an atlas texture (rows = unique-anim-group × tick), and per frame send only
     *  a per-instance row index (animBaseRow + currentTick) in the instance VBO.
     *  Bone upload 6.8MB → 0 (atlas static); per-frame upload shrinks to the
     *  ~228KB instance VBO → animating ≈ as cheap as static. Output is byte-
     *  identical to the per-frame path (same round(age+transition) tick palette,
     *  just relocated into the atlas), so it's a pure speed change. Atlas is keyed
     *  by the bake's MERGED unique-anim count (usually small even for "unique"
     *  replays — many reuse the same bone-anim at different positions). If the
     *  atlas would exceed GL_MAX_TEXTURE_SIZE, or any drawn instance isn't baked/
     *  bound yet, the bucket FALLS BACK to the per-frame path that frame (correct,
     *  no regression). Default ON; toggle off if a GPU driver/indexing issue. */
    public static volatile boolean ENABLE_TIER2H_VAT = true;
    public static volatile long    tier2hVatDraws     = 0L;  /* frames drawn via the atlas path */
    public static volatile long    tier2hVatRebuilds  = 0L;  /* atlas (re)uploads (rare; on bake progress) */
    public static volatile long    tier2hVatFallbacks = 0L;  /* frames that fell back to per-frame path */
    public static volatile int     tier2hVatAtlasRows = 0;   /* current atlas height (groups × ticks) */
    public static volatile int     tier2hVatGroups    = 0;   /* unique-anim groups in the atlas */

    /** Coded by Afrobama/Claude — Tier 2-f Phase A parallelization.
     *
     *  Tier 2-d's Phase A (animator chain + CubicMatrixRenderer extract)
     *  ran SERIAL on the main thread at ~1.5ms for 200 replays. Scales
     *  linear: ~11ms at 1500 replays, blowing past the 30fps frame
     *  budget.
     *
     *  Tier 2-f parallelizes Phase A across ForkJoinPool.commonPool().
     *  Safety analysis:
     *   - animator.applyActions(StubEntity, ...) — StubEntity has no MC
     *     world coupling, so no entity-world race.
     *   - instance.model.applyPose() — each ModelInstance owns its own
     *     model copy, no shared mutable state.
     *   - selfA.bbstools$ensureAnimator() — writes per-renderer animator
     *     field; each submission has its own renderer instance.
     *   - new CubicMatrixRenderer(cubic) + processRenderModel — fully
     *     per-extractor state, no shared mutable.
     *
     *  Risk surface: BBS Molang interpreter may use static state. Mitigation:
     *  parallel-animator (BBS's own outer loop parallelization, mixin
     *  BaseFilmControllerParallelMixin) already runs same code under
     *  ForkJoinPool with ENABLE_PARALLEL_ANIMATOR on — production-verified
     *  thread-safe in user's environment (0 fallbacks observed at 200
     *  replays for 521 ticks).
     *
     *  Expected: Phase A 1.5ms → 0.5ms at 200 replays, 11ms → 3ms at
     *  1500 replays. Closes the 1500@30fps gap.
     *
     *  Default OFF — opt-in via /bbstools replay-fast-path tier2-phase-a on.
     *  Requires same gates as Tier 2-d. */
    public static volatile boolean ENABLE_TIER2_PHASE_A_PARALLEL = false;

    /** Coded by Afrobama/Claude — Incremental createEntities.
     *
     *  BBS BaseFilmController.createEntities() clears the entire
     *  {entities, replayMap} state then re-creates a StubEntity +
     *  FormUtils.copy(form) + keyframes.apply(0, entity) for EVERY replay
     *  in the film. Called from UIReplayList.dupeReplay's
     *  updateFilmEditor — meaning duping 1 replay at 200-replay scale
     *  costs 200 form-copies + 200 keyframe-applications.
     *
     *  Incremental mode (this flag) snapshots existing entities by replay
     *  UUID at the start, then reuses them for unchanged replays —
     *  only NEW UUIDs trigger StubEntity + FormUtils.copy.
     *  Worst case: duping 1 replay = 1 new entity + 199 reuses.
     *
     *  Safety: reuse is only by UUID match. If form is mutated post-init,
     *  the entity's setForm result is unchanged — but BBS calls
     *  createEntities elsewhere on form change paths, so the next
     *  createEntities will refresh. For dupe (append-only), reuse is
     *  always correct.
     *
     *  Default ON 2026-05-20 r1 — critical for 500+ replay placement
     *  (without it, dupe at 500-scale freezes MC 25-50sec from O(N²)
     *  full-rebuild churn). Disable via
     *  /bbstools replay-fast-path incremental-entities off if any
     *  visual regression on dupe. */
    public static volatile boolean ENABLE_INCREMENTAL_ENTITIES = false;   /* MERGE 2026-07-24: default AUS. Im alten fork getunt+erprobt, frisch gemergt griff es ungefragt ins playback (emoticon bewegte sich nicht, erst beim pausieren sichtbar = gecachtes bild). Opt-in via /bbstools perf. */

    /** Per-tick TIME budget (ms) for creating NEW entities (FormUtils.copy +
     *  keyframes.apply) during a bulk duplicate. A big dupe (+3000) deep-copies a
     *  Gecko-animated form per replay; doing all at once froze the render thread for
     *  minutes. Capped here and drained over client ticks (BackgroundBaker →
     *  IIncrementalEntities) so the crowd pops in smoothly. ~12ms ≈ keeps fps up. */
    public static volatile long INCREMENTAL_ENTITY_BUDGET_MS = 12L;
    /** New entities deferred to a later tick on the most recent chunk (creation
     *  still in progress when > 0). */
    public static volatile long incrementalDeferred = 0L;
    /** Client ticks spent draining deferred entity creation. */
    public static volatile long incrementalDrainTicks = 0L;

    /** Tier 2-f counters. */
    public static volatile long tier2fPhaseATasks       = 0L;
    public static volatile long tier2fPhaseAParallelNs  = 0L;
    public static volatile long tier2fPhaseAFallbacks   = 0L;

    /** Incremental createEntities counters. */
    public static volatile long incrementalCreateCalls  = 0L;
    public static volatile long incrementalReuses       = 0L;
    public static volatile long incrementalNewEntities  = 0L;
    public static volatile long incrementalRemovals     = 0L;
    /** createEntities wall-time instrumentation (find the "10 min to make 3000
     *  replays" cost): per-call ms, worst seen, cumulative ms across all calls. */
    public static volatile long incrementalLastMs       = 0L;
    public static volatile long incrementalMaxMs        = 0L;
    public static volatile long incrementalTotalMs      = 0L;
    /** Per-entity cost split (ns, cumulative) — decides whether the dupe freeze is
     *  the deep form copy (FormUtils.copy) or the pose application (keyframes.apply).
     *  Worst single-entity ns each, to catch the "one entity = 568ms" outlier. */
    public static volatile long incrementalCopyNs       = 0L;
    public static volatile long incrementalApplyNs      = 0L;
    public static volatile long incrementalCopyMaxNs    = 0L;
    public static volatile long incrementalApplyMaxNs   = 0L;
    /** Coalesce diagnostics — why does the per-tick build guard not hold across the
     *  synchronous dupe loop? skipCount = createEntities calls suppressed by the guard;
     *  tickResetCount = onClientTick invocations (each resets builtThisTick). If both
     *  climb together during a dupe, ticks are interleaving the synchronous loop. */
    public static volatile long incrementalSkipCount    = 0L;
    public static volatile long incrementalTickResets   = 0L;
    /** Bulk-dupe buildVisualList coalesce: skipped redundant per-iteration rebuilds
     *  (the duplicate-×N loop calls buildVisualList O(N) per iter = O(N^2) freeze;
     *  UIReplayList.render() rebuilds it every frame anyway, so the loop's calls are
     *  redundant). */
    public static volatile long bulkDupeBuildSkips      = 0L;

    /** Coded by Afrobama/Claude — Tier 3 lite — per-thread pools.
     *
     *  Per-frame allocation hotspots in Tier 2-d:
     *    - 15 × new BufferAllocator(estimatedBytes)  (native malloc)
     *    - 15 × new BufferBuilder                    (small Java alloc)
     *    - N × new CubicMatrixRenderer(model)        (ArrayList<Matrix4f> alloc)
     *    - N × new MatrixStack()                     (Java alloc)
     *
     *  Tier 3 lite pools all four via ThreadLocal. Reuse frame-over-frame.
     *  BufferAllocator.reset() rewinds the offset cursor without freeing
     *  native memory — MC's Tessellator singleton uses the same pattern.
     *  CubicMatrixRenderer.matrices ArrayList is overwritten by
     *  CubicRenderer.processRenderModel each call, safe to reuse for the
     *  same modelId.
     *
     *  Expected: ~1-2ms saved per flush at 200 replays (kills GC churn +
     *  native alloc syscalls). Larger gain at 500+ replays where N alloc
     *  scales linearly.
     *
     *  Safety: pools are per-thread. Main thread = serial Phase A + D.
     *  Workers = parallel Phase B+C (each has own ThreadLocal slot).
     *  No cross-thread sharing.
     *
     *  Default ON 2026-05-20 r1. */
    public static volatile boolean ENABLE_TIER3_POOLS = true;

    /** Tier 3 pool counters. */
    public static volatile long tier3AllocReuses        = 0L;
    public static volatile long tier3AllocFreshAllocs   = 0L;
    public static volatile long tier3ExtractorReuses    = 0L;
    public static volatile long tier3ExtractorFresh     = 0L;

    /** r39 2026-05-18 ISOLATION switch — Coded by Afrobama/Claude.
     *  When true, VeilCubicBatch.draw() returns BEFORE any GL state change
     *  or glDrawElementsInstanced. Build + shader compile still run when
     *  veil-cubic is toggled ON. The next user test determines whether
     *  the recurring native AV in vanilla particle render is caused by
     *  (a) our draw call itself / GL state we mutate, or (b) the
     *  build/compile/upload phase. Default ON for the isolation test. */
    public static volatile boolean VEILCUBIC_SKIP_DRAW = true;

    /** Tier 1c — per-frame counters for the Veil-instanced CUBIC replay path.
     *  Mirrors {@link #veilBatchFramesBatched}/{@link #veilBatchReplaysSubmitted}/
     *  {@link #veilBatchFramesFallback} but specific to the cubic dispatcher.
     *  Exposed in {@code /bbsdebug veil-cubic} status. */
    public static volatile long veilCubicFlushes = 0L;
    public static volatile long veilCubicSubmitted = 0L;
    public static volatile long veilCubicFallback = 0L;

    /** Tier 3e: gate for the per-replay frustum cull inside
     *  BaseFilmControllerMixin's INSTANCE renderEntity injection.
     *  Replays outside the camera frustum can skip their full render
     *  (form-stack walk, animation interpolation, etc.) and just save
     *  the work. Skipped for {@code replay.relative} where positioning
     *  is camera-anchored.
     *
     *  DEFAULT FLIPPED 2026-05-18: was true, broke editor + cinematic
     *  replay visibility (replays vanished when camera turned slightly
     *  away — user wants replays always visible during shots per
     *  feedback_no_replay_distance_cull). Toggle on via
     *  /bbsdebug feature replay-frustum-cull on if user opts in. */
    public static volatile boolean ENABLE_REPLAY_FRUSTUM_CULL = false;
    /** Half-extent (in blocks) of the AABB built around each replay's
     *  entity position for the frustum test. Generous default — a Grox
     *  villager is ~2 blocks tall, but tall actors (giants / vehicles)
     *  need more. */
    public static volatile double REPLAY_FRUSTUM_BOX_RADIUS = 2.0D;
    public static volatile long replayFrustumCullCount = 0L;

    /** Coded by Afrobama/Claude — Tier 4c shader hot-reload keybind.
     *  Master switch for the F10 shader hot-reload binding. When true,
     *  pressing the bound key fires {@link com.afrobama.bbstools.client.shader.ShaderHotReload#reload()}
     *  which (a) reflectively calls {@code net.irisshaders.iris.Iris.reload()}
     *  on the active shaderpack and (b) tears down BBSTools' own cached
     *  crowd-renderer GL handles so the next frame re-initialises against
     *  the freshly-reloaded shaders. Toggle false if the reload path ever
     *  causes a crash — the keybind becomes a no-op until re-enabled. */
    public static volatile boolean ENABLE_SHADER_HOT_RELOAD = true;

    /* ------------------------------------------------------------------
     * Tier 3g — static-batch paused replays. When the film is paused
     * (very common during cinematic shot composition), every replay
     * still walks its bone hierarchy and re-uploads bone matrices each
     * frame. For 200 replays × 30 bones at 60 FPS that's 360 k matrix
     * uploads/sec for completely static content. Tier 3g detects
     * pause + caches the per-replay bone matrix array + skips rebuild
     * until pause is released. Default OFF for safety (opt-in for new
     * perf paths, per memorized rule).
     *
     * Two related caches coexist:
     *   - {@link com.afrobama.bbstools.perf.PausedReplayMatrixCache}
     *     (version-counter strategy, used by the Veil batch path).
     *   - {@link com.afrobama.bbstools.perf.PausedReplayBoneCache}
     *     (UUID + tick strategy, used by the VAO upload short-circuit).
     * Both share the same {@code ENABLE_PAUSED_BONE_CACHE} master gate.
     * ------------------------------------------------------------------ */

    /** Coded by Afrobama/Claude — master switch for Tier 3g paused-replay
     *  bone-matrix caching. When true + film paused + cached matrices
     *  match current tick, the BOBJModelVAO's per-frame
     *  rebuild-and-upload work is skipped entirely. Default OFF. */
    public static volatile boolean ENABLE_PAUSED_BONE_CACHE = false;

    /** Cache-hit counter for {@link com.afrobama.bbstools.perf.PausedReplayBoneCache}. */
    public static volatile long pausedBoneCacheHits = 0L;

    /** Cache-miss counter for {@link com.afrobama.bbstools.perf.PausedReplayBoneCache}. */
    public static volatile long pausedBoneCacheMisses = 0L;

    /** Sister gate used by the version-counter
     *  {@link com.afrobama.bbstools.perf.PausedReplayMatrixCache}.
     *  Treated as an alias of {@link #ENABLE_PAUSED_BONE_CACHE} —
     *  toggling either via /bbsdebug paused-bone-cache on flips both. */
    public static volatile boolean ENABLE_STATIC_PAUSED_BATCH = false;

    /** Skip counter for {@link com.afrobama.bbstools.perf.PausedReplayMatrixCache}. */
    public static volatile long staticPausedSkips = 0L;

    /** Recompute counter for {@link com.afrobama.bbstools.perf.PausedReplayMatrixCache}. */
    public static volatile long staticPausedRecomputes = 0L;

    /* ------------------------------------------------------------------
     * Tier 1e — Parallel Animator. Fans the per-replay
     * BaseFilmController.startRenderFrame loop out across
     * ForkJoinPool.commonPool() so Molang variable setup + property
     * channel interpolation can use multiple CPU cores. Target: lift
     * the 1500-replay ceiling at 60 FPS up to 3500-4500 by spreading
     * the CPU-bound animation eval work.
     *
     * Coded by Afrobama/Claude — Tier 1e Parallel Animator.
     * ------------------------------------------------------------------ */

    /** Master switch for the Tier 1e parallel animator path
     *  ({@code BaseFilmControllerParallelMixin}). When true, the
     *  per-replay {@code startRenderFrame} body is dispatched through
     *  {@code Collection.parallelStream()}; the actor-bound branch
     *  (which touches {@code MinecraftClient.world.getEntityById} and
     *  mutates player pose) is collected into a serial post-pass to
     *  preserve render-thread affinity for the MC entity world.
     *
     *  Default ON 2026-05-20 r1 — confirmed 0 fallbacks across 2331
     *  ticks at 500 replays. Disable via
     *  /bbsdebug feature parallel-animator off. */
    public static volatile boolean ENABLE_PARALLEL_ANIMATOR = true;

    /** Count of ticks where the parallel path successfully ran. */
    public static volatile long parallelAnimatorTicks = 0L;

    /** Count of ticks where the parallel path threw and we fell back
     *  to the vanilla sequential loop. */
    public static volatile long parallelAnimatorFallbacks = 0L;

    /* ------------------------------------------------------------------
     * #4 BBS-internal frame-cost profiling. After Tier 2-h/2-i the render
     * path is ~5.4ms @ 3000 but the frame ceiling sits ~40fps (25ms). The
     * remaining ~20ms is BBS Java per-frame work, NOT our GL. These three
     * accumulators split the per-frame BBS cost so we attack the real
     * bottleneck instead of guessing:
     *   - startRenderFrame : property/Molang eval (parallelized Tier 1e).
     *   - render-loop      : BaseFilmController.render's SERIAL per-replay
     *     loop (renderEntity static: matrix alloc + 2× world.getLightLevel
     *     chunk lookup + new FormRenderingContext + FormUtilsClient.render
     *     descent down to the cancelled render3D). HEAD→flush-start window.
     *   - flush            : our FastPathBatch.flush (GPU instanced draw).
     * Avg ns/frame reported in /bbstools replay-fast-path status.
     * Coded by Afrobama/Claude — #4 frame-cost profiling.
     * ------------------------------------------------------------------ */

    /** Transient: System.nanoTime() captured at render() HEAD. Read once
     *  at flush start to derive the render-loop window. */
    public static volatile long bbsRenderHeadNs = 0L;

    public static volatile long bbsRenderLoopNsAccum = 0L;
    public static volatile long bbsRenderLoopFrames  = 0L;
    public static volatile long bbsFlushNsAccum      = 0L;
    public static volatile long bbsStartRenderFrameNsAccum = 0L;
    public static volatile long bbsStartRenderFrameFrames  = 0L;

    /* Sub-split of render-loop (per-frame, summed over all replays):
     *  - descent : FormUtilsClient.render outer-call wall time (incl. our
     *    classify+submit at render3D HEAD + cancelled render3D + body parts).
     *  - shadow  : ModelBlockEntityRenderer.renderShadow buffer-fill.
     *  prework (matrix alloc + 2× light lookup + FormRenderingContext alloc)
     *  = render-loop - descent - shadow. */
    public static volatile long bbsDescentNsAccum = 0L;
    public static volatile long bbsDescentCalls   = 0L;
    public static volatile long bbsShadowNsAccum  = 0L;
    public static volatile long bbsShadowCalls    = 0L;

    public static void resetFrameTiming()
    {
        bbsRenderLoopNsAccum = 0L;
        bbsRenderLoopFrames  = 0L;
        bbsFlushNsAccum      = 0L;
        bbsStartRenderFrameNsAccum = 0L;
        bbsStartRenderFrameFrames  = 0L;
        bbsDescentNsAccum = 0L;
        bbsDescentCalls   = 0L;
        bbsShadowNsAccum  = 0L;
        bbsShadowCalls    = 0L;
    }

    public static String frameTimingSnapshot()
    {
        long rf = bbsRenderLoopFrames;
        long sf = bbsStartRenderFrameFrames;
        double loopMs  = rf == 0L ? 0.0 : (bbsRenderLoopNsAccum / (double) rf) / 1_000_000.0;
        double flushMs = rf == 0L ? 0.0 : (bbsFlushNsAccum      / (double) rf) / 1_000_000.0;
        double startMs = sf == 0L ? 0.0 : (bbsStartRenderFrameNsAccum / (double) sf) / 1_000_000.0;
        double descMs  = rf == 0L ? 0.0 : (bbsDescentNsAccum   / (double) rf) / 1_000_000.0;
        double shadMs  = rf == 0L ? 0.0 : (bbsShadowNsAccum    / (double) rf) / 1_000_000.0;
        double preMs   = loopMs - descMs - shadMs;
        long descPerFrame = rf == 0L ? 0L : bbsDescentCalls / rf;
        long shadPerFrame = rf == 0L ? 0L : bbsShadowCalls  / rf;
        return String.format(
            "startRenderFrame=%.2fms  render-loop=%.2fms [descent=%.2fms(%d/f) shadow=%.2fms(%d/f) prework=%.2fms]  flush=%.2fms  (BBS-internal=%.2fms/frame, frames=%d)",
            startMs, loopMs, descMs, descPerFrame, shadMs, shadPerFrame, preMs, flushMs, startMs + loopMs, rf);
    }

    /* ------------------------------------------------------------------
     * Tier 6c — Bindless textures registry. Eliminates per-draw
     * glBindTexture state changes on GPUs that support
     * GL_ARB_bindless_texture (NVIDIA Kepler+, AMD GCN+; NOT Intel).
     * Each registered texture's uint64 handle is stored in an SSBO so
     * the shader can sample by index. Default OFF.
     *
     * Coded by Afrobama/Claude — Tier 6c bindless texture registry
     * (sequence-after-3b).
     * ------------------------------------------------------------------ */

    /** Master switch for the Tier 6c bindless texture path. When true,
     *  {@link com.afrobama.bbstools.perf.BindlessTextureRegistry} probes
     *  for the GL_ARB_bindless_texture extension and (if present)
     *  allocates a resident-handle SSBO. Default OFF — opt-in via
     *  /bbsdebug bindless on. Has zero effect on hardware without the
     *  extension (Intel iGPUs); the registry self-disables and logs once. */
    public static volatile boolean ENABLE_BINDLESS_TEXTURES = false;

    /** Count of texture handles currently flagged resident by the
     *  bindless registry. Snapshot updated on every registerTexture()
     *  and reset to 0 on shutdown(). */
    public static volatile long bindlessHandlesResident = 0L;

    /* Coded by Afrobama/Claude — Tier 2e DH-style upload thread counters.
     *  Master enable lives on {@link BBSUploadThread#ENABLED} (defaults
     *  OFF — opt-in via /bbsdebug upload-thread on). These counters track
     *  cumulative jobs + bytes pushed through the upload context for the
     *  feature dashboard. Companion gate UPLOAD_THREAD_ENABLED mirrors
     *  the BBSUploadThread.ENABLED flag so callers can branch on a config
     *  field without touching the perf class. */
    public static volatile boolean UPLOAD_THREAD_ENABLED = false;
    public static volatile long uploadThreadJobsCompleted = 0L;
    public static volatile long uploadThreadBytesUploaded = 0L;

    /* ------------------------------------------------------------------
     * Tier 2a — Sodium-style per-thread BufferBuilder pool. Replaces the
     * Phase 1b parallelStream over ForkJoinPool.commonPool with a
     * dedicated pool of N=clamp(max(cores/3, cores-6),1,10) workers, each
     * owning its own off-heap BBSEntityBuildContext. Goal: 5-15% on
     * contended frames where MC chunk meshing + Sodium + BBS keyframe
     * decode are all competing for commonPool.
     *
     * Default OFF — opt-in via /bbsdebug worker-pool on.
     * Coded by Afrobama/Claude — Tier 2a Sodium-style worker pool.
     * ------------------------------------------------------------------ */


    public static volatile boolean CROWD_USE_DEDICATED_POOL = false;

    /** Counters for the Tier 2a worker pool. Read by
     *  /bbsdebug worker-pool. */
    public static volatile long entityWorkerJobsSubmitted = 0L;
    public static volatile long entityWorkerJobsCompleted = 0L;
    public static volatile long entityWorkerJobsFailed    = 0L;

    /* ------------------------------------------------------------------
     * Tier 2c — Indirect multi-draw consolidation. Sodium-style
     * glMultiDrawElementsBaseVertex over a shared VBO+EBO atlas, so all
     * unique crowd sub-meshes are issued in ONE GL call per frame.
     * Coded by Afrobama/Claude — Tier 2c indirect multi-draw.
     * ------------------------------------------------------------------ */


    public static volatile boolean ENABLE_INDIRECT_MULTIDRAW = false;

    /** Tier 2c — counter: number of multi-draw flush calls issued. */
    public static volatile long multiDrawCalls = 0L;

    /** Tier 2c — counter: total sub-mesh draw commands consolidated
     *  across all flushes. Divide by multiDrawCalls to get average
     *  sub-meshes/flush. */
    public static volatile long multiDrawCommandsConsolidated = 0L;

    /* ------------------------------------------------------------------
     * Tier 3b — Texture-atlas consolidation. Consolidates up to 256
     * unique replay textures into ONE 4K×4K GL_RGBA8 atlas so the Veil
     * batch path can stay bound to a single texture across the whole
     * crowd. Per-instance vec4 atlasUV selects which tile. Live state
     * is owned by {@link com.afrobama.bbstools.perf.CrowdTextureAtlas}
     * (counters mirrored here for /bbsdebug grand-dashboard).
     *
     * Default OFF — Java-side prep is shipped but the shader-side UV
     * remap is intentionally deferred to a follow-up, so flipping this
     * on right now is a no-op for visuals (it just primes the atlas).
     *
     * Coded by Afrobama/Claude — Tier 3b texture atlas consolidation.
     * ------------------------------------------------------------------ */
    public static volatile boolean ENABLE_TEXTURE_ATLAS = false;
    public static volatile long atlasTilesUsed = 0L;
    public static volatile long atlasLookupHits = 0L;
    public static volatile long atlasOverflows = 0L;

    /* ------------------------------------------------------------------
     * Army System P1 — record-on-render animation bake cache.
     * See ARMY_BAKE_PLAN.md. The cache records live per-tick bone matrices
     * (AnimationBakeCache) so subsequent renders at the same tick skip the
     * animator entirely (cached == live by construction). Eliminates Phase A
     * cost + the shared-model parallel race for cached ticks.
     * Coded by Afrobama/Claude.
     * ------------------------------------------------------------------ */

    /** Master switch for the Army bake cache. Default OFF (opt-in; BBS renders
     *  normally when off). Toggle /bbstools replay-fast-path army-bake on. */
    public static volatile boolean ENABLE_ARMY_BAKE = false;

    /** Byte cap for the in-memory bone-matrix cache. Over this, oldest frames
     *  evict (LRU). Heap-aware default = maxHeap/3 (clamped to [512MB, 6GB]).
     *  512MB thrashed at 2214 replays (per-entity keyed, no dedup yet → working
     *  set ~850MB+). NOTE: per-entity keying can't reach 100k — dedup by
     *  animation-content is required for the scale goal (Block 2/3). */
    public static volatile long ARMY_BAKE_MAX_BYTES = computeDefaultBakeCap();

    private static long computeDefaultBakeCap()
    {
        long third = Runtime.getRuntime().maxMemory() / 3L;
        long min = 512L * 1024L * 1024L;
        long max = 6L * 1024L * 1024L * 1024L;
        return Math.max(min, Math.min(third, max));
    }

    /** User-overridable cache directory for disk persistence (P2). Null =
     *  default to <instance>/bbstools/anim-cache/. */
    public static volatile String ARMY_BAKE_CACHE_DIR = null;

    /** Render-loop win P4a — skip per-replay blob shadow buffer-fill (~2.9ms
     *  @3000). Opt-in; default off keeps BBS shadows normal. */
    public static volatile boolean SKIP_BLOB_SHADOW = false;
    public static volatile long bbsShadowSkipped = 0L;

    /** P4 render-loop bypass — COMPARE harness. When on, the fast-path capture
     *  also computes a candidate worldMatrix from entity+camera and logs its
     *  max element-delta vs BBS's real captured matrix. Lets us verify the
     *  bypass matrix formula NUMERICALLY (via counters/log) before activating
     *  the actual render() bypass — no behavior change while comparing. */
    public static volatile boolean RENDER_LOOP_BYPASS_COMPARE = false;
    public static volatile long   bypassCompareCount      = 0L;
    public static volatile long   bypassCompareMismatches = 0L;
    public static volatile double bypassCompareMaxDelta   = 0.0;

    /** P4 render-loop bypass — ACTIVE. When on + tier2-h + ALL replays simple
     *  (not actor/grouped/anchored/relative, ModelForm+cubic), the BBS render()
     *  per-replay loop is cancelled and worldMatrices are computed in parallel
     *  (verified bit-exact, maxDelta=0) + submitted straight to Tier 2-h.
     *  All-or-nothing per frame. THE perf lever: replaces the per-replay entity descent
     *  (measured 88% of frame time at 200 replays) → 16-27x fps. Default ON (2026-05-26).
     *  Gated behind ARMY_REPLAY_THRESHOLD (small scenes stay vanilla) AND auto-disabled
     *  during video export (VideoRecorder.isRecording() guard) so the final MP4 renders the
     *  full original crowd. While editing, the simplified base-mesh crowd is acceptable
     *  (fast preview); actor-control works (throttle skips getControlled, FIX). */
    public static volatile boolean ENABLE_RENDER_LOOP_BYPASS = false;   /* MERGE 2026-07-24: default AUS. Im alten fork getunt+erprobt, frisch gemergt griff es ungefragt ins playback (emoticon bewegte sich nicht, erst beim pausieren sichtbar = gecachtes bild). Opt-in via /bbstools perf. */
    public static volatile long bypassFrames    = 0L;
    public static volatile long bypassReplays   = 0L;
    public static volatile long bypassFallbacks = 0L;

    /** Editor-side scaling — animator config cache. ModelFormRenderer
     *  .ensureAnimator rebuilds a throwaway ActionsConfig (HashMap copies of
     *  gecko animations) EVERY call just to detect a (rare) config change —
     *  3000×/tick froze the editor (BaseFilmController.updateEntities). When
     *  on, the re-resolve is throttled: skipped while the animator exists and
     *  the model is unchanged, re-checked at most every ANIMATOR_CACHE_THROTTLE_MS.
     *  Bounded staleness (a config edit applies within the throttle window).
     *
     *  Default ON (Afrobama/Claude 2026-05-25): autopilot A/B measured +52% playback fps
     *  on the 20k crowd (6.7 -> 10.2 fps) — ensureAnimator ran 4.9M times/window doing
     *  resolveActionsConfig's gecko deep-copy + a deep ActionsConfig.equals, all redundant
     *  while the config is unchanged. Throttle 500ms: at 60fps editing it skips ~29/30 frames
     *  with imperceptible edit lag; at low-fps crowd playback it still skips most frames.
     *  Single replays / model swaps / first setup are unaffected (the mixin falls through). */
    public static volatile boolean ENABLE_ANIMATOR_CONFIG_CACHE = true;
    public static volatile long    ANIMATOR_CACHE_THROTTLE_MS = 500L;
    public static volatile long    animatorResolveSkips = 0L;

    /** Playback simulation decouple (Afrobama/Claude 2026-05-25). BBS runs the heavy per-replay
     *  film simulation (BaseFilmController.updateEntities -> keyframes.apply, ~40 interpolate
     *  calls/replay) in ClientTickEvents.END_CLIENT_TICK — once per CLIENT TICK. When playback is
     *  slow the client runs many catch-up ticks per rendered frame (measured ~11 ticks/frame at
     *  1fps on 20k replays), so updateEntities runs ~11x/frame but ONLY the last tick's state is
     *  ever rendered — ~10/11 of the work is invisible, and it death-spirals (slow render -> more
     *  catch-up -> slower). When ON, updateEntities is coalesced to run at most ONCE per RENDERED
     *  frame (keyed on bbstoolsRenderFrameId): the first per-tick call of each frame runs, the rest
     *  are skipped. Self-gating — editing/scrubbing already runs ~1 tick/frame so nothing is
     *  skipped; only the catch-up burst during playback is collapsed. Keyframe motion/pose stays
     *  exact (interpolate is a pure function of tick); the only effect is action-clip/step-sound
     *  side effects fire once/frame instead of every tick (coarser timing at heavy-crowd playback).
     *  Flag OFF for frame-precise action-clip timing. */
    public static volatile boolean ENABLE_PLAYBACK_SIM_DECOUPLE = false;   /* MERGE 2026-07-24: default AUS. Im alten fork getunt+erprobt, frisch gemergt griff es ungefragt ins playback (emoticon bewegte sich nicht, erst beim pausieren sichtbar = gecachtes bild). Opt-in via /bbstools perf. */
    public static volatile long    bbstoolsRenderFrameId = 0L;
    public static volatile long    playbackTickSkips = 0L;

    /** World-transform bake (Afrobama/Claude 2026-05-25). The Army bake caches BONE poses (Tier2h
     *  draws them free), but each replay's WORLD transform (position + rotation from
     *  ReplayKeyframes.apply, ~40 interpolate calls) and the animator-config resolve in
     *  updateEntityAndForm are still computed LIVE every frame — measured interpolate 22% + gecko
     *  14% at 20k, the wall after the playback decouple. Both are pure functions of (replay, tick),
     *  so WorldTransformCache stores 7 floats/tick (x,y,z,yaw,pitch,headYaw,bodyYaw) per replay;
     *  on a hit BaseFilmControllerWorldTransformMixin restores the transform and cancels
     *  updateEntityAndForm + applyReplay (skipping both the interpolate and the gecko resolve).
     *  CONTROLLABLE: the SELECTED replay (currentEntityRef) is never cached → edits stay live;
     *  moving a replay / editing keyframes invalidates its cache → recompute. NO dedup (positions
     *  unique) so it's byte-capped LRU (half of ARMY_BAKE_MAX_BYTES). Lazy fill (the film loops, so
     *  the 2nd loop is cached) + can be pre-filled by the background baker. Default OFF until A/B
     *  verified via /film/play; needs ENABLE_ARMY_BAKE for the bone pose to also be cached. */
    public static volatile boolean ENABLE_WORLD_TRANSFORM_BAKE = true;
    public static volatile long    wtBakeHits = 0L;
    public static volatile long    wtBakeMisses = 0L;
    public static volatile long    wtBakeBytes = 0L;
    public static volatile long    wtBakeEvictions = 0L;

    /** Army replay-count threshold (Afrobama/Claude 2026-05-25). The whole instanced crowd stack
     *  (Tier2h GPU-instanced draw + render bypass + army bake + bg bake + transform-only/skip-baked
     *  sim-skips) is for LARGE crowds. For ordinary editing (a handful of replays) vanilla BBS already
     *  renders fast AND correctly, while the instanced path had low-count corruption (camera-relative
     *  ghosts, multi-model collapse, black/no-depth). So the bypass + bg bake engage ONLY when the
     *  live replay count is at least this; below it the scene renders 100% vanilla BBS. Set to 0 to
     *  force the instanced path on at any count (debugging). */
    public static volatile int ARMY_REPLAY_THRESHOLD = 64;

    /** Live replay count, published EVERY frame at BaseFilmController.render HEAD (unconditional, before
     *  any flag guard) so the count is available to render hooks that do NOT hold the controller ref —
     *  specifically ModelFormRendererFastPathMixin, which runs per-replay inside ModelFormRenderer and
     *  cannot see the crowd size otherwise. The classify/cancel there gates on this: below
     *  {@link #ARMY_REPLAY_THRESHOLD} the fast path does NOT submit or cancel render3D = 100% vanilla.
     *  One-frame lag is fine for a count threshold (crowd size changes rarely). Default 0 → dormant
     *  until the first render() publishes a real count, so a fresh session is vanilla-safe. */
    public static volatile int liveReplayCount = 0;

    /** Skip baked form-properties (Afrobama/Claude 2026-05-25). startRenderFrame walks EVERY replay
     *  every RENDERED frame and runs FormProperties.resetProperties + applyProperties (Molang +
     *  keyframe interpolation of the form pose) — measured ~10% at 20k. When render-loop bypass +
     *  Tier2h + army bake draw the crowd FROM THE BONE BAKE, the live pose is never read for a
     *  fully-baked replay = dead work, so we skip it. The "is the crowd fully baked?" test was
     *  originally a per-replay AnimationBakeCache.firstUncachedTick call (10.9% — a self-inflicted
     *  wall, see the gen-counter note below); it now reads the cheap {@link #crowdBakeComplete}
     *  dirty flag instead. NOT skipped: the SELECTED replay (renders live for editing). Guarded by
     *  bbstoolsLastBypassDrawFrame so a mixed/actor scene (bypass bails → BBS renders the crowd
     *  normally and reads the live pose) never skips. Default ON now the gate is O(1). */
    public static volatile boolean ENABLE_SKIP_BAKED_PROPERTIES = true;

    /** Crowd-fully-baked dirty flag (Afrobama/Claude 2026-05-25). Set true by the render-loop bypass
     *  once a full submission pass hides ZERO replays (every eligible replay is fully baked). Lets
     *  TWO hot per-replay-per-pass loops short-circuit to O(1): (1) the bypass's own hide-until-ready
     *  check (firstUncachedTick + expectedTicks, ~6.8% at 20k) and (2) the skip-baked-properties gate
     *  in parallelStartRenderFrame (~9.9% applyProperties). Validity is fenced by gen (== the bake's
     *  tier2hVatRebuilds at set time) + size (== entities.size() at set time); any cache removal
     *  (AnimationBakeCache.unbind/clear, i.e. an edit or rebake) resets it to false so the per-replay
     *  re-check resumes until the crowd is complete again. */
    public static volatile boolean crowdBakeComplete     = false;
    public static volatile long    crowdBakeCompleteGen  = -1L;
    public static volatile int     crowdBakeCompleteSize = -1;

    /** True while a film is actively playing (set from the render-loop bypass via !this.paused). Used
     *  to suspend the editor's periodic film-save (serialize 20k replays + gzip on the render thread,
     *  ~7% + multi-100ms stalls) until the user pauses. */
    public static volatile boolean filmIsPlaying = false;

    /** Suspend the editor's periodic auto-save WHILE a film is playing (Afrobama/Claude 2026-05-25).
     *  UIDataDashboardPanel.checkPeriodicSave fires every editorPeriodicSave seconds and synchronously
     *  serializes the WHOLE film (20k replays → MapType/ListType → gzip) on the render thread — a
     *  multi-100ms stall mid-playback (the "final"-phase spikes) + ~7% of playback CPU. When ON, the
     *  save is skipped while filmIsPlaying and resumes when paused (close() + the next idle tick still
     *  save, so nothing is lost). Default ON. */
    public static volatile boolean ENABLE_SUSPEND_SAVE_WHILE_PLAYING = true;
    public static volatile long    suspendedSaves = 0L;
    public static volatile long    skippedBakedProps = 0L;

    /** Set to bbstoolsRenderFrameId by the render-loop bypass each frame it actually draws the crowd
     *  (cancels render()). parallelStartRenderFrame only skips baked properties when this is within 1
     *  frame of the current id → guarantees the crowd is being drawn from the bake (not the live
     *  pose) before we stop computing the live pose. */
    public static volatile long    bbstoolsLastBypassDrawFrame = -1L;

    /** Per-replay cache-progress loading bar (green=cached, red=live). Fed by
     *  the bypass loop. Default off. */
    public static volatile boolean ENABLE_CACHE_LOADING_BAR = false;

    /** Expected ticks per replay for the loading-bar fill ratio (progress =
     *  cachedTicks / this). FALLBACK only — the bypass now publishes the real
     *  per-replay expected tick count (film duration, or looping length) via
     *  AnimationBakeCache.publishProgress; the bar reads that. This constant is
     *  used only when the real duration is unavailable (e.g. 0). */
    public static volatile int ARMY_BAKE_EXPECTED_TICKS = 200;

    /** DaVinci-style background bake — fills the cache WHILE PAUSED/idle so the
     *  loading bar climbs without the user playing the film. Runs inside the
     *  render-loop bypass (render thread, serial → no shared-model race): each
     *  idle frame it poses up to BG_BAKE_TICKS_PER_FRAME uncached ticks per the
     *  exact BBS pose path (setAge + keyframes.apply), records the bone matrices
     *  via the proven live extractor, then restores every touched entity to the
     *  live film tick. Only active when the bypass is (pure crowd of simple
     *  replays). Default off. */
    public static volatile boolean ENABLE_BG_BAKE = false;

    /** Bake budget per idle frame (total ticks across all replays). 200 ≈ a
     *  3000-replay × 120-tick scene fully cached in ~30s of sitting idle, with
     *  no perceptible frame hitch. Tunable. */
    public static volatile int BG_BAKE_TICKS_PER_FRAME = 200;

    public static volatile long bgBakeTicks    = 0L;
    public static volatile long bgBakeRestores = 0L;
    /** Last per-replay expected tick count the baker computed (debug). */
    public static volatile int  bgBakeExpectedSample = 0;

    /** Background baker (BackgroundBaker, runs on client tick → fills AFK).
     *  Time-boxed per tick. Moderate while PLAYING (avoid heavy stutter)... */
    public static volatile long BG_BAKE_MS_PER_TICK = 8L;

    /** ...and BIG while paused/idle — a long tick hitch is fine when nothing's
     *  playing, and it finishes the bake in a second or two. */
    public static volatile long BG_BAKE_MS_PER_TICK_IDLE = 60L;

    /** Per-unique-animation dedup: identical replays share one bake (only the
     *  representative is extracted). Off → every replay baked separately. */
    public static volatile boolean ENABLE_BG_BAKE_DEDUP = true;

    /** Distinct animation signatures (unique animations) the baker has seen. */
    public static volatile int bgBakeSignatureGroups = 0;

    /** Live baker state (published each tick) for the status line — proves the
     *  idle budget engages AFK and shows how aggressively data over-segments. */
    public static volatile boolean bgBakeActive = false;
    public static volatile boolean bgBakeIdle   = false;
    public static volatile long    bgBakeBudgetMs = 0L;
    /** Ticks the background baker FAST-OUT skipped because the whole crowd is
     *  already baked + stable. Without this the baker rebuilt the 3000-job list
     *  and called the synchronized AnimationBakeCache.bind for every replay every
     *  tick FOREVER — measured ~10% of the render thread (bind alone 6.6%) on a
     *  cache that was 100% complete (bgBakeTicks delta = 0). Now it does only an
     *  O(1) edit-watch on the selected replay and returns. */
    public static volatile long    bgBakeIdleSkips = 0L;
    /** Distinct DATA variants (form+keyframes+properties). bgBakeSignatureGroups
     *  (unique BONE animations) ≤ this; the gap = bone-irrelevant data spread
     *  (e.g. position) that the bone-verify merges into shared bakes. */
    public static volatile int bgBakeUniqueData = 0;

    /** Army bake counters. */
    public static volatile long armyBakeHits      = 0L;
    public static volatile long armyBakeMisses    = 0L;
    public static volatile long armyBakeFrames    = 0L;
    public static volatile long armyBakeBytes     = 0L;
    public static volatile long armyBakeEvictions = 0L;
    /** Distinct (deduped) bone-matrix arrays actually stored. frames/unique =
     *  dedup ratio: identical replays collapse to one shared array per tick. */
    public static volatile long armyBakeUnique    = 0L;

    /** Hide-until-ready (smart-cache progressive reveal): while army/smart cache
     *  is ON, a replay whose bone cache is not yet COMPLETE is not rendered at
     *  all — so placing a huge crowd costs ~0 render time, the BackgroundBaker
     *  fills caches AFK, and each replay (or whole identical group) POPS IN the
     *  instant its cache is full. Only active together with ENABLE_ARMY_BAKE, so
     *  default-experience (army off) is untouched. */
    public static volatile boolean ENABLE_BAKE_HIDE_UNTIL_READY = true;
    /** Replays skipped this run because their cache wasn't complete yet. */
    public static volatile long hiddenUntilReady = 0L;

    /** Picker-pass animate skip: BBS's stencil picker (hover/click detection in
     *  the editor) renders EVERY selected replay to an offscreen pick-buffer via
     *  the full per-form animator. Selecting a big crowd (e.g. 1500) = 1500
     *  animator runs/frame → 0 fps (frame-timeline `final` phase = 4.5s). The
     *  picker only needs a SILHOUETTE for click detection — the animated pose is
     *  irrelevant there (world position comes from the matrix stack, not bones).
     *  So we skip animator.applyActions for the stencil pass: rest-pose
     *  silhouette, invisible to the user (pick buffer only), correct selection.
     *  Only active with ENABLE_ARMY_BAKE. */
    public static volatile boolean ENABLE_PICKER_SKIP_ANIMATE = true;
    public static volatile long pickerAnimateSkips = 0L;

    /** Stencil-pick throttle: BBS's editor hover/click picker (UIFilmController
     *  .renderStencil) renders forms to an offscreen pick buffer + glReadPixels
     *  EVERY frame the cursor is over the 3D viewport (proven: fps drops only
     *  when the mouse is in the scene). The alt branch renders the WHOLE crowd
     *  (3000) → freeze on select-all. The pick result can only change when the
     *  cursor MOVES, so we re-run renderStencil only on cursor move / alt change
     *  / a 1s safety refresh — stationary cursor reuses the last pick (a click
     *  without moving still hits the same form). Kills the per-frame readback
     *  stall and recovers the select-all freeze when not dragging. */
    public static volatile boolean ENABLE_STENCIL_PICK_THROTTLE = true;
    public static volatile long stencilPickSkips = 0L;

    /** Tier2h fast-pack: for CACHED replays, copy the cache's column-major flat
     *  bone array straight into the instanced bone buffer (PHASE U), skipping
     *  the cached-float→Matrix4f→cached-float round-trip that ran for all 3000
     *  instances every frame. Byte-identical output; pure per-frame CPU saving
     *  on the hot draw path (the moving-crowd FPS lever). */
    public static volatile boolean ENABLE_TIER2H_FASTPACK = true;

    /** Editor update throttle: BBS's FilmEditorController.updateEntities re-applies
     *  EVERY replay's keyframes (incl. 15 equipment-slot ItemStack interpolations
     *  each) EVERY client tick whenever the film is not "paused" — even parked in
     *  the editor not playing. At 3000 replays = ~75k interpolations/tick → freeze
     *  (confirmed via render-thread dump: updateEntities→applyReplay→keyframes.apply
     *  →setEquipmentStack). Re-applying the SAME parked cursor is idempotent, so we
     *  skip it while the film tick isn't advancing and playback isn't running. A
     *  500ms safety re-apply still reflects keyframe edits made while parked. */
    public static volatile boolean ENABLE_EDITOR_UPDATE_THROTTLE = true;
    public static volatile long editorUpdateSkips = 0L;

    /** UIReplayList.buildVisualList re-applies the current selection by calling
     *  visualList.indexOf(replay) for EVERY selected replay. Replay is a ValueGroup
     *  → List.indexOf does a DEEP value-compare (keyframes+15 items+properties) per
     *  element. buildVisualList runs EVERY render frame (render→buildVisualList), so
     *  with 3000 selected that's 3000×3000 deep-equals/frame = the shift-select-all
     *  freeze AND the continuous replay-menu lag (confirmed render-thread dump:
     *  buildVisualList→indexOf→ValueGroup.equals→ValueList.equals→Objects.equals).
     *  Fix: redirect that one indexOf to an IdentityHashMap lookup (O(N²)→O(N),
     *  map reused across frames). Identity is also MORE correct than equals for
     *  duplicate replays (equals collapses all dupes to the first matching index). */
    public static volatile boolean ENABLE_REPLAY_INDEX_FAST = true;
    public static volatile long replayIndexFast = 0L;
    public static volatile long replayIndexFallbacks = 0L;

    /** UIFilmController.getCurrentEntity does replays.getList().indexOf(replay)
     *  = O(N) DEEP ValueGroup.equals, and FilmEditorController.getTransition →
     *  isCurrent calls it ONCE PER REPLAY every render frame. At 3000 replays
     *  that's 3000×3000 deep-equals/frame, and our parallel startRenderFrame
     *  mixin fans it across all cores → whole-machine freeze on select (render-
     *  thread dump: parallelStartRenderFrame → getTransition → isCurrent →
     *  getCurrentEntity → indexOf → ValueGroup.equals). Fix: identity scan +
     *  one-entry cache keyed by the current Replay's identity (steady state O(1),
     *  a selection change = one O(N) pointer-compare scan). Volatile because the
     *  parallel loop calls it from ForkJoin workers. */
    public static volatile boolean ENABLE_CURRENT_ENTITY_CACHE = false;   /* MERGE 2026-07-24: default AUS. Im alten fork getunt+erprobt, frisch gemergt griff es ungefragt ins playback (emoticon bewegte sich nicht, erst beim pausieren sichtbar = gecachtes bild). Opt-in via /bbstools perf. */
    public static volatile long currentEntityCacheHits = 0L;
    public static volatile long currentEntityCacheMisses = 0L;

    /** Auto re-bake on edit: the bake cache + VAT atlas show a replay's BAKED
     *  pose, but editing its keyframes never invalidated the cache (UUID_SIG
     *  memoised the signature forever, invalidate* was never called, a fully-
     *  baked replay never re-baked) → pose edits were invisible. Now the baker
     *  re-hashes the CURRENTLY-SELECTED replay each tick (O(1) — uuid published
     *  by the getCurrentEntity mixin); on a data change it invalidates that
     *  replay's cache, drops its memoised signature (so it de-merges into its
     *  own animation group), and re-bakes it → the VAT atlas rebuilds → the new
     *  pose shows. World keyframes are applied per-frame by updateEntities, which
     *  the editor throttle skips when parked, so an edit also opens a short
     *  force-apply window (editForceApplyUntilMs) during which the throttle does
     *  NOT skip → world edits update live. */
    /** OFF: the auto-rebake-on-edit approach was abandoned — re-baking a 3000-tick
     *  replay on every edit-settle (60ms idle budget while the user is actively
     *  working) stuttered, and our bbstools$pose couldn't reproduce the keyframe
     *  animation anyway (diagnostic: bones identical across ticks). Replaced by
     *  ENABLE_EDIT_LIVE: the selected replay renders live via BBS. */
    public static volatile boolean ENABLE_BAKE_AUTO_REBAKE = false;
    public static volatile String  currentReplayUuid = null;   /* selected replay (published by getCurrentEntity mixin) */
    public static volatile long     editForceApplyUntilMs = 0L; /* (unused) */
    public static volatile long     bakeAutoRebakes = 0L;
    public static volatile long     BAKE_REBAKE_DEBOUNCE_MS = 400L; /* (unused) */

    /** THE simple editing fix (r4, user-requested "deutlich simpler"): while you
     *  are actively EDITING the selected replay, the WHOLE film renders 100%
     *  natively — the render-loop bypass returns early (no cancel), so BBS's own
     *  battle-tested pipeline (startRenderFrame pose + updateEntities + render)
     *  draws everything live and correct. No carve-out, no per-frame re-posing,
     *  nothing to fight. When you're NOT editing (idle / just selecting), the
     *  bypass + VAT bake run as normal → fast crowd, baked pose.
     *
     *  "Editing" = the selected replay's serialized data changed within the last
     *  EDIT_LIVE_WINDOW_MS. BackgroundBaker re-hashes ONLY the selected replay
     *  each client tick (cheap) and stamps editLiveUntilMs; the render thread just
     *  reads that long. */
    public static volatile boolean ENABLE_EDIT_LIVE = true;
    public static volatile long     editLiveRenders = 0L;
    /** now+window stamped on each detected edit of the selected replay; while
     *  System.currentTimeMillis() < this, the bypass renders fully native. */
    public static volatile long     editLiveUntilMs = 0L;
    /** How long after the last keyframe change the selected replay stays "actively
     *  edited" → its re-bake is skipped (bar RED). Once it lapses the replay bakes
     *  (bar GREEN) even while still selected. Short so green returns promptly. */
    public static volatile long     EDIT_LIVE_WINDOW_MS = 600L;
    public static volatile long     editLiveNativeFrames = 0L; /* frames we went full-native for an edit */

    /** Identity of the currently-selected entity (published by the getCurrentEntity
     *  mixin RETURN). The per-entity render3D fast-path (ModelFormRendererFastPath
     *  Mixin) compares context.entity to this: a MATCH means "this is the replay
     *  the user has selected" → it does NOT submit/cancel it to the bake, so its
     *  real mesh renders LIVE (posed every frame by startRenderFrame). That is THE
     *  unbaked-while-selected behaviour — the render3D cancel was why limbs stayed
     *  frozen no matter what the render-loop bypass did. */
    public static volatile Object   currentEntityRef = null;
    public static volatile long     editLiveSelectedSkips = 0L; /* render3D draws of the live selected mesh */

    /** Render-loop-bypass per-phase timing (last completed frame, microseconds).
     *  Splits the ~8ms "entities" hotspot so the 200fps work targets the real
     *  cost (snapshot eligibility / parallel matrix / submit allocs / GL flush /
     *  selected carve-out). Read via /eval. */
    public static volatile long     bypassSnapUs = 0L;
    public static volatile long     bypassMatUs = 0L;
    public static volatile long     bypassSubUs = 0L;
    public static volatile long     bypassFlushUs = 0L;
    public static volatile long     bypassCarveUs = 0L;

    /** Idle-frame fast skip: when the camera, film tick and crowd composition are
     *  ALL unchanged from last frame, the parallel snapshot+matrix+submit prep
     *  (~1.7ms render-thread) produces a draw identical to what idle-reuse already
     *  redraws → it's pure waste. Skip it entirely and re-issue the cached crowd
     *  via Tier2hInstancedDraw.redrawAllBundles(); the selected replay still renders
     *  live (carve-out). Camera is compared EXACTLY (any move → full prep) so the
     *  camera-relative crowd matrices never go stale (no swim). */
    /* r22: re-enabled. The camera-height bug was the idle path drawing at the
     * bypass point (editor/UI modelView); fixed by routing the idle redraw through
     * AFTER_ENTITIES (Tier2hInstancedDraw.requestIdleRedraw → drawDeferred), so the
     * idle skip is both fast (cached bundles, no rebuild) AND correct again. */
    public static volatile boolean ENABLE_BYPASS_IDLE_SKIP = true;
    public static volatile long     bypassIdleSkips = 0L;

    /** Eligibility-snapshot reuse. The bypass's `work` list (which replays are
     *  simple-crowd-eligible + their renderer/instance refs) is CAMERA-INDEPENDENT
     *  — only the per-replay world matrix depends on the camera. So on a camera
     *  MOVE (or playback), rebuilding that list every frame (the ~1ms bypassSnapUs:
     *  iterate entities + instanceof + getRenderer + alloc Eligible records) is
     *  pure waste; eligibility can't change while the crowd SIZE is unchanged.
     *  Cache it, reuse while size is stable → camera motion no longer pays the
     *  snapshot cost (THE "fps must not drop hard when moving" fix). Invalidated
     *  whenever the bypass bails (mixed/actor scene) or the crowd size changes. */
    public static volatile boolean ENABLE_BYPASS_WORK_REUSE = true;
    public static volatile long     bypassWorkReuses = 0L;

    /** GPU timing of the crowd draw (idle redrawAllBundles), via an
     *  ARB_timer_query GL_TIME_ELAPSED query read one frame late (no glFinish, no
     *  pipeline stall). Settles the idle "100fps loss" question definitively:
     *  if crowdGpuUs ≈ the frame-time gap, the loss is GPU fragment/shadow cost
     *  (only frustum cull / fewer instances helps); if it's tiny, the loss is CPU
     *  (our prep) and the work-reuse / pose-skip levers apply. Microseconds. */
    public static volatile boolean ENABLE_GPU_TIMER = true;
    public static volatile long     crowdGpuUs = 0L;

    /** OPT-IN frustum cull for the instanced crowd. Measured 2026-05-24: the same
     *  3000-replay scene runs 11.2ms (89fps) with the crowd ON-screen vs 6.4ms
     *  (156fps+) with it OFF-screen — the cost is Iris's DEFERRED shading of the
     *  crowd's gbuffer pixels (translucent+composite+final), proportional to the
     *  screen area the crowd covers, NOT the cheap 0.4ms gbuffer fill. So dropping
     *  instances that are fully OUTSIDE the camera frustum (already invisible →
     *  zero visual change) cuts the shaded-pixel count whenever a shot frames only
     *  part of the army (the usual cinematic case). DEFAULT OFF — honours the hard
     *  rule "never cull a VISIBLE replay" (this only ever removes off-screen ones).
     *  Radius is generous so a model poking in at a screen edge never pops. */
    public static volatile boolean ENABLE_FRUSTUM_CULL = false;
    public static volatile float    FRUSTUM_CULL_RADIUS = 4.0f;
    public static volatile long     frustumCulled = 0L;

    /** DIAGNOSTIC toggle: when false the render-loop bypass still cancels the
     *  vanilla loop (no slow-path crowd) and still renders the SELECTED replay
     *  live, but does NOT draw the instanced crowd at all — camera, world and UI
     *  stay byte-identical. Flip it (same camera framing the crowd) to read the
     *  crowd's TRUE cost as the fps delta, WITHOUT the confound of pointing the
     *  camera at less terrain. Toggle: /bbsdebug crowd-draw off / on, or
     *  http://localhost:7777/feature/crowd-draw/off. Default ON. */
    public static volatile boolean ENABLE_CROWD_DRAW = true;

    /** Per-tick updateEntities timing (the BBS keyframe re-apply: 15 equipment
     *  slots + pos/rot per replay). Throttled to ~0 while parked, runs FULL during
     *  playback — the suspected playback cost (3-phase test: in-world playback
     *  112fps vs parked editor 158fps, startRenderFrame only 0.3ms). Confirm the
     *  delta lands here before touching it. Read avg = NsAccum/Frames /1e6. */
    public static volatile long updateEntitiesNsAccum = 0L;
    public static volatile long updateEntitiesFrames  = 0L;

    /** THE editor dip-to-120 fix. When the editor is PARKED at an unchanged cursor,
     *  the throttle still force-runs a FULL updateEntities every 500ms (15 equipment
     *  slots + pos/rot × all replays) "to preview edits" — measured at 9.9ms for
     *  3000 replays, firing ~4×/s → periodic frame spikes (200fps→120). But a parked
     *  crowd is STATIC (renders its baked pose from the VAT atlas, positions frozen),
     *  so re-applying 3000 keyframe sets changes nothing visible. With this on, the
     *  parked-unchanged path re-applies ONLY the selected replay (cheap — its
     *  position/equipment edits still preview; its pose previews via startRenderFrame
     *  regardless) and cancels the 3000-crowd full. A cursor change (scrub/playback)
     *  still runs the full so the crowd repositions. Toggle /bbsdebug park-reapply
     *  or /feature/park-reapply/off. Default ON. */
    public static volatile boolean ENABLE_PARK_SELECTED_ONLY_REAPPLY = true;
    public static volatile long    parkSelectedReapplies = 0L;
    /** Selected replay object (not just uuid) for the single-entity parked re-apply. */
    public static volatile Object  currentReplay = null;

    public static volatile long formRendererSkipCount = 0L;
    public static volatile long distanceCullCount = 0L;
    public static volatile long shadowCullCount = 0L;
    public static volatile long onMorphFixCount = 0L;
    public static volatile long onDemorphFixCount = 0L;
    public static volatile long formRendererCallCount = 0L;
    public static volatile long shadowPassDetectedCount = 0L;
    public static volatile long lodBodyPartSkipCount = 0L;

    public static String dump()
    {
        return String.format(
            "distance_cull=%s  max=%.0f blocks  shadow_cull=%.0f blocks  skip_all_shadow=%s  lod=%s  lod_radius=%.0f blocks",
            ENABLE_DISTANCE_CULLING,
            Math.sqrt(MAX_RENDER_DISTANCE_SQ),
            Math.sqrt(SHADOW_CULLING_DISTANCE_SQ),
            SKIP_ALL_SHADOW_PASSES,
            ENABLE_LOD,
            Math.sqrt(LOD_DETAIL_RADIUS_SQ)
        );
    }

    public static String stats()
    {
        return String.format(
            "form_calls=%d  shadow_detected=%d  shadow_skip=%d  dist_cull=%d  shadow_cull=%d  lod_bp_skip=%d",
            formRendererCallCount, shadowPassDetectedCount, formRendererSkipCount,
            distanceCullCount, shadowCullCount, lodBodyPartSkipCount
        );
    }

    public static void resetCounters()
    {
        formRendererSkipCount = 0L;
        distanceCullCount = 0L;
        shadowCullCount = 0L;
        onMorphFixCount = 0L;
        onDemorphFixCount = 0L;
        formRendererCallCount = 0L;
        shadowPassDetectedCount = 0L;
        lodBodyPartSkipCount = 0L;
    }

    /**
     * r76: the full Tier-2h production stack — the exact flag set the working
     * "/bbstools replay-fast-path bench" command applied (minus the benchmark
     * timer). The Optimize→Army-bake menu toggle calls this so ONE click enables
     * the whole optimized + baked path (not just ENABLE_ARMY_BAKE alone, which
     * left the crowd un-instanced and the bar empty). Single source of truth so
     * menu + bench + any future command can't drift apart.
     * Coded by Afrobama/Claude.
     */
    public static void applyFullStack(boolean on)
    {
        ENABLE_FAST_PATH              = on;
        ENABLE_FAST_PATH_RENDER       = on;
        ENABLE_TIER2_GPU              = on;
        ENABLE_TIER2_DRAW             = on;
        ENABLE_TIER2_PARALLEL_SKIN    = on;
        ENABLE_TIER2_PARALLEL_EMIT    = on;
        ENABLE_TIER2_MERGE            = false;  /* v1 regression — always off */
        ENABLE_TIER2_MERGE_V2         = false;  /* v2 regression — always off */
        ENABLE_TIER2_H                = on;     /* GPU instanced draw */
        ENABLE_TIER2_PHASE_A_PARALLEL = false;  /* racy — always off */
        ENABLE_ARMY_BAKE              = on;     /* record-on-render anim cache */
        SKIP_BLOB_SHADOW              = on;     /* -2.9ms render-loop */
        ENABLE_RENDER_LOOP_BYPASS     = on;     /* parallel worldMatrix */
        ENABLE_CACHE_LOADING_BAR      = on;     /* cache bar in replay menu */
        ENABLE_BG_BAKE                = on;     /* fill-while-paused */
        ENABLE_ANIMATOR_CONFIG_CACHE  = on;     /* editor-freeze fix */
        ENABLE_INCREMENTAL_ENTITIES   = on;
        ENABLE_TIER3_POOLS            = on;
        ENABLE_PARALLEL_ANIMATOR      = on;
    }

}
