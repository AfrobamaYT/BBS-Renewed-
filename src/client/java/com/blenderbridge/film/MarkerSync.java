package com.blenderbridge.film;

import com.blenderbridge.BlenderBridgeClient;
import com.blenderbridge.BridgeProtocol;
import com.blenderbridge.BridgeState;
import com.blenderbridge.FilmIdentity;
import com.blenderbridge.actors.FilmBackup;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import com.mojang.logging.LogUtils;

import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.markers.Marker;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;

import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Publishes the current film's persistent timeline markers as a full, revisioned snapshot.
 *
 * <p>BBS already owns a native film-level marker model ({@code Film.markers}) with undo and
 * persistence. This tracker therefore serializes that source of truth instead of inventing a
 * sidecar. Every marker carries a persistent UUID; list indices are not identities because BBS
 * renumbers them after deletion. The SHA-256 revision is over the exact canonical marker array
 * and will be the conditional-write precondition for the reverse direction.</p>
 *
 * <p>The first tick after HELLO always publishes, including an empty list, so Blender can clear a
 * stale marker snapshot. Later ticks are idle unless film id or marker content changed. Main-thread
 * only; called from {@link BlenderBridgeClient}'s client tick.</p>
 */
public final class MarkerSync
{
    private static final Logger LOGGER = LogUtils.getLogger();

    private static String lastFilm = "";
    private static String lastRevision = "";
    private static boolean force = true;
    private static long pushCount;
    private static long writeHandleCount;
    private static long writeAppliedCount;
    private static final Map<String, CachedResult> RESULTS = new LinkedHashMap<>();

    private MarkerSync()
    {
        /* static-only helper */
    }

    public static void tick()
    {
        try
        {
            Film film = FilmIdentity.resolveOpenFilm();

            if (film == null)
            {
                lastFilm = "";
                lastRevision = "";
                force = true;

                return;
            }

            JsonArray markers = encode(film);
            String filmId = film.getId() == null ? "" : film.getId();
            String revision = revision(markers);

            if (!force && filmId.equals(lastFilm) && revision.equals(lastRevision))
            {
                return;
            }

            push(filmId, revision, markers);
            lastFilm = filmId;
            lastRevision = revision;
            force = false;
        }
        catch (Throwable throwable)
        {
            LOGGER.error("[blenderbridge] marker snapshot failed", throwable);
        }
    }

    public static void reset()
    {
        lastFilm = "";
        lastRevision = "";
        force = true;
    }

    public static long pushCount()
    {
        return pushCount;
    }

    /** Conditional Blender -> BBS full-marker write with correlated idempotent receipt. */
    public static void handle(JsonObject message, BridgeState state)
    {
        JsonObject fields = BridgeProtocol.getFields(message);
        String requestId = string(fields, "requestId");
        String requestHash = digest(fields == null ? "null" : fields.toString());
        JsonObject result;

        if (!requestId.isEmpty() && RESULTS.containsKey(requestId))
        {
            CachedResult cached = RESULTS.get(requestId);
            result = cached.hash.equals(requestHash)
                ? cached.result.deepCopy()
                : failure("requestId was reused with different marker data", "request-id-reuse", null, null);
        }
        else
        {
            try
            {
                result = FilmBackup.protect(() -> write(fields, state));
            }
            catch (Throwable throwable)
            {
                LOGGER.error("[blenderbridge] marker write failed", throwable);
                result = failure(throwable.getClass().getSimpleName() + ": " + throwable.getMessage(),
                    "exception", null, null);
            }

            if (!requestId.isEmpty())
            {
                RESULTS.put(requestId, new CachedResult(requestHash, result.deepCopy()));
                while (RESULTS.size() > 64)
                {
                    RESULTS.remove(RESULTS.keySet().iterator().next());
                }
            }
        }

        result.addProperty("requestId", requestId);
        JsonObject envelope = BridgeProtocol.envelope(
            "MARKERS_WRITE_RESULT", "markers", state.nextOutboundSeq(), BridgeProtocol.ORIGIN_BBS);
        envelope.add(BridgeProtocol.KEY_FIELDS, result);
        BlenderBridgeClient.SERVER.send(envelope);
        writeHandleCount++;
    }

