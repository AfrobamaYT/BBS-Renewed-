// Coded by Afrobama/Claude — Blender<->BBS live-sync bridge (plan v2 task 6.2: BBS render path).
package com.blenderbridge.camera;

import com.blenderbridge.BridgeProtocol;
import com.blenderbridge.BridgeState;
import com.blenderbridge.actors.FilmBackup;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import com.mojang.logging.LogUtils;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.clips.modifiers.TrackerClip;
import mchorse.bbs_mod.camera.clips.overwrite.KeyframeClip;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.utils.interps.CustomInterpolationManager;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

import org.slf4j.Logger;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * CameraClipWriter — plan v2 task 6.2: writes a Blender-authored camera fahrt BACK into the open
 * BBS film's camera timeline, so the SECOND render path (weg B, "renderfahrt") can have BBS itself
 * render the sequence — real MC image, first-person hand/HUD, Snowstorm/BBS particles — driven by a
 * camera path the user built/edited in Blender's graph editor.
 *
 * <p>This is the EXACT INVERSE of {@link FilmCameraExporter} (task 1.3, BBS -&gt; Blender): where the
 * exporter dense-samples BBS' whole clip stack into a flat per-tick frame series, this consumes a
 * per-channel keyframe payload (the add-on reads {@code BBS_FilmCam}'s baked F-curves) and rebuilds
 * it as ONE {@link KeyframeClip} on the camera timeline. The wire schema per keyframe is the SAME 7
 * fields the actor round-trip uses (tick/value/interp/lx/ly/rx/ry — COORDINATE_CONTRACT.md
 * "Keyframe-wire-grenze"), so {@link com.blenderbridge.actors.ActorImporter}'s channel-write logic
 * is the mirror this class follows; the values are ALSO the SAME BBS-space raw numbers the exporter
 * emitted, so the add-on owns the axis/rotation/lens conversion end-to-end (converting on both sides
 * is the classic double-transform bug).</p>
 *
 * <h2>WHY A KEYFRAMECLIP, NOT re-authoring Dolly/Path/Idle clips</h2>
 * <p>BBS' {@link KeyframeClip} is the one overwrite-camera clip type whose eight channels
 * (x/y/z/yaw/pitch/roll/fov/distance) map 1:1 onto what Blender exposes on a camera, and it is what
 * {@code KeyframeClip.fromCamera}/the graph-editor camera work already round-trip through. A Blender
 * F-curve has no "this is a Dolly" / "this is a Path" identity to recover — it is a dense curve —
 * exactly the situation {@link KeyframeClip} exists for. This matches the export side's own
 * reasoning (the exporter dense-bakes BECAUSE layered/enveloped composition has no single clip type)
 * and the plan's 2.8 dense-bake fallback.</p>
 *
 * <h2>CAMERA-CLIP REPLACEMENT — surgical, never the whole timeline blindly</h2>
 * <p>The write REPLACES the film's <em>overwrite</em> camera clips (the base position-writing layer:
 * {@code IdleClip}/{@code DollyClip}/{@code PathClip}/{@code KeyframeClip} — everything that extends
 * {@code CameraClip} and sits on the base layer as a position source) with the single authored
 * {@link KeyframeClip}, and by default LEAVES {@link TrackerClip} and other modifier/misc clips
 * (Shake/Translate/Audio/Subtitle/…) ALONE. Rationale (plan 6.2, panel H14): the POV hand/HUD lives
 * on {@code TrackerClip}s the user set up; blowing the whole timeline away would delete them. So the
 * default is "replace the camera fahrt, keep everything else". {@code fields.replaceAll=true}
 * escalates to clearing the ENTIRE camera timeline first (for a from-scratch render), and is a
 * deliberate opt-in, never the default.</p>
 *
 * <h2>POV / TRACKER — write OR preserve, per the payload</h2>
 * <p>Without a {@link TrackerClip} whose {@code show_hand}/{@code show_hud} are on, BBS renders NO
 * first-person hand and NO HUD (both default false — this is the whole reason 5.4 exists). Task 6.2
 * therefore lets the add-on request a tracker clip be written for the POV sections: a
 * {@code fields.tracker} object with {@code showHand}/{@code showHud}/{@code selector}(replay index)/
 * {@code tickStart}/{@code tickEnd} produces one {@link TrackerClip} spanning that range. If the
 * payload has NO {@code tracker} object, the film's existing tracker clips are left completely
 * untouched (the "keep the user's POV setup" path). Either way the tracker is a SEPARATE clip on its
 * own layer — the camera KeyframeClip only ever writes camera math.</p>
 *
 * <h2>WRITE-SAFETY (task 1.5) — non-negotiable, this mutates the USER's film</h2>
 * <p>Every write snapshots the film's {@link mchorse.bbs_mod.film.Film#toData()} tree BEFORE the
 * mutation and commits it to {@code run/blenderbridge_backups/&lt;filmId&gt;/&lt;timestamp&gt;.json}
 * only once something actually landed — the same net {@code ActorBridge.importActors} puts around
 * actor imports (panel H54: the live link is PROVEN to write into user films). Restore is a straight
 * {@link FilmBackup#restore(java.io.File, Film)}. The snapshot is committed HERE (not via {@code
 * FilmBackup.protect}) using {@link FilmBackup}'s already-public folder/rotation helpers because (a)
 * {@code protect} is shaped for the actor payload's {@code applied} count, and (b) adding a new
 * method to the already-loaded {@link FilmBackup} would violate the W1+ deploy window; a NEW lazy
 * class doing the commit through public helpers is deploy-safe. Unlike the throttled actor path this
 * commits UNCONDITIONALLY on success (not once-per-30s): a camera-clip write is a rare, deliberate,
 * user-confirmed action, not a per-second push, so throttling it away would be exactly wrong.
 * Rotation still runs through {@link FilmBackup#enforceLimit(String)} so the same 20-file cap and
 * lexicographic-time ordering hold across both writers.</p>
 *
 * <h2>ECHO GUARD</h2>
 * <p>The whole mutation runs inside {@link BridgeState#withRemoteApply(Runnable)} for the same reason
 * {@link com.blenderbridge.actors.ActorImporter} does: without it BBS-side change detection would see
 * the values we just received as a local change and bounce them back to Blender.</p>
 *
 * <h2>MESSAGE WIRING — raw string type</h2>
 * <p>{@code "CAMERA_CLIP_WRITE"} in, {@code "CAMERA_CLIP_WRITE_RESULT"} out — a raw-string dispatch
 * case, no new {@code BridgeProtocol.TYPE_*} constant (a new static on the already-loaded
 * BridgeProtocol would be a member-add on a loaded class, which the W1+ deploy window forbids). Same
 * body-only pattern as {@link FilmCameraExporter}. requestId is echoed on every reply path
 * (ce2d139/W8-B4 discipline) so the broadcasting bridge lets the add-on tell its own result from a
 * foreign agent's.</p>
 *
 * <h2>THREADING</h2>
 * <p>MAIN THREAD ONLY — it mutates live film/editor state, same constraint as {@link
 * com.blenderbridge.actors.ActorImporter}. Invoked from {@code BlenderBridgeClient.dispatch}.</p>
 */
public final class CameraClipWriter
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** BBS' own keyframe handle defaults (mirrors {@code ActorImporter.DEFAULT_HANDLE_*}). */
    private static final float DEFAULT_HANDLE_X = 5F;
    private static final float DEFAULT_HANDLE_Y = 0F;

    /** The eight camera channels a {@link KeyframeClip} exposes, in payload-key form. */
    private static final String[] CAMERA_CHANNELS =
        {"x", "y", "z", "yaw", "pitch", "roll", "fov", "distance"};

    /** Same filesystem-safe, sortable-by-name==sortable-by-time format {@link FilmBackup} uses, so
     *  both writers' backups rotate together under one {@link FilmBackup#enforceLimit(String)} cap. */
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");

    private CameraClipWriter()
    {
        /* static-only helper */
    }

    /* ================================================================= *
     *  Dispatch entry                                                    *
     * ================================================================= */

    /**
     * Handle a CAMERA_CLIP_WRITE request (from {@code BlenderBridgeClient.dispatch}). Never throws —
     * every failure answers immediately with an {@code ok:false} result rather than leaving the
     * add-on waiting. requestId is echoed on every path (W8-B4 pattern, {@link FilmCameraExporter}).
     */
    public static void handle(JsonObject message, BridgeState state)
    {
        JsonObject fields = BridgeProtocol.getFields(message);
        String requestId = BridgeProtocol.getFieldString(fields, "requestId", "");

        JsonObject out;

        try
        {
            out = write(fields, state);
        }
        catch (Throwable t)
        {
            LOGGER.error("[blenderbridge] camera clip write failed", t);
            out = failure(t.getClass().getSimpleName() + ": " + t.getMessage());
        }

        if (!requestId.isEmpty())
        {
            out.addProperty("requestId", requestId);
        }

        sendResult(out);
    }

    /* ================================================================= *
     *  The write                                                        *
     * ================================================================= */

    /**
     * Write the Blender-authored camera fahrt into the open film's camera timeline.
     *
     * <p>MAIN THREAD ONLY. Never throws on its own — "no film open" / "no channels" are normal
     * {@code ok:false} answers; a genuinely unexpected throw is left to {@link #handle}'s catch.</p>
     *
     * @param fields the CAMERA_CLIP_WRITE {@code fields} object
     * @param state  the shared bridge state (echo guard). Mandatory — a null state is rejected.
     * @return the CAMERA_CLIP_WRITE_RESULT payload; never null, every contract key always present
     */
    public static JsonObject write(JsonObject fields, BridgeState state)
    {
        if (fields == null)
        {
            return failure("no payload");
        }

        if (state == null)
        {
            /* Echo guard is a class contract, not an optimisation — refuse rather than loop. */
            return failure("no bridge state");
        }

        JsonElement channelsElement = fields.get("channels");

        if (channelsElement == null || !channelsElement.isJsonObject())
        {
            return failure("no channels in payload");
        }

        JsonObject channels = channelsElement.getAsJsonObject();

        if (!hasAnyCameraChannel(channels))
        {
            /* An empty {} or an object with only non-camera keys: nothing to write. Reported as a
             * normal failure so a bad add-on payload cannot silently wipe the timeline. */
            return failure("payload carries no camera channels");
        }

        FilmRef ref = resolveOpenFilm();

        if (ref == null)
        {
            return failure("no film open");
        }

        String validation = CameraClipWriteValidation.check(fields, ref.film);

        if (!validation.isEmpty())
        {
            JsonObject out = failure(validation);
            out.addProperty("notApplied", true);
            return out;
        }

        boolean replaceAll = getBoolean(fields, "replaceAll", false);
        JsonObject trackerObject = optObject(fields, "tracker");

        /* Exact pre-mutation tree; compatible with FilmBackup.restore(). */
        BaseType before = ref.film.toData();

        int[] result = new int[3];

        state.withRemoteApply(() -> writeGuarded(ref.film, ref.panel, channels, replaceAll, trackerObject, result));

        int keysWritten = result[0];

        File backupFile = null;

        if (keysWritten > 0)
        {
            backupFile = commitBackup(ref.film.getId(), before);
        }

        JsonObject out = new JsonObject();

        out.addProperty("ok", true);
        out.addProperty("film", ref.film.getId() == null ? "" : ref.film.getId());
        out.addProperty("source", ref.source);
        out.addProperty("keysWritten", keysWritten);
        out.addProperty("cameraClipsRemoved", result[1]);
        out.addProperty("trackerClipsWritten", result[2]);
        out.addProperty("replaceAll", replaceAll);
        out.addProperty("backup", backupFile == null ? "" : backupFile.getName());
        out.addProperty("error", "");

        LOGGER.info("[blenderbridge] camera clip write: film={} source={} keys={} removed={} tracker={} replaceAll={} backup={}",
            ref.film.getId(), ref.source, keysWritten, result[1], result[2], replaceAll,
            backupFile == null ? "-" : backupFile.getName());

        return out;
    }

    /** Apply the already validated replacement inside the echo guard. */
    private static void writeGuarded(Film film, UIFilmPanel panel, JsonObject channels,
        boolean replaceAll, JsonObject trackerObject, int[] result)
    {
        Clips camera = film.camera;

        /* Build the complete detached replacement before removing any live camera work. */
        KeyframeClip clip = new KeyframeClip();
        int keysWritten = fillCameraClip(clip, channels);

        if (keysWritten == 0)
        {
            throw new IllegalStateException("validated camera replacement produced no keys");
        }

        int[] range = channelTickRange(channels);
        clip.tick.set(range[0]);
        clip.duration.set(Math.max(1, range[1] - range[0]));
        result[1] = removeOldCameraClips(camera, replaceAll);
        clip.layer.set(camera.findFreeLayer(clip));
        camera.addClip(clip);
        result[0] = keysWritten;

        /* Omitted tracker means preserve the existing POV setup. */
        if (trackerObject != null)
        {
            result[2] = writeTrackerClip(camera, trackerObject);
        }

        /* Existing modifiers keep their authored layer; findFreeLayer placed only new clips. */
        camera.sync();

        /* A playing film has no editor panel to refresh or save through. */
        if ((keysWritten > 0 || result[2] > 0) && panel != null)
        {
            handOverToBBS(panel);
        }
    }

    /* ================================================================= *
     *  Write-safety backup (task 1.5) — through FilmBackup's public API  *
     * ================================================================= */

    /**
     * Commit a pre-mutation snapshot of the film to
     * {@code run/blenderbridge_backups/&lt;filmId&gt;/&lt;timestamp&gt;.json}, then rotate via {@link
     * FilmBackup#enforceLimit(String)}. Uses {@link FilmBackup#backupFolder(String)} (which
     * sanitises the id) + {@link DataToString#write(File, BaseType, boolean)} — the same on-disk
     * shape {@link FilmBackup} writes for actor imports, so {@link FilmBackup#restore(File, Film)}
     * and the 20-file rotation apply to camera backups verbatim.
     *
     * <p>Never throws — a backup failure is logged and swallowed; the mutation already happened and
     * must not be undone by a backup bug (the same belt-and-braces stance {@code FilmBackup.protect}
     * takes). Returns the file written, or null on failure.</p>
     */
    private static File commitBackup(String filmId, BaseType before)
    {
        if (before == null)
        {
            return null;
        }

        try
        {
            File folder = FilmBackup.backupFolder(filmId);

            if (!folder.exists() && !folder.mkdirs() && !folder.exists())
            {
                LOGGER.error("[blenderbridge] CameraClipWriter could not create backup folder {}", folder);

                return null;
            }

            File file = new File(folder, LocalDateTime.now().format(TIMESTAMP_FORMAT) + ".json");

            DataToString.write(file, before, true);

            /* enforceLimit sanitises the id itself (folder-name form), so pass the RAW id — passing
             * the folder's already-sanitised name would double-sanitise a legal id back into a
             * different folder. FilmBackup.backupFolder(rawId) and enforceLimit(rawId) agree. */
            FilmBackup.enforceLimit(filmId == null ? "" : filmId);

            return file;
        }
        catch (Throwable t)
        {
            LOGGER.error("[blenderbridge] CameraClipWriter backup failed for film '{}'", filmId, t);

            return null;
        }
    }

    /* ================================================================= *
     *  Camera clip build                                                *
     * ================================================================= */

    /**
     * Remove the base-layer camera fahrt that the new KeyframeClip replaces.
     *
     * <p>{@code replaceAll=false} (default): removes only overwrite camera clips — the ones that
     * SOURCE the camera position ({@code IdleClip}/{@code DollyClip}/{@code PathClip}/{@code
     * KeyframeClip}, all extending {@code CameraClip} on the base layer). Modifier clips
     * ({@code TrackerClip}/{@code ShakeClip}/{@code TranslateClip}/…) and misc clips
     * ({@code AudioClip}/{@code SubtitleClip}/…) are kept — the POV hand/HUD lives on TrackerClips.
     * Identifying "overwrite" clips by package keeps this robust as new clip types ship: anything in
     * {@code camera.clips.overwrite} is a position source. {@code replaceAll=true}: clears the whole
     * timeline for a from-scratch render (opt-in).</p>
     *
     * @return how many clips were removed
     */
    private static int removeOldCameraClips(Clips camera, boolean replaceAll)
    {
        /* Copy first — remove() mutates the backing list. */
        List<Clip> toRemove = new ArrayList<>();

        for (Clip clip : camera.get())
        {
            if (replaceAll || isOverwriteCameraClip(clip))
            {
                toRemove.add(clip);
            }
        }

        for (Clip clip : toRemove)
        {
            camera.remove(clip);
        }

        return toRemove.size();
    }

    /** True for the base-layer position-sourcing clips (package {@code camera.clips.overwrite}:
     *  Idle/Dolly/Path/Keyframe). Package-name check, not a class list, so a future overwrite clip
     *  type is covered without editing this. */
    private static boolean isOverwriteCameraClip(Clip clip)
    {
        return clip.getClass().getName().contains(".camera.clips.overwrite.");
    }

    /**
     * Fill the KeyframeClip's channels from the payload. Each named channel is rebuilt from its
     * keyframe array (the channels start empty on a fresh clip, so this is a straight insert — no
     * clear needed, unlike ActorImporter which reuses live channels). Channels ABSENT from the
     * payload stay empty, which KeyframeClip.applyClip treats as "don't write this axis" (its
     * {@code if (!channel.isEmpty())} guards) — so a payload of just x/y/z leaves yaw/pitch/etc. to
     * whatever earlier layer wrote, matching the export side's partial semantics.
     *
     * @return total camera keyframes written across all channels
     */
    private static int fillCameraClip(KeyframeClip clip, JsonObject channels)
    {
        int total = 0;

        for (String id : CAMERA_CHANNELS)
        {
            JsonElement element = channels.get(id);

            if (element == null || !element.isJsonArray())
            {
                continue;
            }

            KeyframeChannel<Double> channel = channelFor(clip, id);

            if (channel == null)
            {
                continue;
            }

            total += fillChannel(channel, element.getAsJsonArray());
        }

        return total;
    }

    /** The KeyframeClip channel with the given payload id. Explicit switch (not reflection) — the
     *  eight channels are a fixed, tiny set and a typo would silently drop a channel. */
    private static KeyframeChannel<Double> channelFor(KeyframeClip clip, String id)
    {
        return switch (id)
        {
            case "x" -> clip.x;
            case "y" -> clip.y;
            case "z" -> clip.z;
            case "yaw" -> clip.yaw;
            case "pitch" -> clip.pitch;
            case "roll" -> clip.roll;
            case "fov" -> clip.fov;
            case "distance" -> clip.distance;
            default -> null;
        };
    }

    /**
     * Insert one channel's keyframes. Mirror of {@code ActorImporter.applyChannel}, minus the
     * removeAll (a fresh clip's channels are already empty) and the factory-boxing (a KeyframeClip
     * channel is always {@code KeyframeChannel<Double>}).
     *
     * @return keyframes written on this channel
     */
    private static int fillChannel(KeyframeChannel<Double> channel, JsonArray keyframes)
    {
        int written = 0;

        for (JsonElement element : keyframes)
        {
            if (!element.isJsonObject())
            {
                continue;
            }

            JsonObject kf = element.getAsJsonObject();

            float tick = (float) BridgeProtocol.getDouble(kf, "tick", 0D);
            double value = BridgeProtocol.getDouble(kf, "value", 0D);

            int index = channel.insert(tick, value);
            Keyframe<Double> keyframe = channel.get(index);

            if (keyframe == null)
            {
                /* shouldn't happen — insert() returns the index it just wrote. */
                continue;
            }

            keyframe.getInterpolation().setInterp(resolveInterp(BridgeProtocol.getFieldString(kf, "interp", "")));

            /* Bezier/hermite handles — public fields, defaulted to BBS' own 5/0/5/0 so a payload
             * without handles produces default curve shape, not a collapsed one (ActorImporter). */
            keyframe.lx = (float) BridgeProtocol.getDouble(kf, "lx", DEFAULT_HANDLE_X);
            keyframe.ly = (float) BridgeProtocol.getDouble(kf, "ly", DEFAULT_HANDLE_Y);
            keyframe.rx = (float) BridgeProtocol.getDouble(kf, "rx", DEFAULT_HANDLE_X);
            keyframe.ry = (float) BridgeProtocol.getDouble(kf, "ry", DEFAULT_HANDLE_Y);

            written += 1;
        }

        return written;
    }

    /**
     * The [minTick, maxTick] spanned by all camera channels in the payload. Used for the clip's
     * tick/duration so the clip is "inside" every tick it holds keyframes for (Clips.getClips uses
     * {@code isInside} — a clip whose duration is too short would stop evaluating before its last
     * keyframe). Defaults to [0, 0] when somehow no numeric tick is present (caller has already
     * confirmed at least one channel array exists).
     */
    private static int[] channelTickRange(JsonObject channels)
    {
        boolean seen = false;
        double min = 0;
        double max = 0;

        for (String id : CAMERA_CHANNELS)
        {
            JsonElement element = channels.get(id);

            if (element == null || !element.isJsonArray())
            {
                continue;
            }

            for (JsonElement kfElement : element.getAsJsonArray())
            {
                if (!kfElement.isJsonObject())
                {
                    continue;
                }

                double tick = BridgeProtocol.getDouble(kfElement.getAsJsonObject(), "tick", 0D);

                if (!seen)
                {
                    min = tick;
                    max = tick;
                    seen = true;
                }
                else
                {
                    min = Math.min(min, tick);
                    max = Math.max(max, tick);
                }
            }
        }

        return new int[] {(int) Math.floor(min), (int) Math.ceil(max)};
    }

    /* ================================================================= *
     *  Tracker (POV hand/HUD)                                            *
     * ================================================================= */

    /**
     * Write ONE {@link TrackerClip} for a POV section (plan 6.2 / 5.4). Without such a clip whose
     * {@code show_hand}/{@code show_hud} are on, BBS renders no first-person hand and no HUD.
     *
     * <p>The tracker is a MODIFIER clip on its own layer — it never sources the camera position, it
     * only flags hand/HUD visibility and (via its {@code selector}) which replay is the POV actor.
     * {@code tickStart}/{@code tickEnd} default to the authored camera range so the hand/HUD show for
     * the whole fahrt unless the add-on narrows it.</p>
     *
     * @return 1 if a tracker clip was written, 0 otherwise
     */
    private static int writeTrackerClip(Clips camera, JsonObject tracker)
    {
        int tickStart = (int) BridgeProtocol.getDouble(tracker, "tickStart", 0D);
        int tickEnd = (int) BridgeProtocol.getDouble(tracker, "tickEnd", 0D);
        int duration = Math.max(1, tickEnd - tickStart);

        TrackerClip clip = new TrackerClip();

        clip.tick.set(Math.max(0, tickStart));
        clip.duration.set(duration);
        clip.showHand.set(getBoolean(tracker, "showHand", false));
        clip.showHud.set(getBoolean(tracker, "showHud", false));
        /* selector is the replay INDEX (EntityClip.selector, -1 = none) — the POV actor. The add-on
         * resolves it from the replay's index in the film, same index space FilmCameraExporter's
         * OfflineEntityResolver uses. */
        clip.selector.set((int) BridgeProtocol.getDouble(tracker, "selector", -1D));

        clip.layer.set(camera.findFreeLayer(clip));
        camera.addClip(clip);

        return 1;
    }

    /* ================================================================= *
     *  Hand-over to BBS: undo, UI refresh, persistence                  *
     * ================================================================= */

    /**
     * Tell BBS the film changed, in the same order BBS' own writers and {@link
     * com.blenderbridge.actors.ActorImporter} use: submit the undo entry the value-tree
     * notifications cached (Clips.addClip/remove fire pre/postNotify), rebuild the camera timeline
     * UI, then persist. {@link #refreshCameraTimeline(UIFilmPanel)} is guarded the same defensive way
     * ActorImporter's refreshSheets is.
     */
    private static void handOverToBBS(UIFilmPanel panel)
    {
        if (panel.getUndoHandler() != null)
        {
            panel.getUndoHandler().submitUndo();
        }

        refreshCameraTimeline(panel);

        /* Guarded internally: a no-op while the panel is mid-load or its editor is disabled. */
        panel.save();
    }

    /**
     * Rebuild the camera timeline UI so the new/removed clips show up. Uses the panel's camera
     * editor when present; a null editor (UI never opened, or a playing film) just means there is
     * nothing on screen to refresh and selecting the film later rebuilds it from the live clips.
     *
     * <p>Reflection-free, defensive: the camera editor field/getter surface differs across UIFilmPanel
     * builds, so this only calls what is guaranteed present ({@code save()} already persists the
     * clips; the timeline re-reads {@code film.camera} on its next draw). We deliberately do NOT poke
     * internal editor widgets here — unlike ActorImporter's keyframe sheets (which hold channel
     * references and stale selection indices), the camera timeline reads the clip list fresh each
     * frame, so no stale-index hazard exists to pre-empt.</p>
     */
    private static void refreshCameraTimeline(UIFilmPanel panel)
    {
        /* No-op by design (see method-doc): save() + the timeline's per-frame re-read of film.camera
         * are sufficient. Kept as a named seam so a future build that DOES need an explicit camera-
         * editor rebuild has the one place to add it, mirroring ActorImporter.refreshSheets. */
    }

    /* ================================================================= *
     *  Film resolution — deliberately duplicated (house convention)     *
     * ================================================================= */

    /** Editor film first, then any playing film — same precedence as {@link FilmCameraExporter}/
     *  {@code ActorImporter}/{@code FilmBackup}. Duplicated on purpose so a change to one is a
     *  conscious change, not a silent shared-code ripple. */
    private static FilmRef resolveOpenFilm()
    {
        UIFilmPanel editorPanel = getEditorPanel();

        if (editorPanel != null)
        {
            return new FilmRef(editorPanel.getData(), editorPanel, "editor");
        }

        Film playingFilm = getPlayingFilm();

        if (playingFilm != null)
        {
            return new FilmRef(playingFilm, null, "playing");
        }

        return null;
    }

    /** peekDashboard() — NOT getDashboard(): the latter builds a dashboard as a side effect. */
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
     *  Interp resolution — mirror of ActorImporter.resolveInterp        *
     * ================================================================= */

    /** Built-ins first, then the user's custom interpolations, then linear — never {@code
     *  Interpolations.get} (which skips the custom registry). Same as ActorImporter. */
    private static IInterp resolveInterp(String key)
    {
        if (key.isEmpty())
        {
            return Interpolations.LINEAR;
        }

        IInterp interp = Interpolations.MAP.get(key);

        if (interp != null)
        {
            return interp;
        }

        IInterp custom = CustomInterpolationManager.INSTANCE.get(key);

        return custom == null ? Interpolations.LINEAR : custom;
    }

    /* ================================================================= *
     *  Small JSON helpers (no getBoolean on BridgeProtocol)             *
     * ================================================================= */

    private static boolean hasAnyCameraChannel(JsonObject channels)
    {
        for (String id : CAMERA_CHANNELS)
        {
            JsonElement element = channels.get(id);

            if (element != null && element.isJsonArray() && !element.getAsJsonArray().isEmpty())
            {
                return true;
            }
        }

        return false;
    }

    /** Defensive boolean read (BridgeProtocol has no getBoolean). Missing/garbled = fallback. */
    private static boolean getBoolean(JsonObject fields, String key, boolean fallback)
    {
        if (fields == null || !fields.has(key))
        {
            return fallback;
        }

        try
        {
            JsonElement element = fields.get(key);

            return element.isJsonPrimitive() ? element.getAsBoolean() : fallback;
        }
        catch (Exception e)
        {
            return fallback;
        }
    }

    /** The nested object at {@code key}, or null when absent/not-an-object. */
    private static JsonObject optObject(JsonObject fields, String key)
    {
        JsonElement element = fields.get(key);

        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    /* ================================================================= *
     *  Payload skeleton + wire                                          *
     * ================================================================= */

    /** Failure payload — every success key present, so the python side never branches on presence. */
    private static JsonObject failure(String error)
    {
        JsonObject out = new JsonObject();

        out.addProperty("ok", false);
        out.addProperty("film", "");
        out.addProperty("source", "");
        out.addProperty("keysWritten", 0);
        out.addProperty("cameraClipsRemoved", 0);
        out.addProperty("trackerClipsWritten", 0);
        out.addProperty("replaceAll", false);
        out.addProperty("backup", "");
        out.addProperty("error", error == null ? "" : error);

        return out;
    }

    private static void sendResult(JsonObject payload)
    {
        long seq = com.blenderbridge.BlenderBridgeClient.STATE.nextOutboundSeq();

        /* Raw string type, no BridgeProtocol.TYPE_* constant — see class-doc "MESSAGE WIRING". */
        JsonObject envelope = BridgeProtocol.envelope("CAMERA_CLIP_WRITE_RESULT", "cameraClip", seq, BridgeProtocol.ORIGIN_BBS);

        envelope.add(BridgeProtocol.KEY_FIELDS, payload);

        com.blenderbridge.BlenderBridgeClient.SERVER.send(envelope);
    }

    /** A resolved film plus the editor panel (null for a playing film) and which lookup produced it. */
    private static final class FilmRef
    {
        private final Film film;
        private final UIFilmPanel panel;
        private final String source;

        private FilmRef(Film film, UIFilmPanel panel, String source)
        {
            this.film = film;
            this.panel = panel;
            this.source = source;
        }
    }
}
