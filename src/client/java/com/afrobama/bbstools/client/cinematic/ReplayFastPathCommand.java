// Coded by Afrobama/Claude — /bbstools replay-fast-path toggle (Tier 1).
//
// Flips BBSPerfConfig.ENABLE_FAST_PATH at runtime so the user can
// enable/disable the Tier 1 measure-mode fast-path without a relaunch.
//
// Distinct from FastPathCommand (which toggles the crowd Iris fast-path,
// CROWD_IRIS_FAST_PATH). This command targets the replay render path.
//
// Usage:
//   /bbstools replay-fast-path on      — enable measure mode (counters + JFR)
//   /bbstools replay-fast-path off     — disable, all counters frozen
//   /bbstools replay-fast-path status  — print router stats + batch counters
//   /bbstools replay-fast-path reset   — zero counters
package com.afrobama.bbstools.client.cinematic;

import com.afrobama.bbstools.client.bbs.fastpath.AnimationBakeCache;
import com.afrobama.bbstools.client.bbs.fastpath.CrowdRouter;
import com.afrobama.bbstools.client.bbs.fastpath.FastPathBatch;
import com.afrobama.bbstools.client.bbs.fastpath.FastPathRenderer;
import com.afrobama.bbstools.client.bbs.fastpath.Tier2Renderer;
import com.afrobama.bbstools.client.bbs.fastpath.Tier2bDrawPath;
import com.afrobama.bbstools.client.bbs.fastpath.Tier2cParallelSkin;
import com.afrobama.bbstools.client.bbs.fastpath.Tier2dParallelEmit;
import com.afrobama.bbstools.client.bbs.fastpath.Tier2eMergedDraw;
import com.afrobama.bbstools.client.bbs.fastpath.Tier2eMergedDrawV2;
import com.afrobama.bbstools.client.bbs.fastpath.Tier2hInstancedDraw;
import com.afrobama.bbstools.client.bbs.fastpath.FpsSampler;
import com.afrobama.bbstools.perf.BBSPerfConfig;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class ReplayFastPathCommand
{
    /* Bench timer state — one-shot deadline + source to feed back to. */
    private static volatile long BENCH_DEADLINE_NS = 0L;
    private static volatile FabricClientCommandSource BENCH_SOURCE;
    private static volatile int BENCH_SECONDS = 0;
    private static volatile boolean BENCH_HANDLER_REGISTERED = false;

    private ReplayFastPathCommand() {}

    public static void register()
    {
        if (!BENCH_HANDLER_REGISTERED)
        {
            BENCH_HANDLER_REGISTERED = true;
            ClientTickEvents.END_CLIENT_TICK.register(client ->
            {
                if (BENCH_DEADLINE_NS > 0 && System.nanoTime() >= BENCH_DEADLINE_NS)
                {
                    FabricClientCommandSource src = BENCH_SOURCE;
                    BENCH_DEADLINE_NS = 0;
                    BENCH_SOURCE = null;
                    if (src != null)
                    {
                        src.sendFeedback(Component.literal(String.format(
                            "[bench] ===== %ds elapsed — auto-status =====", BENCH_SECONDS)));
                        emitStatus(src);
                    }
                }
            });
        }

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) ->
        {
            dispatcher.register(ClientCommands.literal("bbstools")
                .then(ClientCommands.literal("replay-fast-path")
                    .then(ClientCommands.literal("on")
                        .executes(ctx -> setFlag(ctx, true)))
                    .then(ClientCommands.literal("off")
                        .executes(ctx -> setFlag(ctx, false)))
                    .then(ClientCommands.literal("render")
                        .then(ClientCommands.literal("on")
                            .executes(ctx -> setRenderFlag(ctx, true)))
                        .then(ClientCommands.literal("off")
                            .executes(ctx -> setRenderFlag(ctx, false))))
                    .then(ClientCommands.literal("tier2")
                        .then(ClientCommands.literal("on")
                            .executes(ctx -> setTier2Flag(ctx, true)))
                        .then(ClientCommands.literal("off")
                            .executes(ctx -> setTier2Flag(ctx, false))))
                    .then(ClientCommands.literal("tier2-draw")
                        .then(ClientCommands.literal("on")
                            .executes(ctx -> setTier2DrawFlag(ctx, true)))
                        .then(ClientCommands.literal("off")
                            .executes(ctx -> setTier2DrawFlag(ctx, false))))
                    .then(ClientCommands.literal("tier2-parallel")
                        .then(ClientCommands.literal("on")
                            .executes(ctx -> setTier2ParallelFlag(ctx, true)))
                        .then(ClientCommands.literal("off")
                            .executes(ctx -> setTier2ParallelFlag(ctx, false))))
                    .then(ClientCommands.literal("tier2-emit")
                        .then(ClientCommands.literal("on")
                            .executes(ctx -> setTier2EmitFlag(ctx, true)))
                        .then(ClientCommands.literal("off")
                            .executes(ctx -> setTier2EmitFlag(ctx, false))))
                    .then(ClientCommands.literal("tier2-merge")
                        .then(ClientCommands.literal("on")
                            .executes(ctx -> setTier2MergeFlag(ctx, true)))
                        .then(ClientCommands.literal("off")
                            .executes(ctx -> setTier2MergeFlag(ctx, false))))
                    .then(ClientCommands.literal("tier2-e-v2")
                        .then(ClientCommands.literal("on")
                            .executes(ctx -> setTier2MergeV2Flag(ctx, true)))
                        .then(ClientCommands.literal("off")
                            .executes(ctx -> setTier2MergeV2Flag(ctx, false))))
                    .then(ClientCommands.literal("tier2-h")
                        .then(ClientCommands.literal("on")
                            .executes(ctx -> setTier2HFlag(ctx, true)))
                        .then(ClientCommands.literal("off")
                            .executes(ctx -> setTier2HFlag(ctx, false))))
                    .then(ClientCommands.literal("tier2-phase-a")
                        .then(ClientCommands.literal("on")
                            .executes(ctx -> setTier2PhaseAFlag(ctx, true)))
                        .then(ClientCommands.literal("off")
                            .executes(ctx -> setTier2PhaseAFlag(ctx, false))))
                    .then(ClientCommands.literal("incremental-entities")
                        .then(ClientCommands.literal("on")
                            .executes(ctx -> setIncrementalEntitiesFlag(ctx, true)))
                        .then(ClientCommands.literal("off")
                            .executes(ctx -> setIncrementalEntitiesFlag(ctx, false))))
                    .then(ClientCommands.literal("tier3-pools")
                        .then(ClientCommands.literal("on")
                            .executes(ctx -> setTier3PoolsFlag(ctx, true)))
                        .then(ClientCommands.literal("off")
                            .executes(ctx -> setTier3PoolsFlag(ctx, false))))
                    .then(ClientCommands.literal("army-bake")
                        .then(ClientCommands.literal("on")
                            .executes(ctx -> setArmyBakeFlag(ctx, true)))
                        .then(ClientCommands.literal("off")
                            .executes(ctx -> setArmyBakeFlag(ctx, false)))
                        .then(ClientCommands.literal("clear")
                            .executes(ReplayFastPathCommand::clearArmyBake)))
                    .then(ClientCommands.literal("shadow-skip")
                        .then(ClientCommands.literal("on")
                            .executes(ctx -> setShadowSkipFlag(ctx, true)))
                        .then(ClientCommands.literal("off")
                            .executes(ctx -> setShadowSkipFlag(ctx, false))))
                    .then(ClientCommands.literal("bypass-compare")
                        .then(ClientCommands.literal("on")
                            .executes(ctx -> setBypassCompareFlag(ctx, true)))
                        .then(ClientCommands.literal("off")
                            .executes(ctx -> setBypassCompareFlag(ctx, false))))
                    .then(ClientCommands.literal("bypass")
                        .then(ClientCommands.literal("on")
                            .executes(ctx -> setBypassFlag(ctx, true)))
                        .then(ClientCommands.literal("off")
                            .executes(ctx -> setBypassFlag(ctx, false))))
                    .then(ClientCommands.literal("loading-bar")
                        .then(ClientCommands.literal("on")
                            .executes(ctx -> setLoadingBarFlag(ctx, true)))
                        .then(ClientCommands.literal("off")
                            .executes(ctx -> setLoadingBarFlag(ctx, false))))
                    .then(ClientCommands.literal("animator-cache")
                        .then(ClientCommands.literal("on")
                            .executes(ctx -> setAnimatorCacheFlag(ctx, true)))
                        .then(ClientCommands.literal("off")
                            .executes(ctx -> setAnimatorCacheFlag(ctx, false))))
                    .then(ClientCommands.literal("bg-bake")
                        .then(ClientCommands.literal("on")
                            .executes(ctx -> setBgBakeFlag(ctx, true)))
                        .then(ClientCommands.literal("off")
                            .executes(ctx -> setBgBakeFlag(ctx, false))))
                    .then(ClientCommands.literal("bench")
                        .executes(ctx -> startBench(ctx, 30))
                        .then(ClientCommands.argument("seconds", IntegerArgumentType.integer(1, 600))
                            .executes(ctx -> startBench(ctx, IntegerArgumentType.getInteger(ctx, "seconds")))))
                    .then(ClientCommands.literal("status")
                        .executes(ReplayFastPathCommand::printStatus))
                    .then(ClientCommands.literal("reset")
                        .executes(ReplayFastPathCommand::reset))));
        });
    }

    private static int setArmyBakeFlag(CommandContext<FabricClientCommandSource> ctx, boolean on)
    {
        BBSPerfConfig.ENABLE_ARMY_BAKE = on;
        ctx.getSource().sendFeedback(Component.literal(String.format(
            "BBSTools army-bake %s — record-on-render animation cache. First render of each "
            + "(replay,tick) is live + recorded; subsequent renders replay from cache (skips "
            + "animator, no race). Pause film → cached == live. %s",
            on ? "ON" : "OFF",
            on ? "Play/scrub once to fill the cache." : "")));
        return Command.SINGLE_SUCCESS;
    }

    private static int setShadowSkipFlag(CommandContext<FabricClientCommandSource> ctx, boolean on)
    {
        BBSPerfConfig.SKIP_BLOB_SHADOW = on;
        ctx.getSource().sendFeedback(Component.literal(String.format(
            "BBSTools shadow-skip %s — skips per-replay blob-shadow buffer-fill (~2.9ms/frame "
            + "@3000). %s",
            on ? "ON" : "OFF",
            on ? "Blob shadows hidden (Iris draws real shadows later)." : "Blob shadows restored.")));
        return Command.SINGLE_SUCCESS;
    }

    private static int setBypassCompareFlag(CommandContext<FabricClientCommandSource> ctx, boolean on)
    {
        BBSPerfConfig.RENDER_LOOP_BYPASS_COMPARE = on;
        BBSPerfConfig.bypassCompareCount = 0L;
        BBSPerfConfig.bypassCompareMismatches = 0L;
        BBSPerfConfig.bypassCompareMaxDelta = 0.0;
        ctx.getSource().sendFeedback(Component.literal(String.format(
            "BBSTools bypass-compare %s — logs candidate-vs-BBS worldMatrix delta (no behavior "
            + "change). Play, then /bbstools replay-fast-path status → check bypass-compare maxDelta.",
            on ? "ON (counters reset)" : "OFF")));
        return Command.SINGLE_SUCCESS;
    }

    private static int setBypassFlag(CommandContext<FabricClientCommandSource> ctx, boolean on)
    {
        BBSPerfConfig.ENABLE_RENDER_LOOP_BYPASS = on;
        BBSPerfConfig.bypassFrames = 0L;
        BBSPerfConfig.bypassReplays = 0L;
        BBSPerfConfig.bypassFallbacks = 0L;
        ctx.getSource().sendFeedback(Component.literal(String.format(
            "BBSTools render-loop bypass %s — cancels BBS per-replay render loop for pure simple "
            + "crowds, parallel worldMatrix (16 cores) → Tier 2-h. Needs tier2-h ON. "
            + "VERIFY VISUALLY (replays still correct?). %s",
            on ? "ON (counters reset)" : "OFF",
            on ? "" : "BBS render loop restored.")));
        return Command.SINGLE_SUCCESS;
    }

    private static int setAnimatorCacheFlag(CommandContext<FabricClientCommandSource> ctx, boolean on)
    {
        BBSPerfConfig.ENABLE_ANIMATOR_CONFIG_CACHE = on;
        ctx.getSource().sendFeedback(Component.literal(String.format(
            "BBSTools animator-cache %s — throttles ensureAnimator's per-tick ActionsConfig rebuild "
            + "(the editor-freeze hotspot at 3000 replays). Re-resolves every %dms. %s",
            on ? "ON" : "OFF", BBSPerfConfig.ANIMATOR_CACHE_THROTTLE_MS,
            on ? "Turn ON before entering the editor with many replays." : "")));
        return Command.SINGLE_SUCCESS;
    }

    private static int setLoadingBarFlag(CommandContext<FabricClientCommandSource> ctx, boolean on)
    {
        BBSPerfConfig.ENABLE_CACHE_LOADING_BAR = on;
        ctx.getSource().sendFeedback(Component.literal(String.format(
            "BBSTools cache bar %s — progress bar in the REPLAY MENU (select a replay → "
            + "shows baked %%). Needs render-loop bypass ON (publishes per-replay progress). %s",
            on ? "ON" : "OFF",
            on ? "" : "")));
        return Command.SINGLE_SUCCESS;
    }

    private static int setBgBakeFlag(CommandContext<FabricClientCommandSource> ctx, boolean on)
    {
        BBSPerfConfig.ENABLE_BG_BAKE = on;
        ctx.getSource().sendFeedback(Component.literal(String.format(
            "BBSTools background-bake %s — fills the cache WHILE PAUSED (DaVinci-style), so the "
            + "menu bar climbs without playing. Bakes up to %d ticks/idle-frame. Needs army-bake + "
            + "render-loop bypass ON (pure crowd of simple replays). %s",
            on ? "ON" : "OFF", BBSPerfConfig.BG_BAKE_TICKS_PER_FRAME,
            on ? "Pause the film and watch the bar fill." : "")));
        return Command.SINGLE_SUCCESS;
    }

    private static int clearArmyBake(CommandContext<FabricClientCommandSource> ctx)
    {
        AnimationBakeCache.clear();
        com.afrobama.bbstools.client.bbs.fastpath.WorldTransformCache.clear();
        com.afrobama.bbstools.client.bbs.fastpath.BackgroundBaker.reset();
        ctx.getSource().sendFeedback(Component.literal(
            "BBSTools army-bake cache CLEARED (frames + counters + signatures). Re-fills on next play."));
        return Command.SINGLE_SUCCESS;
    }

    private static int setFlag(CommandContext<FabricClientCommandSource> ctx, boolean on)
    {
        BBSPerfConfig.ENABLE_FAST_PATH = on;
        ctx.getSource().sendFeedback(Component.literal(
            String.format("BBSTools replay-fast-path %s — Tier 1 measure mode (no render replacement yet)",
                on ? "ON" : "OFF")));
        return Command.SINGLE_SUCCESS;
    }

    private static int setRenderFlag(CommandContext<FabricClientCommandSource> ctx, boolean on)
    {
        BBSPerfConfig.ENABLE_FAST_PATH_RENDER = on;
        ctx.getSource().sendFeedback(Component.literal(
            String.format("BBSTools replay-fast-path RENDER %s — Tier 1.5b render-replace mode. " +
                "When ON, accepted replays render via FastPathRenderer (state-hoisted + " +
                "direct ModelInstance.render). Requires /bbstools replay-fast-path on.",
                on ? "ON" : "OFF")));
        return Command.SINGLE_SUCCESS;
    }

    private static int setTier2Flag(CommandContext<FabricClientCommandSource> ctx, boolean on)
    {
        BBSPerfConfig.ENABLE_TIER2_GPU = on;
        ctx.getSource().sendFeedback(Component.literal(
            String.format("BBSTools replay-fast-path TIER2 %s — Tier 2-a scaffold mode. " +
                "Builds SkinnedMeshIR cache + counters alongside Tier 1.5 draw. " +
                "Tier 2-b will replace draw with real GPU-instanced path.",
                on ? "ON" : "OFF")));
        return Command.SINGLE_SUCCESS;
    }

    private static int setTier2DrawFlag(CommandContext<FabricClientCommandSource> ctx, boolean on)
    {
        BBSPerfConfig.ENABLE_TIER2_DRAW = on;
        ctx.getSource().sendFeedback(Component.literal(
            String.format("BBSTools replay-fast-path TIER2-DRAW %s — Tier 2-b merged-buffer path. " +
                "Collapses N per-instance draws into 1 drawArrays per (model.id) bucket via " +
                "CPU bone-skin from cached SkinnedMeshIR. Requires /bbstools replay-fast-path on + " +
                "render on + tier2 on. Falls back per-bucket to FastPathRenderer for BOBJ models.",
                on ? "ON" : "OFF")));
        return Command.SINGLE_SUCCESS;
    }

    private static int setTier2ParallelFlag(CommandContext<FabricClientCommandSource> ctx, boolean on)
    {
        BBSPerfConfig.ENABLE_TIER2_PARALLEL_SKIN = on;
        ctx.getSource().sendFeedback(Component.literal(
            String.format("BBSTools replay-fast-path TIER2-PARALLEL %s — Tier 2-c 3-phase pipeline. " +
                "Phase A serial animator+matrix-extract, Phase B parallel skin via ForkJoinPool, " +
                "Phase C serial BufferBuilder emit + 1 drawArrays. Requires all of: replay-fast-path on, " +
                "render on, tier2 on, tier2-draw on. Replaces Tier 2-b when all gates pass.",
                on ? "ON" : "OFF")));
        return Command.SINGLE_SUCCESS;
    }

    private static int setTier2EmitFlag(CommandContext<FabricClientCommandSource> ctx, boolean on)
    {
        BBSPerfConfig.ENABLE_TIER2_PARALLEL_EMIT = on;
        ctx.getSource().sendFeedback(Component.literal(
            String.format("BBSTools replay-fast-path TIER2-EMIT %s — Tier 2-d parallel BufferBuilder " +
                "emit. N workers each build their own BufferBuilder in parallel (Iris extends each " +
                "independently), main thread issues N back-to-back drawArrays under same shader/texture. " +
                "Replaces Tier 2-c when ALL gates on (replay-fast-path/render/tier2/tier2-draw/tier2-parallel).",
                on ? "ON" : "OFF")));
        return Command.SINGLE_SUCCESS;
    }

    private static int setTier2MergeFlag(CommandContext<FabricClientCommandSource> ctx, boolean on)
    {
        BBSPerfConfig.ENABLE_TIER2_MERGE = on;
        ctx.getSource().sendFeedback(Component.literal(
            String.format("BBSTools replay-fast-path TIER2-MERGE %s — Tier 2-e v1 merged-buffer draw. " +
                "REGRESSION at 200 replays (50fps vs Tier 2-d's 70fps) — per-frame BufferAllocator + " +
                "VBO recreation. Use tier2-e-v2 instead.",
                on ? "ON" : "OFF")));
        return Command.SINGLE_SUCCESS;
    }

    private static int setTier2MergeV2Flag(CommandContext<FabricClientCommandSource> ctx, boolean on)
    {
        BBSPerfConfig.ENABLE_TIER2_MERGE_V2 = on;
        ctx.getSource().sendFeedback(Component.literal(
            String.format("BBSTools replay-fast-path TIER2-E-V2 %s — Tier 2-e v2 persistent-buffer " +
                "merged draw. REGRESSION at 1000reps (FPS=13 vs Tier 2-d's 20) — phaseF=38ms from " +
                "memcpy 130MB+upload. Use tier2-h instead.",
                on ? "ON" : "OFF")));
        return Command.SINGLE_SUCCESS;
    }

    private static int setTier2HFlag(CommandContext<FabricClientCommandSource> ctx, boolean on)
    {
        BBSPerfConfig.ENABLE_TIER2_H = on;
        ctx.getSource().sendFeedback(Component.literal(
            String.format("BBSTools replay-fast-path TIER2-H %s — Tier 2-h GPU-instancing. ONE " +
                "glDrawElementsInstanced per (model,texture) bucket; custom shader does bone-skin via " +
                "per-instance SSBO. Replaces ALL Tier 2-b/c/d/e CPU emit paths. Top of dispatch ladder. " +
                "NO Iris-compat in this revision — test with shaderpack OFF. Auto-disables on first GL " +
                "failure (TDR safety). Requires tier2 on + tier2-draw on.",
                on ? "ON" : "OFF")));
        return Command.SINGLE_SUCCESS;
    }

    private static int setTier2PhaseAFlag(CommandContext<FabricClientCommandSource> ctx, boolean on)
    {
        BBSPerfConfig.ENABLE_TIER2_PHASE_A_PARALLEL = on;
        ctx.getSource().sendFeedback(Component.literal(
            String.format("BBSTools replay-fast-path TIER2-PHASE-A %s — Tier 2-f parallelizes the " +
                "animator+matrix-extract phase across ForkJoinPool. Per-replay state is per-renderer " +
                "(StubEntity has no MC world race) so safe. Affects Tier 2-d (parallel-emit) only. " +
                "Falls back to serial on any exception. Closes the 1500@30fps gap.",
                on ? "ON" : "OFF")));
        return Command.SINGLE_SUCCESS;
    }

    private static int setIncrementalEntitiesFlag(CommandContext<FabricClientCommandSource> ctx, boolean on)
    {
        BBSPerfConfig.ENABLE_INCREMENTAL_ENTITIES = on;
        ctx.getSource().sendFeedback(Component.literal(
            String.format("BBSTools replay-fast-path INCREMENTAL-ENTITIES %s — replaces BBS's O(N) " +
                "BaseFilmController.createEntities (used on every replay dupe) with a UUID-keyed " +
                "cache. Dupe at 200-replay scale: 200 form-copies + 200 keyframe-applies → 1 new " +
                "entity + 199 reuses. Safe for dupe/load; full re-init still fires on UUID change.",
                on ? "ON" : "OFF")));
        return Command.SINGLE_SUCCESS;
    }

    private static int setTier3PoolsFlag(CommandContext<FabricClientCommandSource> ctx, boolean on)
    {
        BBSPerfConfig.ENABLE_TIER3_POOLS = on;
        ctx.getSource().sendFeedback(Component.literal(
            String.format("BBSTools replay-fast-path TIER3-POOLS %s — Tier 3 lite per-thread pools. " +
                "Workers reuse BufferAllocator via ThreadLocal (kill native malloc/free 15×/frame), " +
                "main thread reuses CubicMatrixRenderer per modelId (kill ArrayList<Matrix4f> alloc " +
                "200×/frame). Same pattern as MC's Tessellator singleton. Saves ~1-2ms at 200 replays.",
                on ? "ON" : "OFF")));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * One-shot benchmark: turn on all "known-good" fast-path flags +
     * parallel-animator + reset every counter + arm a tick-driven status
     * dump N seconds later. Saves the user from typing 10 commands every
     * test cycle.
     *
     * Production path: Tier 2-h GPU instancing + Tier 2-i parallel Phase A
     * (the BBS static-race that broke phase-a is now fixed by
     * MatricesThreadSafeMixin + CubicModelAnimatorThreadSafeMixin —
     * confirmed clean at 3000). Known-bad flags stay OFF: TIER2_MERGE /
     * TIER2_MERGE_V2 (memcpy regression).
     */
    private static int startBench(CommandContext<FabricClientCommandSource> ctx, int seconds)
    {
        /* Enable the shipped production path (Tier 2-h + 2-i). */
        BBSPerfConfig.ENABLE_FAST_PATH                 = true;
        BBSPerfConfig.ENABLE_FAST_PATH_RENDER          = true;
        BBSPerfConfig.ENABLE_TIER2_GPU                 = true;
        BBSPerfConfig.ENABLE_TIER2_DRAW                = true;
        BBSPerfConfig.ENABLE_TIER2_PARALLEL_SKIN       = true;
        BBSPerfConfig.ENABLE_TIER2_PARALLEL_EMIT       = true;
        BBSPerfConfig.ENABLE_TIER2_MERGE               = false;  /* v1 regression */
        BBSPerfConfig.ENABLE_TIER2_MERGE_V2            = false;  /* v2 also regression: phaseF=38ms @ 1000reps (memcpy 130MB+upload) */
        BBSPerfConfig.ENABLE_TIER2_H                   = true;   /* shipped: GPU instanced draw */
        BBSPerfConfig.ENABLE_TIER2_PHASE_A_PARALLEL    = false;  /* RACY: shared instance.model pose clobbered across workers. Superseded by army-bake (no per-frame animation → no race). */
        BBSPerfConfig.ENABLE_ARMY_BAKE                 = true;   /* record-on-render anim cache */
        BBSPerfConfig.SKIP_BLOB_SHADOW                 = true;   /* -2.9ms render-loop */
        BBSPerfConfig.ENABLE_RENDER_LOOP_BYPASS        = true;   /* verified maxDelta=0; parallel worldMatrix */
        BBSPerfConfig.ENABLE_CACHE_LOADING_BAR         = true;   /* cache bar in replay menu */
        BBSPerfConfig.ENABLE_BG_BAKE                   = true;   /* DaVinci-style fill-while-paused */
        BBSPerfConfig.ENABLE_ANIMATOR_CONFIG_CACHE     = true;   /* editor-freeze fix: throttle ensureAnimator config rebuild */
        BBSPerfConfig.ENABLE_INCREMENTAL_ENTITIES      = true;
        BBSPerfConfig.ENABLE_TIER3_POOLS               = true;
        BBSPerfConfig.ENABLE_PARALLEL_ANIMATOR         = true;

        /* Reset every counter we look at in status. */
        CrowdRouter.resetCounters();
        FastPathBatch.resetCounters();
        FastPathRenderer.resetCounters();
        Tier2Renderer.resetCounters();
        Tier2bDrawPath.resetCounters();
        Tier2cParallelSkin.resetCounters();
        Tier2dParallelEmit.resetCounters();
        Tier2eMergedDraw.resetCounters();
        Tier2eMergedDrawV2.resetCounters();
        Tier2hInstancedDraw.resetCounters();
        FpsSampler.reset();

        BBSPerfConfig.fastPathCancellations         = 0L;
        BBSPerfConfig.parallelAnimatorTicks         = 0L;
        BBSPerfConfig.parallelAnimatorFallbacks     = 0L;
        BBSPerfConfig.tier2fPhaseATasks             = 0L;
        BBSPerfConfig.tier2fPhaseAParallelNs        = 0L;
        BBSPerfConfig.tier2fPhaseAFallbacks         = 0L;
        BBSPerfConfig.incrementalCreateCalls        = 0L;
        BBSPerfConfig.incrementalReuses             = 0L;
        BBSPerfConfig.incrementalNewEntities        = 0L;
        BBSPerfConfig.incrementalRemovals           = 0L;
        BBSPerfConfig.incrementalLastMs             = 0L;
        BBSPerfConfig.incrementalMaxMs              = 0L;
        BBSPerfConfig.incrementalTotalMs            = 0L;
        BBSPerfConfig.tier3AllocReuses              = 0L;
        BBSPerfConfig.tier3AllocFreshAllocs         = 0L;
        BBSPerfConfig.tier3ExtractorReuses          = 0L;
        BBSPerfConfig.tier3ExtractorFresh           = 0L;
        BBSPerfConfig.resetFrameTiming();
        AnimationBakeCache.resetCounters();

        BENCH_SECONDS = seconds;
        BENCH_SOURCE = ctx.getSource();
        BENCH_DEADLINE_NS = System.nanoTime() + (seconds * 1_000_000_000L);

        ctx.getSource().sendFeedback(Component.literal(String.format(
            "[bench] production path ON (tier2-h + phase-a) + counters cleared. "
            + "Status auto-dumps in %d sec. Read frame-cost line for BBS-internal split.",
            seconds)));
        return Command.SINGLE_SUCCESS;
    }

    private static int printStatus(CommandContext<FabricClientCommandSource> ctx)
    {
        emitStatus(ctx.getSource());
        return Command.SINGLE_SUCCESS;
    }

    private static void emitStatus(FabricClientCommandSource source)
    {
        /* Build all status lines once — feed to chat AND auto-dump to a
         * txt file so Claude can read the snapshot directly without the
         * user having to screenshot+OCR. Path:
         *   <MC run dir>/bbstools-fastpath-status.txt
         * Append-mode + timestamp per entry so multiple status calls in
         * one session accumulate (caller can diff over time). */
        List<String> lines = new ArrayList<>();
        lines.add(String.format(
            "replay-fast-path: ENABLED=%s  RENDER=%s  TIER2=%s  TIER2_DRAW=%s  TIER2_PARALLEL=%s  TIER2_EMIT=%s  TIER2_MERGE=%s  TIER2_MERGE_V2=%s  TIER2_H=%s  TIER2_PHASE_A=%s  INCREMENTAL_ENTITIES=%s  TIER3_POOLS=%s  cancellations=%d  maxBones=%d",
            BBSPerfConfig.ENABLE_FAST_PATH, BBSPerfConfig.ENABLE_FAST_PATH_RENDER,
            BBSPerfConfig.ENABLE_TIER2_GPU, BBSPerfConfig.ENABLE_TIER2_DRAW,
            BBSPerfConfig.ENABLE_TIER2_PARALLEL_SKIN,
            BBSPerfConfig.ENABLE_TIER2_PARALLEL_EMIT,
            BBSPerfConfig.ENABLE_TIER2_MERGE,
            BBSPerfConfig.ENABLE_TIER2_MERGE_V2,
            BBSPerfConfig.ENABLE_TIER2_H,
            BBSPerfConfig.ENABLE_TIER2_PHASE_A_PARALLEL,
            BBSPerfConfig.ENABLE_INCREMENTAL_ENTITIES,
            BBSPerfConfig.ENABLE_TIER3_POOLS,
            BBSPerfConfig.fastPathCancellations, BBSPerfConfig.FAST_PATH_MAX_BONES));
        lines.add("router: " + CrowdRouter.snapshot());
        lines.add("passes: " + CrowdRouter.snapshotPasses());
        lines.add("types:  " + CrowdRouter.snapshotTypes());
        lines.add("entCls: " + CrowdRouter.snapshotEntityClasses());
        lines.add("forms:  " + CrowdRouter.snapshotFormIds());
        lines.add("batch:  " + FastPathBatch.snapshot());
        lines.add("render: " + FastPathRenderer.snapshot());
        lines.add("tier2:  " + Tier2Renderer.snapshot());
        lines.add("tier2b: " + Tier2bDrawPath.snapshot());
        lines.add("tier2c: " + Tier2cParallelSkin.snapshot());
        lines.add("tier2d: " + Tier2dParallelEmit.snapshot());
        lines.add("tier2e: " + Tier2eMergedDraw.snapshot());
        lines.add("tier2e-v2: " + Tier2eMergedDrawV2.snapshot());
        lines.add("tier2h: " + Tier2hInstancedDraw.snapshot());
        lines.add("army-bake: " + AnimationBakeCache.snapshot());
        lines.add(String.format(
            "shadow-skip: enabled=%s skipped=%d", BBSPerfConfig.SKIP_BLOB_SHADOW, BBSPerfConfig.bbsShadowSkipped));
        lines.add(String.format(
            "bypass-compare: enabled=%s samples=%d maxDelta=%.5f mismatches(>0.001)=%d",
            BBSPerfConfig.RENDER_LOOP_BYPASS_COMPARE, BBSPerfConfig.bypassCompareCount,
            BBSPerfConfig.bypassCompareMaxDelta, BBSPerfConfig.bypassCompareMismatches));
        lines.add(String.format(
            "bypass: enabled=%s frames=%d replays=%d fallbacks=%d",
            BBSPerfConfig.ENABLE_RENDER_LOOP_BYPASS, BBSPerfConfig.bypassFrames,
            BBSPerfConfig.bypassReplays, BBSPerfConfig.bypassFallbacks));
        lines.add(String.format(
            "cache-bar: enabled=%s (in replay menu)", BBSPerfConfig.ENABLE_CACHE_LOADING_BAR));
        lines.add(String.format(
            "bg-bake: enabled=%s dedup=%s sig=bone-verify active=%s idle=%s budgetMs=%d bakedTicks=%d uniqueAnims=%d dataVariants=%d expected/replay=%d",
            BBSPerfConfig.ENABLE_BG_BAKE, BBSPerfConfig.ENABLE_BG_BAKE_DEDUP,
            BBSPerfConfig.bgBakeActive, BBSPerfConfig.bgBakeIdle, BBSPerfConfig.bgBakeBudgetMs,
            BBSPerfConfig.bgBakeTicks, BBSPerfConfig.bgBakeSignatureGroups,
            BBSPerfConfig.bgBakeUniqueData, BBSPerfConfig.bgBakeExpectedSample));
        lines.add(String.format(
            "hide-until-ready: enabled=%s (army=%s) hiddenSkips=%d — un-baked replays invisible, pop in when cached",
            BBSPerfConfig.ENABLE_BAKE_HIDE_UNTIL_READY, BBSPerfConfig.ENABLE_ARMY_BAKE,
            BBSPerfConfig.hiddenUntilReady));
        lines.add(String.format(
            "picker-skip-animate: enabled=%s skips=%d — editor pick pass uses rest-pose silhouette (fixes multi-select 0fps)",
            BBSPerfConfig.ENABLE_PICKER_SKIP_ANIMATE, BBSPerfConfig.pickerAnimateSkips));
        lines.add(String.format(
            "stencil-pick-throttle: enabled=%s skips=%d — pick only on cursor-move (fixes mouse-in-3D fps + select-all freeze)",
            BBSPerfConfig.ENABLE_STENCIL_PICK_THROTTLE, BBSPerfConfig.stencilPickSkips));
        lines.add(String.format(
            "editor-update-throttle: enabled=%s skips=%d fastpack=%s — skip per-tick 3000-entity keyframe re-apply when parked (fixes select freeze)",
            BBSPerfConfig.ENABLE_EDITOR_UPDATE_THROTTLE, BBSPerfConfig.editorUpdateSkips,
            BBSPerfConfig.ENABLE_TIER2H_FASTPACK));
        lines.add(String.format(
            "replay-index-fast: enabled=%s fast=%d fallbacks=%d — buildVisualList indexOf O(N^2) deep-equals -> O(N) identity map (fixes shift-select-all freeze + menu lag)",
            BBSPerfConfig.ENABLE_REPLAY_INDEX_FAST, BBSPerfConfig.replayIndexFast,
            BBSPerfConfig.replayIndexFallbacks));
        lines.add(String.format(
            "animator-cache: enabled=%s throttleMs=%d resolveSkips=%d",
            BBSPerfConfig.ENABLE_ANIMATOR_CONFIG_CACHE, BBSPerfConfig.ANIMATOR_CACHE_THROTTLE_MS,
            BBSPerfConfig.animatorResolveSkips));

        /* Runtime envelope so Claude can see frame budget without
         * needing the user to type out FPS or heap manually. */
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        int fps = mc.getFps();
        long heapUsed = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024);
        long heapMax = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        lines.add(String.format(
            "runtime: FPS_now=%d  heap=%dMB / %dMB  cores=%d  workers=%d",
            fps, heapUsed, heapMax,
            Runtime.getRuntime().availableProcessors(),
            java.util.concurrent.ForkJoinPool.commonPool().getParallelism()));
        lines.add("FPS-rolling: " + FpsSampler.snapshot());
        lines.add("frame-cost: " + BBSPerfConfig.frameTimingSnapshot());
        lines.add(String.format(
            "parallel-animator: ENABLE=%s  ticks=%d  fallbacks=%d",
            BBSPerfConfig.ENABLE_PARALLEL_ANIMATOR,
            BBSPerfConfig.parallelAnimatorTicks,
            BBSPerfConfig.parallelAnimatorFallbacks));
        lines.add(String.format(
            "tier2f-phaseA: tasks=%d totalNs=%d fallbacks=%d",
            BBSPerfConfig.tier2fPhaseATasks,
            BBSPerfConfig.tier2fPhaseAParallelNs,
            BBSPerfConfig.tier2fPhaseAFallbacks));
        lines.add(String.format(
            "incremental-entities: calls=%d reuses=%d news=%d removals=%d lastMs=%d maxMs=%d totalMs=%d",
            BBSPerfConfig.incrementalCreateCalls,
            BBSPerfConfig.incrementalReuses,
            BBSPerfConfig.incrementalNewEntities,
            BBSPerfConfig.incrementalRemovals,
            BBSPerfConfig.incrementalLastMs,
            BBSPerfConfig.incrementalMaxMs,
            BBSPerfConfig.incrementalTotalMs));
        lines.add(String.format(
            "tier3-pools: allocReuses=%d allocFresh=%d extractorReuses=%d extractorFresh=%d",
            BBSPerfConfig.tier3AllocReuses,
            BBSPerfConfig.tier3AllocFreshAllocs,
            BBSPerfConfig.tier3ExtractorReuses,
            BBSPerfConfig.tier3ExtractorFresh));

        for (String line : lines)
        {
            source.sendFeedback(Component.literal(line));
        }

        dumpStatusToFile(source, lines);
    }

    private static void dumpStatusToFile(FabricClientCommandSource source, List<String> lines)
    {
        try
        {
            File runDir = Minecraft.getInstance().gameDirectory;
            File out = new File(runDir, "bbstools-fastpath-status.txt");

            StringBuilder sb = new StringBuilder();
            sb.append("# bbstools replay-fast-path status — ")
              .append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
              .append('\n');
            for (String line : lines)
            {
                sb.append(line).append('\n');
            }
            sb.append("# end-of-snapshot\n\n");

            Files.writeString(out.toPath(), sb.toString(),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);

            source.sendFeedback(Component.literal(
                "[dump] appended to " + out.getAbsolutePath()));
        }
        catch (Throwable t)
        {
            source.sendFeedback(Component.literal(
                "[dump] write failed: " + t));
        }
    }

    private static int reset(CommandContext<FabricClientCommandSource> ctx)
    {
        CrowdRouter.resetCounters();
        FastPathBatch.resetCounters();
        FastPathRenderer.resetCounters();
        Tier2Renderer.resetCounters();
        Tier2bDrawPath.resetCounters();
        Tier2cParallelSkin.resetCounters();
        Tier2dParallelEmit.resetCounters();
        Tier2eMergedDraw.resetCounters();
        Tier2eMergedDrawV2.resetCounters();
        Tier2hInstancedDraw.resetCounters();
        FpsSampler.reset();
        BBSPerfConfig.fastPathCancellations = 0L;
        ctx.getSource().sendFeedback(Component.literal("replay-fast-path counters reset (Tier 1.5 + Tier 2-a/b/c/d/e/e-v2/h + FPS sampler)"));
        return Command.SINGLE_SUCCESS;
    }

}
