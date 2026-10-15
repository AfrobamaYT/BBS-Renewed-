package mchorse.bbs_mod.compat;

import net.minecraft.client.entity.ClientAvatarState;
import net.minecraft.client.player.AbstractClientPlayer;

/** AbstractClientPlayer.avatarState von 1.21.9+ vor 1.21.9 (Sicht auf oBob/bob). Injiziert. */
public interface AbstractClientPlayerAlt
{
    default ClientAvatarState avatarState()
    {
        return new ClientAvatarState((AbstractClientPlayer) this);
    }
}
