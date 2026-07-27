// Coded by Afrobama/Claude — C2S custom packet: client builder asks the
// server to apply / remove a block on its behalf. Server-authoritative:
// the server re-parses the blockstate string + permission-checks the
// sender before calling setBlockState, so a malicious client can't grief
// outside the player's normal capabilities.
package com.afrobama.bbstools.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record BuilderActionPacket(int blockX, int blockY, int blockZ, String blockState, byte action)
    implements CustomPayload
{
    public static final byte ACTION_PLACE = 0;
    public static final byte ACTION_BREAK = 1;

    public static final Identifier CHANNEL =
        Identifier.of("bbstools", "builder_action");

    public static final CustomPayload.Id<BuilderActionPacket> ID =
        new CustomPayload.Id<>(CHANNEL);

    public static final PacketCodec<PacketByteBuf, BuilderActionPacket> CODEC =
        PacketCodec.tuple(
            PacketCodecs.VAR_INT,           BuilderActionPacket::blockX,
            PacketCodecs.VAR_INT,           BuilderActionPacket::blockY,
            PacketCodecs.VAR_INT,           BuilderActionPacket::blockZ,
            PacketCodecs.STRING,            BuilderActionPacket::blockState,
            PacketCodecs.BYTE,              BuilderActionPacket::action,
            BuilderActionPacket::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId()
    {
        return ID;
    }
}
