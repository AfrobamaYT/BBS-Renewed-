package net.minecraft.network;

import io.netty.buffer.ByteBuf;

/** vor1205: ab 1.20.5 traegt der Puffer Registerzugriff; BBS braucht davon nichts. */
public class RegistryFriendlyByteBuf extends FriendlyByteBuf
{
    public RegistryFriendlyByteBuf(ByteBuf buf)
    {
        super(buf);
    }
}
