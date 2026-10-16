package com.davincilink;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Shell32;
import com.sun.jna.platform.win32.ShellAPI.SHELLEXECUTEINFO;
import com.sun.jna.platform.win32.WinDef.*;
import com.sun.jna.ptr.IntByReference;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Windows tab ownership: an existing desktop Resolve is captured; only tab-started PIDs are closed. */
final class DavinciWindowsSession
{
    private static final Map<Long, ProcessHandle> owned = new HashMap<>();
    final Pointer window;
    final ProcessHandle process;

    private DavinciWindowsSession(Pointer window, ProcessHandle process)
    {
        this.window = window;
        this.process = process;
    }

    static synchronized DavinciWindowsSession open() throws IOException, InterruptedException
    {
        DavinciWindowsSession session = find();
        if (session == null)
        {
            String configured = System.getProperty("davincilink.resolve", "");
            Path binary = configured.isEmpty()
                ? Path.of(System.getenv().getOrDefault("ProgramFiles", "C:\\Program Files"),
                    "Blackmagic Design", "DaVinci Resolve", "Resolve.exe")
                : Path.of(configured);
            if (!java.nio.file.Files.isRegularFile(binary))
                throw new IOException("DaVinci Resolve was not found. Start Resolve before opening its tab.");
            ProcessHandle process = launch(binary);
            owned.put(process.pid(), process);
            long deadline = System.nanoTime() + 60_000_000_000L;
            while (System.nanoTime() < deadline && process.isAlive())
            {
                session = find();
                if (session != null && session.process.pid() == process.pid()) break;
                Thread.sleep(100);
            }
            if (session == null)
            {
                DavinciOwnedShutdown.stop(null, process, null, null);
                owned.remove(process.pid());
                throw new IOException("Resolve did not create a window within 60 seconds");
            }
        }
        if (DavinciWindowsInput.minimized(session.window))
            User32.INSTANCE.ShowWindow(DavinciWindowsInput.hwnd(session.window), 4);
        if (DavinciWindowsInput.minimized(session.window))
            throw new IOException("Restore the Resolve window: Windows cannot capture a minimized window.");
        if (!User32.INSTANCE.SetWindowPos(DavinciWindowsInput.hwnd(session.window),
            new HWND(Pointer.createConstant(1)), 0, 0, 0, 0, 0x0010 | 0x0001 | 0x0002))
            throw new IOException("Could not place Resolve behind BBS: " + com.sun.jna.Native.getLastError());
        owned.entrySet().removeIf(entry -> !entry.getValue().isAlive());
        return session;
    }

    ProcessHandle ownedProcess() { return owned.get(this.process.pid()); }

    static void requestClose(ProcessHandle process)
    {
        if (!owned.containsKey(process.pid())) return;
        User32.INSTANCE.EnumWindows((window, ignored) ->
        {
            IntByReference pid = new IntByReference();
            User32.INSTANCE.GetWindowThreadProcessId(window, pid);
            if (Integer.toUnsignedLong(pid.getValue()) == process.pid())
                if (!DavinciWindowsInput.Api.LIB.PostMessage(window, 0x0010, new WPARAM(0), new LPARAM(0)))
                    throw new IllegalStateException("Resolve close message failed: " + com.sun.jna.Native.getLastError());
            return true;
        }, null);
    }

    static void stopOwned()
    {
        synchronized (DavinciWindowsSession.class)
        {
            /* Shutdown interrupts a pending open first; wait for its PID registration before cleanup. */
            for (ProcessHandle process : java.util.List.copyOf(owned.values()))
                DavinciOwnedShutdown.stop(null, process, null, null);
            owned.clear();
        }
    }

    private static ProcessHandle launch(Path binary) throws IOException
    {
        DavinciWindowsActivation.begin();
        SHELLEXECUTEINFO info = new SHELLEXECUTEINFO();
        info.fMask = 0x0040 | 0x0100;
        info.lpFile = binary.toString();
        info.nShow = 4;
        if (!Shell32.INSTANCE.ShellExecuteEx(info) || info.hProcess == null)
            throw new IOException("Could not start Resolve: " + com.sun.jna.Native.getLastError());
        try
        {
            return ProcessHandle.of(Integer.toUnsignedLong(Kernel32.INSTANCE.GetProcessId(info.hProcess))).orElseThrow(
                () -> new IOException("Resolve exited while starting"));
        }
        finally { Kernel32.INSTANCE.CloseHandle(info.hProcess); }
    }

    private static DavinciWindowsSession find()
    {
        DavinciWindowsSession[] result = {null};
        long[] largest = {0L};
        User32.INSTANCE.EnumWindows((window, ignored) ->
        {
            if (!User32.INSTANCE.IsWindowVisible(window)) return true;
            IntByReference pid = new IntByReference();
            User32.INSTANCE.GetWindowThreadProcessId(window, pid);
            ProcessHandle process = ProcessHandle.of(Integer.toUnsignedLong(pid.getValue())).orElse(null);
            if (process == null || !process.info().command().orElse("").toLowerCase(java.util.Locale.ROOT).endsWith("resolve.exe"))
                return true;
            RECT rect = new RECT();
            if (!User32.INSTANCE.GetClientRect(window, rect)) return true;
            /* The splash is "Resolve", then "Project Manager", then the project name. Match the
             * exact executable already checked above; a title filter hides the first-use UI. */
            long area = (long) rect.right * rect.bottom;
            if (area > largest[0])
            {
                largest[0] = area;
                result[0] = new DavinciWindowsSession(window.getPointer(), process);
            }
            return true;
        }, null);
        return result[0];
    }
}
