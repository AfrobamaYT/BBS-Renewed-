package com.davincilink;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;

/** Windows lets the foreground process prevent a new background app from activating itself. */
final class DavinciWindowsActivation
{
    interface Api extends StdCallLibrary
    {
        Api LIB = Native.load("user32", Api.class);
        boolean LockSetForegroundWindow(int mode);
    }

    private static volatile boolean locked;
    private static long deadline, changedAt, window;

    private DavinciWindowsActivation() {}

    static void begin()
    {
        HWND foreground = User32.INSTANCE.GetForegroundWindow();
        if (foreground == null) return;
        IntByReference pid = new IntByReference();
        User32.INSTANCE.GetWindowThreadProcessId(foreground, pid);
        if (Integer.toUnsignedLong(pid.getValue()) != ProcessHandle.current().pid()) return;
        if (!Api.LIB.LockSetForegroundWindow(1))
        {
            com.mojang.logging.LogUtils.getLogger().warn("[davincilink] Windows refused startup activation lock; using focus restoration");
            return;
        }
        locked = true;
        changedAt = System.nanoTime();
        deadline = changedAt + 60_000_000_000L;
        window = 0L;
    }

    static void sample(HWND current)
    {
        if (!locked) return;
        long now = System.nanoTime();
        if (now >= deadline) { end(); return; }
        if (current == null) return;
        long handle = Pointer.nativeValue(current.getPointer());
        if (handle != window) { window = handle; changedAt = now; }
        char[] title = new char[512];
        User32.INSTANCE.GetWindowText(current, title, title.length);
        if (!Native.toString(title).equals("Resolve") && now - changedAt >= 2_000_000_000L) end();
    }

    static void end()
    {
        if (!locked) return;
        locked = false;
        /* Alt-Tab/clicking another app releases the Windows lock automatically as well. */
        if (!Api.LIB.LockSetForegroundWindow(2))
            com.mojang.logging.LogUtils.getLogger().warn("[davincilink] Windows did not confirm activation unlock (a user focus change can release it first)");
    }
}
