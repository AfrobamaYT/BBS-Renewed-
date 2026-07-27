package com.afrobama.bbstools.client.keybinds;

import net.fabricmc.loader.api.FabricLoader;

import mchorse.bbs_mod.ui.utils.keys.KeyCombo;

import org.slf4j.Logger;
import mchorse.bbs_mod.ui.utils.keys.KeyCombo;

import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Preset-aware persistence of {@link KeyCombo} bindings. Multiple named
 * presets each store a {@code id → key-array} map of overrides relative
 * to the hardcoded BBS defaults. One preset is "active" at a time and
 * its overrides are applied to all live combos.
 *
 * <p>File: {@code config/bbs/keybind_presets.json} with format:</p>
 * <pre>
 *  {
 *    "active": "Left-Hand",
 *    "presets": {
 *      "BBS Default": {},
 *      "Left-Hand": { "next": [69], "prev": [81], … }
 *    }
 *  }
 * </pre>
 *
 * <p>Two presets are auto-created on first run: an empty {@code "BBS
 * Default"} (factory keybinds) and {@code "Left-Hand"} (the optimised
 * left-hand-only layout). Users can add unlimited custom presets via
 * the {@link mchorse.bbs_mod.ui.framework.elements.input.UIKeybindEditor}.</p>
 */
