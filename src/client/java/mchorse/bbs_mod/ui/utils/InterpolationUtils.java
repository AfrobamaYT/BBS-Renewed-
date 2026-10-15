package mchorse.bbs_mod.ui.utils;

import com.mojang.blaze3d.platform.InputConstants;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.utils.context.ContextAction;
import mchorse.bbs_mod.utils.interps.IInterp;

public class InterpolationUtils
{
    public static void setupKeybind(IInterp interp, ContextAction action, IKey category)
    {
        String key = interp.getKey();

        if (key.endsWith("_in"))
        {
            action.key(category, interp.getKeyCode(), InputConstants.KEY_LSHIFT);
        }
        else if (key.endsWith("_out"))
        {
            action.key(category, interp.getKeyCode(), InputConstants.KEY_LCONTROL);
        }
        else
        {
            action.key(category, interp.getKeyCode());
        }
    }

    public static IKey getName(IInterp interp)
    {
        return UIKeys.C_INTERPOLATION.get(interp.getKey());
    }
}
