package com.afrobama.bbstools.gametest;

import com.afrobama.bbstools.client.blockanim.BlockAnimation;
import com.afrobama.bbstools.clip.BlockAnimationActionClip;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.MCEntity;
import mchorse.bbs_mod.resources.Link;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Real replay actions and live world rendering; inspect the recorded video before accepting motion. */
public final class BlockAnimationMotionGameTest implements FabricClientGameTest
{
    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (TestSingleplayerContext world = context.worldBuilder().create())
        {
            world.getConnection().waitForChunksRender();
            BlockPos origin = world.getServer().computeOnServer(server ->
            {
                ServerLevel level = server.overworld();
                BlockPos start = server.getPlayerList().getPlayers().getFirst().blockPosition().offset(0, 0, 5);
                level.setBlockAndUpdate(start, Blocks.GOLD_BLOCK.defaultBlockState());
                level.setBlockAndUpdate(start.east(), Blocks.GOLD_BLOCK.defaultBlockState());
                return start;
            });
            context.waitFor(client -> client.level.getBlockState(origin).is(Blocks.GOLD_BLOCK)
                && client.level.getBlockState(origin.east()).is(Blocks.GOLD_BLOCK));
            context.getInput().lookAt(origin.above());
            context.waitTicks(5);
            context.takeScreenshot("block-animation-world-before");
            List<Map<String, Object>> results = new ArrayList<>();
            try
            {
                for (String id : List.of("bbstools:block_animation", "bbs:block_animation"))
                {
                    String label = id.replace(':', '-');
                    context.runOnClient(client -> fire(id, origin, false));
                    List<Double> start = context.computeOnClient(client -> positions());
                    if (start.size() != 2) throw new AssertionError("Registered action did not capture both real blocks: " + id);
                    long began = context.computeOnClient(client -> BlockAnimation.getActiveBlocks().getFirst().startTimeNanos);
                    context.waitFor(client -> System.nanoTime() - began >= 800_000_000L);
                    List<Double> middle = context.computeOnClient(client -> positions());
                    context.takeScreenshot("block-animation-" + label + "-middle");
                    context.waitFor(client -> System.nanoTime() - began >= 2_200_000_000L);
                    List<Double> end = context.computeOnClient(client -> positions());
                    if (middle.getFirst() <= start.getFirst() || end.getFirst() != origin.getY() + 2D)
                        throw new AssertionError("Block Animation position did not advance to its configured endpoint");
                    context.takeScreenshot("block-animation-" + label + "-end");
                    context.runOnClient(client -> BlockAnimation.clear());
                    context.waitTicks(3);
                    if (!context.computeOnClient(client -> BlockAnimation.getActiveBlocks().isEmpty()))
                        throw new AssertionError("Explicit block animation clear did not release the active set");
                    context.takeScreenshot("block-animation-" + label + "-cleared");
                    results.add(Map.of("id", id, "startY", start, "middleY", middle, "endY", end,
                        "explicitClear", true));
                }
                boolean single = context.computeOnClient(client ->
                {
                    fire("bbstools:block_animation", origin, true);
                    return BlockAnimation.getActiveBlocks().size() == 1;
                });
                boolean unchanged = world.getServer().computeOnServer(server ->
                    server.overworld().getBlockState(origin).is(Blocks.GOLD_BLOCK)
                        && server.overworld().getBlockState(origin.east()).is(Blocks.GOLD_BLOCK));
                System.out.println("BBS_BLOCK_ANIMATION_MOTION " + new com.google.gson.Gson().toJson(
                    Map.of("origin", List.of(origin.getX(), origin.getY(), origin.getZ()), "cases", results,
                        "singleBlockAccepted", single, "serverBlocksUnchanged", unchanged,
                        "visualAcceptance", "requires recorded-video inspection; position data alone is insufficient")));
                if (!unchanged || !single) throw new AssertionError("Block Animation failed inclusive single-block or unchanged-world contract");
            }
            finally { context.runOnClient(client -> BlockAnimation.clear()); }
        }
    }

    private static void fire(String id, BlockPos origin, boolean single)
    {
        BlockAnimation.clear();
        BlockAnimationActionClip clip = (BlockAnimationActionClip) BBSMod.getFactoryActionClips().create(Link.create(id));
        clip.minX.set(origin.getX()); clip.maxX.set(origin.getX() + (single ? 0 : 1));
        clip.minY.set(origin.getY()); clip.maxY.set(origin.getY());
        clip.minZ.set(origin.getZ()); clip.maxZ.set(origin.getZ());
        clip.offsetX.set(0F); clip.offsetY.set(2F); clip.offsetZ.set(0F);
        clip.animDuration.set(2F);
        clip.duration.set(80);
        Replay replay = new Replay("block_motion");
        replay.actions.addClip(clip);
        System.out.println("BBS_BLOCK_ANIMATION_PHASE " + new com.google.gson.Gson().toJson(
            Map.of("id", id, "single", single, "epochMs", System.currentTimeMillis())));
        replay.applyClientActions(0, new MCEntity(Minecraft.getInstance().player), new Film());
    }

    private static List<Double> positions()
    {
        return BlockAnimation.getActiveBlocks().stream().map(block -> block.currentPos().y).toList();
    }
}
