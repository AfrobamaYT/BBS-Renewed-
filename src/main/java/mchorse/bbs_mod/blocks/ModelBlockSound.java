package mchorse.bbs_mod.blocks;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.SoundType;

/**
 * Sound material of a model block. Lives in the block STATE, not the block entity, because
 * {@code getSoundType} receives only a state, no position to reach the block entity from.
 * From BBS FS 996eb0adc (Wemppy4/bbs-fs, MIT).
 */
public enum ModelBlockSound implements StringRepresentable
{
    STONE("stone", SoundType.STONE),
    WOOD("wood", SoundType.WOOD),
    METAL("metal", SoundType.METAL),
    GLASS("glass", SoundType.GLASS),
    WOOL("wool", SoundType.WOOL),
    GRASS("grass", SoundType.GRASS),
    NONE("none", SoundType.EMPTY);

    public final String id;
    public final SoundType group;

    ModelBlockSound(String id, SoundType group)
    {
        this.id = id;
        this.group = group;
    }

    public static ModelBlockSound byId(String id)
    {
        for (ModelBlockSound sound : values())
        {
            if (sound.id.equals(id))
            {
                return sound;
            }
        }

        return STONE;
    }

    /**
     * The nearest of these to a vanilla block's own sounds, told by the name of its place sound
     * ("block.cherry_wood.place" is wood): vanilla has dozens of groups, this has seven.
     */
    public static ModelBlockSound of(SoundType group)
    {
        if (group == SoundType.EMPTY)
        {
            return NONE;
        }

        String path = group.getPlaceSound().location().getPath();

        for (ModelBlockSound sound : values())
        {
            if (sound != NONE && path.contains(sound.id))
            {
                return sound;
            }
        }

        return STONE;
    }

    @Override
    public String getSerializedName()
    {
        return this.id;
    }
}
