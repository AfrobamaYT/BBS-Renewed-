package com.blenderbridge.actors;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.Set;

/** Strict value validation used before a conditional actor write mutates BBS. */
final class ActorChannelPatchValues
{
    private static final Set<String> ROOT_CHANNELS = Set.of("x", "y", "z", "yaw", "bodyYaw", "scale_x", "scale_y", "scale_z");

    private ActorChannelPatchValues()
    {
    }

    static String validate(String key, JsonElement value, boolean numeric)
    {
        return numeric ? validateNumeric(key, value) : validateItem(key, value);
    }

    private static String validateNumeric(String key, JsonElement value)
    {
        if (!ROOT_CHANNELS.contains(key))
        {
            return "unsupported numeric channel";
        }
        if (!value.isJsonArray())
        {
            return "numeric channel must be an array";
        }

        for (JsonElement element : value.getAsJsonArray())
        {
            if (!element.isJsonObject())
            {
                return "numeric keyframe must be an object";
            }

            JsonObject keyframe = element.getAsJsonObject();

            if (!finiteNumber(keyframe.get(ActorProtocol.KF_TICK))
                || !finiteNumber(keyframe.get(ActorProtocol.KF_VALUE)))
            {
                return "numeric keyframe needs finite tick/value";
            }

            for (String handle : new String[] {ActorProtocol.KF_LX, ActorProtocol.KF_LY,
                ActorProtocol.KF_RX, ActorProtocol.KF_RY})
            {
                if (keyframe.has(handle) && !finiteNumber(keyframe.get(handle)))
                {
                    return "numeric keyframe handle must be finite";
                }
            }

            if (keyframe.has(ActorProtocol.KF_INTERP)
                && (!keyframe.get(ActorProtocol.KF_INTERP).isJsonPrimitive()
                    || !keyframe.getAsJsonPrimitive(ActorProtocol.KF_INTERP).isString()))
            {
                return "numeric keyframe interp must be a string";
            }
        }

        return "";
    }

    private static String validateItem(String key, JsonElement value)
    {
        boolean known = false;

        for (String itemChannel : StringChannels.ITEM_CHANNELS)
        {
            known |= itemChannel.equals(key);
        }

        if (!known || !value.isJsonArray())
        {
            return "unsupported item channel";
        }

        for (JsonElement element : value.getAsJsonArray())
        {
            if (!element.isJsonArray() || element.getAsJsonArray().size() != 2)
            {
                return "item keyframe must be [tick,id]";
            }

            JsonArray pair = element.getAsJsonArray();

            if (!finiteNumber(pair.get(0)) || !pair.get(1).isJsonPrimitive()
                || !pair.get(1).getAsJsonPrimitive().isString())
            {
                return "item keyframe needs finite tick and string id";
            }

            ResourceLocation identifier = ResourceLocation.tryParse(pair.get(1).getAsString());

            if (identifier == null)
            {
                return "invalid item id";
            }

            Item item = BuiltInRegistries.ITEM.getValue(identifier);

            if (item == null || !identifier.equals(BuiltInRegistries.ITEM.getKey(item)))
            {
                return "unknown item id";
            }
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
