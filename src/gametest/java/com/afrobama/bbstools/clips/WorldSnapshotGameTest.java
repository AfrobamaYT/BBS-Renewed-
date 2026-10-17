package com.afrobama.bbstools.clips;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.entity.ActorEntity;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.CommandSourceUtils;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.List;
import java.util.Map;

/** Real server blocks, vanilla disk snapshots and the registered action entry point. */
public final class WorldSnapshotGameTest implements FabricClientGameTest
{
    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (TestSingleplayerContext world = context.worldBuilder().create())
        {
            world.getConnection().waitForChunksRender();
            Map<String, Object> result = world.getServer().computeOnServer(server ->
            {
                ServerLevel level = server.overworld();
                BlockPos origin = new BlockPos(12, -59, 12), chestPos = origin.east();
                level.setBlockAndUpdate(origin, Blocks.GOLD_BLOCK.defaultBlockState());
                level.setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());
                ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(chestPos);
                chest.setItem(0, new ItemStack(Items.DIAMOND, 4));
                Identifier id = Identifier.fromNamespaceAndPath("bbstools", "ci_snapshot");
                StructureTemplate template = server.getStructureTemplateManager().getOrCreate(id);
                template.fillFromWorld(level, origin, new Vec3i(2, 1, 1), false, List.of());
                if (!server.getStructureTemplateManager().save(id)) throw new AssertionError("Cannot save vanilla snapshot");
                server.getStructureTemplateManager().remove(id);
                template = server.getStructureTemplateManager().get(id).orElseThrow();
                level.setBlockAndUpdate(origin, Blocks.STONE.defaultBlockState());
                chest.setItem(0, new ItemStack(Items.EMERALD, 2));

                WorldSnapshotClip clip = (WorldSnapshotClip) BBSMod.getFactoryActionClips().create(Link.create("bbstools:world_snapshot"));
                if (clip.isClient()) throw new AssertionError("Snapshot restore must use the server action path");
                clip.snapshotFile.set(id.toString());
                clip.minX.set(chestPos.getX()); clip.maxX.set(origin.getX());
                clip.minY.set(origin.getY()); clip.maxY.set(origin.getY());
                clip.minZ.set(origin.getZ()); clip.maxZ.set(origin.getZ());
                ActorEntity actor = new ActorEntity(BBSMod.ACTOR_ENTITY, level);
                Replay replay = new Replay("snapshot");
                replay.actions.addClip(clip);
                replay.applyActions(actor, null, new Film(), 0);
                if (!level.getBlockState(origin).is(Blocks.GOLD_BLOCK)) throw new AssertionError("Registered clip did not restore blocks");
                chest = (ChestBlockEntity) level.getBlockEntity(chestPos);
                if (!chest.getItem(0).is(Items.EMERALD) || chest.getItem(0).getCount() != 2)
                    throw new AssertionError("Disabled block-entity restore changed current contents");

                clip.restoreBlockEntities.set(true);
                replay.applyActions(actor, null, new Film(), 0);
                chest = (ChestBlockEntity) level.getBlockEntity(chestPos);
                if (!chest.getItem(0).is(Items.DIAMOND) || chest.getItem(0).getCount() != 4)
                    throw new AssertionError("Captured chest contents were not restored");
                WorldSnapshots.restore(CommandSourceUtils.of(actor, level), id.toString(), origin, chestPos, true, true);
                level.setBlockAndUpdate(origin, Blocks.STONE.defaultBlockState());
                chest.setItem(0, new ItemStack(Items.EMERALD, 7));
                clip.diffOnly.set(false);
                clip.restoreBlockEntities.set(false);
                replay.applyActions(actor, null, new Film(), 0);
                chest = (ChestBlockEntity) level.getBlockEntity(chestPos);
                if (!level.getBlockState(origin).is(Blocks.GOLD_BLOCK) || !chest.getItem(0).is(Items.EMERALD)
                    || chest.getItem(0).getCount() != 7) throw new AssertionError("Full placement did not preserve disabled block-entity data");
                clip.restoreBlockEntities.set(true);
                replay.applyActions(actor, null, new Film(), 0);
                chest = (ChestBlockEntity) level.getBlockEntity(chestPos);
                if (!chest.getItem(0).is(Items.DIAMOND) || chest.getItem(0).getCount() != 4)
                    throw new AssertionError("Full placement did not restore block-entity data");
                clip.diffOnly.set(true);
                StructureTemplate.StructureBlockInfo unchanged = new StructureTemplate.StructureBlockInfo(origin,
                    Blocks.GOLD_BLOCK.defaultBlockState(), null);
                if (new WorldSnapshotProcessor(true, true).processBlock(level, origin, origin, BlockPos.ZERO,
                    unchanged, new StructurePlaceSettings()) != null)
                    throw new AssertionError("diff_only did not skip an unchanged block");
                if (new WorldSnapshotProcessor(true, false).processBlock(level, origin, origin, BlockPos.ZERO,
                    unchanged, new StructurePlaceSettings()) == null)
                    throw new AssertionError("Disabled diff_only still skips placement");

                Identifier single = Identifier.fromNamespaceAndPath("bbstools", "ci_snapshot_single");
                StructureTemplate one = server.getStructureTemplateManager().getOrCreate(single);
                one.fillFromWorld(level, origin, new Vec3i(1, 1, 1), false, List.of());
                server.getStructureTemplateManager().save(single);
                level.setBlockAndUpdate(origin, Blocks.STONE.defaultBlockState());
                clip.snapshotFile.set(single.toString()); clip.minX.set(origin.getX());
                replay.applyActions(actor, null, new Film(), 0);
                if (!level.getBlockState(origin).is(Blocks.GOLD_BLOCK)) throw new AssertionError("Inclusive one-block region failed");

                level.setBlockAndUpdate(origin, Blocks.STONE.defaultBlockState());
                net.minecraft.commands.CommandSourceStack source = CommandSourceUtils.of(actor, level);
                reject(() -> WorldSnapshots.restore(source, "bbstools:../outside", origin, origin, true, true));
                reject(() -> WorldSnapshots.restore(source, "bbstools:missing_ci_snapshot", origin, origin, true, true));
                reject(() -> WorldSnapshots.restore(source.withPermission(PermissionSet.NO_PERMISSIONS), single.toString(), origin, origin, true, true));
                if (!level.getBlockState(origin).is(Blocks.STONE)) throw new AssertionError("Rejected restore changed the world");
                return Map.of("diskRoundTrip", true, "registeredServerAction", true, "reversedCorners", true,
                    "singleBlock", true, "restoreBlockEntitiesBothModes", true, "diffOnlyBothModes", true,
                    "invalidMissingUnauthorizedRejected", true);
            });
            System.out.println("BBS_WORLD_SNAPSHOT_CONTRACT " + new com.google.gson.Gson().toJson(result));
        }
    }

    private static void reject(Runnable operation)
    {
        try { operation.run(); }
        catch (IllegalArgumentException | SecurityException expected) { return; }
        throw new AssertionError("Expected snapshot request rejection");
    }
}
