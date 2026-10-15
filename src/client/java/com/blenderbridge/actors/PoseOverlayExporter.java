package com.blenderbridge.actors;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.settings.values.core.ValuePose;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;

/** Ordered original overlay curves, never flattened into the editable base pose. */
public final class PoseOverlayExporter
{
    private PoseOverlayExporter() {}

    public static JsonObject serialize(Replay replay)
    {
        JsonObject result = new JsonObject();
        if (!(replay.form.get() instanceof ModelForm form))
        {
            return result;
        }
        JsonArray layers = new JsonArray();
        add(layers, replay, form.poseOverlay);
        for (int i = 0; i < 4 && i < form.additionalOverlays.size(); i++)
        {
            add(layers, replay, form.additionalOverlays.get(i));
        }
        if (!layers.isEmpty())
        {
            result.add("poseOverlays", layers);
        }
        return result;
    }

    private static void add(JsonArray layers, Replay replay, ValuePose property)
    {
        KeyframeChannel<?> existing = replay.properties.properties.get(property.getId());
        KeyframeChannel<Pose> channel;
        if (existing != null && !existing.isEmpty())
        {
            channel = (KeyframeChannel<Pose>) existing;
        }
        else
        {
            Pose original = property.getOriginalValue();
            if (original.transforms.isEmpty())
            {
                return;
            }
            channel = new KeyframeChannel<>("pose", KeyframeFactories.POSE);
            channel.insert(0, original);
        }

        /* The existing scalar exporter only reads the channel. The temporary map
         * does not attach/reparent it or change any authored pose or notification. */
        Replay container = new Replay("overlay_export");
        container.properties.properties.put("pose", channel);
        JsonObject bones = PerBoneExporter.serializePerBone(container).getAsJsonObject("poseTrack");
        if (bones == null || bones.isEmpty())
        {
            return;
        }
        for (String name : bones.keySet())
        {
            JsonArray fixes = new JsonArray();
            int index = 0;
            for (Keyframe<Pose> key : channel.getKeyframes())
            {
                PoseTransform transform = key.getValue().transforms.get(name);
                JsonObject fix = bones.getAsJsonObject(name).getAsJsonArray("tx").get(index++).getAsJsonObject().deepCopy();
                fix.addProperty("value", transform == null ? 0F : transform.fix);
                fixes.add(fix);
            }
            bones.getAsJsonObject(name).add("fix", fixes);
        }
        JsonObject layer = new JsonObject();
        layer.addProperty("id", property.getId());
        layer.add("bones", bones);
        layers.add(layer);
    }
}