public final class KeybindOverrides
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/keybinds");

    public static final String PRESET_DEFAULT = "BBS Default";
    public static final String PRESET_LEFT_HAND = "Left-Hand";

    /** Combo id → KeyCombo instance. Mutating its key list propagates
     *  immediately to every callsite. */
    private static final Map<String, KeyCombo> registry = new LinkedHashMap<>();

    /** Combo id → factory default key array. Captured at first
     *  registration so reset works without re-creating combos. */
    private static final Map<String, int[]> defaultKeys = new LinkedHashMap<>();

    /** name → (id → key-array). Always contains PRESET_DEFAULT (empty)
     *  and at least one user-editable preset. */
    private static final Map<String, Map<String, int[]>> presets = new LinkedHashMap<>();

    private static String activePreset = PRESET_DEFAULT;

    /** Pending overrides for combos not yet registered (load can race
     *  with class init). Drained as combos register. */
    private static boolean loaded;

    private KeybindOverrides()
    {
    }

    /* === KeyCombo registration === */

    public static synchronized void register(KeyCombo combo)
    {
        if (combo == null || combo.id == null || combo.id.isEmpty()) return;
        if (registry.containsKey(combo.id)) return;

        registry.put(combo.id, combo);

        int[] defaults = new int[combo.keys.size()];
        for (int i = 0; i < combo.keys.size(); i++) defaults[i] = combo.keys.get(i);
        defaultKeys.put(combo.id, defaults);

        if (loaded)
        {
            int[] override = activeOverride(combo.id);
            if (override != null) applyKeys(combo, override);
        }
    }

    private static int[] activeOverride(String id)
    {
        Map<String, int[]> active = presets.get(activePreset);
        return active == null ? null : active.get(id);
    }

    public static synchronized Map<String, KeyCombo> all()
    {
        return Collections.unmodifiableMap(registry);
    }

    /**
     * Returns every other registered KeyCombo whose key set is identical
     * to the given combo's. Order of keys doesn't matter — Shift+E and
     * E+Shift conflict because the user can't distinguish them.
     *
     * <p>Empty / unbound combos return an empty list (an unbound combo
     * isn't a real binding and shouldn't be flagged as conflicting).</p>
     */
    public static synchronized List<KeyCombo> findConflicts(KeyCombo combo)
    {
        List<KeyCombo> conflicts = new ArrayList<>();

        if (combo == null || combo.keys == null || combo.keys.isEmpty())
        {
            return conflicts;
        }

        java.util.Set<Integer> mine = new java.util.HashSet<>(combo.keys);

        for (KeyCombo other : registry.values())
        {
            if (other == combo) continue;
            if (other.id != null && other.id.equals(combo.id)) continue;
            if (other.keys == null || other.keys.isEmpty()) continue;
            if (other.keys.size() != combo.keys.size()) continue;

            java.util.Set<Integer> theirs = new java.util.HashSet<>(other.keys);
            if (mine.equals(theirs))
            {
                conflicts.add(other);
            }
        }

        return conflicts;
    }

    public static synchronized void resetToDefault(KeyCombo combo)
    {
        int[] defaults = defaultKeys.get(combo.id);
        if (defaults == null) return;
        applyKeys(combo, defaults);

        Map<String, int[]> active = ensurePresetMap(activePreset);
        active.remove(combo.id);

        save();
    }

    public static synchronized void resetAll()
    {
        for (KeyCombo combo : registry.values())
        {
            int[] defaults = defaultKeys.get(combo.id);
            if (defaults != null) applyKeys(combo, defaults);
        }
        Map<String, int[]> active = ensurePresetMap(activePreset);
        active.clear();
        save();
    }

    public static synchronized boolean isCustom(KeyCombo combo)
    {
        int[] defaults = defaultKeys.get(combo.id);
        if (defaults == null) return false;
        if (defaults.length != combo.keys.size()) return true;
        for (int i = 0; i < defaults.length; i++)
        {
            if (defaults[i] != combo.keys.get(i)) return true;
        }
        return false;
    }

    /* === Preset management === */

    public static synchronized List<String> getPresetNames()
    {
        return new ArrayList<>(presets.keySet());
    }

    public static synchronized String getActivePreset()
    {
        return activePreset;
    }

    /**
     * Switch to a different preset. Live combos reset to factory then
     * gain the new preset's overrides. Persists the new active name.
     */
    public static synchronized void setActivePreset(String name)
    {
        if (!presets.containsKey(name)) return;
        activePreset = name;

        /* Reset all combos to defaults, then layer the new preset on top. */
        for (KeyCombo combo : registry.values())
        {
            int[] defaults = defaultKeys.get(combo.id);
            if (defaults != null) applyKeys(combo, defaults);
        }
        Map<String, int[]> active = presets.get(name);
        if (active != null)
        {
            for (Map.Entry<String, int[]> e : active.entrySet())
            {
                KeyCombo c = registry.get(e.getKey());
                if (c != null) applyKeys(c, e.getValue());
            }
        }

        save();
    }

    public static synchronized void createPreset(String name)
    {
        if (name == null || name.isEmpty() || presets.containsKey(name)) return;
        presets.put(name, new LinkedHashMap<>());
        save();
    }

    public static synchronized void deletePreset(String name)
    {
        if (PRESET_DEFAULT.equals(name)) return;
        presets.remove(name);
        if (activePreset.equals(name))
        {
            activePreset = PRESET_DEFAULT;
            setActivePreset(PRESET_DEFAULT);
        }
        else
        {
            save();
        }
    }

    /**
     * Save current state of all combos as the named preset. If the
     * preset doesn't exist it's created. Active preset becomes this one.
     */
    public static synchronized void snapshotCurrentAs(String name)
    {
        Map<String, int[]> map = new LinkedHashMap<>();
        for (KeyCombo combo : registry.values())
        {
            if (isCustom(combo))
            {
                int[] arr = new int[combo.keys.size()];
                for (int i = 0; i < combo.keys.size(); i++) arr[i] = combo.keys.get(i);
                map.put(combo.id, arr);
            }
        }
        presets.put(name, map);
        activePreset = name;
        save();
    }

    /**
     * Called by UIKeybindEditor when a single combo is rebound. Updates
     * both the live KeyCombo and the active preset's stored map.
     *
     * <p>Safeguard: if {@code defaultKeys} doesn't have an entry for
     * this combo's id (combo registered AFTER load, or not at all), we
     * cannot reliably tell whether the new binding matches default. In
     * that case we ALWAYS write the current keys as an override —
     * never silently delete the user's existing preset entry. This
     * prevents the data-loss bug seen on 2026-05-08 where a broken
     * KeyComboMixin left {@code defaultKeys} empty and every "edit"
     * silently stripped entries from the active preset.</p>
     */
    public static synchronized void onComboEdited(KeyCombo combo)
    {
        Map<String, int[]> active = ensurePresetMap(activePreset);

        boolean defaultsKnown = defaultKeys.containsKey(combo.id);

        if (!defaultsKnown || isCustom(combo))
        {
            int[] arr = new int[combo.keys.size()];
            for (int i = 0; i < combo.keys.size(); i++) arr[i] = combo.keys.get(i);
            active.put(combo.id, arr);
        }
        else
        {
            active.remove(combo.id);
        }

        save();
    }

    private static Map<String, int[]> ensurePresetMap(String name)
    {
        return presets.computeIfAbsent(name, (k) -> new LinkedHashMap<>());
    }

    private static void applyKeys(KeyCombo combo, int[] keys)
    {
        combo.keys.clear();
        for (int k : keys) combo.keys.add(k);
    }

    /* === Disk I/O === */

    public static File getFile()
    {
        File configRoot = FabricLoader.getInstance().getConfigDir().toFile();
        File bbsDir = new File(configRoot, "bbs");
        return new File(bbsDir, "keybind_presets.json");
    }

    /** Legacy single-file format from the pre-presets era. Migrated on
     *  first load into the {@link #PRESET_LEFT_HAND} preset since that's
     *  what we shipped users in the v1 keybinds.json. */
    public static File getLegacyFile()
    {
        File configRoot = FabricLoader.getInstance().getConfigDir().toFile();
        File bbsDir = new File(configRoot, "bbs");
        return new File(bbsDir, "keybinds.json");
    }

    public static synchronized void loadFromDisk()
    {
        loaded = true;
        File file = getFile();

        /* Always seed PRESET_DEFAULT (empty) and PRESET_LEFT_HAND (the
         *  optimised layout) so they're always available. */
        presets.computeIfAbsent(PRESET_DEFAULT, (k) -> new LinkedHashMap<>());
        presets.computeIfAbsent(PRESET_LEFT_HAND, (k) -> buildLeftHandLayout());

        if (file.exists())
        {
            try
            {
                parsePresetFile(file);
                LOG.info("Loaded keybind presets from {}", file);
            }
            catch (Exception ex)
            {
                LOG.error("Failed to load keybind presets, using defaults", ex);
            }
        }
        else
        {
            /* First run: migrate the old single-file overrides into the
             *  Left-Hand preset, then write a fresh presets file. */
            File legacy = getLegacyFile();
            if (legacy.exists())
            {
                try
                {
                    String body = readAll(legacy);
                    Map<String, int[]> parsed = parseFlatJson(body);
                    if (!parsed.isEmpty())
                    {
                        presets.put(PRESET_LEFT_HAND, parsed);
                        activePreset = PRESET_LEFT_HAND;
                        LOG.info("Migrated legacy keybinds.json into '{}' preset", PRESET_LEFT_HAND);
                    }
                }
                catch (Exception ignored) {}
            }
            else
            {
                /* Brand-new install: pick Left-Hand as the active preset
                 *  so the user gets the curated layout out of the box. */
                activePreset = PRESET_LEFT_HAND;
            }
            save();
        }

        /* Apply active preset's overrides to whatever combos already
         *  registered (rest get applied as they register). */
        Map<String, int[]> active = presets.get(activePreset);
        if (active != null)
        {
            int applied = 0;
            for (Map.Entry<String, int[]> e : active.entrySet())
            {
                KeyCombo c = registry.get(e.getKey());
                if (c != null)
                {
                    applyKeys(c, e.getValue());
                    applied++;
                }
            }
            LOG.info("Applied {} active overrides from preset '{}'", applied, activePreset);
        }

        LOG.info("Registry has {} KeyCombos at loadFromDisk time. Presets: {}",
            registry.size(), presets.keySet());
    }

    public static synchronized void save()
    {
        File file = getFile();
        if (!file.getParentFile().exists()) file.getParentFile().mkdirs();

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file)))
        {
            writer.write("{\n");
            writer.write("  \"active\": \"");
            writer.write(escape(activePreset));
            writer.write("\",\n");
            writer.write("  \"presets\": {\n");

            List<String> names = new ArrayList<>(presets.keySet());
            for (int n = 0; n < names.size(); n++)
            {
                String name = names.get(n);
                Map<String, int[]> map = presets.get(name);

                writer.write("    \"");
                writer.write(escape(name));
                writer.write("\": {");

                if (map == null || map.isEmpty())
                {
                    writer.write("}");
                }
                else
                {
                    writer.write("\n");
                    List<String> ids = new ArrayList<>(map.keySet());
                    for (int i = 0; i < ids.size(); i++)
                    {
                        String id = ids.get(i);
                        int[] keys = map.get(id);

                        writer.write("      \"");
                        writer.write(escape(id));
                        writer.write("\": [");
                        for (int k = 0; k < keys.length; k++)
                        {
                            if (k > 0) writer.write(", ");
                            writer.write(Integer.toString(keys[k]));
                        }
                        writer.write("]");
                        if (i < ids.size() - 1) writer.write(",");
                        writer.write("\n");
                    }
                    writer.write("    }");
                }

                if (n < names.size() - 1) writer.write(",");
                writer.write("\n");
            }

            writer.write("  }\n");
            writer.write("}\n");
        }
        catch (Exception ex)
        {
            LOG.error("Failed to save keybind presets", ex);
        }
    }

    /* === Tiny JSON parsing (no external dep) === */

    private static String readAll(File file) throws Exception
    {
        try (BufferedReader r = new BufferedReader(new FileReader(file)))
        {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        }
    }

    private static void parsePresetFile(File file) throws Exception
    {
        String s = readAll(file);
        int n = s.length();

        /* "active": "name" */
        int activeIdx = s.indexOf("\"active\"");
        if (activeIdx >= 0)
        {
            int colon = s.indexOf(':', activeIdx);
            int q1 = s.indexOf('"', colon);
            int q2 = s.indexOf('"', q1 + 1);
            if (q1 > 0 && q2 > q1)
            {
                activePreset = s.substring(q1 + 1, q2);
            }
        }

        /* "presets": { "name1": {...}, "name2": {...} } */
        int presetsIdx = s.indexOf("\"presets\"");
        if (presetsIdx < 0) return;

        int openBrace = s.indexOf('{', presetsIdx);
        if (openBrace < 0) return;

        int i = openBrace + 1;
        while (i < n)
        {
            /* Find next preset name. */
            while (i < n && s.charAt(i) != '"' && s.charAt(i) != '}') i++;
            if (i >= n || s.charAt(i) == '}') break;

            int nameStart = i + 1;
            int nameEnd = s.indexOf('"', nameStart);
            if (nameEnd < 0) break;
            String name = s.substring(nameStart, nameEnd);
            i = nameEnd + 1;

            /* Find this preset's body { … }. */
            int bodyStart = s.indexOf('{', i);
            if (bodyStart < 0) break;
            int depth = 1;
            int j = bodyStart + 1;
            while (j < n && depth > 0)
            {
                char c = s.charAt(j);
                if (c == '{') depth++;
                else if (c == '}') depth--;
                if (depth == 0) break;
                j++;
            }
            if (j >= n) break;

            String body = s.substring(bodyStart, j + 1);
            Map<String, int[]> parsed = parseFlatJson(body);
            presets.put(name, parsed);

            i = j + 1;
        }
    }

    private static Map<String, int[]> parseFlatJson(String s)
    {
        Map<String, int[]> result = new LinkedHashMap<>();
        int i = 0;
        int n = s.length();

        while (i < n)
        {
            while (i < n && s.charAt(i) != '"') i++;
            if (i >= n) break;
            i++;

            int keyStart = i;
            while (i < n && s.charAt(i) != '"') i++;
            if (i >= n) break;
            String key = s.substring(keyStart, i);
            i++;

            int bracket = s.indexOf('[', i);
            if (bracket < 0) break;
            int closeBr = s.indexOf(']', bracket);
            if (closeBr < 0) break;

            String body = s.substring(bracket + 1, closeBr);
            List<Integer> ints = new ArrayList<>();
            for (String part : body.split(","))
            {
                String t = part.trim();
                if (t.isEmpty()) continue;
                try { ints.add(Integer.parseInt(t)); } catch (NumberFormatException ignored) {}
            }

            int[] arr = new int[ints.size()];
            for (int k = 0; k < arr.length; k++) arr[k] = ints.get(k);
            result.put(key, arr);

            i = closeBr + 1;
        }

        return result;
    }

    private static String escape(String s)
    {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * Hardcoded "Left-Hand" preset — the curated optimisation we ship.
     * Codes are GLFW key constants (lowercase letter codes, F-keys,
     * 340/341/342 = LSHIFT/LCTRL/LALT, 32 = SPACE).
     */
    private static Map<String, int[]> buildLeftHandLayout()
    {
        Map<String, int[]> m = new LinkedHashMap<>();
        m.put("next", new int[]{69});                          /* E */
        m.put("prev", new int[]{81});                          /* Q */
        m.put("jump_forward", new int[]{84});                  /* T */
        m.put("jump_backward", new int[]{82});                 /* R */
        m.put("next_clip", new int[]{69, 340});                /* Shift+E */
        m.put("prev_clip", new int[]{81, 340});                /* Shift+Q */
        m.put("clip_select_after", new int[]{84, 340});        /* Shift+T */
        m.put("clip_select_before", new int[]{82, 340});       /* Shift+R */
        m.put("clip_duration", new int[]{71});                 /* G */
        m.put("clip_enable", new int[]{66});                   /* B */
        m.put("looping", new int[]{70, 340});                  /* Shift+F */
        m.put("looping_set_max", new int[]{50});               /* 2 */
        m.put("looping_set_min", new int[]{49});               /* 1 */
        m.put("fade_in", new int[]{67, 340});                  /* Shift+C */
        m.put("fade_out", new int[]{86, 340});                 /* Shift+V */
        m.put("redo", new int[]{82, 341});                     /* Ctrl+R */
        m.put("delete", new int[]{88, 342});                   /* Alt+X */
        m.put("confirm", new int[]{32, 341});                  /* Ctrl+Space */
        m.put("keybinds", new int[]{298});                     /* F9 — Tab interferes with focus, F9 is rarely used */
        m.put("keybind_editor", new int[]{298, 340});          /* Shift+F9 */
        return m;
    }

}
