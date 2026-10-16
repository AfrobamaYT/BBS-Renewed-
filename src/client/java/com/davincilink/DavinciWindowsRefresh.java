package com.davincilink;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Resolve replaces its project-manager HWND with a different project window; WGC must follow. */
final class DavinciWindowsRefresh
{
    private static final Field CAPTURE = field("capture"), INPUT = field("x11"), DISPLAY = field("display");
    private static final Field RESOLVE = field("resolve"), PAUSED = field("paused"), ERROR = field("error");
    private static final Method FIND = finder();
    private static volatile long generation;
    private static volatile boolean active;
    private static final java.util.concurrent.atomic.AtomicBoolean requested = new java.util.concurrent.atomic.AtomicBoolean();
    private static Thread worker;

    private DavinciWindowsRefresh() {}

    static synchronized void start(DavinciScreen screen)
    {
        requested.set(true);
        if (active && worker != null && worker.isAlive()) return;
        active = true;
        long token = ++generation;
        worker = new Thread(() -> run(screen, token), "davincilink-windows-window");
        worker.setDaemon(true);
        worker.start();
    }

    static synchronized void stop()
    {
        active = false;
        DavinciWindowsActivation.end();
        requested.set(false);
        generation++;
        if (worker != null) worker.interrupt();
    }

    private static boolean current(long token) { return active && generation == token; }

    private static void run(DavinciScreen screen, long token)
    {
        while (current(token))
        {
            try
            {
                DavinciWindowsActivation.sample(null);
                boolean force = requested.getAndSet(false);
                if (force)
                {
                    /* Launch/wait outside the screen lock: the first-use UI must keep rendering. */
                    DavinciWindowsFocus.remember();
                    DavinciWindowsSession session = DavinciWindowsSession.open();
                    publish(screen, session, token, true);
                }
                else if (DavinciTabState.shownNow())
                {
                    DavinciWindowsSession session = (DavinciWindowsSession) FIND.invoke(null);
                    if (session != null) publish(screen, session, token, false);
                    else
                    {
                        synchronized (screen)
                        {
                            if (!current(token)) return;
                            DavinciX11 input = (DavinciX11) INPUT.get(screen);
                            if (input != null && input.lost())
                            {
                                DavinciCapture capture = (DavinciCapture) CAPTURE.get(screen);
                                if (capture != null) capture.stop();
                                input.close();
                                INPUT.set(screen, null);
                                ERROR.set(screen, "Resolve has no visible window. Reopen its tab to reconnect.");
                            }
                        }
                    }
                }
                Thread.sleep(250);
            }
            catch (InterruptedException interrupted)
            {
                DavinciWindowsActivation.end();
                if (!current(token)) return;
            }
            catch (Exception failure)
            {
                DavinciWindowsActivation.end();
                if (!current(token)) return;
                try { ERROR.set(screen, failure.toString()); }
                catch (IllegalAccessException impossible) { throw new IllegalStateException(impossible); }
                com.mojang.logging.LogUtils.getLogger().error("[davincilink] Windows Resolve window capture failed", failure);
                try { Thread.sleep(1000); }
                catch (InterruptedException interrupted) { if (!current(token)) return; }
            }
        }
    }

    private static void publish(DavinciScreen screen, DavinciWindowsSession session, long token, boolean force) throws Exception
    {
        if (!current(token) || !DavinciTabState.shownNow()) return;
        DavinciWindowsActivation.sample(DavinciWindowsInput.hwnd(session.window));
        if (DavinciWindowsInput.minimized(session.window)) return;
        int width = DavinciWindowsInput.width(session.window), height = DavinciWindowsInput.height(session.window);
        if (width <= 0 || height <= 0) return;
        DavinciWindowsFocus.keep(session.ownedProcess());
        String display = "hwnd:" + Long.toUnsignedString(Pointer.nativeValue(session.window));
        synchronized (screen)
        {
            if (!current(token) || !DavinciTabState.shownNow() || !force && PAUSED.getBoolean(screen)) return;
            DavinciCapture previous = (DavinciCapture) CAPTURE.get(screen);
            if (!force && previous != null && display.equals(DISPLAY.get(screen))
                && previous.width() == width && previous.height() == height) return;
            if (previous != null) previous.stop();
            DavinciX11 input = (DavinciX11) INPUT.get(screen);
            if (input != null) input.close();
            if (!User32.INSTANCE.SetWindowPos(DavinciWindowsInput.hwnd(session.window),
                new HWND(Pointer.createConstant(1)), 0, 0, 0, 0, 0x0010 | 0x0001 | 0x0002))
                throw new IllegalStateException("Could not keep Resolve behind BBS: " + com.sun.jna.Native.getLastError());
            input = DavinciX11.open(display);
            DavinciCapture capture = new DavinciCapture(display, width, height);
            DISPLAY.set(screen, display);
            RESOLVE.set(screen, session.ownedProcess());
            CAPTURE.set(screen, capture);
            PAUSED.setBoolean(screen, false);
            INPUT.set(screen, input);
            ERROR.set(screen, "");
        }
    }

    private static Field field(String name)
    {
        try
        {
            Field field = DavinciScreen.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        }
        catch (ReflectiveOperationException error) { throw new ExceptionInInitializerError(error); }
    }

    private static Method finder()
    {
        try
        {
            Method method = DavinciWindowsSession.class.getDeclaredMethod("find");
            method.setAccessible(true);
            return method;
        }
        catch (ReflectiveOperationException error) { throw new ExceptionInInitializerError(error); }
    }
}
