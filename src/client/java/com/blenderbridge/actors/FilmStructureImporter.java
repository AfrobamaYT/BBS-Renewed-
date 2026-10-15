package com.blenderbridge.actors;

import com.blenderbridge.BlenderBridgeClient;
import com.blenderbridge.BridgeProtocol;
import com.blenderbridge.BridgeState;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;

import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Blender -> BBS replay creation, metadata, deletion and exact ordering. */
public final class FilmStructureImporter
{
    private static final Logger LOGGER = LogUtils.getLogger();

    private FilmStructureImporter() {}

    public static void handle(JsonObject message, BridgeState state)
    {
        JsonObject fields = BridgeProtocol.getFields(message);
        String requestId = BridgeProtocol.getFieldString(fields, "requestId", "");
        JsonObject result;

        try
        {
            result = FilmBackup.protect(() -> write(fields, state));
        }
        catch (Throwable t)
        {
            LOGGER.error("[blenderbridge] film structure import failed", t);
            result = failure(t.getClass().getSimpleName() + ": " + t.getMessage());
        }

        result.addProperty("requestId", requestId);
        JsonObject envelope = BridgeProtocol.envelope(
            "FILM_STRUCTURE_IMPORT_RESULT", "filmStructure", state.nextOutboundSeq(), BridgeProtocol.ORIGIN_BBS);
        envelope.add(BridgeProtocol.KEY_FIELDS, result);
        BlenderBridgeClient.SERVER.send(envelope);
    }

    static JsonObject write(JsonObject fields, BridgeState state)
    {
        if (fields == null || state == null)
        {
            return failure("missing payload or bridge state");
        }

        JsonElement raw = fields.get("replays");
        if (raw == null || !raw.isJsonArray())
        {
            return failure("no replays array in payload");
        }

        String mode = FilmStructureCodec.string(fields, "mode", "merge").toLowerCase();
        if (!mode.equals("merge") && !mode.equals("replace"))
        {
            return failure("mode must be 'merge' or 'replace'");
        }

        FilmStructureTarget target = FilmStructureTarget.resolve();
        if (target == null)
        {
            return failure("no film open");
        }

        String expectedFilm = FilmStructureCodec.string(fields, "film", "");
        if (com.blenderbridge.FilmIdentity.isForeignFilm(expectedFilm))
        {
            return failure("film mismatch: write was for '" + expectedFilm + "', open film is '"
                + target.film.getId() + "' — no structure changed");
        }

        JsonArray entries = raw.getAsJsonArray();
        String validation = FilmStructureCodec.validate(entries, target.film, mode.equals("replace"));
        if (!validation.isEmpty())
        {
            return failure(validation);
        }

        int[] counts = new int[4]; /* created, updated, removed, moved */
        state.withRemoteApply(() -> apply(target.film, entries, mode.equals("replace"), counts));

        int applied = counts[0] + counts[1] + counts[2] + counts[3];
        if (applied > 0)
        {
            target.refreshAndSave();
        }

        JsonObject out = result(true, "");
        out.addProperty("film", target.film.getId() == null ? "" : target.film.getId());
        out.addProperty("source", target.source);
        out.addProperty("mode", mode);
        out.addProperty("created", counts[0]);
        out.addProperty("updated", counts[1]);
        out.addProperty("removed", counts[2]);
        out.addProperty("moved", counts[3]);
        out.addProperty("total", target.film.replays.getList().size());
        out.addProperty("applied", applied);

        LOGGER.info("[blenderbridge] film structure {}: created={} updated={} removed={} moved={} total={}",
            mode, counts[0], counts[1], counts[2], counts[3], target.film.replays.getList().size());
        return out;
    }

    private static void apply(Film film, JsonArray entries, boolean replace, int[] counts)
    {
        List<Replay> desired = new ArrayList<>();
        Set<String> desiredIds = new HashSet<>();

        for (JsonElement element : entries)
        {
            JsonObject entry = element.getAsJsonObject();
            String uuid = FilmStructureCodec.string(entry, "uuid", "");
            Replay replay = FilmStructureCodec.find(film, uuid);

            if (replay == null)
            {
                replay = film.replays.addReplay();
                replay.uuid.set(uuid);
                FilmStructureCodec.apply(replay, entry);
                counts[0] += 1;
            }
            else if (FilmStructureCodec.apply(replay, entry))
            {
                counts[1] += 1;
            }

            desired.add(replay);
            desiredIds.add(uuid);
        }

        if (!replace)
        {
            film.replays.sync();
            return;
        }

        for (Replay replay : new ArrayList<>(film.replays.getList()))
        {
            if (!desiredIds.contains(replay.uuid.get()))
            {
                film.replays.remove(replay);
                counts[2] += 1;
            }
        }

        for (int i = 0; i < desired.size(); i++)
        {
            Replay replay = desired.get(i);
            int current = film.replays.getList().indexOf(replay);

            if (current != i)
            {
                film.replays.remove(replay);
                film.replays.add(i, replay);
                counts[3] += 1;
            }
        }

        film.replays.sync();
    }

    private static JsonObject failure(String error)
    {
        return result(false, error == null ? "" : error);
    }

    private static JsonObject result(boolean ok, String error)
    {
        JsonObject out = new JsonObject();
        out.addProperty("ok", ok);
        out.addProperty("film", "");
        out.addProperty("source", "");
        out.addProperty("mode", "");
        out.addProperty("created", 0);
        out.addProperty("updated", 0);
        out.addProperty("removed", 0);
        out.addProperty("moved", 0);
        out.addProperty("total", 0);
        out.addProperty("applied", 0);
        out.addProperty("error", error);
        return out;
    }
}
