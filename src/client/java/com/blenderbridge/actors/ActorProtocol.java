// Coded by Afrobama/Claude — Blender<->BBS live-sync bridge (Phase 3 actor export).
package com.blenderbridge.actors;

/**
 * ActorProtocol — the field keys of the ACTORS_EXPORT_RESULT payload.
 *
 * <p>The message TYPE constants live in {@code BridgeProtocol} next to the other message types;
 * these are the payload-internal keys, kept beside the exporter that writes them so the two cannot
 * drift apart.</p>
 *
 * <p>PAYLOAD SHAPE (the {@code fields} object of an ACTORS_EXPORT_RESULT):</p>
 * <pre>
 * {
 *   "ok":     true,
 *   "film":   "&lt;film id&gt;",
 *   "source": "editor" | "playing",
 *   "replays": [
 *     {
 *       "id":       "&lt;replay id, e.g. \"0\"&gt;",
 *       "uuid":     "&lt;stable uuid&gt;",
 *       "name":     "&lt;label, else form display name&gt;",
 *       "label":    "&lt;raw label, may be empty&gt;",
 *       "form":     "&lt;form display name&gt;",
 *       "formType": "&lt;form registry key: model/mob/item/...&gt;",
 *       "isGroup":  false,
 *       "group":    "&lt;parent group uuid, empty when top level&gt;",
 *       "enabled":  true,
 *       "fp":       false,
 *       "channels": {
 *         "x": [{"tick":0.0,"value":10.5,"interp":"hermite","lx":5.0,"ly":0.0,"rx":5.0,"ry":0.0}, ...],
 *         "y": [...], "pitch": [...]
 *       },
 *       "skippedChannels": ["item_main_hand", ...],
 *       "strings": {
 *         "item_main_hand": [[0.0,"minecraft:diamond_sword"], [40.0,"minecraft:air"]],
 *         "hotbar_slot_1":  [[0.0,"minecraft:cobblestone"]]
 *       }
 *     }
 *   ],
 *   "error": ""
 * }
 * </pre>
 *
 * <p>Empty channels are omitted entirely (a replay carries ~50 channels, most unused). Channels that
 * hold data but are NOT numeric curves are omitted from {@code channels} and named in
 * {@code skippedChannels}, so the reader can tell "no data" from "data we cannot express as a
 * float curve". On failure {@code ok} is false, {@code error} carries the reason, and
 * {@code replays} is {@code []} — never null, never absent.</p>
 *
 * <p>TASK 5.2: the 15 ItemStack channels (equipment + hotbar) that land in {@code skippedChannels}
 * ALSO travel — as item registry ids — under a {@code strings} object, added only when non-empty
 * (an item-less replay keeps the byte-identical old shape). The key and codec live in
 * {@code StringChannels} (a new class, deploy-window-free) rather than here, because this class is
 * already loaded and cannot take a new constant via hotswap. {@code skippedChannels} still lists
 * them: {@code strings} is the additional channel-of-record, not a replacement.</p>
 *
 * <p>VALUES ARE RAW BBS UNITS. Axis/rotation conversion is the Blender side's job
 * (COORDINATE_CONTRACT.md).</p>
 */
public final class ActorProtocol
{
    /* ---- Top-level payload keys ---- */

    /** True when a film was found and serialised. */
    public static final String OK = "ok";

    /** Id of the exported film; empty on failure. */
    public static final String FILM = "film";

    /** Which notion of "open" answered: {@link #SOURCE_EDITOR} or {@link #SOURCE_PLAYING}. */
    public static final String SOURCE = "source";

    /** Array of replay objects; {@code []} on failure, never null. */
    public static final String REPLAYS = "replays";

    /** Failure reason; empty when {@link #OK} is true. */
    public static final String ERROR = "error";

    /* ---- SOURCE values ---- */

    /** The film open in the BBS film editor panel. */
    public static final String SOURCE_EDITOR = "editor";

    /** A film currently playing in-world. */
    public static final String SOURCE_PLAYING = "playing";

    /* ---- Replay object keys ---- */

    public static final String REPLAY_ID = "id";
    public static final String REPLAY_UUID = "uuid";
    public static final String REPLAY_NAME = "name";
    public static final String REPLAY_LABEL = "label";
    public static final String REPLAY_FORM = "form";
    public static final String REPLAY_FORM_TYPE = "formType";
    /* The model key ("emoticons/alex") as opposed to REPLAY_FORM, which is only a display name.
     * Form.getDisplayName() returns the user's custom name when one is set and falls back to the
     * model key otherwise — so a renamed actor used to send "Bob" and Blender found no model for
     * it. Blender resolves the .bobj off this field and treats REPLAY_FORM as a label only. */
    public static final String REPLAY_FORM_MODEL = "formModel";
    public static final String REPLAY_IS_GROUP = "isGroup";
    public static final String REPLAY_GROUP = "group";
    public static final String REPLAY_ENABLED = "enabled";
    public static final String REPLAY_FIRST_PERSON = "fp";
    public static final String REPLAY_CHANNELS = "channels";
    public static final String REPLAY_SKIPPED_CHANNELS = "skippedChannels";

    /** Payload-feld: material-eigenschafts-kanäle (color/paint/glow) aus replay.properties,
     *  analog zu REPLAY_CHANNELS aber mit color als r/g/b/a-subkurven. */
    public static final String REPLAY_PROPERTIES = "properties";
    /** Color-subkurven-keys innerhalb eines properties-eintrags (jede ist eine keyframe-liste). */
    public static final String PROP_R = "r";
    public static final String PROP_G = "g";
    public static final String PROP_B = "b";
    public static final String PROP_A = "a";
    /** Glow-subkurven-key (Phase G): GlowSettings.intensity als eigene skalar-kurve neben r/g/b.
     *  Kann in BBS NEGATIV sein (dunkelt) — Blender clamped emission ≥0 (dokumentierte grenze). */
    public static final String PROP_INTENSITY = "intensity";
    /** Top-level key des glow-kanals im properties-objekt (analog zu "color"). */
    public static final String PROP_GLOW = "glow";

    /* ---- Keyframe object keys ---- */

    /** Film tick of the keyframe (float). */
    public static final String KF_TICK = "tick";

    /** Raw BBS value (number). */
    public static final String KF_VALUE = "value";

    /** Interpolation key: "linear", "constant", "hermite", "step", ... */
    public static final String KF_INTERP = "interp";

    /** Left/right bezier handle offsets. BBS defaults: lx=rx=5, ly=ry=0. */
    public static final String KF_LX = "lx";
    public static final String KF_LY = "ly";
    public static final String KF_RX = "rx";
    public static final String KF_RY = "ry";

    /* ---- ACTORS_IMPORT_RESULT keys (the reverse direction: Blender -> BBS) ---- */

    /**
     * Number of replays that were matched by uuid and written. Reuses {@link #OK} and {@link #ERROR}
     * above; see {@code ActorImporter} for the request shape (which is a subset of the export
     * payload above: only {@code replays[].uuid} and {@code replays[].channels} are read).
     */
    public static final String APPLIED = "applied";

    /**
     * Number of payload replays that were NOT applied — no uuid, an unknown uuid (deleted actor), or
     * no {@code channels} object. Replay-level, like {@link #APPLIED}: channels skipped inside an
     * applied replay (unknown id / non-numeric) are not counted here.
     */
    public static final String SKIPPED = "skipped";

    private ActorProtocol()
    {
        /* static-only constants holder */
    }
}
