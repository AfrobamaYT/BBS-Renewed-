package com.afrobama.bbstools.client.perf;

import com.mojang.blaze3d.systems.GpuQueryPool;
import com.mojang.blaze3d.buffers.GpuFence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalLong;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/** A submitted fence precedes availability; free queries require a new pre-pass reset. */
final class TimestampPairs
{
    private final GpuQueryPool pool;
    private final boolean[] pending;
    private final boolean[] prepared;
    private final GpuFence[] fences;
    private final long[] sequences;
    private final int bits;
    private final float period;
    private long nextSequence;
    private int active = -1;

    TimestampPairs(GpuQueryPool pool, int count, int bits, float period)
    {
        validateClock(bits, period);
        if (count < 1 || pool.size() < Math.multiplyExact(count, 2)) throw new IllegalArgumentException("Too few native query slots");
        this.pool = pool;
        this.pending = new boolean[count];
        this.prepared = new boolean[count];
        this.fences = new GpuFence[count];
        this.sequences = new long[count];
        this.bits = bits;
        this.period = period;
    }

    static void validateClock(int bits, float period)
    {
        if (bits < 1 || bits > 64 || !Float.isFinite(period) || period <= 0F)
            throw new IllegalArgumentException("GPU timestamp precision/period unsupported: " + bits + "/" + period);
    }

    static long elapsedTicks(long first, long last, int bits)
    {
        if (bits < 1 || bits > 64) throw new IllegalArgumentException("Invalid GPU timestamp bit width " + bits);
        long difference = last - first;
        long ticks = bits == 64 ? difference : difference & ((1L << bits) - 1L);
        if (ticks < 0L) throw new ArithmeticException("GPU timestamp interval exceeds unambiguous signed precision");
        return ticks;
    }

    void prepare(IntConsumer reset)
    {
        if (this.active >= 0) throw new IllegalStateException("Cannot reset an active GPU interval");
        for (int index = 0; index < this.pending.length; index++)
        {
            if (this.pending[index] || this.prepared[index]) continue;
            reset.accept(index * 2);
            this.prepared[index] = true;
        }
    }

    boolean begin(IntConsumer stamp)
    {
        if (this.active >= 0) throw new IllegalStateException("Previous GPU timestamp interval was not ended");
        for (int index = 0; index < this.pending.length; index++)
        {
            if (this.pending[index] || !this.prepared[index]) continue;
            stamp.accept(index * 2);
            this.prepared[index] = false;
            this.active = index;
            this.sequences[index] = this.nextSequence++;
            return true;
        }
        return false;
    }

    void end(IntConsumer stamp, Supplier<GpuFence> fence)
    {
        if (this.active < 0) return;
        stamp.accept(this.active * 2 + 1);
        this.fences[this.active] = java.util.Objects.requireNonNull(fence.get(), "Native submit fence");
        this.pending[this.active] = true;
        this.active = -1;
    }

    List<NativeGpuTimer.Sample> collect()
    {
        List<NativeGpuTimer.Sample> samples = new ArrayList<>();
        for (int index = 0; index < this.pending.length; index++)
        {
            if (!this.pending[index]) continue;
            /* Old availability can survive until the queued reset executes. */
            if (!this.fences[index].awaitCompletion(0L)) continue;
            OptionalLong[] values = this.pool.getValues(index * 2, 2);
            if (values.length != 2) throw new IllegalStateException("Native timestamp API returned the wrong result count");
            if (values[0].isEmpty() || values[1].isEmpty()) continue;
            long ticks = elapsedTicks(values[0].getAsLong(), values[1].getAsLong(), this.bits);
            double microseconds = ticks * (double) this.period / 1000D;
            if (!Double.isFinite(microseconds) || microseconds > Long.MAX_VALUE)
                throw new ArithmeticException("Native GPU timestamp interval overflow");
            samples.add(new NativeGpuTimer.Sample(this.sequences[index], Math.round(microseconds)));
            this.pending[index] = false;
            this.fences[index].close();
            this.fences[index] = null;
        }
        samples.sort(Comparator.comparingLong(NativeGpuTimer.Sample::sequence));
        return samples;
    }

    void close()
    {
        for (GpuFence fence : this.fences)
        {
            if (fence != null) fence.close();
        }
    }
}
