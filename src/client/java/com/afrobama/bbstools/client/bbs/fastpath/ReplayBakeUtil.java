// Coded by Afrobama/Claude — Army System: per-replay bake length.
//
// The number of distinct ticks a replay animates over = the denominator for
// the cache bar AND the range the background baker fills. BBS's film duration
// (film.camera.calculateDuration()) is the CAMERA timeline length, which is 0
// when a crowd scene has no camera clips — that made expected=1 and froze the
// bake at one tick per replay. The real length is the replay's own keyframe
// span (max last-keyframe tick across its channels). We take the max of the
// two so a long camera shot OR long keyframe animation both fill fully.
//
// Memoized per replay UUID (keyframe edits are rare relative to render frames);
// cleared with the cache.
package com.afrobama.bbstools.client.bbs.fastpath;

import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public final class ReplayBakeUtil
{
    private ReplayBakeUtil() {}

    private static final Map<String, Integer> KEYFRAME_DURATION = new ConcurrentHashMap<>();

    /** Ticks to bake/track for a replay. looping replays wrap at their loop
     *  length; otherwise max(camera timeline, keyframe span). Always >= 1. */
    public static int expectedTicks(Replay replay, int cameraDuration)
    {
        if (replay == null) return Math.max(1, cameraDuration);

        int loop = replay.looping.get();
        if (loop > 0) return loop;

        String uuid = replay.uuid.get();
        Integer memo = uuid == null ? null : KEYFRAME_DURATION.get(uuid);
        int kf = memo != null ? memo : computeKeyframeDuration(replay);
        if (memo == null && uuid != null) KEYFRAME_DURATION.put(uuid, kf);

        return Math.max(1, Math.max(cameraDuration, kf));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static int computeKeyframeDuration(Replay replay)
    {
        int max = 0;
        try
        {
            for (KeyframeChannel channel : replay.keyframes.getChannels())
            {
                List kfs = channel.getKeyframes();
                if (kfs != null && !kfs.isEmpty())
                {
                    float t = ((Keyframe) kfs.get(kfs.size() - 1)).getTick();
                    int ti = (int) Math.ceil(t) + 1;
                    if (ti > max) max = ti;
                }
            }
        }
        catch (Throwable ignored) {}
        return max;
    }

    public static void clearMemo()
    {
        KEYFRAME_DURATION.clear();
    }

}
