package com.blenderbridge.actors;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.RigPropertyPath;
import mchorse.bbs_mod.settings.values.base.BaseValue;

import java.util.LinkedHashSet;
import java.util.Set;

/** Persistent per-protected-path native edit generations; values are not duplicated here. */
public final class ActorNativeRevisions
{
    private static final int LIMIT = 4096;

    private ActorNativeRevisions()
    {}

    private static JsonObject data(Film film)
    {
        return film.bridgePathRevisions.isEmpty() ? new JsonObject()
            : JsonParser.parseString(film.bridgePathRevisions).getAsJsonObject();
    }

    private static Set<String> paths(JsonArray replays)
    {
        Set<String> result = new LinkedHashSet<>();

        for (JsonElement element : replays)
        {
            if (!element.isJsonObject())
            {
                throw new IllegalArgumentException("revision replay must be an object");
            }

            JsonObject replay = element.getAsJsonObject();
            String uuid = replay.has("uuid") ? replay.get("uuid").getAsString() : "";

            if (uuid.isEmpty())
            {
                throw new IllegalArgumentException("revision replay requires uuid");
            }

            for (String section : new String[] {"channels", "strings", "bones", "shapeKeys"})
            {
                if (!replay.has(section))
                {
                    continue;
                }

                JsonObject values = replay.getAsJsonObject(section);

                for (String path : values.keySet())
                {
                    if ((section.equals("channels") && !ActorLeases.ROOT.contains(path))
                        || (section.equals("strings") && !ActorLeases.ITEMS.contains(path))
                        || ((section.equals("bones") || section.equals("shapeKeys"))
                            && (path.isEmpty() || path.length() > 256)))
                    {
                        throw new IllegalArgumentException("unsupported revision path");
                    }

                    result.add(ActorLeases.key(uuid, section, path));
                }
            }

            if (replay.has(RigPropertyCurves.KEY))
            {
                JsonObject rig = replay.getAsJsonObject(RigPropertyCurves.KEY);

                for (String bone : rig.keySet())
                {
                    for (String property : rig.getAsJsonObject(bone).keySet())
                    {
                        result.add(ActorLeases.key(uuid, RigPropertyCurves.KEY,
                            RigPropertyPath.key("", bone, property)));
                    }
                }
            }

            if (replay.has(ShapeTimelinePatch.SECTION))
            {
                if (!replay.get(ShapeTimelinePatch.SECTION).isJsonArray())
                {
                    throw new IllegalArgumentException("shapeTimeline revision path must be an array");
                }

                result.add(uuid + "/" + ShapeTimelinePatch.SECTION);
            }
            if (replay.has(ShapeGridPatch.SECTION))
            {
                if (!replay.get(ShapeGridPatch.SECTION).isJsonObject())
                {
                    throw new IllegalArgumentException("shapeGrid revision path must be an object");
                }

                result.add(uuid + "/" + ShapeGridPatch.SECTION);
            }
            if (replay.has(VisibilityPatch.SECTION))
            {
                if (!replay.get(VisibilityPatch.SECTION).isJsonPrimitive()
                    || !replay.getAsJsonPrimitive(VisibilityPatch.SECTION).isBoolean())
                {
                    throw new IllegalArgumentException("visibility revision path must be boolean");
                }

                result.add(ActorLeases.key(
                    uuid, VisibilityPatch.SECTION, VisibilityPatch.PATH));
            }
        }

        return result;
    }

    public static JsonObject capture(Film film, JsonArray replays)
    {
        JsonObject revisions = data(film);
        JsonObject result = new JsonObject();

        for (String path : paths(replays))
        {
            result.addProperty(path, revisions.has(path) ? revisions.get(path).getAsLong() : 0L);
        }

        return result;
    }

    public static void track(Film film, JsonArray replays)
    {
        JsonObject revisions = data(film);

        for (String path : paths(replays))
        {
            if (!revisions.has(path))
            {
                revisions.addProperty(path, 0L);
            }
        }

        if (revisions.size() > LIMIT)
        {
            throw new IllegalStateException("native revision capacity reached");
        }

        film.bridgePathRevisions = revisions.toString();
    }

