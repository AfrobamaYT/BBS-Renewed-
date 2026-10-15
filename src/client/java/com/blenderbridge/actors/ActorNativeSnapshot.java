package com.blenderbridge.actors;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.RigPropertyPath;
import mchorse.bbs_mod.obj.shapes.ShapeKeys;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;

/** Exact native per-path checkpoints for compensation, not a second wire decoder. */
public final class ActorNativeSnapshot
{
    private ActorNativeSnapshot()
    {}

    private static Replay replay(Film film, String uuid)
    {
        return film.replays.getList().stream().filter(r -> r.uuid.get().equals(uuid)).findFirst()
            .orElseThrow(() -> new IllegalStateException("undo actor no longer exists: " + uuid));
    }

    private static JsonElement data(BaseType value)
    {
        return value == null ? JsonNull.INSTANCE : new JsonPrimitive(DataToString.toString(value));
    }

    private static BaseType read(JsonElement value)
    {
        BaseType parsed = DataToString.fromString(value.getAsString());
        if (parsed == null)
        {
            throw new IllegalStateException("invalid native undo checkpoint");
        }
        return parsed;
    }

    public static JsonArray capture(Film film, JsonArray replays)
    {
        JsonArray result = new JsonArray();
        for (JsonElement element : replays)
        {
            JsonObject patch = element.getAsJsonObject();
            Replay replay = replay(film, patch.get("uuid").getAsString());
            JsonObject out = new JsonObject();
            out.addProperty("uuid", replay.uuid.get());
            out.addProperty("formClass", replay.form.get().getClass().getName());
            JsonObject values = new JsonObject();
            boolean firstShapeGrid = ShapeKeysPatch.createsFirstGrid(patch);
            for (String section : new String[] {"channels", "strings", "bones", "shapeKeys"})
            {
                if (!patch.has(section))
                {
                    continue;
                }
                if (section.equals("shapeKeys") && firstShapeGrid)
                {
                    continue;
                }
                for (String path : patch.getAsJsonObject(section).keySet())
                {
                    JsonElement saved;
                    if (section.equals("bones"))
                    {
                        saved = bone(replay, path);
                    }
                    else if (section.equals("shapeKeys"))
                    {
                        saved = shape(replay, path);
                    }
                    else
                    {
                        BaseValue channel = replay.keyframes.get(path);
                        if (channel == null)
                        {
                            throw new IllegalStateException("undo channel missing: " + path);
                        }
                        saved = data(channel.toData());
                    }
                    values.add(section + "/" + path, saved);
                }
            }
            if (patch.has(RigPropertyCurves.KEY))
            {
                JsonObject rig = patch.getAsJsonObject(RigPropertyCurves.KEY);

                for (String bone : rig.keySet())
                {
                    for (String property : rig.getAsJsonObject(bone).keySet())
                    {
                        String path = RigPropertyPath.key("", bone, property);
                        BaseValue channel = replay.properties.get(path);
                        values.add(RigPropertyCurves.KEY + "/" + path,
                            channel == null ? JsonNull.INSTANCE : data(channel.toData()));
                    }
                }
            }
            if (patch.has(ShapeTimelinePatch.SECTION))
            {
                values.add(ShapeTimelinePatch.SECTION, shapeTimeline(replay));
            }
            if (patch.has(ShapeGridPatch.SECTION) || firstShapeGrid)
            {
                BaseValue channel = replay.properties.properties.get("shape_keys");

                if (channel != null && (!(channel instanceof KeyframeChannel)
                    || ((KeyframeChannel) channel).getFactory() != KeyframeFactories.SHAPE_KEYS))
                {
                    throw new IllegalStateException("undo ShapeKey grid missing");
                }

                values.add(ShapeGridPatch.SECTION,
                    channel == null ? JsonNull.INSTANCE : data(channel.toData()));
            }
            if (patch.has(VisibilityPatch.SECTION))
            {
                values.add(VisibilityPatch.SECTION, data(replay.enabled.toData()));
            }
            out.add("values", values);
            result.add(out);
        }
        return result;
    }

