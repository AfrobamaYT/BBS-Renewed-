package com.afrobama.blendertransform;

import com.mojang.blaze3d.vertex.PoseStack;
import mchorse.bbs_mod.client.renderer.MorphRenderer;
import mchorse.bbs_mod.compat.gpu.ZustandsSchluessel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Logik hinter {@code EntityRenderDispatcherMixin} — traegt eine Entitaet eine BBS-Gestalt,
 * zeichnet BBS sie statt Vanilla.
 *
 * <p>Zwei Handler, die zusammengehoeren: seit 26.2 laeuft das Zeichnen ueber einen
 * {@code EntityRenderState}, und der kennt die Entitaet selbst nicht mehr. {@link #merken}
 * haengt sie beim Einsammeln an, {@link #gezeichnet} holt sie beim Zeichnen wieder heraus.</p>
 *
 * <p>⛔ OHNE DAS MERKEN GIBT ES KEIN ZEICHNEN. Wer den ersten Handler entfernt oder seinen
 * Schluessel aendert, bekommt keinen Fehler — {@code entityVon} liefert dann einfach
 * {@code null}, und alle Gestalten verschwinden lautlos zurueck auf ihr Vanilla-Modell.</p>
 */
public final class EntitaetVersandLogik
{
    private EntitaetVersandLogik() {}

    /** {@code extractEntity} @RETURN — die Entitaet an ihren Renderzustand haengen. */
    public static void merken(EntityRenderState zustand, Entity entitaet)
    {
        if (zustand != null)
        {
            mchorse.bbs_mod.compat.gpu.ZustandsSchluessel.setData(zustand, MorphRenderer.ENTITY_AM_ZUSTAND, entitaet);
        }
    }

    /**
     * {@code submit} — hat BBS die Entitaet selbst gezeichnet?
     *
     * <p>⚠️ SPIELER ZUERST. {@code renderPlayer} und {@code renderLivingEntity} sind zwei
     * Wege; ein Spieler wuerde vom zweiten auch angenommen, aber ohne seine Spieler-Teile
     * (Arm, Skin-Schichten). Die Reihenfolge ist die Entscheidung, nicht Kosmetik.</p>
     *
     * <p>⚠️ {@code hurtTime}/{@code deathTime} landen in der Ueberlagerung — das ist das rote
     * Aufblitzen beim Treffer. Faellt es weg, sieht man Schaden an Gestalten nicht mehr.</p>
     *
     * @return {@code true} = fertig, Vanillas Renderer wird uebersprungen
     */
    public static boolean gezeichnet(EntityRenderState zustand, PoseStack matrizen, SubmitNodeCollector sammler)
    {
        Entity entitaet = MorphRenderer.entityVon(zustand);

        if (!(entitaet instanceof LivingEntity lebendig))
        {
            return false;
        }

        float teilTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        //? if >=1.21.5 {
        /*int licht = zustand.lightCoords;
        *///?} else {
        int licht = mchorse.bbs_mod.compat.gpu.ZustandsLicht.aktuell;
        //?}

        if (lebendig instanceof AbstractClientPlayer spieler
            && MorphRenderer.renderPlayer(spieler, 0F, teilTick, matrizen, sammler, licht))
        {
            return true;
        }

        int ueberlagerung = OverlayTexture.pack(0F, lebendig.hurtTime > 0 || lebendig.deathTime > 0);

        return MorphRenderer.renderLivingEntity(lebendig, 0F, teilTick, matrizen, sammler,
            licht, ueberlagerung);
    }
}
