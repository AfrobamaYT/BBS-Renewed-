package com.afrobama.bbstools.clips;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** Restores named vanilla structure snapshots on the owning server thread. */
public final class WorldSnapshots
{
    private WorldSnapshots() {}

    public static void restore(CommandSourceStack source, String name, BlockPos first, BlockPos second,
        boolean restoreBlockEntities, boolean diffOnly)
    {
        ServerLevel level = source.getLevel();
        if (!level.getServer().isSameThread()) throw new IllegalStateException("World snapshots require the server thread");
        if (!source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
            throw new SecurityException("World snapshots require game-master permission");
        Identifier id = name == null || name.isBlank() ? null : Identifier.tryParse(name);
        if (id == null || id.getPath().startsWith("/") || java.util.Arrays.stream(id.getPath().split("/"))
            .anyMatch(part -> part.equals(".") || part.equals("..") || part.isEmpty()))
            throw new IllegalArgumentException("Use a saved structure ID such as minecraft:scene_before");

        BlockPos min = new BlockPos(Math.min(first.getX(), second.getX()), Math.min(first.getY(), second.getY()), Math.min(first.getZ(), second.getZ()));
        BlockPos max = new BlockPos(Math.max(first.getX(), second.getX()), Math.max(first.getY(), second.getY()), Math.max(first.getZ(), second.getZ()));
        if (level.isOutsideBuildHeight(min) || level.isOutsideBuildHeight(max)
            || !level.getWorldBorder().isWithinBounds(min) || !level.getWorldBorder().isWithinBounds(max))
            throw new IllegalArgumentException("Snapshot region is outside the world's build bounds");
        if (!level.hasChunksAt(min, max)) throw new IllegalArgumentException("Load the snapshot region before playback");

        StructureTemplate template = level.getServer().getStructureTemplateManager().get(id)
            .orElseThrow(() -> new IllegalArgumentException("Saved structure snapshot not found: " + id));
        StructurePlaceSettings settings = new StructurePlaceSettings().setIgnoreEntities(true)
            .setBoundingBox(new BoundingBox(min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ()))
            .setRandom(RandomSource.create(0L)).setLiquidSettings(LiquidSettings.IGNORE_WATERLOGGING)
            .addProcessor(new WorldSnapshotProcessor(restoreBlockEntities, diffOnly));
        if (!template.placeInWorld(level, min, min, settings, RandomSource.create(0L), Block.UPDATE_ALL))
            throw new IllegalArgumentException("Saved snapshot contains no placeable blocks: " + id);
    }
}
