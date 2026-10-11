package com.afrobama.bbstools.clips;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/** Native structure capture/placement, scoped to the film's server world and inclusive region. */
public final class WorldSnapshots
{
    public static void execute(ServerLevel world, WorldSnapshotClip clip) throws IOException
    {
        BlockPos min = new BlockPos(Math.min(clip.minX.get(), clip.maxX.get()),
            Math.min(clip.minY.get(), clip.maxY.get()), Math.min(clip.minZ.get(), clip.maxZ.get()));
        BlockPos max = new BlockPos(Math.max(clip.minX.get(), clip.maxX.get()),
            Math.max(clip.minY.get(), clip.maxY.get()), Math.max(clip.minZ.get(), clip.maxZ.get()));
        Vec3i size = new Vec3i(max.getX() - min.getX() + 1, max.getY() - min.getY() + 1, max.getZ() - min.getZ() + 1);
        if ((long) size.getX() * size.getY() * size.getZ() > 1048576L)
        {
            throw new IllegalArgumentException("Region exceeds 1048576 blocks");
        }
        for (BlockPos position : BlockPos.betweenClosed(min, max))
        {
            if (!world.isInWorldBounds(position) || !world.hasChunkAt(position))
            {
                throw new IllegalArgumentException("Region must be inside loaded world bounds");
            }
        }
        Path base = world.getServer().getWorldPath(LevelResource.ROOT).resolve("bbstools/snapshots").toAbsolutePath().normalize();
        String name = clip.snapshotFile.get();
        if (name.isBlank())
        {
            throw new IllegalArgumentException("Snapshot file is empty");
        }
        Path file = base.resolve(name.endsWith(".nbt") ? name : name + ".nbt").normalize();
        if (!file.startsWith(base) || file.equals(base))
        {
            throw new IllegalArgumentException("Snapshot file must be inside bbstools/snapshots");
        }
        StructureTemplate template = new StructureTemplate();
        if (clip.capture.get())
        {
            template.fillFromWorld(world, min, size, false, List.of());
            Files.createDirectories(file.getParent());
            Path temporary = Files.createTempFile(file.getParent(), "snapshot-", ".nbt");
            try
            {
                NbtIo.writeCompressed(template.save(new CompoundTag()), temporary);
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }
            finally
            {
                Files.deleteIfExists(temporary);
            }
            return;
        }
        template.load(world.registryAccess().lookupOrThrow(Registries.BLOCK), NbtIo.readCompressed(file, NbtAccounter.create(67108864L)));
        if (!template.getSize().equals(size))
        {
            throw new IllegalArgumentException("Snapshot size " + template.getSize() + " differs from region " + size);
        }
        StructurePlaceSettings settings = new StructurePlaceSettings().setIgnoreEntities(true);
        settings.addProcessor(new StructureProcessor()
        {
            @Override
            public StructureTemplate.StructureBlockInfo processBlock(LevelReader level, BlockPos origin,
                BlockPos pivot, BlockPos relative, StructureTemplate.StructureBlockInfo info, StructurePlaceSettings placement)
            {
                if (clip.diffOnly.get() && level.getBlockState(info.pos()).equals(info.state()))
                {
                    if (!clip.restoreBlockEntities.get() || info.nbt() == null)
                    {
                        return null;
                    }
                    BlockEntity entity = level.getBlockEntity(info.pos());
                    CompoundTag expected = info.nbt().copy();
                    expected.putInt("x", info.pos().getX());
                    expected.putInt("y", info.pos().getY());
                    expected.putInt("z", info.pos().getZ());
                    if (entity != null && entity.saveWithFullMetadata(world.registryAccess()).equals(expected))
                    {
                        return null;
                    }
                }
                return clip.restoreBlockEntities.get() ? info : new StructureTemplate.StructureBlockInfo(info.pos(), info.state(), null);
            }

            @Override
            public MapCodec<? extends StructureProcessor> codec()
            {
                return MapCodec.unit(this);
            }
        });
        template.placeInWorld(world, min, min, settings, world.getRandom(), Block.UPDATE_ALL);
    }

    private WorldSnapshots()
    {}
}
