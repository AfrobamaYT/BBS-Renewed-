package com.afrobama.bbstools.client.fracture;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Loads {@code fracture_groups.json} from the model's asset directory
 * and resolves group names → bone-name sets. Lets the user define
 * which bones fracture together (e.g. {@code "front_cabin"} contains
 * {@code [cabin, boiler, chimney]} so triggering that group leaves
 * the wagons intact).
 *
 * <p>Format:</p>
 * <pre>
 * {
 *   "groups": {
 *     "front_cabin":  ["cabin", "boiler", "chimney"],
 *     "wagon_1":      ["wagon_1_body", "wagon_1_wheels"],
 *     "wheels_left":  ["wheel_l1", "wheel_l2", "wheel_l3"]
 *   },
 *   "default_group": "front_cabin"
 * }
 * </pre>
 *
 * <p>Lookup is cached per-model-link to avoid re-parsing JSON every
 * trigger. Cache survives until the BBS model registry reloads (which
 * is rare).</p>
 *
 * <p>The JSON file lives next to the model's BOBJ — typically in
 * {@code config/bbs/assets/models/<modelname>/fracture_groups.json}.
 * Falls back to {@code <model_dir>/fracture_groups.json} via the
 * model's resolved path. If the file's missing we return null and
 * {@link FractureSpawner} fractures every bone.</p>
 */
public final class FractureGroupConfig
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/fracture/groups");

    private static final Map<String, Map<String, Set<String>>> CACHE = new HashMap<>();
    private static final Set<String> NEGATIVE_CACHE = new HashSet<>();

    private FractureGroupConfig()
    {
    }

    /**
     * Look up the bone-name set for a given group on the given actor's
     * model. Returns null when the model has no fracture-groups config
     * (the spawner will then fracture all bones).
     */
    public static Set<String> lookupGroup(IEntity actor, String groupName)
    {
        if (actor == null || groupName == null || groupName.isEmpty()) return null;

        Form form = actor.getForm();
        if (!(form instanceof ModelForm modelForm)) return null;

        String modelId = modelForm.model.get();
        if (modelId == null || modelId.isEmpty()) return null;

        Map<String, Set<String>> groups = loadGroups(modelId);
        if (groups == null) return null;

        Set<String> bones = groups.get(groupName);
        return bones == null || (bones.size() == 0) ? null : bones;
    }

    private static synchronized Map<String, Set<String>> loadGroups(String modelId)
    {
        if (CACHE.containsKey(modelId)) return CACHE.get(modelId);
        if (NEGATIVE_CACHE.contains(modelId)) return null;

        File file = resolveGroupsFile(modelId);
        if (file == null || !file.exists())
        {
            NEGATIVE_CACHE.add(modelId);
            return null;
        }

        try (BufferedReader r = new BufferedReader(new FileReader(file)))
        {
            JsonElement root = JsonParser.parseReader(r);
            if (!root.isJsonObject())
            {
                NEGATIVE_CACHE.add(modelId);
                return null;
            }

            JsonObject obj = root.getAsJsonObject();
            JsonElement groupsEl = obj.get("groups");
            if (groupsEl == null || !groupsEl.isJsonObject())
            {
                NEGATIVE_CACHE.add(modelId);
                return null;
            }

            JsonObject groupsObj = groupsEl.getAsJsonObject();
            Map<String, Set<String>> groups = new HashMap<>();

            for (Map.Entry<String, JsonElement> entry : groupsObj.entrySet())
            {
                if (!entry.getValue().isJsonArray()) continue;

                JsonArray arr = entry.getValue().getAsJsonArray();
                Set<String> bones = new HashSet<>();
                for (JsonElement e : arr)
                {
                    if (e.isJsonPrimitive()) bones.add(e.getAsString());
                }
                if (!(bones.size() == 0)) groups.put(entry.getKey(), bones);
            }

            CACHE.put(modelId, groups);
            LOG.info("Loaded {} fracture groups for model '{}'", groups.size(), modelId);
            return groups;
        }
        catch (Throwable t)
        {
            LOG.warn("Failed to parse fracture_groups.json for '{}': {}", modelId, t.toString());
            NEGATIVE_CACHE.add(modelId);
            return null;
        }
    }

    /**
     * Resolve the on-disk path of {@code fracture_groups.json} for a
     * model. Strategy: ask BBSModClient.getModels().getModel(id) for
     * the {@link ModelInstance}, then look at {@code instance.path} or
     * a similar "where this model came from" accessor. As a fallback,
     * try the standard {@code config/bbs/assets/models/<id>/} layout.
     */
    private static File resolveGroupsFile(String modelId)
    {
        try
        {
            ModelInstance instance = BBSModClient.getModels().getModel(modelId);
            if (instance != null)
            {
                /* Probe the most likely accessor names via reflection so
                 *  we don't tie BBSTools to a specific BBS internal API
                 *  (these accessors may change between BBS-CML and
                 *  Refreshed). */
                File modelDir = probeModelDirectory(instance);
                if (modelDir != null && modelDir.isDirectory())
                {
                    return new File(modelDir, "fracture_groups.json");
                }
            }
        }
        catch (Throwable ignored) {}

        /* Fallback: standard BBS asset layout. */
        File configRoot = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().toFile();
        File guess = new File(configRoot, "bbs/assets/models/" + modelId + "/fracture_groups.json");
        return guess;
    }

    private static File probeModelDirectory(ModelInstance instance)
    {
        for (java.lang.reflect.Field f : instance.getClass().getDeclaredFields())
        {
            try
            {
                f.setAccessible(true);
                Object v = f.get(instance);
                if (v instanceof File fileVal && fileVal.isDirectory())
                {
                    return fileVal;
                }
                if (v instanceof java.nio.file.Path pathVal && pathVal.toFile().isDirectory())
                {
                    return pathVal.toFile();
                }
            }
            catch (Throwable ignored) {}
        }
        return null;
    }

    /** Clear the cache — call on model registry reload. */
    public static synchronized void invalidate()
    {
        CACHE.clear();
        NEGATIVE_CACHE.clear();
    }

    public static synchronized Set<String> cachedGroupNames(String modelId)
    {
        Map<String, Set<String>> groups = CACHE.get(modelId);
        return groups == null ? Collections.emptySet() : Collections.unmodifiableSet(groups.keySet());
    }

}
