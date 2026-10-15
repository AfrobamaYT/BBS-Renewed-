package com.blenderbridge.actors;

import com.blenderbridge.BBSCompat;
import com.blenderbridge.BridgeProtocol;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.settings.values.core.ValuePose;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import mchorse.bbs_mod.utils.pose.Transform;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/** Transient render-only bone channels. Never writes a Film, keyframe or undo journal. */
public final class ActorPosePreview
{
    private static final Map<Form, Sample> ACTIVE = new WeakHashMap<>();
    private static final long TTL = 1_500_000_000L;
    private static boolean installed;

    private ActorPosePreview()
    {}

    private record Sample(Film film, int tick, String client, long serial,
                          long expires, ValuePose overlay)
    {}

    private static UIFilmPanel panel()
    {
        return BBSCompat.peekDashboard() == null ? null
            : BBSCompat.peekDashboard().getPanel(UIFilmPanel.class);
    }

    public static JsonObject handle(JsonObject fields)
    {
        JsonObject out = new JsonObject();
        out.addProperty("posePreview", true);
        out.addProperty("requestId", BridgeProtocol.getFieldString(fields, "requestId", ""));
        out.addProperty("ok", false);
        Form target = null;
        String client = BridgeProtocol.getFieldString(fields, "client", "");
        try
        {
            if (!installed)
            {
                ClientTickEvents.END_CLIENT_TICK.register(clientGame -> expire());
                installed = true;
            }
            expire();
            UIFilmPanel panel = panel();
            if (panel == null || panel.getData() == null || panel.isRunning()
                || !ActorProject.ENGINE.equals(BridgeProtocol.getFieldString(fields, "engine", ""))
                || !panel.getData().getId().equals(BridgeProtocol.getFieldString(fields, "film", ""))
                || client.isBlank() || client.length() > 128)
            {
                throw new IllegalArgumentException("pose preview requires the same engine and stopped film");
            }
            if (!fields.has("replays") || fields.getAsJsonArray("replays").size() != 1)
            {
                throw new IllegalArgumentException("pose preview requires exactly one actor");
            }
            JsonObject patch = fields.getAsJsonArray("replays").get(0).getAsJsonObject();
            String uuid = BridgeProtocol.getFieldString(patch, "uuid", "");
            Replay replay = null;
            for (int i = 0; i < panel.getData().replays.getList().size(); i++)
            {
                Replay candidate = panel.getData().replays.getList().get(i);
                if (uuid.equals(candidate.uuid.get()))
                {
                    replay = candidate;
                    IEntity entity = panel.getController().getEntities().get(i);
                    target = entity == null ? null : entity.getForm();
                    break;
                }
            }
            if (replay == null || !(target instanceof ModelForm))
            {
                throw new IllegalArgumentException("pose preview actor has no live ModelForm");
            }
            Sample old = ACTIVE.get(target);
            long serial = fields.get("serial").getAsLong();
            if (old != null && old.expires > System.nanoTime()
                && (!old.client.equals(client) || serial <= old.serial))
            {
                out.addProperty("error", "pose preview is owned or the sample is stale");
                return out;
            }
            JsonObject bones = patch.getAsJsonObject("bones");
            if (bones.size() == 0)
            {
                remove(target);
                out.addProperty("ok", true);
                return out;
            }
            if (ACTIVE.size() >= 32 && old == null || bones.size() > 256)
            {
                throw new IllegalArgumentException("pose preview capacity exceeded");
            }
            double tick = fields.get("tick").getAsDouble();
            if (!Double.isFinite(tick) || tick != panel.getCursor())
            {
                throw new IllegalArgumentException("pose preview playheads differ");
            }
            JsonObject expected = patch.getAsJsonObject("expected");
            JsonObject current = PerBoneExporter.serializePerBone(replay);
            var model = mchorse.bbs_mod.BBSModClient.getModels().getModel(((ModelForm) target).model.get());
            if (model == null)
            {
                throw new IllegalArgumentException("pose preview model is unavailable");
            }
            var modelBones = model.model.getGroupKeysInHierarchyOrder();
            Pose values = new Pose();
            for (Map.Entry<String, JsonElement> entry : bones.entrySet())
            {
                String name = entry.getKey();
                if (name.isBlank() || name.length() > 256 || !modelBones.contains(name))
                {
                    throw new IllegalArgumentException("invalid preview bone name");
                }
                for (String section : new String[] {"bones", "poseTrack"})
                {
                    if (!expected.has(section) || !expected.getAsJsonObject(section).has(name)
                        || !ActorChannelPatchCanonical.bone(value(current, section, name)).equals(
                            ActorChannelPatchCanonical.bone(value(expected, section, name))))
                    {
                        throw new IllegalArgumentException("pose preview conflict: " + uuid + "/" + name);
                    }
                }
                values.transforms.put(name, decode(entry.getValue().getAsJsonObject()));
            }
            ValuePose overlay = old == null ? new ValuePose("blender_pose_preview", values) : old.overlay;
            if (old == null)
            {
                ((ModelForm) target).additionalOverlays.add(overlay);
            }
            else
            {
                overlay.set(values);
            }
            ACTIVE.put(target, new Sample(panel.getData(), panel.getCursor(), client,
                serial, System.nanoTime() + TTL, overlay));
            out.addProperty("ok", true);
        }
        catch (RuntimeException exception)
        {
            Sample old = ACTIVE.get(target);
            if (old != null && old.client.equals(client))
            {
                remove(target);
            }
            out.addProperty("error", exception.getMessage());
        }
        return out;
    }

