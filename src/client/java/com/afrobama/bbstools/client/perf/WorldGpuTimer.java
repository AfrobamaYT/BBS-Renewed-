package com.afrobama.bbstools.client.perf;

import com.afrobama.bbstools.perf.BBSPerfConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** GPU clock interval of Fabric START_MAIN to END_MAIN, collected without blocking. */
public final class WorldGpuTimer
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/world-gpu");
    private static NativeGpuTimer timer;
    private static boolean registered;

    private WorldGpuTimer() {}

    public static void register()
    {
        if (registered) return;
        registered = true;
        net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents.END_EXTRACTION.register(context ->
        {
            try
            {
                if (BBSPerfConfig.ENABLE_WORLD_GPU_TIMER && timer == null) timer = new NativeGpuTimer(4);
                if (timer != null)
                {
                    for (NativeGpuTimer.Sample sample : timer.collect())
                    {
                        BBSPerfConfig.worldGpuUs = sample.microseconds();
                        BBSPerfConfig.worldGpuFrames++;
                    }
                }
                NativeGpuTimer.prepareAll();
            }
            catch (Throwable error) { failed("END_EXTRACTION", error); }
        });
        WorldRenderEvents.START_MAIN.register(context ->
        {
            if (!BBSPerfConfig.ENABLE_WORLD_GPU_TIMER) return;
            try
            {
                if (timer == null) timer = new NativeGpuTimer(4);
                timer.begin();
            }
            catch (Throwable error) { failed("START_MAIN", error); }
        });
        WorldRenderEvents.END_MAIN.register(context ->
        {
            if (timer == null) return;
            try { timer.end(); }
            catch (Throwable error) { failed("END_MAIN", error); }
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client ->
        {
            if (timer != null) timer.close();
            timer = null;
        });
        LOG.info("[world-gpu] native timestamps registered for Fabric START_MAIN to END_MAIN");
    }

    private static void failed(String phase, Throwable error)
    {
        BBSPerfConfig.ENABLE_WORLD_GPU_TIMER = false;
        LOG.error("[world-gpu] native timer disabled after " + phase + " failure", error);
        if (timer != null) timer.close();
        timer = null;
    }
}
