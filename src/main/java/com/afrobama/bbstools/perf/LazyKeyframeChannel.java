// Coded by Afrobama/Claude — Tier 3f lazy keyframe channel.
//
// Observability hub for the KeyframeChannel allocation/read tracker.
// Wired into BBSDebugRegistry from BBSToolsModClient so /bbsdebug
// keyframes prints status + stats.
//
// What gets counted:
//   totalChannels       — every KeyframeChannel constructor call
//                         (Mixin: KeyframeChannelLazyMixin TAIL <init>)
//   allocatedChannels   — every first-touch (findSegment / interpolate)
//                         (Mixin: KeyframeChannelLazyMixin HEAD findSegment)
//   savedHeapBytes      — approximation: 16 bytes / never-touched ArrayList
//                         shell + 16 bytes / channel object overhead.
//                         Tier 3f doesn't actually free those today
//                         (the field is `final`); this counter exposes
//                         the THEORETICAL savings ceiling so later
//                         tiers (3g+) know whether deeper work is
//                         worth it.
//
// Reset semantics (KeyframeChannel.resetProperties does NOT reset
// the channel itself — it only clears form.runtimeValue). So our
// WeakHashMap survives properly across film reset/restart; entries
// only vanish when the channel is GC'd along with its parent Film.
package com.afrobama.bbstools.perf;

import com.afrobama.bbstools.debug.IFeatureDebugger;

import java.util.concurrent.atomic.AtomicLong;

public final class LazyKeyframeChannel implements IFeatureDebugger
{
    public static final LazyKeyframeChannel INSTANCE = new LazyKeyframeChannel();

    public final AtomicLong totalChannels = new AtomicLong();
    public final AtomicLong allocatedChannels = new AtomicLong();
    public final AtomicLong savedHeapBytes = new AtomicLong();

    private LazyKeyframeChannel() {}

    @Override public String getName() { return "keyframes"; }
    @Override public boolean isEnabled() { return BBSPerfConfig.ENABLE_LAZY_KEYFRAME_CHANNEL; }
    @Override public void setEnabled(boolean v) { BBSPerfConfig.ENABLE_LAZY_KEYFRAME_CHANNEL = v; }
    @Override public int priority() { return 120; }

    @Override
    public void writeStatus(StringBuilder sb)
    {
        sb.append("enabled=").append(BBSPerfConfig.ENABLE_LAZY_KEYFRAME_CHANNEL).append('\n');
        sb.append("backing=LazyArrayList (extends ArrayList)\n");
        sb.append("scope=ValueList parent (covers KeyframeChannel + all subclasses)\n");
    }

    @Override
    public void writeStats(StringBuilder sb)
    {
        long total = totalChannels.get();
        long touched = allocatedChannels.get();
        long wasted = Math.max(0L, total - touched);
        double pct = total == 0L ? 0.0D : (100.0D * wasted / total);
        sb.append("totalChannels=").append(total).append('\n');
        sb.append("mutationTouched=").append(touched).append('\n');
        sb.append("lazyKeyframeChannelBuilds=").append(BBSPerfConfig.lazyKeyframeChannelBuilds).append('\n');
        sb.append("neverMutated=").append(wasted)
          .append(" (").append(String.format("%.1f%%", pct)).append(")\n");
        sb.append("ceilingHeapKB=").append(savedHeapBytes.get() / 1024L).append('\n');
    }

}
