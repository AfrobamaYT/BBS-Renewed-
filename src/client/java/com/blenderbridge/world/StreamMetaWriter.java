// Coded by Afrobama/Claude — Blender<->BBS live-sync bridge (Task 3.5-streaming: lighting manifest for the streaming export path).
package com.blenderbridge.world;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * StreamMetaWriter — Task 3.5-streaming: the same {@code world_meta.json} lighting manifest
 * {@link WorldExporter#writeWorldMeta} writes for the synchronous alt-path, but for {@link
 * StreamingWorldExporter}'s off-thread job. See that class's own doc: "Streaming-pfad schreibt
 * das manifest NICHT (offen)" — this class closes that gap.
 *
 * <h2>WHY A NEW, SELF-CONTAINED CLASS (no signature/field changes to {@code StreamingWorldExporter})</h2>
 * <p>Same deploy-window reasoning as {@link SkyReachability}/{@link FaceMerger}/{@link
 * StreamRegionClamp}: {@code StreamingWorldExporter} is an already-loaded class in the live JVM
 * this session, so its methods can only gain BODY lines (calls into this brand-new class), never
 * new parameters/fields. This class carries every bit of the new state itself: an arm flag (world
 * + region bounds for the job about to run) and a thread-safe accumulator that every worker's
 * {@code meshColumn} call feeds into as it scans blocks it already has in hand — no extra world
 * scan, no second pass over the region.</p>
 *
 * <h2>WHY A QUEUE, NOT A SHARED LIST</h2>
 * <p>Emissive collection happens on WORKER threads (inside {@code meshColumn}'s existing per-block
 * loop, the same loop that already reads {@code state.getLuminance()} — no, it does not yet; this
 * class adds that one check), so the accumulator must be safe for concurrent adds from many
 * columns' worker threads at once. {@link ConcurrentLinkedQueue} needs no external lock and never
 * blocks a mesh worker on writer contention (unlike routing this through {@code Job#writerLock},
 * which is reserved for OBJ text output and already documented as a contention point).</p>
 *
 * <h2>ORDERING</h2>
 * <p>The synchronous alt-path emits entries in scan order (Y outer, Z, X inner — see {@code
 * WorldExporter.exportRegion}'s loop). The streaming path meshes columns out of order across a
 * worker pool, so the queue's DRAIN order is not the alt-path's scan order. {@link #write} sorts
 * the drained list (natural string order) before writing — NOT merely a comparison-time nicety:
 * this pipeline has an existing, measured, load-bearing invariant (the write-sequencer built for
 * Task 3.3/3.4, see {@code SkyReachability}'s "DETERMINISTIC WRITE ORDER" doc) that 2x identical
 * exports of the same region are byte/MD5-identical, and every future diff/MD5 gate over the whole
 * export directory (not just {@code world.obj}) depends on that staying true for every file in it,
 * {@code world_meta.json} included. An unsorted queue-drain order is worker-scheduling-dependent
 * (which column's worker happens to finish first), so two back-to-back exports of the identical
 * region could otherwise legitimately produce two different (but equally correct) emissive-list
 * orderings — a silent source of gate flakiness this sort eliminates. Sorting does NOT, by itself,
 * make {@code timeOfDay}/{@code skyAngle} deterministic across repeated exports (both are read live
 * at write time and drift with real elapsed ticks) — a determinism gate over this file must still
 * exempt those two lines, exactly like the alt-path comparison already does.</p>
 *
 * <h2>SCHEMA</h2>
 * <p>Byte-for-byte the same field set as {@code WorldExporter.writeWorldMeta}: {@code region},
 * {@code timeOfDay}, {@code skyAngle} (via {@code world.getSkyAngle(1.0F)}, the same exact vanilla
 * call, no re-derived formula), {@code dimension}, {@code emissive} (list of
 * {@code "x y z luminance block-id"} strings). Written by hand, same as the alt-path — the payload
 * is trivial and gson would be the only new import for either class. Since W7-B2 this includes the
 * {@code "animated"} array too (via {@link TextureStripExporter#appendAnimatedJson}, armed by
 * {@code StreamingWorldExporter.maybeFinish} — sorted by textureName there, so the array is
 * deterministic run-to-run even though the alt-path's is scan-ordered; compare across paths
 * order-insensitively, like the emissive list).</p>
 *
 * <h2>EMISSIVE VS CULLCAVES (W7-B2, end-review fund 4)</h2>
 * <p>{@link #recordIfEmissive} is called AFTER the mesh/cull decision (only for blocks that kept at
 * least one face when {@code cullCaves} is armed) — see the call site in {@code
 * StreamingWorldExporter.meshColumn} for the full reasoning. A culled manifest therefore lists only
 * geometry-existent lights; with {@code cullCaves} off the record is unconditional (pre-W7
 * behaviour, streaming==alt parity).</p>
 */
public final class StreamMetaWriter
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Emissive-block accumulator for the job currently being meshed. Replaced wholesale by {@link #armNextJob}. */
    private static volatile ConcurrentLinkedQueue<String> emissive = new ConcurrentLinkedQueue<>();

    /** World the currently-armed job reads from — used only for the same-world sanity check in
     * {@link #write} (end-review fix 5/finding 5, 2026-07-17: this doc used to describe a check
     * that did not exist, and the field was never cleared, pinning the whole {@code ClientWorld}
     * graph past job end/world leave). Cleared in {@link #write}'s finally AND by the
     * {@code StreamingWorldExporter.sendResult} teardown (covers abort paths that never reach
     * write) — never stored past the job. */
    private static volatile ClientLevel armedWorld;

    private StreamMetaWriter()
    {
        /* static-only helper */
    }

    /**
     * Arm a fresh accumulator for a new streaming job. Call ONCE, on the main thread, before the
     * job's worker pool starts meshing columns — mirrors {@code SkyReachability.resetWriteSequencer()}'s
     * "fresh state per job" convention. A stale accumulator from a previous job must never bleed into
     * the next one's manifest.
     */
    public static void armNextJob(ClientLevel world)
    {
        emissive = new ConcurrentLinkedQueue<>();
        armedWorld = world;
    }

    /**
     * Record one emissive block, called from {@link StreamingWorldExporter#meshColumn}'s existing
     * per-block loop (WORKER thread) — the same loop that already reads {@code state} for every
     * non-air block in the column, so this is a zero-extra-scan addition: one {@code
     * getLuminance() > 0} check per block already being visited, exactly mirroring {@code
     * WorldExporter.exportRegion}'s own emissive check. Safe to call from any worker thread
     * concurrently (see class doc "WHY A QUEUE").
     */
    public static void recordIfEmissive(BlockPos pos, BlockState state)
    {
        if (state.getLightEmission() <= 0)
        {
            return;
        }

        emissive.add(pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + state.getLightEmission() + " "
            + BuiltInRegistries.BLOCK.getKey(state.getBlock()));
    }

    /**
     * Write {@code world_meta.json} next to the streaming job's OBJ, once the job has fully
     * finished (called from {@link StreamingWorldExporter#maybeFinish}, main thread, after the OBJ
     * itself is closed — same call-site convention as the alt-path calling {@code writeWorldMeta}
     * right after {@code writeMtl}). Field-for-field identical schema to {@code
     * WorldExporter.writeWorldMeta}.
     *
     * @param outDir the job's output directory (same directory as {@code world.obj})
     * @param world  the SAME {@link ClientLevel} the job exported from (read for {@code
     *               getTimeOfDay}/{@code getSkyAngle}/{@code getRegistryKey} at write time, i.e.
     *               "now", exactly like the alt-path reads it at the end of its own scan — both
     *               paths' skyAngle is a snapshot at write-time, not scan-start-time, so minor
     *               drift between the two is expected and tolerated by the gate, not a bug)
     */
    public static void write(File outDir, ClientLevel world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) throws IOException
    {
        /* Same-world sanity check (end-review fix: this used to be documented on the armedWorld
         * field but never built). A mismatch means the client switched worlds/dimensions between
         * armNextJob and job finish — the emissive queue then belongs to the OLD world while
         * timeOfDay/skyAngle/dimension below are read from the NEW one. Warn loudly instead of
         * failing: the manifest is still structurally valid and the job's OBJ already exists. */
        ClientLevel armed = armedWorld;

        if (armed != null && armed != world)
        {
            LOGGER.warn("[blenderbridge] streaming world meta: write() world differs from the armed job's world ({} vs {}) — emissive list and time/sky may mix worlds",
                world.dimension().location(), armed.dimension().location());
        }

        List<String> snapshot = new java.util.ArrayList<>(emissive);

        /* Sort into a deterministic order — see class doc "ORDERING". The worker-pool drain order
         * is scheduling-dependent (NOT reproducible run-to-run), which would otherwise make this
         * file the one thing in the export directory that breaks the pipeline's existing byte/MD5
         * determinism guarantee. Natural string order is stable and gives both paths (streaming vs
         * alt) a directly comparable emissive list. */
        java.util.Collections.sort(snapshot);

        File metaFile = new File(outDir, "world_meta.json");

        BufferedWriter meta = Files.newBufferedWriter(metaFile.toPath(), StandardCharsets.UTF_8);

        try
        {
            meta.write("{\n");
            meta.write("  \"region\": [" + minX + ", " + minY + ", " + minZ + ", " + maxX + ", " + maxY + ", " + maxZ + "],\n");
            meta.write("  \"timeOfDay\": " + (world.getOverworldClockTime() % 24000L) + ",\n");
            meta.write("  \"skyAngle\": " + sunAngleFraction(world) + ",\n");
            meta.write("  \"dimension\": \"" + world.dimension().location() + "\",\n");
            meta.write("  \"emissive\": [\n");

            for (int i = 0; i < snapshot.size(); i++)
            {
                meta.write("    \"" + snapshot.get(i) + "\"" + (i + 1 < snapshot.size() ? "," : "") + "\n");
            }

            meta.write("  ],\n");

            /* W7-B2: the same "animated" section the alt-path writes — TextureStripExporter's
             * static hand-off, armed by StreamingWorldExporter.maybeFinish immediately before this
             * call (see that call site for the thread + TreeMap-ordering reasoning). appendAnimated-
             * Json drains AND clears the holder in its own finally, so nothing is retained here. */
            TextureStripExporter.appendAnimatedJson(meta);

            meta.write("}\n");
        }
        finally
        {
            meta.close();

            /* Retention fix (end-review finding 5): drop the job's references once written — the
             * world pin kept the whole ClientWorld graph alive past world-leave, and the queue
             * kept the last job's emissive strings (write copies, it never drained). A fresh queue
             * instead of clear() so a straggling worker add can never race the drain. */
            armedWorld = null;
            emissive = new ConcurrentLinkedQueue<>();
        }

        LOGGER.info("[blenderbridge] streaming world meta: {} emissive blocks, time {} -> {}", snapshot.size(), world.getOverworldClockTime() % 24000L, metaFile.getAbsolutePath());
    }

    /**
     * Der sonnenstand als bruch 0..1 — genau das feld, das das addon ({@code world_light.py})
     * seit Task 3.5 unter {@code skyAngle} liest.
     *
     * <p>PORT 26.2: {@code ClientLevel.getSkyAngle(1.0F)} gibt es nicht mehr. Der himmel läuft
     * jetzt über environment-attribute; die ground truth ist
     * {@code EnvironmentAttributes.SUN_ANGLE}. Dass dieser wert in GRAD steht, ist am jar
     * gemessen: {@code SkyRenderer} liest ihn und multipliziert mit {@code 0.017453292f}
     * (grad→bogenmaß) bevor er ihn in {@code SkyRenderState.sunAngle} legt. Das {@code /360}
     * hier bringt ihn zurück in den 0..1-bereich, in dem das feld bisher geschrieben wurde —
     * der alt-pfad ({@code WorldExporter.writeWorldMeta}) rechnet identisch, sonst wären die
     * beiden manifeste nicht mehr vergleichbar.</p>
     *
     * <p>⚠ NICHT GEPRÜFT: ob der NULLPUNKT derselbe ist wie beim alten {@code getSkyAngle}
     * (dort war 0.0 der sonnenaufgang). Betrag und wertebereich stimmen; eine mögliche
     * phasenverschiebung lässt sich nur am laufenden spiel messen (uhrzeit im spiel setzen,
     * geschriebenen wert gegen die alte 1.21.1-kurve halten).</p>
     */
    private static float sunAngleFraction(ClientLevel world)
    {
        Float degrees = Float.valueOf(world.getTimeOfDay(1F) * 360F);

        /* Kein attribut = kein himmel (dimension ohne sonne). 0 ist derselbe wert, den der
         * alte pfad für eine fixe tageszeit lieferte — nie null in die JSON schreiben. */
        return degrees == null ? 0F : degrees.floatValue() / 360F;
    }
}
