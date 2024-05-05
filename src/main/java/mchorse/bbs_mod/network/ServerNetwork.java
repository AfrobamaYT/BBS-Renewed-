package mchorse.bbs_mod.network;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ByteType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.FilmManager;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.morphing.Morph;
import mchorse.bbs_mod.utils.repos.RepositoryOperation;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class ServerNetwork
{
    public static final Identifier CLIENT_CLICKED_MODEL_BLOCK_PACKET = new Identifier(BBSMod.MOD_ID, "c1");
    public static final Identifier CLIENT_PLAYER_FORM_PACKET = new Identifier(BBSMod.MOD_ID, "c2");
    public static final Identifier CLIENT_PLAY_FILM_PACKET = new Identifier(BBSMod.MOD_ID, "c3");
    public static final Identifier CLIENT_MANAGER_DATA_PACKET = new Identifier(BBSMod.MOD_ID, "c4");
    public static final Identifier CLIENT_STOP_FILM_PACKET = new Identifier(BBSMod.MOD_ID, "c5");

    public static final Identifier SERVER_MODEL_BLOCK_FORM_PACKET = new Identifier(BBSMod.MOD_ID, "s1");
    public static final Identifier SERVER_MODEL_BLOCK_TRANSFORMS_PACKET = new Identifier(BBSMod.MOD_ID, "s2");
    public static final Identifier SERVER_PLAYER_FORM_PACKET = new Identifier(BBSMod.MOD_ID, "s3");
    public static final Identifier SERVER_MANAGER_DATA_PACKET = new Identifier(BBSMod.MOD_ID, "s4");

    public static void setup()
    {
        ServerPlayNetworking.registerGlobalReceiver(SERVER_MODEL_BLOCK_FORM_PACKET, (server, player, handler, buf, responder) -> handleModelBlockFormPacket(server, player, buf));
        ServerPlayNetworking.registerGlobalReceiver(SERVER_MODEL_BLOCK_TRANSFORMS_PACKET, (server, player, handler, buf, responder) -> handleModelBlockTransformsPacket(server, player, buf));
        ServerPlayNetworking.registerGlobalReceiver(SERVER_PLAYER_FORM_PACKET, (server, player, handler, buf, responder) -> handlePlayerFormPacket(server, player, buf));
        ServerPlayNetworking.registerGlobalReceiver(SERVER_MANAGER_DATA_PACKET, (server, player, handler, buf, responder) -> handleManagerDataPacket(server, player, buf));
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

    private static void handleModelBlockTransformsPacket(MinecraftServer server, ServerPlayerEntity player, PacketByteBuf buf)
    {
        try
        {
            MapType data = (MapType) DataStorageUtils.readFromPacket(buf);

            server.execute(() ->
            {
                ItemStack stack = player.getEquippedStack(EquipmentSlot.MAINHAND).copy();

                stack.getNbt().getCompound("BlockEntityTag").put("Properties", DataStorageUtils.toNbt(data));

                player.equipStack(EquipmentSlot.MAINHAND, stack);
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

    private static void handleManagerDataPacket(MinecraftServer server, ServerPlayerEntity player, PacketByteBuf buf)
    {
        int callbackId = buf.readInt();
        RepositoryOperation op = RepositoryOperation.values()[buf.readInt()];
        MapType data = (MapType) DataStorageUtils.readFromPacket(buf);
        FilmManager films = BBSMod.getFilms();

        if (op == RepositoryOperation.LOAD)
        {
            String id = data.getString("id");
            Film film = films.load(id);

            sendManagerData(player, callbackId, op, film.toData());
        }
        else if (op == RepositoryOperation.SAVE)
        {
            films.save(data.getString("id"), data.getMap("data"));
        }
        else if (op == RepositoryOperation.RENAME)
        {
            films.rename(data.getString("from"), data.getString("to"));
        }
        else if (op == RepositoryOperation.DELETE)
        {
            films.delete(data.getString("id"));
        }
        else if (op == RepositoryOperation.KEYS)
        {
            ListType list = new ListType();

            for (String key : films.getKeys())
            {
                list.addString(key);
            }

            sendManagerData(player, callbackId, op, list);
        }
        else if (op == RepositoryOperation.ADD_FOLDER)
        {
            sendManagerData(player, callbackId, op, new ByteType(films.addFolder(data.getString("folder"))));
        }
        else if (op == RepositoryOperation.RENAME_FOLDER)
        {
            sendManagerData(player, callbackId, op, new ByteType(films.renameFolder(data.getString("from"), data.getString("to"))));
        }
        else if (op == RepositoryOperation.DELETE_FOLDER)
        {
            sendManagerData(player, callbackId, op, new ByteType(films.deleteFolder(data.getString("folder"))));
        }
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

    public static void sendStopFilm(ServerPlayerEntity player, String filmId)
    {
        PacketByteBuf buf = PacketByteBufs.create();

        buf.writeString(filmId);

        ServerPlayNetworking.send(player, CLIENT_STOP_FILM_PACKET, buf);
    }

    public static void sendManagerData(ServerPlayerEntity player, int callbackId, RepositoryOperation op, BaseType data)
    {
        PacketByteBuf buf = PacketByteBufs.create();

        buf.writeInt(callbackId);
        buf.writeInt(op.ordinal());
        DataStorageUtils.writeToPacket(buf, data);

        ServerPlayNetworking.send(player, CLIENT_MANAGER_DATA_PACKET, buf);
    }
}