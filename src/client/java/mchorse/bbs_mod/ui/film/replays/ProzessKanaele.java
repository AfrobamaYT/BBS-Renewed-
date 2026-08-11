package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Numerische keyframe-kanaele eines replays in anzeige-reihenfolge (kuratierte zuerst) —
 * die kanalliste des "Process replay(s)"-dialogs. Unveraendert aus {@code UIReplayList}
 * herausgeloest, die datei steht ueber der 800-zeilen-grenze und darf nur noch schrumpfen.
 */
public final class ProzessKanaele
{
    private ProzessKanaele()
    {}

    public static List<String> sammeln(Replay replay)
    {
        ArrayList<String> out = new ArrayList<>();
        HashSet<String> added = new HashSet<>();

        for (String id : ReplayKeyframes.CURATED_CHANNELS)
        {
            BaseValue baseValue = replay.keyframes.get(id);

            if (baseValue instanceof KeyframeChannel<?> channel && KeyframeFactories.isNumeric(channel.getFactory()))
            {
                out.add(id);
                added.add(id);
            }
        }

        for (KeyframeChannel<?> channel : replay.keyframes.getChannels())
        {
            if (!KeyframeFactories.isNumeric(channel.getFactory()) || added.contains(channel.getId()))
            {
                continue;
            }

            out.add(channel.getId());
        }

        return out;
    }
}
