package com.afrobama.bbstools.kernel.logik;

import com.afrobama.bbstools.perf.BBSPerfConfig;
import com.afrobama.bbstools.perf.LazyArrayList;
import com.afrobama.bbstools.perf.LazyKeyframeChannel;

import java.util.ArrayList;

/**
 * Logik hinter {@code KeyframeChannelLazyMixin}, hierher verschoben damit sie über
 * {@link com.afrobama.bbstools.kernel.KernelBruecke} hotswapbar bleibt.
 *
 * <p>Ersetzt {@code ValueList}'s eager {@code new ArrayList<>()} feld-init durch entweder eine
 * vanilla {@link ArrayList} (feature AUS) oder {@link LazyArrayList} (feature AN). Siehe
 * {@code KeyframeChannelLazyMixin} für die volle begründung des tier-3f-designs.</p>
 */
public final class KeyframeChannelLazyLogik
{
    private KeyframeChannelLazyLogik() {}

    public static ArrayList<Object> lazyArrayList()
    {
        LazyKeyframeChannel.INSTANCE.totalChannels.incrementAndGet();
        LazyKeyframeChannel.INSTANCE.savedHeapBytes.addAndGet(32L);

        if (!BBSPerfConfig.ENABLE_LAZY_KEYFRAME_CHANNEL)
        {
            return new ArrayList<>();
        }

        return new LazyArrayList<>();
    }
}
