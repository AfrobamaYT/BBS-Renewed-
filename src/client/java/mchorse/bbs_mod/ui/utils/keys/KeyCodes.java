package mchorse.bbs_mod.ui.utils.keys;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.utils.OS;
import mchorse.bbs_mod.l10n.L10n;
//? if >=26.3 {
import org.lwjgl.sdl.SDLKeyboard;
//?} else {
/*import org.lwjgl.glfw.GLFW;
*///?}
import org.lwjgl.sdl.SDLScancode;

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
                case SDLScancode.SDL_SCANCODE_0: return "0";
                case SDLScancode.SDL_SCANCODE_1: return "1";
                case SDLScancode.SDL_SCANCODE_2: return "2";
                case SDLScancode.SDL_SCANCODE_3: return "3";
                case SDLScancode.SDL_SCANCODE_4: return "4";
                case SDLScancode.SDL_SCANCODE_5: return "5";
                case SDLScancode.SDL_SCANCODE_6: return "6";
                case SDLScancode.SDL_SCANCODE_7: return "7";
                case SDLScancode.SDL_SCANCODE_8: return "8";
                case SDLScancode.SDL_SCANCODE_9: return "9";
                case SDLScancode.SDL_SCANCODE_A: return "A";
                case SDLScancode.SDL_SCANCODE_B: return "B";
                case SDLScancode.SDL_SCANCODE_C: return "C";
                case SDLScancode.SDL_SCANCODE_D: return "D";
                case SDLScancode.SDL_SCANCODE_E: return "E"; /* Nice! */
                case SDLScancode.SDL_SCANCODE_F: return "F";
                case SDLScancode.SDL_SCANCODE_G: return "G";
                case SDLScancode.SDL_SCANCODE_H: return "H";
                case SDLScancode.SDL_SCANCODE_I: return "I";
                case SDLScancode.SDL_SCANCODE_J: return "J";
                case SDLScancode.SDL_SCANCODE_K: return "K";
                case SDLScancode.SDL_SCANCODE_L: return "L";
                case SDLScancode.SDL_SCANCODE_M: return "M";
                case SDLScancode.SDL_SCANCODE_N: return "N";
                case SDLScancode.SDL_SCANCODE_O: return "O";
                case SDLScancode.SDL_SCANCODE_P: return "P";
                case SDLScancode.SDL_SCANCODE_Q: return "Q";
                case SDLScancode.SDL_SCANCODE_R: return "R";
                case SDLScancode.SDL_SCANCODE_S: return "S";
                case SDLScancode.SDL_SCANCODE_T: return "T";
                case SDLScancode.SDL_SCANCODE_U: return "U";
                case SDLScancode.SDL_SCANCODE_V: return "V";
                case SDLScancode.SDL_SCANCODE_W: return "W";
                case SDLScancode.SDL_SCANCODE_X: return "X";
                case SDLScancode.SDL_SCANCODE_Y: return "Y";
                case SDLScancode.SDL_SCANCODE_Z: return "Z";
            }
        }

        switch (keyCode)
        {
            case SDLScancode.SDL_SCANCODE_SPACE: return "Space";
            case SDLScancode.SDL_SCANCODE_ESCAPE: return "Escape";
            case SDLScancode.SDL_SCANCODE_RETURN: return "Enter";
            case SDLScancode.SDL_SCANCODE_TAB: return "Tab";
            case SDLScancode.SDL_SCANCODE_BACKSPACE: return "Backspace";
            case SDLScancode.SDL_SCANCODE_INSERT: return "Insert";
            case SDLScancode.SDL_SCANCODE_DELETE: return "Delete";
            case SDLScancode.SDL_SCANCODE_RIGHT: return "Right";
            case SDLScancode.SDL_SCANCODE_LEFT: return "Left";
            case SDLScancode.SDL_SCANCODE_DOWN: return "Down";
            case SDLScancode.SDL_SCANCODE_UP: return "Up";
            case SDLScancode.SDL_SCANCODE_PAGEUP: return "Page up";
            case SDLScancode.SDL_SCANCODE_PAGEDOWN: return "Page down";
            case SDLScancode.SDL_SCANCODE_HOME: return "Home";
            case SDLScancode.SDL_SCANCODE_END: return "End";
            case SDLScancode.SDL_SCANCODE_CAPSLOCK: return "Capslock";
            case SDLScancode.SDL_SCANCODE_SCROLLLOCK: return "Scrolllock";
            case SDLScancode.SDL_SCANCODE_NUMLOCKCLEAR: return "Numlock";
            case SDLScancode.SDL_SCANCODE_PRINTSCREEN: return "Printscreen";
            case SDLScancode.SDL_SCANCODE_PAUSE: return "Pause";
            /* Shift */
            case SDLScancode.SDL_SCANCODE_LSHIFT: return "Shift";
            /* Control */
            case SDLScancode.SDL_SCANCODE_LCTRL: return "Ctrl";
            /* Alt/Option */
            case SDLScancode.SDL_SCANCODE_LALT: return "Alt";
            /* Command/Windows */
            case SDLScancode.SDL_SCANCODE_LGUI: return OS.CURRENT == OS.MACOS ? "Cmd" : "Win";
            case SDLScancode.SDL_SCANCODE_RSHIFT: return "R.Shift";
            case SDLScancode.SDL_SCANCODE_RCTRL: return "R.Ctrl";
            case SDLScancode.SDL_SCANCODE_RALT: return "R.Alt";
            case SDLScancode.SDL_SCANCODE_RGUI: return "R." + (OS.CURRENT == OS.MACOS ? "Cmd" : "Win");
        }

        //? if >=26.3 {
        String name = SDLKeyboard.SDL_GetKeyName(SDLKeyboard.SDL_GetKeyFromScancode(keyCode, (short) 0, true));
        //?} else {
        /*String name = GLFW.glfwGetKeyName(keyCode, 0);
        *///?}

        /* Der Ziffernblock liegt bei SDL von KP_DIVIDE bis KP_PERIOD, bei GLFW von KP_0 bis KP_EQUAL. */
        //? if >=26.3 {
        if ((keyCode >= SDLScancode.SDL_SCANCODE_KP_DIVIDE && keyCode <= SDLScancode.SDL_SCANCODE_KP_PERIOD) || keyCode == SDLScancode.SDL_SCANCODE_KP_EQUALS)
        //?} else {
        /*if (keyCode >= GLFW.GLFW_KEY_KP_0 && keyCode <= GLFW.GLFW_KEY_KP_EQUAL)
        *///?}
        {
            switch (keyCode)
            {
                case SDLScancode.SDL_SCANCODE_KP_ENTER: name = "Enter"; break;
                case SDLScancode.SDL_SCANCODE_KP_EQUALS: name = "="; break;
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
