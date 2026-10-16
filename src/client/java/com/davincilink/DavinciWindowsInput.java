package com.davincilink;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.*;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Background window messages; never changes the system pointer or foreground window. */
final class DavinciWindowsInput
{
    interface Api extends StdCallLibrary
    {
        Api LIB = Native.load("user32", Api.class, W32APIOptions.DEFAULT_OPTIONS);
        boolean PostMessage(HWND window, int message, WPARAM word, LPARAM parameter);
        boolean SetKeyboardState(byte[] state);
        boolean IsIconic(HWND window);
        boolean ClientToScreen(HWND window, POINT point);
    }

    private static final Map<Long, State> states = new ConcurrentHashMap<>();
    private static final class State
    {
        final byte[] keys = new byte[256];
        int x, y, buttons;
    }

    private DavinciWindowsInput() {}
    static HWND hwnd(Pointer pointer) { return new HWND(pointer); }
    private static State state(Pointer pointer)
    {
        return states.computeIfAbsent(Pointer.nativeValue(pointer), unused -> new State());
    }
    static boolean lost(Pointer pointer) { return !User32.INSTANCE.IsWindow(hwnd(pointer)); }
    static int width(Pointer pointer) { return rectangle(pointer).right; }
    static int height(Pointer pointer) { return rectangle(pointer).bottom; }
    static boolean minimized(Pointer pointer) { return Api.LIB.IsIconic(hwnd(pointer)); }

    private static RECT rectangle(Pointer pointer)
    {
        RECT rect = new RECT();
        if (!User32.INSTANCE.GetClientRect(hwnd(pointer), rect)) throw new IllegalStateException("Resolve window is gone");
        return rect;
    }

    private static void post(Pointer pointer, int message, long word, long parameter)
    {
        if (!Api.LIB.PostMessage(hwnd(pointer), message, new WPARAM(word), new LPARAM(parameter)))
        {
            if (lost(pointer)) return;
            throw new IllegalStateException("Resolve window input failed: " + Native.getLastError());
        }
    }

    private static void activate(Pointer pointer)
    {
        post(pointer, 0x001c, 1, 0);
        post(pointer, 0x0086, 1, 0);
        post(pointer, 0x0006, 1, 0);
        post(pointer, 0x0007, 0, 0);
    }

    private static int flags(State state)
    {
        return state.buttons | (state.keys[0x10] != 0 ? 4 : 0) | (state.keys[0x11] != 0 ? 8 : 0);
    }

    static void move(Pointer pointer, int x, int y)
    {
        State state = state(pointer);
        state.x = x; state.y = y;
        post(pointer, 0x0200, flags(state), (x & 65535) | ((long) (y & 65535) << 16));
    }

    static void button(Pointer pointer, int button, boolean down)
    {
        activate(pointer);
        State state = state(pointer);
        int bit = button == 1 ? 1 : button == 3 ? 2 : 16;
        state.buttons = down ? state.buttons | bit : state.buttons & ~bit;
        int message = button == 1 ? 0x0201 : button == 3 ? 0x0204 : 0x0207;
        post(pointer, message + (down ? 0 : 1), flags(state),
            (state.x & 65535) | ((long) (state.y & 65535) << 16));
    }

    static void scroll(Pointer pointer, int clicks)
    {
        activate(pointer);
        State state = state(pointer);
        POINT point = new POINT(state.x, state.y);
        if (!Api.LIB.ClientToScreen(hwnd(pointer), point)) throw new IllegalStateException("Resolve scroll coordinates unavailable");
        post(pointer, 0x020a, flags(state) | ((long) (clicks * 120 & 65535) << 16),
            (point.x & 65535) | ((long) (point.y & 65535) << 16));
    }

