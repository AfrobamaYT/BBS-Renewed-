package mchorse.bbs_mod.utils;

import mchorse.bbs_mod.BBSMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class PermissionUtils
{
    /* 26.2: eine Regel ist jetzt ein GameRule<Boolean>; getGameRules().get(regel) liefert
     * den Wert direkt statt eines BooleanValue-wrappers. isOp nimmt NameAndId statt
     * GameProfile. */
    public static boolean arePanelsAllowed(MinecraftServer server, ServerPlayer player)
    {
        boolean allowed = server.getGameRules().get(BBSMod.BBS_EDITING_RULE) || server.getPlayerList().isOp(player.nameAndId());

        return allowed;
    }
}
