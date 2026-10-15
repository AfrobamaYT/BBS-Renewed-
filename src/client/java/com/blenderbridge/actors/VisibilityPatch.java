package com.blenderbridge.actors;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import mchorse.bbs_mod.film.replays.Replay;

/** Protected static mapping between BBS replay.enabled and Blender actor visibility. */
final class VisibilityPatch
{
    static final String SECTION = "visibility";
    static final String PATH = "enabled";

    private VisibilityPatch()
    {}

    static ActorChannelPatchComplexValidation.Result check(String uuid, JsonObject patch,
        JsonObject expected, JsonObject current)
    {
        JsonElement desired = patch.get(SECTION);
        if (desired == null)
        {
            return ActorChannelPatchComplexValidation.Result.empty();
        }
        JsonElement before = expected.get(SECTION);
        JsonElement now = current.get(ActorProtocol.REPLAY_ENABLED);
        if (!booleanValue(desired) || !booleanValue(before) || !booleanValue(now))
        {
            return ActorChannelPatchComplexValidation.Result.error(
                "visibility needs boolean desired/current/precondition", uuid + "/" + SECTION);
        }
        if (now.getAsBoolean() == desired.getAsBoolean())
        {
            return ActorChannelPatchComplexValidation.Result.ok(true, false);
        }
        if (now.getAsBoolean() != before.getAsBoolean())
        {
            return ActorChannelPatchComplexValidation.Result.error(
                "precondition failed", uuid + "/" + SECTION);
        }
        return ActorChannelPatchComplexValidation.Result.ok(true, true);
    }

    static int apply(JsonObject patch, Replay replay)
    {
        JsonElement desired = patch.get(SECTION);
        if (!booleanValue(desired) || replay.enabled.get() == desired.getAsBoolean())
        {
            return 0;
        }
        replay.enabled.set(desired.getAsBoolean());
        return 1;
    }

    private static boolean booleanValue(JsonElement value)
    {
        return value != null && value.isJsonPrimitive()
            && value.getAsJsonPrimitive().isBoolean();
    }
}
