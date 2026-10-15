// Coded by Afrobama/Claude — Blender<->BBS live-sync bridge (Phase 3 actor import).
package com.blenderbridge.actors;

import com.blenderbridge.BridgeProtocol;
import com.blenderbridge.BridgeState;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.utils.interps.CustomInterpolationManager;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

import java.util.List;
import java.util.Map;

/**
 * ActorImporter — writes Blender-authored keyframe curves back into the open BBS film's replays.
 *
 * <p>The mirror image of {@link ActorExporter}: it consumes the SAME payload shape the exporter
 * produces (see {@link ActorProtocol}), so a Blender round-trip is export -&gt; edit -&gt; import
 * without any schema translation. Only {@code replays[].uuid} and {@code replays[].channels} are
 * read; the other exported replay fields (form, label, group, ...) are metadata for Blender and are
 * deliberately NOT written back — this bridge syncs ANIMATION, not film structure.</p>
 *
 * <p>MATCHING IS BY UUID, never by index. {@code Replay.uuid} is a stable per-replay id; the list
 * index shifts as soon as the user adds/reorders/deletes a replay in BBS, which would silently write
 * a Blender curve onto the wrong actor.</p>
 *
 * <p>CHANNEL SEMANTICS — FULL REPLACEMENT: a channel named in {@code channels} is cleared and
 * rebuilt from the payload (Blender owns that curve; its F-curve IS the truth). A channel NOT named
 * in the payload is left completely untouched. So a partial payload ("just x/y/z") edits exactly
 * those curves and cannot damage the rest of the performance.</p>
 *
 * <p>NUMERIC ONLY. Channels whose factory is not double/float/integer ({@code item_main_hand} holds
 * ItemStacks, {@code ridden} a MountLink) are skipped — same boundary the exporter draws when it
 * reports them in {@code skippedChannels}. Blender animates numbers.</p>
 *
 * <p>COORDINATES ARE RAW, exactly as in the exporter: values are written into BBS verbatim and the
 * Blender add-on owns the axis/rotation conversion (COORDINATE_CONTRACT.md). Converting on both
 * sides is how you get a double-transform bug.</p>
 *
 * <p>THREADING: MAIN THREAD ONLY — it mutates live film state and rebuilds UI elements. It is
 * invoked from the client-tick dispatch in {@code BlenderBridgeClient}.</p>
 */
public final class ActorImporter
{
    /** Indices into the {@code counts} accumulator carried through the apply lambda. */
    private static final int APPLIED = 0;
    private static final int SKIPPED = 1;

    /** BBS' own keyframe handle defaults (see {@code Keyframe.lx}/{@code rx}). */
    private static final float DEFAULT_HANDLE_X = 5F;
    private static final float DEFAULT_HANDLE_Y = 0F;

    private ActorImporter()
    {
        /* static-only helper */
    }