    /** Linux evdev/set-1 codes share the ordinary scan codes; extended keys need their E0 prefix. */
    static int virtualKey(int evdev)
    {
        int scan = switch (evdev)
        {
            case 96 -> 0xe01c; case 97 -> 0xe01d; case 98 -> 0xe035; case 99 -> 0xe037;
            case 100 -> 0xe038; case 102 -> 0xe047; case 103 -> 0xe048; case 104 -> 0xe049;
            case 105 -> 0xe04b; case 106 -> 0xe04d; case 107 -> 0xe04f; case 108 -> 0xe050;
            case 109 -> 0xe051; case 110 -> 0xe052; case 111 -> 0xe053; case 125 -> 0xe05b;
            case 126 -> 0xe05c; case 127 -> 0xe05d; default -> evdev;
        };
        return User32.INSTANCE.MapVirtualKeyEx(scan, 3, User32.INSTANCE.GetKeyboardLayout(0));
    }

    static boolean namedKey(Pointer pointer, String name, boolean down)
    {
        int key = switch (name)
        {
            case "Escape" -> 0x1b; case "Return" -> 0x0d; case "space" -> 0x20;
            case "Control_L" -> 0xa2; case "Control_R" -> 0xa3;
            case "Shift_L" -> 0xa0; case "Shift_R" -> 0xa1;
            default -> name.length() == 1 ? User32.INSTANCE.VkKeyScanExW(name.charAt(0),
                User32.INSTANCE.GetKeyboardLayout(0)) & 255 : 0;
        };
        if (key == 0) return false;
        key(pointer, key, down);
        return true;
    }

    static void key(Pointer pointer, int key, boolean down)
    {
        if (key <= 0 || key > 255) throw new IllegalArgumentException("Invalid Resolve virtual key " + key);
        activate(pointer);
        State state = state(pointer);
        state.keys[key] = down ? (byte) 128 : 0;
        state.keys[0x10] = (byte) (state.keys[0xa0] | state.keys[0xa1]);
        state.keys[0x11] = (byte) (state.keys[0xa2] | state.keys[0xa3]);
        state.keys[0x12] = (byte) (state.keys[0xa4] | state.keys[0xa5]);
        DWORD caller = new DWORD(Kernel32.INSTANCE.GetCurrentThreadId());
        int targetThread = User32.INSTANCE.GetWindowThreadProcessId(hwnd(pointer), null);
        DWORD target = new DWORD(targetThread);
        byte[] saved = new byte[256];
        if (!User32.INSTANCE.GetKeyboardState(saved)) throw new IllegalStateException("Cannot read keyboard state");
        if (!User32.INSTANCE.AttachThreadInput(caller, target, true))
            throw new IllegalStateException("Cannot attach Resolve input queue: " + Native.getLastError());
        try
        {
            if (!Api.LIB.SetKeyboardState(state.keys)) throw new IllegalStateException("Cannot set Resolve keyboard state");
            int scan = User32.INSTANCE.MapVirtualKeyEx(key, 4, User32.INSTANCE.GetKeyboardLayout(targetThread));
            long parameter = 1L | ((long) (scan & 255) << 16) | (scan > 255 ? 1L << 24 : 0L)
                | (down ? 0L : 3L << 30);
            boolean alt = state.keys[0x12] != 0 || key == 0xa4 || key == 0xa5;
            post(pointer, alt ? (down ? 0x0104 : 0x0105) : (down ? 0x0100 : 0x0101), key, parameter);
            /* As with pywinauto's background keystrokes, let the target consume the shared state. */
            try { Thread.sleep(10); }
            catch (InterruptedException error) { Thread.currentThread().interrupt(); }
        }
        finally
        {
            boolean restored = Api.LIB.SetKeyboardState(saved);
            boolean detached = User32.INSTANCE.AttachThreadInput(caller, target, false);
            if (!restored || !detached) throw new IllegalStateException("Cannot restore keyboard input state");
        }
    }

    static void close(Pointer pointer) { states.remove(Pointer.nativeValue(pointer)); }
}
