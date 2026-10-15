package com.blenderbridge.actors;

import com.blenderbridge.BridgeProtocol;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Preflight validation for {@link ActorChannelPatch}; performs no mutations. */
final class ActorChannelPatchValidation
{
    private ActorChannelPatchValidation()
    {
    }

    static Result check(JsonObject fields, JsonArray currentReplays)
    {
        JsonElement raw = fields.get(ActorProtocol.REPLAYS);

        if (raw == null || !raw.isJsonArray())
        {
            return Result.error("no replays in payload", "");
        }
        JsonArray replays = raw.getAsJsonArray();
        Set<String> seen = new HashSet<>();
        boolean needsWrite = false;

        for (JsonElement element : replays)
        {
            if (!element.isJsonObject())
            {
                return Result.error("replay entry must be an object", "");
            }

            JsonObject patch = element.getAsJsonObject();
            String uuid = BridgeProtocol.getFieldString(patch, ActorProtocol.REPLAY_UUID, "");

            if (uuid.isEmpty() || !seen.add(uuid))
            {
                return Result.error("missing or duplicate replay uuid", uuid + "/replay");
            }
            JsonObject current = findReplay(currentReplays, uuid);

            if (current == null)
            {
                return Result.error("replay no longer exists", uuid + "/replay");
            }

            JsonElement expectedRaw = patch.get("expected");

            if (expectedRaw == null || !expectedRaw.isJsonObject())
            {
                return Result.error("missing expected preconditions", uuid + "/expected");
            }

            JsonObject expected = expectedRaw.getAsJsonObject();

            Section channels = checkSection(uuid, "channels", patch, expected, current, true);

            if (!channels.error.isEmpty())
            {
                return Result.error(channels.error, channels.conflict);
            }

            Section strings = checkSection(uuid, StringChannels.STRINGS, patch, expected, current, false);

            if (!strings.error.isEmpty())
            {
                return Result.error(strings.error, strings.conflict);
            }

            ActorChannelPatchComplexValidation.Result complex =
                ActorChannelPatchComplexValidation.check(uuid, patch, expected, current);

            if (!complex.error.isEmpty())
            {
                return Result.error(complex.error, complex.conflict);
            }
            if (!channels.seen && !strings.seen && !complex.seen)
            {
                return Result.error("conditional replay has no protected changes", uuid + "/empty");
            }

            needsWrite |= channels.needsWrite || strings.needsWrite || complex.needsWrite;
        }

        return Result.ok(needsWrite);
    }

    private static Section checkSection(String uuid, String section, JsonObject patch,
        JsonObject expected, JsonObject current, boolean numeric)
    {
        JsonElement desiredRaw = patch.get(section);

        if (desiredRaw == null)
        {
            return Section.empty();
        }
        if (!desiredRaw.isJsonObject())
        {
            return Section.error("section must be an object", uuid + "/" + section);
        }

        JsonElement expectedRaw = expected.get(section);

        if (expectedRaw == null || !expectedRaw.isJsonObject())
        {
            return Section.error("missing section preconditions", uuid + "/" + section);
        }

        JsonObject desired = desiredRaw.getAsJsonObject();
        JsonObject expectedSection = expectedRaw.getAsJsonObject();
        JsonObject currentSection = objectOrEmpty(current.get(section));
        boolean needsWrite = false;
        for (Map.Entry<String, JsonElement> entry : desired.entrySet())
        {
            String key = entry.getKey();
            String path = uuid + "/" + section + "/" + key;
            String invalid = ActorChannelPatchValues.validate(key, entry.getValue(), numeric);

            if (!invalid.isEmpty())
            {
                return Section.error(invalid, path);
            }
            if (!expectedSection.has(key))
            {
                return Section.error("missing channel precondition", path);
            }

            JsonElement currentValue = ActorChannelPatchCanonical.value(
                currentSection.has(key) ? currentSection.get(key) : JsonNull.INSTANCE);
            JsonElement desiredValue = ActorChannelPatchCanonical.value(entry.getValue());
            JsonElement expectedValue = ActorChannelPatchCanonical.value(expectedSection.get(key));

            if (currentValue.equals(desiredValue))
            {
                continue;
            }
            if (!currentValue.equals(expectedValue))
            {
                return Section.error("precondition failed", path);
            }

            needsWrite = true;
        }

        return Section.ok(!desired.entrySet().isEmpty(), needsWrite);
    }

    private static JsonObject findReplay(JsonArray replays, String uuid)
    {
        for (JsonElement element : replays)
        {
            if (element.isJsonObject()
                && uuid.equals(BridgeProtocol.getFieldString(element.getAsJsonObject(),
                    ActorProtocol.REPLAY_UUID, "")))
            {
                return element.getAsJsonObject();
            }
        }

        return null;
    }

    private static JsonObject objectOrEmpty(JsonElement element)
    {
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
    }

    static final class Result
    {
        final String error;
        final String conflict;
        final boolean needsWrite;

        private Result(String error, String conflict, boolean needsWrite)
        {
            this.error = error;
            this.conflict = conflict;
            this.needsWrite = needsWrite;
        }

        static Result ok(boolean needsWrite)
        {
            return new Result("", "", needsWrite);
        }

        static Result error(String error, String conflict)
        {
            return new Result(error, conflict, false);
        }
    }

    private static final class Section
    {
        final String error;
        final String conflict;
        final boolean seen;
        final boolean needsWrite;

        private Section(String error, String conflict, boolean seen, boolean needsWrite)
        {
            this.error = error;
            this.conflict = conflict;
            this.seen = seen;
            this.needsWrite = needsWrite;
        }

        static Section empty()
        {
            return new Section("", "", false, false);
        }

        static Section ok(boolean seen, boolean needsWrite)
        {
            return new Section("", "", seen, needsWrite);
        }

        static Section error(String error, String conflict)
        {
            return new Section(error, conflict, true, false);
        }
    }
}
