package mchorse.bbs_mod.network;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.Form;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class ServerNetwork
{
    public static final Identifier CLIENT_CLICKED_MODEL_BLOCK_PACKET = new Identifier(BBSMod.MOD_ID, "clicked_model_block");

    public static final Identifier SERVER_MODEL_BLOCK_FORM_PACKET = new Identifier(BBSMod.MOD_ID, "model_block_form");

    public static void setup()
    {
        ServerPlayNetworking.registerGlobalReceiver(SERVER_MODEL_BLOCK_FORM_PACKET, (server, player, handler, buf, responder) ->
        {
            BlockPos pos = buf.readBlockPos();
            MapType data = (MapType) DataStorageUtils.readFromPacket(buf);

            try
            {
                Form form = BBSMod.getForms().fromData(data);
                MapType transform = (MapType) DataStorageUtils.readFromPacket(buf);
                boolean shadow = buf.readBoolean();

                server.execute(() ->
                {
                    World world = player.getWorld();
                    BlockEntity be = world.getBlockEntity(pos);

                    if (be instanceof ModelBlockEntity modelBlock)
                    {
                        modelBlock.updateForm(form, transform, shadow, world);
                    }
                });
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        });
    }

    public static void sendClickedModelBlock(ServerPlayerEntity player, BlockPos pos)
    {
        PacketByteBuf buf = PacketByteBufs.create();

        buf.writeBlockPos(pos);

        ServerPlayNetworking.send(player, CLIENT_CLICKED_MODEL_BLOCK_PACKET, buf);
    }
}