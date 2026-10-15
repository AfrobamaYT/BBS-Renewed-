// Coded by Afrobama/Claude — /bbstools blockspawn <radius> [durationTicks]
// Quick-trigger entry for BlockSpawnAnimEffect: defines the cube around
// the local player and dispatches the animation. Used for fast iteration
// during the 1m pillager-manhunt cinematic ("set up the wall, run the
// command, roll camera, replay").
//
//   /bbstools blockspawn 8           — radius 8, default 30 t (~1.5 s)
//   /bbstools blockspawn 8 60        — radius 8, 60 t (~3 s) fall
//   /bbstools blockspawn clear       — abort any running animation
//
// Client-side (ClientCommandRegistrationCallback) because the FX itself
// is purely render-side; running it through the integrated server would
// be an unnecessary round-trip and would also break in worlds where the
// user isn't a server operator. The renderer reads from the client
// world inside trigger() so we have everything we need locally.
package com.afrobama.bbstools.client.blockspawn;

import com.mojang.brigadier.arguments.IntegerArgumentType;

import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BlockSpawnAnimCommand
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/blockspawn/cmd");

    /** Default fall duration in ticks. 30 t = 1.5 s @ 20 TPS, which lines
     *  up with the eased-drop feel — long enough to read on camera, short
     *  enough to chain into the next beat of the cinematic. */
    private static final int DEFAULT_DURATION_TICKS = 30;

    /** Radius clamps. 1 = literally just under the player's feet (rare,
     *  but useful for testing); 64 caps the captured cube at 129³ ≈
     *  2.1 M positions which would max out MAX_BLOCKS instantly. The
     *  effect itself can run with up to MAX_BLOCKS = 20k entries; the
     *  radius cap is a usability guard against typo'd /bbstools blockspawn
     *  256 freezing the client during the capture phase. */
    private static final int MIN_RADIUS = 1;
    private static final int MAX_RADIUS = 64;

    private static final int MIN_DURATION_TICKS = 1;
    private static final int MAX_DURATION_TICKS = 600;

    private BlockSpawnAnimCommand() {}

    public static void register()
    {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) ->
        {
            dispatcher.register(ClientCommands.literal("bbstools")
                .then(ClientCommands.literal("blockspawn")
                    .then(ClientCommands.literal("clear")
                        .executes(ctx -> doClear(ctx.getSource())))
                    .then(ClientCommands.argument("radius",
                            IntegerArgumentType.integer(MIN_RADIUS, MAX_RADIUS))
                        .executes(ctx -> doTrigger(
                            ctx.getSource(),
                            IntegerArgumentType.getInteger(ctx, "radius"),
                            DEFAULT_DURATION_TICKS))
                        .then(ClientCommands.argument("durationTicks",
                                IntegerArgumentType.integer(MIN_DURATION_TICKS, MAX_DURATION_TICKS))
                            .executes(ctx -> doTrigger(
                                ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "radius"),
                                IntegerArgumentType.getInteger(ctx, "durationTicks")))))));
        });

        LOG.info("Registered /bbstools blockspawn <radius> [durationTicks] | clear");
    }

    private static int doTrigger(FabricClientCommandSource src, int radius, int durationTicks)
    {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null)
        {
            src.sendFeedback(Component.literal("§c[blockspawn] no client player"));
            return 0;
        }

        BlockPos center = player.blockPosition();
        BlockPos min = center.offset(-radius, -radius, -radius);
        BlockPos max = center.offset( radius,  radius,  radius);

        BlockSpawnAnimEffect.trigger(min, max, durationTicks);

        int n = BlockSpawnAnimEffect.activeCount();
        src.sendFeedback(Component.literal(
            "§a[blockspawn] §fdropping §a" + n + "§f blocks around you (r=" + radius
            + ", duration=" + durationTicks + "t)"));
        LOG.info("/bbstools blockspawn r={} duration={}t blocks={}", radius, durationTicks, n);
        return 1;
    }

    private static int doClear(FabricClientCommandSource src)
    {
        BlockSpawnAnimEffect.clear();
        src.sendFeedback(Component.literal("§7[blockspawn] animation cleared"));
        return 1;
    }

}
