package com.afrobama.bbstools.clips;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;

/** Transient placement policy; structure capture and placement remain vanilla. */
final class WorldSnapshotProcessor implements StructureProcessor
{
    private static final MapCodec<WorldSnapshotProcessor> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        Codec.BOOL.fieldOf("restore_block_entities").forGetter(value -> value.restoreBlockEntities),
        Codec.BOOL.fieldOf("diff_only").forGetter(value -> value.diffOnly)
    ).apply(instance, WorldSnapshotProcessor::new));

    private final boolean restoreBlockEntities;
    private final boolean diffOnly;

    WorldSnapshotProcessor(boolean restoreBlockEntities, boolean diffOnly)
    {
        this.restoreBlockEntities = restoreBlockEntities;
        this.diffOnly = diffOnly;
    }

    @Override
    public StructureBlockInfo processBlock(LevelReader level, BlockPos origin, BlockPos pivot, BlockPos relative,
        StructureBlockInfo block, StructurePlaceSettings settings)
    {
        if (this.diffOnly && level.getBlockState(block.pos()).equals(block.state()))
        {
            if (!this.restoreBlockEntities || block.nbt() == null) return null;
            BlockEntity current = level.getBlockEntity(block.pos());
            if (current != null && comparable(current.saveWithoutMetadata(level.registryAccess())).equals(comparable(block.nbt())))
                return null;
        }
        return this.restoreBlockEntities ? block : new StructureBlockInfo(block.pos(), block.state(), null);
    }

    private static CompoundTag comparable(CompoundTag original)
    {
        CompoundTag copy = original.copy();
        for (String key : java.util.List.of("x", "y", "z", "id")) copy.remove(key);
        return copy;
    }

    @Override
    public MapCodec<? extends StructureProcessor> codec()
    {
        return CODEC;
    }
}
