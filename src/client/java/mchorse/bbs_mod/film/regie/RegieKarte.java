package mchorse.bbs_mod.film.regie;

import java.util.List;
import java.util.Locale;

/**
 * Eine Weltkarte der Regiehilfe: Inhalt, Zustand, Masse je Zustand und ihre Lage auf dem
 * Bildschirm. Masse in CSS-Pixeln des Designs (.rg-wl-card und die is-*-Varianten), die
 * Lage in Bildschirm-Pixeln.
 */
public final class RegieKarte
{
    public RegieAnsicht.Art art;
    public int farbe;
    public String nummer;
    public String titel;
    public String status;
    public String fenster;
    public float fortschritt;
    public RegieAnsicht.Phase phase;
    public boolean ok;
    public boolean kompakt;
    public boolean verdeckt;
    public float prioritaet;

    /* Ankerpunkt auf dem Bildschirm und Tiefe. */
    public float px;
    public float py;
    public float tiefe;

    /* Masse je Zustand, CSS-Pixel (siehe messen). */
    public float padOben;
    public float padRechts;
    public float padUnten;
    public float padLinks;
    public float badge;
    public float badgeSchrift;
    public float titelGroesse;
    public float titelBreite;
    public boolean meta;
    public boolean fensterZeigen;
    public boolean balken;
    public float balkenHoehe;
    public float balkenAbstand;
    public float mindestBreite;

    /* Ergebnis in Bildschirm-Pixeln. */
    public float skala;
    public float x;
    public float y;
    public float breite;
    public float hoehe;
    public float stiel;

    /** Masse je Zustand, wie im Design-CSS. */
    void messen(RegieAnsicht.Messer messer)
    {
        if (this.art == RegieAnsicht.Art.START)
        {
            this.polster(10F, 16F, 11F, 10F, 44F, 22F, 18F, 0F);
            this.meta = true;
            this.fensterZeigen = true;
            this.balken = false;
        }
        else if (this.phase == RegieAnsicht.Phase.ERLEDIGT)
        {
            this.polster(6F, 12F, 6F, 6F, 30F, 16F, 16F, 0F);
            this.meta = true;
            this.fensterZeigen = false;
            this.balken = false;
        }
        else if (this.kompakt)
        {
            this.polster(7F, 12F, 7F, 7F, 32F, 17F, 19F, 0F);
            this.meta = false;
            this.fensterZeigen = false;
            this.balken = true;
            this.balkenHoehe = 6F;
            this.balkenAbstand = 6F;
        }
        else
        {
            this.polster(10F, 16F, 11F, 10F, 44F, 22F, 22F, 228F);
            this.meta = true;
            this.fensterZeigen = true;
            this.balken = true;
            this.balkenHoehe = 9F;
            this.balkenAbstand = 8F;
        }

        this.titelBreite = Math.min(300F, messer.breite(RegieAnsicht.SCHRIFT_TITEL, this.titelGroesse, 0.005F * this.titelGroesse, this.titel));
    }

    private void polster(float oben, float rechts, float unten, float links, float badge, float badgeSchrift,
        float titel, float mindestBreite)
    {
        this.padOben = oben;
        this.padRechts = rechts;
        this.padUnten = unten;
        this.padLinks = links;
        this.badge = badge;
        this.badgeSchrift = badgeSchrift;
        this.titelGroesse = titel;
        this.mindestBreite = mindestBreite;
    }

    /** Hoehe der Spalte aus Titel, Metazeile und Balken (CSS-Pixel). */
    float hauptHoehe()
    {
        return this.titelGroesse * 1.08F + (this.meta ? 4F + 12.5F : 0F)
            + (this.balken ? this.balkenAbstand + this.balkenHoehe : 0F);
    }

    /**
     * Groesse nach Entfernung, beim Spielen waagerecht im Bild halten, ueberlappende Karten
     * nach oben schieben (bis zu sechs Runden, wie im Design).
     */
    void platzieren(boolean spielen, float bildBreite, float k, RegieAnsicht.Messer messer, List<float[]> belegt)
    {
        this.messen(messer);

        float r = (spielen ? RegieAnsicht.klemmen(20F / this.tiefe, 0.86F, 1.05F) : RegieAnsicht.klemmen(36F / this.tiefe, 0.74F, 0.92F)) * k;
        float hauptBreite = this.titelBreite;

        if (this.meta)
        {
            float meta = messer.breite(RegieAnsicht.SCHRIFT_TITEL, 12.5F, 0.07F * 12.5F, this.status.toUpperCase(Locale.ROOT));

            if (this.fensterZeigen)
            {
                meta += 14F + messer.breite(RegieAnsicht.SCHRIFT_MONO, 11.5F, 0F, this.fenster);
            }

            hauptBreite = Math.max(hauptBreite, meta);
        }

        float w = Math.max(this.mindestBreite, this.padLinks + this.badge + 12F + hauptBreite + this.padRechts);
        float h = this.padOben + Math.max(this.badge, this.hauptHoehe()) + this.padUnten;
        float tw = w * r;
        float th = h * r;
        float links = this.px - tw / 2F;
        float rechts = this.px + tw / 2F;
        float unten = this.py - (this.art == RegieAnsicht.Art.BLICK ? 18F : 30F) * r;

        if (spielen)
        {
            float versatz = 0F;

            if (links < 14F * k)
            {
                versatz = 14F * k - links;
            }
            else if (rechts > bildBreite - 14F * k)
            {
                versatz = bildBreite - 14F * k - rechts;
            }

            versatz = RegieAnsicht.klemmen(versatz, -tw * 0.46F, tw * 0.46F);
            links += versatz;
            rechts += versatz;
        }

        for (int runde = 0; runde < 6; runde++)
        {
            boolean gestossen = false;

            for (float[] b : belegt)
            {
                if (rechts > b[0] - 4F * k && links < b[1] + 4F * k && unten > b[2] - 6F * k && unten - th < b[3] + 6F * k)
                {
                    unten = b[2] - 8F * k;
                    gestossen = true;
                }
            }

            if (!gestossen)
            {
                break;
            }
        }

        belegt.add(new float[] {links, rechts, unten - th, unten});

        this.skala = r;
        this.x = links;
        this.y = unten - th;
        this.breite = tw;
        this.hoehe = th;
        this.stiel = this.py - unten;
    }
}