    private static JsonArray shapeTimeline(Replay replay)
    {
        KeyframeChannel channel = replay.properties.properties.get("shape_keys");

        if (channel == null || channel.getKeyframes().isEmpty())
        {
            throw new IllegalStateException("undo ShapeKey timeline missing");
        }

        JsonArray frames = new JsonArray();

        for (Object object : channel.getKeyframes())
        {
            Keyframe key = (Keyframe) object;
            JsonObject frame = new JsonObject();
            frame.addProperty("tick", key.getTick());
            frame.addProperty("interp", key.getInterpolation().getKey());
            frame.addProperty("lx", key.lx);
            frame.addProperty("ly", key.ly);
            frame.addProperty("rx", key.rx);
            frame.addProperty("ry", key.ry);
            frames.add(frame);
        }

        return frames;
    }

    private static JsonElement shapeScalar(Float value)
    {
        if (value == null)
        {
            return JsonNull.INSTANCE;
        }

        MapType scalar = new MapType();
        scalar.putFloat("value", value);

        return data(scalar);
    }

    private static JsonArray shape(Replay replay, String name)
    {
        KeyframeChannel channel = replay.properties.properties.get("shape_keys");

        if (channel == null)
        {
            throw new IllegalStateException("undo ShapeKey channel missing: " + name);
        }

        JsonArray frames = new JsonArray();

        for (Object object : channel.getKeyframes())
        {
            Keyframe key = (Keyframe) object;

            if (!(key.getValue() instanceof ShapeKeys shapes))
            {
                throw new IllegalStateException("invalid native ShapeKey frame");
            }

            MapType shape = key.toData().asMap();
            shape.remove("value");
            JsonObject frame = new JsonObject();
            frame.add("shape", data(shape));
            frame.add("value", shapeScalar(shapes.shapeKeys.get(name)));
            frames.add(frame);
        }

        return frames;
    }

    private static JsonObject bone(Replay replay, String name)
    {
        JsonObject result = new JsonObject();
        BaseValue track = replay.properties.get("pose.bones." + name);
        result.add("track", data(track == null ? null : track.toData()));
        KeyframeChannel channel = replay.properties.properties.get("pose");
        if (channel == null)
        {
            result.add("pose", JsonNull.INSTANCE);
            return result;
        }
        JsonArray frames = new JsonArray();
        for (Object object : channel.getKeyframes())
        {
            Keyframe key = (Keyframe) object;
            Pose pose = (Pose) key.getValue();
            MapType shape = key.toData().asMap();
            shape.remove("value");
            JsonObject frame = new JsonObject();
            frame.add("shape", data(shape));
            PoseTransform transform = pose.transforms.get(name);
            frame.add("value", data(transform == null ? null : transform.toData()));
            frames.add(frame);
        }
        result.add("pose", frames);
        return result;
    }

    private static boolean same(JsonElement a, JsonElement b)
    {
        if (a.isJsonNull() || b.isJsonNull())
        {
            return a.equals(b);
        }
        if (a.isJsonPrimitive() && b.isJsonPrimitive())
        {
            JsonPrimitive first = a.getAsJsonPrimitive();
            JsonPrimitive second = b.getAsJsonPrimitive();

            /* ShapeKey timeline fields are native floats. Gson reparses the durable journal's
             * shortest round-trippable decimal as Double, so JsonPrimitive.equals() can reject
             * 0.65f versus the persisted 0.65 although both map to the same native float. */
            if (first.isNumber() && second.isNumber())
            {
                Number firstNumber = first.getAsNumber();
                Number secondNumber = second.getAsNumber();

                if (firstNumber instanceof Float || secondNumber instanceof Float)
                {
                    return Float.compare(first.getAsFloat(), second.getAsFloat()) == 0;
                }

                return first.equals(second);
            }
            if (!first.isString() || !second.isString())
            {
                return first.equals(second);
            }

            BaseType firstData = DataToString.fromString(first.getAsString());
            BaseType secondData = DataToString.fromString(second.getAsString());

            return firstData == null || secondData == null
                ? first.equals(second) : firstData.equals(secondData);
        }
        if (a.isJsonArray() && b.isJsonArray())
        {
            if (a.getAsJsonArray().size() != b.getAsJsonArray().size())
            {
                return false;
            }
            for (int i = 0; i < a.getAsJsonArray().size(); i++)
            {
                if (!same(a.getAsJsonArray().get(i), b.getAsJsonArray().get(i)))
                {
                    return false;
                }
            }
            return true;
        }
        if (!a.isJsonObject() || !b.isJsonObject() || !a.getAsJsonObject().keySet().equals(b.getAsJsonObject().keySet()))
        {
            return false;
        }
        for (String key : a.getAsJsonObject().keySet())
        {
            if (!same(a.getAsJsonObject().get(key), b.getAsJsonObject().get(key)))
            {
                return false;
            }
        }
        return true;
    }

