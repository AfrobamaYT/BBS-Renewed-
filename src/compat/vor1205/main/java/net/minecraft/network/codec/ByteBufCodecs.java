package net.minecraft.network.codec;

import net.minecraft.network.FriendlyByteBuf;

/** vor1205: die von BBS benutzten Grundcodecs, auf FriendlyByteBuf. */
public final class ByteBufCodecs
{
    public static final StreamCodec<FriendlyByteBuf, Integer> VAR_INT = new StreamCodec<>()
    {
        @Override public Integer decode(FriendlyByteBuf buf) { return buf.readVarInt(); }
        @Override public void encode(FriendlyByteBuf buf, Integer wert) { buf.writeVarInt(wert); }
    };

    public static final StreamCodec<FriendlyByteBuf, String> STRING_UTF8 = new StreamCodec<>()
    {
        @Override public String decode(FriendlyByteBuf buf) { return buf.readUtf(); }
        @Override public void encode(FriendlyByteBuf buf, String wert) { buf.writeUtf(wert); }
    };

    public static final StreamCodec<FriendlyByteBuf, Byte> BYTE = new StreamCodec<>()
    {
        @Override public Byte decode(FriendlyByteBuf buf) { return buf.readByte(); }
        @Override public void encode(FriendlyByteBuf buf, Byte wert) { buf.writeByte(wert); }
    };

    private ByteBufCodecs()
    {}
}
