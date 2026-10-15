package net.minecraft.world.item.component;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;

/** vor1205: 1.20.5s CustomData als unveraenderliche Huelle um ein CompoundTag. */
public final class CustomData
{
    public static final CustomData EMPTY = new CustomData(new CompoundTag());

    private final CompoundTag tag;

    private CustomData(CompoundTag tag)
    {
        this.tag = tag;
    }

    public static CustomData of(CompoundTag tag)
    {
        return new CustomData(tag.copy());
    }

    public boolean isEmpty()
    {
        return this.tag.isEmpty();
    }

    public CompoundTag copyTag()
    {
        return this.tag.copy();
    }

    public CompoundTag copyTagWithoutId()
    {
        CompoundTag kopie = this.tag.copy();

        kopie.remove("id");

        return kopie;
    }

    /** Wie 1.20.5s loadInto: Daten in eine Block-Entitaet laden (1.20.4 kennt nur load). */
    public void loadInto(BlockEntity ziel, HolderLookup.Provider register)
    {
        ziel.load(this.copyTag());
    }

    @Deprecated
    public CompoundTag getUnsafe()
    {
        return this.tag;
    }
}
