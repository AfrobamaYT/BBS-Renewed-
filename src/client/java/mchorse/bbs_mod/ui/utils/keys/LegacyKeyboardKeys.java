package mchorse.bbs_mod.ui.utils.keys;

import org.lwjgl.sdl.SDLScancode;

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
            return key == -1 ? SDLScancode.SDL_SCANCODE_UNKNOWN : key;
        }

        return switch (key)
        {
            case 314 -> UNMAPPED_F25;
            case 32 -> SDLScancode.SDL_SCANCODE_SPACE;
            case 39 -> SDLScancode.SDL_SCANCODE_APOSTROPHE;
            case 44 -> SDLScancode.SDL_SCANCODE_COMMA;
            case 45 -> SDLScancode.SDL_SCANCODE_MINUS;
            case 46 -> SDLScancode.SDL_SCANCODE_PERIOD;
            case 47 -> SDLScancode.SDL_SCANCODE_SLASH;
            case 48 -> SDLScancode.SDL_SCANCODE_0;
            case 49 -> SDLScancode.SDL_SCANCODE_1;
            case 50 -> SDLScancode.SDL_SCANCODE_2;
            case 51 -> SDLScancode.SDL_SCANCODE_3;
            case 52 -> SDLScancode.SDL_SCANCODE_4;
            case 53 -> SDLScancode.SDL_SCANCODE_5;
            case 54 -> SDLScancode.SDL_SCANCODE_6;
            case 55 -> SDLScancode.SDL_SCANCODE_7;
            case 56 -> SDLScancode.SDL_SCANCODE_8;
            case 57 -> SDLScancode.SDL_SCANCODE_9;
            case 59 -> SDLScancode.SDL_SCANCODE_SEMICOLON;
            case 61 -> SDLScancode.SDL_SCANCODE_EQUALS;
            case 65 -> SDLScancode.SDL_SCANCODE_A;
            case 66 -> SDLScancode.SDL_SCANCODE_B;
            case 67 -> SDLScancode.SDL_SCANCODE_C;
            case 68 -> SDLScancode.SDL_SCANCODE_D;
            case 69 -> SDLScancode.SDL_SCANCODE_E;
            case 70 -> SDLScancode.SDL_SCANCODE_F;
            case 71 -> SDLScancode.SDL_SCANCODE_G;
            case 72 -> SDLScancode.SDL_SCANCODE_H;
            case 73 -> SDLScancode.SDL_SCANCODE_I;
            case 74 -> SDLScancode.SDL_SCANCODE_J;
            case 75 -> SDLScancode.SDL_SCANCODE_K;
            case 76 -> SDLScancode.SDL_SCANCODE_L;
            case 77 -> SDLScancode.SDL_SCANCODE_M;
            case 78 -> SDLScancode.SDL_SCANCODE_N;
            case 79 -> SDLScancode.SDL_SCANCODE_O;
            case 80 -> SDLScancode.SDL_SCANCODE_P;
            case 81 -> SDLScancode.SDL_SCANCODE_Q;
            case 82 -> SDLScancode.SDL_SCANCODE_R;
            case 83 -> SDLScancode.SDL_SCANCODE_S;
            case 84 -> SDLScancode.SDL_SCANCODE_T;
            case 85 -> SDLScancode.SDL_SCANCODE_U;
            case 86 -> SDLScancode.SDL_SCANCODE_V;
            case 87 -> SDLScancode.SDL_SCANCODE_W;
            case 88 -> SDLScancode.SDL_SCANCODE_X;
            case 89 -> SDLScancode.SDL_SCANCODE_Y;
            case 90 -> SDLScancode.SDL_SCANCODE_Z;
            case 91 -> SDLScancode.SDL_SCANCODE_LEFTBRACKET;
            case 92 -> SDLScancode.SDL_SCANCODE_BACKSLASH;
            case 93 -> SDLScancode.SDL_SCANCODE_RIGHTBRACKET;
            case 96 -> SDLScancode.SDL_SCANCODE_GRAVE;
            case 161 -> SDLScancode.SDL_SCANCODE_INTERNATIONAL1;
            case 162 -> SDLScancode.SDL_SCANCODE_INTERNATIONAL2;
            case 256 -> SDLScancode.SDL_SCANCODE_ESCAPE;
            case 257 -> SDLScancode.SDL_SCANCODE_RETURN;
            case 258 -> SDLScancode.SDL_SCANCODE_TAB;
            case 259 -> SDLScancode.SDL_SCANCODE_BACKSPACE;
            case 260 -> SDLScancode.SDL_SCANCODE_INSERT;
            case 261 -> SDLScancode.SDL_SCANCODE_DELETE;
            case 262 -> SDLScancode.SDL_SCANCODE_RIGHT;
            case 263 -> SDLScancode.SDL_SCANCODE_LEFT;
            case 264 -> SDLScancode.SDL_SCANCODE_DOWN;
            case 265 -> SDLScancode.SDL_SCANCODE_UP;
            case 266 -> SDLScancode.SDL_SCANCODE_PAGEUP;
            case 267 -> SDLScancode.SDL_SCANCODE_PAGEDOWN;
            case 268 -> SDLScancode.SDL_SCANCODE_HOME;
            case 269 -> SDLScancode.SDL_SCANCODE_END;
            case 280 -> SDLScancode.SDL_SCANCODE_CAPSLOCK;
            case 281 -> SDLScancode.SDL_SCANCODE_SCROLLLOCK;
            case 282 -> SDLScancode.SDL_SCANCODE_NUMLOCKCLEAR;
            case 283 -> SDLScancode.SDL_SCANCODE_PRINTSCREEN;
            case 284 -> SDLScancode.SDL_SCANCODE_PAUSE;
            case 290 -> SDLScancode.SDL_SCANCODE_F1;
            case 291 -> SDLScancode.SDL_SCANCODE_F2;
            case 292 -> SDLScancode.SDL_SCANCODE_F3;
            case 293 -> SDLScancode.SDL_SCANCODE_F4;
            case 294 -> SDLScancode.SDL_SCANCODE_F5;
            case 295 -> SDLScancode.SDL_SCANCODE_F6;
            case 296 -> SDLScancode.SDL_SCANCODE_F7;
            case 297 -> SDLScancode.SDL_SCANCODE_F8;
            case 298 -> SDLScancode.SDL_SCANCODE_F9;
            case 299 -> SDLScancode.SDL_SCANCODE_F10;
            case 300 -> SDLScancode.SDL_SCANCODE_F11;
            case 301 -> SDLScancode.SDL_SCANCODE_F12;
            case 302 -> SDLScancode.SDL_SCANCODE_F13;
            case 303 -> SDLScancode.SDL_SCANCODE_F14;
            case 304 -> SDLScancode.SDL_SCANCODE_F15;
            case 305 -> SDLScancode.SDL_SCANCODE_F16;
            case 306 -> SDLScancode.SDL_SCANCODE_F17;
            case 307 -> SDLScancode.SDL_SCANCODE_F18;
            case 308 -> SDLScancode.SDL_SCANCODE_F19;
            case 309 -> SDLScancode.SDL_SCANCODE_F20;
            case 310 -> SDLScancode.SDL_SCANCODE_F21;
            case 311 -> SDLScancode.SDL_SCANCODE_F22;
            case 312 -> SDLScancode.SDL_SCANCODE_F23;
            case 313 -> SDLScancode.SDL_SCANCODE_F24;
            case 320 -> SDLScancode.SDL_SCANCODE_KP_0;
            case 321 -> SDLScancode.SDL_SCANCODE_KP_1;
            case 322 -> SDLScancode.SDL_SCANCODE_KP_2;
            case 323 -> SDLScancode.SDL_SCANCODE_KP_3;
            case 324 -> SDLScancode.SDL_SCANCODE_KP_4;
            case 325 -> SDLScancode.SDL_SCANCODE_KP_5;
            case 326 -> SDLScancode.SDL_SCANCODE_KP_6;
            case 327 -> SDLScancode.SDL_SCANCODE_KP_7;
            case 328 -> SDLScancode.SDL_SCANCODE_KP_8;
            case 329 -> SDLScancode.SDL_SCANCODE_KP_9;
            case 330 -> SDLScancode.SDL_SCANCODE_KP_PERIOD;
            case 331 -> SDLScancode.SDL_SCANCODE_KP_DIVIDE;
            case 332 -> SDLScancode.SDL_SCANCODE_KP_MULTIPLY;
            case 333 -> SDLScancode.SDL_SCANCODE_KP_MINUS;
            case 334 -> SDLScancode.SDL_SCANCODE_KP_PLUS;
            case 335 -> SDLScancode.SDL_SCANCODE_KP_ENTER;
            case 336 -> SDLScancode.SDL_SCANCODE_KP_EQUALS;
            case 340 -> SDLScancode.SDL_SCANCODE_LSHIFT;
            case 341 -> SDLScancode.SDL_SCANCODE_LCTRL;
            case 342 -> SDLScancode.SDL_SCANCODE_LALT;
            case 343 -> SDLScancode.SDL_SCANCODE_LGUI;
            case 344 -> SDLScancode.SDL_SCANCODE_RSHIFT;
            case 345 -> SDLScancode.SDL_SCANCODE_RCTRL;
            case 346 -> SDLScancode.SDL_SCANCODE_RALT;
            case 347 -> SDLScancode.SDL_SCANCODE_RGUI;
            case 348 -> SDLScancode.SDL_SCANCODE_APPLICATION;
            default -> throw new IllegalArgumentException("Unsupported pre-26.3 keyboard code: " + key);
        };
    }

    private LegacyKeyboardKeys()
    {}
}
