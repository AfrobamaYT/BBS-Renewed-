package mchorse.bbs_mod.client.renderer.item;

import io.netty.util.collection.IntObjectMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.film.replays.UIReplayList;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import static mchorse.bbs_mod.client.renderer.item.GehaltenerPropDaten.*;
import static mchorse.bbs_mod.client.renderer.item.GehaltenerPropAuswahl.*;

public final class GehaltenerPropVerweis
{
    private static final Map<ModelBlockItemRenderer.Item, Verweis> ITEMS = Collections.synchronizedMap(new WeakHashMap<>());
    public static void merken(ModelBlockItemRenderer.Item item, ItemStack stack)
    {
        GehaltenerBlockBild.merken(item, stack);
        GehaltenerPropSichtbarkeit.registrieren();
        Verweis link = lesen(stack);
        if (link == null)
            ITEMS.remove(item);
        else
            ITEMS.put(item, link);
    }

    public static boolean verknuepft(ModelBlockItemRenderer.Item item, ItemDisplayContext mode)
    {
        Verweis link = ITEMS.get(item);
        return link != null && mchorse.bbs_mod.camera.clips.misc.TrackerClientClip.currentEntity != null
            && BBSModClient.getCameraController().getCurrent() instanceof mchorse.bbs_mod.camera.controller.CameraWorkCameraController
            && (mode == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || mode == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND);
    }

    public static IEntity aufloesen(ModelBlockItemRenderer.Item item, ItemDisplayContext mode, IntObjectMap<IEntity> entities, int holderIndex)
    {
        Kontext context = kontext(entities);
        Verweis link = ITEMS.get(item);
        if (context == null || link == null || link.off() != (mode == ItemDisplayContext.FIRST_PERSON_LEFT_HAND))
            return null;
        List<Replay> replays = context.film().replays.getList();
        if (holderIndex >= replays.size() || !replays.get(holderIndex).uuid.get().equals(link.holder()))
            return null;

        for (int i = 0; i < replays.size(); i++)
        {
            Replay replay = replays.get(i);
            IEntity entity = entities.get(i);
            Form form = entity == null ? null : entity.getForm();
            if (replay.uuid.get().equals(link.replay()) && replay.enabled.get() && form != null && form.visible.get() &&
                form.anchor.get().replay == holderIndex && form.anchor.get().attachment.equals(link.bone()))
                return entity;
        }
        return null;
    }

    public static IEntity neuer(ModelBlockItemRenderer.Item item, IntObjectMap<IEntity> entities, IEntity candidate, IEntity previous)
    {
        Kontext context = kontext(entities);
        Form source = item.entity.getProperties().getForm(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND);
        if (context == null || !gleicheForm(source, candidate.getForm()))
            return previous;
        Replay next = replay(context.film(), entities, candidate);
        Replay old = previous == null ? null : replay(context.film(), entities, previous);
        return next != null && (old == null || beginn(next, context.tick()) >= beginn(old, context.tick())) ? candidate : previous;
    }

    public static boolean wiederverwenden(UIReplayList list, Replay holder, String bone, ItemStack stack)
    {
        ItemStack main = mchorse.bbs_mod.film.replays.HandAusHotbar.hand(holder.keyframes, list.panel.getCursor());
        return wiederverwenden(list, holder, bone, stack, main == null || main.getItem() != mchorse.bbs_mod.BBSMod.MODEL_BLOCK_ITEM);
    }

    public static boolean wiederverwenden(UIReplayList list, Replay holder, String bone, ItemStack stack, boolean off)
    {
        Verweis link = lesen(stack);
        Replay prop = ziel(list.panel.getData(), link);
        if (prop == null || link.off() != off || !link.holder().equals(holder.uuid.get()) || !link.bone().equals(bone))
            return false;
        GehaltenerBlockPicker.zeigen(list.panel, prop);
        list.panel.getController().createEntities();
        list.panel.replayEditor.updateChannelsList();
        return true;
    }
    public static void normalisieren(Film film, Replay holder)
    {
        for (boolean off : new boolean[] {false, true})
            for (KeyframeChannel<ItemStack> channel : kanaele(holder.keyframes, off))
                for (Keyframe<ItemStack> key : channel.getKeyframes())
                {
                    ItemStack stack = key.getValue();
                    if (lesen(stack) != null) continue;
                    ModelBlockItemRenderer.Item item = ModelBlockItemRenderer.get(stack);
                    Form third = item == null ? null : item.entity.getProperties().getForm(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND);
                    if (third == null || third.visible.get()) continue;
                    Replay prop = alt(film, holder, off, key.getTick(), third);
                    if (prop != null) key.setValue(schreiben(stack, new Verweis(holder.uuid.get(), prop.uuid.get(), prop.form.get().anchor.get().attachment, off)));
                }
    }
    public static void binden(Film film, Replay holder, Replay prop, boolean off, ItemStack hollow)
    {
        Verweis link = new Verweis(holder.uuid.get(), prop.uuid.get(), prop.form.get().anchor.get().attachment, off);
        for (KeyframeChannel<ItemStack> channel : kanaele(holder.keyframes, off))
            for (Keyframe<ItemStack> key : channel.getKeyframes())
            {
                Verweis existing = lesen(key.getValue());
                if (existing != null && ziel(film, existing) != null && existing.off() == off && existing.holder().equals(holder.uuid.get())) continue;
                if (ItemStack.isSameItemSameComponents(unmark(key.getValue()), unmark(hollow))) key.setValue(schreiben(key.getValue(), link));
            }
    }
}
