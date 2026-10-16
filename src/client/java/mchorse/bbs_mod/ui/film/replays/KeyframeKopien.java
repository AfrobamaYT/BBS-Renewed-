package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

import java.util.ArrayList;
import java.util.List;

/**
 * Shift+D (Blender duplicate-and-grab) for {@link KeyframeGreifen}: the selected keys move as with G,
 * and detached copies taken at the start are inserted where they were once the move is kept.
 */
final class KeyframeKopien
{
    private record Kopie(KeyframeChannel channel, Keyframe key)
    {}

    private final List<Kopie> kopien = new ArrayList<>();

    void merken(KeyframeChannel channel, Keyframe key)
    {
        Keyframe kopie = new Keyframe("", key.getFactory(), key.getTick(), null);

        kopie.copy(key);
        this.kopien.add(new Kopie(channel, kopie));
    }

    boolean leer()
    {
        return this.kopien.isEmpty();
    }

    /**
     * Copies where the keys were. Appended silently (ValueList.add): a notifying insert reached the
     * undo handler first and the one undo step went back to "moved, no copy" (measured 2026-10-16);
     * submitKeyframes then sorts, keeps the selection on the moved keys and records the whole step.
     * A moved key that landed on a copy's tick wins, as channel.insert would.
     */
    void lassen(UIKeyframes view)
    {
        for (UIKeyframeSheet sheet : view.getGraph().getSheets())
        {
            for (Kopie kopie : this.kopien)
            {
                if (kopie.channel == sheet.channel && !belegt(sheet.channel, kopie.key.getTick()))
                {
                    sheet.channel.add(kopie.key);
                }
            }
        }
    }

    private static boolean belegt(KeyframeChannel channel, float tick)
    {
        for (Object key : channel.getKeyframes())
        {
            if (((Keyframe) key).getTick() == tick)
            {
                return true;
            }
        }

        return false;
    }
}
