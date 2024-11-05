package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.network.ClientNetwork;
import net.minecraft.network.packet.s2c.play.CustomPayloadS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(CustomPayloadS2CPacket.class)
public class CustomPayloadS2CPacketMixin
{
    @ModifyConstant(method = "<init>(Lnet/minecraft/network/PacketByteBuf;)V", constant = @Constant(intValue = 1048576))
    private int modifyMaxPacketSizeIdentifierBuf(int value)
    {
        return ClientNetwork.isUnlimitedPacketSize() ? Integer.MAX_VALUE : value;
    }

    @ModifyConstant(method = "<init>(Lnet/minecraft/util/Identifier;Lnet/minecraft/network/PacketByteBuf;)V", constant = @Constant(intValue = 1048576))
    private int modifyMaxPacketSizeBuf(int value)
    {
        return ClientNetwork.isUnlimitedPacketSize() ? Integer.MAX_VALUE : value;
    }
}