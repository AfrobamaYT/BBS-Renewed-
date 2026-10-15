package com.blenderbridge.actors;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.Set;

/** Strict validation for bone transforms and rig-control curves. */
final class ActorChannelPatchStructuredValues
{
    private static final Set<String> BONE_CURVES = Set.of(
        "tx", "ty", "tz", "sx", "sy", "sz", "rx", "ry", "rz", "qw", "qx", "qy", "qz");

    private ActorChannelPatchStructuredValues()
    {
    }

    static String validateBone(JsonElement element)
    {
        if (element == null || !element.isJsonObject())
        {
            return "bone payload must be an object";
        }

        JsonObject bone = element.getAsJsonObject();
        boolean anyCurve = false;

        for (java.util.Map.Entry<String, JsonElement> entry : bone.entrySet())
        {
            String key = entry.getKey();

            if ("quat".equals(key))
            {
                if (!entry.getValue().isJsonPrimitive()
                    || !entry.getValue().getAsJsonPrimitive().isBoolean())
                {
                    return "bone quat flag must be boolean";
                }

                continue;
            }
            if ("interp".equals(key))
            {
                String error = validateShapeArray(entry.getValue());

                if (!error.isEmpty())
                {
                    return error;
                }

                continue;
            }
            if (!BONE_CURVES.contains(key))
            {
                return "unsupported bone component";
            }

            anyCurve = true;
            String error = validateNumericArray(entry.getValue());

            if (!error.isEmpty())
            {
                return error;
            }
        }

        return anyCurve ? "" : "bone payload has no curves";
    }

    static String validateNumericArray(JsonElement element)
    {
        if (element == null || !element.isJsonArray())
        {
            return "keyframe list must be an array";
        }

        for (JsonElement raw : element.getAsJsonArray())
        {
            if (!raw.isJsonObject())
            {
                return "keyframe must be an object";
            }

            JsonObject keyframe = raw.getAsJsonObject();

            if (!finiteNumber(keyframe.get(ActorProtocol.KF_TICK))
                || !finiteNumber(keyframe.get(ActorProtocol.KF_VALUE)))
            {
                return "keyframe needs finite tick/value";
            }

            String error = validateShape(keyframe, true);

            if (!error.isEmpty())
            {
                return error;
            }
        }

        return "";
    }

    private static String validateShapeArray(JsonElement element)
    {
        if (element == null || !element.isJsonArray())
        {
            return "bone interp must be an array";
        }

        for (JsonElement raw : element.getAsJsonArray())
        {
            if (!raw.isJsonObject())
            {
                return "bone interp entry must be an object";
            }

            JsonObject shape = raw.getAsJsonObject();

            if (!finiteNumber(shape.get(ActorProtocol.KF_TICK)))
            {
                return "bone interp tick must be finite";
            }

            String error = validateShape(shape, false);

            if (!error.isEmpty())
            {
                return error;
            }
        }

        return "";
    }

    private static String validateShape(JsonObject shape, boolean allowValue)
    {
        for (String handle : new String[] {ActorProtocol.KF_LX, ActorProtocol.KF_LY,
            ActorProtocol.KF_RX, ActorProtocol.KF_RY})
        {
            if (shape.has(handle) && !finiteNumber(shape.get(handle)))
            {
                return "keyframe handles must be finite";
            }
        }

        if (shape.has(ActorProtocol.KF_INTERP)
            && (!shape.get(ActorProtocol.KF_INTERP).isJsonPrimitive()
                || !shape.getAsJsonPrimitive(ActorProtocol.KF_INTERP).isString()))
        {
            return "keyframe interp must be a string";
        }

        if (!allowValue && shape.has(ActorProtocol.KF_VALUE))
        {
            return "bone interp entry must not contain a value";
        }

        return "";
    }

    private static boolean finiteNumber(JsonElement element)
    {
        if (element == null || !element.isJsonPrimitive())
        {
            return false;
        }

        JsonPrimitive primitive = element.getAsJsonPrimitive();
        return primitive.isNumber() && Double.isFinite(primitive.getAsDouble());
    }
}
