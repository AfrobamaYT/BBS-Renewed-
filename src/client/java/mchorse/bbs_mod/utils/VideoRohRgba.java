package mchorse.bbs_mod.utils;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/** Recorders whose encoder takes raw RGBA (see VideoRecorder.startRecording). A class of its own
 * so the switch swaps into a running game without adding a field to a loaded class. */
public final class VideoRohRgba
{
    private static final Set<VideoRecorder> AN = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    public static void setzen(VideoRecorder recorder, boolean an)
    {
        if (an)
        {
            AN.add(recorder);
        }
        else
        {
            AN.remove(recorder);
        }
    }

    public static boolean an(VideoRecorder recorder)
    {
        return AN.contains(recorder);
    }

    private VideoRohRgba()
    {}
}
