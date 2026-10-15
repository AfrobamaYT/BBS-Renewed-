package net.minecraft.client.entity;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * BBS-Shim fuer Minecraft vor 1.21.9: der Sichtwackel-Zustand, der ab 1.21.9 am Spieler-Avatar
 * haengt. Vor 1.21.9 sind es die Felder oBob/bob des Spielers; Rechnung wie 1.21.9s updateBob.
 */
public final class ClientAvatarState
{
    private final Player spieler;

    public ClientAvatarState(Player spieler)
    {
        this.spieler = spieler;
    }

    public float getInterpolatedBob(float teilTick)
    {
        return Mth.lerp(teilTick, this.spieler.oBob, this.spieler.bob);
    }

    public void updateBob(float ziel)
    {
        this.spieler.oBob = this.spieler.bob;
        this.spieler.bob += (ziel - this.spieler.bob) * 0.4F;
    }
}