    /**
     * Apply an inbound Blender actor payload onto the open film.
     *
     * <p>MAIN THREAD ONLY. Never throws — "no film open" is a normal, expected answer and comes back
     * as {@code ok:false}, not as an exception.</p>
     *
     * <p>ECHO GUARD: the entire mutation runs inside {@link BridgeState#withRemoteApply(Runnable)},
     * so {@link BridgeState#isApplyingRemote()} is raised before the first BBS write and lowered in a
     * {@code finally} even on exception. Without it, BBS-side change-detection would see the values
     * we just received from Blender as a local change and push them straight back — the feedback loop
     * that made the camera jitter for 30-60s before the baseline-adoption fix in
     * {@code CameraBridge.applyCameraGuarded}.</p>
     *
     * @param payload the ACTORS_IMPORT {@code fields} object: {@code {"replays":[...]}}
     * @param state   the shared bridge state, providing the echo guard. Mandatory — a null state is
     *                rejected rather than silently applied unguarded.
     * @return the ACTORS_IMPORT_RESULT payload; never null, every contract key always present
     */
    public static JsonObject applyActors(JsonObject payload, BridgeState state)
    {
        if (payload == null)
        {
            return failure("no payload");
        }

        if (state == null)
        {
            /* The echo guard is a class contract, not an optimisation: applying without it would
             * bounce every imported keyframe back to Blender. Refuse rather than loop. */
            return failure("no bridge state");
        }

        JsonElement element = payload.get(ActorProtocol.REPLAYS);

        if (element == null || !element.isJsonArray())
        {
            return failure("no replays in payload");
        }

        /* Editor film first, playing film as fallback — the same precedence ActorExporter uses, so
         * import and export always address the same film. The PANEL (not just the Film) is resolved
         * here because the editor path needs it for the undo/refresh/save hand-off below; a playing
         * film has no editor UI and gets none of that. */
        UIFilmPanel panel = getEditorPanel();
        Film film = panel != null ? panel.getData() : getPlayingFilm();

        if (film == null)
        {
            return failure("no film open");
        }

        /* Task 1.0 IDENTITY GUARD — the datenverlust seal. The payload carries the id of the film
         * Blender THINKS it is editing (ActorProtocol.FILM, stamped by the add-on from the film it
         * bound to). If BBS has since switched to a DIFFERENT film, applying here would write
         * Blender's curves into the wrong film — the user loses work in a film they weren't looking
         * at. Reject BEFORE any mutation. An empty/absent expected id (pre-1.0 add-on, manual/EXEC
         * caller) is deliberately NOT a mismatch (backward-compatible), and an unsaved/empty current
         * id is not one either — see FilmIdentity.isForeignFilm. FilmIdentity is a NEW lazy class, so
         * this is a pure body-swap on this already-loaded importer (deploy rules). */
        String expectedFilm = BridgeProtocol.getFieldString(payload, ActorProtocol.FILM, "");

        if (com.blenderbridge.FilmIdentity.isForeignFilm(expectedFilm))
        {
            return filmMismatch(expectedFilm, film.getId() == null ? "" : film.getId());
        }

        JsonArray replays = element.getAsJsonArray();
        int[] counts = new int[2];
        boolean conditional = ActorChannelPatch.isConditional(payload);

        state.withRemoteApply(() -> ActorImporter.applyGuarded(
            replays, film, panel, counts, conditional));

        return success(counts);
    }

    /* ================================================================= *
     *  Apply — runs inside the applyingRemote guard                     *
     * ================================================================= */

    /**
     * The actual mutation, run inside the echo guard by {@link #applyActors(JsonObject, BridgeState)}.
     * Writes every matched replay, then hands the change over to BBS (undo, UI, disk) exactly the way
     * BBS' own bulk keyframe writers do — see {@link #handOverToBBS(UIFilmPanel)}.
     */
    /** Apply the existing native channel decoders to a detached film before durable commit. */
    static JsonObject applyToFilm(JsonObject payload, Film film, BridgeState state)
    {
        int[] counts = new int[2];
        boolean conditional = ActorChannelPatch.isConditional(payload);
        state.withRemoteApply(() -> applyGuarded(
            payload.getAsJsonArray("replays"), film, null, counts, conditional));
        return success(counts);
    }

    private static void applyGuarded(JsonArray replays, Film film, UIFilmPanel panel, int[] counts,
        boolean conditional)
    {
        for (JsonElement element : replays)
        {
            if (!element.isJsonObject())
            {
                counts[SKIPPED] += 1;

                continue;
            }

            if (applyReplay(element.getAsJsonObject(), film, conditional))
            {
                counts[APPLIED] += 1;
            }
            else
            {
                counts[SKIPPED] += 1;
            }
        }

        if (counts[APPLIED] == 0 || panel == null)
        {
            /* Nothing landed, or the target is a playing film with no editor attached: no undo entry
             * to submit, no sheets to rebuild, nothing to save. */
            return;
        }

        handOverToBBS(panel);
    }

