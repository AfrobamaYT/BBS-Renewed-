package net.fabricmc.fabric.api.networking.v1;

import mchorse.bbs_mod.compat.netz.Nutzlast;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * vor1205: Fabrics Nutzlast-Register kam mit 1.20.5. Hier merkt es sich nur die Codecs je
 * Kanal; NetzServer/NetzClient schreiben und lesen damit ueber die alte Kanal-API, immer mit
 * einem RegistryFriendlyByteBuf (passt fuer FriendlyByteBuf- wie RegistryFriendlyByteBuf-Codecs).
 */
public final class PayloadTypeRegistry
{
    private static final PayloadTypeRegistry ZUM_SERVER = new PayloadTypeRegistry();
    private static final PayloadTypeRegistry ZUM_CLIENT = new PayloadTypeRegistry();

    private final Map<ResourceLocation, StreamCodec<?, ?>> codecs = new HashMap<>();

    public static PayloadTypeRegistry serverboundPlay()
    {
        return ZUM_SERVER;
    }

    public static PayloadTypeRegistry clientboundPlay()
    {
        return ZUM_CLIENT;
    }

    /* Namen der Fabric-API vor 26.1 (die vor261-Regeln benennen dahin um). */
    public static PayloadTypeRegistry playC2S()
    {
        return ZUM_SERVER;
    }

    public static PayloadTypeRegistry playS2C()
    {
        return ZUM_CLIENT;
    }

    public <T extends Nutzlast> Nutzlast.Type<T> register(Nutzlast.Type<T> typ, StreamCodec<?, T> codec)
    {
        this.codecs.put(typ.id(), codec);

        return typ;
    }

    @SuppressWarnings("unchecked")
    public <T extends Nutzlast> StreamCodec<RegistryFriendlyByteBuf, T> codec(ResourceLocation id)
    {
        StreamCodec<?, ?> codec = this.codecs.get(id);

        if (codec == null)
        {
            throw new IllegalStateException("Kein Codec fuer Kanal " + id + " registriert");
        }

        return (StreamCodec<RegistryFriendlyByteBuf, T>) codec;
    }
}
