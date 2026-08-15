package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.function.Consumer;

/**
 * Ein Reiter am Aussenrand der Seitenleiste — Beschriftung um 90° gedreht, wie in Blender.
 *
 * <p>Die Drehung kommt aus {@code batcher.text(..., winkel)}. Vorher stapelte dieser Knopf
 * die Buchstaben untereinander, weil der Batcher keinen Drehwinkel kannte — die Schwaeche
 * lag im Zeichner, nicht im Reiter, und ist dort behoben worden.
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

        mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer schrift = context.batcher.getFont();
        int breite = schrift.getWidth(this.beschriftung);
        int hoehe = schrift.getHeight();

        /* -90° laesst den Text von unten nach oben laufen. Nach der Drehung belegt er vom
         * Ankerpunkt aus x .. x+Schrifthoehe und y-Textbreite .. y — daher der Versatz,
         * damit er mittig im Knopf sitzt. */
        context.batcher.text(this.beschriftung, this.area.mx() - hoehe / 2F,
            this.area.my() + breite / 2F, this.aktiv ? Colors.WHITE : 0xFFbbbbbb, true, -90F);

        super.render(context);
    }
}