    /**
     * Write one replay's channels. Returns false when the payload entry could not be applied (no
     * uuid, unknown uuid, or no {@code channels} object) — the caller counts that as skipped.
     */
    private static boolean applyReplay(JsonObject replayObject, Film film, boolean conditional)
    {
        String uuid = BridgeProtocol.getFieldString(replayObject, ActorProtocol.REPLAY_UUID, "");

        if (uuid.isEmpty())
        {
            return false;
        }

        Replay replay = findReplay(film, uuid);

        if (replay == null)
        {
            /* The user deleted/never had this actor in BBS. Reported as skipped rather than created:
             * this bridge syncs animation onto existing actors, it does not author film structure. */
            return false;
        }

        boolean touched = false;

        JsonElement element = replayObject.get(ActorProtocol.REPLAY_CHANNELS);

        if (element != null && element.isJsonObject())
        {
            JsonObject channels = element.getAsJsonObject();

            for (Map.Entry<String, JsonElement> entry : channels.entrySet())
            {
                if (!entry.getValue().isJsonArray())
                {
                    continue;
                }

                KeyframeChannel<?> channel = findChannel(replay, entry.getKey());

                if (channel == null || !KeyframeFactories.isNumeric(channel.getFactory()))
                {
                    /* Unknown channel id, or one that holds ItemStacks/MountLinks — see class-doc.
                     * Silently ignored: a replay carries ~50 channels and the reply's contract is a
                     * replay-level count, not a per-channel report. */
                    continue;
                }

                applyChannel(channel, entry.getValue().getAsJsonArray());
            }

            touched = true;
        }

        /* Task 5.2 (W-HUD, HB-3): the reverse of ActorExporter's `strings` — item-id keyframes back
         * onto the ItemStack channels. Delegated to StringChannels (deploy rules: this is a loaded
         * class, body call only). A payload may carry ONLY `strings` (a Blender-side item edit with
         * no curve edit), so applying it counts the replay as touched even without a `channels`
         * object — that is why the old `return false` on a missing `channels` object is gone. */
        touched |= StringChannels.apply(replayObject, replay) > 0;

        /* Knochen-Keys aus Blender -> pose.bones.<knochen> (PoseBoneImporter, 2026-09-13): die
         * Gegenrichtung zu PerBoneExporter, damit "in Blender animieren" auch fuer Glieder gilt. */
        JsonElement bones = replayObject.get("bones");

        if (bones != null && bones.isJsonObject())
        {
            touched |= PoseBoneImporter.apply(replay, bones.getAsJsonObject()) > 0;
        }

        touched |= RigPropertyCurves.apply(replayObject, replay) > 0;

        if (conditional)
        {
            touched |= VisibilityPatch.apply(replayObject, replay) > 0;
            touched |= ShapeGridPatch.apply(replayObject, replay) > 0;
            touched |= ShapeKeysPatch.apply(replayObject, replay) > 0;
            touched |= ShapeTimelinePatch.apply(replayObject, replay) > 0;
        }

        /* No protected section applied means nothing to write for this replay. */
        return touched;
    }

    /**
     * Rebuild one channel from its payload array.
     *
     * <p>Raw {@link KeyframeChannel} on purpose: the channel's value type is only known at runtime
     * from its factory, and BBS itself reaches for the raw type in the same situation (see
     * {@code UIReplaysEditor.updateChannelsList}). {@link #box(IKeyframeFactory, double)} is what
     * keeps the unchecked insert honest — it returns null for anything the factory cannot hold.</p>
     *
     * <p>UNDO: {@code removeAll()} and every {@code insert()} fire {@code preNotify()}, which bubbles
     * up the value tree to the Film root where {@code UIFilmPanel}'s undo handler caches the
     * channel's pre-state — but only the FIRST time it sees that value. So the whole import collapses
     * into one before/after pair per channel, which is exactly the undo entry a user expects.</p>
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void applyChannel(KeyframeChannel channel, JsonArray keyframes)
    {
        IKeyframeFactory factory = channel.getFactory();

        /* Full replacement: Blender's F-curve IS this channel now. Clearing first is what makes a
         * keyframe DELETED in Blender actually disappear in BBS — a merge-only import would leave
         * orphaned keyframes behind and the two sides would drift apart forever. */
        channel.removeAll();

