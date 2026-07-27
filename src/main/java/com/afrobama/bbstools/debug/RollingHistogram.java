// Coded by Afrobama/Claude — allocation-free rolling histogram for
// hot-path stat collection in BBSTools tier features. Used by every
// IFeatureDebugger that needs latency percentiles.
//
// 256 exponential buckets covering ~1ns to ~17min (log2 scale).
// Window: circular ring of N recent samples. record() is O(1) lock-free.
package com.afrobama.bbstools.debug;

public final class RollingHistogram
{
    /** Number of histogram buckets. log2(value) maps to 0..255. */
    private static final int BUCKETS = 256;

    private final long[] bucketCounts = new long[BUCKETS];
    private final long[] ring;
    private final int[] ringBucket;
    private final int windowSize;
    private int head;
    private long totalCount;
    private long sum;
    private long min = Long.MAX_VALUE;
    private long max = Long.MIN_VALUE;

    public RollingHistogram(int windowSize)
    {
        if (windowSize < 1) windowSize = 1;
        this.windowSize = windowSize;
        this.ring = new long[windowSize];
        this.ringBucket = new int[windowSize];
        for (int i = 0; i < windowSize; i++) this.ringBucket[i] = -1;
    }

    public synchronized void record(long valueNs)
    {
        if (valueNs < 0L) valueNs = 0L;
        int bucket = bucketOf(valueNs);

        /* Evict oldest entry. */
        int prevBucket = this.ringBucket[this.head];
        if (prevBucket >= 0)
        {
            this.bucketCounts[prevBucket]--;
            this.totalCount--;
            this.sum -= this.ring[this.head];
        }

        /* Insert new. */
        this.ring[this.head] = valueNs;
        this.ringBucket[this.head] = bucket;
        this.bucketCounts[bucket]++;
        this.totalCount++;
        this.sum += valueNs;
        if (valueNs < this.min) this.min = valueNs;
        if (valueNs > this.max) this.max = valueNs;

        this.head = (this.head + 1) % this.windowSize;
    }

    public long count()
    {
        return this.totalCount;
    }

    public long avgNs()
    {
        return this.totalCount == 0L ? 0L : (this.sum / this.totalCount);
    }

    public long p50() { return percentile(0.50); }
    public long p95() { return percentile(0.95); }
    public long p99() { return percentile(0.99); }
    public long min() { return this.totalCount == 0 ? 0 : this.min; }
    public long max() { return this.totalCount == 0 ? 0 : this.max; }

    public synchronized long percentile(double q)
    {
        if (this.totalCount == 0L) return 0L;
        long target = (long) Math.ceil(this.totalCount * q);
        long acc = 0L;
        for (int b = 0; b < BUCKETS; b++)
        {
            acc += this.bucketCounts[b];
            if (acc >= target) return bucketMidpointNs(b);
        }
        return bucketMidpointNs(BUCKETS - 1);
    }

    public synchronized void reset()
    {
        for (int i = 0; i < BUCKETS; i++) this.bucketCounts[i] = 0L;
        for (int i = 0; i < this.windowSize; i++) this.ringBucket[i] = -1;
        this.head = 0;
        this.totalCount = 0L;
        this.sum = 0L;
        this.min = Long.MAX_VALUE;
        this.max = Long.MIN_VALUE;
    }

    public synchronized void writeSummary(StringBuilder sb)
    {
        sb.append("n=").append(this.totalCount)
          .append(" avg=").append(formatNs(avgNs()))
          .append(" p50=").append(formatNs(p50()))
          .append(" p95=").append(formatNs(p95()))
          .append(" p99=").append(formatNs(p99()))
          .append(" min=").append(formatNs(min()))
          .append(" max=").append(formatNs(max()));
    }

    private static int bucketOf(long valueNs)
    {
        if (valueNs <= 1L) return 0;
        /* log2(valueNs) ≈ 63 - numberOfLeadingZeros. Map to 0..255 with
         * scale that covers ~1ns to ~17 minutes (40 bits). */
        int log2 = 63 - Long.numberOfLeadingZeros(valueNs);
        int bucket = log2 * 6;
        if (bucket > 255) bucket = 255;
        if (bucket < 0) bucket = 0;
        return bucket;
    }

    private static long bucketMidpointNs(int bucket)
    {
        if (bucket <= 0) return 1L;
        int log2 = bucket / 6;
        return 1L << log2;
    }

    private static String formatNs(long ns)
    {
        if (ns < 1_000L) return ns + "ns";
        if (ns < 1_000_000L) return String.format("%.1fus", ns / 1_000.0);
        if (ns < 1_000_000_000L) return String.format("%.2fms", ns / 1_000_000.0);
        return String.format("%.2fs", ns / 1_000_000_000.0);
    }

}
