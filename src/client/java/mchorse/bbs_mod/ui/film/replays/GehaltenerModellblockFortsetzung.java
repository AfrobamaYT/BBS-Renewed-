package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Preserve the animated prop when a continued recording supplies the original inventory item. */
public final class GehaltenerModellblockFortsetzung
{
    private GehaltenerModellblockFortsetzung()
    {}

    public static void uebernehmen(Film film, Replay holder, ReplayKeyframes recorded)
    {
        mchorse.bbs_mod.client.renderer.item.GehaltenerPropAufnahme.uebernehmen(film, holder, recorded);
    }

    public static void erhalten(Film film, Replay holder)
    {
        mchorse.bbs_mod.client.renderer.item.GehaltenerPropAufnahme.normalisieren(film, holder);
    }

    /* Existing private helpers retain the installed class schema during live redefinition. */
    private static void erhalten(KeyframeChannel<ItemStack> channel)
    {
        ItemStack previous = ItemStack.EMPTY;
        MapType identity = null;

        for (Keyframe<ItemStack> key : channel.getKeyframes())
        {
            ItemStack stack = key.getValue();
            ModelBlockEntity block = lesen(stack);

            if (block == null)
            {
                previous = ItemStack.EMPTY;
                identity = null;
                continue;
            }

            Form third = block.getProperties().getForm(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND);
            boolean hidden = third != null && !third.visible.get();

            if (third != null)
            {
                third.visible.set(true);
                block.getProperties().setFormThirdPerson(third);
            }

            MapType data = block.getProperties().toData().asMap();

            /* Compare decoded properties: old films omit defaults and use the older anchor format. */
            if (hidden)
            {
                previous = stack;
                identity = data;
            }
            else if (identity != null && identity.equals(data) && gleicheKomponenten(previous, stack))
            {
                key.setValue(previous.copyWithCount(stack.getCount()));
            }
            else
            {
                previous = ItemStack.EMPTY;
                identity = null;
            }
        }
    }

    private static boolean gleicheKomponenten(ItemStack a, ItemStack b)
    {
        ItemStack left = mchorse.bbs_mod.client.renderer.item.GehaltenerPropDaten.unmark(a);
        ItemStack right = mchorse.bbs_mod.client.renderer.item.GehaltenerPropDaten.unmark(b);

        left.remove(DataComponents.BLOCK_ENTITY_DATA);
        right.remove(DataComponents.BLOCK_ENTITY_DATA);

        return ItemStack.isSameItemSameComponents(left, right);
    }

    private static ModelBlockEntity lesen(ItemStack stack)
    {
        net.minecraft.client.multiplayer.ClientLevel world = Minecraft.getInstance().level;
        net.minecraft.world.item.component.TypedEntityData<net.minecraft.world.level.block.entity.BlockEntityType<?>> data = stack.get(DataComponents.BLOCK_ENTITY_DATA);

        if (world == null || stack.getItem() != BBSMod.MODEL_BLOCK_ITEM || data == null)
        {
            return null;
        }

        ModelBlockEntity block = new ModelBlockEntity(BlockPos.ZERO, BBSMod.MODEL_BLOCK.defaultBlockState());

        data.loadInto(block, world.registryAccess());

        return block;
    }

    public static boolean wiederverwenden(UIReplayList list, Replay holder, String bone, ItemStack stack)
    {
        return mchorse.bbs_mod.client.renderer.item.GehaltenerPropVerweis.wiederverwenden(list, holder, bone, stack);
    }
}