    public static String conflict(Film film, JsonArray paths, JsonArray expected)
    {
        JsonArray actual = capture(film, paths);
        if (actual.size() != expected.size())
        {
            return "undo paths changed";
        }
        for (int i = 0; i < actual.size(); i++)
        {
            JsonObject now = actual.get(i).getAsJsonObject();
            JsonObject then = expected.get(i).getAsJsonObject();
            if (!now.get("uuid").equals(then.get("uuid")) || !now.get("formClass").equals(then.get("formClass")))
            {
                return "undo identity changed";
            }
            for (String path : now.getAsJsonObject("values").keySet())
            {
                if (!same(now.getAsJsonObject("values").get(path), then.getAsJsonObject("values").get(path)))
                {
                    return now.get("uuid").getAsString() + "/" + path;
                }
            }
        }
        return "";
    }

    public static void restore(Film film, JsonArray saved)
    {
        for (JsonElement element : saved)
        {
            JsonObject actor = element.getAsJsonObject();
            Replay replay = replay(film, actor.get("uuid").getAsString());
            JsonObject values = actor.getAsJsonObject("values");
            boolean restoresShapeTimeline = values.has(ShapeTimelinePatch.SECTION);

            if (restoresShapeTimeline)
            {
                restoreShapeTimeline(replay, values.getAsJsonArray(ShapeTimelinePatch.SECTION));
            }

            for (String key : values.keySet())
            {
                if (key.equals(ShapeTimelinePatch.SECTION))
                {
                    continue;
                }
                if (key.equals(ShapeGridPatch.SECTION))
                {
                    BaseValue channel = replay.properties.properties.get("shape_keys");

                    if (values.get(key).isJsonNull())
                    {
                        if (channel != null)
                        {
                            replay.properties.properties.remove("shape_keys");
                            replay.properties.remove(channel);
                        }

                        continue;
                    }
                    if (channel == null)
                    {
                        channel = replay.properties.registerChannel(
                            "shape_keys", KeyframeFactories.SHAPE_KEYS);
                    }
                    if (!(channel instanceof KeyframeChannel)
                        || ((KeyframeChannel) channel).getFactory() != KeyframeFactories.SHAPE_KEYS)
                    {
                        throw new IllegalStateException("shared ShapeKey grid unavailable");
                    }

                    channel.fromData(read(values.get(key)));
                    continue;
                }
                if (key.equals(VisibilityPatch.SECTION))
                {
                    replay.enabled.fromData(read(values.get(key)));
                    continue;
                }

                String[] split = key.split("/", 2);
                JsonElement value = values.get(key);
                if (split[0].equals("bones"))
                {
                    restoreBone(replay, split[1], value.getAsJsonObject());
                }
                else if (split[0].equals("shapeKeys"))
                {
                    restoreShape(replay, split[1], value.getAsJsonArray(), restoresShapeTimeline);
                }
                else if (split[0].equals(RigPropertyCurves.KEY))
                {
                    restoreRigProperty(replay, split[1], value);
                }
                else
                {
                    replay.keyframes.get(split[1]).fromData(read(value));
                }
            }
        }
    }

