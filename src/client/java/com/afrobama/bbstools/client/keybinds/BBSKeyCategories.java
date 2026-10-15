package com.afrobama.bbstools.client.keybinds;

import net.minecraft.client.KeyMapping;
import net.minecraft.resources.ResourceLocation;

/**
 * Die Tastenbelegungs-Kategorien von BBS, an EINER Stelle registriert.
 *
 * <p>Bis 1.21.1 war eine Kategorie ein Zeichenketten-Schlüssel, den jeder Aufrufer selbst
 * hinschrieb ({@code "category.bbstools"}). 26.2 macht daraus einen Typ:
 * {@code String} ist ein Record über einen {@link ResourceLocation}, und eigene
 * Kategorien müssen über {@code Category.register(ResourceLocation)} angemeldet werden
 * (javap: {@code public static KeyMapping$Category register(net.minecraft.resources.ResourceLocation)}).
 *
 * <p>⛔ EINMAL REGISTRIEREN, NICHT JE AUFRUFSTELLE. Die Registrierung ist eine Sammelstelle:
 * zweimal denselben ResourceLocation anzumelden ist bestenfalls überflüssig und schlimmstenfalls
 * ein Startabbruch. Darum stehen die Kategorien hier als statische Felder und werden von
 * den Tastenbelegungen nur noch referenziert.
 *
 * <p>Die Übersetzungsschlüssel bleiben unverändert: der ResourceLocation {@code bbstools:bbstools}
 * ergibt den Schlüssel, den die vorhandenen lang-Dateien schon tragen. Wer hier etwas
 * ändert, muss die lang-Dateien mitziehen.
 */
public final class BBSKeyCategories
{
    /** Sammelkategorie für alle BBSTools-Tastenbelegungen. */
    public static final String BBSTOOLS =
        "category.bbstools.bbstools";

    /** Eigene Kategorie für die Fracture-/Schwerkraftwaffen-Tasten. */
    public static final String FRACTURE =
        "category.bbstools.fracture";

    /** Kategorie des Studio-Teilprojekts. */
    public static final String MCSTUDIO =
        "category.mcstudio.mcstudio";

    /** Hauptkategorie des BBS-Mods selbst (frueher "category." + MOD_ID + ".main"). */
    public static final String BBS_MAIN =
        "category.bbs.main";

    private BBSKeyCategories()
    {}
}
