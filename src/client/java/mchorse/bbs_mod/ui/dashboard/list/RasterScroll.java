package mchorse.bbs_mod.ui.dashboard.list;

import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.Scroll;

/**
 * Scroll-Geometrie fuer ein Kachelraster statt einer Zeilenliste.
 *
 * <p>Die Zeilenliste rechnet Index = y / Zeilenhoehe. Ein Raster braucht dieselbe Rechnung
 * in zwei Achsen. Das ist der EINZIGE Unterschied — Auswahl, Kontextmenue, Tastenkuerzel und
 * Suche der Liste bleiben unangetastet, weil sie alle ueber diesen Index gehen.</p>
 */
public class RasterScroll extends Scroll
{
    /** Wird beim Umbrechen aus der Breite berechnet, nie geraten. */
    public int spalten = 1;

    public RasterScroll(Area area, int kachelHoehe)
    {
        super(area, kachelHoehe);
    }

    @Override
    public void setSize(int items)
    {
        int s = Math.max(1, this.spalten);

        this.scrollSize = ((items + s - 1) / s) * this.scrollItemSize;
    }

    @Override
    public int getIndex(int x, int y)
    {
        int s = Math.max(1, this.spalten);
        int breite = Math.max(1, this.area.w / s);
        int spalte = (x - this.area.x) / breite;

        if (spalte < 0 || spalte >= s)
        {
            return -1;
        }

        int zeile = (y - this.area.y + (int) this.getScroll()) / this.scrollItemSize;

        return zeile * s + spalte;
    }
}
