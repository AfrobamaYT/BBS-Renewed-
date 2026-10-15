// Coded by Afrobama/Claude — BBS <-> DaVinci Resolve live link (task 109, docs/DAVINCI-LINK-PLAN.md).
package com.davincilink;

/**
 * A physical key from BBS to the private X server: SDL scancode -> X keycode.
 *
 * <p>Minecraft 26.3 runs on SDL3, so BBS gets SDL scancodes, which are USB HID usage IDs. An X
 * keycode on Linux is the evdev code + 8. Both are published standards (USB HID Usage Tables,
 * Linux input-event-codes.h); this table is written from them, not copied from any program.
 * Physical keys are forwarded and the X server applies the user's layout, so a German keyboard
 * types German characters and Ctrl+Z is Ctrl+Z.</p>
 */
final class DavinciKeys
{
    private static final int[] EVDEV = new int[232];

    static
    {
        /* letters a..z (HID 4..29) */
        int[] letters = {30, 48, 46, 32, 18, 33, 34, 35, 23, 36, 37, 38, 50, 49, 24, 25, 16, 19, 31, 20, 22, 47, 17, 45, 21, 44};
        for (int i = 0; i < letters.length; i++) EVDEV[4 + i] = letters[i];
        /* digits 1..9, 0 (HID 30..39) */
        for (int i = 0; i < 10; i++) EVDEV[30 + i] = 2 + i;
        int[][] pairs = {
            {40, 28}, {41, 1}, {42, 14}, {43, 15}, {44, 57}, {45, 12}, {46, 13}, {47, 26}, {48, 27},
            {49, 43}, {50, 43}, {51, 39}, {52, 40}, {53, 41}, {54, 51}, {55, 52}, {56, 53}, {57, 58},
            {70, 99}, {71, 70}, {72, 119}, {73, 110}, {74, 102}, {75, 104}, {76, 111}, {77, 107},
            {78, 109}, {79, 106}, {80, 105}, {81, 108}, {82, 103}, {83, 69}, {84, 98}, {85, 55},
            {86, 74}, {87, 78}, {88, 96}, {89, 79}, {90, 80}, {91, 81}, {92, 75}, {93, 76}, {94, 77},
            {95, 71}, {96, 72}, {97, 73}, {98, 82}, {99, 83}, {100, 86}, {101, 127},
            {224, 29}, {225, 42}, {226, 56}, {227, 125}, {228, 97}, {229, 54}, {230, 100}, {231, 126}};
        for (int[] pair : pairs) EVDEV[pair[0]] = pair[1];
        /* F1..F10 (HID 58..67) -> 59..68, F11/F12 (HID 68/69) -> 87/88 */
        for (int i = 0; i < 10; i++) EVDEV[58 + i] = 59 + i;
        EVDEV[68] = 87;
        EVDEV[69] = 88;
    }

    private DavinciKeys()
    {}

    /** The X keycode for an SDL scancode, or 0 if the key is not mapped. */
    static int toX(int sdlScancode)
    {
        return sdlScancode > 0 && sdlScancode < EVDEV.length && EVDEV[sdlScancode] != 0 ? EVDEV[sdlScancode] + 8 : 0;
    }

    /** A GLFW mouse button (0 left, 1 right, 2 middle) as an X button (1 left, 2 middle, 3 right). */
    static int xButton(int glfw)
    {
        return glfw == 1 ? 3 : glfw == 2 ? 2 : 1;
    }
}
