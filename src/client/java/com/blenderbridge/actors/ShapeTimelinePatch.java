package com.blenderbridge.actors;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import java.util.Map;
import java.util.Set;
/** Conditional edits of the one physical timeline shared by all ShapeKey morph values. */
final class ShapeTimelinePatch
{
    static final String SECTION = "shapeTimeline";
    private static final Set<String> FIELDS = Set.of("tick", "interp", "lx", "ly", "rx", "ry");
    private static final Set<String> EDITABLE_INTERPS = Set.of(
        "linear", "constant", "bezier",
        "sine_in", "sine_out", "sine_inout",
        "circle_in", "circle_out", "circle_inout",
        "quad_in", "quad_out", "quad_inout",
        "cubic_in", "cubic_out", "cubic_inout",
        "quart_in", "quart_out", "quart_inout",
        "quint_in", "quint_out", "quint_inout",
        "back_in", "back_out");
    private ShapeTimelinePatch()
    {}
    static ActorChannelPatchComplexValidation.Result check(String uuid, JsonObject patch,
        JsonObject expected, JsonObject properties)
    {
        JsonElement desiredRaw = patch.get(SECTION);
        if (desiredRaw == null)
        {
            return ActorChannelPatchComplexValidation.Result.empty();
        }
        if (!desiredRaw.isJsonArray())
        {
            return ActorChannelPatchComplexValidation.Result.error(
                "shapeTimeline must be an array", uuid + "/" + SECTION);
        }
        JsonElement expectedRaw = expected.get(SECTION);
        if (expectedRaw == null || !expectedRaw.isJsonArray())
        {
            return ActorChannelPatchComplexValidation.Result.error(
                "missing ShapeKey timeline precondition", uuid + "/" + SECTION);
        }
        JsonArray desired = desiredRaw.getAsJsonArray();
        JsonArray expectedTimeline = expectedRaw.getAsJsonArray();
        JsonArray current;
        try
        {
            current = fromProperties(properties);
        }
        catch (IllegalArgumentException exception)
        {
            return ActorChannelPatchComplexValidation.Result.error(
                exception.getMessage(), uuid + "/" + SECTION);
        }

        String invalid = validateDesired(desired, expectedTimeline);

        if (!invalid.isEmpty())
        {
            return ActorChannelPatchComplexValidation.Result.error(
                invalid, uuid + "/" + SECTION);
        }

        JsonElement canonicalCurrent = ActorChannelPatchCanonical.value(current);
        JsonElement canonicalExpected = ActorChannelPatchCanonical.value(expectedTimeline);
        JsonElement canonicalDesired = ActorChannelPatchCanonical.value(desired);

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

    private static String validateDesired(JsonArray desired, JsonArray expected)
    {
        if (desired.size() == 0 || desired.size() != expected.size())
        {
            return "ShapeKey timeline key count edits are not enabled yet";
        }

        double previousTick = -1D;

        for (int i = 0; i < desired.size(); i += 1)
        {
            if (!desired.get(i).isJsonObject() || !expected.get(i).isJsonObject())
            {
                return "ShapeKey timeline keys must be objects";
            }

            JsonObject after = desired.get(i).getAsJsonObject();
            JsonObject before = expected.get(i).getAsJsonObject();

            if (!after.keySet().equals(FIELDS) || !before.keySet().equals(FIELDS))
            {
                return "invalid ShapeKey timeline key fields";
            }

            String interp;

            try
            {
                interp = after.get("interp").getAsString();
                for (String field : new String[] {"tick", "lx", "ly", "rx", "ry"})
                {
                    double a = after.get(field).getAsDouble();
                    double b = before.get(field).getAsDouble();

                    if (!Double.isFinite(a) || !Double.isFinite(b))
                    {
                        return "ShapeKey timeline needs finite ticks/handles";
                    }
                }
            }
            catch (Exception exception)
            {
                return "invalid ShapeKey timeline key";
            }

            double tick = after.get("tick").getAsDouble();

            if (tick < 0D || tick <= previousTick)
            {
                return "ShapeKey timeline ticks must be non-negative and strictly increasing";
            }
            if (after.get("lx").getAsDouble() < 0D || after.get("rx").getAsDouble() < 0D)
            {
                return "ShapeKey timeline handle distances must be non-negative";
            }
            previousTick = tick;

            if (!EDITABLE_INTERPS.contains(interp))
            {
                return "unsupported exact ShapeKey timeline interpolation";
            }
        }

        return "";
    }

    private static JsonArray fromProperties(JsonObject properties)
    {
        JsonElement raw = properties.get(ShapeKeysPatch.SECTION);

        if (raw == null || !raw.isJsonObject() || raw.getAsJsonObject().size() == 0)
        {
            throw new IllegalArgumentException("ShapeKey timeline requires an existing shared grid");
        }

        JsonArray timeline = null;

        for (Map.Entry<String, JsonElement> entry : raw.getAsJsonObject().entrySet())
        {
            if (!entry.getValue().isJsonArray() || entry.getValue().getAsJsonArray().size() == 0)
            {
                continue;
            }

            JsonArray candidate = metadata(entry.getValue().getAsJsonArray());

            if (timeline == null)
            {
                timeline = candidate;
            }
            else if (!ActorChannelPatchCanonical.value(timeline)
                .equals(ActorChannelPatchCanonical.value(candidate)))
            {
                throw new IllegalArgumentException(
                    "ShapeKey morphs disagree on the shared timeline");
            }
        }

        if (timeline == null)
        {
            throw new IllegalArgumentException("ShapeKey timeline requires an existing shared grid");
        }

        return timeline;
    }

    private static JsonArray metadata(JsonArray curve)
    {
        JsonArray out = new JsonArray();

        for (JsonElement raw : curve)
        {
            if (!raw.isJsonObject())
            {
                throw new IllegalArgumentException("invalid ShapeKey timeline key");
            }

            JsonObject source = raw.getAsJsonObject();
            JsonObject key = new JsonObject();

            for (String field : FIELDS)
            {
                if (!source.has(field))
                {
                    throw new IllegalArgumentException(
                        "ShapeKey timeline metadata is incomplete");
                }
                key.add(field, source.get(field).deepCopy());
            }

            out.add(key);
        }

        return out;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static int apply(JsonObject replayObject, Replay replay)
    {
        JsonElement raw = replayObject.get(SECTION);

        if (raw == null || !raw.isJsonArray())
        {
            return 0;
        }

        BaseValue value = replay.properties.properties.get("shape_keys");

        if (!(value instanceof KeyframeChannel channel)
            || channel.getFactory() != KeyframeFactories.SHAPE_KEYS)
        {
            return 0;
        }

        JsonArray desired = raw.getAsJsonArray();

        if (channel.getKeyframes().size() != desired.size())
        {
            throw new IllegalStateException("shared ShapeKey timeline changed during apply");
        }

        BaseValue.edit(channel, edited ->
        {
            for (int i = 0; i < desired.size(); i += 1)
            {
                Keyframe key = (Keyframe) edited.getKeyframes().get(i);
                String name = desired.get(i).getAsJsonObject().get("interp").getAsString();
                IInterp interp = Interpolations.MAP.get(name);

                if (interp == null || !EDITABLE_INTERPS.contains(name))
                {
                    throw new IllegalArgumentException(
                        "unsupported ShapeKey timeline interpolation");
                }

                key.getInterpolation().setInterp(interp);
                JsonObject frame = desired.get(i).getAsJsonObject();
                key.setTick(frame.get("tick").getAsFloat(), false);
                key.lx = frame.get("lx").getAsFloat();
                key.ly = frame.get("ly").getAsFloat();
                key.rx = frame.get("rx").getAsFloat();
                key.ry = frame.get("ry").getAsFloat();
            }
        });

        return 1;
    }
}
