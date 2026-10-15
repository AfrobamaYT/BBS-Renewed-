package com.blenderbridge.actors;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.resources.Link;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Validation and field mapping for Blender-authored replay-list structure. */
final class FilmStructureCodec
{
    private FilmStructureCodec() {}

    static String validate(JsonArray entries, Film film, boolean replace)
    {
        Set<String> seen = new HashSet<>();
        Map<String, Boolean> groups = new HashMap<>();

        if (!replace)
        {
            for (Replay replay : film.replays.getList())
            {
                groups.put(replay.uuid.get(), replay.isGroup.get());
            }
        }

        for (int i = 0; i < entries.size(); i++)
        {
            JsonElement element = entries.get(i);

            if (!element.isJsonObject())
            {
                return "replays[" + i + "] is not an object";
            }

            JsonObject entry = element.getAsJsonObject();
            String uuid = string(entry, "uuid", "").trim();

            if (uuid.isEmpty())
            {
                return "replays[" + i + "] has no uuid";
            }
            if (!seen.add(uuid))
            {
                return "duplicate replay uuid '" + uuid + "'";
            }

            boolean isGroup = bool(entry, "isGroup", false);
            groups.put(uuid, isGroup);

            if (!isGroup)
            {
                Replay existing = find(film, uuid);
                String type = string(entry, "formType", "").trim();

                if (type.isEmpty() && existing == null)
                {
                    return "replay '" + uuid + "' has no formType";
                }
                if (!type.isEmpty() && BBSMod.getForms().getTypeClass(type) == null)
                {
                    return "unsupported formType '" + type + "' for replay '" + uuid + "'";
                }
            }
        }

        for (JsonElement element : entries)
        {
            JsonObject entry = element.getAsJsonObject();
            String parent = string(entry, "group", "").trim();

            if (!parent.isEmpty() && !Boolean.TRUE.equals(groups.get(parent)))
            {
                return "replay '" + string(entry, "uuid", "")
                    + "' references missing/non-group parent '" + parent + "'";
            }
        }

        return "";
    }

    static Replay find(Film film, String uuid)
    {
        for (Replay replay : film.replays.getList())
        {
            if (uuid.equals(replay.uuid.get()))
            {
                return replay;
            }
        }

        return null;
    }

    static boolean apply(Replay replay, JsonObject entry)
    {
        boolean changed = false;
        boolean isGroup = bool(entry, "isGroup", replay.isGroup.get());

        changed |= set(replay.isGroup, isGroup);
        changed |= set(replay.label, string(entry, "label", replay.label.get()));
        changed |= set(replay.group, string(entry, "group", replay.group.get()));
        changed |= set(replay.category, Replay.normalizeCategory(
            string(entry, "category", replay.category.get())));
        changed |= set(replay.enabled, bool(entry, "enabled", replay.enabled.get()));
        changed |= set(replay.fp, bool(entry, "firstPerson", replay.fp.get()));

        if (isGroup)
        {
            if (replay.form.get() != null)
            {
                replay.form.set(null);
                changed = true;
            }

            return changed;
        }

        String type = string(entry, "formType", "").trim();
        Form form = replay.form.get();

        if (!type.isEmpty() && (form == null || !type.equals(form.getFormId())))
        {
            form = BBSMod.getForms().create(Link.create(type));
            replay.form.set(form);
            changed = true;
        }

        if (form instanceof ModelForm model && entry.has("formModel"))
        {
            changed |= set(model.model, string(entry, "formModel", model.model.get()));
        }
        else if (form instanceof MobForm mob && entry.has("formMob"))
        {
            changed |= set(mob.mobID, string(entry, "formMob", mob.mobID.get()));
        }

        return changed;
    }

    static String string(JsonObject object, String key, String fallback)
    {
        try
        {
            JsonElement value = object == null ? null : object.get(key);
            return value == null || value.isJsonNull() ? fallback : value.getAsString();
        }
        catch (RuntimeException e)
        {
            return fallback;
        }
    }

    static boolean bool(JsonObject object, String key, boolean fallback)
    {
        try
        {
            JsonElement value = object == null ? null : object.get(key);
            return value == null || value.isJsonNull() ? fallback : value.getAsBoolean();
        }
        catch (RuntimeException e)
        {
            return fallback;
        }
    }

    private static boolean set(mchorse.bbs_mod.settings.values.core.ValueString value, String next)
    {
        if (value.get().equals(next)) return false;
        value.set(next);
        return true;
    }

    private static boolean set(mchorse.bbs_mod.settings.values.numeric.ValueBoolean value, boolean next)
    {
        if (value.get() == next) return false;
        value.set(next);
        return true;
    }
}
