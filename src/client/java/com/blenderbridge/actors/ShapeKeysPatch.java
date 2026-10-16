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

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Value-only per-morph patches for BBS' shared shape_keys channel. */
final class ShapeKeysPatch
{
    static final String SECTION = "shapeKeys";
    private static final Set<String> KEY_FIELDS = Set.of(
        ActorProtocol.KF_TICK, ActorProtocol.KF_VALUE, ActorProtocol.KF_INTERP,
        ActorProtocol.KF_LX, ActorProtocol.KF_LY, ActorProtocol.KF_RX, ActorProtocol.KF_RY);

    private ShapeKeysPatch()
    {
    }

    static boolean createsFirstGrid(JsonObject patch)
    {
        JsonElement desiredRaw = patch.get(SECTION);
        JsonElement expectedRaw = patch.get("expected");
        if (desiredRaw == null || !desiredRaw.isJsonObject() || desiredRaw.getAsJsonObject().size() == 0
            || expectedRaw == null || !expectedRaw.isJsonObject())
        {
            return false;
        }
        JsonElement expectedShapesRaw = expectedRaw.getAsJsonObject().get(SECTION);
        if (expectedShapesRaw == null || !expectedShapesRaw.isJsonObject())
        {
            return false;
        }
        JsonObject desired = desiredRaw.getAsJsonObject();
        JsonObject expected = expectedShapesRaw.getAsJsonObject();
        if (!desired.keySet().equals(expected.keySet()))
        {
            return false;
        }
        return desired.entrySet().stream().allMatch(entry -> entry.getValue().isJsonArray()
            && expected.get(entry.getKey()).isJsonNull());
    }

    static String validateDesired(JsonElement desired, JsonElement expected, JsonObject currentShapes)
    {
        boolean absent = expected == null || expected.isJsonNull();

        if (desired == null || desired.isJsonNull())
        {
            if (absent)
            {
                return "cannot remove an absent ShapeKey morph";
            }

            return expected.isJsonArray()
                ? ""
                : "ShapeKey precondition must be a keyframe array";
        }

        String invalid = validateCurve(desired);

        if (!invalid.isEmpty())
        {
            return invalid;
        }

        JsonArray desiredArray = desired.getAsJsonArray();

        if (absent)
        {
            JsonArray template = sharedGrid(currentShapes);

            return template == null || sameGrid(desiredArray, template)
                ? ""
                : "new ShapeKey morph grid differs from the existing shared key grid";
        }
        if (!expected.isJsonArray())
        {
            return "ShapeKey precondition must be a keyframe array";
        }

        return sameGrid(desiredArray, expected.getAsJsonArray())
            ? ""
            : "ShapeKey tick/interpolation/handle edits are not enabled yet";
    }

    static String validateCurve(JsonElement desired)
    {
        String invalid = ActorChannelPatchStructuredValues.validateNumericArray(desired);

        if (!invalid.isEmpty())
        {
            return invalid;
        }

        JsonArray desiredArray = desired.getAsJsonArray();

        if (desiredArray.size() == 0)
        {
            return "empty ShapeKey curve must use null removal";
        }

        double previousTick = -1D;

        for (JsonElement raw : desiredArray)
        {
            JsonObject keyframe = raw.getAsJsonObject();

            if (!keyframe.keySet().equals(KEY_FIELDS))
            {
                return "unsupported ShapeKey keyframe field";
            }

            double value = keyframe.get(ActorProtocol.KF_VALUE).getAsDouble();

            if (value < 0D || value > 1D)
            {
                return "ShapeKey values must be in [0,1]";
            }
            double tick = keyframe.get(ActorProtocol.KF_TICK).getAsDouble();
            String interp = keyframe.get(ActorProtocol.KF_INTERP).getAsString();

            if (tick < 0D || tick <= previousTick)
            {
                return "ShapeKey ticks must be non-negative and strictly increasing";
            }
            if (!Interpolations.MAP.containsKey(interp))
            {
                return "unsupported ShapeKey interpolation";
            }
            if (keyframe.get(ActorProtocol.KF_LX).getAsDouble() < 0D
                || keyframe.get(ActorProtocol.KF_RX).getAsDouble() < 0D)
            {
                return "ShapeKey handle distances must be non-negative";
            }
            previousTick = tick;
        }

        return "";
    }

    private static JsonArray sharedGrid(JsonObject shapes)
    {
        for (Map.Entry<String, JsonElement> entry : shapes.entrySet())
        {
            if (entry.getValue().isJsonArray() && entry.getValue().getAsJsonArray().size() > 0)
            {
                return entry.getValue().getAsJsonArray();
            }
        }

        return null;
    }

