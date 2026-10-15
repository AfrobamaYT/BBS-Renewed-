package mchorse.bbs_mod.utils;

import com.mojang.authlib.GameProfile;
import mchorse.bbs_mod.network.ClientNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;

public class PlayerUtils
{
    public static void teleport(double x, double y, double z, float yaw, float pitch)
    {
        teleport(x, y, z, yaw, yaw, pitch);
    }

    public static void teleport(double x, double y, double z, float yaw, float bodyYaw, float pitch)
    {
        LocalPlayer player = Minecraft.getInstance().player;

        if (!ClientNetwork.isIsBBSModOnServer())
        {
            String command = "tp " + player.getGameProfile().name() + " " + x + " " + y + " " + z + " " + yaw + " " + pitch;

            /* ⚠️ sendUnsignedCommand ist weg. sendCommand ist der Nachfolger mit demselben
             * Paket; der zweite Kandidat sendUnattendedCommand(cmd, screen) waere FALSCH,
             * denn der setzt bei Erfolg den Bildschirm auf sein Argument — mit null wuerde
             * er also den offenen BBS-Editor zumachen. Unterschied zu frueher: ein Befehl
             * mit signierbaren Argumenten (Chat-artig, z.B. /me) wurde vom alten Weg still
             * verworfen, sendCommand signiert und sendet ihn. Fuer /tp ohne Belang. */
            player.connection.sendCommand(command);
        }
        else
        {
            ClientNetwork.sendTeleport(x, y, z, yaw, bodyYaw, pitch);
            player.setYRot(yaw);
            player.setYHeadRot(yaw);
            player.setYBodyRot(bodyYaw);
            player.setXRot(pitch);
        }
    }

    public static void teleport(double x, double y, double z)
    {
        LocalPlayer player = Minecraft.getInstance().player;

        if (!ClientNetwork.isIsBBSModOnServer())
        {
            player.connection.sendCommand("tp " + player.getGameProfile().name() + " " + x + " " + y + " " + z);
        }
        else
        {
            ClientNetwork.sendTeleport(player, x, y, z);
        }
    }

    public static class ProtectedAccess extends Player
    {
        public static EntityDataAccessor<Byte> getModelParts()
        {
            return DATA_PLAYER_MODE_CUSTOMISATION;
        }

        /* ⚠️ Der Player-Konstruktor nimmt in 26.2 nur noch Welt + Profil; Position und Blickwinkel
         * setzt man danach ueber snapTo(). Die Klasse existiert ohnehin nur, um an das
         * geschuetzte DATA_PLAYER_MODE_CUSTOMISATION heranzukommen (einziger Nutzer:
         * MobFormRenderer) — sie wird nirgends instanziiert, deshalb faellt der ungenutzte
         * pos/yaw-Teil der Signatur ersatzlos weg statt weitergereicht zu werden. */
        //? if >=1.21.9 {
        public ProtectedAccess(Level world, GameProfile gameProfile)
        {
            super(world, gameProfile);
        }
        //?} else {
        /*public ProtectedAccess(Level world, GameProfile gameProfile)
        {
            super(world, net.minecraft.core.BlockPos.ZERO, 0F, gameProfile);
        }
        *///?}

        /* ⚠️ Player#gameMode ist in 26.2 abstrakt (der Spielmodus haengt jetzt an der Entitaet,
         * nicht mehr am Interaktionsmanager). SURVIVAL ist die Antwort, die zu den beiden
         * bestehenden Ueberschreibungen unten passt (weder Zuschauer noch Kreativ). */
        //? if >=1.21.9 {
        @Override
        public GameType gameMode()
        {
            return GameType.SURVIVAL;
        }
        //?}

        @Override
        public boolean isSpectator()
        {
            return false;
        }

        @Override
        public boolean isCreative()
        {
            return false;
        }
    }
}