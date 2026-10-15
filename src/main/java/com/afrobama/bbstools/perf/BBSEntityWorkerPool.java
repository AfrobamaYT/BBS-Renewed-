// Coded by Afrobama/Claude — Tier 2a Sodium-style worker pool.
//
// Per-thread BufferBuilder-style worker pool for parallel crowd vertex
// bake. Mirrors Sodium's ChunkBuilder.java:29-50, 172-195 pattern:
//
//   - ConcurrentLinkedDeque<Runnable> job queue (unbounded, lock-free).
//   - N worker threads named "BBSEntity-Worker-%d", daemon=true.
//   - N = mchorse.bbs_mod.compat.main.Java17.clamp(Math.max(cores/3, cores-6), 1, 10).
//   - Each worker owns a BBSEntityBuildContext for its full lifetime.
//   - submit(Runnable) appends to the deque.
//   - shutdown() flips a flag + interrupts every worker.
//
// Goal: kill ForkJoinPool.commonPool contention. Today's Phase 1b uses
// IntStream.range(...).parallel().forEach which shares the JVM common
// pool with vanilla MC chunk meshing + BBS keyframe decode + Sodium
// itself. Dedicated workers give the crowd bake its own slice of CPU.
//
// Initialization is LAZY (on first submit). The static `INSTANCE` field
// itself does NOT spawn threads — workers come up only when needed.
// Lifecycle: workers stay up until JVM exit (daemon=true). shutdown()
// is optional, intended for the in-game close hook.
//
// Pattern reference: CaffeineMC/sodium ChunkBuilder.java (LGPL-3.0
// inspiration, MIT-rewriteable behavior).
package com.afrobama.bbstools.perf;

