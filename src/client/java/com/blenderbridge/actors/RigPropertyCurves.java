package com.blenderbridge.actors;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.ik.RigMechanik;
import mchorse.bbs_mod.cubic.model.bobj.BOBJModel;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.RigPropertyPath;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

import java.util.Map;

/** BBS rig scalar channels -> Blender pose-bone custom-property curves. */
public final class RigPropertyCurves
{
    public static final String KEY = "rigProperties";
    public static final String KEYS = "keys";
    public static final String KIND = "kind";

    private RigPropertyCurves()
    {}

    /** The result belongs below {@code replay.properties.rigProperties}. */
    public static JsonObject serialize(Replay replay)
    {
        JsonObject bones = new JsonObject();

        if (replay == null || replay.properties == null)
        {
            return bones;
        }

        for (Map.Entry<String, KeyframeChannel> entry : replay.properties.properties.entrySet())
        {
            RigPropertyPath path = RigPropertyPath.parse(entry.getKey());
            KeyframeChannel channel = entry.getValue();

            /* The imported Blender actor currently represents the replay's top-level ModelForm.
             * Nested form rigs need their own armature identity and remain explicit boundary. */
            if (path == null || !path.formPath().isEmpty() || channel == null || channel.isEmpty())
            {
                continue;
            }

            JsonArray keys = serializeChannel(channel);

            if (keys.size() == 0)
            {
                continue;
            }

            JsonObject property = schema(replay, path);
            property.add(KEYS, keys);

            JsonObject bone = bones.has(path.bone())
                ? bones.getAsJsonObject(path.bone()) : new JsonObject();
            bone.add(path.property(), property);
            bones.add(path.bone(), bone);
        }

        return bones;
    }

    /** Apply only explicitly named controls; {@code null} removes the BBS animation channel. */
    public static int apply(JsonObject replayObject, Replay replay)
    {
        if (replayObject == null || replay == null || replay.properties == null)
        {
            return 0;
        }

        JsonElement root = replayObject.get(KEY);
        Form form = replay.form.get();

        if (root == null || !root.isJsonObject() || form == null)
        {
            return 0;
        }

        int written = 0;

        for (Map.Entry<String, JsonElement> boneEntry : root.getAsJsonObject().entrySet())
        {
            for (Map.Entry<String, JsonElement> propEntry
                : boneEntry.getValue().getAsJsonObject().entrySet())
            {
                String id = RigPropertyPath.key("", boneEntry.getKey(), propEntry.getKey());

                if (propEntry.getValue().isJsonNull())
                {
                    KeyframeChannel channel = replay.properties.properties.get(id);

                    if (channel != null)
                    {
                        replay.properties.preNotify();
                        replay.properties.properties.remove(id);
                        replay.properties.remove(channel);
                        replay.properties.postNotify();
                        written++;
                    }

                    continue;
                }

                JsonArray keys = propEntry.getValue().getAsJsonObject().getAsJsonArray(KEYS);
                KeyframeChannel channel = replay.properties.properties.get(id);

                if (channel == null)
                {
                    replay.properties.preNotify();
                    channel = replay.properties.getOrCreate(form, id);
                    ActorImporter.applyChannel(channel, keys);
                    replay.properties.postNotify();
                    written++;
                }
                else
                {
                    ActorImporter.applyChannel(channel, keys);
                    written++;
                }
            }
        }

        return written;
    }

