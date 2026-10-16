package mchorse.bbs_mod.client;

import io.netty.buffer.Unpooled;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.blocks.ModelBlockUmwandlung;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.network.ServerNetwork;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;

/**
 * The client's half of Alt + right click with a model block: the click on a block is taken before
 * the item would place a new model block next to it, and the server is asked to swap the block
 * (see {@link ModelBlockUmwandlung}). While Alt is held every such click is taken, even one that
 * converts nothing: poking along a row, a miss must not drop a stray model block or open the
 * dashboard on a block that already is one. From BBS FS b4ba56e23 (Wemppy4/bbs-fs, MIT); FS takes
 * the click in a mixin on doItemUse, here Fabric's block use event does.
 */
public final class ModelBlockUmwandlungClient
{
    private ModelBlockUmwandlungClient()
    {}

    public static void register()
    {
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) ->
        {
            if (stack.is(BBSMod.MODEL_BLOCK_ITEM))
            {
                lines.add(Component.translatable("block.bbs.model.tooltip.convert").withStyle(ChatFormatting.GRAY));
            }
        });

        UseBlockCallback.EVENT.register((player, world, hand, hit) ->
        {
            /* The integrated server fires this event too; only the client's click counts */
            if (!world.isClientSide() || player != Minecraft.getInstance().player || !player.isCreative()
                || !player.getItemInHand(hand).is(BBSMod.MODEL_BLOCK_ITEM) || !Window.isAltPressed())
            {
                return InteractionResult.PASS;
            }

            if (ModelBlockUmwandlung.canConvert(world.getBlockState(hit.getBlockPos())))
            {
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());

                buf.writeBlockPos(hit.getBlockPos());
                ClientPlayNetworking.send(ServerNetwork.BufPayload.from(buf, ServerNetwork.idFor(ModelBlockUmwandlung.PAKET)));
                player.swing(hand, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
            }

            /* FAIL: nothing is placed and nothing goes to the server as a use */
            return InteractionResult.FAIL;
        });
    }
}
