package mchorse.bbs_mod.compat.netz;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;

/** vor1205: Gegenstueck zu NetzServer auf der Client-Seite (Empfang auf dem Client-Thread). */
public final class NetzClient
{
    public record Kontext(Minecraft client, PacketSender responseSender)
    {}

    @FunctionalInterface
    public interface Empfaenger<T extends Nutzlast>
    {
        void receive(T payload, Kontext context);
    }

    private NetzClient()
    {}

    public static <T extends Nutzlast> boolean empfangen(Nutzlast.Type<T> typ, Empfaenger<T> empfaenger)
    {
        return ClientPlayNetworking.registerGlobalReceiver(typ.id(), (client, handler, buf, sender) ->
        {
            T nutzlast = PayloadTypeRegistry.clientboundPlay().<T>codec(typ.id()).decode(new RegistryFriendlyByteBuf(buf));
            Kontext kontext = new Kontext(client, sender);

            client.execute(() -> empfaenger.receive(nutzlast, kontext));
        });
    }

    public static void senden(Nutzlast nutzlast)
    {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer());

        PayloadTypeRegistry.serverboundPlay().codec(nutzlast.type().id()).encode(buf, nutzlast);
        ClientPlayNetworking.send(nutzlast.type().id(), buf);
    }
}
