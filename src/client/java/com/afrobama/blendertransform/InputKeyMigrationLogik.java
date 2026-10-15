package com.afrobama.blendertransform;

import com.mojang.blaze3d.platform.InputConstants;

/** The old -1 keyboard sentinel is unbound; mouse indices and valid scancodes retain identity. */
public final class InputKeyMigrationLogik
{
    private InputKeyMigrationLogik()
    {}

    public static InputConstants.Key legacyUnknown(InputConstants.Key key)
    {
        return key.getType() == InputConstants.Type.KEYBOARD && key.getValue() == -1
            ? InputConstants.UNKNOWN : key;
    }
}
