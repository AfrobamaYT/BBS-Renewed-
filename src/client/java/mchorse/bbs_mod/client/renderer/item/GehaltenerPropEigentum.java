package mchorse.bbs_mod.client.renderer.item;

import io.netty.util.collection.IntObjectMap;
import java.util.Map;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.HandAusHotbar;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import net.minecraft.world.item.ItemStack;
import static mchorse.bbs_mod.client.renderer.item.GehaltenerPropDaten.*;

/** A held prop draws only while its own item is in its owner's hand. */
public final class GehaltenerPropEigentum
{
    public static boolean darfZeichnen(Form form, mchorse.bbs_mod.forms.renderers.FormRenderingContext context)
    {
        if (context.ui || context.entity == null || form.anchor.get().replay < 0 || context.entity.getForm() != form) return true;
        for (BaseFilmController controller : BBSModClient.getFilms().getControllers())
        {
            Boolean allowed = zeichnen(controller.film, controller.getEntities(), controller.getActors(), controller.getTick() + context.transition, form, context);
            if (allowed != null) return allowed;
        }
        if (BBSModClient.getDashboardIfCreated() != null)
        {
            UIFilmPanel panel = BBSModClient.getDashboardIfCreated().getPanel(UIFilmPanel.class);
            if (panel != null && panel.getData() != null)
            {
                Boolean allowed = zeichnen(panel.getData(), panel.getController().getEntities(), null, panel.getCursor() + context.transition, form, context);
                if (allowed != null) return allowed;
            }
        }
        return true;
    }

    private static Boolean zeichnen(Film film, IntObjectMap<IEntity> entities, Map<String, Integer> actors, float tick,
        Form form, mchorse.bbs_mod.forms.renderers.FormRenderingContext context)
    {
        for (IntObjectMap.PrimitiveEntry<IEntity> entry : entities.entries())
        {
            Replay replay = film.replays.getList().get(entry.key());
            boolean same = entry.value() == context.entity;
            if (!same && context.entity instanceof mchorse.bbs_mod.forms.entities.MCEntity entity && actors != null)
                same = java.util.Objects.equals(actors.get(replay.getId()), entity.getMcEntity().getId());
            if (same) return aktiv(film, replay, form, tick);
        }
        return null;
    }

    private static boolean aktiv(Film film, Replay replay, Form form, float filmTick)
    {
        if (form.anchor.get().replay >= film.replays.getList().size()) return true;
        Replay holder = film.replays.getList().get(form.anchor.get().replay);
        for (boolean off : new boolean[] {false, true})
        {
            boolean managed = false;
            for (KeyframeChannel<ItemStack> channel : kanaele(holder.keyframes, off))
                for (Keyframe<ItemStack> key : channel.getKeyframes())
                {
                    Verweis link = lesen(key.getValue());
                    if (link != null && link.off() == off && link.holder().equals(holder.uuid.get()) && link.replay().equals(replay.uuid.get()))
                        managed = true;
                }
            if (!managed)
                continue;
            float holderTick = holder.getTick((int)filmTick) + (filmTick - (int)filmTick);
            ItemStack held = off ? holder.keyframes.offHand.interpolate(holderTick, ItemStack.EMPTY) : HandAusHotbar.hand(holder.keyframes, holderTick);
            Verweis active = lesen(held);
            return active != null && active.off() == off && active.holder().equals(holder.uuid.get()) &&
                active.replay().equals(replay.uuid.get()) && active.bone().equals(form.anchor.get().attachment);
        }
        return true;
    }
}
