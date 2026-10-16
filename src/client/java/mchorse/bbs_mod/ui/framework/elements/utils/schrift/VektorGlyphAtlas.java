package mchorse.bbs_mod.ui.framework.elements.utils.schrift;

import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.utils.resources.Pixels;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Ein Glyphen-Atlas EINER Pixelgroesse: alle Zeichen werden einmal vorgerastert und in
 * einem Rutsch hochgeladen — im eingeschwungenen Zustand gibt es keine Uploads mehr
 * (Blenders BLF-Muster: Glyphen-Cache je Groesse). Weisse Glyphen, Deckung im Alpha;
 * die Faerbung macht der Vertex-Tint des Batchers.
 */
public final class VektorGlyphAtlas
{
    /** Platzierung eines Zeichens: Pixel-UVs im Atlas + Metriken in Pixeln. */
    public record Glyph(int u, int v, int w, int h, int bearingX, int bearingY, float advance)
    {
    }

    public final int seite;
    public final int pixelGroesse;

    private final Map<Integer, Glyph> glyphen = new HashMap<>();
    private ByteBuffer bild;
    private Texture texture;

    /* Shelf-Packer: naechste freie Stelle + Hoehe der laufenden Zeile. */
    private int packX = 1;
    private int packY = 1;
    private int zeilenHoehe;

    public VektorGlyphAtlas(int pixelGroesse)
    {
        this.pixelGroesse = pixelGroesse;
        this.seite = pixelGroesse <= 24 ? 512 : 1024;
        this.bild = MemoryUtil.memCalloc(this.seite * this.seite * 4);
    }

    public Glyph holen(int codepoint)
    {
        return this.glyphen.get(codepoint);
    }

    /**
     * Ein gerastertes Zeichen einlegen. {@code zeilen}/{@code spalten} sind die
     * Bitmap-Masse, {@code pitch} der Zeilenschritt der Quelle (FreeType-Konvention,
     * kann breiter als die Breite sein).
     */
    public boolean einlegen(int codepoint, ByteBuffer quelle, int spalten, int zeilen, int pitch, int bearingX, int bearingY, float advance)
    {
        if (this.bild == null)
        {
            return false;
        }

        if (this.packX + spalten + 1 >= this.seite)
        {
            this.packX = 1;
            this.packY += this.zeilenHoehe + 1;
            this.zeilenHoehe = 0;
        }

        if (this.packY + zeilen + 1 >= this.seite)
        {
            System.err.println("[vektorschrift] Atlas " + this.pixelGroesse + "px ist voll — '" + new String(Character.toChars(codepoint)) + "' entfaellt");

            return false;
        }

        for (int r = 0; r < zeilen; r++)
        {
            for (int c = 0; c < spalten; c++)
            {
                int deckung = kante(quelle.get(r * pitch + c) & 0xFF);
                int ziel = ((this.packY + r) * this.seite + this.packX + c) * 4;

                this.bild.put(ziel, (byte) 0xFF);
                this.bild.put(ziel + 1, (byte) 0xFF);
                this.bild.put(ziel + 2, (byte) 0xFF);
                this.bild.put(ziel + 3, (byte) deckung);
            }
        }

        this.glyphen.put(codepoint, new Glyph(this.packX, this.packY, spalten, zeilen, bearingX, bearingY, advance));
        this.packX += spalten + 1;
        this.zeilenHoehe = Math.max(this.zeilenHoehe, zeilen);

        return true;
    }

    /**
     * Kantenkurve fuer die Glyph-Deckung — macht die Schrift scharf statt weich.
     *
     * <p>⛔ WARUM (2026-09-01). Inter wird bei 18 px (BASIS 9 x GUI-Skala 2) gerastert.
     * FreeType liefert dort reines Graustufen-AA, und ein Stamm von 1,4 px Breite
     * verteilt sich auf zwei Pixel mit je ~70 % Deckung — das ist die weiche Schrift.
     * Hinting half nicht: Inter bringt keine Hinting-Tabellen mit, FreeType nutzt den
     * Autohinter dort ohnehin schon, FT_LOAD_FORCE_AUTOHINT war messbar ein No-Op
     * (Textregion ueber den Build-Wechsel bit-identisch).</p>
     *
     * <p>Der wirksame Hebel ist die Deckungskurve selbst: eine S-Kurve um 0,5 zieht
     * Halbdeckungen zu voll oder leer, ohne die Glyphform zu verschieben. Das ist der
     * uebliche Weg (Skia: Gammakorrektur + Kontrastanhebung fuer Text, FreeType:
     * Stem-Darkening), kein Eigenbau.</p>
     *
     * <p>⛔ Nur die DECKUNG anfassen, nie Position oder Groesse — sonst wandern die
     * Buchstaben, und das Mass in VektorSchrift.breite() passt nicht mehr zum Bild.</p>
     */
    private static int kante(int deckung)
    {
        float x = deckung / 255F;

        /* Stem-Darkening: duenne Striche etwas kraeftiger, sonst wirken sie ausgewaschen. */
        x = (float) Math.pow(x, 0.82F);

        /* Kontrast um die Mitte — 1,7 ist gemessen der Punkt, an dem Kanten hart werden,
         * ohne dass duenne Rundungen (e, s, a) aufbrechen. */
        x = (x - 0.5F) * 1.7F + 0.5F;

        return Math.max(0, Math.min(255, Math.round(x * 255F)));
    }

    /** Einmal nach dem Fuellen; gibt den CPU-Puffer frei — danach ist der Atlas fix. */
    public void hochladen()
    {
        if (this.bild == null)
        {
            return;
        }

        mchorse.bbs_mod.graphics.Messpunkt.zaehle("vektorschrift.upload");

        /* 9729 = GL_LINEAR: die Quads liegen durch float-Vorschuebe auf Subpixeln,
         * NEAREST wuerde daraus sichtbares Zittern der Buchstabenbreiten machen. */
        this.texture = Texture.textureFromPixels(new Pixels(this.bild, this.seite, this.seite), 9729);
        this.bild = null;
    }

    public Texture textur()
    {
        return this.texture;
    }

    public void schliessen()
    {
        if (this.texture != null) { this.texture.delete(); this.texture = null; }
        if (this.bild != null) { MemoryUtil.memFree(this.bild); this.bild = null; }
        this.glyphen.clear();
    }
}