        for (JsonElement element : keyframes)
        {
            if (!element.isJsonObject())
            {
                continue;
            }

            JsonObject keyframeObject = element.getAsJsonObject();
            Object value = box(factory, BridgeProtocol.getDouble(keyframeObject, ActorProtocol.KF_VALUE, 0D));

            if (value == null)
            {
                continue;
            }

            float tick = (float) BridgeProtocol.getDouble(keyframeObject, ActorProtocol.KF_TICK, 0D);
            int index = channel.insert(tick, value);
            Keyframe keyframe = channel.get(index);

            if (keyframe == null)
            {
                /* shouldn't happen — insert() returns the index it just wrote. */
                continue;
            }

            keyframe.getInterpolation().setInterp(resolveInterp(BridgeProtocol.getFieldString(keyframeObject, ActorProtocol.KF_INTERP, "")));

            /* Bezier/hermite handles. Public fields on Keyframe (no setters), defaulted to BBS' own
             * 5/0/5/0 so a payload without handles produces BBS-default curve shape rather than a
             * collapsed one. */
            keyframe.lx = (float) BridgeProtocol.getDouble(keyframeObject, ActorProtocol.KF_LX, DEFAULT_HANDLE_X);
            keyframe.ly = (float) BridgeProtocol.getDouble(keyframeObject, ActorProtocol.KF_LY, DEFAULT_HANDLE_Y);
            keyframe.rx = (float) BridgeProtocol.getDouble(keyframeObject, ActorProtocol.KF_RX, DEFAULT_HANDLE_X);
            keyframe.ry = (float) BridgeProtocol.getDouble(keyframeObject, ActorProtocol.KF_RY, DEFAULT_HANDLE_Y);
        }
    }

    /* ================================================================= *
     *  Hand-over to BBS: undo, UI refresh, persistence                  *
     * ================================================================= */

    /**
     * Tell BBS that the film changed, in the exact order BBS' own bulk keyframe writers use
     * ({@code UIReplaysEditor.toolbarAnimationToKeyframes}, and {@code BBSModClient} after a
     * recording):
     *
     * <ol>
     *   <li>{@code submitUndo()} — turns the pre-states cached by the value-tree notifications into
     *       ONE undo entry, so the user can Ctrl+Z the import as a single step. Without it the
     *       cached values stay pending and get folded into whatever the user edits next.</li>
     *   <li>{@code updateChannelsList()} — rebuilds the timeline sheets for the selected replay. The
     *       keyframe DATA renders live (a {@code UIKeyframeSheet} holds the channel by reference,
     *       and we mutate it in place), but each sheet's {@code KeyframeSelection} holds keyframe
     *       INDICES: after a rebuild those indices point at different keyframes, so a stale selection
     *       would highlight — and let the user drag — the wrong ones. This rebuild drops it.</li>
     *   <li>{@code save()} — Film has no dirty flag; persistence is an explicit repository write
     *       (BBS' periodic autosave only runs when {@code editorPeriodicSave} is on). BBS saves right
     *       after a recording, so an import saves too rather than leaving the change disk-less.</li>
     * </ol>
     *
     * <p>The actors themselves need no respawn: {@code BaseFilmController.update()} re-applies every
     * replay's channels onto its entity each client tick from the current cursor, so the new curve
     * shows up even while playback is paused. {@code createEntities()} is only for when the replay
     * LIST changes, which an import never does.</p>
     */
    private static void handOverToBBS(UIFilmPanel panel)
    {
        if (panel.getUndoHandler() != null)
        {
            panel.getUndoHandler().submitUndo();
        }

        refreshSheets(panel);

        /* Guarded internally: a no-op while the panel is mid-load or its editor is disabled. */
        panel.save();
    }

    /**
     * Rebuild the timeline sheets — but only when {@code updateChannelsList()} will actually rebuild
     * them.
     *
     * <p>TRAP: that method DETACHES the keyframe editor ({@code keyframeEditor.removeFromParent()})
     * as its very first act, and only re-attaches it at the end. Both of its early-outs — no replay
     * selected, and a non-group replay without a form — return AFTER the detach, leaving the user
     * staring at a blank timeline until they click a replay. BBS' own callers only ever hit it with a
     * replay selected (right after a recording), so the bug never shows there; an import can arrive at
     * any moment, including with nothing selected. Hence this mirrors its preconditions instead of
     * calling blind.</p>
     *
     * <p>Skipping is also simply correct: with no replay selected there are no sheets on screen to
     * refresh, and selecting one rebuilds them from the live channels anyway.</p>
     */
    private static void refreshSheets(UIFilmPanel panel)
    {
        if (panel.replayEditor == null)
        {
            return;
        }

        Replay selected = panel.replayEditor.getReplay();

        if (selected == null)
        {
            return;
        }

        if (!selected.isGroup.get() && selected.form.get() == null)
        {
            return;
        }

        panel.replayEditor.updateChannelsList();
    }

    /* ================================================================= *
     *  Lookups                                                          *
     * ================================================================= */

    /**
     * The film editor panel, or null when the UI was never opened this session / holds no film.
     *
     * <p>peekDashboard() — NOT getDashboard(): the latter CONSTRUCTS a dashboard as a side effect.
     * Same trap the exporter documents.</p>
     */
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

    /** The replay carrying {@code uuid}, or null. Identity, never index — see class-doc. */
    private static Replay findReplay(Film film, String uuid)
    {
        for (Replay replay : film.replays.getList())
        {
            if (replay != null && uuid.equals(replay.uuid.get()))
            {
                return replay;
            }
        }

        return null;
    }

    /**
     * The replay's keyframe channel with the given id, or null when the id is unknown.
     * {@code ReplayKeyframes} is a ValueGroup keyed by channel id, so this is a map hit — the same
     * lookup {@code UIReplaysEditor.updateChannelsList} uses to build the sheets.
     */
    private static KeyframeChannel<?> findChannel(Replay replay, String id)
    {
        BaseValue value = replay.keyframes.get(id);

        return value instanceof KeyframeChannel<?> channel ? channel : null;
    }

    /**
     * Box a JSON number into whatever the channel's factory holds.
     *
     * @return the boxed value, or null when the factory is not numeric (the caller has already
     *         filtered those, so null here is a belt-and-braces guard for the unchecked insert)
     */
    private static Object box(IKeyframeFactory<?> factory, double value)
    {
        if (factory == KeyframeFactories.DOUBLE)
        {
            return Double.valueOf(value);
        }

        if (factory == KeyframeFactories.FLOAT)
        {
            return Float.valueOf((float) value);
        }

        if (factory == KeyframeFactories.INTEGER)
        {
            return Integer.valueOf((int) Math.round(value));
        }

        return null;
    }

    /**
     * Resolve an interpolation key ("linear", "hermite", ...) to BBS' shared IInterp singleton.
     *
     * <p>Built-ins first, then the user's custom interpolations, then linear — mirroring
     * {@code Interpolation.fromData}. NOT {@code Interpolations.get(key)}: that one falls back to
     * linear WITHOUT consulting the custom-interpolation registry, so it would silently flatten a
     * curve that used a custom easing on the way out.</p>
     */
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
     *  Payload skeletons — every key always present (python reads blind) *
     * ================================================================= */

    /** Success payload. Counts are replay-level: matched-and-written vs. not applied. */
    private static JsonObject success(int[] counts)
    {
        JsonObject out = new JsonObject();

        out.addProperty(ActorProtocol.OK, true);
        out.addProperty(ActorProtocol.APPLIED, counts[APPLIED]);
        out.addProperty(ActorProtocol.SKIPPED, counts[SKIPPED]);
        out.addProperty(ActorProtocol.ERROR, "");

        return out;
    }

    /** Failure payload. Counts are 0, never absent. */
    static JsonObject failure(String error)
    {
        JsonObject out = new JsonObject();

        out.addProperty(ActorProtocol.OK, false);
        out.addProperty(ActorProtocol.APPLIED, 0);
        out.addProperty(ActorProtocol.SKIPPED, 0);
        out.addProperty(ActorProtocol.ERROR, error == null ? "" : error);

        return out;
    }

    /**
     * Task 1.0 film-mismatch rejection (the datenverlust seal). A normal {@code ok:false} with a
     * readable reason AND the two ids echoed as their own fields, so the add-on can react precisely
     * (auto-pause its sync + name the film it drifted onto in the panel warning) instead of guessing
     * from a free-text error string. {@code applied} is 0 — nothing was written, by construction the
     * guard runs before any mutation.
     *
     * @param expected the film id the write was tagged for (what Blender thought it was editing)
     * @param open     the id of the film actually open right now (where the write would have landed)
     */
    private static JsonObject filmMismatch(String expected, String open)
    {
        JsonObject out = failure("film mismatch: write was for '" + expected + "', open film is '" + open + "' — sync paused to protect your work");

        /* Machine-readable so the add-on need not parse the error string. */
        out.addProperty(ActorProtocol.FILM, open);
        out.addProperty("expectedFilm", expected);
        out.addProperty("filmMismatch", true);

        return out;
    }
}
