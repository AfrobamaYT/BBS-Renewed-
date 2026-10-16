package mchorse.bbs_mod.ui.utils.keys;

import com.mojang.blaze3d.platform.InputConstants;

public enum KeyAction
{
    PRESSED, RELEASED, REPEAT;

    public static KeyAction get(int action)
    {
        if (action == InputConstants.PRESS)
        {
            return PRESSED;
        }
        else if (action == InputConstants.REPEAT)
        {
            return REPEAT;
        }

        return RELEASED;
    }
}
