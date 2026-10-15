package com.afrobama.blendertransform;

import com.mojang.blaze3d.vertex.PoseStack;
import mchorse.bbs_mod.client.renderer.MorphRenderer;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.FormSammler;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.morphing.Morph;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;

/**
 * Logik hinter {@code PlayerEntityRendererMixin} — was der Spieler-Renderer tun darf, wenn
 * der Spieler eine BBS-Gestalt traegt.
 *
 * <p>Zwei Faelle, die dasselbe beantworten: greift die Gestalt, oder zeichnet Vanilla?</p>
 */
public final class SpielerFormRendererLogik
{
    private SpielerFormRendererLogik() {}

    /**
     * {@code PlayerRenderer.getRenderOffset} @HEAD.
     *
     * <p>⚠️ VANILLAS VERSATZ MUSS WEG, NICHT ERGAENZT WERDEN. Der Versatz drueckt geduckte
     * Spieler nach unten; die Gestalt bringt ihre eigene Hoehe mit und wuerde sonst doppelt
     * versetzt stehen — sichtbar als im Boden versunkenes Modell beim Schleichen.</p>
     *
     * @param zustand der Renderzustand; der Spieler haengt als Datum daran, nicht als Feld
     * @return {@code true} = Versatz auf null setzen
     */
    public static boolean versatzUnterdruecken(PlayerRenderState zustand)
    {
        Entity entitaet = MorphRenderer.entityVon(zustand);

        if (!(entitaet instanceof AbstractClientPlayer spieler))
        {
            return false;
        }

        Morph gestalt = Morph.getMorph(spieler);

        return gestalt != null && gestalt.getForm() != null;
    }

    /**
     * {@code renderRightHand} / {@code renderLeftHand} @HEAD.
     *
     * <p>⚠️ DER ARM KOMMT VOM EIGENEN SPIELER, nicht aus dem Renderzustand. Diese beiden
     * Methoden zeichnen die Ich-Perspektive; ein anderer Spieler landet hier nie. Deshalb ist
     * {@code Minecraft.getInstance().player} hier richtig und woanders falsch.</p>
     *
     * <p>⚠️ {@code renderArm} ZEICHNET UND ANTWORTET IN EINEM. Der Rueckgabewert sagt "ich habe
     * ihn gezeichnet" — er ist keine Abfrage, die man vorziehen oder weglassen koennte.</p>
     *
     * @return {@code true} = Vanillas Arm wird uebersprungen
     */
    public static boolean arm(PoseStack matrizen, SubmitNodeCollector sammler, int licht, InteractionHand hand)
    {
        AbstractClientPlayer spieler = Minecraft.getInstance().player;

        if (spieler == null)
        {
            return false;
        }

        Morph gestalt = Morph.getMorph(spieler);
        Form form = gestalt == null ? null : gestalt.getForm();

        if (form == null)
        {
            return false;
        }

        FormRenderer zeichner = FormUtilsClient.getRenderer(form);

        if (zeichner == null)
        {
            return false;
        }

        /* The hand has its own native collector, outside the level's registered pass. */
        boolean[] gezeichnet = new boolean[1];

        FormSammler.mitSammler(sammler, () ->
            gezeichnet[0] = zeichner.renderArm(matrizen, licht, spieler, hand));

        return gezeichnet[0];
    }
}
