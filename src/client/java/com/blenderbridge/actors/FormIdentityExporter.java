package com.blenderbridge.actors;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

/** Exports BBS' existing constant Model/mobId FormProperties timeline. */
public final class FormIdentityExporter
{
    public static final String KEY = "formIdentity";

    private FormIdentityExporter() {}

    public static JsonObject serialize(Replay replay)
    {
        JsonObject out = new JsonObject();

        if (replay == null || replay.properties == null)
        {
            return out;
        }

        Form form = replay.form.get();
        String property = form instanceof ModelForm ? "model" : form instanceof MobForm ? "mobId" : "";

        if (property.isEmpty())
        {
            return out;
        }

        KeyframeChannel channel = replay.properties.properties.get(property);

        if (channel == null || channel.isEmpty())
        {
            return out;
        }

        JsonArray keys = new JsonArray();

        for (Object object : channel.getKeyframes())
        {
            Keyframe keyframe = (Keyframe) object;
            Object value = keyframe.getValue();

            if (!(value instanceof String string))
            {
                continue;
            }

            JsonObject key = new JsonObject();
            key.addProperty("tick", keyframe.getTick());
            key.addProperty("value", string);
            keys.add(key);
        }

        if (!keys.isEmpty())
        {
            JsonObject track = new JsonObject();
            track.addProperty("property", property);
            track.add("keys", keys);
            out.add(KEY, track);
        }

        return out;
    }
}
