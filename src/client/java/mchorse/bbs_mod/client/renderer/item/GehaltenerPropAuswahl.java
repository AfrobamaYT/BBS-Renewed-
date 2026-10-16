package mchorse.bbs_mod.client.renderer.item;

import io.netty.util.collection.IntObjectMap;
import java.util.List;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

public final class GehaltenerPropAuswahl
{
    record Kontext(Film film, float tick) {}
    static Kontext kontext(IntObjectMap<IEntity> entities)
    {
        for (BaseFilmController controller : BBSModClient.getFilms().getControllers())
            if (controller.getEntities() == entities)
                return new Kontext(controller.film, controller.getTick());
        if (BBSModClient.getDashboardIfCreated() != null)
        {
            UIFilmPanel panel = BBSModClient.getDashboardIfCreated().getPanel(UIFilmPanel.class);
            if (panel != null && panel.getData() != null && panel.getController().getEntities() == entities)
                return new Kontext(panel.getData(), panel.getCursor());
        }
        return null;
    }

    static Replay replay(Film film, IntObjectMap<IEntity> entities, IEntity entity)
    {
        for (IntObjectMap.PrimitiveEntry<IEntity> entry : entities.entries())
            if (entry.value() == entity && entry.key() < film.replays.getList().size())
                return film.replays.getList().get(entry.key());
        return null;
    }

    static float beginn(Replay replay, float tick)
    {
        float start = -Float.MAX_VALUE;
        boolean previous = replay.form.get() != null && replay.form.get().visible.get();
        KeyframeChannel<Boolean> channel = replay.properties.properties.get("visible");
        if (channel != null)
            for (Keyframe<Boolean> key : channel.getKeyframes())
            {
                if (key.getTick() > tick)
                    break;
                if (key.getValue() && !previous)
                    start = key.getTick();
                previous = key.getValue();
            }
        return start;
    }

    static boolean sichtbar(Replay replay, float tick)
    {
        KeyframeChannel<Boolean> channel = replay.properties.properties.get("visible");
        boolean base = replay.form.get() != null && replay.form.get().visible.get();
        return channel == null ? base : channel.interpolate(tick, base);
    }

    static boolean gleicheForm(Form a, Form b)
    {
        if (a == null || b == null || a.getClass() != b.getClass())
            return false;
        return a instanceof ModelForm left && b instanceof ModelForm right ? left.model.get().equals(right.model.get())
                                                                           : a.getDisplayName().equals(b.getDisplayName());
    }

    static String knochen(Replay holder, boolean off)
    {
        if (!(holder.form.get() instanceof ModelForm form))
            return "";
        mchorse.bbs_mod.cubic.ModelInstance model = BBSModClient.getModels().getModel(form.model.get());
        if (model == null)
            return "";
        List<mchorse.bbs_mod.cubic.model.ArmorSlot> slots = off ? model.getItemsOff() : model.getItemsMain();
        return slots.isEmpty() ? "" : slots.get(0).group;
    }

    static Replay alt(Film film, Replay holder, boolean off, float tick, Form source)
    {
        String bone = knochen(holder, off);
        if (bone.isEmpty())
            return null;
        int index = film.replays.getList().indexOf(holder);
        Replay best = null;
        for (Replay candidate : film.replays.getList())
        {
            Form form = candidate.form.get();
            if (candidate.enabled.get() && gleicheForm(source, form) && form.anchor.get().replay == index && bone.equals(form.anchor.get().attachment) &&
                sichtbar(candidate, tick) && (best == null || beginn(candidate, tick) >= beginn(best, tick)))
                best = candidate;
        }
        return best;
    }
}