    public static JsonArray encode(Film film)
    {
        JsonArray out = new JsonArray();

        for (Marker marker : film.markers.getList())
        {
            JsonObject item = new JsonObject();

            item.addProperty("uuid", marker.uuid.get());
            item.addProperty("tick", marker.tick.get());
            item.addProperty("label", marker.label.get());
            item.addProperty("color", marker.color.get());
            out.add(item);
        }

        return out;
    }

    public static String revision(JsonArray markers)
    {
        return digest(markers.toString());
    }

    private static JsonObject write(JsonObject fields, BridgeState state)
    {
        if (fields == null || state == null)
        {
            return failure("missing payload or bridge state", "invalid", null, null);
        }

        String expectedFilm = string(fields, "film");
        String baseRevision = string(fields, "baseRevision");
        String requestId = string(fields, "requestId");
        JsonElement rawMarkers = fields.get("markers");

        if (requestId.isEmpty() || requestId.length() > 128)
        {
            return failure("missing or unusable requestId", "invalid", null, null);
        }
        if (expectedFilm.isEmpty() || FilmIdentity.isForeignFilm(expectedFilm))
        {
            return failure("film mismatch or missing film id", "film", null, null);
        }
        if (!baseRevision.matches("[0-9a-f]{64}"))
        {
            return failure("baseRevision must be a SHA-256 digest", "invalid", null, null);
        }
        if (rawMarkers == null || !rawMarkers.isJsonArray())
        {
            return failure("markers must be an array", "invalid", null, null);
        }

        JsonArray desired = rawMarkers.getAsJsonArray();
        String invalid = validate(desired);
        if (!invalid.isEmpty())
        {
            return failure(invalid, "invalid", null, null);
        }

        UIFilmPanel panel = editorPanel();
        Film film = panel == null ? null : panel.getData();
        if (film == null)
        {
            return failure("no film editor open", "film", null, null);
        }
        if (!expectedFilm.equals(film.getId()))
        {
            return failure("film mismatch: write was for '" + expectedFilm + "', open film is '"
                + film.getId() + "'", "film", film, encode(film));
        }

        JsonArray current = encode(film);
        String currentRevision = revision(current);
        String desiredRevision = revision(desired);

        if (desiredRevision.equals(currentRevision))
        {
            JsonObject result = success(film, currentRevision, current, 0);
            result.addProperty("alreadyApplied", true);
            return result;
        }
        if (!baseRevision.equals(currentRevision))
        {
            return failure("BBS markers changed after Blender's baseline", "revision",
                film, current);
        }

        if (panel.getUndoHandler() != null)
        {
            /* Marker edits all address the same /markers value and are otherwise mergeable.
             * Fence both sides so this remote transaction cannot absorb the user's previous
             * marker edit, nor can the next local edit absorb this one. */
            panel.getUndoHandler().getUndoManager().markLastUndoNoMerging();
        }
        state.withRemoteApply(() -> replace(film, desired));
        writeAppliedCount++;
        if (panel.getUndoHandler() != null)
        {
            panel.getUndoHandler().submitUndo();
            panel.getUndoHandler().getUndoManager().markLastUndoNoMerging();
        }
        panel.save();

        JsonArray applied = encode(film);
        String appliedRevision = revision(applied);
        lastFilm = film.getId() == null ? "" : film.getId();
        lastRevision = appliedRevision;
        force = false;

        return success(film, appliedRevision, applied, 1);
    }

    private static void replace(Film film, JsonArray desired)
    {
        film.markers.preNotify();
        film.markers.getAllTyped().clear();

        for (JsonElement element : desired)
        {
            JsonObject item = element.getAsJsonObject();
            Marker marker = new Marker(String.valueOf(film.markers.getList().size()));

            marker.uuid.set(item.get("uuid").getAsString());
            marker.tick.set(item.get("tick").getAsInt());
            marker.label.set(item.get("label").getAsString());
            marker.color.set(item.get("color").getAsInt());
            film.markers.add(marker);
        }

        film.markers.sync();
        film.markers.postNotify();
    }

