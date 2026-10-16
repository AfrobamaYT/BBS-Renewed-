package mchorse.bbs_mod.ui.film.replays;

import com.mojang.blaze3d.platform.InputConstants;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.utils.keys.KeyCombo;
import mchorse.bbs_mod.ui.utils.pose.UIPoseEditor;

import java.util.ArrayList;
import java.util.List;

/**
 * Katalog A4: Blender's Select Mirror (Shift+Ctrl+M). Every selected bone swaps for its left/right
 * partner (the model's pairs, else the name pattern, as mirrored pasting); bones without one stay.
 */
public final class SpiegelAuswahl
{
    public static final KeyCombo TASTE = new KeyCombo("pose_select_mirror", IKey.raw("Select the mirrored bones (left/right)"),
        InputConstants.KEY_M, InputConstants.KEY_LCONTROL, InputConstants.KEY_LSHIFT).categoryKey("transformations");

    private SpiegelAuswahl()
    {}

    public static void anmelden(UIPoseEditor element)
    {
        element.keys().register(TASTE, () -> spiegeln(element)).category(UIKeys.TRANSFORMS_KEYS_CATEGORY);
    }

    /** False (and a message) when no selected bone has a partner */
    public static boolean spiegeln(UIPoseEditor element)
    {
        List<String> alt = element.groups.list.getCurrent();
        List<String> neu = new ArrayList<>();

        for (String bone : alt)
        {
            String partner = FilmPoseClipboard.partner(bone, element.getFlippedParts());
            String ziel = element.hasBone(partner) ? partner : bone;

            if (!neu.contains(ziel))
            {
                neu.add(ziel);
            }
        }

        if (neu.equals(alt))
        {
            element.getContext().notifyInfo(IKey.raw("No mirrored bone for the selection"));

            return false;
        }

        element.restoreSelection(neu);

        return true;
    }
}
