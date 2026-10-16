// Coded by Afrobama/Claude — BBS <-> DaVinci Resolve live link (task 109, docs/DAVINCI-LINK-PLAN.md).
package com.davincilink;

import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;

/**
 * Mouse and keys into the private X server Resolve runs in (XTest, like VNC servers do).
 *
 * <p>That server has its own pointer: what BBS sends there never moves the user's cursor
 * (measured 2026-10-11: a clip dragged 100 frames by XTest on :91, the host cursor stayed at
 * 9600,560). One connection, used from the render thread only.</p>
 */
public final class DavinciX11
{
    interface X11 extends Library
    {
        X11 LIB = Native.load("X11", X11.class);

        Pointer XOpenDisplay(String name);
        int XCloseDisplay(Pointer display);
        int XDefaultScreen(Pointer display);
        int XDisplayWidth(Pointer display, int screen);
        int XDisplayHeight(Pointer display, int screen);
        NativeLong XStringToKeysym(String name);
        void XSetIOErrorExitHandler(Pointer display, IOExit handler, Pointer data);
        byte XKeysymToKeycode(Pointer display, NativeLong keysym);
        int XFlush(Pointer display);
    }

    interface XTest extends Library
    {
        XTest LIB = Native.load("Xtst", XTest.class);

        int XTestFakeMotionEvent(Pointer display, int screen, int x, int y, NativeLong delay);
        int XTestFakeButtonEvent(Pointer display, int button, boolean press, NativeLong delay);
        int XTestFakeKeyEvent(Pointer display, int keycode, boolean press, NativeLong delay);
    }

    /** Called by Xlib when the server is gone; returning keeps the process alive (libX11 1.7+). */
    interface IOExit extends Callback
    {
        void invoke(Pointer display, Pointer data);
    }

    private static final NativeLong NOW = new NativeLong(0);

    private final Pointer display;
    private final int screen;
    /* Held here: Xlib keeps only a native pointer, a collected callback would crash it. */
    private final IOExit onLost = (display, data) -> this.lost = true;
    private volatile boolean lost;
    /* Sending and closing hold this object's lock: the render thread sent a motion event on a
     * connection another thread had just closed, and Xlib crashed BBS (SIGSEGV in _XSend, 2026-10-12). */
    private boolean closed;

    private DavinciX11(Pointer display)
    {
        this.display = display;
        if (com.sun.jna.Platform.isWindows())
        {
            this.screen = 0;
            return;
        }
        this.screen = X11.LIB.XDefaultScreen(display);
        /* Xlib's default ends the whole process with exit(1) when the server goes away -- on
         * 2026-10-12 BBS died that way ("XIO: fatal IO error 0 on X server :90") when the tab's
         * Xwayland closed while the render thread still moved the pointer. */
        X11.LIB.XSetIOErrorExitHandler(display, this.onLost, null);
    }

    /** The private X server is gone; nothing more may be sent on this connection. */
    public boolean lost()
    {
        return this.lost || (com.sun.jna.Platform.isWindows() && DavinciWindowsInput.lost(this.display));
    }

    /** Connects to e.g. ":91"; null while that server is not up yet. */
    public static DavinciX11 open(String name)
    {
        if (com.sun.jna.Platform.isWindows())
            return name.startsWith("hwnd:") ? new DavinciX11(Pointer.createConstant(Long.parseUnsignedLong(name.substring(5)))) : null;
        Pointer display = X11.LIB.XOpenDisplay(name);

        return display == null ? null : new DavinciX11(display);
    }

    public int width()
    {
        if (com.sun.jna.Platform.isWindows()) return DavinciWindowsInput.width(this.display);
        return X11.LIB.XDisplayWidth(this.display, this.screen);
    }

    public int height()
    {
        if (com.sun.jna.Platform.isWindows()) return DavinciWindowsInput.height(this.display);
        return X11.LIB.XDisplayHeight(this.display, this.screen);
    }

    public synchronized void move(int x, int y)
    {
        if (this.lost || this.closed) return;
        if (com.sun.jna.Platform.isWindows()) { DavinciWindowsInput.move(this.display, x, y); return; }
        XTest.LIB.XTestFakeMotionEvent(this.display, this.screen, x, y, NOW);
        X11.LIB.XFlush(this.display);
    }

    /** X buttons: 1 left, 2 middle, 3 right, 4/5 wheel up/down. */
    public synchronized void button(int button, boolean press)
    {
        if (this.lost || this.closed) return;
        if (com.sun.jna.Platform.isWindows()) { DavinciWindowsInput.button(this.display, button, press); return; }
        XTest.LIB.XTestFakeButtonEvent(this.display, button, press, NOW);
        X11.LIB.XFlush(this.display);
    }

    public void scroll(int clicks)
    {
        if (com.sun.jna.Platform.isWindows())
        {
            synchronized (this) { if (!this.closed) DavinciWindowsInput.scroll(this.display, clicks); }
            return;
        }
        int button = clicks > 0 ? 4 : 5;

        for (int i = 0; i < Math.abs(clicks); i++)
        {
            this.button(button, true);
            this.button(button, false);
        }
    }

    /** A key by its X keysym name ("Return", "a", "Control_L"); false if the server has no such key. */
    public synchronized boolean key(String keysym, boolean press)
    {
        if (com.sun.jna.Platform.isWindows())
            return !this.closed && DavinciWindowsInput.namedKey(this.display, keysym, press);
        if (this.lost || this.closed)
        {
            return false;
        }

        NativeLong sym = X11.LIB.XStringToKeysym(keysym);
        int code = sym.longValue() == 0 ? 0 : X11.LIB.XKeysymToKeycode(this.display, sym) & 0xFF;

        if (code == 0)
        {
            return false;
        }

        XTest.LIB.XTestFakeKeyEvent(this.display, code, press, NOW);
        X11.LIB.XFlush(this.display);

        return true;
    }

    /** A physical key by X keycode: the private server applies its own (the user's) layout. */
    public synchronized void keycode(int code, boolean press)
    {
        if (com.sun.jna.Platform.isWindows())
        {
            if (!this.closed) DavinciWindowsInput.key(this.display, code, press);
            return;
        }
        if (code > 7 && code < 256 && !this.lost && !this.closed)
        {
            XTest.LIB.XTestFakeKeyEvent(this.display, code, press, NOW);
            X11.LIB.XFlush(this.display);
        }
    }

    public synchronized void close()
    {
        if (this.lost || this.closed) return;   // a dead server's connection: freeing it again crashes
        this.closed = true;
        if (com.sun.jna.Platform.isWindows()) { DavinciWindowsInput.close(this.display); return; }
        X11.LIB.XCloseDisplay(this.display);
    }
}
