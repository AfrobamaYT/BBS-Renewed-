package mchorse.bbs_mod.client.renderer.item;

import io.netty.util.collection.IntObjectMap;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.clips.misc.TrackerClientClip;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import net.minecraft.world.item.ItemDisplayContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Task 114, first person: the model block the tracked replay holds is drawn by the vanilla hand
 * (tracker clip with "hand"), not by the replay's model -- the owner's films remove the model's
 * arms so only the vanilla hand shows (2026-10-13). "Animate held model block" turned the block
 * into a prop replay anchored to the model's hand; that prop is what gets keyframed. Here the
 * first-person hand draws the prop's current, animated form in place of the item's own, so the
 * animation shows where the block is actually seen, bobbing with the hand.
 *
 * <p>Only for a held block that "Animate held model block" hollowed out (its third-person form is
 * invisible) -- any other model block in the hand draws as before.</p>
 */
public final class GehaltenerPropEgo
{
    private GehaltenerPropEgo()
    {}

    /** The prop entity to draw instead of {@code item}'s first-person form, or null. */
    public static IEntity ersatz(ModelBlockItemRenderer.Item item, ItemDisplayContext mode)
    {
        if (mode != ItemDisplayContext.FIRST_PERSON_RIGHT_HAND && mode != ItemDisplayContext.FIRST_PERSON_LEFT_HAND)
        {
            return null;
        }

        Form third = item.entity.getProperties().getForm(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND);
        IEntity holder = TrackerClientClip.currentEntity;

        if (holder == null || third == null || third.visible.get())
        {
            return null;
        }

        for (IntObjectMap<IEntity> entities : maps())
        {
            int index = -1;

            for (IntObjectMap.PrimitiveEntry<IEntity> entry : entities.entries())
            {
                if (entry.value() == holder)
                {
                    index = entry.key();
                }
            }

            if (index < 0)
            {
                continue;
            }

            for (IEntity entity : entities.values())
            {
                Form form = entity.getForm();

                if (entity != holder && form != null && form.anchor.get().replay == index && !form.anchor.get().attachment.isEmpty())
                {
                    return entity;
                }
            }
        }

        return null;
    }

    /** Every film's entities that can be tracked right now: playing films and the editor's. */
    private static List<IntObjectMap<IEntity>> maps()
    {
        List<IntObjectMap<IEntity>> maps = new ArrayList<>();

        for (BaseFilmController controller : BBSModClient.getFilms().getControllers())
        {
            maps.add(controller.getEntities());
        }

        UIDashboard dashboard = BBSModClient.getDashboardIfCreated();
        UIFilmPanel panel = dashboard == null ? null : dashboard.getPanel(UIFilmPanel.class);

        if (panel != null && panel.getData() != null)
        {
            maps.add(panel.getController().getEntities());
        }

        return maps;
    }
}
