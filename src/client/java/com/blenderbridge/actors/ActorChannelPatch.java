package com.blenderbridge.actors;

import com.blenderbridge.BridgeProtocol;
import com.blenderbridge.BridgeState;
import com.blenderbridge.FilmIdentity;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.Map;

/** Optimistic, idempotent root/item writes for the actor bridge. */
public final class ActorChannelPatch
{
    private static final int RECEIPT_LIMIT = 64;
    private static final Map<String, Receipt> RECEIPTS = new LinkedHashMap<>()
    {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Receipt> eldest)
        {
            return this.size() > RECEIPT_LIMIT;
        }
    };

    private ActorChannelPatch()
    {
    }

    public static boolean isConditional(JsonObject fields)
    {
        return fields != null && BridgeProtocol.getLong(fields, "channelPatch", 0L) == 1L;
    }

    public static JsonObject apply(JsonObject fields, BridgeState state)
    {
        String requestId = BridgeProtocol.getFieldString(fields, "requestId", "");

        if (requestId.isEmpty() || requestId.length() > 128)
        {
            return failure(requestId, "conditional actor write requires requestId", "");
        }

        Receipt previous = RECEIPTS.get(requestId);

        if (previous != null)
        {
            if (previous.request.equals(fields))
            {
                return previous.result.deepCopy();
            }

            return failure(requestId, "requestId was reused with different content", "requestId");
        }

        JsonObject result = applyFresh(fields, state, requestId);
        RECEIPTS.put(requestId, new Receipt(fields.deepCopy(), result.deepCopy()));
        return result;
    }

    /** Read a retained receipt without ever executing or caching a write. */
    public static JsonObject recover(JsonObject fields)
    {
        if (fields != null && fields.has("project"))
        {
            return ActorDurableTransactions.handle(fields, null, true);
        }
        String requestId = BridgeProtocol.getFieldString(fields, "requestId", "");
        Receipt previous = RECEIPTS.get(requestId);

        if (previous != null && previous.request.equals(fields))
        {
            JsonObject result = previous.result.deepCopy();
            result.addProperty("receiptRecovered", true);
            return result;
        }

        /* An evicted receipt cannot prove that the original write never happened. */
        JsonObject result = failure(requestId,
            previous == null ? "receipt unavailable; outcome unknown" : "receipt content mismatch", "");
        result.addProperty("receiptUnknown", true);
        return result;
    }

    private static JsonObject applyFresh(JsonObject fields, BridgeState state, String requestId)
    {
        if (state == null)
        {
            return failure(requestId, "no bridge state", "");
        }
        String expectedFilm = BridgeProtocol.getFieldString(fields, ActorProtocol.FILM, "");

        if (FilmIdentity.isForeignFilm(expectedFilm))
        {
            JsonObject out = failure(requestId, "film mismatch", "film");
            out.addProperty(ActorProtocol.FILM, FilmIdentity.currentFilmId());
            out.addProperty("expectedFilm", expectedFilm);
            out.addProperty("filmMismatch", true);
            return out;
        }

        JsonObject current = ActorExporter.exportActors();

        if (!current.get(ActorProtocol.OK).getAsBoolean())
        {
            return failure(requestId, current.get(ActorProtocol.ERROR).getAsString(), "");
        }

        ActorChannelPatchValidation.Result check = ActorChannelPatchValidation.check(
            fields, current.getAsJsonArray(ActorProtocol.REPLAYS));

        if (!check.error.isEmpty())
        {
            return failure(requestId, check.error, check.conflict);
        }
        if (!check.needsWrite)
        {
            JsonObject out = success(requestId, 0, 0);
            out.addProperty("alreadyApplied", true);
            return out;
        }

        JsonObject result = FilmBackup.protect(() -> ActorImporter.applyActors(fields, state));
        result.addProperty("requestId", requestId);
        result.addProperty("alreadyApplied", false);

        if (result.get(ActorProtocol.OK).getAsBoolean()
            && result.get(ActorProtocol.APPLIED).getAsInt() > 0)
        {
            ActorPushTracker.markSynced();
        }

        return result;
    }

    private static JsonObject success(String requestId, int applied, int skipped)
    {
        JsonObject out = new JsonObject();
        out.addProperty(ActorProtocol.OK, true);
        out.addProperty(ActorProtocol.APPLIED, applied);
        out.addProperty(ActorProtocol.SKIPPED, skipped);
        out.addProperty(ActorProtocol.ERROR, "");
        out.addProperty("requestId", requestId);
        return out;
    }

    private static JsonObject failure(String requestId, String error, String conflict)
    {
        JsonObject out = success(requestId, 0, 0);
        out.addProperty(ActorProtocol.OK, false);
        out.addProperty(ActorProtocol.ERROR, error == null ? "" : error);

        if (conflict != null && !conflict.isEmpty())
        {
            out.addProperty("conflict", conflict);
        }

        return out;
    }

    private static final class Receipt
    {
        final JsonObject request;
        final JsonObject result;

        Receipt(JsonObject request, JsonObject result)
        {
            this.request = request;
            this.result = result;
        }
    }
}
