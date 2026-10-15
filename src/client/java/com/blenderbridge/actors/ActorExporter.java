// Coded by Afrobama/Claude — Blender<->BBS live-sync bridge (Phase 3 actor export).
package com.blenderbridge.actors;

import com.blenderbridge.BridgeProtocol;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.Recorder;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.morphing.Morph;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.utils.interps.Interpolation;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * ActorExporter — serialises the currently open BBS film's replays (= actor performances) into the
 * JSON payload of an ACTORS_EXPORT_RESULT message.
 *
 * <p>ONE-WAY (Phase 3): BBS -&gt; Blender. Nothing here writes into BBS state; every call is a pure
 * read of the live film.</p>
 *
 * <p>WHICH FILM: BBS has two independent notions of "open" and they do NOT overlap:</p>
 * <ol>
 *   <li>the film loaded in the EDITOR — {@code com.blenderbridge.BBSCompat.peekDashboard().getPanel(UIFilmPanel.class).getData()};</li>
 *   <li>the films currently PLAYING in-world — {@code BBSModClient.getFilms().getControllers()},
 *       each {@link BaseFilmController#film}.</li>
 * </ol>
 * The editor wins (that is the film a user is authoring and expects to see in Blender); a playing
 * film is the fallback. The reply's {@code source} field says which one answered. Note that
 * {@code BBSModClient.getDashboard()} CONSTRUCTS a dashboard when none exists — a read-only export
 * must never do that, so this class uses {@code peekDashboard()}, which returns null instead.
 *
 * <p>COORDINATES ARE RAW. Values are exported exactly as BBS stores them; the Blender add-on applies
 * the axis/rotation conversion (see {@code COORDINATE_CONTRACT.md}). Converting on both sides is how
 * you get a double-transform bug, so this side deliberately does nothing.</p>
 *
 * <p>THREADING: MAIN THREAD ONLY — it walks live film state, and is invoked from the client-tick
 * dispatch in {@code BlenderBridgeClient}.</p>
 */
public final class ActorExporter
{
    /** Interp key reported when a keyframe has no resolvable interpolation. Matches BBS' own fallback. */
    private static final String DEFAULT_INTERP = "linear";

    private ActorExporter()
    {
        /* static-only helper */
    }

    /**
     * Read the open film and serialise its replays.
     *
     * <p>MAIN THREAD ONLY. Never throws — "no film open" is a normal, expected answer and comes back
     * as {@code ok:false} with an empty {@code replays} array, not as an exception.</p>
     *
     * @return the ACTORS_EXPORT_RESULT payload; never null, every contract key always present
     */
    public static JsonObject exportActors()
    {
        FilmRef ref = resolveOpenFilm();

        if (ref == null)
        {
            return failure("no film open");
        }

        JsonObject out = success(ref);
        out.addProperty("project", ActorProject.identity(ref.film));
        out.addProperty("engine", ActorProject.ENGINE);
        JsonArray replays = new JsonArray();
        List<Replay> list = ref.film.replays.getList();

        for (Replay replay : list)
        {
            if (replay == null)
            {
                continue;
            }

            replays.add(exportReplay(replay));
        }

        out.add(ActorProtocol.REPLAYS, replays);

        return out;
    }

    /* ================================================================= *
     *  Film resolution                                                  *
     * ================================================================= */

    /**
     * Find the film to export: editor first, then any playing film.
     *
     * @return the film + where it came from, or null when nothing is open
     */
    private static FilmRef resolveOpenFilm()
    {
        Film editorFilm = getEditorFilm();

        if (editorFilm != null)
        {
            return new FilmRef(editorFilm, ActorProtocol.SOURCE_EDITOR);
        }

        Film playingFilm = getPlayingFilm();

        if (playingFilm != null)
        {
            return new FilmRef(playingFilm, ActorProtocol.SOURCE_PLAYING);
        }

        return null;
    }

    /**
     * The film loaded in the film editor panel, or null.
     *
     * <p>peekDashboard() — NOT getDashboard(): the latter builds a dashboard as a side effect, which
     * an export must not do. Null dashboard simply means the UI was never opened this session.</p>
     */
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
     *  Replay / channel serialisation                                   *
     * ================================================================= */

    /** Serialise one replay (one actor performance) with its non-empty numeric channels. */
    private static JsonObject exportReplay(Replay replay)
    {
        JsonObject out = new JsonObject();
        Form form = replay.form.get();

        out.addProperty(ActorProtocol.REPLAY_ID, replay.getId());
        out.addProperty(ActorProtocol.REPLAY_UUID, replay.uuid.get());
        out.addProperty(ActorProtocol.REPLAY_NAME, replay.getName());
        out.addProperty(ActorProtocol.REPLAY_LABEL, replay.label.get());

        /* "form" is the human-readable name shown in BBS; "formType" is the registry key of the form
         * class (model/mob/item/...). Blender wants the first for naming, the second for branching. */
        out.addProperty(ActorProtocol.REPLAY_FORM, form == null ? "" : form.getDisplayName());
        out.addProperty(ActorProtocol.REPLAY_FORM_TYPE, form == null ? "" : form.getFormId());

        /* MEASURED 2026-07-16: getDisplayName() above returns the user's CUSTOM name once they
         * rename an actor, and only falls back to the model key when unnamed. Blender was resolving
         * the .bobj off it, so a renamed actor silently imported as an empty. Send the model key
         * itself — the only field that always identifies the geometry. */
        out.addProperty(ActorProtocol.REPLAY_FORM_MODEL,
            form instanceof ModelForm modelForm ? modelForm.model.get() : "");

        /* Task 4.1 (W7-B1): mob-typed forms carry their registry id ("minecraft:villager") so the
         * Blender side can branch a mob replay onto its ENTITY_EXPORT model cache (entity_import.py)
         * — the exact same reasoning as formModel above: 'form' is a display name, only the id
         * always identifies the geometry. RAW string key, no ActorProtocol constant: adding a
         * static field to this already-loaded class would break the body-only hotswap rule (same
         * convention as the raw-string message types, see FilmCameraExporter's class doc). */
        out.addProperty("formMob",
            form instanceof mchorse.bbs_mod.forms.forms.MobForm mobForm ? mobForm.mobID.get() : "");

        /* Group replays are folders in BBS' replay list: no performance of their own, but children
         * point at them via "group" (the parent's uuid), so Blender can rebuild the hierarchy. */
        out.addProperty(ActorProtocol.REPLAY_IS_GROUP, replay.isGroup.get());
        out.addProperty(ActorProtocol.REPLAY_GROUP, replay.group.get());
        out.addProperty(ActorProtocol.REPLAY_ENABLED, replay.enabled.get());
        out.addProperty(ActorProtocol.REPLAY_FIRST_PERSON, replay.fp.get());
        out.addProperty("category", replay.category.get());

        JsonObject channels = new JsonObject();
        JsonArray skipped = new JsonArray();
        List<KeyframeChannel<?>> list = replay.keyframes.getChannels();

        for (KeyframeChannel<?> channel : list)
        {
            if (channel == null || channel.isEmpty())
            {
                /* Empty channel: every replay carries all ~50 channels, most unused. Sending them
                 * would multiply the payload for nothing. */
                continue;
            }

            JsonArray keyframes = exportChannel(channel);

            if (keyframes == null)
            {
                /* Non-numeric channel (ItemStack / MountLink) — see exportChannel. Named here so the
                 * Blender side can see that data EXISTS but was not sent, rather than assume empty. */
                skipped.add(channel.getId());

                continue;
            }

            channels.add(channel.getId(), keyframes);
        }

        out.add(ActorProtocol.REPLAY_CHANNELS, channels);
        out.add(ActorProtocol.REPLAY_SKIPPED_CHANNELS, skipped);

        /* Task 5.2 (W-HUD, HB-3): the 15 ItemStack channels (equipment + hotbar) that exportChannel
         * returns null for — and therefore still names in `skipped` above — get their own transport
         * as item registry ids under `strings`. Delegated wholesale to StringChannels (a NEW lazy
         * class): ActorExporter is already loaded, so it can only take this one body call, no new
         * field/method (package class-doc deploy rules). Only added when non-empty, so a replay
         * without items keeps the byte-identical old payload shape. `skipped` is intentionally left
         * unchanged — `strings` is the ADDITIONAL channel-of-record, not a replacement, so an old
         * add-on that ignores `strings` still sees the data reported as skipped. */
        JsonObject strings = StringChannels.serialize(replay);

        if (strings.size() > 0)
        {
            out.add(StringChannels.STRINGS, strings);
        }

        /* Material-eigenschaften (color/paint/glow) aus replay.properties — analog zu `strings`
         * ein ADDITIVES feld, nur wenn nicht-leer (replay ohne color = byte-identischer payload).
         * Delegiert an die lazy ActorPropertiesExporter (deploy-regel: ActorExporter ist geladen). */
        JsonObject properties = ActorPropertiesExporter.serialize(replay);

        if (properties.size() > 0)
        {
            out.add(ActorProtocol.REPLAY_PROPERTIES, properties);
        }

        return out;
    }

    /**
     * Serialise one keyframe channel.
     *
     * @return the keyframe array, or null when the channel is not numeric (its value type has no
     *         meaningful float-curve representation — {@code item_main_hand} and friends hold
     *         {@code ItemStack}s, {@code ridden} holds a {@code MountLink}). Blender animates
     *         numbers; those channels need their own message type, not a fake numeric curve.
     */
    private static JsonArray exportChannel(KeyframeChannel<?> channel)
    {
        JsonArray out = new JsonArray();

        for (Keyframe<?> keyframe : channel.getKeyframes())
        {
            if (keyframe == null)
            {
                continue;
            }

            Object value = keyframe.getValue();

            if (!(value instanceof Number))
            {
                return null;
            }

            JsonObject entry = new JsonObject();

            /* Tick and handles stay float (not cast to double): Gson renders a Float via its own
             * toString, so 0.1F stays "0.1" instead of turning into 0.10000000149011612. */
            entry.addProperty(ActorProtocol.KF_TICK, keyframe.getTick());
            entry.addProperty(ActorProtocol.KF_VALUE, (Number) value);
            entry.addProperty(ActorProtocol.KF_INTERP, interpKey(keyframe));

            /* Bezier/hermite handles — BBS' curve shape is meaningless without them. */
            entry.addProperty(ActorProtocol.KF_LX, keyframe.lx);
            entry.addProperty(ActorProtocol.KF_LY, keyframe.ly);
            entry.addProperty(ActorProtocol.KF_RX, keyframe.rx);
            entry.addProperty(ActorProtocol.KF_RY, keyframe.ry);

            out.add(entry);
        }

        return out;
    }

    /* ================================================================= *
     *  Disposable live-recording frame payloads                          *
     * ================================================================= */

    static JsonObject recordingNumericValues(Recorder recorder)
    {
        JsonObject values = new JsonObject();

        for (KeyframeChannel<?> channel : recorder.keyframes.getChannels())
        {
            Keyframe<?> keyframe = latest(channel);

            if (keyframe != null && keyframe.getValue() instanceof Number number)
            {
                values.addProperty(channel.getId(), number);
            }
        }

        return values;
    }

    static JsonObject recordingStringValues(Recorder recorder)
    {
        JsonObject values = new JsonObject();

        for (KeyframeChannel<?> channel : recorder.keyframes.getChannels())
        {
            Keyframe<?> keyframe = latest(channel);

            if (keyframe != null && keyframe.getValue() instanceof ItemStack stack)
            {
                values.addProperty(channel.getId(), StringChannels.itemId(stack));
            }
        }

        return values;
    }

    static JsonObject recordingCurrentForm()
    {
        JsonObject out = new JsonObject();
        Form form = null;

        if (Minecraft.getInstance().player != null)
        {
            Morph morph = Morph.getMorph(Minecraft.getInstance().player);
            form = morph == null ? null : morph.getForm();
        }

        out.addProperty("type", form == null ? "" : form.getFormId());
        out.addProperty("name", form == null ? "" : form.getDisplayName());
        out.addProperty("model", form instanceof ModelForm model ? model.model.get() : "");
        out.addProperty("mob", form instanceof MobForm mob ? mob.mobID.get() : "");

        return out;
    }

    static JsonObject recordingReplayPayload(String uuid)
    {
        JsonObject actors = exportActors();
        JsonElement element = actors.get(ActorProtocol.REPLAYS);

        if (element != null && element.isJsonArray())
        {
            for (JsonElement entry : element.getAsJsonArray())
            {
                if (entry.isJsonObject()
                    && uuid.equals(BridgeProtocol.getFieldString(
                        entry.getAsJsonObject(), ActorProtocol.REPLAY_UUID, "")))
                {
                    return entry.getAsJsonObject().deepCopy();
                }
            }
        }

        return new JsonObject();
    }

    private static Keyframe<?> latest(KeyframeChannel<?> channel)
    {
        if (channel == null || channel.isEmpty())
        {
            return null;
        }

        List<? extends Keyframe<?>> keyframes = channel.getKeyframes();

        return keyframes.isEmpty() ? null : keyframes.get(keyframes.size() - 1);
    }

    /**
     * The interpolation key of a keyframe ("linear", "constant", "hermite", ...).
     *
     * <p>Interpolation.getKey() dereferences its inner IInterp, which BBS itself treats as nullable
     * (see Interpolation.interpolate) — hence the guard.</p>
     */
    private static String interpKey(Keyframe<?> keyframe)
    {
        Interpolation interp = keyframe.getInterpolation();

        if (interp == null || interp.getInterp() == null)
        {
            return DEFAULT_INTERP;
        }

        String key = interp.getKey();

        return key == null || key.isEmpty() ? DEFAULT_INTERP : key;
    }

    /* ================================================================= *
     *  Payload skeletons — every key always present (python reads blind) *
     * ================================================================= */

    /** Failure payload. {@code replays} is an empty array, never null. */
    static JsonObject failure(String error)
    {
        JsonObject out = new JsonObject();

        out.addProperty(ActorProtocol.OK, false);
        out.addProperty(ActorProtocol.FILM, "");
        out.addProperty(ActorProtocol.SOURCE, "");
        out.add(ActorProtocol.REPLAYS, new JsonArray());
        out.addProperty(ActorProtocol.ERROR, error == null ? "" : error);

        return out;
    }

    /** Success skeleton; the caller fills in {@code replays}. */
    private static JsonObject success(FilmRef ref)
    {
        JsonObject out = new JsonObject();

        out.addProperty(ActorProtocol.OK, true);
        out.addProperty(ActorProtocol.FILM, ref.film.getId() == null ? "" : ref.film.getId());
        out.addProperty(ActorProtocol.SOURCE, ref.source);
        out.add(ActorProtocol.REPLAYS, new JsonArray());
        out.addProperty(ActorProtocol.ERROR, "");

        return out;
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
