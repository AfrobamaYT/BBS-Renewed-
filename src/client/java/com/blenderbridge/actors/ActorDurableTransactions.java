package com.blenderbridge.actors;

import com.blenderbridge.BridgeProtocol;
import com.blenderbridge.BridgeState;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.film.Film;

import java.util.HashSet;
import java.util.Set;

/** Durable conditional actor operations, committed with their receipt in the native film. */
public final class ActorDurableTransactions
{
    private static final int RECEIPTS = 64;
    private static final int CLIENTS = 128;
    private static final int JOURNAL_BYTES = 16 * 1024 * 1024;

    private ActorDurableTransactions()
    {}

    static JsonObject result(JsonObject fields, boolean ok, String error, String conflict)
    {
        JsonObject out = new JsonObject();
        out.addProperty("ok", ok);
        out.addProperty("requestId", BridgeProtocol.getFieldString(fields, "requestId", ""));
        out.addProperty("applied", 0);
        out.addProperty("skipped", 0);
        out.addProperty("error", error);
        if (!conflict.isEmpty())
        {
            out.addProperty("conflict", conflict);
        }
        return out;
    }

    public static JsonObject handle(JsonObject fields, BridgeState state, boolean query)
    {
        try
        {
            Film film = ActorProject.bound(fields);
            String id = BridgeProtocol.getFieldString(fields, "requestId", "");
            String client = BridgeProtocol.getFieldString(fields, "client", "");
            if (id.isEmpty() || id.length() > 128 || !client.matches("[a-zA-Z0-9_-]{1,64}"))
            {
                throw new IllegalArgumentException("invalid durable request/client identity");
            }
            long sequence = fields.get("transactionSeq").getAsBigDecimal().longValueExact();
            if (sequence <= 0L || sequence > 9007199254740991L)
            {
                throw new IllegalArgumentException("transaction sequence must be a positive exact integer");
            }
            JsonObject journal = ActorProject.journal(film);
            JsonObject receipts = journal.getAsJsonObject("receipts");
            JsonObject clients = journal.getAsJsonObject("clients");
            if (receipts.has(id))
            {
                JsonObject previous = receipts.getAsJsonObject(id);
                if (!previous.get("request").equals(fields))
                {
                    return result(fields, false, "requestId reused with different content", "requestId");
                }
                JsonObject out = previous.getAsJsonObject("result").deepCopy();
                if (query)
                {
                    out.addProperty("receiptRecovered", true);
                }
                return out;
            }
            long highWater = clients.has(client) ? clients.get(client).getAsLong() : 0L;
            if (sequence <= highWater || query)
            {
                JsonObject out = result(fields, false,
                    sequence <= highWater ? "receipt expired; old operation will not execute again" : "operation not committed", "");
                out.addProperty("receiptUnknown", sequence <= highWater);
                out.addProperty("notApplied", sequence > highWater);
                out.addProperty("project", ActorProject.identity(film));
                return out;
            }
            if (!clients.has(client) && clients.size() >= CLIENTS)
            {
                throw new IllegalStateException("durable client capacity reached; no identity was evicted");
            }
            return commit(fields, film, state, journal, client, sequence);
        }
        catch (Exception exception)
        {
            JsonObject out = result(fields, false, exception.getMessage(), "");
            out.addProperty("receiptUnknown", true);
            return out;
        }
    }

