// Coded by Afrobama/Claude — Tier 5a GL audit infrastructure.
//
// Records every GL call's calling thread + call site (top stack frame)
// when AUDIT_GL_FROM_NON_RENDER_THREAD is enabled. Reveals whether BBS
// or its deps make off-render-thread GL calls that would block a future
// Mojang-style extract/draw split.
//
// Implementation: lock-free MPSC ring buffer of 4096 entries. Each entry
// is (threadId, threadName, callSite). Coalesced into histogram on dump.
package com.afrobama.bbstools.perf.audit;

import com.afrobama.bbstools.debug.IFeatureDebugger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class GlAuditRingBuffer implements IFeatureDebugger
{
    public static final GlAuditRingBuffer INSTANCE = new GlAuditRingBuffer();

    /** Master toggle — flip via /bbsdebug gl-audit on. */
    public static volatile boolean AUDIT_GL_FROM_NON_RENDER_THREAD = false;

    private static final int RING_CAP = 4096;

    private final long[] timestamps = new long[RING_CAP];
    private final String[] threadNames = new String[RING_CAP];
    private final String[] callSites = new String[RING_CAP];
    private final AtomicInteger writeIdx = new AtomicInteger();

    private final AtomicLong totalRecorded = new AtomicLong();
    private final AtomicLong totalNonRender = new AtomicLong();

    private final Map<String, Long> coalesced = new HashMap<>();

    private GlAuditRingBuffer() {}

    /**
     * Called from BBSToolsGlAuditMixin on every assertOnRenderThread.
     * Lock-free record into ring buffer; coalesce later on dump.
     */
    public void record(String threadName, String callSite, boolean isRender)
    {
        if (!AUDIT_GL_FROM_NON_RENDER_THREAD) return;
        int slot = writeIdx.getAndIncrement() & (RING_CAP - 1);
        timestamps[slot] = System.nanoTime();
        threadNames[slot] = threadName;
        callSites[slot] = callSite;
        totalRecorded.incrementAndGet();
        if (!isRender) totalNonRender.incrementAndGet();
    }

    /**
     * Walk the ring buffer + coalesce into a (thread, callSite) → count
     * histogram. Called on /bbsdebug gl-audit dump.
     */
    public synchronized Map<String, Long> coalesce()
    {
        coalesced.clear();
        for (int i = 0; i < RING_CAP; i++)
        {
            String thread = threadNames[i];
            String site = callSites[i];
            if (thread == null || site == null) continue;
            String key = thread + " @ " + site;
            coalesced.merge(key, 1L, Long::sum);
        }
        return new HashMap<>(coalesced);
    }

    @Override public String getName() { return "gl-audit"; }
    @Override public boolean isEnabled() { return AUDIT_GL_FROM_NON_RENDER_THREAD; }
    @Override public void setEnabled(boolean v) { AUDIT_GL_FROM_NON_RENDER_THREAD = v; }
    @Override public int priority() { return 200; }

    @Override
    public void writeStatus(StringBuilder sb)
    {
        sb.append("enabled=").append(AUDIT_GL_FROM_NON_RENDER_THREAD).append('\n');
        sb.append("ringCap=").append(RING_CAP).append('\n');
        sb.append("ringWriteIdx=").append(writeIdx.get()).append('\n');
    }

    @Override
    public void writeStats(StringBuilder sb)
    {
        sb.append("totalRecorded=").append(totalRecorded.get()).append('\n');
        sb.append("totalNonRenderThread=").append(totalNonRender.get()).append('\n');
        Map<String, Long> hist = coalesce();
        sb.append("uniqueCallSites=").append(hist.size()).append('\n');
        sb.append("--- top 20 sites ---\n");
        hist.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(20)
            .forEach(e -> sb.append("  ").append(e.getValue()).append(" × ")
                .append(e.getKey()).append('\n'));
    }

}
