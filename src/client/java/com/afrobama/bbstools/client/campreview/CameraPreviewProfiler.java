// Coded by Afrobama/Claude — Phase 7: per-pass timing profiler
package com.afrobama.bbstools.client.campreview;

/**
 * Ring-buffer profiler for camera-preview render passes. Records the
 * nanosecond duration of each preview's pre-pass world render so the
 * user can quantify the FPS cost of having N preview windows open.
 *
 * <p>Exposed via {@code /bbsdebug profile-cam-preview} → {@link #snapshot()}
 * returns a human-readable summary with min/avg/p95/max + per-frame
 * total in milliseconds.</p>
 *
 * <p>Buffer holds 240 samples (= ~4s at 60fps or ~2s at 120fps). Older
 * entries roll off automatically. Recording is a single
 * {@code System.nanoTime()} read + array slot write — negligible
 * overhead compared to the render itself.</p>
 */
public final class CameraPreviewProfiler
{
    private static final int RING_SIZE = 240;
    private static final long[] PASS_DURATIONS_NS = new long[RING_SIZE];
    private static final long[] FRAME_TOTALS_NS = new long[RING_SIZE];
    private static int passWriteIdx;
    private static int frameWriteIdx;
    private static long currentFrameAccumNs;
    private static long lastStartNs;

    private CameraPreviewProfiler() {}

    public static void recordPassStart()
    {
        lastStartNs = System.nanoTime();
    }

    public static void recordPassEnd()
    {
        long dur = System.nanoTime() - lastStartNs;
        PASS_DURATIONS_NS[passWriteIdx] = dur;
        passWriteIdx = (passWriteIdx + 1) % RING_SIZE;
        currentFrameAccumNs += dur;
    }

    /** Call once at the end of all preview renders for this frame so
     *  the frame total moves into the per-frame ring. */
    public static void frameEnd()
    {
        FRAME_TOTALS_NS[frameWriteIdx] = currentFrameAccumNs;
        frameWriteIdx = (frameWriteIdx + 1) % RING_SIZE;
        currentFrameAccumNs = 0L;
    }

    public static void reset()
    {
        java.util.Arrays.fill(PASS_DURATIONS_NS, 0L);
        java.util.Arrays.fill(FRAME_TOTALS_NS, 0L);
        passWriteIdx = 0;
        frameWriteIdx = 0;
        currentFrameAccumNs = 0L;
    }

    public static String snapshot()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("== Camera Preview Profile ==\n");

        long[] passes = collectNonZero(PASS_DURATIONS_NS);
        long[] frames = collectNonZero(FRAME_TOTALS_NS);

        if (passes.length == 0)
        {
            sb.append("  no samples yet (open dashboard with previews active)\n");
            return sb.toString();
        }

        sb.append(String.format("per-pass:  n=%d  min=%.2fms  avg=%.2fms  p95=%.2fms  max=%.2fms%n",
            passes.length,
            ns2ms(min(passes)), ns2ms(avg(passes)), ns2ms(percentile(passes, 95)), ns2ms(max(passes))));

        if (frames.length > 0)
        {
            sb.append(String.format("per-frame: n=%d  min=%.2fms  avg=%.2fms  p95=%.2fms  max=%.2fms%n",
                frames.length,
                ns2ms(min(frames)), ns2ms(avg(frames)), ns2ms(percentile(frames, 95)), ns2ms(max(frames))));
        }

        int active = 0;
        for (CameraPreview p : CameraPreviewRegistry.list())
        {
            if (p.enabled) active++;
        }
        sb.append("active previews: ").append(active).append('\n');
        sb.append(CameraPreviewPerf.dump()).append('\n');

        return sb.toString();
    }

    private static long[] collectNonZero(long[] src)
    {
        int n = 0;
        for (long v : src) if (v > 0) n++;
        long[] out = new long[n];
        int j = 0;
        for (long v : src) if (v > 0) out[j++] = v;
        return out;
    }

    private static long min(long[] a) { long m = Long.MAX_VALUE; for (long v : a) if (v < m) m = v; return m; }
    private static long max(long[] a) { long m = 0L; for (long v : a) if (v > m) m = v; return m; }
    private static long avg(long[] a) { long s = 0L; for (long v : a) s += v; return a.length == 0 ? 0L : s / a.length; }
    private static long percentile(long[] a, int p)
    {
        if (a.length == 0) return 0L;
        long[] sorted = a.clone();
        java.util.Arrays.sort(sorted);
        int idx = Math.min(sorted.length - 1, (int) Math.ceil(p / 100D * sorted.length) - 1);
        return sorted[Math.max(0, idx)];
    }
    private static double ns2ms(long ns) { return ns / 1_000_000D; }

}