    private static void restoreRigProperty(Replay replay, String path, JsonElement saved)
    {
        BaseValue existing = replay.properties.get(path);

        if (saved.isJsonNull())
        {
            if (existing != null)
            {
                replay.properties.properties.remove(path);
                replay.properties.remove(existing);
            }

            return;
        }

        KeyframeChannel channel = replay.properties.getOrCreate(replay.form.get(), path);

        if (channel == null)
        {
            throw new IllegalStateException("shared rig property unavailable: " + path);
        }

        channel.fromData(read(saved));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void restoreShapeTimeline(Replay replay, JsonArray saved)
    {
        KeyframeChannel channel = replay.properties.properties.get("shape_keys");

        if (channel == null || channel.getKeyframes().size() != saved.size())
        {
            throw new IllegalStateException("shared ShapeKey timeline changed");
        }

        BaseValue.edit(channel, edited ->
        {
            for (int i = 0; i < saved.size(); i += 1)
            {
                Keyframe key = (Keyframe) edited.getKeyframes().get(i);
                JsonObject frame = saved.get(i).getAsJsonObject();
                IInterp interpolation = Interpolations.MAP.get(frame.get("interp").getAsString());

                if (interpolation == null)
                {
                    throw new IllegalStateException("invalid saved ShapeKey timeline interpolation");
                }

                key.setTick(frame.get("tick").getAsFloat(), false);
                key.getInterpolation().setInterp(interpolation);
                key.lx = frame.get("lx").getAsFloat();
                key.ly = frame.get("ly").getAsFloat();
                key.rx = frame.get("rx").getAsFloat();
                key.ry = frame.get("ry").getAsFloat();
            }
        });
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void restoreShape(Replay replay, String name, JsonArray saved,
        boolean restoresShapeTimeline)
    {
        KeyframeChannel channel = replay.properties.properties.get("shape_keys");

        if (channel == null || channel.getKeyframes().size() != saved.size())
        {
            throw new IllegalStateException("shared ShapeKey timing changed");
        }

        for (int i = 0; i < saved.size(); i += 1)
        {
            Keyframe key = (Keyframe) channel.getKeyframes().get(i);
            JsonObject frame = saved.get(i).getAsJsonObject();
            MapType currentShape = key.toData().asMap();
            currentShape.remove("value");

            if (!restoresShapeTimeline && !same(data(currentShape), frame.get("shape")))
            {
                throw new IllegalStateException("shared ShapeKey timing changed");
            }

            if (!(key.getValue() instanceof ShapeKeys current))
            {
                throw new IllegalStateException("invalid native ShapeKey frame");
            }

            ShapeKeys shapes = current.copy();
            JsonElement scalar = frame.get("value");

            if (scalar.isJsonNull())
            {
                shapes.shapeKeys.remove(name);
            }
            else
            {
                shapes.shapeKeys.put(name, read(scalar).asMap().getFloat("value"));
            }

            key.setValue(shapes, true);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void restoreBone(Replay replay, String name, JsonObject saved)
    {
        String path = "pose.bones." + name;
        if (saved.get("track").isJsonNull())
        {
            BaseValue existing = replay.properties.get(path);

            if (existing != null)
            {
                replay.properties.properties.remove(path);
                replay.properties.remove(existing);
            }
        }
        else
        {
            replay.properties.getOrCreate(replay.form.get(), path).fromData(read(saved.get("track")));
        }
        if (saved.get("pose").isJsonNull())
        {
            return;
        }
        KeyframeChannel channel = replay.properties.properties.get("pose");
        JsonArray frames = saved.getAsJsonArray("pose");
        if (channel == null || channel.getKeyframes().size() != frames.size())
        {
            throw new IllegalStateException("shared pose timing changed");
        }
        for (int i = 0; i < frames.size(); i++)
        {
            Keyframe key = (Keyframe) channel.getKeyframes().get(i);
            Pose pose = ((Pose) key.getValue()).copy();
            JsonElement value = frames.get(i).getAsJsonObject().get("value");
            if (value.isJsonNull())
            {
                pose.transforms.remove(name);
            }
            else
            {
                PoseTransform transform = new PoseTransform();
                transform.fromData(read(value).asMap());
                pose.transforms.put(name, transform);
            }
            key.setValue(pose, true);
        }
    }
}
