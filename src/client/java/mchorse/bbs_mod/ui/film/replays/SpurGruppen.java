package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeGroup;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Bündelt die Hand- und HUD-Spuren des Replays zu einer aufklappbaren Spur.
 *
 * <p>USER-WUNSCH 2026-07-28: „hotbar und hud keyframe tracks alle in einem klapbar zu machen.
 * Man sieht einen keyframe track der nennt sich Hand/hud und wenn man den aufklappt sieht man
 * alle keyframe tracks die man braucht." Zugeklappt kostet der ganze Block eine Zeile statt 23 —
 * die Spurliste des Replay-Editors war dadurch praktisch nur noch Hotbar.</p>
 *
 * <p>Die Aufklapp-Mechanik selbst lag bereits fertig im Dope-Sheet ({@link UIKeyframeGroup} samt
 * Layout, Beschriftung, Pfeil und Klick) — sie wurde nur nirgends benutzt. Hier wird sie
 * angewandt, nichts davon neu gebaut.</p>
 */
public final class SpurGruppen
{
    /** Welche kuratierten Kanäle in die Sammel-Spur wandern. */
    private static final Set<String> HAND_HUD = new HashSet<>(Arrays.asList(
        "selected_slot",
        "hotbar_slot_1", "hotbar_slot_2", "hotbar_slot_3", "hotbar_slot_4", "hotbar_slot_5",
        "hotbar_slot_6", "hotbar_slot_7", "hotbar_slot_8", "hotbar_slot_9",
        "item_main_hand", "item_off_hand", "item_head", "item_chest", "item_legs", "item_feet",
        "health", "hunger",
        "show_hand", "show_hud",
        "using_item", "item_use_time", "active_hand"
    ));

    /**
     * Aufgeklappt-Zustand, absichtlich statisch: die Spurliste wird bei jedem Replay-Wechsel und
     * bei jeder Filter-Änderung komplett neu gebaut. Am Editor gehalten würde die Gruppe dabei
     * jedes Mal wieder zuklappen.
     */
    private static boolean handHudAufgeklappt;

    private SpurGruppen()
    {
    }

    /**
     * Hängt die Spuren in den Editor — die Hand/HUD-Spuren gebündelt an der Stelle der ersten
     * von ihnen, alle übrigen unverändert einzeln.
     */
    public static void einfuegen(UIKeyframes view, List<UIKeyframeSheet> sheets)
    {
        List<UIKeyframeSheet> gebuendelt = new ArrayList<>();

        if (gruppierungAn())
        {
            for (UIKeyframeSheet sheet : sheets)
            {
                if (HAND_HUD.contains(sheet.id))
                {
                    gebuendelt.add(sheet);
                }
            }
        }

        /* Unter zwei Treffern lohnt keine Kopfzeile — sie kostete dann mehr Zeilen als sie spart. */
        if (gebuendelt.size() < 2)
        {
            for (UIKeyframeSheet sheet : sheets)
            {
                view.addSheet(sheet);
            }

            return;
        }

        UIKeyframeGroup gruppe = new UIKeyframeGroup(titel(), gebuendelt.get(0).color);

        gruppe.collapsed = !handHudAufgeklappt;
        gruppe.onToggle = () -> handHudAufgeklappt = !gruppe.collapsed;

        for (UIKeyframeSheet sheet : gebuendelt)
        {
            gruppe.add(sheet);
        }

        boolean gesetzt = false;

        for (UIKeyframeSheet sheet : sheets)
        {
            if (HAND_HUD.contains(sheet.id))
            {
                if (!gesetzt)
                {
                    view.addElement(gruppe);

                    gesetzt = true;
                }

                continue;
            }

            view.addSheet(sheet);
        }
    }

    /** Wie oben: der Sprachschlüssel ist neu, nach einem Hotswap steht dort null. */
    private static IKey titel()
    {
        IKey key = UIKeys.FILM_REPLAY_GROUP_HAND_HUD;

        return key == null ? IKey.constant("Hand/HUD") : key;
    }

    /**
     * ⚠ Der Schalter ist ein neues statisches Feld. Beim Hotswap laufen statische Initialisierer
     * nicht erneut, das Feld bleibt dann null und würde die ganze Spurliste mit einer NPE
     * abreißen. Im Zweifel gruppieren — das ist der Zustand, den der Nutzer bestellt hat.
     */
    private static boolean gruppierungAn()
    {
        try
        {
            return BBSSettings.editorGroupHandHudTracks.get();
        }
        catch (Throwable t)
        {
            return true;
        }
    }
}
