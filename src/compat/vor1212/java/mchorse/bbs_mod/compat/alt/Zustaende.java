package mchorse.bbs_mod.compat.alt;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Vor 1.21.2: baut aus einer Entitaet den Render-Zustand, den 1.21.2+ selbst einsammelt
 * (Lage, Licht, fuer Lebewesen Drehung, Groesse, Gang), damit die zustandsbasierte Logik von
 * BBS unveraendert laeuft.
 */
public final class Zustaende
{
    private Zustaende()
    {}

    public static EntityRenderState fuer(Entity entitaet, float teilTick, int licht)
    {
        EntityRenderState zustand = entitaet instanceof Player ? new PlayerRenderState()
            : entitaet instanceof LivingEntity ? new LivingEntityRenderState() : new EntityRenderState();

        zustand.bbsEntitaet = entitaet;
        zustand.x = Mth.lerp(teilTick, entitaet.xOld, entitaet.getX());
        zustand.y = Mth.lerp(teilTick, entitaet.yOld, entitaet.getY());
        zustand.z = Mth.lerp(teilTick, entitaet.zOld, entitaet.getZ());
        zustand.ageInTicks = entitaet.tickCount + teilTick;
        zustand.partialTick = teilTick;
        zustand.isInvisible = entitaet.isInvisible();
        zustand.distanceToCameraSq = Minecraft.getInstance().getEntityRenderDispatcher().distanceToSqr(entitaet);
        zustand.lightCoords = licht;

        if (zustand instanceof LivingEntityRenderState lebend && entitaet instanceof LivingEntity l)
        {
            lebend.bodyRot = Mth.rotLerp(teilTick, l.yBodyRotO, l.yBodyRot);
            lebend.yRot = Mth.rotLerp(teilTick, l.yHeadRotO, l.yHeadRot) - lebend.bodyRot;
            lebend.xRot = l.getViewXRot(teilTick);
            lebend.scale = l.getScale();
            lebend.deathTime = l.deathTime > 0 ? l.deathTime + teilTick : 0F;
            lebend.isBaby = l.isBaby();
            lebend.walkAnimationPos = l.walkAnimation.position(teilTick);
            lebend.walkAnimationSpeed = l.walkAnimation.speed(teilTick);
        }

        return zustand;
    }
}
