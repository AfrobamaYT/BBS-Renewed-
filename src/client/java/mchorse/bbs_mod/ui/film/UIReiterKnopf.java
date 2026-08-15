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

        /* ⛔ Der AKTIVE Reiter traegt die PANEL-Farbe und reicht drei Punkte nach links
         * ueber seinen Streifen hinaus — damit verschmilzt er mit dem Inhalt, wie in
         * Blender. Als eigenstaendig blauer Kasten sah er aus wie ein Knopf neben dem
         * Panel, nicht wie sein Reiter. */
        /* Dieselbe Flaeche wie das Panel — so verschmilzt der aktive Reiter damit, wie in
         * Blender. Nicht die Akzentfarbe: die machte aus dem Reiter einen blauen Knopf. */
        int flaeche = mchorse.bbs_mod.BBSSettings.deepSurface();

        int farbe = this.aktiv ? flaeche : (drueber ? 0xFF3a3a3a : 0xFF2b2b2b);
        float x = this.area.x - (this.aktiv ? 3 : 0);
        float breiteK = this.area.ex() - x;

        /* Rund wie in Blender. Ohne Vektor-Oberflaeche gibt es kein Rundeck — dann der
         * eckige Kasten, statt gar nichts zu zeichnen. */
        if (mchorse.bbs_mod.ui.framework.elements.utils.vektor.VektorZeichner.an())
        {
            context.batcher.rundeck(x, this.area.y, breiteK, this.area.h, 4F, farbe);
        }
        else
        {
            context.batcher.box(x, this.area.y, this.area.ex(), this.area.ey(), farbe);
        }

        mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer schrift = context.batcher.getFont();
        int breite = schrift.getWidth(this.beschriftung);
        int hoehe = schrift.getHeight();

        /* +90°: der Text laeuft von oben nach unten, Buchstabenoberseiten nach rechts —
         * Blenders Richtung (vorher lief er andersherum, das ist die Drehung um 180°).
         * Nach der Drehung belegt er vom Ankerpunkt aus x-Schrifthoehe .. x und
         * y .. y+Textbreite, daher der Versatz.
         *
         * ⛔ OHNE Schatten, wie Blender: der Schatten ist ein zweiter Durchgang um (+1,+1)
         * im TEXTraum, und der wird mitgedreht — bei 90° faellt er diagonal neben die
         * Buchstaben und macht die 7 px hohe Schrift matschig statt lesbarer. */
        context.batcher.text(this.beschriftung, this.area.mx() + hoehe / 2F,
            this.area.my() - breite / 2F, this.aktiv ? Colors.WHITE : 0xFF9a9a9a, false, 90F);

        super.render(context);
    }
}