    private static JsonObject commit(JsonObject fields, Film film, BridgeState state,
        JsonObject journal, String client, long sequence)
    {
        if (state == null)
        {
            throw new IllegalArgumentException("no bridge state");
        }
        if (fields.has("compensateOf"))
        {
            return compensate(fields, film, state, journal, client, sequence);
        }

        JsonObject snapshot = ActorExporter.exportActors();
        boolean resync = fields.has("resync") && fields.get("resync").getAsBoolean();

        if (resync && fields.getAsJsonArray("replays").size() != 0)
        {
            throw new IllegalArgumentException("resync fence cannot also mutate actor paths");
        }

        ActorChannelPatchValidation.Result check = ActorChannelPatchValidation.check(
            fields, snapshot.getAsJsonArray("replays"));
        JsonObject out = result(fields, check.error.isEmpty(), check.error, check.conflict);
        Film staged = BBSMod.getFilms().create(film.getId(), film.toData().asMap());
        boolean write = check.error.isEmpty() && check.needsWrite;
        JsonArray before = null;
        JsonArray after = null;
        JsonObject revisions = null;

        if (write)
        {
            JsonArray paths = fields.getAsJsonArray("replays");
            before = ActorNativeSnapshot.capture(staged, paths);
            revisions = ActorNativeRevisions.capture(film, paths);
            ActorNativeRevisions.track(staged, paths);
            out = ActorImporter.applyToFilm(fields, staged, state);

            if (!out.get("ok").getAsBoolean() || out.get("skipped").getAsInt() != 0)
            {
                throw new IllegalStateException("validated operation could not be staged in full");
            }

            after = ActorNativeSnapshot.capture(staged, paths);
        }

        decorate(out, fields, film, check.error.isEmpty() && !write);
        out.addProperty("undoAvailable", write);

        if (resync)
        {
            out.add("snapshot", snapshot);
            out.addProperty("resync", true);
        }

        addReceipt(journal, fields, out, before, after, revisions, write ? "applied" : "", "");
        advanceJournal(journal, client, sequence);
        persist(film, staged, journal);

        if (write)
        {
            JsonObject applied = ActorImporter.applyActors(fields, state);

            if (!applied.get("ok").getAsBoolean() || applied.get("skipped").getAsInt() != 0)
            {
                throw new IllegalStateException("committed on disk; live publication requires reload");
            }

            ActorPushTracker.markSynced();
        }

        return out;
    }

    private static JsonObject compensate(JsonObject fields, Film film, BridgeState state,
        JsonObject journal, String client, long sequence)
    {
        String targetId = BridgeProtocol.getFieldString(fields, "compensateOf", "");
        String direction = BridgeProtocol.getFieldString(fields, "compensateDirection", "");
        JsonObject receipts = journal.getAsJsonObject("receipts");
        JsonObject target = receipts.has(targetId) ? receipts.getAsJsonObject(targetId) : null;
        String problem = compensationProblem(fields, target, client, direction, film);
        JsonObject out = result(fields, problem.isEmpty(), problem, "");
        Film staged = BBSMod.getFilms().create(film.getId(), film.toData().asMap());

        if (problem.isEmpty())
        {
            JsonArray paths = target.getAsJsonObject("request").getAsJsonArray("replays");
            JsonObject revisions = target.getAsJsonObject("nativeRevisions");
            String revisionConflict = ActorNativeRevisions.conflict(film, revisions);

            if (!revisionConflict.isEmpty())
            {
                out = result(fields, false, "native path changed after the protected write", revisionConflict);
            }
            else
            {
                boolean undo = direction.equals("undo");
                JsonArray expected = target.getAsJsonArray(undo ? "nativeAfter" : "nativeBefore");
                JsonArray desired = target.getAsJsonArray(undo ? "nativeBefore" : "nativeAfter");
                String expectedState = undo ? "applied" : "undone";
                String desiredState = undo ? "undone" : "applied";
                String currentState = target.get("state").getAsString();
                boolean already = currentState.equals(desiredState);
                String conflict = ActorNativeSnapshot.conflict(film, paths, already ? desired : expected);

                if (!currentState.equals(expectedState) && !already)
                {
                    out = result(fields, false, "target transaction is not in a compensatable state", targetId);
                }
                else if (!conflict.isEmpty())
                {
                    out = result(fields, false, "compensation precondition failed", conflict);
                }
                else
                {
                    if (!already)
                    {
                        ActorNativeSnapshot.restore(staged, desired);
                        target.addProperty("state", desiredState);
                    }

                    out = result(fields, true, "", "");
                    out.addProperty("compensated", true);
                    out.addProperty("compensateOf", targetId);
                    out.addProperty("compensateDirection", direction);
                    out.addProperty("alreadyApplied", already);
                }
            }
        }

        decorate(out, fields, film, out.get("ok").getAsBoolean() && out.has("alreadyApplied")
            && out.get("alreadyApplied").getAsBoolean());

        if (!out.get("ok").getAsBoolean())
        {
            out.addProperty("notApplied", true);
        }

        addReceipt(journal, fields, out, null, null, null, "", targetId);
        advanceJournal(journal, client, sequence);
        persist(film, staged, journal);

        if (out.get("ok").getAsBoolean() && out.get("compensated").getAsBoolean()
            && !out.get("alreadyApplied").getAsBoolean())
        {
            JsonArray desired = target.getAsJsonArray(direction.equals("undo") ? "nativeBefore" : "nativeAfter");
            state.withRemoteApply(() -> ActorNativeSnapshot.restore(film, desired));
            ActorPushTracker.markSynced();
        }

        return out;
    }

