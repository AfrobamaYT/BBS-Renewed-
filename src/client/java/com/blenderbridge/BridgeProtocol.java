// Coded by Afrobama/Claude — Blender<->BBS live-sync bridge (Phase 1 transport).
package com.blenderbridge;

import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * BridgeProtocol — the wire format shared between the BBS java side (this package)
 * and the Blender add-on side (python).
 *
 * <p>WIRE ENCODING: null-byte-delimited ({@code \0}) UTF-8 JSON messages, exactly like
 * the Blender-MCP server ({@code {...}\0{...}\0}). One JSON object per delimiter frame.
 * See {@link BridgeServer} for the framing/parsing.</p>
 *
 * <p>MESSAGE SCHEMA (a single "delta" envelope):</p>
 * <pre>
 * {
 *   "type":   "&lt;msgtype&gt;",   // one of the TYPE_* constants below
 *   "uuid":   "&lt;string&gt;",    // stable identity of the synced object (camera, actor, ...)
 *   "seq":    &lt;long&gt;,        // monotonically increasing per-sender sequence number
 *   "origin": "bbs"|"blender", // who produced this message (echo-suppression + LWW)
 *   "fields": { ... }          // type-specific payload (see below)
 * }
 * </pre>
 *
 * <p>ECHO / LWW (last-writer-wins): every message carries {@code seq} (monotonic) and
 * {@code origin}. The receiver keeps the highest {@code seq} it has seen per {@code uuid}
 * ({@link BridgeState#lastSeqPerUuid}) and drops any inbound message whose seq is not
 * strictly newer — this collapses feedback loops. {@link BridgeState#applyingRemote}
 * guards the apply-path so BBS' own change-detection does not immediately re-emit a
 * change we just applied from Blender.</p>
 *
 * <p>Phase-1 CAMERA fields: {@code x, y, z} (position, doubles), {@code pitch, yaw, roll}
 * (degrees, doubles), {@code fov} (degrees, double), {@code tick} (film tick, long).</p>
 */
public final class BridgeProtocol
{
    /* ---- Message types (Phase 1) ---- */

    /** Liveness probe. Sender expects a {@link #TYPE_PONG} back. */
    public static final String TYPE_PING = "PING";

    /** Liveness reply to a {@link #TYPE_PING}. */
    public static final String TYPE_PONG = "PONG";

    /** Handshake sent on connect; {@code fields} may carry {@code app}/{@code version}. */
    public static final String TYPE_HELLO = "HELLO";

    /** Camera delta: position + rotation + fov + tick (see class-doc for field list). */
    public static final String TYPE_CAMERA = "CAMERA";

    /**
     * EXEC request: run a Java-code string inside the live BBS JVM. The code is carried in
     * {@code fields.}{@value #EXEC_CODE}. Dev tool only — gated by {@link BridgeExec#isEnabled()}.
     */
    public static final String TYPE_EXEC = "EXEC";

    /**
     * EXEC reply: the structured result of an {@link #TYPE_EXEC}. Its {@code fields} object IS the
     * payload from {@link BridgeExec#eval(String)} — {@code {ok, stdout, result, error}}. Built by
     * {@link #execResult(long, JsonObject)}.
     */
    public static final String TYPE_EXEC_RESULT = "EXEC_RESULT";

    /**
     * WORLD_EXPORT request (Phase 2): dump a Minecraft block region to an OBJ/MTL/PNG set on disk
     * so Blender can import the actual world as a camera reference. Its {@code fields} carry the
     * inclusive region bounds {@code minX,minY,minZ,maxX,maxY,maxZ} (ints). The output path is
     * chosen by BBS (see {@code WorldExportBridge}) and returned in the reply — the caller does not
     * pass one.
     */
    public static final String TYPE_WORLD_EXPORT = "WORLD_EXPORT";

    /**
     * WORLD_EXPORT reply: the structured result of a {@link #TYPE_WORLD_EXPORT}. Its {@code fields}
     * object IS the payload from {@code WorldExportBridge.export(...)} —
     * {@code {ok, objPath, blocks, faces, error}}. Built by {@link #worldExportResult(long, JsonObject)}.
     */
    public static final String TYPE_WORLD_EXPORT_RESULT = "WORLD_EXPORT_RESULT";

    /**
     * ACTORS_EXPORT request (Phase 3): serialise the currently open film's replays (actor
     * performances) so Blender can rebuild them as animated objects. Takes NO parameters — its
     * {@code fields} object is empty; the exporter picks whichever film is open (editor first, then
     * a playing one).
     */
    public static final String TYPE_ACTORS_EXPORT = "ACTORS_EXPORT";

    /**
     * ACTORS_EXPORT reply: the structured result of a {@link #TYPE_ACTORS_EXPORT}. Its {@code fields}
     * object IS the payload from {@code ActorBridge.export(...)} —
     * {@code {ok, film, source, replays, error}}; see {@code ActorProtocol} for the full shape. Built
     * by {@link #actorsExportResult(long, JsonObject)}.
     */
    public static final String TYPE_ACTORS_EXPORT_RESULT = "ACTORS_EXPORT_RESULT";

    /**
     * ACTORS_IMPORT request (Phase 3, the reverse of {@link #TYPE_ACTORS_EXPORT}): write
     * Blender-authored keyframe curves back into the open film's replays. Its {@code fields} object
     * is the SAME shape the export reply produces — {@code {"replays":[{"uuid":..,"channels":{..}}]}}
     * — so a round-trip needs no schema translation. Replays are matched by uuid; a channel named in
     * the payload is replaced wholesale, a channel absent from it is left untouched. See
     * {@code ActorImporter}.
     */
    public static final String TYPE_ACTORS_IMPORT = "ACTORS_IMPORT";

    /** Read-only recovery: never reinterpret a missing receipt as a new write. */
    public static final String TYPE_ACTORS_IMPORT_RECEIPT = "ACTORS_IMPORT_RECEIPT";

    /**
     * ACTORS_IMPORT reply: the structured result of a {@link #TYPE_ACTORS_IMPORT}. Its {@code fields}
     * object IS the payload from {@code ActorBridge.importActors(...)} —
     * {@code {ok, applied, skipped, error}}; see {@code ActorProtocol}. Built by
     * {@link #actorsImportResult(long, JsonObject)}.
     */
    public static final String TYPE_ACTORS_IMPORT_RESULT = "ACTORS_IMPORT_RESULT";

    /** UUID used for the actor-export/import channel. Blender and BBS agree on this constant. */
    public static final String UUID_ACTORS = "actors";

    /* ---- BOOT-WINDOW SCHEMA RESERVATIONS (2026-07-16) --------------------------------------
     * Message types whose HANDLERS do not exist yet. They are declared now because constants are
     * class structure: the next -Pdebug boot is the one guaranteed chance to load them, after
     * which handler BODIES can iterate freely over JDWP hotswap (deploy-fenster-doc, klasse 2/3).
     * Dispatch for unknown types is already a safe no-op in BlenderBridgeClient. */

    /**
     * Incremental progress of a streaming world export (phase 3): {@code fields} carries
     * {@code {done, total, blocks, verts}} chunk-column counters so Blender can show a bar
     * instead of a frozen "exporting…" label. Emitted every N completed columns.
     */
    public static final String TYPE_WORLD_EXPORT_PROGRESS = "WORLD_EXPORT_PROGRESS";

    /**
     * Ask BBS to bake a free (AI-driven) entity's per-tick pose over N ticks (phase 4):
     * {@code fields = {entityUuid, ticks}}. Replay-bound actors do NOT use this — their
     * keyframes are read directly; this exists because a 0.5s poll can never reconstruct
     * 20tps motion (panel BLOCKER: pose-capture braucht einen mechanismus).
     */
    public static final String TYPE_ENTITY_BAKE = "ENTITY_BAKE";

    /** Reply to {@link #TYPE_ENTITY_BAKE}: {@code {ok, entityUuid, fps, frames:[...], error}}. */
    public static final String TYPE_ENTITY_BAKE_RESULT = "ENTITY_BAKE_RESULT";

    /**
     * Explicit camera-ownership handover (phase 1): {@code fields = {owner}} with one of
     * {@code "NOBODY" | "BBS" | "BLENDER"} (mirrors {@link LiveCameraOverride.Owner}).
     * Sent by whichever side GRANTS or RELEASES control; replaces the historical implicit
     * grab-on-first-frame that pinned the user's camera.
     */
    public static final String TYPE_CAMERA_OWNER = "CAMERA_OWNER";

    /** Blender asks BBS to start, stop, or inspect a player replay recording. */
    public static final String TYPE_REPLAY_RECORD_CONTROL = "REPLAY_RECORD_CONTROL";

    /** Correlated reply to {@link #TYPE_REPLAY_RECORD_CONTROL}. */
    public static final String TYPE_REPLAY_RECORD_CONTROL_RESULT = "REPLAY_RECORD_CONTROL_RESULT";

    /** Static subject data for one live player recording (film, replay, form, channels). */
    public static final String TYPE_REPLAY_RECORD_START = "REPLAY_RECORD_START";

    /** One timestamped live-preview sample recorded by BBS. */
    public static final String TYPE_REPLAY_RECORD_FRAME = "REPLAY_RECORD_FRAME";

    /** End marker for the preview stream; the normal actor push carries the authoritative bake. */
    public static final String TYPE_REPLAY_RECORD_STOP = "REPLAY_RECORD_STOP";

    /* ---- EXEC field keys ---- */

    /** Field carrying the Java-code string to run in an {@link #TYPE_EXEC} request. */
    public static final String EXEC_CODE = "code";

    /**
     * Antwortfeld: die {@code uuid} DESSEN, der gefragt hat — damit ein Messer seine eigene
     * Antwort von einer fremden unterscheiden kann.
     *
     * <p>⛔ WOFUER (2026-08-02 gemessen: 11 von 12 Antworten gehoerten zu einer fremden
     * Anfrage): der Server sendet jede Antwort an JEDE offene Verbindung, und
     * {@link #execResult} stempelt {@link #UUID_EXEC} samt eigener Sequenznummer darauf.
     * Damit trug die Antwort kein einziges Feld, das den Fragesteller nennt. Zwei
     * gleichzeitige Messungen bekamen wechselseitig das Ergebnis der anderen —
     * vollstaendig, plausibel und zur falschen Frage.</p>
     *
     * <p>⚠️ ZUSATZFELD, KEINE UMBENENNUNG: die uuid des Umschlags bleibt {@code UUID_EXEC},
     * damit jeder vorhandene Leser unveraendert weiterlaeuft. Wer korrelieren will, liest
     * {@code fields.fuer}; wer das Feld nicht kennt, merkt nichts.</p>
     */
    public static final String EXEC_FUER = "fuer";

    /** UUID used for the EXEC channel. Blender and BBS agree on this constant. */
    public static final String UUID_EXEC = "exec";

    /* ---- WORLD_EXPORT field keys ---- */

    /** Inclusive region bounds of a {@link #TYPE_WORLD_EXPORT} request (ints). */
    public static final String WORLD_MIN_X = "minX";
    public static final String WORLD_MIN_Y = "minY";
    public static final String WORLD_MIN_Z = "minZ";
    public static final String WORLD_MAX_X = "maxX";
    public static final String WORLD_MAX_Y = "maxY";
    public static final String WORLD_MAX_Z = "maxZ";

    /**
     * Optional override for the output directory. NOT part of the agreed Blender-&gt;BBS contract
     * (BBS picks the path); accepted only so a manual/EXEC-driven caller can redirect the dump.
     */
    public static final String WORLD_OUT = "out";

    /* ---- WORLD_EXPORT_RESULT field keys ---- */

    /** True when the OBJ was written. */
    public static final String WORLD_OK = "ok";

    /** Absolute path (forward slashes) of the written .obj — what Blender opens. */
    public static final String WORLD_OBJ_PATH = "objPath";

    /** Absolute path of the written .mtl. */
    public static final String WORLD_MTL_PATH = "mtlPath";

    /** Absolute path of the directory the block PNGs were extracted into. */
    public static final String WORLD_TEX_DIR = "texDir";

    /** Number of blocks that contributed at least one face. */
    public static final String WORLD_BLOCKS = "blocks";

    /** Number of quads written. */
    public static final String WORLD_FACES = "faces";

    /** Failure reason; empty when {@link #WORLD_OK} is true. */
    public static final String WORLD_ERROR = "error";

    /** UUID used for the world-export channel. Blender and BBS agree on this constant. */
    public static final String UUID_WORLD = "world";

    /* ---- Envelope keys ---- */

    public static final String KEY_TYPE = "type";
    public static final String KEY_UUID = "uuid";
    public static final String KEY_SEQ = "seq";
    public static final String KEY_ORIGIN = "origin";
    public static final String KEY_FIELDS = "fields";

    /* ---- Origin values ---- */

    public static final String ORIGIN_BBS = "bbs";
    public static final String ORIGIN_BLENDER = "blender";

    /* ---- CAMERA field keys ---- */

    public static final String CAM_X = "x";
    public static final String CAM_Y = "y";
    public static final String CAM_Z = "z";
    public static final String CAM_PITCH = "pitch";
    public static final String CAM_YAW = "yaw";
    public static final String CAM_ROLL = "roll";
    public static final String CAM_FOV = "fov";
    public static final String CAM_TICK = "tick";

    /** UUID used for the single scene camera. Blender and BBS agree on this constant. */
    public static final String UUID_CAMERA = "camera";

    private BridgeProtocol()
    {
        /* static-only helper */
    }

    /**
     * Build a well-formed envelope with an empty (but present) {@code fields} object.
     *
     * @param type   one of the {@code TYPE_*} constants
     * @param uuid   stable object identity
     * @param seq    monotonic per-sender sequence number
     * @param origin {@link #ORIGIN_BBS} or {@link #ORIGIN_BLENDER}
     */
    public static JsonObject envelope(String type, String uuid, long seq, String origin)
    {
        JsonObject message = new JsonObject();

        message.addProperty(KEY_TYPE, type);
        message.addProperty(KEY_UUID, uuid);
        message.addProperty(KEY_SEQ, seq);
        message.addProperty(KEY_ORIGIN, origin);
        message.add(KEY_FIELDS, new JsonObject());

        return message;
    }

    /** Add the versioned BBS feature surface to a HELLO fields object. */
    public static void addLocalHelloFields(JsonObject fields)
    {
        fields.addProperty("app", "bbs");
        fields.addProperty("version", net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("bbs")
            .map((c) -> c.getMetadata().getVersion().getFriendlyString()).orElse("unknown"));
        fields.addProperty("protocol", 2);
        JsonArray capabilities = new JsonArray();

        for (String capability : new String[] {
            "camera", "camera_owner", "playhead", "actors_pull", "actors_push",
            "actors_write", "actor_channel_patch_v1", "actor_shape_patch_v1",
            "actor_rig_property_pull_v1",
            "actor_rig_property_patch_v1",
            "actor_rig_property_lease_v1",
            "actor_rig_property_durable_v1",
            "actor_shape_map_patch_v1", "actor_shape_timeline_patch_v1",
            "shape_bezier_eval_v1",
            "actor_shape_timeline_transform_v1", "actor_first_shape_v1",
            "actor_shape_timeline_durable_transform_v1",
            "actor_shape_grid_patch_v1",
            "actor_shape_grid_durable_v1",
            "actor_first_shape_durable_v1",
            "actor_scene_timing_durable_v1",
            "actor_root_scale_v1", "actor_pose_preview_v1",
            "actor_shape_easing_patch_v1",
            "actor_visibility_patch_v1", "actor_visibility_durable_v1",
            "actor_receipt_recovery_v1", "actor_durable_v1",
            "actor_resync_v1", "actor_leases_v1", "actor_shared_undo_v1", "recording_live_v1",
            "recording_morph_preview_v1", "film_structure_write_v1", "markers_pull_v1",
            "markers_write_v1",
            "world_export", "world_delta",
            "film_camera", "camera_clip_write", "camera_clip_bound_v1", "lights_write", "render_composite"
        })
        {
            capabilities.add(capability);
        }

        fields.add("capabilities", capabilities);
    }

    /**
     * Build a CAMERA envelope. All angles are in degrees; position in world units.
     *
     * @param seq  monotonic per-sender sequence number
     * @param tick the current film/playback tick
     */
    public static JsonObject camera(long seq, double x, double y, double z, double pitch, double yaw, double roll, double fov, long tick)
    {
        JsonObject message = envelope(TYPE_CAMERA, UUID_CAMERA, seq, ORIGIN_BBS);
        JsonObject fields = message.getAsJsonObject(KEY_FIELDS);

        fields.addProperty(CAM_X, x);
        fields.addProperty(CAM_Y, y);
        fields.addProperty(CAM_Z, z);
        fields.addProperty(CAM_PITCH, pitch);
        fields.addProperty(CAM_YAW, yaw);
        fields.addProperty(CAM_ROLL, roll);
        fields.addProperty(CAM_FOV, fov);
        fields.addProperty(CAM_TICK, tick);

        return message;
    }

    /** Build a PONG in reply to a received PING (echoes the ping's uuid). */
    public static JsonObject pong(long seq, String uuid)
    {
        return envelope(TYPE_PONG, uuid == null ? UUID_CAMERA : uuid, seq, ORIGIN_BBS);
    }

    /**
     * Build an EXEC_RESULT envelope whose {@code fields} object IS the given {@code payload}
     * ({@code {ok, stdout, result, error}} from {@link BridgeExec#eval(String)}).
     *
     * @param seq     monotonic per-sender sequence number
     * @param payload the eval result object (used directly as the envelope's {@code fields})
     */
    public static JsonObject execResult(long seq, JsonObject payload)
    {
        JsonObject message = envelope(TYPE_EXEC_RESULT, UUID_EXEC, seq, ORIGIN_BBS);

        /* Replace the empty fields object with the eval payload directly. */
        message.add(KEY_FIELDS, payload == null ? new JsonObject() : payload);

        return message;
    }

    /**
     * Build a WORLD_EXPORT_RESULT envelope whose {@code fields} object IS the given {@code payload}
     * ({@code {ok, objPath, blocks, faces, error}} from {@code WorldExportBridge.export(...)}).
     *
     * @param seq     monotonic per-sender sequence number
     * @param payload the export result object (used directly as the envelope's {@code fields})
     */
    public static JsonObject worldExportResult(long seq, JsonObject payload)
    {
        JsonObject message = envelope(TYPE_WORLD_EXPORT_RESULT, UUID_WORLD, seq, ORIGIN_BBS);

        /* Replace the empty fields object with the export payload directly. */
        message.add(KEY_FIELDS, payload == null ? new JsonObject() : payload);

        return message;
    }

    /**
     * Build an ACTORS_EXPORT_RESULT envelope whose {@code fields} object IS the given {@code payload}
     * ({@code {ok, film, source, replays, error}} from {@code ActorBridge.export(...)}).
     *
     * @param seq     monotonic per-sender sequence number
     * @param payload the export result object (used directly as the envelope's {@code fields})
     */
    public static JsonObject actorsExportResult(long seq, JsonObject payload)
    {
        JsonObject message = envelope(TYPE_ACTORS_EXPORT_RESULT, UUID_ACTORS, seq, ORIGIN_BBS);

        /* Replace the empty fields object with the export payload directly. */
        message.add(KEY_FIELDS, payload == null ? new JsonObject() : payload);

        return message;
    }

    /**
     * Build an ACTORS_IMPORT_RESULT envelope whose {@code fields} object IS the given {@code payload}
     * ({@code {ok, applied, skipped, error}} from {@code ActorBridge.importActors(...)}).
     *
     * @param seq     monotonic per-sender sequence number
     * @param payload the import result object (used directly as the envelope's {@code fields})
     */
    public static JsonObject actorsImportResult(long seq, JsonObject payload)
    {
        JsonObject message = envelope(TYPE_ACTORS_IMPORT_RESULT, UUID_ACTORS, seq, ORIGIN_BBS);

        /* Replace the empty fields object with the import payload directly. */
        message.add(KEY_FIELDS, payload == null ? new JsonObject() : payload);

        return message;
    }

    /* ---- Safe accessors (tolerate missing / mistyped members) ---- */

    /** Read the envelope {@code type}, or {@code ""} if absent. */
    public static String getType(JsonObject message)
    {
        return getString(message, KEY_TYPE, "");
    }

    /** Read the envelope {@code uuid}, or {@code null} if absent. */
    public static String getUuid(JsonObject message)
    {
        return getString(message, KEY_UUID, null);
    }

    /** Read the envelope {@code origin}, or {@code ""} if absent. */
    public static String getOrigin(JsonObject message)
    {
        return getString(message, KEY_ORIGIN, "");
    }

    /** Read the envelope {@code seq}, or {@code fallback} if absent / not a number. */
    public static long getSeq(JsonObject message, long fallback)
    {
        JsonElement element = message == null ? null : message.get(KEY_SEQ);

        if (element == null || !element.isJsonPrimitive())
        {
            return fallback;
        }

        JsonPrimitive primitive = element.getAsJsonPrimitive();

        if (!primitive.isNumber())
        {
            return fallback;
        }

        return primitive.getAsLong();
    }

    /** Read the {@code fields} object, or {@code null} if absent / not an object. */
    public static JsonObject getFields(JsonObject message)
    {
        if (message == null)
        {
            return null;
        }

        JsonElement element = message.get(KEY_FIELDS);

        if (element == null || !element.isJsonObject())
        {
            return null;
        }

        return element.getAsJsonObject();
    }

    /** Read a double field from a {@code fields} object, or {@code fallback} if absent. */
    public static double getDouble(JsonObject fields, String key, double fallback)
    {
        if (fields == null)
        {
            return fallback;
        }

        JsonElement element = fields.get(key);

        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber())
        {
            return fallback;
        }

        return element.getAsDouble();
    }

    /** Read a long field from a {@code fields} object, or {@code fallback} if absent. */
    public static long getLong(JsonObject fields, String key, long fallback)
    {
        if (fields == null)
        {
            return fallback;
        }

        JsonElement element = fields.get(key);

        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber())
        {
            return fallback;
        }

        return element.getAsLong();
    }

    /** Read a string field from a {@code fields} object, or {@code fallback} if absent / not a string. */
    public static String getFieldString(JsonObject fields, String key, String fallback)
    {
        return getString(fields, key, fallback);
    }

    private static String getString(JsonObject message, String key, String fallback)
    {
        if (message == null)
        {
            return fallback;
        }

        JsonElement element = message.get(key);

        if (element == null || !element.isJsonPrimitive())
        {
            return fallback;
        }

        return element.getAsString();
    }
}
