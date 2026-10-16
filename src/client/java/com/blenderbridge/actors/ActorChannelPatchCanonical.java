package com.blenderbridge.actors;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/** Canonical forms matching exactly what the conditional importers consume. */
final class ActorChannelPatchCanonical
{
    private ActorChannelPatchCanonical()
    {
    }

    static JsonElement value(JsonElement element)
    {
        if (element == null || element.isJsonNull())
        {
            return JsonNull.INSTANCE;
        }
        if (element.isJsonArray())
        {
            JsonArray out = new JsonArray();

            for (JsonElement item : element.getAsJsonArray())
            {
                out.add(value(item));
            }

            return out;
        }
        if (element.isJsonObject())
        {
            JsonObject out = new JsonObject();

            for (java.util.Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet())
            {
                out.add(entry.getKey(), value(entry.getValue()));
            }

            return out;
        }

        JsonPrimitive primitive = element.getAsJsonPrimitive();

        if (primitive.isNumber())
        {
            double number = primitive.getAsDouble();
            return new JsonPrimitive(Math.rint(number * 1_000_000D) / 1_000_000D);
        }

        return primitive.deepCopy();
    }

    static JsonElement bone(JsonElement element)
    {
        if (element == null || element.isJsonNull())
        {
            return JsonNull.INSTANCE;
        }
        if (!element.isJsonObject())
        {
            return value(element);
        }

        JsonObject source = element.getAsJsonObject();
        JsonObject out = new JsonObject();
        boolean quat = source.has("quat") && source.get("quat").isJsonPrimitive()
            && source.get("quat").getAsBoolean();
        String[] base = {"tx", "ty", "tz", "sx", "sy", "sz"};
        String[] rotation = quat
            ? new String[] {"qw", "qx", "qy", "qz"}
            : new String[] {"rx", "ry", "rz"};

        copy(source, out, base);
        copy(source, out, rotation);
        copy(source, out, new String[] {"interp"});
        out.addProperty("quat", quat);
        return out;
    }

    private static void copy(JsonObject source, JsonObject target, String[] keys)
    {
        for (String key : keys)
        {
            if (source.has(key))
            {
                target.add(key, value(source.get(key)));
            }
        }
    }
}