    private static JsonElement value(JsonObject object, String section, String name)
    {
        return object.has(section) && object.getAsJsonObject(section).has(name)
            ? object.getAsJsonObject(section).get(name) : JsonNull.INSTANCE;
    }

    private static float[] vector(JsonObject value, String key, int length)
    {
        if (!value.has(key) || !value.get(key).isJsonArray()
            || value.getAsJsonArray(key).size() != length)
        {
            throw new IllegalArgumentException("invalid preview " + key);
        }
        float[] out = new float[length];
        for (int i = 0; i < length; i++)
        {
            out[i] = value.getAsJsonArray(key).get(i).getAsFloat();
            if (!Float.isFinite(out[i]) || Math.abs(out[i]) > 1_000_000F)
            {
                throw new IllegalArgumentException("non-finite or oversized preview value");
            }
        }
        return out;
    }

    private static PoseTransform decode(JsonObject value)
    {
        float[] location = vector(value, "location", 3);
        float[] scale = vector(value, "scale", 3);
        float[] rotation = vector(value, "quaternion", 4);
        PoseTransform pose = new PoseTransform();
        pose.translate.set(location[0], location[1], location[2]);
        pose.scale.set(scale[0], scale[1], scale[2]);
        pose.quat.set(rotation[1], rotation[2], rotation[3], rotation[0]);
        if (pose.quat.lengthSquared() < 1E-12F)
        {
            throw new IllegalArgumentException("zero preview quaternion");
        }
        pose.quat.normalize();
        pose.rotationMode = Transform.RotationMode.QUATERNION;
        pose.fix = 1F;
        return pose;
    }

    private static void remove(Form form)
    {
        Sample sample = ACTIVE.remove(form);
        if (sample != null && form instanceof ModelForm model)
        {
            model.additionalOverlays.remove(sample.overlay);
        }
    }

    /** The existing renderer evaluates additionalOverlays without changing serialized poses. */
    private static void expire()
    {
        UIFilmPanel panel = panel();
        for (Map.Entry<Form, Sample> entry : new HashMap<>(ACTIVE).entrySet())
        {
            Sample sample = entry.getValue();
            if (sample.expires < System.nanoTime() || panel == null || panel.isRunning()
                || panel.getData() != sample.film || panel.getCursor() != sample.tick)
            {
                remove(entry.getKey());
            }
        }
    }
}
