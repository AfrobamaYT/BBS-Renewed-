package mchorse.bbs_mod.actions;

import org.jetbrains.annotations.Nullable;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

public class SuperFakePlayerNetworkHandler extends ServerGamePacketListenerImpl
{
    private static final Connection FAKE_CONNECTION = new FakeClientConnection();

    public SuperFakePlayerNetworkHandler(ServerPlayer player)
    {
        super(player.level().getServer(), FAKE_CONNECTION, player, CommonListenerCookie.createInitial(player.getGameProfile(), false));
    }

    @Override
    //? if >=1.21.6 {
    public void send(Packet<?> packet, @Nullable io.netty.channel.ChannelFutureListener callbacks)
    //?} else {
    /*public void send(Packet<?> packet, @Nullable net.minecraft.network.PacketSendListener callbacks)
    *///?}
    {}

    private static final class FakeClientConnection extends Connection
    {
        private FakeClientConnection()
        {
            super(PacketFlow.CLIENTBOUND);
        }

        public void setPacketListener(PacketListener packetListener)
        {}
    }
}