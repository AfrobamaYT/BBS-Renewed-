package mchorse.bbs_mod.client.renderer.item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class GehaltenerPropDaten
{
    private static final String KEY = "bbs:held_prop";
    private static final Map<CustomData, Verweis> LINKS = Collections.synchronizedMap(new WeakHashMap<>());
    record Verweis(String holder, String replay, String bone, boolean off) {}
    static Verweis lesen(ItemStack stack)
    {
        if (stack == null || stack.getItem() != BBSMod.MODEL_BLOCK_ITEM)
            return null;
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (LINKS.containsKey(data))
            return LINKS.get(data);
        CompoundTag tag = data.copyTag().getCompound(KEY).orElse(null);
        if (tag == null)
        {
            LINKS.put(data, null);
            return null;
        }
        String owner = tag.getString("holder").orElse("");
        String prop = tag.getString("replay").orElse("");
        String bone = tag.getString("bone").orElse("");
        Verweis link = owner.isEmpty() || prop.isEmpty() || bone.isEmpty() ? null : new Verweis(owner, prop, bone, tag.getBoolean("off").orElse(false));
        LINKS.put(data, link);
        return link;
    }

    static ItemStack schreiben(ItemStack stack, Verweis link)
    {
        ItemStack copy = stack.copy();
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag binding = new CompoundTag();
        binding.putString("holder", link.holder());
        binding.putString("replay", link.replay());
        binding.putString("bone", link.bone());
        binding.putBoolean("off", link.off());
        tag.put(KEY, binding);
        copy.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return copy;
    }

    static List<KeyframeChannel<ItemStack>> kanaele(ReplayKeyframes keys, boolean off)
    {
        if (off)
            return List.of(keys.offHand);
        List<KeyframeChannel<ItemStack>> result = new ArrayList<>();
        result.add(keys.mainHand);
        Collections.addAll(result, keys.hotbarSlots);
        return result;
    }

    static Replay ziel(Film film, Verweis link)
    {
        if (link != null)
            for (Replay replay : film.replays.getList())
                if (replay.uuid.get().equals(link.replay()))
                    return replay;
        return null;
    }
    public static ItemStack unmark(ItemStack stack)
    {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack copy = stack.copy();
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.remove(KEY);
        if (tag.isEmpty()) copy.remove(DataComponents.CUSTOM_DATA);
        else copy.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return copy;
    }
}
