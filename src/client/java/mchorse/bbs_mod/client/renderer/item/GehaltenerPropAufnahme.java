package mchorse.bbs_mod.client.renderer.item;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import static mchorse.bbs_mod.client.renderer.item.GehaltenerPropDaten.*;
import static mchorse.bbs_mod.client.renderer.item.GehaltenerPropAuswahl.*;

/** Preserve item identity before a continued recording replaces its original source key. */
public final class GehaltenerPropAufnahme
{
    private record Quelle(float tick, ItemStack stack, MapType identity, Verweis link) {}
    private static ModelBlockEntity block(ItemStack stack)
    {
        if (stack == null || stack.getItem() != BBSMod.MODEL_BLOCK_ITEM || Minecraft.getInstance().level == null)
            return null;
        net.minecraft.world.item.component.TypedEntityData<net.minecraft.world.level.block.entity.BlockEntityType<?>> data =
            stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (data == null)
            return null;
        ModelBlockEntity block = new ModelBlockEntity(BlockPos.ZERO, BBSMod.MODEL_BLOCK.defaultBlockState());
        return data.loadInto(block, Minecraft.getInstance().level.registryAccess()) ? block : null;
    }

    private static MapType identitaet(ModelBlockEntity block)
    {
        Form third = FormUtils.copy(block.getProperties().getForm(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND));
        if (third == null)
            return null;
        third.visible.set(true);
        block.getProperties().setFormThirdPerson(third);
        return block.getProperties().toData().asMap();
    }

    private static boolean gleich(Quelle source, ItemStack stack, MapType identity)
    {
        return source.identity().equals(identity) && ItemStack.isSameItemSameComponents(vergleich(source.stack()), vergleich(stack));
    }

    private static List<Quelle> quellen(Film film, Replay holder, boolean off)
    {
        List<Quelle> sources = new ArrayList<>();
        for (KeyframeChannel<ItemStack> channel : kanaele(holder.keyframes, off))
            for (Keyframe<ItemStack> key : channel.getKeyframes())
            {
                ItemStack stack = key.getValue();
                ModelBlockEntity block = block(stack);
                Form third = block == null ? null : block.getProperties().getForm(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND);
                if (third == null || third.visible.get())
                    continue;
                Verweis link = lesen(stack);
                if (link == null)
                {
                    Replay prop = alt(film, holder, off, key.getTick(), third);
                    if (prop == null)
                        continue;
                    link = new Verweis(holder.uuid.get(), prop.uuid.get(), prop.form.get().anchor.get().attachment, off);
                }
                if (!link.holder().equals(holder.uuid.get()) || link.off() != off || ziel(film, link) == null)
                    continue;
                MapType identity = identitaet(block);
                if (identity != null)
                    sources.add(new Quelle(key.getTick(), schreiben(stack, link), identity, link));
            }
        sources.sort(Comparator.comparingDouble(Quelle::tick));
        return sources;
    }

    private static void anwenden(ReplayKeyframes keys, boolean off, List<Quelle> sources)
    {
        for (KeyframeChannel<ItemStack> channel : kanaele(keys, off))
            for (Keyframe<ItemStack> key : channel.getKeyframes())
            {
                if (lesen(key.getValue()) != null)
                    continue;
                ModelBlockEntity block = block(key.getValue());
                MapType identity = block == null ? null : identitaet(block);
                if (identity == null)
                    continue;
                Quelle best = null;
                for (Quelle source : sources)
                    if (source.tick() <= key.getTick() && gleich(source, key.getValue(), identity))
                        best = source;
                if (best != null)
                    key.setValue(best.stack().copyWithCount(key.getValue().getCount()));
            }
    }

    public static void normalisieren(Film film, Replay holder)
    {
        GehaltenerPropVerweis.normalisieren(film, holder);
        for (boolean off : new boolean[] {false, true})
            anwenden(holder.keyframes, off, quellen(film, holder, off));
    }

    public static void uebernehmen(Film film, Replay holder, ReplayKeyframes recorded)
    {
        GehaltenerPropVerweis.normalisieren(film, holder);
        for (boolean off : new boolean[] {false, true})
            anwenden(recorded, off, quellen(film, holder, off));
        holder.keyframes.copyOver(recorded, 0);
        normalisieren(film, holder);
    }
    private static ItemStack vergleich(ItemStack stack)
    {
        ItemStack copy = unmark(stack);
        copy.remove(DataComponents.BLOCK_ENTITY_DATA);
        return copy;
    }
}