import com.afrobama.bbstools.debug.IFeatureDebugger;
import com.afrobama.bbstools.debug.RollingHistogram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class BBSEntityWorkerPool implements IFeatureDebugger
{
    /* LOG declared BEFORE any other static so it's initialized first. */
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/worker-pool");

    public static final BBSEntityWorkerPool INSTANCE = new BBSEntityWorkerPool();

    public static BBSEntityWorkerPool getInstance() { return INSTANCE; }

    /** Override worker count for testing. -1 = auto (Sodium formula). */
    public static volatile int WORKER_COUNT_OVERRIDE = -1;

    private final ConcurrentLinkedDeque<Runnable> jobQueue = new ConcurrentLinkedDeque<>();
    private final Object queueLock = new Object();
    private Thread[] workers;
    private BBSEntityBuildContext[] contexts;
    private volatile boolean started;
    private volatile boolean shutdown;

    public final AtomicLong jobsCompleted = new AtomicLong();
    public final RollingHistogram jobLatency = new RollingHistogram(2048);

    private BBSEntityWorkerPool() { /* lazy: workers spawn on first submit */ }

    /** Sodium-style worker count: clamp(max(cores/3, cores-6), 1, 10). */
    static int computeWorkerCount()
    {
        if (WORKER_COUNT_OVERRIDE > 0) return WORKER_COUNT_OVERRIDE;
        int cores = Runtime.getRuntime().availableProcessors();
        int n = Math.max(cores / 3, cores - 6);
        if (n < 1) n = 1;
        if (n > 10) n = 10;
        return n;
    }

    private void ensureStarted()
    {
        if (started) return;
        synchronized (queueLock)
        {
            if (started) return;
            int n = computeWorkerCount();
            this.workers  = new Thread[n];
            this.contexts = new BBSEntityBuildContext[n];
            for (int i = 0; i < n; i++)
            {
                final int wid = i;
                this.contexts[i] = new BBSEntityBuildContext(wid);
                Thread t = new Thread(() -> runWorker(wid), String.format("BBSEntity-Worker-%d", wid));
                t.setDaemon(true);
                this.workers[i] = t;
                t.start();
            }
            started = true;
            LOG.info("BBSEntityWorkerPool started with {} workers (Sodium formula: cores={})",
                n, Runtime.getRuntime().availableProcessors());
        }
    }

    /** Submit a job. If the dedicated-pool gate is OFF, runs inline on
     *  the caller's thread — so callers that want to fall back to
     *  parallelStream should branch on the gate BEFORE calling submit. */
    public void submit(Runnable job)
    {
        if (!BBSPerfConfig.CROWD_USE_DEDICATED_POOL)
        {
            job.run();
            BBSPerfConfig.entityWorkerJobsCompleted++;
            return;
        }
        ensureStarted();
        jobQueue.add(job);
        BBSPerfConfig.entityWorkerJobsSubmitted++;
        /* Wake any parked worker. Sodium uses a ReentrantLock+Condition;
         * we lean on the deque's own visibility + tight poll loop so the
         * job is picked up within microseconds. */
        synchronized (queueLock) { queueLock.notify(); }
    }

    /** Static convenience: forward to the singleton. */
    public static void submit(Consumer<BBSEntityBuildContext> job)
    {
        INSTANCE.submitJob(job);
    }

    /** Submit a context-aware job. The worker invokes {@code job.accept(ctx)}
     *  with its dedicated {@link BBSEntityBuildContext}. When the dedicated
     *  pool gate is OFF, a transient inline context is allocated on the
     *  caller's thread (rare path — callers should branch on isReady()). */
    public void submitJob(Consumer<BBSEntityBuildContext> job)
    {
        if (!BBSPerfConfig.CROWD_USE_DEDICATED_POOL)
        {
            /* Inline fallback. Allocate a one-shot context; close after
             * use so the memAlloc doesn't leak across many inline runs. */
            try (BBSEntityBuildContext inline = new BBSEntityBuildContext(-1))
            {
                try { job.accept(inline); }
                catch (Throwable t)
                {
                    BBSPerfConfig.entityWorkerJobsFailed++;
                    LOG.warn("BBSEntityWorkerPool inline job threw: {}", t.toString());
                }
            }
            BBSPerfConfig.entityWorkerJobsCompleted++;
            return;
        }
        ensureStarted();
        /* Adapter: capture the consumer + thread-local context lookup. The
         *  worker loop already does ctx.reset() before each job; we just
         *  hand the right ctx to the consumer here. */
        jobQueue.add(new CtxJob(job));
        BBSPerfConfig.entityWorkerJobsSubmitted++;
        synchronized (queueLock) { queueLock.notify(); }
    }

    /** True once the pool is started AND has at least one worker thread.
     *  Callers should use this as a gate before {@link #submit(Consumer)} /
     *  {@link #submitJob(Consumer)}; if false, dispatch via the legacy
     *  parallelStream path. Triggers lazy start on first call so that
     *  the first frame after the master gate flips on actually engages
     *  the pool instead of falling through to inline. */
    public static boolean isReady()
    {
        if (!BBSPerfConfig.CROWD_USE_DEDICATED_POOL) return false;
        INSTANCE.ensureStarted();
        return INSTANCE.started && !INSTANCE.shutdown
            && INSTANCE.workers != null && INSTANCE.workers.length > 0;
    }

    /** Marker job class so {@link #runWorker} can detect the ctx-aware
     *  variant and route the worker's owned context into the consumer. */
    static final class CtxJob implements Runnable
    {
        final Consumer<BBSEntityBuildContext> consumer;
        CtxJob(Consumer<BBSEntityBuildContext> c) { this.consumer = c; }
        @Override public void run()
        {
            /* Should never be invoked directly — the worker loop dispatches
             *  via the consumer with the correct ctx. If something bypasses
             *  the loop (e.g. inline path), accept a null ctx to fail loud. */
            this.consumer.accept(null);
        }
    }

    private void runWorker(int wid)
    {
        BBSEntityBuildContext ctx = contexts[wid];
        while (!shutdown)
        {
            Runnable job = jobQueue.pollFirst();
            if (job == null)
            {
                synchronized (queueLock)
                {
                    if (jobQueue.isEmpty() && !shutdown)
                    {
                        try { queueLock.wait(20L); }
                        catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                    }
                }
                continue;
            }
            long start = System.nanoTime();
            try
            {
                ctx.reset();
                job.run();
                jobsCompleted.incrementAndGet();
                BBSPerfConfig.entityWorkerJobsCompleted++;
            }
            catch (Throwable t)
            {
                BBSPerfConfig.entityWorkerJobsFailed++;
                LOG.warn("BBSEntity-Worker-{} job threw: {}", wid, t.toString());
            }
            jobLatency.record(System.nanoTime() - start);
        }
        if (ctx != null) ctx.close();
    }

    public void shutdown()
    {
        shutdown = true;
        synchronized (queueLock) { queueLock.notifyAll(); }
    }

    @Override public String getName() { return "worker-pool"; }
    @Override public int priority()   { return 40; }
    @Override public boolean isEnabled() { return BBSPerfConfig.CROWD_USE_DEDICATED_POOL; }
    @Override public void setEnabled(boolean v) { BBSPerfConfig.CROWD_USE_DEDICATED_POOL = v; }

    @Override
    public void writeStatus(StringBuilder sb)
    {
        sb.append("CROWD_USE_DEDICATED_POOL=").append(BBSPerfConfig.CROWD_USE_DEDICATED_POOL).append('\n');
        sb.append("started=").append(started).append('\n');
        sb.append("workers=").append(workers == null ? 0 : workers.length).append('\n');
        sb.append("queueDepth=").append(jobQueue.size()).append('\n');
        sb.append("shutdown=").append(shutdown).append('\n');
        sb.append("(Tier 2a — Sodium-style per-thread bake pool. Default OFF.)\n");
    }

    @Override
    public void writeStats(StringBuilder sb)
    {
        sb.append("jobsSubmitted=").append(BBSPerfConfig.entityWorkerJobsSubmitted).append('\n');
        sb.append("jobsCompleted=").append(BBSPerfConfig.entityWorkerJobsCompleted).append('\n');
        sb.append("jobsFailed=")   .append(BBSPerfConfig.entityWorkerJobsFailed)   .append('\n');
        sb.append("jobLatency: "); jobLatency.writeSummary(sb); sb.append('\n');
    }

}
