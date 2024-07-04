package mchorse.bbs_mod.actions;

import mchorse.bbs_mod.actions.types.ChatActionClip;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.network.ServerPlayerEntity;

public class ActionHandler
{
    public static void registerHandlers(ActionManager actions)
    {
        ServerMessageEvents.CHAT_MESSAGE.register((SignedMessage message, ServerPlayerEntity sender, MessageType.Parameters params) ->
        {
            ChatActionClip clip = new ChatActionClip();

            clip.message.set(message.getContent().getLiteralString());
            actions.addAction(sender, clip);
        });
    }
}