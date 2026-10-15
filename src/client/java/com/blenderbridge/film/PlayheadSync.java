// Coded by Afrobama/Claude — Blender<->BBS live-sync bridge (plan v2 task 1.4: playhead sync).
package com.blenderbridge.film;

import com.blenderbridge.BlenderBridgeClient;
import com.blenderbridge.BridgeProtocol;
import com.blenderbridge.camera.CameraAuthority;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import com.mojang.logging.LogUtils;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import org.joml.Vector2i;

import org.slf4j.Logger;

/**
 * PlayheadSync — plan v2 task 1.4: keep Blender's timeline frame and BBS' film playhead
 * showing the SAME tick ("beide sehen immer dieselbe Szene", panel H50). Structurally this is
 * the camera-authority pattern (task 1.1) applied to a different shared scalar: exactly one
 * side drives at a time, {@code NOBODY} means BBS' own value is the live default everyone
 * mirrors, no side ever bounces a value it just received right back out.
 *
 * <h2>RECON (this task's own EXEC measurement — NOT reachable from task 1.3)</h2>
 * <p>The plan's own reconciliation note is correct: {@code FilmCameraExporter} (1.3) is an
 * OFFLINE sampler over {@code CameraClipContext} — it never touches a live runner/cursor, so it
 * has nothing to reuse here. The actual playhead lives on {@link UIFilmPanel} itself (which
 * implements BBS' {@code ICursor}):</p>
 * <ul>
 *   <li>{@code UIFilmPanel.getCursor()} → {@code this.runner.ticks} (the scrub position, an
 *       {@code int} tick).</li>
 *   <li>{@code UIFilmPanel.setCursor(int)} → clamps to {@code >= 0}, writes
 *       {@code this.runner.ticks}, and notifies the server (multiplayer SEEK) — the exact same
 *       call the timeline's own drag/keyboard shortcuts use, so applying a remote tick here is
 *       indistinguishable from a local scrub to the rest of BBS.</li>
 *   <li>{@code UIFilmPanel.isRunning()} → {@code this.runner.isRunning()}, i.e. whether the film
 *       is currently PLAYING (not just paused at a tick) — {@code this.runner} is a
 *       {@code RunnerCameraController}, the very "Runner" the camera-authority class-docs already
 *       distinguish from "Play" as a playback (not user-input) controller.</li>
 * </ul>
 * <p>All three are PUBLIC methods on an already-loaded class — reading/calling them needs no
 * boot, exactly like {@code ActorImporter}/{@code FilmCameraExporter} already call
 * {@code UIDashboard.getPanel}/{@code UIFilmPanel.getData()}. Verified live via EXEC against the
 * running game (see this task's proof log), not assumed from source alone.</p>
 *
 * <h2>OWNER MODEL — DELIBERATE SCOPE DECISION (Regel 0: documented, not hidden)</h2>
 * <p>The task text intentionally reuses ONLY the {@link CameraAuthority.Owner} ENUM (no new
 * enum, no new {@code BridgeProtocol} constant — a new member on that already-loaded class would
 * need a boot window per the deploy-klassen-regel) and specifies exactly one wire message,
 * {@code PLAYHEAD}, in both directions. No separate ownership-handshake type (the camera's
 * {@code CAMERA_OWNER}) is listed for this task. Rather than inventing a second message type,
 * the {@code owner} field is folded into the SAME {@code PLAYHEAD} envelope's {@code fields}
 * (plain JSON key, no Java constant — same idiom {@code CAMERA_OWNER}'s {@code fields.owner}
 * already uses):</p>
 * <ul>
 *   <li><b>Outbound (BBS → Blender):</b> {@link #tick()} fingerprints the panel's own
 *       cursor+playing state every client tick (mirrors {@code ActorPushTracker}'s fingerprint
 *       idiom) and pushes {@code {"tick":n,"playing":bool}} — no {@code owner} key needed, BBS is
 *       never the one claiming timeline authority — the instant it changed AND
 *       {@link #owner} != {@code BLENDER} (the {@code NOBODY} row of the camera's own matrix:
 *       "ja, live-preview"). While {@code owner == BLENDER}, BBS never pushes back — this is ALSO
 *       what prevents an echo: applying an inbound tick changes the panel's cursor, the very next
 *       {@link #tick()} sees that as "a change", but the push is gated shut by the same owner
 *       check, so nothing bounces back (measured: task 1.4 proof d).</li>
 *   <li><b>Inbound (Blender → BBS):</b> the add-on's "Drive timeline" toggle (analog
 *       {@code bbsbridge_drive_camera}) is the ONLY thing that ever puts an {@code owner} key on
 *       the wire: flipping it on/off sends one {@code PLAYHEAD} with
 *       {@code fields.owner = "BLENDER"|"NOBODY"} (the toggle-on variant also carries the current
 *       frame as an immediate handshake value, toggle-off carries no {@code tick} at all — a pure
 *       release ping). {@link #handle(JsonObject)} applies an {@code owner} key FIRST (so a
 *       release message is honoured even though it carries no position), and only then applies a
 *       {@code tick} — and ONLY when {@link #owner} is (now) {@code BLENDER}. A plain per-frame
 *       push from an already-driving Blender (no {@code owner} key) keeps whatever owner value is
 *       already recorded, exactly like the camera's continuous CAMERA stream needs no owner key on
 *       every frame either.</li>
 * </ul>
 * <p>Consequence, spelled out because it is easy to miss: turning "Drive timeline" OFF must send
 * that release ping — without it {@link #owner} would stay {@code BLENDER} forever after a single
 * use, permanently blocking the default BBS→Blender direction until the next {@code HELLO}. Both
 * the add-on toggle callback and {@link #reset()} (called from the existing {@code HELLO} dispatch
 * case, mirroring how {@code CameraAuthority} is released on reconnect) cover this.</p>
 *
 * <h2>PLAYBACK STATE</h2>
 * <p>{@code playing} follows the same owner and echo gates as {@code tick}. An inbound Blender
 * message toggles the BBS runner only while Blender owns the playhead. BBS pushes its own running
 * state while no Blender owner is active; the add-on mirrors it through Blender's native
 * animation-play/animation-cancel operators.</p>
 *
 * <h2>LOOP / PREVIEW RANGE</h2>
 * <p>The same state sample carries BBS' end-exclusive loop ticks and Blender's native preview
 * range. The add-on performs the FPS conversion and treats Blender's inclusive preview end as
 * an end-exclusive BBS tick. A range sample is validated atomically before ownership or editor
 * state changes: booleans must be booleans, bounds exact non-negative integers and
 * {@code max >= min}. This prevents malformed input from claiming the channel or leaving only
 * one loop bound changed.</p>
 *
 * <h2>NO PER-FILM IDENTITY GUARD (documented boundary, mirrors task 1.3)</h2>
 * <p>Task 1.0 (stable film-id) is still {@code [ENTWURF]}. Like {@code FilmCameraExporter}
 * (1.3) before it, this class does not block on it — the playhead is panel/runner-local state
 * ({@code RunnerCameraController.ticks}), not per-film wire data, so there is no cross-film
 * ambiguity to guard structurally beyond "whichever film the editor panel currently shows".</p>
 *
 * <h2>WHY A NEW CLASS, ONE TICK-CALL, ONE DISPATCH CASE</h2>
 * <p>Same deploy-model constraint as every other Phase-1/3/4 addition in this package
 * (W1+, {@code canAddMethod=false} measured, task 0.2): brand new, lazy-loadable class,
 * {@code BlenderBridgeClient} gains ZERO new members — {@link #tick()} rides the
 * already-registered {@code onClientTick} body (same shape as {@code EntityBakeHandler}/
 * {@code ActorPushTracker}), and the dispatch switch merely widens by one raw-string case
 * ({@code "PLAYHEAD"}, no {@code BridgeProtocol.TYPE_*} — same reasoning
 * {@code FilmCameraExporter} documents for {@code FILM_CAMERA_EXPORT}).</p>
 *
 * <h2>THREADING</h2>
 * <p>MAIN THREAD ONLY, like every other class in this package: {@link #tick()} is called from
 * {@code BlenderBridgeClient.onClientTick}, {@link #handle(JsonObject)} from {@code dispatch} —
 * both already main-thread-only call sites.</p>
 */
