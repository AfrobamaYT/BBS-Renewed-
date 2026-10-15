package mchorse.bbs_mod.compat;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/** 26.2's chat and action-bar messages on the client player, for the versions before 26.1. Injected into LocalPlayer. */
public interface LocalPlayerAlt
{
    default void sendSystemMessage(Component message)
    {
        ((LocalPlayer) this).displayClientMessage(message, false);
    }

    default void sendOverlayMessage(Component message)
    {
        ((LocalPlayer) this).displayClientMessage(message, true);
    }
}
