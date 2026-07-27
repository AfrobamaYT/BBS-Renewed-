package com.afrobama.bbstools.config;

import com.afrobama.bbstools.perf.BBSPerfConfig;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads and saves a JSON config at
 * {@code <configDir>/bbstools/config.json} that mirrors the
 * {@link BBSPerfConfig} static knobs. Lets users tweak distance-cull /
 * shadow-cull / LOD-radius without rebuilding the mod.
 *
 * <p>Format is plain key→value to keep it human-editable. Missing keys
 * inherit the runtime defaults.</p>
 */
public final class BBSToolsConfig
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/config");

    /**
     * Feature-name → gewuenschter zustand, aus der config gelesen.
     *
     * <p>Nur die namen, die der nutzer im Optimize-menue bewusst gesetzt hat. Was hier
     * NICHT drinsteht, bleibt dem start-sweep ueberlassen — so wachsen neue features
     * weiter mit, ohne dass jemand die config pflegen muss.</p>
     */
    private static final Map<String, Boolean> gespeicherteFeatures = new LinkedHashMap<>();

    private BBSToolsConfig()
    {
    }

    /** Die gespeicherten feature-wuensche (leer, solange nichts geladen wurde). */
    public static Map<String, Boolean> gespeicherteFeatures()
    {
        return gespeicherteFeatures;
    }

    /** Einen feature-wunsch merken. Schreibt noch nicht — dafuer {@link #save()} rufen. */
    public static void merkeFeature(String name, boolean an)
    {
        gespeicherteFeatures.put(name, an);
    }

    private static File configFile()
    {
        File configDir = FabricLoader.getInstance().getConfigDir().toFile();
        File ours = new File(configDir, "bbstools");
        if (!ours.exists()) ours.mkdirs();
        return new File(ours, "config.json");
    }

    public static void loadOrCreate()
    {
        File f = configFile();
        if (!f.exists())
        {
            save();
            return;
        }

        try
        {
            String text = Files.readString(f.toPath());
            JsonObject root = JsonParser.parseString(text).getAsJsonObject();

            if (root.has("enable_distance_culling"))
                BBSPerfConfig.ENABLE_DISTANCE_CULLING = root.get("enable_distance_culling").getAsBoolean();
            if (root.has("max_render_distance"))
            {
                double d = root.get("max_render_distance").getAsDouble();
                BBSPerfConfig.MAX_RENDER_DISTANCE_SQ = d * d;
            }
            if (root.has("shadow_culling_distance"))
            {
                double d = root.get("shadow_culling_distance").getAsDouble();
                BBSPerfConfig.SHADOW_CULLING_DISTANCE_SQ = d * d;
            }
            if (root.has("skip_all_shadow_passes"))
                BBSPerfConfig.SKIP_ALL_SHADOW_PASSES = root.get("skip_all_shadow_passes").getAsBoolean();
            if (root.has("enable_lod"))
                BBSPerfConfig.ENABLE_LOD = root.get("enable_lod").getAsBoolean();
            if (root.has("lod_detail_radius"))
            {
                double d = root.get("lod_detail_radius").getAsDouble();
                BBSPerfConfig.LOD_DETAIL_RADIUS_SQ = d * d;
            }
            if (root.has("enable_veil_batch"))
                BBSPerfConfig.ENABLE_VEIL_BATCH = root.get("enable_veil_batch").getAsBoolean();
            if (root.has("veil_batch_max_bones"))
                BBSPerfConfig.VEIL_BATCH_MAX_BONES = root.get("veil_batch_max_bones").getAsInt();

            /* Feature-schalter aus dem Optimize-menue. Werden hier nur EINGELESEN;
             * angewendet werden sie in FeatureSchalter.anwendenNachSweep(), weil der
             * start-sweep sonst darueber huepft (er laeuft spaeter, bei CLIENT_STARTED). */
            if (root.has("features") && root.get("features").isJsonObject())
            {
                JsonObject features = root.getAsJsonObject("features");

                gespeicherteFeatures.clear();

                for (String name : features.keySet())
                {
                    gespeicherteFeatures.put(name, features.get(name).getAsBoolean());
                }
            }

            LOG.info("Loaded config from {}", f);
        }
        catch (Throwable t)
        {
            LOG.warn("Failed to load config — falling back to defaults: {}", t.toString());
        }
    }

    public static void save()
    {
        File f = configFile();

        /* Hand-written JSON to keep deterministic key order + readable. */
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("enable_distance_culling", BBSPerfConfig.ENABLE_DISTANCE_CULLING);
        values.put("max_render_distance", Math.sqrt(BBSPerfConfig.MAX_RENDER_DISTANCE_SQ));
        values.put("shadow_culling_distance", Math.sqrt(BBSPerfConfig.SHADOW_CULLING_DISTANCE_SQ));
        values.put("skip_all_shadow_passes", BBSPerfConfig.SKIP_ALL_SHADOW_PASSES);
        values.put("enable_lod", BBSPerfConfig.ENABLE_LOD);
        values.put("lod_detail_radius", Math.sqrt(BBSPerfConfig.LOD_DETAIL_RADIUS_SQ));
        values.put("enable_veil_batch", BBSPerfConfig.ENABLE_VEIL_BATCH);
        values.put("veil_batch_max_bones", BBSPerfConfig.VEIL_BATCH_MAX_BONES);

        StringBuilder sb = new StringBuilder("{\n");
        int i = 0;
        for (Map.Entry<String, Object> e : values.entrySet())
        {
            sb.append("  \"").append(e.getKey()).append("\": ");
            Object v = e.getValue();
            if (v instanceof Boolean) sb.append(v);
            else if (v instanceof Double) sb.append(String.format(java.util.Locale.ROOT, "%.1f", (Double) v));
            else if (v instanceof Integer) sb.append(v);
            else sb.append("\"").append(v).append("\"");
            /* Komma auch hinter dem LETZTEN wert, wenn danach noch der features-block
             * kommt — sonst entsteht ungueltiges JSON und die config faellt beim naechsten
             * start still auf die defaults zurueck. */
            if (i++ < values.size() - 1 || !gespeicherteFeatures.isEmpty()) sb.append(",");
            sb.append("\n");
        }

        /* features als verschachteltes objekt — ein flacher key pro feature waere bei
         * 80+ schaltern unleserlich, und so bleibt sichtbar, was zusammengehoert. */
        if (!gespeicherteFeatures.isEmpty())
        {
            sb.append("  \"features\": {\n");

            int j = 0;

            for (Map.Entry<String, Boolean> e : gespeicherteFeatures.entrySet())
            {
                sb.append("    \"").append(e.getKey()).append("\": ").append(e.getValue());
                if (j++ < gespeicherteFeatures.size() - 1) sb.append(",");
                sb.append("\n");
            }

            sb.append("  }\n");
        }

        sb.append("}\n");

        try
        {
            Files.writeString(f.toPath(), sb.toString());
            LOG.info("Saved config to {}", f);
        }
        catch (IOException ex)
        {
            LOG.warn("Failed to save config: {}", ex.toString());
        }
    }

}
