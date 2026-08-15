package mchorse.bbs_mod.ui.framework.elements.utils.schrift;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.Minecraft;
import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.freetype.FT_Bitmap;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FT_GlyphSlot;
import org.lwjgl.util.freetype.FreeType;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Vektor-Text fuer die BBS-Oberflaeche (Programm P1): FreeType — dieselbe Bibliothek,
 * mit der Blender seinen Text setzt, und sie liegt der 26.2-JVM bereits bei — rastert
 * die Hausschrift je EFFEKTIVER Pixelgroesse in einen {@link VektorGlyphAtlas}.
 * Gemalt wird ueber den normalen Batcher-Quad-Weg, gemessen ueber dieselbe Instanz —
 * Mass und Bild kommen damit aus einer Quelle.
 *
 * <p>⛔ Nur vom Render-Thread benutzen (GL-Upload, ein FT_Face). Statics sind lazy und
 * null-gewachtet — ein Hotswap laesst neue Statics null (belegter Absturz, #65).</p>
 */
public final class VektorSchrift
{
    /** MC-Zeilenrhythmus: 9 GUI-Einheiten Zeile, Grundlinie bei +7 wie das Bitmap-Font. */
    private static final int BASIS = 9;
    private static final int GRUNDLINIE = 7;

    private static VektorSchrift instanz;
    private static boolean kaputt;

    private long bibliothek;
    private FT_Face gesicht;
    private ByteBuffer schriftBytes;
    private Map<Integer, VektorGlyphAtlas> atlanten = new HashMap<>();

    /** Zeichnet dieser Text vektorisiert? Farbcodes (§) versteht der Pfad selbst. */
    public static boolean zeichnet(String text)
    {
        return BBSSettings.fontVector != null && BBSSettings.fontVector.get()
            && text != null && holen() != null;
    }

    public static VektorSchrift holen()
    {
        if (kaputt)
        {
            return null;
        }

        if (instanz == null)
        {
            try
            {
                instanz = new VektorSchrift();
            }
            catch (Throwable t)
            {
                kaputt = true;
                System.err.println("[vektorschrift] Start fehlgeschlagen — es bleibt beim Bitmap-Font: " + t);
            }
        }

        return instanz;
    }

    private VektorSchrift() throws Exception
    {
        byte[] bytes;

        /* Hausschrift: Inter (OFL, Lizenz liegt daneben) — Variable-Font, dessen
         * Default-Instanz Regular ist; weitere Schnitte kommen spaeter ueber die Achsen. */
        try (InputStream stream = BBSMod.getProvider().getAsset(Link.assets("fonts/inter-variable.ttf")))
        {
            bytes = stream.readAllBytes();
        }

        /* FreeType haelt den Zeiger auf diesen Puffer, solange das Gesicht lebt —
         * er darf nie freigegeben oder vom GC bewegt werden (direkter Speicher, Feld). */
        this.schriftBytes = MemoryUtil.memAlloc(bytes.length).put(bytes).flip();

        PointerBuffer zeiger = BufferUtils.createPointerBuffer(1);
        int fehler = FreeType.FT_Init_FreeType(zeiger);

        if (fehler != 0)
        {
            throw new IllegalStateException("FT_Init_FreeType: " + fehler);
        }

        this.bibliothek = zeiger.get(0);
        fehler = FreeType.FT_New_Memory_Face(this.bibliothek, this.schriftBytes, 0, zeiger);

        if (fehler != 0)
        {
            throw new IllegalStateException("FT_New_Memory_Face: " + fehler);
        }

        this.gesicht = FT_Face.create(zeiger.get(0));
    }

    private float skala()
    {
        return (float) Minecraft.getInstance().getWindow().getGuiScale();
    }

    private VektorGlyphAtlas atlas()
    {
        int px = Math.max(6, Math.round(BASIS * this.skala()));

        return this.atlanten.computeIfAbsent(px, this::rastern);
    }

    private VektorGlyphAtlas rastern(int px)
    {
        VektorGlyphAtlas atlas = new VektorGlyphAtlas(px);

        FreeType.FT_Set_Pixel_Sizes(this.gesicht, 0, px);

        for (int cp = 0x20; cp <= 0x20AC; cp++)
        {
            /* Druckbares Latein + Latin-1 + die gaengige Typographie — mehr braucht die
             * UI heute nicht, und ein fixer Satz heisst: nie ein Upload im Betrieb. */
            boolean gewollt = (cp <= 0x7E) || (cp >= 0xA0 && cp <= 0xFF)
                || cp == 0x2013 || cp == 0x2014 || cp == 0x2018 || cp == 0x2019
                || cp == 0x201A || cp == 0x201C || cp == 0x201D || cp == 0x201E
                || cp == 0x2026 || cp == 0x20AC;

            if (!gewollt || FreeType.FT_Load_Char(this.gesicht, cp, FreeType.FT_LOAD_RENDER) != 0)
            {
                continue;
            }

            FT_GlyphSlot schlitz = this.gesicht.glyph();
            FT_Bitmap bitmap = schlitz.bitmap();
            int spalten = bitmap.width();
            int zeilen = bitmap.rows();
            int pitch = Math.abs(bitmap.pitch());
            ByteBuffer quelle = zeilen > 0 && pitch > 0 ? bitmap.buffer(pitch * zeilen) : null;
            float vorschub = schlitz.advance().x() / 64F;

            if (quelle == null)
            {
                /* Leerzeichen & Co: kein Bild, aber ein Vorschub. */
                atlas.einlegen(cp, MemoryUtil.memCalloc(1), 0, 0, 1, 0, 0, vorschub);

                continue;
            }

            atlas.einlegen(cp, quelle, spalten, zeilen, pitch, schlitz.bitmap_left(), schlitz.bitmap_top(), vorschub);
        }

        atlas.hochladen();

        return atlas;
    }

    /** Textbreite in GUI-Einheiten — dieselbe Rechnung, mit der {@link #zeichne} malt. */
    public int breite(String text)
    {
        VektorGlyphAtlas atlas = this.atlas();
        float summe = 0F;

        for (int i = 0; i < text.length(); )
        {
            int cp = text.codePointAt(i);

            if (cp == '§' && i + 1 < text.length())
            {
                i += 2;

                continue;
            }

            VektorGlyphAtlas.Glyph glyph = atlas.holen(cp);

            summe += glyph != null ? glyph.advance() : atlas.pixelGroesse / 3F;
            i += Character.charCount(cp);
        }

        return Math.round(summe / this.skala());
    }

    /** Zeilenumbruch mit den echten Vektor-Breiten (Stil-Vortrag siehe VektorStil). */
    public java.util.List<String> umbruch(String text, int breite)
    {
        return VektorStil.umbruch(text, breite, this::breite);
    }

    public void zeichne(Batcher2D batcher, String text, float x, float y, int color, boolean schatten)
    {
        if (schatten)
        {
            this.durchgang(batcher, text, x + 1F, y + 1F, color, true);
        }

        this.durchgang(batcher, text, x, y, color, false);
    }

    private void durchgang(Batcher2D batcher, String text, float x, float y, int basis, boolean dunkeln)
    {
        VektorGlyphAtlas atlas = this.atlas();
        float skala = this.skala();
        float stift = x;
        float grundlinie = y + GRUNDLINIE;
        int farbe = basis;
        GlyphNetz netz = new GlyphNetz();

        for (int i = 0; i < text.length(); )
        {
            int cp = text.codePointAt(i);

            if (cp == '§' && i + 1 < text.length())
            {
                int neu = VektorStil.farbe(text.charAt(i + 1), basis, farbe);

                farbe = neu == VektorStil.BEHALTEN ? farbe : neu;
                i += 2;

                continue;
            }

            VektorGlyphAtlas.Glyph glyph = atlas.holen(cp);

            if (glyph == null)
            {
                stift += atlas.pixelGroesse / 3F / skala;
                i += Character.charCount(cp);

                continue;
            }

            if (glyph.w() > 0)
            {
                float gx = stift + glyph.bearingX() / skala;
                float gy = grundlinie - glyph.bearingY() / skala;

                netz.lege(gx, gy, glyph.w() / skala, glyph.h() / skala,
                    glyph.u(), glyph.v(), glyph.w(), glyph.h(),
                    dunkeln ? VektorStil.dunkeln(farbe) : farbe);
            }

            stift += glyph.advance() / skala;
            i += Character.charCount(cp);
        }

        netz.einreichen(batcher, atlas.textur(), atlas.seite);
    }
}
