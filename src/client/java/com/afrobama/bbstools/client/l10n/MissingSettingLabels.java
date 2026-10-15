// Coded by Afrobama/Claude — restores BBS setting labels dropped by the CML beta-2 upgrade.
package com.afrobama.bbstools.client.l10n;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.LangKey;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Puts back the BBS lang keys that only existed inside the fork's patched CML jar.
 *
 * BACKGROUND: the fork's editor settings (GPU batching, FPS counter, render-entities, replay HUD,
 * FP bobbing, …) were added to CML as a source patch — the BBSSettings FIELDS plus their labels in
 * CML's own en_us.json. The beta-2 upgrade moved onto clean upstream CML, which still REGISTERS all
 * those fields (measured: instanced_block_replays / fps_counter / render_entities / replay_hud are
 * all in BBSSettings.register) but ships none of the labels. BBS derives a setting's label key
 * dynamically as "bbs.config.&lt;category&gt;.&lt;name&gt;", so an absent key renders as the raw key —
 * which is why typing "gpu" in settings surfaced nothing.
 *
 * WHY DIRECT INJECTION AND NOT A REGISTERED LANG FILE: L10n loads lang files through BBS's
 * AssetProvider, whose source packs are fixed to [minecraft, world, assets, http, https] (measured
 * live). There is no "bbstools" source, so a companion Link never resolves — a registered lang file
 * silently loads nothing. Writing straight into L10n.getStrings() is the one path that works from a
 * companion mod without touching BBS source or its asset packs.
 *
 * Existing keys are never overwritten: if a future CML ships its own label, that one wins.
 */
public final class MissingSettingLabels
{
    /** Companion-jar resource holding the recovered keys (plain classpath, not a BBS asset). */
    private static final String RESOURCE = "/assets/bbstools/strings/en_us.json";

    private MissingSettingLabels() {}

    /**
     * Merges the recovered labels into the live string map.
     *
     * @return how many keys were actually added (already-present keys are skipped)
     */
    public static int inject(L10n l10n)
    {
        if (l10n == null)
        {
            return 0;
        }

        JsonObject json = read();

        if (json == null)
        {
            return 0;
        }

        Map<String, LangKey> strings = l10n.getStrings();
        int added = 0;

        for (Map.Entry<String, JsonElement> entry : json.entrySet())
        {
            added += put(strings, entry.getKey(), entry.getValue());

            /* CATEGORY DRIFT: beta-2's BBSSettings.register is missing a category("editor") call
             * before the perf block, so ~45 options that used to live under "editor" (GPU batching,
             * FPS counter, render entities, bake_*, edit_res, …) are now registered under "cdn"
             * — verified against the live config file. BBS asks for the label under the CURRENT
             * category, i.e. bbs.config.cdn.<name>, while our recovered keys say editor.<name>.
             * Register the same label under both prefixes so the option is labelled wherever BBS
             * happens to file it. */
            String key = entry.getKey();

            if (key.startsWith("bbs.config.editor."))
            {
                added += put(strings, "bbs.config.cdn." + key.substring("bbs.config.editor.".length()),
                    entry.getValue());
            }
        }

        return added;
    }

    /** Adds or repairs one label; returns 1 when it changed anything, 0 otherwise. */
    private static int put(Map<String, LangKey> strings, String key, JsonElement value)
    {
        if (value == null || !value.isJsonPrimitive())
        {
            return 0;
        }

        LangKey existing = strings.get(key);

        /* Never clobber a REAL label the running BBS provides itself. A LangKey whose content
         * equals its own key is BBS's miss-placeholder (L10n.lang() caches `new LangKey(null, key,
         * key)` the first time an unknown key is asked for) — measured live: settings had already
         * queried these keys during panel construction, so a plain containsKey() check skipped every
         * single one of them and nothing was restored. */
        if (existing != null && existing.content != null && !existing.content.equals(key))
        {
            return 0;
        }

        String label = value.getAsString();

        if (existing != null)
        {
            /* Mutate in place — UI elements built earlier hold this exact LangKey instance. */
            existing.content = label;
        }
        else
        {
            strings.put(key, new LangKey(null, key, label));
        }

        return 1;
    }

    private static JsonObject read()
    {
        try (InputStream in = MissingSettingLabels.class.getResourceAsStream(RESOURCE))
        {
            if (in == null)
            {
                return null;
            }

            JsonElement parsed = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));

            return parsed != null && parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        }
        catch (Throwable t)
        {
            return null;
        }
    }
}
