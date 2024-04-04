package mchorse.bbs_mod.network;

import mchorse.bbs_mod.BBSData;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.camera.controller.PlayCameraController;
import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.morphing.Morph;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.model_blocks.UIModelBlockPanel;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public class ClientNetwork
{
    public static void setup()
    {
        ClientPlayNetworking.registerGlobalReceiver(ServerNetwork.CLIENT_CLICKED_MODEL_BLOCK_PACKET, (client, handler, buf, responseSender) -> handleClientModelBlockPacket(client, buf));
        ClientPlayNetworking.registerGlobalReceiver(ServerNetwork.CLIENT_PLAYER_FORM_PACKET, (client, handler, buf, responseSender) -> handlePlayerFormPacket(client, buf));
        ClientPlayNetworking.registerGlobalReceiver(ServerNetwork.CLIENT_PLAY_FILM_PACKET, (client, handler, buf, responseSender) -> handlePlayFilmPacket(buf));
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

            UIDashboard dashboard = BBSModClient.getDashboard();

            if (!(client.currentScreen instanceof UIScreen) || ((UIScreen) client.currentScreen).getMenu() != dashboard)
            {
                client.setScreen(new UIScreen(Text.empty(), dashboard));
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
                morph.form = finalForm;
            }
        });
    }

    private static void handlePlayFilmPacket(PacketByteBuf buf)
    {
        String filmId = buf.readString();
        boolean withCamera = buf.readBoolean();

        try
        {
            Film film = BBSData.getFilms().load(filmId);

            if (withCamera)
            {
                BBSModClient.getCameraController().add(new PlayCameraController(film.camera));
            }

            BBSModClient.getFilms().addFilm(film);
        }
        catch (Exception e)
        {}
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
}