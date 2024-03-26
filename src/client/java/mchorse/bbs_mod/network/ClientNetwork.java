package mchorse.bbs_mod.network;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.morphing.Morph;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.model_blocks.UIModelBlockPanel;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public class ClientNetwork
{
    public static void setup()
    {
        ClientPlayNetworking.registerGlobalReceiver(ServerNetwork.CLIENT_CLICKED_MODEL_BLOCK_PACKET, (client, handler, buf, responseSender) ->
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
        });

        ClientPlayNetworking.registerGlobalReceiver(ServerNetwork.CLIENT_PLAYER_FORM, (client, handler, buf, responseSender) ->
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

                if (entity instanceof PlayerEntity)
                {
                    Morph morph = Morph.getMorph((PlayerEntity) entity);

                    morph.form = finalForm;
                }
            });
        });
    }

    public static void sendModelBlockForm(BlockPos pos, ModelBlockEntity modelBlock)
    {
        PacketByteBuf buf = PacketByteBufs.create();
        MapType mapType = FormUtils.toData(modelBlock.getForm());

        buf.writeBlockPos(pos);
        DataStorageUtils.writeToPacket(buf, mapType == null ? new MapType() : mapType);
        DataStorageUtils.writeToPacket(buf, modelBlock.getTransform().toData());
        buf.writeBoolean(modelBlock.getShadow());

        ClientPlayNetworking.send(ServerNetwork.SERVER_MODEL_BLOCK_FORM_PACKET, buf);
    }

    public static void sendRandom()
    {
        ClientPlayNetworking.send(ServerNetwork.SERVER_RANDOM, PacketByteBufs.empty());
    }

    public static void sendPlayerForm(Form form)
    {
        PacketByteBuf buf = PacketByteBufs.create();
        MapType mapType = FormUtils.toData(form);

        DataStorageUtils.writeToPacket(buf, mapType == null ? new MapType() : mapType);

        ClientPlayNetworking.send(ServerNetwork.SERVER_PLAYER_FORM, buf);
    }
}