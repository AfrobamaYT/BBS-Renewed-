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
        //? if >=26.1 {
        /*boolean allowed = server.getGameRules().get(BBSMod.BBS_EDITING_RULE) || server.getPlayerList().isOp(player.nameAndId());
        *///?} else if >=1.21.11 {
        /*boolean allowed = server.overworld().getGameRules().get(BBSMod.BBS_EDITING_RULE) || server.getPlayerList().isOp(player.nameAndId());
        *///?} else {
        boolean allowed = server.overworld().getGameRules().getBoolean(BBSMod.BBS_EDITING_RULE) || server.getPlayerList().isOp(player.getGameProfile());
        //?}

        return allowed;
    }
}
