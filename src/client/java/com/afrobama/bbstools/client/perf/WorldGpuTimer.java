// Coded by Afrobama/Claude — whole-WORLD-render GPU timer to settle the question
// that the CPU sampler cannot: is the render thread GPU-bound or CPU-bound?
//
// The cpu-sampler attributes the render thread's busy-wait to whatever GL-calling
// Java frame happens to be on the stack (PhysicsMod, swap, buffer upload), so its
// "top method" is misleading — removing PhysicsMod (23% sampled) changed fps by ~0,
// the hallmark of a GPU-bound thread spinning on the driver. This measures the GPU
// clock directly.
//
// Method: glQueryCounter(GL_TIMESTAMP) at WorldRenderEvents.START and .END. The
// diff = GPU time spent processing the world-render command stream. GL_TIMESTAMP
// (unlike glBeginQuery/GL_TIME_ELAPSED) is NOT an "active query", so it cannot
// conflict with Sodium/Iris timer queries. Double-buffered: frame N writes slot A,
// frame N+1 reads slot A (GPU finished by then) and writes slot B — zero stall.
//
// Read worldGpuUs vs FrameTimeline's world-phase WALL time:
//   worldGpuUs ~= wall  -> GPU-bound (vertex throughput / fill) -> reduce geometry,
//                          render distance, model complexity, resolution
//   worldGpuUs <<  wall -> CPU-bound (draw-call submission, per-frame buffer upload,
//                          state changes) -> reduce draw calls / fix upload churn
package com.afrobama.bbstools.client.perf;

import com.afrobama.bbstools.perf.BBSPerfConfig;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL33C;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WorldGpuTimer
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/world-gpu");

    /** Two slots of {startTimestampQuery, endTimestampQuery} for double buffering. */
    private static final int[] START_Q = new int[2];
    private static final int[] END_Q = new int[2];
    private static final boolean[] WRITTEN = new boolean[2];

    private static boolean generated;
    private static int slot;
    private static boolean registered;

    private WorldGpuTimer()
    {
    }

    public static void register()
    {
        if (registered)
        {
            return;
        }

        registered = true;

        /* START: read the PREVIOUS slot's result (GPU has finished it by now),
         *  then stamp this frame's start on the current slot. */
        WorldRenderEvents.START.register(ctx ->
        {
            if (!BBSPerfConfig.ENABLE_WORLD_GPU_TIMER)
            {
                return;
            }

            try
            {
                ensureQueries();
                readPreviousResult();
                GL33C.glQueryCounter(START_Q[slot], GL33C.GL_TIMESTAMP);
            }
            catch (Throwable t)
            {
                BBSPerfConfig.ENABLE_WORLD_GPU_TIMER = false;
                LOG.warn("[world-gpu] disabled after START failure: {}", t.toString());
            }
        });

        /* END: stamp this frame's end, mark the slot ready, advance. */
        WorldRenderEvents.END.register(ctx ->
        {
            if (!BBSPerfConfig.ENABLE_WORLD_GPU_TIMER || !generated)
            {
                return;
            }

            try
            {
                GL33C.glQueryCounter(END_Q[slot], GL33C.GL_TIMESTAMP);
                WRITTEN[slot] = true;
                slot ^= 1;
            }
            catch (Throwable t)
            {
                BBSPerfConfig.ENABLE_WORLD_GPU_TIMER = false;
                LOG.warn("[world-gpu] disabled after END failure: {}", t.toString());
            }
        });

        LOG.info("[world-gpu] registered — GL_TIMESTAMP world-render GPU timer (read BBSPerfConfig.worldGpuUs)");
    }

    private static void ensureQueries()
    {
        if (generated)
        {
            return;
        }

        for (int i = 0; i < 2; i++)
        {
            START_Q[i] = GL15C.glGenQueries();
            END_Q[i] = GL15C.glGenQueries();
        }

        generated = true;
    }

    private static void readPreviousResult()
    {
        int prev = slot ^ 1;

        if (!WRITTEN[prev])
        {
            return;
        }

        int availStart = GL15C.glGetQueryObjecti(START_Q[prev], GL15C.GL_QUERY_RESULT_AVAILABLE);
        int availEnd = GL15C.glGetQueryObjecti(END_Q[prev], GL15C.GL_QUERY_RESULT_AVAILABLE);

        if (availStart == GL15C.GL_TRUE && availEnd == GL15C.GL_TRUE)
        {
            long s = GL33C.glGetQueryObjecti64(START_Q[prev], GL15C.GL_QUERY_RESULT);
            long e = GL33C.glGetQueryObjecti64(END_Q[prev], GL15C.GL_QUERY_RESULT);
            long ns = e - s;

            if (ns >= 0L)
            {
                BBSPerfConfig.worldGpuUs = ns / 1000L;
                BBSPerfConfig.worldGpuFrames++;
            }
        }
    }

}
