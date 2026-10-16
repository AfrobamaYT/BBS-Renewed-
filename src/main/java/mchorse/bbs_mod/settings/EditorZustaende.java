package mchorse.bbs_mod.settings;

import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;

/**
 * Schalter, die der Nutzer im Editor umlegt und die einen Neustart ueberleben sollen: Auto Key,
 * die Optionen des Voice-Over-Fensters, Magnet und "nur gewaehlter Bone" der Zeitleiste, das
 * Vorschaumodell des Texture Studios. Bis 2026-10-14 lagen sie in statischen Feldern und waren
 * nach jedem Start wieder auf Werkseinstellung (Nutzer: "die Einstellungen sind nach einem
 * Neustart weg").
 *
 * <p>Gespeichert wie {@code favoriteColors} in {@code BBSSettings}: unsichtbare Werte der
 * "bbs"-Einstellungen. Die UI schreibt sie selbst, jede Aenderung speichert die Datei (Settings
 * meldet postNotify -> saveLater), und in der Einstellungsliste erscheinen sie nicht.</p>
 */
public final class EditorZustaende
{
    public static ValueBoolean autoKey;
    public static ValueInt voiceoverRate;
    public static ValueBoolean voiceoverCountdown;
    public static ValueBoolean voiceoverMute;
    public static ValueBoolean voiceoverBeiReplay;
    public static ValueBoolean zeitleisteMagnetAus;
    public static ValueBoolean zeitleisteNurBone;
    public static ValueString texturStudioModell;
    /* Help (2026-10-14): first-run hint pending | later:<n> | known | watched; where the guide stood (s);
     * the most seen of each chapter (comma list, s); the one-time note before the guide; mini-player x,y. */
    public static ValueString hilfeErststart;
    public static ValueInt hilfeStelle;
    public static ValueString hilfeGesehen;
    public static ValueBoolean hilfeVorspannGesehen;
    public static ValueString hilfeNeuGesehen;
    public static ValueString hilfeMiniPos;
    /* Blender's Animation workspace: the film camera's view docked beside the free viewport */
    public static ValueBoolean kameraAnsicht;

    private EditorZustaende()
    {}

    public static void register(SettingsBuilder builder)
    {
        autoKey = unsichtbar(builder.getBoolean("state_auto_key", false));
        voiceoverRate = unsichtbar(builder.getInt("state_voiceover_rate", 48000));
        voiceoverCountdown = unsichtbar(builder.getBoolean("state_voiceover_countdown", true));
        voiceoverMute = unsichtbar(builder.getBoolean("state_voiceover_mute", false));
        voiceoverBeiReplay = unsichtbar(builder.getBoolean("state_voiceover_with_replay", false));
        zeitleisteMagnetAus = unsichtbar(builder.getBoolean("state_timeline_magnet_off", false));
        zeitleisteNurBone = unsichtbar(builder.getBoolean("state_timeline_only_bone", false));
        texturStudioModell = unsichtbar(builder.getString("state_texture_studio_model", ""));
        hilfeErststart = unsichtbar(builder.getString("state_help_first_run", "pending"));
        hilfeStelle = unsichtbar(builder.getInt("state_help_position", 0));
        hilfeGesehen = unsichtbar(builder.getString("state_help_watched", ""));
        hilfeVorspannGesehen = unsichtbar(builder.getBoolean("state_help_preroll_seen", false));
        hilfeNeuGesehen = unsichtbar(builder.getString("state_help_news_seen", ""));
        hilfeMiniPos = unsichtbar(builder.getString("state_help_mini_pos", ""));
        kameraAnsicht = unsichtbar(builder.getBoolean("state_camera_view_docked", false));
    }

    private static <T extends BaseValue> T unsichtbar(T value)
    {
        value.invisible();

        return value;
    }
}
