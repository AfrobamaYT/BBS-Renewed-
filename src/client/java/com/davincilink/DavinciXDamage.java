package com.davincilink;

import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.IntByReference;

import java.io.IOException;
import java.util.concurrent.locks.LockSupport;

/** XDamage gates XShmGetImage; the capture thread alone owns and closes its X connection. */
final class DavinciXDamage extends DavinciFrameSource
{
    interface X extends Library
    {
        X LIB = Native.load("X11", X.class);
        Pointer XOpenDisplay(String name);
        int XCloseDisplay(Pointer display);
        int XDefaultScreen(Pointer display);
        int XDefaultDepth(Pointer display, int screen);
        Pointer XDefaultVisual(Pointer display, int screen);
        NativeLong XDefaultRootWindow(Pointer display);
        int XConnectionNumber(Pointer display);
        int XPending(Pointer display);
        int XNextEvent(Pointer display, Pointer event);
        int XSync(Pointer display, boolean discard);
        int XDestroyImage(Pointer image);
        void XSetIOErrorExitHandler(Pointer display, Lost handler, Pointer data);
    }

    interface Damage extends Library
    {
        Damage LIB = Native.load("Xdamage", Damage.class);
        boolean XDamageQueryExtension(Pointer display, IntByReference event, IntByReference error);
        NativeLong XDamageCreate(Pointer display, NativeLong drawable, int level);
        void XDamageSubtract(Pointer display, NativeLong damage, NativeLong repair, NativeLong parts);
        void XDamageDestroy(Pointer display, NativeLong damage);
    }

    interface Shm extends Library
    {
        Shm LIB = Native.load("Xext", Shm.class);
        boolean XShmQueryExtension(Pointer display);
        Pointer XShmCreateImage(Pointer display, Pointer visual, int depth, int format,
            Pointer data, Segment segment, int width, int height);
        boolean XShmAttach(Pointer display, Segment segment);
        boolean XShmDetach(Pointer display, Segment segment);
        boolean XShmGetImage(Pointer display, NativeLong drawable, Pointer image, int x, int y, NativeLong mask);
    }

    interface C extends Library
    {
        C LIB = Native.load("c", C.class);
        int shmget(int key, NativeLong size, int flags);
        Pointer shmat(int id, Pointer address, int flags);
        int shmdt(Pointer address);
        int shmctl(int id, int command, Pointer data);
        int poll(Pointer descriptors, NativeLong count, int timeout);
    }

    interface Lost extends Callback { void invoke(Pointer display, Pointer data); }

    @Structure.FieldOrder({"shmseg", "shmid", "shmaddr", "readOnly"})
    public static class Segment extends Structure
    {
        public NativeLong shmseg;
        public int shmid = -1;
        public Pointer shmaddr;
        public int readOnly;
    }

    @Structure.FieldOrder({"width", "height", "xoffset", "format", "data", "byteOrder", "bitmapUnit",
        "bitmapBitOrder", "bitmapPad", "depth", "stride", "bits", "red", "green", "blue", "obdata", "functions"})
    public static class Image extends Structure
    {
        public int width, height, xoffset, format;
        public Pointer data;
        public int byteOrder, bitmapUnit, bitmapBitOrder, bitmapPad, depth, stride, bits;
        public NativeLong red, green, blue;
        public Pointer obdata;
        public Pointer[] functions = new Pointer[6];
        public Image(Pointer pointer) { super(pointer); this.read(); }
    }

    private final Segment segment = new Segment();
    private final Lost onLost = (display, data) ->
    {
        this.failure = "Resolve X server disconnected";
        this.lost = true;
        this.active = false;
    };
    private Pointer display;
    private NativeLong root;
    private NativeLong damage;
    private Image image;
    private boolean attached;
    private volatile boolean lost;
    private boolean first = true;
    private int eventType;
    private long lastFrame;
    private final Memory event = new Memory(24L * Native.LONG_SIZE);
    private final Memory poll = new Memory(8);