    private static String compensationProblem(JsonObject fields, JsonObject target,
        String client, String direction, Film film)
    {
        if (!direction.equals("undo") && !direction.equals("redo"))
        {
            return "invalid compensation direction";
        }
        if (target == null || !target.has("nativeBefore") || !target.has("nativeAfter")
            || !target.has("nativeRevisions") || !target.has("state"))
        {
            return "target transaction has no compensation checkpoint";
        }

        JsonObject targetRequest = target.getAsJsonObject("request");

        if (!client.equals(BridgeProtocol.getFieldString(targetRequest, "client", "")))
        {
            return "compensation belongs to another durable client";
        }

        Set<String> requested = new HashSet<>(
            ActorNativeRevisions.capture(film, fields.getAsJsonArray("replays")).keySet());
        Set<String> original = new HashSet<>(
            ActorNativeRevisions.capture(film, targetRequest.getAsJsonArray("replays")).keySet());

        return requested.equals(original) ? "" : "compensation path set differs from target transaction";
    }

    private static void decorate(JsonObject out, JsonObject fields, Film film, boolean already)
    {
        out.addProperty("requestId", fields.get("requestId").getAsString());
        out.addProperty("project", ActorProject.identity(film));
        out.addProperty("durable", true);
        out.addProperty("alreadyApplied", already);
    }

    private static void addReceipt(JsonObject journal, JsonObject fields, JsonObject out,
        JsonArray before, JsonArray after, JsonObject revisions, String state, String preserve)
    {
        JsonObject receipt = new JsonObject();
        receipt.add("request", fields.deepCopy());
        receipt.add("result", out.deepCopy());

        if (before != null)
        {
            receipt.add("nativeBefore", before);
            receipt.add("nativeAfter", after);
            receipt.add("nativeRevisions", revisions);
            receipt.addProperty("state", state);
        }

        JsonObject receipts = journal.getAsJsonObject("receipts");
        receipts.add(fields.get("requestId").getAsString(), receipt);

        while (receipts.size() > RECEIPTS)
        {
            String victim = "";
            String current = fields.get("requestId").getAsString();

            for (String id : receipts.keySet())
            {
                if (!id.equals(preserve) && !id.equals(current))
                {
                    victim = id;
                    break;
                }
            }

            if (victim.isEmpty())
            {
                throw new IllegalStateException("receipt retention could not evict a safe entry");
            }

            receipts.remove(victim);
        }
    }

    private static void advanceJournal(JsonObject journal, String client, long sequence)
    {
        journal.getAsJsonObject("clients").addProperty(client, sequence);
        journal.addProperty("generation", Math.addExact(journal.get("generation").getAsLong(), 1L));
    }

    private static void persist(Film film, Film staged, JsonObject journal)
    {
        staged.bridgeJournal = journal.toString();

        if (staged.bridgeJournal.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > JOURNAL_BYTES)
        {
            throw new IllegalStateException("durable journal capacity reached; no operation executed");
        }
        if (!BBSMod.getFilms().save(film.getId(), staged.toData().asMap()))
        {
            throw new IllegalStateException("durable commit unconfirmed; reload before recovery");
        }

        film.bridgeJournal = staged.bridgeJournal;
        film.bridgePathRevisions = staged.bridgePathRevisions;
    }
}
