package com.blenderbridge.camera;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.utils.interps.CustomInterpolationManager;
import mchorse.bbs_mod.utils.interps.Interpolations;

import java.util.Set;

/** Validate the complete camera replacement before any live clip is removed. */
final class CameraClipWriteValidation
{
    private static final Set<String> CHANNELS = Set.of("x", "y", "z", "yaw", "pitch", "roll", "fov", "distance");
    private static final Set<String> KEY_FIELDS = Set.of("tick", "value", "interp", "lx", "ly", "rx", "ry");

    private CameraClipWriteValidation()
    {}

    static String check(JsonObject fields, Film film)
    {
        try
        {
            String requestedFilm = string(fields.get("film"), "film");
            String request = string(fields.get("requestId"), "requestId");

            if (requestedFilm.isEmpty() || !requestedFilm.equals(film.getId()))
            {
                return "camera write film differs from the bound film";
            }
            if (request.isEmpty() || request.length() > 128)
            {
                return "camera write requires a requestId of 1..128 characters";
            }

            checkBoolean(fields, "replaceAll");
            JsonObject channels = fields.getAsJsonObject("channels");

            for (String name : channels.keySet())
            {
                JsonElement raw = channels.get(name);

                if (!CHANNELS.contains(name) || !raw.isJsonArray() || raw.getAsJsonArray().isEmpty())
                {
                    return "invalid or empty camera channel: " + name;
                }

                float previous = -1F;

                for (JsonElement element : raw.getAsJsonArray())
                {
                    if (!element.isJsonObject())
                    {
                        return "camera key must be an object: " + name;
                    }

                    JsonObject key = element.getAsJsonObject();

                    if (!KEY_FIELDS.containsAll(key.keySet()))
                    {
                        return "unknown camera key field: " + name;
                    }

                    double tick = number(key.get("tick"), "tick");
                    float storedTick = (float) tick;
                    number(key.get("value"), "value");

                    if (tick < 0D || tick >= Integer.MAX_VALUE - 1D
                        || !Float.isFinite(storedTick) || storedTick <= previous)
                    {
                        return "camera ticks must be nonnegative, ordered and distinct as native floats";
                    }

                    previous = storedTick;

                    for (String handle : new String[] {"lx", "ly", "rx", "ry"})
                    {
                        if (key.has(handle) && !Float.isFinite((float) number(key.get(handle), handle)))
                        {
                            return "camera handle exceeds native float range";
                        }
                    }

                    if (key.has("interp"))
                    {
                        String interpolation = string(key.get("interp"), "interp");

                        if (!Interpolations.MAP.containsKey(interpolation)
                            && CustomInterpolationManager.INSTANCE.get(interpolation) == null)
                        {
                            return "unknown camera interpolation: " + interpolation;
                        }
                    }
                }
            }

            if (fields.has("tracker"))
            {
                if (!fields.get("tracker").isJsonObject())
                {
                    return "camera tracker must be an object";
                }

                JsonObject tracker = fields.getAsJsonObject("tracker");
                Set<String> allowed = Set.of("showHand", "showHud", "selector", "tickStart", "tickEnd");

                if (!allowed.containsAll(tracker.keySet()))
                {
                    return "unknown camera tracker field";
                }

                checkBoolean(tracker, "showHand");
                checkBoolean(tracker, "showHud");
                int start = integer(tracker, "tickStart", 0);
                int end = integer(tracker, "tickEnd", 0);
                int selector = integer(tracker, "selector", -1);

                if (start < 0 || end < start || (long) end - start >= Integer.MAX_VALUE
                    || selector < -1 || selector >= film.replays.getList().size())
                {
                    return "invalid camera tracker range or replay selector";
                }
            }

            return "";
        }
        catch (IllegalArgumentException | IllegalStateException | NullPointerException exception)
        {
            return "invalid camera write: " + exception.getMessage();
        }
    }

    private static double number(JsonElement value, String field)
    {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()
            || !Double.isFinite(value.getAsDouble()))
        {
            throw new IllegalArgumentException("finite number required for " + field);
        }

        return value.getAsDouble();
    }

    private static String string(JsonElement value, String field)
    {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
        {
            throw new IllegalArgumentException("string required for " + field);
        }

        return value.getAsString();
    }

    private static int integer(JsonObject fields, String field, int fallback)
    {
        if (!fields.has(field))
        {
            return fallback;
        }

        double value = number(fields.get(field), field);

        if (value != Math.rint(value) || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE)
        {
            throw new IllegalArgumentException("exact integer required for " + field);
        }

        return (int) value;
    }

    private static void checkBoolean(JsonObject fields, String field)
    {
        if (fields.has(field) && (!fields.get(field).isJsonPrimitive()
            || !fields.getAsJsonPrimitive(field).isBoolean()))
        {
            throw new IllegalArgumentException("boolean required for " + field);
        }
    }
}
