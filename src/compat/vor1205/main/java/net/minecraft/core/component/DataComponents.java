package net.minecraft.core.component;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.CustomData;

/**
 * vor1205: die drei Bestandteile, die BBS liest und schreibt, abgebildet auf NBT des ItemStacks
 * (mchorse.bbs_mod.compat.main.Komponenten).
 */
public final class DataComponents
{
    /** NBT-Unterknoten "BlockEntityTag". */
    public static final DataComponentType<CustomData> BLOCK_ENTITY_DATA = new DataComponentType<>("block_entity_data");
    /** Das ganze NBT des Stapels. */
    public static final DataComponentType<CustomData> CUSTOM_DATA = new DataComponentType<>("custom_data");
    /** display.Name. */
    public static final DataComponentType<Component> CUSTOM_NAME = new DataComponentType<>("custom_name");

    private DataComponents()
    {}
}