    DavinciXDamage(String name, int width, int height) throws IOException
    {
        try
        {
            this.display = X.LIB.XOpenDisplay(name);
            if (this.display == null) throw new IOException("Cannot open Resolve display " + name);
            X.LIB.XSetIOErrorExitHandler(this.display, this.onLost, null);
            IntByReference base = new IntByReference();
            if (!Damage.LIB.XDamageQueryExtension(this.display, base, new IntByReference())
                || !Shm.LIB.XShmQueryExtension(this.display))
                throw new IOException("Resolve display requires XDamage and MIT-SHM");
            this.eventType = base.getValue();
            this.root = X.LIB.XDefaultRootWindow(this.display);
            this.damage = Damage.LIB.XDamageCreate(this.display, this.root, 3);
            int screen = X.LIB.XDefaultScreen(this.display);
            Pointer pointer = Shm.LIB.XShmCreateImage(this.display, X.LIB.XDefaultVisual(this.display, screen),
                X.LIB.XDefaultDepth(this.display, screen), 2, null, this.segment, width, height);
            if (pointer == null) throw new IOException("XShmCreateImage failed");
            this.image = new Image(pointer);
            if (this.image.bits != 32 || this.image.byteOrder != 0 || this.image.stride != width * 4
                || this.image.red.longValue() != 0xff0000L || this.image.blue.longValue() != 0xffL
                || this.image.green.longValue() != 0xff00L)
                throw new IOException("Resolve capture requires packed little-endian BGRX32");
            this.segment.shmid = C.LIB.shmget(0, new NativeLong((long) this.image.stride * height), 01000 | 0600);
            if (this.segment.shmid < 0) throw new IOException("shmget failed: " + Native.getLastError());
            this.segment.shmaddr = C.LIB.shmat(this.segment.shmid, null, 0);
            if (Pointer.nativeValue(this.segment.shmaddr) == -1L) throw new IOException("shmat failed");
            this.image.data = this.segment.shmaddr;
            this.image.write();
            if (!Shm.LIB.XShmAttach(this.display, this.segment)) throw new IOException("XShmAttach failed");
            this.attached = true;
            X.LIB.XSync(this.display, false);
            C.LIB.shmctl(this.segment.shmid, 0, null);
            this.poll.clear();
            this.poll.setInt(0, X.LIB.XConnectionNumber(this.display));
            this.poll.setShort(4, (short) 1);
        }
        catch (IOException | RuntimeException | LinkageError error)
        {
            this.close();
            throw new IOException("Resolve XDamage capture: " + error.getMessage(), error);
        }
    }

    @Override boolean next(byte[] pixels) throws IOException
    {
        boolean changed = this.first;
        while (this.active)
        {
            while (X.LIB.XPending(this.display) > 0)
            {
                X.LIB.XNextEvent(this.display, this.event);
                changed |= this.event.getInt(0) == this.eventType;
            }
            if (this.lost) throw new IOException("Resolve X server disconnected");
            if (changed)
            {
                LockSupport.parkNanos(Math.max(0L, this.lastFrame + 16_666_667L - System.nanoTime()));
                if (!this.active) return false;
                Damage.LIB.XDamageSubtract(this.display, this.damage, new NativeLong(0), new NativeLong(0));
                if (!Shm.LIB.XShmGetImage(this.display, this.root, this.image.getPointer(), 0, 0, new NativeLong(-1)))
                    throw new IOException("XShmGetImage failed");
                this.segment.shmaddr.read(0, pixels, 0, pixels.length);
                this.first = false;
                this.lastFrame = System.nanoTime();
                return true;
            }
            if (C.LIB.poll(this.poll, new NativeLong(1), 100) < 0 && Native.getLastError() != 4)
                throw new IOException("Resolve display poll failed: " + Native.getLastError());
        }
        return false;
    }

    @Override public void close()
    {
        this.active = false;
        if (this.display != null && !this.lost)
        {
            if (this.attached) Shm.LIB.XShmDetach(this.display, this.segment);
            if (this.damage != null) Damage.LIB.XDamageDestroy(this.display, this.damage);
            X.LIB.XSync(this.display, false);
        }
        if (this.image != null)
        {
            this.image.data = null;
            this.image.write();
            X.LIB.XDestroyImage(this.image.getPointer());
            this.image = null;
        }
        if (this.segment.shmaddr != null && Pointer.nativeValue(this.segment.shmaddr) != -1L)
            C.LIB.shmdt(this.segment.shmaddr);
        if (this.segment.shmid >= 0) C.LIB.shmctl(this.segment.shmid, 0, null);
        if (this.display != null && !this.lost) X.LIB.XCloseDisplay(this.display);
        this.display = null;
        this.segment.shmaddr = null;
        this.segment.shmid = -1;
        this.event.close();
        this.poll.close();
    }
}
