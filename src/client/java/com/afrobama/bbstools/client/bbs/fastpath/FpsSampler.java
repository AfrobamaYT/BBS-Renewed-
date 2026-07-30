// Coded by Afrobama/Claude — rolling FPS sampler.
//
// Single MinecraftClient.getFps() snapshot at /bbstools status time
// is noisy (read 1 frame, can be 50-100fps spike). Rolling buffer over the
// last ~10 seconds of frames smooths the signal so we get useful avg/min/max
// stats with one /bbstools status call.
//
// Sample frequency: once per Tier 2-d / 2-e flush (= once per render frame).
// At 60fps + 600 capacity = 10sec window. Cheap: 1 int write per frame.
package com.afrobama.bbstools.client.bbs.fastpath;

import net.minecraft.client.Minecraft;

public final class FpsSampler
{
    private static final int CAPACITY = 600;
    private static final int[] BUFFER = new int[CAPACITY];
    private static int writeIdx = 0;
    private static int count = 0;

    private FpsSampler() {}

    /**
     * Record current FPS. Cheap — single ring-buffer slot write. Called
     * from FastPathBatch.flush each frame so the buffer naturally tracks
     * render-thread frame cadence.
     */
    public static void recordCurrentFps()
    {
        int fps = Minecraft.getInstance().getFps();
        BUFFER[writeIdx] = fps;
        writeIdx = (writeIdx + 1) % CAPACITY;
        if (count < CAPACITY) count++;
    }

    public static String snapshot()
    {
        if (count == 0) return "no samples yet";
        int min = Integer.MAX_VALUE;
        int max = 0;
        long sum = 0;
        for (int i = 0; i < count; i++)
        {
            int v = BUFFER[i];
            sum += v;
            if (v < min) min = v;
            if (v > max) max = v;
        }
        int avg = (int) (sum / count);
        return String.format("samples=%d  avg=%d  min=%d  max=%d",
            count, avg, min, max);
    }

    public static void reset()
    {
        writeIdx = 0;
        count = 0;
    }

}
