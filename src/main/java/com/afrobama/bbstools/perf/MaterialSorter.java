// Coded by Afrobama/Claude — Tier 3c material sorting + state minimization.
//
// 64-bit sort-key radix sort over draw entries. Groups by (texture, blend,
// depth-state, model, distance-bucket) to minimize state changes during
// flush. Allocation-free hot path via reused ThreadLocal scratch.
//
// Key layout (high → low bits):
//   [63..48] texture hash (16 bits)
//   [47..44] blend mode    (4 bits)
//   [43..40] depth state   (4 bits)
//   [39..24] model hash    (16 bits)
//   [23..16] distance bkt  (8 bits)  back-to-front for translucent
//   [15..00] sequence idx  (16 bits) stable-sort tiebreaker
//
// Radix sort: 4 passes × 16-bit chunks. 65536 buckets × 4 passes =
// 262144 ops + 4 × N moves. For N=100k = ~660k ops/frame ≈ 1-2ms.
package com.afrobama.bbstools.perf;

public final class MaterialSorter
{
    private static final int RADIX = 16;
    private static final int BUCKETS = 1 << RADIX;
    private static final int MASK = BUCKETS - 1;

    private static final ThreadLocal<int[]> TL_COUNTS = ThreadLocal.withInitial(() -> new int[BUCKETS]);
    /** Code-review-fix: pre-size scratch to 4096 so first-call doesn't
     *  realloc on each worker thread (avoids first-frame stall). */
    private static final ThreadLocal<long[]> TL_TMP_KEYS = ThreadLocal.withInitial(() -> new long[4096]);
    private static final ThreadLocal<int[]> TL_TMP_IDX = ThreadLocal.withInitial(() -> new int[4096]);

    private MaterialSorter() {}

    /**
     * Pack a 64-bit sort key.
     */
    public static long packKey(int textureHash16, int blendMode4, int depthState4,
        int modelHash16, int distBucket8, int seqIdx16)
    {
        return ((long) (textureHash16 & 0xFFFF) << 48)
             | ((long) (blendMode4 & 0xF) << 44)
             | ((long) (depthState4 & 0xF) << 40)
             | ((long) (modelHash16 & 0xFFFF) << 24)
             | ((long) (distBucket8 & 0xFF) << 16)
             | ((long) (seqIdx16 & 0xFFFF));
    }

    /**
     * Sort indices[0..n] by keys[0..n] using 4-pass 16-bit radix sort.
     * keys[] and indices[] are MODIFIED IN PLACE (final sorted order).
     * Allocation-free for repeated N at the same scale (reuses TL buffers).
     */
    public static void radixSort(long[] keys, int[] indices, int n)
    {
        if (n < 2) return;

        long[] tmpKeys = TL_TMP_KEYS.get();
        int[] tmpIdx = TL_TMP_IDX.get();
        if (tmpKeys.length < n)
        {
            tmpKeys = new long[Math.max(n, tmpKeys.length * 2)];
            tmpIdx = new int[tmpKeys.length];
            TL_TMP_KEYS.set(tmpKeys);
            TL_TMP_IDX.set(tmpIdx);
        }
        int[] counts = TL_COUNTS.get();

        long[] srcK = keys;
        int[] srcI = indices;
        long[] dstK = tmpKeys;
        int[] dstI = tmpIdx;

        for (int pass = 0; pass < 4; pass++)
        {
            int shift = pass * RADIX;
            for (int b = 0; b < BUCKETS; b++) counts[b] = 0;
            for (int i = 0; i < n; i++)
            {
                int bucket = (int) (srcK[i] >>> shift) & MASK;
                counts[bucket]++;
            }
            int sum = 0;
            for (int b = 0; b < BUCKETS; b++)
            {
                int c = counts[b];
                counts[b] = sum;
                sum += c;
            }
            for (int i = 0; i < n; i++)
            {
                int bucket = (int) (srcK[i] >>> shift) & MASK;
                int pos = counts[bucket]++;
                dstK[pos] = srcK[i];
                dstI[pos] = srcI[i];
            }
            long[] swK = srcK; srcK = dstK; dstK = swK;
            int[] swI = srcI; srcI = dstI; dstI = swI;
        }

        /* If swapped odd times, copy final result back to caller-owned. */
        if (srcK != keys)
        {
            System.arraycopy(srcK, 0, keys, 0, n);
            System.arraycopy(srcI, 0, indices, 0, n);
        }
    }

}
