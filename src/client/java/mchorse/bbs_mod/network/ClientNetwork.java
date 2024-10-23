package mchorse.bbs_mod.network;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSResources;
import mchorse.bbs_mod.actions.ActionState;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.Films;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.forms.triggers.StateTrigger;
import mchorse.bbs_mod.morphing.Morph;
import mchorse.bbs_mod.resources.ISourcePack;
import mchorse.bbs_mod.resources.cache.CacheAssetsSourcePack;
import mchorse.bbs_mod.resources.cache.ResourceCache;
import mchorse.bbs_mod.resources.cache.ResourceEntry;
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class ClientNetwork
{
    private static int ids = 0;
    private static Map<Integer, Consumer<BaseType>> callbacks = new HashMap<>();

    private static String serverId;
    private static boolean isBBSModOnServer;

    public static void resetHandshake()
    {
        serverId = "";
        isBBSModOnServer = false;
    }

    public static boolean isIsBBSModOnServer()
    {
        return isBBSModOnServer;
    }

    public static String getServerId()
    {
        return serverId;
    }

    /* Network */

    public static void setup()
    {
        ClientPlayNetworking.registerGlobalReceiver(ServerNetwork.CLIENT_CLICKED_MODEL_BLOCK_PACKET, (client, handler, buf, responseSender) -> handleClientModelBlockPacket(client, buf));
        ClientPlayNetworking.registerGlobalReceiver(ServerNetwork.CLIENT_PLAYER_FORM_PACKET, (client, handler, buf, responseSender) -> handlePlayerFormPacket(client, buf));
        ClientPlayNetworking.registerGlobalReceiver(ServerNetwork.CLIENT_PLAY_FILM_PACKET, (client, handler, buf, responseSender) -> handlePlayFilmPacket(client, buf));
        ClientPlayNetworking.registerGlobalReceiver(ServerNetwork.CLIENT_MANAGER_DATA_PACKET, (client, handler, buf, responseSender) -> handleManagerDataPacket(client, buf));
        ClientPlayNetworking.registerGlobalReceiver(ServerNetwork.CLIENT_STOP_FILM_PACKET, (client, handler, buf, responseSender) -> handleStopFilmPacket(client, buf));
        ClientPlayNetworking.registerGlobalReceiver(ServerNetwork.CLIENT_HANDSHAKE, (client, handler, buf, responseSender) -> handleHandshakePacket(client, buf));
        ClientPlayNetworking.registerGlobalReceiver(ServerNetwork.CLIENT_RECORDED_ACTIONS, (client, handler, buf, responseSender) -> handleRecordedActionsPacket(client, buf));
        ClientPlayNetworking.registerGlobalReceiver(ServerNetwork.CLIENT_FORM_TRIGGER, (client, handler, buf, responseSender) -> handleFormTriggerPacket(client, buf));
        ClientPlayNetworking.registerGlobalReceiver(ServerNetwork.CLIENT_ASSET, (client, handler, buf, responseSender) -> handleAssetPacket(client, buf));
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

    private static void handleManagerDataPacket(MinecraftClient client, PacketByteBuf buf)
    {
        int callbackId = buf.readInt();
        RepositoryOperation op = RepositoryOperation.values()[buf.readInt()];
        BaseType data = DataStorageUtils.readFromPacket(buf);

        MinecraftClient.getInstance().execute(() ->
        {
            Consumer<BaseType> callback = callbacks.remove(callbackId);

            if (callback != null)
            {
                callback.accept(data);
            }
        });
    }

    private static void handleStopFilmPacket(MinecraftClient client, PacketByteBuf buf)
    {
        String filmId = buf.readString();

        client.execute(() -> Films.stopFilm(filmId));
    }

    private static void handleHandshakePacket(MinecraftClient client, PacketByteBuf buf)
    {
        serverId = buf.readString();
        isBBSModOnServer = true;

        if (!serverId.isEmpty())
        {
            List<ResourceEntry> assets = new ArrayList<>();
            int c = buf.readInt();

            for (int i = 0; i < c; i++)
            {
                String path = buf.readString();
                long l = buf.readLong();

                assets.add(new ResourceEntry(path, l));
            }

            BBSResources.setup(client, serverId, new ResourceCache(assets));
        }
    }

    private static void handleRecordedActionsPacket(MinecraftClient client, PacketByteBuf buf)
    {
        String filmId = buf.readString();
        int replayId = buf.readInt();
        BaseType data = DataStorageUtils.readFromPacket(buf);

        client.execute(() ->
        {
            BBSModClient.getDashboard().getPanels().getPanel(UIFilmPanel.class).receiveActions(filmId, replayId, data);
        });
    }

    private static void handleFormTriggerPacket(MinecraftClient client, PacketByteBuf buf)
    {
        int id = buf.readInt();
        String triggerId = buf.readString();

        client.execute(() ->
        {
            Entity entity = client.world.getEntityById(id);
            Morph morph = Morph.getMorph(entity);

            if (morph.getForm() instanceof ModelForm modelForm)
            {
                for (StateTrigger trigger : modelForm.triggers.triggers)
                {
                    if (trigger.id.equals(triggerId))
                    {
                        ((ModelFormRenderer) FormUtilsClient.getRenderer(modelForm)).triggerState(trigger);

                        return;
                    }
                }
            }
        });
    }

    private static void handleAssetPacket(MinecraftClient client, PacketByteBuf buf)
    {
        String path = buf.readString();
        int index = buf.readInt();
        int total = buf.readInt();
        int size = buf.readInt();
        byte[] bytes = new byte[size];

        buf.readBytes(bytes);

        ISourcePack sourcePack = BBSMod.getDynamicSourcePack().getSourcePack();

        if (sourcePack instanceof CacheAssetsSourcePack pack)
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
                client.execute(() -> sendRequestAsset(path, index + 1));
            }
        }
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

    public static void sendTeleport(double x, double y, double z)
    {
        PacketByteBuf buf = PacketByteBufs.create();

        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);

        ClientPlayNetworking.send(ServerNetwork.SERVER_PLAYER_TP, buf);
    }

    public static void sendFormTrigger(String triggerId)
    {
        PacketByteBuf buf = PacketByteBufs.create();

        buf.writeString(triggerId);

        ClientPlayNetworking.send(ServerNetwork.SERVER_FORM_TRIGGER, buf);
    }

    public static void sendRequestAsset(String asset, int index)
    {
        PacketByteBuf buf = PacketByteBufs.create();

        buf.writeString(asset);
        buf.writeInt(index);

        ClientPlayNetworking.send(ServerNetwork.SERVER_REQUEST_ASSET, buf);
    }
}