package mchorse.bbs_mod.compat.main;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * vor1205: 1.20.5s ItemStack#get/set/has/remove fuer die drei Bestandteile aus DataComponents,
 * auf das NBT von 1.20.4 abgebildet (per Interface-Injection an ItemStack, Mixin
 * ItemStackKomponentenMixin). Ein unbekannter Schluessel wirft laut.
 */
public interface KomponentenAlt
{
    private ItemStack stapel()
    {
        return (ItemStack) (Object) this;
    }

    @SuppressWarnings("unchecked")
    default <T> T get(DataComponentType<T> typ)
    {
        ItemStack s = this.stapel();

        if (typ == DataComponents.BLOCK_ENTITY_DATA)
        {
            CompoundTag tag = s.getTagElement("BlockEntityTag");

            return tag == null ? null : (T) CustomData.of(tag);
        }
        else if (typ == DataComponents.CUSTOM_DATA)
        {
            return s.getTag() == null ? null : (T) CustomData.of(s.getTag());
        }
        else if (typ == DataComponents.CUSTOM_NAME)
        {
            return s.hasCustomHoverName() ? (T) s.getHoverName() : null;
        }

        throw new IllegalArgumentException("Bestandteil vor 1.20.5 nicht abgebildet: " + typ.name);
    }

    default <T> T set(DataComponentType<T> typ, T wert)
    {
        T alt = this.get(typ);
        ItemStack s = this.stapel();

        if (typ == DataComponents.BLOCK_ENTITY_DATA)
        {
            s.addTagElement("BlockEntityTag", ((CustomData) wert).copyTagWithoutId());
        }
        else if (typ == DataComponents.CUSTOM_DATA)
        {
            s.setTag(((CustomData) wert).copyTag());
        }
        else if (typ == DataComponents.CUSTOM_NAME)
        {
            s.setHoverName((Component) wert);
        }

        return alt;
    }

    default boolean has(DataComponentType<?> typ)
    {
        return this.get(typ) != null;
    }

    default <T> T remove(DataComponentType<T> typ)
    {
        T alt = this.get(typ);
        ItemStack s = this.stapel();

        if (typ == DataComponents.BLOCK_ENTITY_DATA)
        {
            s.removeTagKey("BlockEntityTag");
        }
        else if (typ == DataComponents.CUSTOM_DATA)
        {
            s.setTag(null);
        }
        else if (typ == DataComponents.CUSTOM_NAME)
        {
            s.resetHoverName();
        }

        return alt;
    }
}
