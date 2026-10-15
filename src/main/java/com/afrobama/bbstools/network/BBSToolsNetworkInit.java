// Coded by Afrobama/Claude — central registration for BBSTools custom
// payloads + server-side handlers. Call init() exactly once from the
// ModInitializer (main entrypoint) so the codec is registered before
// any world is loaded.
package com.afrobama.bbstools.network;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.server.permissions.Permissions;

public final class BBSToolsNetworkInit
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/network");

    /** Permission level required to apply blocks remotely. Same as the
     *  /buildschematic command itself (op-level 2). */
    private static final int REQUIRED_OP_LEVEL = 2;

    /** Max squared distance the player may be from the target block.
     *  256² = 256 b ≈ 16 chunks; bigger than any reasonable build. */
    private static final double MAX_DISTANCE_SQ = 256.0D * 256.0D;

    private BBSToolsNetworkInit() {}

    public static void init()
    {
        PayloadTypeRegistry.playC2S().register(BuilderActionPacket.ID, BuilderActionPacket.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(BuilderActionPacket.ID,
            (payload, context) ->
        {
            /* Master Debugger Module 13a: log RECV on the integrated
             * server side. Reflective so main-set stays free of
             * client-set imports (dedicated server has no NetTrace). */
            notifyNetTraceRecv(BuilderActionPacket.CHANNEL.toString(),
                /* approx size = 16B header + state-string length */
                18L + (payload.blockState() == null ? 0 : payload.blockState().length()));

            ServerPlayer player = context.player();
            if (!player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
            {
                LOG.warn("BuilderActionPacket from non-op player {} dropped", player.getName().getString());
                return;
            }
            BlockPos pos = new BlockPos(payload.blockX(), payload.blockY(), payload.blockZ());
            double dsq = player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
            if (dsq > MAX_DISTANCE_SQ)
            {
                LOG.warn("BuilderActionPacket from {} rejected: dist² = {} > {}",
                    player.getName().getString(), dsq, MAX_DISTANCE_SQ);
                return;
            }
            Level world = player.level();
            if (payload.action() == BuilderActionPacket.ACTION_BREAK)
            {
                world.removeBlock(pos, false);
                return;
            }
            BlockState state;
            try
            {
                BlockStateParser.BlockResult res = BlockStateParser.parseForBlock(
                    BuiltInRegistries.BLOCK, payload.blockState(), false);
                state = res.blockState();
            }
            catch (CommandSyntaxException e)
            {
                LOG.warn("Bad blockstate '{}' from {} — dropped", payload.blockState(), player.getName().getString());
                return;
            }
            world.setBlock(pos, state, 3);
        });

        LOG.info("BBSTools network init: BuilderActionPacket channel = {}", BuilderActionPacket.CHANNEL);
    }

    /* Reflection-cached method handle for the client-set NetTrace.
     * Resolved lazily on first call; nulled out on any failure so
     * dedicated-server runs (no client classes) skip the trace
     * silently. Module 13a is opt-in via /bbsdebug feature net-trace,
     * so a null on the integrated-server side is fine — the SEND
     * line on the client still logs. */
    private static volatile boolean netTraceProbed = false;
    private static volatile java.lang.reflect.Method netTraceRecvMethod = null;

    private static void notifyNetTraceRecv(String channel, long sizeBytes)
    {
        java.lang.reflect.Method m = netTraceRecvMethod;
        if (m == null)
        {
            if (netTraceProbed) return;
            try
            {
                Class<?> cls = Class.forName(
                    "com.afrobama.bbstools.client.debug.master.net.NetTrace",
                    false, BBSToolsNetworkInit.class.getClassLoader());
                m = cls.getMethod("recordRecv", String.class, long.class);
                netTraceRecvMethod = m;
            }
            catch (Throwable t)
            {
                /* Dedicated server or class load failed — disable forever. */
            }
            netTraceProbed = true;
            if (m == null) return;
        }
        try
        {
            m.invoke(null, channel, sizeBytes);
        }
        catch (Throwable t)
        {
            LOG.warn("[net-trace] reflective recordRecv failed: {}", t.toString());
            netTraceRecvMethod = null;
        }
    }

}
