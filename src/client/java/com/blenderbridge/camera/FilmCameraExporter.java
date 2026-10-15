// Coded by Afrobama/Claude — Blender<->BBS live-sync bridge (plan v2 task 1.3: film camera export).
package com.blenderbridge.camera;

import com.blenderbridge.BlenderBridgeClient;
import com.blenderbridge.BridgeProtocol;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import com.mojang.logging.LogUtils;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.clips.CameraClipContext;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.Clips;

import org.slf4j.Logger;

import java.util.List;

/**
 * FilmCameraExporter — plan v2 task 1.3: sample the open film's ENTIRE camera-clip timeline
 * (Idle/Dolly/Path/Keyframe/... whatever is active, layered/enveloped) into a dense per-tick
 * frame series, so the Blender add-on can rebuild it as F-curves on a dedicated camera and the
 * user can see + edit the camera fahrt in Blender's graph editor. ONE-WAY (BBS -&gt; Blender); the
 * reverse (Blender edits back into BBS clips) is a separate task, deliberately not started here.
 *
 * <h2>WHY SAMPLING, NOT PER-CLIP-TYPE TRANSLATION</h2>
 * <p>BBS' camera timeline is a stack: {@link Clips#getClips(int)} returns every clip active at a
 * tick sorted by layer, and {@link CameraClipContext#apply} lets each further blend via its
 * envelope (fade in/out) or read the position an earlier layer already wrote (additive clips like
 * {@code ShakeClip}/{@code TranslateClip} in the {@code camera.clips.modifiers} package).
 * Re-deriving that composition per clip TYPE would mean reimplementing BBS' own clip stack here
 * and would drift the moment a new clip type ships. Instead this asks the SAME clip-evaluator
 * BBS' own {@code RunnerCameraController}/{@code PlayCameraController} use
 * ({@code CameraWorkCameraController.apply}) — minus the parts of that method that are NOT camera
 * math ({@code TrackerClientClip}'s hand-bob/HUD statics, {@code AudioClientClip}'s sound
 * management, both client-render side effects this export must never trigger) — once per sampled
 * tick, across the whole film, recording wherever the camera ends up. This mirrors the
 * "sample every tick" pattern {@code EntityBakeHandler} already established for entity poses, and
 * is the plan's own fallback (2.8: dense-bake when a clip's interpolation cannot be mapped 1:1) —
 * here EVERY clip is dense-baked, since layered/enveloped composition has no single
 * "interpolation type" to preserve in the first place.</p>
 *
 * <h2>NOT the live Camera</h2>
 * <p>Deliberately does NOT touch {@code BBSModClient.getCameraController().camera} — that is
 * {@link com.blenderbridge.CameraBridge}'s job (the LIVE single-pose sync). This class builds its
 * OWN {@link CameraClipContext} + {@link Position}, evaluated purely against the film's stored
 * clip data. No live camera is read or written, no render-frame side effect exists, and the
 * export works even for a film that is not currently playing.</p>
 *
 * <h2>Continuity across samples</h2>
 * <p>ONE {@link Position} instance is reused and carried tick-to-tick as the loop walks
 * {@code 0..duration} in order — exactly what the live controller does (its own {@code position}
 * field is only ever reseeded from the actual on-screen camera, which itself was written by the
 * previous frame's apply). A tick with no active position-clip therefore holds the last written
 * pose, matching in-game behaviour, rather than snapping to some default. The upper bound
 * ({@code tick <= duration}, not {@code < duration}) mirrors {@link Clip#applyLast}'s own
 * convention of evaluating one tick past a clip's nominal end for "the last frame".</p>
 *
 * <h2>ENTITY-TRACKING CLIPS — resolved offline since task 1.3b (was: KNOWN LIMITATION)</h2>
 * <p>{@link CameraClipContext#entities} (used by {@code EntityClip}/{@code LookClip}/
 * {@code OrbitClip} to resolve a followed actor) is populated during real playback from the
 * replay entities ({@code BaseFilmController.createEntities} → shared into the context by
 * {@code Films.playFilm}/{@code UIFilmController.createEntities}). Task 1.3b replays that exact
 * recipe offline: {@link OfflineEntityResolver} builds the same index-keyed {@code StubEntity} map
 * from the film's replays and re-poses it per sampled tick via {@code replay.keyframes.apply} —
 * see its class-doc for what real playback does, what is deliberately NOT replicated (mount-sync,
 * client actions, render side effects) and why. Whatever still cannot resolve offline (no/disabled
 * target, riding targets, a clip that throws) is reported in the result's {@code warnings} array
 * (stage-1 contract: warn + keep exporting with the position holding, NEVER abort, never silently
 * wrong). A clip that throws offline is caught PER CLIP — before 1.3b it would have failed the
 * whole export; the per-clip catch plus warning is strictly more informative.</p>
 *
 * <h2>WHICH FILM</h2>
 * <p>Same shape as {@code ActorExporter}/{@code FilmBackup}'s {@code resolveOpenFilm()} (editor
 * film first, then any playing film) — deliberately duplicated per this package's established
 * convention rather than shared, so a future change to one does not silently change the others.</p>
 *
 * <h2>MESSAGE WIRING — raw string type, no new BridgeProtocol constant</h2>
 * <p>Wired via a RAW STRING case in {@code BlenderBridgeClient.dispatch}'s switch —
 * {@code "FILM_CAMERA_EXPORT"} in, {@code "FILM_CAMERA_EXPORT_RESULT"} out — instead of new
 * {@code BridgeProtocol.TYPE_*} fields. Both ends are body-only changes: this class is brand new
 * (lazy-loadable, no boot needed) and the switch's BODY merely widens by one case, but a new
 * {@code public static final String} on the already-loaded {@code BridgeProtocol} would be a new
 * member on a loaded class, which the W1+ deploy window (canAddMethod=false, task 0.2) forbids. A
 * raw string literal on both the Java case label and the Python request side effect avoids that
 * entirely, at the cost of not having a shared constant — acceptable for one channel used from
 * exactly two places (this file's {@link #sendResult}, and the dispatch case that calls
 * {@link #handle}).</p>
 */
