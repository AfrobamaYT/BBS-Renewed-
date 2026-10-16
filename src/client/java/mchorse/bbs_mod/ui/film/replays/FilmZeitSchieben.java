package mchorse.bbs_mod.ui.film.replays;

import com.mojang.blaze3d.platform.InputConstants;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.utils.keys.KeyCombo;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.KeyframeLoop;

import java.util.ArrayList;
import java.util.List;

/**
 * Insert or remove time at the playhead (animBot's insert/remove inbetween, Maya's Insert Time,
 * the original BBS Shift+S): every key, loop and action clip of the current actor after the cursor
 * moves one tick later or earlier, so the timing after the cursor changes without dragging keys.
 * Keys on the cursor stay. Removing stops when a key would land on or pass a key before it.
 */
public final class FilmZeitSchieben
{
    public static final KeyCombo EINFUEGEN = new KeyCombo("insert_time", IKey.raw("Insert one tick of time after the playhead"),
        InputConstants.KEY_PERIOD, InputConstants.KEY_LALT).repeatable().categoryKey("keyframes");
    public static final KeyCombo ENTFERNEN = new KeyCombo("remove_time", IKey.raw("Remove one tick of time after the playhead"),
        InputConstants.KEY_COMMA, InputConstants.KEY_LALT).repeatable().categoryKey("keyframes");

    private FilmZeitSchieben()
    {}

    /** Keep new callbacks in the new class, preserving the loaded controller's lambda methods. */
    public static void anmelden(mchorse.bbs_mod.ui.film.controller.UIFilmController controller, java.util.function.Supplier<Boolean> hasActor, IKey category)
    {
        controller.keys().register(EINFUEGEN, () -> schieben(controller.panel, 1F)).strict().active(hasActor).category(category);
        controller.keys().register(ENTFERNEN, () -> schieben(controller.panel, -1F)).strict().active(hasActor).category(category);
    }

    /** Moves everything after the cursor by {@code delta} ticks; false (and a message) when nothing moved. */
    public static boolean schieben(UIFilmPanel panel, float delta)
    {
        Replay replay = panel.replayEditor == null ? null : panel.replayEditor.getReplay();

        if (replay == null)
        {
            return false;
        }

        float cursor = panel.getCursor();
        List<KeyframeChannel<?>> kanaele = kanaele(replay);

        if (delta < 0F && !frei(kanaele, cursor, -delta))
        {
            panel.getContext().notifyInfo(IKey.raw("Cannot remove time: a key sits within the tick after the playhead"));

            return false;
        }

        panel.getUndoHandler().submitUndo();
        panel.getUndoHandler().getUndoManager().markLastUndoNoMerging();
        BaseValue.edit(replay, r ->
        {
            for (KeyframeChannel<?> channel : kanaele)
            {
                for (Keyframe<?> key : channel.getKeyframes())
                {
                    if (key.getTick() > cursor) key.setTick(key.getTick() + delta);
                }

                for (KeyframeLoop loop : new ArrayList<>(channel.getLoops()))
                {
                    channel.putLoop(new KeyframeLoop(loop.id(), nach(loop.start(), cursor, delta),
                        nach(loop.sourceEnd(), cursor, delta), nach(loop.end(), cursor, delta)));
                }
            }

            for (Clip clip : r.actions.get())
            {
                if (clip.tick.get() > cursor) clip.tick.set(Math.round(clip.tick.get() + delta));
            }
        });
        panel.getUndoHandler().submitUndo();
        panel.getUndoHandler().getUndoManager().markLastUndoNoMerging();

        return true;
    }

    private static float nach(float tick, float cursor, float delta)
    {
        return tick > cursor ? tick + delta : tick;
    }

    private static List<KeyframeChannel<?>> kanaele(Replay replay)
    {
        List<KeyframeChannel<?>> kanaele = new ArrayList<>(replay.keyframes.getChannels());

        for (KeyframeChannel<?> channel : replay.properties.properties.values())
        {
            kanaele.add(channel);
        }

        return kanaele;
    }

    /** No key in (cursor, cursor + amount), and none at cursor + amount when one sits on the cursor. */
    private static boolean frei(List<KeyframeChannel<?>> kanaele, float cursor, float amount)
    {
        for (KeyframeChannel<?> channel : kanaele)
        {
            boolean amCursor = false;

            for (Keyframe<?> key : channel.getKeyframes())
            {
                float t = key.getTick();

                if (t == cursor) amCursor = true;
                if (t > cursor && t < cursor + amount) return false;
                if (t == cursor + amount && amCursor) return false;
            }
        }

        return true;
    }
}
