package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.vektor.VektorZeichner;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.function.Consumer;

/**
 * Knopf mit runden Ecken — fuer die Rig-Layer-Leiste, wie Blender sie zeichnet.
 *
 * <p>⛔ Warum eine eigene Klasse und keine Aenderung am Theme: {@code UIButton.renderSkin}
 * ist per Mixin durch {@code UIButtonSkinLogik} ersetzt, und die zeichnet fuer die GANZE
 * Oberflaeche bewusst eckig ("AE/Premiere-Look"). Alle Knoepfe rund zu machen waere eine
 * Aenderung am ganzen Editor, nicht an der Seitenleiste. Diese Unterklasse ueberschreibt
 * {@code renderSkin} selbst — der Mixin haengt an UIButtons eigener Methode und laeuft
 * hier deshalb nicht mit.
 */
public class UIRundKnopf extends UIButton
{
    public UIRundKnopf(IKey label, Consumer<UIButton> callback)
    {
        super(label, callback);
    }

    @Override
    protected void renderSkin(UIContext context)
    {
        int farbe = this.custom ? this.customColor : mchorse.bbs_mod.BBSSettings.primaryColor(0);

        /* ⛔ `color(int)` maskiert das Alpha weg — ohne Auffuellen bliebe die Flaeche
         * durchsichtig (dieselbe Falle wie im Theme-Painter, dort schon bezahlt). */
        if ((farbe >>> 24) == 0)
        {
            farbe |= 0xFF000000;
        }

        if (this.hover)
        {
            farbe = Colors.lerp(farbe, Colors.WHITE, 0.12F);
        }

        if (this.background)
        {
            if (VektorZeichner.an())
            {
                context.batcher.rundeck(this.area.x, this.area.y, this.area.w, this.area.h,
                    3F, farbe);
            }
            else
            {
                context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), farbe);
            }
        }

        FontRenderer font = context.batcher.getFont();
        String label = font.limitToWidth(this.label.get(), this.area.w - 4);

        context.batcher.text(label, this.area.mx(font.getWidth(label)),
            this.area.my(font.getHeight()), this.textColor, this.textShadow);
    }
}
