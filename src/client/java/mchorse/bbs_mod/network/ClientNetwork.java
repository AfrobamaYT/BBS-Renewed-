package mchorse.bbs_mod.network;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.actions.ActionState;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.Films;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.morphing.Morph;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.model_blocks.UIModelBlockPanel;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.utils.repos.RepositoryOperation;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class ClientNetwork
{
    private static int ids = 0;
    private static Map<Integer, Consumer<BaseType>> callbacks = new HashMap<>();
    private static boolean isBBSModOnServer;

    public static void resetHandshake()
    {
        isBBSModOnServer = false;
    }

    public static boolean isIsBBSModOnServer()
    {
        return isBBSModOnServer;
    }

    /* Network */

    public static void setup()
    {
        ClientPlayNetworking.registerGlobalReceiver(ServerNetwork.CLIENT_CLICKED_MODEL_BLOCK_PACKET, (client, handler, buf, responseSender) -> handleClientModelBlockPacket(client, buf));
        ClientPlayNetworking.registerGlobalReceiver(ServerNetwork.CLIENT_PLAYER_FORM_PACKET, (client, handler, buf, responseSender) -> handlePlayerFormPacket(client, buf));
        ClientPlayNetworking.registerGlobalReceiver(ServerNetwork.CLIENT_PLAY_FILM_PACKET, (client, handler, buf, responseSender) -> handlePlayFilmPacket(client, buf));
        ClientPlayNetworking.registerGlobalReceiver(ServerNetwork.CLIENT_MANAGER_DATA_PACKET, (client, handler, buf, responseSender) -> handleManagerDataPacket(buf));
        ClientPlayNetworking.registerGlobalReceiver(ServerNetwork.CLIENT_STOP_FILM_PACKET, (client, handler, buf, responseSender) -> handleStopFilmPacket(buf));
        ClientPlayNetworking.registerGlobalReceiver(ServerNetwork.CLIENT_HANDSHAKE, (client, handler, buf, responseSender) -> isBBSModOnServer = true);
        ClientPlayNetworking.registerGlobalReceiver(ServerNetwork.CLIENT_RECORDED_ACTIONS, (client, handler, buf, responseSender) -> handleRecordedActionsPacket(buf));
    }

    /* Handlers */

    private static void handleClientModelBlockPacket(MinecraftClient client, PacketByteBuf buf)
    {
        BlockPos pos = buf.readBlockPos();

        client.execute(() ->
        {
            BlockEntity entity = client.world.getBlockEntity(pos);

            if (!(entity instanceof ModelBlockEntity))
            {
                return;
            }

            UIBaseMenu menu = UIScreen.getCurrentMenu();
            UIDashboard dashboard = BBSModClient.getDashboard();

            if (menu != dashboard)
            {
                UIScreen.open(dashboard);
            }

            UIModelBlockPanel panel = dashboard.getPanels().getPanel(UIModelBlockPanel.class);

            dashboard.setPanel(panel);
            panel.fill((ModelBlockEntity) entity, true);
        });
    }

    private static void handlePlayerFormPacket(MinecraftClient client, PacketByteBuf buf)
    {
        int id = buf.readInt();
        Form form = null;

        if (buf.readBoolean())
        {
            form = FormUtils.fromData((MapType) DataStorageUtils.readFromPacket(buf));
        }

        final Form finalForm = form;

        client.execute(() ->
        {
            Entity entity = client.world.getEntityById(id);
            Morph morph = Morph.getMorph(entity);

            System.out.println(entity + " " + finalForm);

            if (morph != null)
            {
                morph.setForm(finalForm);
            }
        });
    }

    private static void handlePlayFilmPacket(MinecraftClient client, PacketByteBuf buf)
    {
        String filmId = buf.readString();
        boolean withCamera = buf.readBoolean();
        Film film = new Film();

        film.setId(filmId);
        film.fromData(DataStorageUtils.readFromPacket(buf));

        client.execute(() ->
        {
            Films.playFilm(film, withCamera);
        });
    }

    private static void handleManagerDataPacket(PacketByteBuf buf)
    {
        int callbackId = buf.readInt();
        RepositoryOperation op = RepositoryOperation.values()[buf.readInt()];
        BaseType data = DataStorageUtils.readFromPacket(buf);

        Consumer<BaseType> callback = callbacks.remove(callbackId);

        if (callback != null)
        {
            callback.accept(data);
        }
    }

    private static void handleStopFilmPacket(PacketByteBuf buf)
    {
        String filmId = buf.readString();

        MinecraftClient.getInstance().execute(() ->
        {
            Films.stopFilm(filmId);
        });
    }

    private static void handleRecordedActionsPacket(PacketByteBuf buf)
    {
        String filmId = buf.readString();
        int replayId = buf.readInt();
        BaseType data = DataStorageUtils.readFromPacket(buf);

        MinecraftClient.getInstance().execute(() ->
        {
            BBSModClient.getDashboard().getPanels().getPanel(UIFilmPanel.class).receiveActions(filmId, replayId, data);
        });
    }

    /* API */
    
    public static void sendModelBlockForm(BlockPos pos, ModelBlockEntity modelBlock)
    {
        PacketByteBuf buf = PacketByteBufs.create();

        buf.writeBlockPos(pos);
        DataStorageUtils.writeToPacket(buf, modelBlock.getProperties().toData());

        ClientPlayNetworking.send(ServerNetwork.SERVER_MODEL_BLOCK_FORM_PACKET, buf);
    }

    public static void sendPlayerForm(Form form)
    {
        PacketByteBuf buf = PacketByteBufs.create();
        MapType mapType = FormUtils.toData(form);

        DataStorageUtils.writeToPacket(buf, mapType == null ? new MapType() : mapType);

        ClientPlayNetworking.send(ServerNetwork.SERVER_PLAYER_FORM_PACKET, buf);
    }

    public static void sendModelBlockTransforms(MapType data)
    {
        PacketByteBuf buf = PacketByteBufs.create();

        DataStorageUtils.writeToPacket(buf, data);

        ClientPlayNetworking.send(ServerNetwork.SERVER_MODEL_BLOCK_TRANSFORMS_PACKET, buf);
    }

    public static void sendManagerDataLoad(String id, Consumer<BaseType> consumer)
    {
        MapType mapType = new MapType();

        mapType.putString("id", id);
        ClientNetwork.sendManagerData(RepositoryOperation.LOAD, mapType, consumer);
    }

    public static void sendManagerData(RepositoryOperation op, BaseType data, Consumer<BaseType> consumer)
    {
        int id = ids;

        callbacks.put(id, consumer);
        sendManagerData(id, op, data);

        ids += 1;
    }

    public static void sendManagerData(int callbackId, RepositoryOperation op, BaseType data)
    {
        PacketByteBuf buf = PacketByteBufs.create();

        buf.writeInt(callbackId);
        buf.writeInt(op.ordinal());
        DataStorageUtils.writeToPacket(buf, data);

        ClientPlayNetworking.send(ServerNetwork.SERVER_MANAGER_DATA_PACKET, buf);
    }

    public static void sendActionRecording(String filmId, int replayId, int tick, boolean state)
    {
        PacketByteBuf buf = PacketByteBufs.create();

        buf.writeString(filmId);
        buf.writeInt(replayId);
        buf.writeInt(tick);
        buf.writeBoolean(state);

        ClientPlayNetworking.send(ServerNetwork.SERVER_ACTION_RECORDING, buf);
    }

    public static void sendToggleFilm(String filmId, boolean withCamera)
    {
        PacketByteBuf buf = PacketByteBufs.create();

        buf.writeString(filmId);
        buf.writeBoolean(withCamera);

        ClientPlayNetworking.send(ServerNetwork.SERVER_TOGGLE_FILM, buf);
    }

    public static void sendActionState(String filmId, ActionState state, int tick)
    {
        PacketByteBuf buf = PacketByteBufs.create();

        buf.writeString(filmId);
        buf.writeByte(state.ordinal());
        buf.writeInt(tick);

        ClientPlayNetworking.send(ServerNetwork.SERVER_ACTION_CONTROL, buf);
    }

    public static void sendActions(String filmId, int replayId, Clips actions)
    {
        PacketByteBuf buf = PacketByteBufs.create();

        buf.writeString(filmId);
        buf.writeInt(replayId);
        DataStorageUtils.writeToPacket(buf, actions.toData());

        ClientPlayNetworking.send(ServerNetwork.SERVER_ACTIONS_UPLOAD, buf);
    }

    public static void sendTeleport(int x, int y, int z)
    {
        PacketByteBuf buf = PacketByteBufs.create();

        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);

        ClientPlayNetworking.send(ServerNetwork.SERVER_PLAYER_TP, buf);
    }
}