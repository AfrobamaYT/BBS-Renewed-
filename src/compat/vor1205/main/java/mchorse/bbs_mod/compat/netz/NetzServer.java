package mchorse.bbs_mod.compat.netz;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * vor1205: 1.20.5s typisierte Fabric-Netzwerk-API auf der alten Kanal-API von 1.20.4.
 * Kodiert wird mit den Codecs aus PayloadTypeRegistry. Die alte API ruft Empfaenger auf dem
 * Netzwerk-Thread auf; die neue (die BBS erwartet) auf dem Server-Thread -- darum wird hier
 * erst dekodiert (der Puffer gehoert nur dem Aufruf) und dann an den Server uebergeben.
 */
public final class NetzServer
{
    public record Kontext(MinecraftServer server, ServerPlayer player, PacketSender responseSender)
    {}

    @FunctionalInterface
    public interface Empfaenger<T extends Nutzlast>
    {
        void receive(T payload, Kontext context);
    }

    private NetzServer()
    {}

    public static <T extends Nutzlast> boolean empfangen(Nutzlast.Type<T> typ, Empfaenger<T> empfaenger)
    {
        return ServerPlayNetworking.registerGlobalReceiver(typ.id(), (server, player, handler, buf, sender) ->
        {
            T nutzlast = PayloadTypeRegistry.serverboundPlay().<T>codec(typ.id()).decode(new RegistryFriendlyByteBuf(buf));
            Kontext kontext = new Kontext(server, player, sender);

            server.execute(() -> empfaenger.receive(nutzlast, kontext));
        });
    }

    public static void senden(ServerPlayer spieler, Nutzlast nutzlast)
    {
        ServerPlayNetworking.send(spieler, nutzlast.type().id(), kodieren(nutzlast));
    }

    public static void senden(PacketSender sender, Nutzlast nutzlast)
    {
        sender.sendPacket(nutzlast.type().id(), kodieren(nutzlast));
    }

    private static RegistryFriendlyByteBuf kodieren(Nutzlast nutzlast)
    {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer());

        PayloadTypeRegistry.clientboundPlay().codec(nutzlast.type().id()).encode(buf, nutzlast);

        return buf;
    }
}
