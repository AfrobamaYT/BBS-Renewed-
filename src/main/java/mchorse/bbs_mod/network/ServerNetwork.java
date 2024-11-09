package mchorse.bbs_mod.network;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.actions.ActionManager;
import mchorse.bbs_mod.actions.ActionPlayer;
import mchorse.bbs_mod.actions.ActionState;
import mchorse.bbs_mod.actions.types.FormTriggerActionClip;
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
import mchorse.bbs_mod.resources.ISourcePack;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.resources.packs.ExternalAssetsSourcePack;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.EnumUtils;
import mchorse.bbs_mod.utils.IOUtils;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.utils.repos.RepositoryOperation;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class ServerNetwork
{
    public static final List<String> EXTENSIONS = Arrays.asList("wav", "json", "vox", "png", "ogg");

    public static final Identifier CLIENT_CLICKED_MODEL_BLOCK_PACKET = new Identifier(BBSMod.MOD_ID, "c1");
    public static final Identifier CLIENT_PLAYER_FORM_PACKET = new Identifier(BBSMod.MOD_ID, "c2");
    public static final Identifier CLIENT_PLAY_FILM_PACKET = new Identifier(BBSMod.MOD_ID, "c3");
    public static final Identifier CLIENT_MANAGER_DATA_PACKET = new Identifier(BBSMod.MOD_ID, "c4");
    public static final Identifier CLIENT_STOP_FILM_PACKET = new Identifier(BBSMod.MOD_ID, "c5");
    public static final Identifier CLIENT_HANDSHAKE = new Identifier(BBSMod.MOD_ID, "c6");
    public static final Identifier CLIENT_RECORDED_ACTIONS = new Identifier(BBSMod.MOD_ID, "c7");
    public static final Identifier CLIENT_FORM_TRIGGER = new Identifier(BBSMod.MOD_ID, "c8");
    public static final Identifier CLIENT_ASSET = new Identifier(BBSMod.MOD_ID, "c9");
    public static final Identifier CLIENT_REQUEST_ASSET = new Identifier(BBSMod.MOD_ID, "c10");

    public static final Identifier SERVER_MODEL_BLOCK_FORM_PACKET = new Identifier(BBSMod.MOD_ID, "s1");
    public static final Identifier SERVER_MODEL_BLOCK_TRANSFORMS_PACKET = new Identifier(BBSMod.MOD_ID, "s2");
    public static final Identifier SERVER_PLAYER_FORM_PACKET = new Identifier(BBSMod.MOD_ID, "s3");
    public static final Identifier SERVER_MANAGER_DATA_PACKET = new Identifier(BBSMod.MOD_ID, "s4");
    public static final Identifier SERVER_ACTION_RECORDING = new Identifier(BBSMod.MOD_ID, "s5");
    public static final Identifier SERVER_TOGGLE_FILM = new Identifier(BBSMod.MOD_ID, "s6");
    public static final Identifier SERVER_ACTION_CONTROL = new Identifier(BBSMod.MOD_ID, "s7");
    public static final Identifier SERVER_ACTIONS_UPLOAD = new Identifier(BBSMod.MOD_ID, "s8");
    public static final Identifier SERVER_PLAYER_TP = new Identifier(BBSMod.MOD_ID, "s9");
    public static final Identifier SERVER_FORM_TRIGGER = new Identifier(BBSMod.MOD_ID, "s10");
    public static final Identifier SERVER_REQUEST_ASSET = new Identifier(BBSMod.MOD_ID, "s11");
    public static final Identifier SERVER_ASSET = new Identifier(BBSMod.MOD_ID, "s12");

    public static void setup()
    {
        ServerPlayNetworking.registerGlobalReceiver(SERVER_MODEL_BLOCK_FORM_PACKET, (server, player, handler, buf, responder) -> handleModelBlockFormPacket(server, player, buf));
        ServerPlayNetworking.registerGlobalReceiver(SERVER_MODEL_BLOCK_TRANSFORMS_PACKET, (server, player, handler, buf, responder) -> handleModelBlockTransformsPacket(server, player, buf));
        ServerPlayNetworking.registerGlobalReceiver(SERVER_PLAYER_FORM_PACKET, (server, player, handler, buf, responder) -> handlePlayerFormPacket(server, player, buf));
        ServerPlayNetworking.registerGlobalReceiver(SERVER_MANAGER_DATA_PACKET, (server, player, handler, buf, responder) -> handleManagerDataPacket(server, player, buf));
        ServerPlayNetworking.registerGlobalReceiver(SERVER_ACTION_RECORDING, (server, player, handler, buf, responder) -> handleActionRecording(server, player, buf));
        ServerPlayNetworking.registerGlobalReceiver(SERVER_TOGGLE_FILM, (server, player, handler, buf, responder) -> handleToggleFilm(server, player, buf));
        ServerPlayNetworking.registerGlobalReceiver(SERVER_ACTION_CONTROL, (server, player, handler, buf, responder) -> handleActionControl(server, player, buf));
        ServerPlayNetworking.registerGlobalReceiver(SERVER_ACTIONS_UPLOAD, (server, player, handler, buf, responder) -> handleActionsUpload(server, player, buf));
        ServerPlayNetworking.registerGlobalReceiver(SERVER_PLAYER_TP, (server, player, handler, buf, responder) -> handleTeleportPlayer(server, player, buf));
        ServerPlayNetworking.registerGlobalReceiver(SERVER_FORM_TRIGGER, (server, player, handler, buf, responder) -> handleFormTrigger(server, player, buf));
        ServerPlayNetworking.registerGlobalReceiver(SERVER_REQUEST_ASSET, (server, player, handler, buf, responder) -> handleRequestAssets(server, player, buf));
        ServerPlayNetworking.registerGlobalReceiver(SERVER_ASSET, (server, player, handler, buf, responder) -> handleAssetPacket(server, player, buf));
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
            Morph.getMorph(player).setForm(FormUtils.copy(finalForm));

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
            ListType list = DataStorageUtils.stringListToData(films.getKeys());

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

    private static void handleActionRecording(MinecraftServer server, ServerPlayerEntity player, PacketByteBuf buf)
    {
        String filmId = buf.readString();
        int replayId = buf.readInt();
        int tick = buf.readInt();
        boolean recording = buf.readBoolean();

        server.execute(() ->
        {
            if (recording)
            {
                Film film = BBSMod.getFilms().load(filmId);

                if (film != null)
                {
                    BBSMod.getActions().startRecording(film, player, tick);
                    BBSMod.getActions().play(player.getServerWorld(), film, tick, replayId);
                }
            }
            else
            {
                Clips clips = BBSMod.getActions().stopRecording(player);

                /* Save clips to the film */
                Film film = BBSMod.getFilms().load(filmId);

                if (clips != null && film != null && CollectionUtils.inRange(film.replays.getList(), replayId))
                {
                    film.replays.getList().get(replayId).actions.fromData(clips.toData());
                    BBSMod.getFilms().save(filmId, film.toData().asMap());
                }

                /* Send recorded clips to the client */
                sendRecordedActions(player, filmId, replayId, clips);
            }
        });
    }

    private static void handleToggleFilm(MinecraftServer server, ServerPlayerEntity player, PacketByteBuf buf)
    {
        String filmId = buf.readString();
        boolean withCamera = buf.readBoolean();

        server.execute(() ->
        {
            ActionPlayer actionPlayer = BBSMod.getActions().getPlayer(filmId);

            if (actionPlayer != null)
            {
                BBSMod.getActions().stop(filmId);

                for (ServerPlayerEntity otherPlayer : server.getPlayerManager().getPlayerList())
                {
                    sendStopFilm(otherPlayer, filmId);
                }
            }
            else
            {
                sendPlayFilm(player.getServerWorld(), filmId, withCamera);
            }
        });
    }

    private static void handleActionControl(MinecraftServer server, ServerPlayerEntity player, PacketByteBuf buf)
    {
        ActionManager actions = BBSMod.getActions();
        String filmId = buf.readString();
        ActionState state = EnumUtils.getValue(buf.readByte(), ActionState.values(), ActionState.STOP);
        int tick = buf.readInt();

        server.execute(() ->
        {
            if (state == ActionState.SEEK)
            {
                ActionPlayer actionPlayer = actions.getPlayer(filmId);

                if (actionPlayer != null)
                {
                    actionPlayer.goTo(tick);
                }
            }
            else if (state == ActionState.PLAY)
            {
                ActionPlayer actionPlayer = actions.getPlayer(filmId);

                if (actionPlayer != null)
                {
                    actionPlayer.goTo(tick);
                    actionPlayer.playing = true;
                }
            }
            else if (state == ActionState.PAUSE)
            {
                ActionPlayer actionPlayer = actions.getPlayer(filmId);

                if (actionPlayer != null)
                {
                    actionPlayer.goTo(tick);
                    actionPlayer.playing = false;
                }
            }
            else if (state == ActionState.RESTART)
            {
                ActionPlayer actionPlayer = actions.getPlayer(filmId);

                if (actionPlayer == null)
                {
                    Film film = BBSMod.getFilms().load(filmId);

                    if (film != null)
                    {
                        actionPlayer = actions.play(player.getServerWorld(), film, tick);
                    }
                }
                else
                {
                    actions.stop(filmId);

                    actionPlayer = actions.play(player.getServerWorld(), actionPlayer.film, 0);
                }

                if (actionPlayer != null)
                {
                    actionPlayer.syncing = true;
                    actionPlayer.playing = false;

                    if (tick != 0)
                    {
                        actionPlayer.goTo(tick);
                    }
                }

                sendStopFilm(player, filmId);
            }
            else if (state == ActionState.STOP)
            {
                actions.stop(filmId);
            }
        });
    }

    private static void handleActionsUpload(MinecraftServer server, ServerPlayerEntity player, PacketByteBuf buf)
    {
        String filmId = buf.readString();
        int replayId = buf.readInt();
        BaseType data = DataStorageUtils.readFromPacket(buf);

        server.execute(() ->
        {
            BBSMod.getActions().updatePlayers(filmId, replayId, data);
        });
    }

    private static void handleTeleportPlayer(MinecraftServer server, ServerPlayerEntity player, PacketByteBuf buf)
    {
        double x = buf.readDouble();
        double y = buf.readDouble();
        double z = buf.readDouble();
        float yaw = buf.readFloat();
        float pitch = buf.readFloat();

        server.execute(() ->
        {
            boolean wow = player.teleport(x, y, z, false);

            player.setYaw(yaw);
            player.setHeadYaw(yaw);
            player.setBodyYaw(yaw);
            player.setPitch(pitch);
        });
    }

    private static void handleFormTrigger(MinecraftServer server, ServerPlayerEntity player, PacketByteBuf buf)
    {
        String string = buf.readString();
        PacketByteBuf newBuf = PacketByteBufs.create();

        newBuf.writeInt(player.getId());
        newBuf.writeString(string);

        for (ServerPlayerEntity otherPlayer : PlayerLookup.tracking(player))
        {
            ServerPlayNetworking.send(otherPlayer, CLIENT_FORM_TRIGGER, newBuf);
        }

        server.execute(() ->
        {
            BBSMod.getActions().addAction(player, () ->
            {
                FormTriggerActionClip action = new FormTriggerActionClip();

                action.trigger.set(string);

                return action;
            });
        });
    }

    private static void handleRequestAssets(MinecraftServer server, ServerPlayerEntity player, PacketByteBuf buf)
    {
        String path = buf.readString();
        Link link = Link.assets(path);
        int index = buf.readInt();

        sendAsset(player, link, index);
    }

    private static void handleAssetPacket(MinecraftServer server, ServerPlayerEntity player, PacketByteBuf buf)
    {
        if (!BBSSettings.serverAssetManager.get().equals(player.getUuidAsString()))
        {
            player.sendMessage(Text.literal("You don't have permission to upload files"), true);

            return;
        }

        String path = buf.readString();
        int index = buf.readInt();

        /* If index is -1, we gotta delete the file */
        if (index == -1)
        {
            ISourcePack sourcePack = BBSMod.getDynamicSourcePack().getSourcePack();

            if (sourcePack instanceof ExternalAssetsSourcePack pack)
            {
                File file = new File(pack.getFolder(), path);

                if (file.exists())
                {
                    file.delete();
                }
            }

            return;
        }

        int total = buf.readInt();
        int size = buf.readInt();
        byte[] bytes = new byte[size];

        buf.readBytes(bytes);

        ISourcePack sourcePack = BBSMod.getDynamicSourcePack().getSourcePack();

        if (sourcePack instanceof ExternalAssetsSourcePack pack)
        {
            File file = new File(pack.getFolder(), path);

            file.getParentFile().mkdirs();

            try (OutputStream stream = new FileOutputStream(file, index != 0))
            {
                stream.write(bytes);
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }

            if (index != total - 1)
            {
                server.execute(() -> sendRequestAsset(player, path, index + 1));
            }
            else
            {
                System.out.println("[Server] Received completely: " + path);
            }

            BBSMod.getResourceTracker().timer.mark();
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

    public static void sendPlayFilm(ServerWorld world, String filmId, boolean withCamera)
    {
        try
        {
            Film film = BBSMod.getFilms().load(filmId);

            if (film != null)
            {
                BBSMod.getActions().play(world, film, 0);

                PacketByteBuf newBuf = PacketByteBufs.create();

                newBuf.writeString(filmId);
                newBuf.writeBoolean(withCamera);
                DataStorageUtils.writeToPacket(newBuf, film.toData());

                for (ServerPlayerEntity otherPlayer : world.getPlayers())
                {
                    ServerPlayNetworking.send(otherPlayer, CLIENT_PLAY_FILM_PACKET, newBuf);
                }
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public static void sendPlayFilm(ServerPlayerEntity player, String filmId, boolean withCamera)
    {
        try
        {
            Film film = BBSMod.getFilms().load(filmId);

            if (film != null)
            {
                BBSMod.getActions().play(player.getServerWorld(), film, 0);

                PacketByteBuf buf = PacketByteBufs.create();

                buf.writeString(filmId);
                buf.writeBoolean(withCamera);
                DataStorageUtils.writeToPacket(buf, film.toData());

                ServerPlayNetworking.send(player, CLIENT_PLAY_FILM_PACKET, buf);
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
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

    public static void sendRecordedActions(ServerPlayerEntity player, String filmId, int replayId, Clips clips)
    {
        PacketByteBuf newBuf = PacketByteBufs.create();

        newBuf.writeString(filmId);
        newBuf.writeInt(replayId);
        DataStorageUtils.writeToPacket(newBuf, clips.toData());

        ServerPlayNetworking.send(player, CLIENT_RECORDED_ACTIONS, newBuf);
    }

    public static void sendHandshake(MinecraftServer server, PacketSender packetSender)
    {
        packetSender.sendPacket(ServerNetwork.CLIENT_HANDSHAKE, createHandshakeBuf(server));
    }

    public static void sendHandshake(MinecraftServer server, ServerPlayerEntity player)
    {
        ServerPlayNetworking.send(player, ServerNetwork.CLIENT_HANDSHAKE, createHandshakeBuf(server));
    }

    private static PacketByteBuf createHandshakeBuf(MinecraftServer server)
    {
        PacketByteBuf buf = PacketByteBufs.create();
        String id = BBSSettings.serverId.get().trim();

        /* No need to do that in singleplayer */
        if (server.isSingleplayer())
        {
            id = "";
        }

        buf.writeString(id);
        buf.writeBoolean(BBSSettings.unlimitedPacketSize.get());

        if (!id.isEmpty())
        {
            Collection<Link> links = BBSMod.getProvider().getLinksFromPath(Link.assets(""));
            List<Pair<String, Long>> assets = new ArrayList<>();

            for (Link link : links)
            {
                String extension = StringUtils.extension(link.path);

                if (!extension.equals(link.path) && EXTENSIONS.contains(extension.toLowerCase()) && !link.path.startsWith("audio/elevenlabs/"))
                {
                    File file = BBSMod.getProvider().getFile(link);
                    long l = file.lastModified();

                    assets.add(new Pair<>(link.path, l));
                }
            }

            buf.writeInt(assets.size());

            for (Pair<String, Long> asset : assets)
            {
                buf.writeString(asset.a);
                buf.writeLong(asset.b);
            }
        }

        return buf;
    }

    public static void sendAsset(ServerPlayerEntity player, Link link, int index)
    {
        try
        {
            InputStream stream = BBSMod.getOriginalSourcePack().getAsset(link);
            byte[] bytes = IOUtils.readBytes(stream);

            int placeholder = 1000;
            int bufferSize = (BBSSettings.unlimitedPacketSize.get() ? 1048576 : 32767) - placeholder;
            int total = (int) Math.ceil(bytes.length / (float) bufferSize);
            int offset = index * bufferSize;

            PacketByteBuf buf = PacketByteBufs.create();
            int size = Math.min(bufferSize, bytes.length - offset);

            buf.writeString(link.path);
            buf.writeInt(index);
            buf.writeInt(total);
            buf.writeInt(size);
            buf.writeBytes(bytes, offset, size);

            ServerPlayNetworking.send(player, CLIENT_ASSET, buf);
        }
        catch (IOException e)
        {
            System.err.println("Failed to read asset: " + link);
        }
    }

    public static void sendRequestAsset(ServerPlayerEntity player, String asset, int index)
    {
        PacketByteBuf buf = PacketByteBufs.create();

        buf.writeString(asset);
        buf.writeInt(index);

        ServerPlayNetworking.send(player, ServerNetwork.CLIENT_REQUEST_ASSET, buf);
    }
}