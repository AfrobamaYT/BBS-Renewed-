package mchorse.bbs_mod.ui.utils.keys;

import org.lwjgl.glfw.GLFW; /* SDLScancode */

/** Converts persisted pre-26.3 keyboard codes to the physical SDL scancodes used by Minecraft. */
public final class LegacyKeyboardKeys
{
    /** Preserved unsupported binding identity, never passed to SDL as a scancode. */
    public static final int UNMAPPED_F25 = Integer.MAX_VALUE;

    public static boolean isUnmapped(int key)
    {
        return key == UNMAPPED_F25;
    }

    public static int fromGlfw(int key)
    {
        if (key <= 0)
        {
            return key == -1 ? GLFW.GLFW_KEY_UNKNOWN : key;
        }

        return switch (key)
        {
            case 314 -> UNMAPPED_F25;
            case 32 -> GLFW.GLFW_KEY_SPACE;
            case 39 -> GLFW.GLFW_KEY_APOSTROPHE;
            case 44 -> GLFW.GLFW_KEY_COMMA;
            case 45 -> GLFW.GLFW_KEY_MINUS;
            case 46 -> GLFW.GLFW_KEY_PERIOD;
            case 47 -> GLFW.GLFW_KEY_SLASH;
            case 48 -> GLFW.GLFW_KEY_0;
            case 49 -> GLFW.GLFW_KEY_1;
            case 50 -> GLFW.GLFW_KEY_2;
            case 51 -> GLFW.GLFW_KEY_3;
            case 52 -> GLFW.GLFW_KEY_4;
            case 53 -> GLFW.GLFW_KEY_5;
            case 54 -> GLFW.GLFW_KEY_6;
            case 55 -> GLFW.GLFW_KEY_7;
            case 56 -> GLFW.GLFW_KEY_8;
            case 57 -> GLFW.GLFW_KEY_9;
            case 59 -> GLFW.GLFW_KEY_SEMICOLON;
            case 61 -> GLFW.GLFW_KEY_EQUAL;
            case 65 -> GLFW.GLFW_KEY_A;
            case 66 -> GLFW.GLFW_KEY_B;
            case 67 -> GLFW.GLFW_KEY_C;
            case 68 -> GLFW.GLFW_KEY_D;
            case 69 -> GLFW.GLFW_KEY_E;
            case 70 -> GLFW.GLFW_KEY_F;
            case 71 -> GLFW.GLFW_KEY_G;
            case 72 -> GLFW.GLFW_KEY_H;
            case 73 -> GLFW.GLFW_KEY_I;
            case 74 -> GLFW.GLFW_KEY_J;
            case 75 -> GLFW.GLFW_KEY_K;
            case 76 -> GLFW.GLFW_KEY_L;
            case 77 -> GLFW.GLFW_KEY_M;
            case 78 -> GLFW.GLFW_KEY_N;
            case 79 -> GLFW.GLFW_KEY_O;
            case 80 -> GLFW.GLFW_KEY_P;
            case 81 -> GLFW.GLFW_KEY_Q;
            case 82 -> GLFW.GLFW_KEY_R;
            case 83 -> GLFW.GLFW_KEY_S;
            case 84 -> GLFW.GLFW_KEY_T;
            case 85 -> GLFW.GLFW_KEY_U;
            case 86 -> GLFW.GLFW_KEY_V;
            case 87 -> GLFW.GLFW_KEY_W;
            case 88 -> GLFW.GLFW_KEY_X;
            case 89 -> GLFW.GLFW_KEY_Y;
            case 90 -> GLFW.GLFW_KEY_Z;
            case 91 -> GLFW.GLFW_KEY_LEFT_BRACKET;
            case 92 -> GLFW.GLFW_KEY_BACKSLASH;
            case 93 -> GLFW.GLFW_KEY_RIGHT_BRACKET;
            case 96 -> GLFW.GLFW_KEY_GRAVE_ACCENT;
            case 161 -> GLFW.GLFW_KEY_WORLD_1;
            case 162 -> GLFW.GLFW_KEY_WORLD_2;
            case 256 -> GLFW.GLFW_KEY_ESCAPE;
            case 257 -> GLFW.GLFW_KEY_ENTER;
            case 258 -> GLFW.GLFW_KEY_TAB;
            case 259 -> GLFW.GLFW_KEY_BACKSPACE;
            case 260 -> GLFW.GLFW_KEY_INSERT;
            case 261 -> GLFW.GLFW_KEY_DELETE;
            case 262 -> GLFW.GLFW_KEY_RIGHT;
            case 263 -> GLFW.GLFW_KEY_LEFT;
            case 264 -> GLFW.GLFW_KEY_DOWN;
            case 265 -> GLFW.GLFW_KEY_UP;
            case 266 -> GLFW.GLFW_KEY_PAGE_UP;
            case 267 -> GLFW.GLFW_KEY_PAGE_DOWN;
            case 268 -> GLFW.GLFW_KEY_HOME;
            case 269 -> GLFW.GLFW_KEY_END;
            case 280 -> GLFW.GLFW_KEY_CAPS_LOCK;
            case 281 -> GLFW.GLFW_KEY_SCROLL_LOCK;
            case 282 -> GLFW.GLFW_KEY_NUM_LOCK;
            case 283 -> GLFW.GLFW_KEY_PRINT_SCREEN;
            case 284 -> GLFW.GLFW_KEY_PAUSE;
            case 290 -> GLFW.GLFW_KEY_F1;
            case 291 -> GLFW.GLFW_KEY_F2;
            case 292 -> GLFW.GLFW_KEY_F3;
            case 293 -> GLFW.GLFW_KEY_F4;
            case 294 -> GLFW.GLFW_KEY_F5;
            case 295 -> GLFW.GLFW_KEY_F6;
            case 296 -> GLFW.GLFW_KEY_F7;
            case 297 -> GLFW.GLFW_KEY_F8;
            case 298 -> GLFW.GLFW_KEY_F9;
            case 299 -> GLFW.GLFW_KEY_F10;
            case 300 -> GLFW.GLFW_KEY_F11;
            case 301 -> GLFW.GLFW_KEY_F12;
            case 302 -> GLFW.GLFW_KEY_F13;
            case 303 -> GLFW.GLFW_KEY_F14;
            case 304 -> GLFW.GLFW_KEY_F15;
            case 305 -> GLFW.GLFW_KEY_F16;
            case 306 -> GLFW.GLFW_KEY_F17;
            case 307 -> GLFW.GLFW_KEY_F18;
            case 308 -> GLFW.GLFW_KEY_F19;
            case 309 -> GLFW.GLFW_KEY_F20;
            case 310 -> GLFW.GLFW_KEY_F21;
            case 311 -> GLFW.GLFW_KEY_F22;
            case 312 -> GLFW.GLFW_KEY_F23;
            case 313 -> GLFW.GLFW_KEY_F24;
            case 320 -> GLFW.GLFW_KEY_KP_0;
            case 321 -> GLFW.GLFW_KEY_KP_1;
            case 322 -> GLFW.GLFW_KEY_KP_2;
            case 323 -> GLFW.GLFW_KEY_KP_3;
            case 324 -> GLFW.GLFW_KEY_KP_4;
            case 325 -> GLFW.GLFW_KEY_KP_5;
            case 326 -> GLFW.GLFW_KEY_KP_6;
            case 327 -> GLFW.GLFW_KEY_KP_7;
            case 328 -> GLFW.GLFW_KEY_KP_8;
            case 329 -> GLFW.GLFW_KEY_KP_9;
            case 330 -> GLFW.GLFW_KEY_KP_DECIMAL;
            case 331 -> GLFW.GLFW_KEY_KP_DIVIDE;
            case 332 -> GLFW.GLFW_KEY_KP_MULTIPLY;
            case 333 -> GLFW.GLFW_KEY_KP_SUBTRACT;
            case 334 -> GLFW.GLFW_KEY_KP_ADD;
            case 335 -> GLFW.GLFW_KEY_KP_ENTER;
            case 336 -> GLFW.GLFW_KEY_KP_EQUAL;
            case 340 -> GLFW.GLFW_KEY_LEFT_SHIFT;
            case 341 -> GLFW.GLFW_KEY_LEFT_CONTROL;
            case 342 -> GLFW.GLFW_KEY_LEFT_ALT;
            case 343 -> GLFW.GLFW_KEY_LEFT_SUPER;
            case 344 -> GLFW.GLFW_KEY_RIGHT_SHIFT;
            case 345 -> GLFW.GLFW_KEY_RIGHT_CONTROL;
            case 346 -> GLFW.GLFW_KEY_RIGHT_ALT;
            case 347 -> GLFW.GLFW_KEY_RIGHT_SUPER;
            case 348 -> GLFW.GLFW_KEY_MENU;
            default -> throw new IllegalArgumentException("Unsupported pre-26.3 keyboard code: " + key);
        };
    }

    private LegacyKeyboardKeys()
    {}
}
