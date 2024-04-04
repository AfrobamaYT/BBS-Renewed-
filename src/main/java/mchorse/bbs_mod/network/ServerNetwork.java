package mchorse.bbs_mod.network;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.morphing.Morph;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class ServerNetwork
{
    public static final Identifier CLIENT_CLICKED_MODEL_BLOCK_PACKET = new Identifier(BBSMod.MOD_ID, "clicked_model_block");
    public static final Identifier CLIENT_PLAYER_FORM_PACKET = new Identifier(BBSMod.MOD_ID, "client_player_form");
    public static final Identifier CLIENT_PLAY_FILM_PACKET = new Identifier(BBSMod.MOD_ID, "client_play_film");

    public static final Identifier SERVER_MODEL_BLOCK_FORM_PACKET = new Identifier(BBSMod.MOD_ID, "server_model_block_form");
    public static final Identifier SERVER_PLAYER_FORM_PACKET = new Identifier(BBSMod.MOD_ID, "server_player_form");

    public static void setup()
    {
        ServerPlayNetworking.registerGlobalReceiver(SERVER_MODEL_BLOCK_FORM_PACKET, (server, player, handler, buf, responder) -> handleModelBlockFormPacket(server, player, buf));
        ServerPlayNetworking.registerGlobalReceiver(SERVER_PLAYER_FORM_PACKET, (server, player, handler, buf, responder) -> handlePlayerFormPacket(server, player, buf));
    }

    /* Handlers */

    private static void handleModelBlockFormPacket(MinecraftServer server, ServerPlayerEntity player, PacketByteBuf buf)
    {
        BlockPos pos = buf.readBlockPos();

        try
        {
            MapType data = (MapType) DataStorageUtils.readFromPacket(buf);

            server.execute(() ->
            {
                World world = player.getWorld();
                BlockEntity be = world.getBlockEntity(pos);

                if (be instanceof ModelBlockEntity modelBlock)
                {
                    modelBlock.updateForm(data, world);
                }
            });
        }
        catch (Exception e)
        {}
    }

    private static void handlePlayerFormPacket(MinecraftServer server, ServerPlayerEntity player, PacketByteBuf buf)
    {
        MapType data = (MapType) DataStorageUtils.readFromPacket(buf);
        Form form = null;

        try
        {
            form = BBSMod.getForms().fromData(data);
        }
        catch (Exception e)
        {}

        final Form finalForm = form;

        server.execute(() ->
        {
            Morph.getMorph(player).form = FormUtils.copy(finalForm);

            sendMorphToTracked(player, finalForm);
        });
    }

    /* API */

    public static void sendMorph(ServerPlayerEntity player, int playerId, Form form)
    {
        PacketByteBuf buf = PacketByteBufs.create();

        buf.writeInt(playerId);
        buf.writeBoolean(form != null);

        if (form != null)
        {
            DataStorageUtils.writeToPacket(buf, FormUtils.toData(form));
        }

        ServerPlayNetworking.send(player, CLIENT_PLAYER_FORM_PACKET, buf);
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

    public static void sendPlayFilm(ServerPlayerEntity player, String filmId, boolean withCamera)
    {
        PacketByteBuf buf = PacketByteBufs.create();

        buf.writeString(filmId);
        buf.writeBoolean(withCamera);

        ServerPlayNetworking.send(player, CLIENT_PLAY_FILM_PACKET, buf);
    }
}