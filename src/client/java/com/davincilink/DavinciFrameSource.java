package com.davincilink;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Native BGRA frames. State lives here so existing capture objects need no new fields at swap. */
abstract class DavinciFrameSource implements AutoCloseable
{
    private static final Map<DavinciCapture, DavinciFrameSource> sources = Collections.synchronizedMap(new WeakHashMap<>());
    volatile boolean active = true;
    volatile String failure = "";
    Thread reader;

    static DavinciFrameSource open(DavinciCapture owner, String display, int width, int height) throws IOException
    {
        DavinciFrameSource source = display.startsWith("hwnd:")
            ? new DavinciWindowsCapture(display.substring(5), width, height)
            : new DavinciXDamagePipe(display, width, height);
        sources.put(owner, source);
        return source;
    }

    static DavinciFrameSource get(DavinciCapture owner) { return sources.get(owner); }
    abstract boolean next(byte[] pixels) throws IOException;
    void stop() { this.active = false; }
    @Override public abstract void close();
}
