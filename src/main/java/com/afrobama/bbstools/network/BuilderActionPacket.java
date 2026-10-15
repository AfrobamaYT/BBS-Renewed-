// Coded by Afrobama/Claude — C2S custom packet: client builder asks the
// server to apply / remove a block on its behalf. Server-authoritative:
// the server re-parses the blockstate string + permission-checks the
// sender before calling setBlockState, so a malicious client can't grief
// outside the player's normal capabilities.
package com.afrobama.bbstools.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import mchorse.bbs_mod.compat.netz.Nutzlast;
import net.minecraft.resources.ResourceLocation;

public record BuilderActionPacket(int blockX, int blockY, int blockZ, String blockState, byte action)
    implements Nutzlast
{
    public static final byte ACTION_PLACE = 0;
    public static final byte ACTION_BREAK = 1;

    public static final ResourceLocation CHANNEL =
        new ResourceLocation("bbstools", "builder_action");

    public static final Nutzlast.Type<BuilderActionPacket> ID =
        new Nutzlast.Type<>(CHANNEL);

    public static final StreamCodec<FriendlyByteBuf, BuilderActionPacket> CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT,           BuilderActionPacket::blockX,
            ByteBufCodecs.VAR_INT,           BuilderActionPacket::blockY,
            ByteBufCodecs.VAR_INT,           BuilderActionPacket::blockZ,
            ByteBufCodecs.STRING_UTF8,            BuilderActionPacket::blockState,
            ByteBufCodecs.BYTE,              BuilderActionPacket::action,
            BuilderActionPacket::new);

    @Override
    public Nutzlast.Type<? extends Nutzlast> type()
    {
        return ID;
    }
}
