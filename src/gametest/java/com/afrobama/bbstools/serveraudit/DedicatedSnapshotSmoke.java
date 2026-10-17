package com.afrobama.bbstools.serveraudit;

import com.afrobama.bbstools.clips.WorldSnapshotClip;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.entity.ActorEntity;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.resources.Link;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/** A real dedicated-server boot and saved-structure action; never shipped in the product. */
public final class DedicatedSnapshotSmoke implements ModInitializer
{
    @Override
    public void onInitialize()
    {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> server.execute(() -> verify(server)));
    }

    private static void verify(MinecraftServer server)
    {
        Path receipt = Path.of(System.getProperty("bbs.ci.serverReceipt"));
        try
        {
            FabricLoader loader = FabricLoader.getInstance();
            if (!server.isDedicatedServer() || loader.getEnvironmentType() != EnvType.SERVER
                || loader.isDevelopmentEnvironment() || loader.isModLoaded("bbsdev")
                || DedicatedSnapshotSmoke.class.getClassLoader().getResource("net/minecraft/client/Minecraft.class") != null)
                throw new AssertionError("Expected an actual production dedicated server without client classes or companion");
            Path product = Path.of(BBSMod.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(product)));
            ServerLevel level = server.overworld();
            level.getChunk(0, 0);
            BlockPos origin = new BlockPos(0, 64, 0);
            level.setBlockAndUpdate(origin, Blocks.GOLD_BLOCK.defaultBlockState());
            Identifier id = Identifier.fromNamespaceAndPath("bbstools", "dedicated_snapshot_smoke");
            StructureTemplate template = server.getStructureTemplateManager().getOrCreate(id);
            template.fillFromWorld(level, origin, new Vec3i(1, 1, 1), false, List.of());
            if (!server.getStructureTemplateManager().save(id)) throw new AssertionError("Cannot save dedicated-server snapshot");
            server.getStructureTemplateManager().remove(id);
            level.setBlockAndUpdate(origin, Blocks.STONE.defaultBlockState());
            WorldSnapshotClip clip = (WorldSnapshotClip) BBSMod.getFactoryActionClips().create(Link.create("bbstools:world_snapshot"));
            clip.snapshotFile.set(id.toString());
            clip.minX.set(0); clip.maxX.set(0);
            clip.minY.set(64); clip.maxY.set(64);
            clip.minZ.set(0); clip.maxZ.set(0);
            Replay replay = new Replay("dedicated_snapshot");
            replay.actions.addClip(clip);
            replay.applyActions(new ActorEntity(BBSMod.ACTOR_ENTITY, level), null, new Film(), 0);
            if (!level.getBlockState(origin).is(Blocks.GOLD_BLOCK)) throw new AssertionError("Dedicated replay action did not restore the block");
            String result = new com.google.gson.Gson().toJson(Map.of("success", true, "dedicated", true,
                "clientClassesAbsent", true, "companionAbsent", true, "loadedProductSha256", hash,
                "diskSnapshotAndRegisteredReplayAction", true));
            Files.createDirectories(receipt.getParent());
            Files.writeString(receipt, result);
            System.out.println("BBS_DEDICATED_SNAPSHOT " + result);
        }
        catch (Throwable failure)
        {
            failure.printStackTrace();
            try { Files.createDirectories(receipt.getParent()); Files.writeString(receipt,
                new com.google.gson.Gson().toJson(Map.of("success", false, "error", failure.toString()))); }
            catch (Exception writeFailure) { writeFailure.printStackTrace(); }
        }
        finally { server.halt(false); }
    }
}
