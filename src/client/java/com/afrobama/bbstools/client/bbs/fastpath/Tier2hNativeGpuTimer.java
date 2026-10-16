package com.afrobama.bbstools.client.bbs.fastpath;

import com.afrobama.bbstools.perf.BBSPerfConfig;
import com.afrobama.bbstools.client.perf.NativeGpuTimer;
import com.mojang.renderpearl.api.commands.RenderPass;

/** Optional native timestamps; pending queries are never overwritten or treated as zero work. */
final class Tier2hNativeGpuTimer
{
    private static NativeGpuTimer timer;
    private static boolean active;

    private Tier2hNativeGpuTimer()
    {}

    static void prepare()
    {
        if (!BBSPerfConfig.ENABLE_GPU_TIMER) return;
        if (timer == null) timer = new NativeGpuTimer(4);
        timer.prepare();
    }

    static void begin(RenderPass pass)
    {
        if (!BBSPerfConfig.ENABLE_GPU_TIMER) return;
        if (timer == null) timer = new NativeGpuTimer(4);
        for (var sample : timer.collect()) BBSPerfConfig.crowdGpuUs = sample.microseconds();
        active = timer.begin(pass);
    }

    static void end(RenderPass pass)
    {
        if (!active) return;
        timer.end(pass);
        active = false;
    }

    static long elapsedTicks(long first, long last, int bits)
    {
        return NativeGpuTimer.elapsedTicks(first, last, bits);
    }

    static void close()
    {
        if (timer != null) timer.close();
        timer = null;
        active = false;
    }
}
