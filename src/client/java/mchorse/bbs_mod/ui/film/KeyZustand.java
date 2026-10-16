package mchorse.bbs_mod.ui.film;

import com.mcstudio.nano.NanoDesign;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

/**
 * Katalog G7: Blender's property colours for a keyframe channel at the playhead -- "k" a key on this
 * tick (yellow), "a" keys elsewhere (green), "" not animated (plain field).
 */
public final class KeyZustand
{
    private KeyZustand()
    {}

    public static String von(Object kanal, int tick)
    {
        if (!(kanal instanceof KeyframeChannel<?> channel) || channel.isEmpty())
        {
            return "";
        }

        return amTick(channel, tick) ? "k" : "a";
    }

    /** Binary search, as this runs every frame over recorded channels (a key per tick); mid-drag the list may be unsorted for a frame */
    public static boolean amTick(KeyframeChannel<?> channel, int tick)
    {
        float quelle = channel.getSourceTick(tick); /* inside a keyframe loop: the source pass */
        java.util.List<? extends Keyframe<?>> keys = channel.getKeyframes();
        int low = 0;
        int high = keys.size();

        while (low < high)
        {
            int mid = (low + high) >>> 1;

            if (keys.get(mid).getTick() < quelle) low = mid + 1;
            else high = mid;
        }

        return low < keys.size() && keys.get(low).getTick() == quelle;
    }

    public static int grund(String zustand)
    {
        return "k".equals(zustand) ? NanoDesign.FELD_KEY : "a".equals(zustand) ? NanoDesign.FELD_ANIMIERT : NanoDesign.FELD;
    }
}