public final class PlayheadSync
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** BBS runs at a fixed 20 ticks/second — same constant FilmCameraExporter documents,
     * repeated here (not shared) per this package's small-per-class-constant convention. */
    private static final int TICKS_PER_SECOND = 20;

    private static volatile CameraAuthority.Owner owner = CameraAuthority.Owner.NOBODY;

    /** Whether {@link #lastTick}/{@link #lastPlaying} hold a real observation yet. The first
     * tick after connecting or opening the editor sends the current playhead as a snapshot,
     * then subsequent ticks push only actual changes. */
    private static boolean hasBaseline;
    private static int lastTick;
    private static boolean lastPlaying;
    private static boolean lastLooping;
    private static int lastLoopMin;
    private static int lastLoopMax;

    /** Counters for this task's ECHO-GATE / owner-gate proofs (EXEC-readable). */
    private static long pushCount;
    private static long appliedCount;

    private PlayheadSync()
    {
        /* static-only helper */
    }

    /**
     * Per-tick drain (called from {@code BlenderBridgeClient.onClientTick}'s widened body).
     * Cheap no-op when no film editor is open or nothing changed — see class-doc for why this
     * is safe to call unconditionally every tick.
     */
    public static void tick()
    {
        try
        {
            UIFilmPanel panel = getEditorPanel();

            if (panel == null)
            {
                /* Editor closed: forget the baseline so a LATER re-open never diffs against
                 * stale state from a previous session (mirrors ActorPushTracker's film-close
                 * reset). */
                hasBaseline = false;

                return;
            }

            int tick = panel.getCursor();
            boolean playing = panel.isRunning();
            Vector2i range = panel.getLoopingRange();
            boolean looping = BBSSettings.editorLoop.get() && range.x >= 0 && range.y > range.x;
            int loopMin = looping ? range.x : 0;
            int loopMax = looping ? range.y : 0;

            if (!hasBaseline)
            {
                lastTick = tick;
                lastPlaying = playing;
                lastLooping = looping;
                lastLoopMin = loopMin;
                lastLoopMax = loopMax;
                hasBaseline = true;

                /* Nach HELLO oder dem ersten Film-Oeffnen ist dies bereits der
                 * aktuelle BBS-Stand. Ohne diesen Snapshot bleibt Blender auf
                 * seinem alten Frame, bis jemand in BBS scrubbt. */
                if (owner != CameraAuthority.Owner.BLENDER)
                {
                    push(tick, playing, looping, loopMin, loopMax);
                }

                return;
            }

            if (tick == lastTick && playing == lastPlaying && looping == lastLooping
                && loopMin == lastLoopMin && loopMax == lastLoopMax)
            {
                /* IDLE-GATE: nothing changed since last tick — the normal case. */
                return;
            }

            lastTick = tick;
            lastPlaying = playing;
            lastLooping = looping;
            lastLoopMin = loopMin;
            lastLoopMax = loopMax;

            if (owner == CameraAuthority.Owner.BLENDER)
            {
                /* Blender drives right now — BBS never pushes back. This is ALSO the echo
                 * guard: a tick just applied by handle() below changed the panel's cursor, this
                 * very check sees it as "changed" too, but does not re-emit it. */
                return;
            }

            push(tick, playing, looping, loopMin, loopMax);
        }
        catch (Throwable t)
        {
            LOGGER.error("[blenderbridge] playhead tick failed", t);
        }
    }

    /**
     * Handle an inbound PLAYHEAD message (called from {@code BlenderBridgeClient.dispatch}'s
     * switch). Never throws.
     */
    public static void handle(JsonObject message)
    {
        try
        {
            if (BridgeProtocol.ORIGIN_BBS.equals(BridgeProtocol.getOrigin(message)))
            {
                /* Our own echo bounced back — never re-apply it (mirrors handleCamera). */
                return;
            }

            String uuid = BridgeProtocol.getUuid(message);
            long seq = BridgeProtocol.getSeq(message, Long.MIN_VALUE);

            if (!BlenderBridgeClient.STATE.shouldApply(uuid, seq))
            {
                /* Stale / out-of-order — drop (same LWW defense the continuous CAMERA stream
                 * uses; playhead is likewise a stream, not a one-shot request/reply). */
                return;
            }

            JsonObject fields = BridgeProtocol.getFields(message);

            if (fields == null)
            {
                return;
            }

            JsonElement tickElement = fields.get("tick");
            Integer tick = tickElement == null ? null : exactInteger(tickElement);
            boolean hasLoop = fields.has("loopEnabled");
            Boolean looping = hasLoop ? exactBoolean(fields.get("loopEnabled")) : null;
            Integer loopMin = hasLoop ? exactInteger(fields.get("loopMin")) : null;
            Integer loopMax = hasLoop ? exactInteger(fields.get("loopMax")) : null;

            if ((tickElement != null && (tick == null || tick < 0))
                || (hasLoop && (looping == null || loopMin == null || loopMax == null
                    || loopMin < 0 || loopMax < loopMin)))
            {
                /* Validate the complete state sample before applying even its owner. A malformed
                 * range must not claim the channel and then leave BBS half-updated. */
                LOGGER.warn("[blenderbridge] malformed PLAYHEAD state ignored");

                return;
            }

            /* Owner key first after validation: a pure release/claim ping carries no "tick" at
             * all. See class-doc "OWNER MODEL". */
            JsonElement ownerElement = fields.get("owner");

            if (ownerElement != null && !ownerElement.isJsonNull())
            {
                applyOwner(ownerElement.getAsString());
            }

            if (owner != CameraAuthority.Owner.BLENDER)
            {
                /* Not authoritative right now — a stray PLAYHEAD arriving while BBS' own
                 * default direction is live must not move BBS (owner-gate, task 1.4 proof e). */
                return;
            }

            if (!hasLoop && tick == null)
            {
                return;
            }

            UIFilmPanel panel = getEditorPanel();

            if (panel == null)
            {
                return;
            }

            if (hasLoop)
            {
                panel.cameraEditor.clips.loopMin = loopMin;
                panel.cameraEditor.clips.loopMax = loopMax;
                BBSSettings.editorLoop.set(looping && loopMax > loopMin);
                lastLooping = BBSSettings.editorLoop.get();
                lastLoopMin = loopMin;
                lastLoopMax = loopMax;
            }

            if (tick == null)
            {
                /* Loop-only update. */
                appliedCount++;

                return;
            }

            boolean playing = getBoolean(fields, "playing");

            panel.setCursor(tick);
            if (panel.isRunning() != playing)
            {
                panel.togglePlayback();
            }

            lastTick = tick;
            lastPlaying = playing;
            hasBaseline = true;
            appliedCount++;

            LOGGER.debug("[blenderbridge] playhead applied from blender: tick={}", tick);
        }
        catch (Throwable t)
        {
            LOGGER.error("[blenderbridge] playhead apply failed", t);
        }
    }

    /**
     * Release ownership back to {@code NOBODY} — called from {@code BlenderBridgeClient}'s
     * existing {@code HELLO} dispatch case (peer reconnect), the exact same hygiene
     * {@code CameraAuthority} gets there: ownership must never carry over a reconnect.
     */
    public static void reset()
    {
        if (owner != CameraAuthority.Owner.NOBODY)
        {
            LOGGER.info("[blenderbridge] playhead owner {} -> NOBODY (HELLO / peer reconnect)", owner);
        }

        owner = CameraAuthority.Owner.NOBODY;
        hasBaseline = false;
    }

    /* ================================================================= *
     *  EXEC-readable counters / state (task 1.4 proofs)                 *
     * ================================================================= */

    public static CameraAuthority.Owner owner()
    {
        return owner;
    }

    public static long pushCount()
    {
        return pushCount;
    }

    public static long appliedCount()
    {
        return appliedCount;
    }

    /* ================================================================= *
     *  Push                                                             *
     * ================================================================= */

    private static void push(int tick, boolean playing, boolean looping, int loopMin, int loopMax)
    {
        JsonObject fields = new JsonObject();

        fields.addProperty("tick", tick);
        fields.addProperty("playing", playing);
        fields.addProperty("loopEnabled", looping);
        fields.addProperty("loopMin", loopMin);
        fields.addProperty("loopMax", loopMax);

        long seq = BlenderBridgeClient.STATE.nextOutboundSeq();
        JsonObject envelope = BridgeProtocol.envelope("PLAYHEAD", "playhead", seq, BridgeProtocol.ORIGIN_BBS);

        envelope.add(BridgeProtocol.KEY_FIELDS, fields);

        BlenderBridgeClient.SERVER.send(envelope);
        pushCount++;

        LOGGER.debug("[blenderbridge] playhead push: tick={} playing={}", tick, playing);
    }

    /* ================================================================= *
     *  Owner                                                             *
     * ================================================================= */

    private static void applyOwner(String raw)
    {
        try
        {
            CameraAuthority.Owner next = CameraAuthority.Owner.valueOf(raw);

            if (next != owner)
            {
                LOGGER.info("[blenderbridge] playhead owner {} -> {}", owner, next);
            }

            owner = next;
        }
        catch (IllegalArgumentException e)
        {
            LOGGER.warn("[blenderbridge] PLAYHEAD with unusable owner '{}' -- ignored", raw);
        }
    }

    /* ================================================================= *
     *  Lookup + small helpers                                           *
     * ================================================================= */

    /** peekDashboard() — NOT getDashboard(): the latter constructs a dashboard as a side
     * effect. Same trap the rest of this package documents (ActorImporter/FilmCameraExporter).
     * Deliberately duplicated rather than shared, matching this package's established
     * convention of small per-class copies. */
    private static UIFilmPanel getEditorPanel()
    {
        UIDashboard dashboard = com.blenderbridge.BBSCompat.peekDashboard();

        if (dashboard == null)
        {
            return null;
        }

        UIFilmPanel panel = dashboard.getPanel(UIFilmPanel.class);

        if (panel == null || panel.getData() == null)
        {
            return null;
        }

        return panel;
    }

    private static boolean getBoolean(JsonObject fields, String key)
    {
        JsonElement element = fields.get(key);

        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()
            && element.getAsBoolean();
    }

    private static Boolean exactBoolean(JsonElement element)
    {
        if (element == null || !element.isJsonPrimitive()
            || !element.getAsJsonPrimitive().isBoolean())
        {
            return null;
        }

        return element.getAsBoolean();
    }

    private static Integer exactInteger(JsonElement element)
    {
        if (element == null || !element.isJsonPrimitive()
            || !element.getAsJsonPrimitive().isNumber())
        {
            return null;
        }

        double number = element.getAsDouble();

        if (!Double.isFinite(number) || number != Math.rint(number)
            || number < Integer.MIN_VALUE || number > Integer.MAX_VALUE)
        {
            return null;
        }

        return (int) number;
    }
}
