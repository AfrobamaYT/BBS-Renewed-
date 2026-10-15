package mchorse.bbs_mod.ui.utils.keys;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.utils.OS;
import mchorse.bbs_mod.l10n.L10n;
//? if >=26.3 {
/*import org.lwjgl.sdl.SDLKeyboard;
*///?} else {
import org.lwjgl.glfw.GLFW;
//?}
import org.lwjgl.glfw.GLFW; /* SDLScancode */

import java.util.HashMap;
import java.util.Map;

public class KeyCodes
{
    private static final Map<Character, Character> CYRILLIC_TO_QWERTY = new HashMap<>();

    static
    {
        String ruLower = "ёйцукенгшщзхъфывапролджэячсмитьбю";
        String enLower = "`qwertyuiop[]asdfghjkl;'zxcvbnm,.";

        String ruUpper = "ЁЙЦУКЕНГШЩЗХЪФЫВАПРОЛДЖЭЯЧСМИТЬБЮ";
        String enUpper = "~QWERTYUIOP{}ASDFGHJKL:\"ZXCVBNM<>";

        for (int i = 0; i < ruLower.length(); i++) CYRILLIC_TO_QWERTY.put(ruLower.charAt(i), enLower.charAt(i));
        for (int i = 0; i < ruUpper.length(); i++) CYRILLIC_TO_QWERTY.put(ruUpper.charAt(i), enUpper.charAt(i));
    }

    public static String cyrillicToQwerty(String input)
    {
        if (input == null || input.isEmpty())
        {
            return input;
        }

        StringBuilder out = new StringBuilder(input.length());

        for (int i = 0; i < input.length(); i++)
        {
            char ch = input.charAt(i);

            out.append(CYRILLIC_TO_QWERTY.getOrDefault(ch, ch));
        }

        return out.toString();
    }