    public static String validate(JsonElement element)
    {
        if (element == null || element.isJsonNull())
        {
            return "";
        }
        if (!element.isJsonObject())
        {
            return "rig property must be an object or null";
        }

        JsonObject spec = element.getAsJsonObject();
        java.util.Set<String> allowed = java.util.Set.of(
            KIND, KEYS, "default", "min", "max", "soft_min", "soft_max");

        if (!allowed.containsAll(spec.keySet()))
        {
            return "unknown rig property field";
        }
        if (!spec.has(KIND) || !spec.get(KIND).isJsonPrimitive()
            || !spec.getAsJsonPrimitive(KIND).isString()
            || !java.util.Set.of("float", "int", "bool").contains(spec.get(KIND).getAsString()))
        {
            return "rig property kind must be float/int/bool";
        }
        if (!spec.has(KEYS))
        {
            return "rig property has no keys";
        }

        String keysError = ActorChannelPatchStructuredValues.validateNumericArray(spec.get(KEYS));
        if (!keysError.isEmpty() || spec.getAsJsonArray(KEYS).isEmpty())
        {
            return keysError.isEmpty() ? "rig property has no keys" : keysError;
        }

        for (String field : new String[] {"default", "min", "max", "soft_min", "soft_max"})
        {
            if (spec.has(field) && !finite(spec.get(field)))
            {
                return "rig property metadata must be finite";
            }
        }
        if (spec.has("min") && spec.has("max")
            && spec.get("min").getAsDouble() > spec.get("max").getAsDouble())
        {
            return "rig property min exceeds max";
        }

        return "";
    }

    /** Canonical persisted meaning: the channel keys. UI schema is derived from the BOBJ rig. */
    public static JsonElement canonical(JsonElement element)
    {
        if (element == null || element.isJsonNull())
        {
            return JsonNull.INSTANCE;
        }

        return ActorChannelPatchCanonical.value(element.getAsJsonObject().get(KEYS));
    }

    private static boolean finite(JsonElement element)
    {
        if (element == null || !element.isJsonPrimitive())
        {
            return false;
        }

        JsonPrimitive primitive = element.getAsJsonPrimitive();
        return primitive.isNumber() && Double.isFinite(primitive.getAsDouble());
    }

    private static JsonArray serializeChannel(KeyframeChannel channel)
    {
        JsonArray out = new JsonArray();

        for (Object raw : channel.getKeyframes())
        {
            if (!(raw instanceof Keyframe keyframe) || !(keyframe.getValue() instanceof Number value))
            {
                continue;
            }

            JsonObject item = new JsonObject();
            item.addProperty(ActorProtocol.KF_TICK, keyframe.getTick());
            item.addProperty(ActorProtocol.KF_VALUE, value);
            item.addProperty(ActorProtocol.KF_INTERP, interpKey(keyframe));
            item.addProperty(ActorProtocol.KF_LX, keyframe.lx);
            item.addProperty(ActorProtocol.KF_LY, keyframe.ly);
            item.addProperty(ActorProtocol.KF_RX, keyframe.rx);
            item.addProperty(ActorProtocol.KF_RY, keyframe.ry);
            out.add(item);
        }

        return out;
    }

    /** Include Blender's ID-property type and UI limits when the loaded BOBJ rig exposes them. */
    private static JsonObject schema(Replay replay, RigPropertyPath path)
    {
        JsonObject out = new JsonObject();
        MapType data = schemaData(replay, path);
        String kind = data == null ? "float" : data.getString("art", "float");

        out.addProperty(KIND, kind);

        if (data != null)
        {
            out.addProperty("default", data.getFloat("default", data.getFloat("wert", 0F)));

            if (data.has("min") && data.has("max"))
            {
                out.addProperty("min", data.getFloat("min"));
                out.addProperty("max", data.getFloat("max"));
            }
        }

        return out;
    }

    private static MapType schemaData(Replay replay, RigPropertyPath path)
    {
        try
        {
            if (!(replay.form.get() instanceof ModelForm form)
                || !(FormUtilsClient.getRenderer(form) instanceof ModelFormRenderer renderer))
            {
                return null;
            }

            ModelInstance instance = renderer.getModel();

            if (instance == null || !(instance.model instanceof BOBJModel bobj))
            {
                return null;
            }

            RigMechanik.installieren(bobj, form.model.get());

            if (!(bobj.getArmature().zwang instanceof RigMechanik mechanik))
            {
                return null;
            }

            return mechanik.treiber.regler.reglerVon(path.bone()).get(path.property());
        }
        catch (Exception ignored)
        {
            /* Actor export must remain available before the renderer/model is loaded. */
            return null;
        }
    }

    private static String interpKey(Keyframe keyframe)
    {
        try
        {
            String key = keyframe.getInterpolation().getKey();

            return key == null || key.isEmpty() ? "linear" : key;
        }
        catch (Exception ignored)
        {
            return "linear";
        }
    }
}
