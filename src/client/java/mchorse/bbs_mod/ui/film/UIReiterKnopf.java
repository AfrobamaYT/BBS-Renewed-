package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.function.Consumer;

/**
 * Ein Reiter am Aussenrand der Seitenleiste — Beschriftung senkrecht, wie in Blender.
 *
 * <p>⛔ Blender DREHT den Text um 90°. BBS' Batcher hat keinen Rotationsparameter
 * ({@code batcher.text(text, x, y, farbe)}), und einen einzubauen waere Renderer-Arbeit
 * fuer reine Kosmetik. Hier stehen die Buchstaben deshalb GESTAPELT untereinander. Das
 * ist sichtbar nicht dasselbe, aber es liest sich senkrecht und kostet keine neue
 * Zeichenroutine — und es steht hier, damit niemand es fuer Blenders Loesung haelt.
 */
public class UIReiterKnopf extends UIElement
{
    private final String beschriftung;
    private final Consumer<UIReiterKnopf> klick;

    public boolean aktiv;

    public UIReiterKnopf(String beschriftung, Consumer<UIReiterKnopf> klick)
    {
        this.beschriftung = beschriftung;
        this.klick = klick;
    }

    public String beschriftung()
    {
        return this.beschriftung;
    }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (this.area.isInside(context) && context.mouseButton == 0)
        {
            this.klick.accept(this);

            return true;
        }

        return super.subMouseClicked(context);
    }

    @Override
    public void render(UIContext context)
    {
        boolean drueber = this.area.isInside(context);

        context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(),
            this.aktiv ? 0xFF4772B3 : (drueber ? 0xFF4a4a4a : 0xFF333333));

        /* Ein Buchstabe je Zeile, mittig. Zeilenhoehe 9 wie die Schrift selbst. */
        mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer schrift = context.batcher.getFont();
        int hoehe = this.beschriftung.length() * 9;
        int y = this.area.my() - hoehe / 2;

        for (int i = 0; i < this.beschriftung.length(); i++)
        {
            String z = String.valueOf(this.beschriftung.charAt(i));

            context.batcher.text(z, this.area.mx(schrift.getWidth(z)), y + i * 9,
                this.aktiv ? Colors.WHITE : 0xFFbbbbbb);
        }

        super.render(context);
    }
}