    public static String getName(int keyCode)
    {
        if (LegacyKeyboardKeys.isUnmapped(keyCode))
        {
            return L10n.lang("bbs.ui.keys.legacy_unavailable").format("F25").get();
        }

        if (keyCode == 0)
        {
            return "N/A";
        }

        if (keyCode < 0)
        {
            switch (keyCode)
            {
                case -0: return "LMB";
                case -1: return "RMB";
                case -2: return "MMB";
            }

            return "Mouse " + (-keyCode + 1);
        }

        if (BBSSettings.forceQwerty.get())
        {
            switch (keyCode)
            {
                case GLFW.GLFW_KEY_0: return "0";
                case GLFW.GLFW_KEY_1: return "1";
                case GLFW.GLFW_KEY_2: return "2";
                case GLFW.GLFW_KEY_3: return "3";
                case GLFW.GLFW_KEY_4: return "4";
                case GLFW.GLFW_KEY_5: return "5";
                case GLFW.GLFW_KEY_6: return "6";
                case GLFW.GLFW_KEY_7: return "7";
                case GLFW.GLFW_KEY_8: return "8";
                case GLFW.GLFW_KEY_9: return "9";
                case GLFW.GLFW_KEY_A: return "A";
                case GLFW.GLFW_KEY_B: return "B";
                case GLFW.GLFW_KEY_C: return "C";
                case GLFW.GLFW_KEY_D: return "D";
                case GLFW.GLFW_KEY_E: return "E"; /* Nice! */
                case GLFW.GLFW_KEY_F: return "F";
                case GLFW.GLFW_KEY_G: return "G";
                case GLFW.GLFW_KEY_H: return "H";
                case GLFW.GLFW_KEY_I: return "I";
                case GLFW.GLFW_KEY_J: return "J";
                case GLFW.GLFW_KEY_K: return "K";
                case GLFW.GLFW_KEY_L: return "L";
                case GLFW.GLFW_KEY_M: return "M";
                case GLFW.GLFW_KEY_N: return "N";
                case GLFW.GLFW_KEY_O: return "O";
                case GLFW.GLFW_KEY_P: return "P";
                case GLFW.GLFW_KEY_Q: return "Q";
                case GLFW.GLFW_KEY_R: return "R";
                case GLFW.GLFW_KEY_S: return "S";
                case GLFW.GLFW_KEY_T: return "T";
                case GLFW.GLFW_KEY_U: return "U";
                case GLFW.GLFW_KEY_V: return "V";
                case GLFW.GLFW_KEY_W: return "W";
                case GLFW.GLFW_KEY_X: return "X";
                case GLFW.GLFW_KEY_Y: return "Y";
                case GLFW.GLFW_KEY_Z: return "Z";
            }
        }

        switch (keyCode)
        {
            case GLFW.GLFW_KEY_SPACE: return "Space";
            case GLFW.GLFW_KEY_ESCAPE: return "Escape";
            case GLFW.GLFW_KEY_ENTER: return "Enter";
            case GLFW.GLFW_KEY_TAB: return "Tab";
            case GLFW.GLFW_KEY_BACKSPACE: return "Backspace";
            case GLFW.GLFW_KEY_INSERT: return "Insert";
            case GLFW.GLFW_KEY_DELETE: return "Delete";
            case GLFW.GLFW_KEY_RIGHT: return "Right";
            case GLFW.GLFW_KEY_LEFT: return "Left";
            case GLFW.GLFW_KEY_DOWN: return "Down";
            case GLFW.GLFW_KEY_UP: return "Up";
            case GLFW.GLFW_KEY_PAGE_UP: return "Page up";
            case GLFW.GLFW_KEY_PAGE_DOWN: return "Page down";
            case GLFW.GLFW_KEY_HOME: return "Home";
            case GLFW.GLFW_KEY_END: return "End";
            case GLFW.GLFW_KEY_CAPS_LOCK: return "Capslock";
            case GLFW.GLFW_KEY_SCROLL_LOCK: return "Scrolllock";
            case GLFW.GLFW_KEY_NUM_LOCK: return "Numlock";
            case GLFW.GLFW_KEY_PRINT_SCREEN: return "Printscreen";
            case GLFW.GLFW_KEY_PAUSE: return "Pause";
            /* Shift */
            case GLFW.GLFW_KEY_LEFT_SHIFT: return "Shift";
            /* Control */
            case GLFW.GLFW_KEY_LEFT_CONTROL: return "Ctrl";
            /* Alt/Option */
            case GLFW.GLFW_KEY_LEFT_ALT: return "Alt";
            /* Command/Windows */
            case GLFW.GLFW_KEY_LEFT_SUPER: return OS.CURRENT == OS.MACOS ? "Cmd" : "Win";
            case GLFW.GLFW_KEY_RIGHT_SHIFT: return "R.Shift";
            case GLFW.GLFW_KEY_RIGHT_CONTROL: return "R.Ctrl";
            case GLFW.GLFW_KEY_RIGHT_ALT: return "R.Alt";
            case GLFW.GLFW_KEY_RIGHT_SUPER: return "R." + (OS.CURRENT == OS.MACOS ? "Cmd" : "Win");
        }

        //? if >=26.3 {
        /*String name = SDLKeyboard.SDL_GetKeyName(SDLKeyboard.SDL_GetKeyFromScancode(keyCode, (short) 0, true));
        *///?} else {
        String name = GLFW.glfwGetKeyName(keyCode, 0);
        //?}

        /* Der Ziffernblock liegt bei SDL von KP_DIVIDE bis KP_PERIOD, bei GLFW von KP_0 bis KP_EQUAL. */
        //? if >=26.3 {
        /*if ((keyCode >= GLFW.GLFW_KEY_KP_DIVIDE && keyCode <= GLFW.GLFW_KEY_KP_DECIMAL) || keyCode == GLFW.GLFW_KEY_KP_EQUAL)
        *///?} else {
        if (keyCode >= GLFW.GLFW_KEY_KP_0 && keyCode <= GLFW.GLFW_KEY_KP_EQUAL)
        //?}
        {
            switch (keyCode)
            {
                case GLFW.GLFW_KEY_KP_ENTER: name = "Enter"; break;
                case GLFW.GLFW_KEY_KP_EQUAL: name = "="; break;
            }

            return "Num " + name;
        }

        if (name == null || name.isEmpty())
        {
            name = "Key " + keyCode;
        }

        return name.length() == 1 ? name.toUpperCase() : name;
    }
}