    static boolean sameGrid(JsonArray left, JsonArray right)
    {
        if (left.size() != right.size())
        {
            return false;
        }

        for (int i = 0; i < left.size(); i += 1)
        {
            if (!left.get(i).isJsonObject() || !right.get(i).isJsonObject())
            {
                return false;
            }

            JsonElement a = ActorChannelPatchCanonical.value(grid(left.get(i).getAsJsonObject()));
            JsonElement b = ActorChannelPatchCanonical.value(grid(right.get(i).getAsJsonObject()));

            if (!a.equals(b))
            {
                return false;
            }
        }

        return true;
    }

    private static JsonObject grid(JsonObject keyframe)
    {
        JsonObject out = new JsonObject();

        for (String key : new String[] {
            ActorProtocol.KF_TICK, ActorProtocol.KF_INTERP,
            ActorProtocol.KF_LX, ActorProtocol.KF_LY, ActorProtocol.KF_RX, ActorProtocol.KF_RY})
        {
            if (keyframe.has(key))
            {
                out.add(key, keyframe.get(key).deepCopy());
            }
        }

        return out;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static int apply(JsonObject replayObject, Replay replay)
    {
        JsonElement raw = replayObject.get(SECTION);

        if (raw == null || !raw.isJsonObject())
        {
            return 0;
        }

        JsonObject desired = raw.getAsJsonObject();

        if (desired.size() == 0)
        {
            return 0;
        }

        BaseValue value = replay.properties.properties.get("shape_keys");
        KeyframeChannel channel;

        if (value == null)
        {
            JsonArray template = null;

            for (JsonElement element : desired.entrySet().stream()
                .map(Map.Entry::getValue).toList())
            {
                if (element != null && element.isJsonArray())
                {
                    template = element.getAsJsonArray();
                    break;
                }
            }

            if (template == null || template.isEmpty())
            {
                return 0;
            }

            replay.properties.preNotify();
            channel = replay.properties.registerChannel("shape_keys", KeyframeFactories.SHAPE_KEYS);

            for (int i = 0; i < template.size(); i += 1)
            {
                JsonObject metadata = template.get(i).getAsJsonObject();
                ShapeKeys shapes = new ShapeKeys();

                for (Map.Entry<String, JsonElement> entry : desired.entrySet())
                {
                    if (entry.getValue() != null && entry.getValue().isJsonArray())
                    {
                        shapes.shapeKeys.put(entry.getKey(), entry.getValue().getAsJsonArray()
                            .get(i).getAsJsonObject().get(ActorProtocol.KF_VALUE).getAsFloat());
                    }
                }

                int index = channel.insert(metadata.get(ActorProtocol.KF_TICK).getAsFloat(), shapes);
                Keyframe frame = channel.get(index);
                frame.getInterpolation().setInterp(Interpolations.MAP.get(
                    metadata.get(ActorProtocol.KF_INTERP).getAsString()));
                frame.lx = metadata.get(ActorProtocol.KF_LX).getAsFloat();
                frame.ly = metadata.get(ActorProtocol.KF_LY).getAsFloat();
                frame.rx = metadata.get(ActorProtocol.KF_RX).getAsFloat();
                frame.ry = metadata.get(ActorProtocol.KF_RY).getAsFloat();
            }

            replay.properties.postNotify();
            return desired.size();
        }
        if (!(value instanceof KeyframeChannel existing)
            || existing.getFactory() != KeyframeFactories.SHAPE_KEYS)
        {
            return 0;
        }

        channel = existing;

        BaseValue.edit(channel, (edited) ->
        {
            List<?> frames = edited.getKeyframes();

            for (int i = 0; i < frames.size(); i += 1)
            {
                Keyframe frame = (Keyframe) frames.get(i);
                Object current = frame.getValue();
                ShapeKeys shapes = current instanceof ShapeKeys keys ? keys.copy() : new ShapeKeys();

                for (Map.Entry<String, JsonElement> entry : desired.entrySet())
                {
                    JsonElement curve = entry.getValue();

                    if (curve == null || curve.isJsonNull())
                    {
                        shapes.shapeKeys.remove(entry.getKey());
                    }
                    else
                    {
                        JsonArray points = curve.getAsJsonArray();
                        float scalar = points.get(i).getAsJsonObject()
                            .get(ActorProtocol.KF_VALUE).getAsFloat();
                        shapes.shapeKeys.put(entry.getKey(), scalar);
                    }
                }

                frame.setValue(shapes, false);
            }
        });

        return desired.size();
    }
}
