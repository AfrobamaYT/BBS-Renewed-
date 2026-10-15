package mchorse.bbs_mod.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

public class ClientPacketCrusher extends PacketCrusher
{
    @Override
    protected void sendBuffer(Player entity, ResourceLocation identifier, FriendlyByteBuf buf)
    {
        mchorse.bbs_mod.compat.netz.NetzClient.senden(ServerNetwork.BufPayload.from(buf, ServerNetwork.idFor(identifier)));
    }
}