    public static String conflict(Film film, JsonObject expected)
    {
        JsonObject revisions = data(film);

        for (String path : expected.keySet())
        {
            long actual = revisions.has(path) ? revisions.get(path).getAsLong() : 0L;

            if (actual != expected.get(path).getAsLong())
            {
                return path;
            }
        }

        return "";
    }

    public static void noteNative(Film film, BaseValue value)
    {
        JsonObject revisions = data(film);

        if (revisions.size() == 0)
        {
            return;
        }

        boolean changed = false;

        for (Replay replay : film.replays.getList())
        {
            String uuid = replay.uuid.get();
            boolean broad = value == replay || value == replay.form || value == replay.uuid
                || value == replay.keyframes || value == replay.properties;

            for (String path : ActorLeases.ROOT)
            {
                String key = ActorLeases.key(uuid, "channels", path);

                if (revisions.has(key) && (broad || related(value, replay.keyframes.get(path))))
                {
                    increment(revisions, key);
                    changed = true;
                }
            }

            for (String path : ActorLeases.ITEMS)
            {
                String key = ActorLeases.key(uuid, "strings", path);

                if (revisions.has(key) && (broad || related(value, replay.keyframes.get(path))))
                {
                    increment(revisions, key);
                    changed = true;
                }
            }

            String prefix = uuid + "/bones/";

            for (String key : Set.copyOf(revisions.keySet()))
            {
                if (!key.startsWith(prefix))
                {
                    continue;
                }

                String bone = key.substring(prefix.length());

                if (broad || related(value, replay.properties.get("pose"))
                    || related(value, replay.properties.get("pose.bones." + bone)))
                {
                    increment(revisions, key);
                    changed = true;
                }
            }

            String shapePrefix = uuid + "/shapeKeys/";
            BaseValue shapeChannel = replay.properties.get("shape_keys");

            if (broad || related(value, shapeChannel))
            {
                for (String key : Set.copyOf(revisions.keySet()))
                {
                    if (key.startsWith(shapePrefix))
                    {
                        increment(revisions, key);
                        changed = true;
                    }
                }

                String timeline = uuid + "/" + ShapeTimelinePatch.SECTION;

                if (revisions.has(timeline))
                {
                    increment(revisions, timeline);
                    changed = true;
                }

                String grid = uuid + "/" + ShapeGridPatch.SECTION;

                if (revisions.has(grid))
                {
                    increment(revisions, grid);
                    changed = true;
                }
            }

            String visibility = ActorLeases.key(
                uuid, VisibilityPatch.SECTION, VisibilityPatch.PATH);

            if (revisions.has(visibility) && (broad || related(value, replay.enabled)))
            {
                increment(revisions, visibility);
                changed = true;
            }

            String rigPrefix = uuid + "/" + RigPropertyCurves.KEY + "/";

            for (String key : Set.copyOf(revisions.keySet()))
            {
                if (!key.startsWith(rigPrefix))
                {
                    continue;
                }

                String path = key.substring(rigPrefix.length());

                if (broad || related(value, replay.properties.get(path)))
                {
                    increment(revisions, key);
                    changed = true;
                }
            }
        }

        if (changed)
        {
            film.bridgePathRevisions = revisions.toString();
        }
    }

    private static void increment(JsonObject revisions, String key)
    {
        long value = revisions.get(key).getAsLong();
        revisions.addProperty(key, Math.addExact(value, 1L));
    }

    private static boolean related(BaseValue first, BaseValue second)
    {
        if (first == null || second == null)
        {
            return false;
        }

        for (BaseValue value = first; value != null; value = value.getParent())
        {
            if (value == second)
            {
                return true;
            }
        }

        for (BaseValue value = second; value != null; value = value.getParent())
        {
            if (value == first)
            {
                return true;
            }
        }

        return false;
    }
}
