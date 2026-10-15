// Coded by Afrobama/Claude — Block Animation command.
// /bbsanim clear — stops + clears any active block animation.
// (The old `capture` subcommand was removed 2026-05-31: it relied on the TAB-hold
//  BlockSelection region, which is deleted. Block animations now come from the BBS
//  Block Animation Clip, which calls BlockAnimation.captureAndAnimateRegion with
//  explicit coords.)
package com.afrobama.bbstools.client.blockanim;

import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.network.chat.Component;

public final class BlockAnimationCommands
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/blockanim/cmd");

    private BlockAnimationCommands() {}

    public static void register()
    {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) ->
        {
            dispatcher.register(ClientCommands.literal("bbsanim")
                .then(ClientCommands.literal("clear")
                    .executes(ctx ->
                    {
                        BlockAnimation.clear();
                        ctx.getSource().sendFeedback(Component.literal("§7[BBSAnim] Cleared block animation"));
                        return 1;
                    })));
        });

        LOG.info("Registered /bbsanim command (clear)");
    }

}