    private static String validate(JsonArray markers)
    {
        java.util.Set<String> uuids = new java.util.HashSet<>();
        java.util.Set<Integer> ticks = new java.util.HashSet<>();

        for (int index = 0; index < markers.size(); index++)
        {
            JsonElement element = markers.get(index);
            if (!element.isJsonObject())
            {
                return "marker " + index + " is not an object";
            }

            JsonObject item = element.getAsJsonObject();
            String uuid = string(item, "uuid");
            String label = strictString(item.get("label"));
            Integer tick = exactInteger(item.get("tick"));
            Integer color = exactInteger(item.get("color"));

            if (uuid.isEmpty() || uuid.length() > 128 || !uuids.add(uuid))
            {
                return "marker " + index + " has missing/duplicate uuid";
            }
            if (label == null)
            {
                return "marker " + index + " has no string label";
            }
            if (tick == null || tick < 0 || !ticks.add(tick))
            {
                return "marker " + index + " has invalid/duplicate tick";
            }
            if (color == null)
            {
                return "marker " + index + " has invalid ARGB color";
            }
        }

        return "";
    }

    private static JsonObject success(Film film, String revision, JsonArray markers, int applied)
    {
        JsonObject out = new JsonObject();

        out.addProperty("ok", true);
        out.addProperty("error", "");
        out.addProperty("conflict", "");
        out.addProperty("film", film.getId() == null ? "" : film.getId());
        out.addProperty("revision", revision);
        out.addProperty("applied", applied);
        out.addProperty("total", markers.size());
        out.addProperty("alreadyApplied", false);
        out.add("markers", markers);
        return out;
    }

    private static JsonObject failure(String error, String conflict, Film film, JsonArray markers)
    {
        JsonObject out = new JsonObject();

        out.addProperty("ok", false);
        out.addProperty("error", error == null ? "" : error);
        out.addProperty("conflict", conflict == null ? "" : conflict);
        out.addProperty("film", film == null || film.getId() == null ? "" : film.getId());
        out.addProperty("revision", markers == null ? "" : revision(markers));
        out.addProperty("applied", 0);
        out.addProperty("total", markers == null ? 0 : markers.size());
        out.addProperty("alreadyApplied", false);
        out.add("markers", markers == null ? new JsonArray() : markers);
        return out;
    }

    private static UIFilmPanel editorPanel()
    {
        UIDashboard dashboard = com.blenderbridge.BBSCompat.peekDashboard();
        UIFilmPanel panel = dashboard == null ? null : dashboard.getPanel(UIFilmPanel.class);

        return panel == null || panel.getData() == null ? null : panel;
    }

    private static String string(JsonObject object, String key)
    {
        return object == null ? "" : strictString(object.get(key)) == null
            ? "" : strictString(object.get(key));
    }

    private static String strictString(JsonElement element)
    {
        return element != null && element.isJsonPrimitive()
            && element.getAsJsonPrimitive().isString() ? element.getAsString() : null;
    }

    private static Integer exactInteger(JsonElement element)
    {
        if (element == null || !element.isJsonPrimitive())
        {
            return null;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (!primitive.isNumber())
        {
            return null;
        }
        double number = primitive.getAsDouble();
        return Double.isFinite(number) && number == Math.rint(number)
            && number >= Integer.MIN_VALUE && number <= Integer.MAX_VALUE ? (int) number : null;
    }

    private static String digest(String value)
    {
        try
        {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        }
        catch (NoSuchAlgorithmException exception)
        {
            throw new IllegalStateException("JVM has no SHA-256", exception);
        }
    }

    private static final class CachedResult
    {
        final String hash;
        final JsonObject result;

        CachedResult(String hash, JsonObject result)
        {
            this.hash = hash;
            this.result = result;
        }
    }

    public static long writeHandleCount()
    {
        return writeHandleCount;
    }

    public static long writeAppliedCount()
    {
        return writeAppliedCount;
    }

    private static void push(String film, String revision, JsonArray markers)
    {
        JsonObject fields = new JsonObject();

        fields.addProperty("film", film);
        fields.addProperty("revision", revision);
        fields.add("markers", markers);

        JsonObject envelope = BridgeProtocol.envelope(
            "MARKERS_SYNC", "markers:" + film, BlenderBridgeClient.STATE.nextOutboundSeq(),
            BridgeProtocol.ORIGIN_BBS);
        envelope.add(BridgeProtocol.KEY_FIELDS, fields);
        BlenderBridgeClient.SERVER.send(envelope);
        pushCount++;
    }
}
