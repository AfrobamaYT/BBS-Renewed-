// Coded by Afrobama/Claude — /bbstools deletegrass cinematic grass-clearer.
// User reported short/tall grass blocks visually distracting during the
// 1m pillager-manhunt cinematic — tufts poking into low-angle hero
// shots, animated grass-sway breaking the "stillness" before the
// charge, and (worst) flickering around ragdoll feet because PhysicsMod
// debris colliders snag on grass hitboxes. Vanilla /fill replace works
// but needs two passes (one per block id) and chokes on the 256³ cubes
// the user records. This command sweeps a single radius-cube once,
// matches both grass ids in one BlockPos.iterate pass, and reports the
// count so the user knows the area is actually clean before rolling.
//
//   /bbstools deletegrass          — clears within 64 b (default)
//   /bbstools deletegrass <r>      — clears within r b (1-256)
//
// Block IDs: in 1.21.1 Mojang renamed GRASS → SHORT_GRASS at the
// flattening (the BLOCK, not the BLOCK_TAG; tall_grass is unchanged).
// Yarn matches that rename, so Blocks.SHORT_GRASS + Blocks.TALL_GRASS
// is the correct pair. setBlockState flags=3 = NOTIFY_NEIGHBORS|NOTIFY_LISTENERS
// which is what /fill uses (avoids leaving "ghost" client copies and
// fires the proper block-update so neighboring grass that depended on
// us doesn't pop later).
//
// Architectural note: this file lives in src/client/java because all
// world-affecting commands the user can trigger mid-shoot get grouped
// near the rest of the cinematic toolchain (CinematicDistanceCommand,
// CinematicProfileCommand etc.) for easier maintenance. CommandRegistra
// tionCallback.EVENT (the non-client one) still fires on the integrated
// server inside a singleplayer client, so this is server-authoritative
// despite the package. Registration is wired from BBSToolsModClient
// because Loom's splitEnvironmentSourceSets blocks main → client refs;
// the user's spec asked for the server entry but that's not reachable
// from this sourceSet — see BBSDebug for the same pattern.
package com.afrobama.bbstools.client.level;

import com.afrobama.bbstools.BBSToolsMod;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public final class DeleteGrassCommand
{
    /** Default sweep radius when the user types just `/bbstools deletegrass`.
     *  64 b matches the typical hero-shot framing radius — large enough
     *  to cover what fits in-frame on a 16:9 70mm-equiv lens, small
     *  enough that even a 64³ cube (262 144 positions) finishes in well
     *  under one tick on any modern CPU. */
    private static final double DEFAULT_RADIUS = 64.0;

    private DeleteGrassCommand() {}

    public static void register()
    {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) ->
        {
            dispatcher.register(Commands.literal("bbstools")
                .then(Commands.literal("deletegrass")
                    .executes(ctx -> doDelete(ctx.getSource(), DEFAULT_RADIUS))
                    .then(Commands.argument("radius", DoubleArgumentType.doubleArg(1.0, 256.0))
                        .executes(ctx -> doDelete(ctx.getSource(),
                            DoubleArgumentType.getDouble(ctx, "radius"))))));
        });
    }

    private static int doDelete(CommandSourceStack src, double radius)
    {
        ServerPlayer player;
        try
        {
            player = src.getPlayerOrException();
        }
        catch (Exception e)
        {
            src.sendSuccess(() -> Component.literal(
                "§c[deletegrass] §fmust be run by a player §7(needs a position to sweep around)"), false);
            return 0;
        }

        ServerLevel world = player.level();
        Vec3 pos = player.position();
        BlockPos center = BlockPos.containing(pos);

        /* Inclusive integer-radius bounds. Math.ceil so a fractional
         *  radius (e.g. 64.5) still includes the outermost slice — the
         *  user expects "within 64 blocks" to mean ≤64, not <64. */
        int r = (int) Math.ceil(radius);
        BlockPos min = center.offset(-r, -r, -r);
        BlockPos max = center.offset( r,  r,  r);

        double rSq = radius * radius;
        int deleted = 0;
        int scanned = 0;

        long t0 = System.nanoTime();

        /* BlockPos.iterate returns a reused mutable iterator-style
         *  BlockPos; we MUST .toImmutable() before calling setBlockState
         *  because Minecraft caches the position reference internally in
         *  the chunk's pending-update list. Without toImmutable the
         *  cached entry mutates with us and later updates target the
         *  wrong column (subtle, only manifests at large radii). */
        for (BlockPos p : BlockPos.betweenClosed(min, max))
        {
            scanned++;

            /* Spherical mask inside the iterated cube — feels more
             *  natural than a square cull for the user ("clear around
             *  me to N blocks"). Cheap (squared-distance, no sqrt). */
            double dx = p.getX() + 0.5 - pos.x;
            double dy = p.getY() + 0.5 - pos.y;
            double dz = p.getZ() + 0.5 - pos.z;
            if (dx * dx + dy * dy + dz * dz > rSq)
            {
                continue;
            }

            var state = world.getBlockState(p);
            var block = state.getBlock();
            if (block == Blocks.SHORT_GRASS || block == Blocks.TALL_GRASS)
            {
                /* Flag 3 = NOTIFY_LISTENERS (1) | NOTIFY_NEIGHBORS (2),
                 *  matches /fill's default. Skip flag 16 (NO_REDRAW) so
                 *  the chunk re-meshes immediately and the user sees the
                 *  cleared area without re-logging. */
                world.setBlock(p.immutable(), Blocks.AIR.defaultBlockState(), 3);
                deleted++;
            }
        }

        long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;

        /* Two-line feedback: headline count for the user mid-shoot, then
         *  a detail line with scan count + ms so we can spot if a future
         *  radius bump starts to chug. Logged too — content creators
         *  rarely think to grep the log themselves but it makes
         *  post-session debugging much easier. */
        final int deletedFinal = deleted;
        final int scannedFinal = scanned;
        final long elapsedFinal = elapsedMs;
        final int rFinal = r;

        src.sendSuccess(() -> Component.literal(
            "§a[deletegrass] §fcleared §a" + deletedFinal
            + "§f grass blocks within §a" + rFinal + "§f b"), true);
        src.sendSuccess(() -> Component.literal(
            "§7  scanned " + scannedFinal + " positions in " + elapsedFinal + " ms"), false);

        BBSToolsMod.LOG.info("/bbstools deletegrass r={} deleted={} scanned={} elapsed={}ms",
            rFinal, deletedFinal, scannedFinal, elapsedFinal);

        return Command.SINGLE_SUCCESS;
    }

}
