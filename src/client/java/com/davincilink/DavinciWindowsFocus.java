package com.davincilink;

import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinUser.LASTINPUTINFO;
import com.sun.jna.ptr.IntByReference;

/** Undo tab-started Resolve's automatic activation without overriding later real user input. */
final class DavinciWindowsFocus
{
    private static HWND previous;
    private static int lastInput;

    private DavinciWindowsFocus() {}

    static void remember()
    {
        HWND foreground = User32.INSTANCE.GetForegroundWindow();
        long pid = pid(foreground);
        if (pid == 0) return;
        String command = ProcessHandle.of(pid).flatMap(process -> process.info().command()).orElse("");
        if (command.toLowerCase(java.util.Locale.ROOT).endsWith("resolve.exe")) return;
        previous = foreground;
        lastInput = inputTime();
    }

    static void keep(ProcessHandle owned)
    {
        if (owned == null) return;
        HWND foreground = User32.INSTANCE.GetForegroundWindow();
        if (pid(foreground) != owned.pid())
        {
            remember();
            return;
        }
        /* A click/Alt-Tab since the previous foreground sample is the user's choice. */
        if (previous == null || !User32.INSTANCE.IsWindow(previous) || inputTime() != lastInput) return;
        if (!User32.INSTANCE.SetForegroundWindow(previous))
            throw new IllegalStateException("Resolve activated itself; could not restore the previous window");
    }

    private static long pid(HWND window)
    {
        if (window == null) return 0L;
        IntByReference pid = new IntByReference();
        User32.INSTANCE.GetWindowThreadProcessId(window, pid);
        return Integer.toUnsignedLong(pid.getValue());
    }

    private static int inputTime()
    {
        LASTINPUTINFO input = new LASTINPUTINFO();
        input.cbSize = input.size();
        if (!User32.INSTANCE.GetLastInputInfo(input))
            throw new IllegalStateException("Cannot read the user's last input time");
        return input.dwTime;
    }
}
