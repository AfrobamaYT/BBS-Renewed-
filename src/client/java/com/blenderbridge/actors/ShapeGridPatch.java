package com.blenderbridge.actors;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.obj.shapes.ShapeKeys;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

import java.util.Map;

/** Atomic full replacement of BBS' one physical, vector-valued ShapeKey grid. */
final class ShapeGridPatch
{
    static final String SECTION = "shapeGrid";

    private ShapeGridPatch()
    {}

    static ActorChannelPatchComplexValidation.Result check(String uuid, JsonObject patch,
        JsonObject expected, JsonObject properties)
    {
        JsonElement desiredRaw = patch.get(SECTION);

        if (desiredRaw == null)
        {
            return ActorChannelPatchComplexValidation.Result.empty();
        }
        if (!desiredRaw.isJsonObject())
        {
            return ActorChannelPatchComplexValidation.Result.error(
                "shapeGrid must be an object", uuid + "/" + SECTION);
        }

        JsonElement expectedRaw = expected.get(SECTION);

        if (expectedRaw == null || !expectedRaw.isJsonObject())
        {
            return ActorChannelPatchComplexValidation.Result.error(
                "missing ShapeKey grid precondition", uuid + "/" + SECTION);
        }

        JsonObject desired = desiredRaw.getAsJsonObject();
        JsonObject before = expectedRaw.getAsJsonObject();
        JsonObject current = objectOrEmpty(properties.get(ShapeKeysPatch.SECTION));
        String invalid = validate(desired, before, current);

        if (!invalid.isEmpty())
        {
            return ActorChannelPatchComplexValidation.Result.error(
                invalid, uuid + "/" + SECTION);
        }

        JsonElement canonicalCurrent = ActorChannelPatchCanonical.value(current);
        JsonElement canonicalDesired = ActorChannelPatchCanonical.value(desired);
        JsonElement canonicalExpected = ActorChannelPatchCanonical.value(before);

        if (canonicalCurrent.equals(canonicalDesired))
        {
            return ActorChannelPatchComplexValidation.Result.ok(true, false);
        }
        if (!canonicalCurrent.equals(canonicalExpected))
        {
            return ActorChannelPatchComplexValidation.Result.error(
                "precondition failed", uuid + "/" + SECTION);
        }

        return ActorChannelPatchComplexValidation.Result.ok(true, true);
    }

    private static String validate(JsonObject desired, JsonObject expected, JsonObject current)
    {
        if (desired.size() == 0 || !desired.keySet().equals(expected.keySet())
            || !desired.keySet().equals(current.keySet()))
        {
            return "ShapeKey grid replacement must contain every existing morph exactly once";
        }

        JsonArray desiredGrid = null;
        JsonArray expectedGrid = null;

        for (Map.Entry<String, JsonElement> entry : desired.entrySet())
        {
            String name = entry.getKey();

            if (name.isEmpty() || name.length() > 256)
            {
                return "invalid ShapeKey grid morph name";
            }
            String desiredError = ShapeKeysPatch.validateCurve(entry.getValue());
            String expectedError = ShapeKeysPatch.validateCurve(expected.get(name));

            if (!desiredError.isEmpty() || !expectedError.isEmpty())
            {
                return !desiredError.isEmpty() ? desiredError : "invalid ShapeKey grid precondition";
            }

            JsonArray desiredCurve = entry.getValue().getAsJsonArray();
            JsonArray expectedCurve = expected.get(name).getAsJsonArray();

            if (desiredGrid == null)
            {
                desiredGrid = desiredCurve;
                expectedGrid = expectedCurve;
            }
            else if (!ShapeKeysPatch.sameGrid(desiredCurve, desiredGrid)
                || !ShapeKeysPatch.sameGrid(expectedCurve, expectedGrid))
            {
                return "ShapeKey grid morphs must share one complete timeline";
            }
        }

        return "";
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static int apply(JsonObject replayObject, Replay replay)
    {
        JsonElement raw = replayObject.get(SECTION);

        if (raw == null || !raw.isJsonObject())
        {
            return 0;
        }

        BaseValue value = replay.properties.properties.get("shape_keys");

        if (!(value instanceof KeyframeChannel channel)
            || channel.getFactory() != KeyframeFactories.SHAPE_KEYS)
        {
            return 0;
        }

        JsonObject desired = raw.getAsJsonObject();
        JsonArray template = desired.entrySet().iterator().next().getValue().getAsJsonArray();
        KeyframeChannel<ShapeKeys> replacement = new KeyframeChannel<>(
            "shape_keys", KeyframeFactories.SHAPE_KEYS);

        for (int i = 0; i < template.size(); i += 1)
        {
            JsonObject metadata = template.get(i).getAsJsonObject();
            ShapeKeys shapes = new ShapeKeys();

            for (Map.Entry<String, JsonElement> entry : desired.entrySet())
            {
                shapes.shapeKeys.put(entry.getKey(), entry.getValue().getAsJsonArray()
                    .get(i).getAsJsonObject().get(ActorProtocol.KF_VALUE).getAsFloat());
            }

            int index = replacement.insert(
                metadata.get(ActorProtocol.KF_TICK).getAsFloat(), shapes);
            Keyframe<ShapeKeys> frame = replacement.get(index);
            frame.getInterpolation().setInterp(Interpolations.MAP.get(
                metadata.get(ActorProtocol.KF_INTERP).getAsString()));
            frame.lx = metadata.get(ActorProtocol.KF_LX).getAsFloat();
            frame.ly = metadata.get(ActorProtocol.KF_LY).getAsFloat();
            frame.rx = metadata.get(ActorProtocol.KF_RX).getAsFloat();
            frame.ry = metadata.get(ActorProtocol.KF_RY).getAsFloat();
        }

        BaseValue.edit(channel, edited -> edited.copyKeyframes(replacement));

        return 1;
    }

    private static JsonObject objectOrEmpty(JsonElement element)
    {
        return element != null && element.isJsonObject()
            ? element.getAsJsonObject() : new JsonObject();
    }
}
