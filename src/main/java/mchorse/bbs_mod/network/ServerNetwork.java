package mchorse.bbs_mod.network;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.morphing.Morph;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
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
    public static final Identifier CLIENT_PLAYER_FORM = new Identifier(BBSMod.MOD_ID, "client_player_form");

    public static final Identifier SERVER_MODEL_BLOCK_FORM_PACKET = new Identifier(BBSMod.MOD_ID, "server_model_block_form");
    public static final Identifier SERVER_RANDOM = new Identifier(BBSMod.MOD_ID, "server_random");

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
            {}
        });

        ServerPlayNetworking.registerGlobalReceiver(SERVER_RANDOM, (server, player, handler, buf, responder) ->
        {
            ModelForm form = new ModelForm();

            form.model.set("butterfly");

            Morph.getMorph(player).form = FormUtils.copy(form);

            sendMorphToTracked(player, form);
        });
    }

    public static void sendMorph(ServerPlayerEntity player, int playerId, Form form)
    {
        PacketByteBuf buf = PacketByteBufs.create();

        buf.writeInt(playerId);
        buf.writeBoolean(form != null);

        if (form != null)
        {
            DataStorageUtils.writeToPacket(buf, FormUtils.toData(form));
        }

        ServerPlayNetworking.send(player, CLIENT_PLAYER_FORM, buf);
    }

    public static void sendMorphToTracked(ServerPlayerEntity player, Form form)
    {
        sendMorph(player, player.getId(), form);

        for (ServerPlayerEntity otherPlayer : PlayerLookup.tracking(player))
        {
            sendMorph(otherPlayer, player.getId(), form);
        }
    }

    public static void sendClickedModelBlock(ServerPlayerEntity player, BlockPos pos)
    {
        PacketByteBuf buf = PacketByteBufs.create();

        buf.writeBlockPos(pos);

        ServerPlayNetworking.send(player, CLIENT_CLICKED_MODEL_BLOCK_PACKET, buf);
    }
}