public final class FilmCameraExporter
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** BBS runs at a fixed 20 ticks/second internally — sent so the Blender-side fps-mapping
     * (frame = tick * blenderFps/20, same formula {@code actors.py} already applies) has it
     * explicitly rather than relying on an unwritten shared constant. */
    private static final int TICKS_PER_SECOND = 20;

    private FilmCameraExporter()
    {
        /* static-only helper */
    }

    /**
     * Handle a FILM_CAMERA_EXPORT request (called from {@code BlenderBridgeClient.dispatch}'s
     * switch case). Never throws — every failure path answers immediately with an {@code ok:false}
     * FILM_CAMERA_EXPORT_RESULT rather than leaving the caller waiting.
     *
     * <p>REQUEST CORRELATION (W8-B4, the ce2d139/2178b12 pattern): the bridge broadcasts every
     * result to ALL connected clients, so the add-on can only tell its own result from a foreign
     * agent's by an echoed id. An optional {@code fields.requestId} on the request is echoed on
     * EVERY reply path — success, "no film open" failure, and the throwable catch alike (the
     * WORLD_EXPORT lesson: an id echoed only on success re-opens the race exactly on the paths
     * where a confused add-on hurts most). Missing/empty id = byte-identical old reply shape, so
     * older add-on builds and EXEC callers see no change.</p>
     */
    public static void handle(JsonObject message)
    {
        /* getFields(null-safe) -> getFieldString(null-safe): a malformed envelope degrades to ""
         * (= no echo), never to a throw before our own catch is armed. */
        String requestId = BridgeProtocol.getFieldString(BridgeProtocol.getFields(message), "requestId", "");

        try
        {
            JsonObject out = export();

            if (!requestId.isEmpty())
            {
                out.addProperty("requestId", requestId);
            }

            sendResult(out);
        }
        catch (Throwable t)
        {
            LOGGER.error("[blenderbridge] film camera export failed", t);

            JsonObject out = failure(t.getClass().getSimpleName() + ": " + t.getMessage());

            if (!requestId.isEmpty())
            {
                out.addProperty("requestId", requestId);
            }

            sendResult(out);
        }
    }

    /**
     * Sample the open film's camera clips into a dense per-tick frame series.
     *
     * <p>MAIN THREAD ONLY (touches live film/editor state, same constraint as
     * {@code ActorExporter.exportActors}). Never throws on its own — "no film open" is a normal
     * answer, not an exception; a clip whose {@code applyClip} itself throws is left to
     * {@link #handle}'s catch.</p>
     *
     * @return the FILM_CAMERA_EXPORT_RESULT payload; never null, every contract key always present
     */
    public static JsonObject export()
    {
        FilmRef ref = resolveOpenFilm();

        if (ref == null)
        {
            return failure("no film open");
        }

        Clips clips = ref.film.camera;
        int duration = clips.calculateDuration();

        JsonArray frames = new JsonArray();
        Position position = new Position();
        CameraClipContext context = new CameraClipContext();

        context.clips = clips;

        /* Task 1.3b: feed the film's replays into context.entities (the way real playback does)
         * so entity-tracking clips resolve; also collects the stage-1 warnings. See
         * OfflineEntityResolver's class-doc for the full recipe + what is deliberately skipped. */
        OfflineEntityResolver resolver = OfflineEntityResolver.arm(ref.film, context);

        try
        {
            for (int tick = 0; tick <= duration; tick++)
            {
                /* Same per-tick reset CameraWorkCameraController.apply() does, minus the tracker/audio
                 * side effects (see class-doc) — clear stale per-context cached values, THEN set up
                 * ticks/relativeTick/transition for this sample. */
                context.clipData.clear();
                context.setup(tick, 0F);

                /* Pose the offline entities at this film tick BEFORE the clips read them —
                 * the offline stand-in for BaseFilmController.updateEntities(ticks). */
                resolver.applyTick(tick);

                for (Clip clip : context.clips.getClips(tick))
                {
                    try
                    {
                        context.apply(clip, position);
                    }
                    catch (Throwable error)
                    {
                        /* Stage-1 contract (task 1.3b): a clip that cannot evaluate offline warns
                         * and holds the pose — it must never abort the whole export. */
                        resolver.recordClipFailure(clip, error);
                    }
                }

                frames.add(frame(tick, position));
            }
        }
        finally
        {
            /* Restore TrackerClientClip's live render statics — MUST run even when a frame
             * evaluation escalates (see OfflineEntityResolver "TRACKER RENDER-STATICS BRACKET"). */
            resolver.finish();
        }

        JsonObject out = new JsonObject();

        out.addProperty("ok", true);
        out.addProperty("film", ref.film.getId() == null ? "" : ref.film.getId());
        out.addProperty("source", ref.source);
        out.addProperty("clipCount", clips.get().size());
        out.addProperty("duration", duration);
        out.addProperty("fps", TICKS_PER_SECOND);
        out.add("frames", frames);
        /* Task 1.3b, additive schema change: stage-1 warnings — always present, empty when every
         * tracking reference resolved. The add-on appends them to its status line + console. */
        out.add("warnings", resolver.getWarnings());
        out.addProperty("error", "");

        LOGGER.info("[blenderbridge] film camera export: film={} source={} clips={} duration={} frames={} warnings={}",
            ref.film.getId(), ref.source, clips.get().size(), duration, frames.size(), resolver.getWarnings().size());

        return out;
    }

    /** One sampled frame: {@code {tick, x, y, z, pitch, yaw, roll, fov}} — BBS-space, degrees
     * (same convention as {@code BridgeProtocol.CAM_*}/{@code CameraBridge.readCurrentCamera}). */
    private static JsonObject frame(int tick, Position position)
    {
        JsonObject frame = new JsonObject();

        frame.addProperty("tick", tick);
        frame.addProperty("x", position.point.x);
        frame.addProperty("y", position.point.y);
        frame.addProperty("z", position.point.z);
        frame.addProperty("pitch", position.angle.pitch);
        frame.addProperty("yaw", position.angle.yaw);
        frame.addProperty("roll", position.angle.roll);
        frame.addProperty("fov", position.angle.fov);

        return frame;
    }

    /* ================================================================= *
     *  Film resolution — deliberately duplicated, see class-doc         *
     * ================================================================= */

    private static FilmRef resolveOpenFilm()
    {
        Film editorFilm = getEditorFilm();

        if (editorFilm != null)
        {
            return new FilmRef(editorFilm, "editor");
        }

        Film playingFilm = getPlayingFilm();

        if (playingFilm != null)
        {
            return new FilmRef(playingFilm, "playing");
        }

        return null;
    }

    /** peekDashboard() — NOT getDashboard(): the latter builds a dashboard as a side effect,
     * which a read-only export must not do. Null dashboard just means the UI was never opened. */
    private static Film getEditorFilm()
    {
        UIDashboard dashboard = com.blenderbridge.BBSCompat.peekDashboard();

        if (dashboard == null)
        {
            return null;
        }

        UIFilmPanel panel = dashboard.getPanel(UIFilmPanel.class);

        if (panel == null)
        {
            return null;
        }

        return panel.getData();
    }

    /** The first film currently playing in-world, or null when none is. */
    private static Film getPlayingFilm()
    {
        if (BBSModClient.getFilms() == null)
        {
            return null;
        }

        List<BaseFilmController> controllers = BBSModClient.getFilms().getControllers();

        if (controllers == null)
        {
            return null;
        }

        for (BaseFilmController controller : controllers)
        {
            if (controller != null && controller.film != null)
            {
                return controller.film;
            }
        }

        return null;
    }

    /* ================================================================= *
     *  Payload skeletons + wire helpers                                  *
     * ================================================================= */

    /** Failure payload. {@code frames}/{@code warnings} are empty arrays, never null — key set
     * byte-identical to a success payload (task 1.3b added {@code warnings} on both paths). */
    private static JsonObject failure(String error)
    {
        JsonObject out = new JsonObject();

        out.addProperty("ok", false);
        out.addProperty("film", "");
        out.addProperty("source", "");
        out.addProperty("clipCount", 0);
        out.addProperty("duration", 0);
        out.addProperty("fps", TICKS_PER_SECOND);
        out.add("frames", new JsonArray());
        out.add("warnings", new JsonArray());
        out.addProperty("error", error == null ? "" : error);

        return out;
    }

    private static void sendResult(JsonObject payload)
    {
        long seq = BlenderBridgeClient.STATE.nextOutboundSeq();

        /* Raw string type, no BridgeProtocol.TYPE_* constant — see class-doc "MESSAGE WIRING". */
        JsonObject envelope = BridgeProtocol.envelope("FILM_CAMERA_EXPORT_RESULT", "filmCamera", seq, BridgeProtocol.ORIGIN_BBS);

        /* Replace the empty fields object with the export payload directly — same convention as
         * BridgeProtocol.worldExportResult/actorsExportResult/EntityBakeHandler.sendResult. */
        envelope.add(BridgeProtocol.KEY_FIELDS, payload);

        BlenderBridgeClient.SERVER.send(envelope);
    }

    /** A resolved film plus which of the two "open" notions produced it. */
    private static final class FilmRef
    {
        private final Film film;
        private final String source;

        private FilmRef(Film film, String source)
        {
            this.film = film;
            this.source = source;
        }
    }
}
