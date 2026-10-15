package mchorse.bbs_mod.film.regie;

import org.lwjgl.nanovg.NVGColor;
import org.lwjgl.nanovg.NVGPaint;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import static org.lwjgl.nanovg.NanoVG.*;

/**
 * Zeichenhelfer der Regiehilfe ueber NanoVG: Formen mit Alpha, box-shadow als Box-Verlauf,
 * Schrift, die drei Typ-Symbole des Designs und das Streifenmuster der Fortschrittsbalken.
 * Haelt die NVG-Farbpuffer selbst; Farben als 0xRRGGBB plus Alpha.
 */
final class RegieStift
{
    static final String TITEL = "regie-titel";
    static final String ZIFFER = "regie-ziffer";
    static final String MONO = "regie-mono";
    static final int DUNKEL = 0x090B0F;
    static final int GRUND = 0x07090D;

    final NVGColor a = NVGColor.create();
    final NVGColor b = NVGColor.create();
    final NVGPaint farbe = NVGPaint.create();
    private long streifenKontext;
    private int streifen;

    NVGColor rgba(int rgb, float alpha, NVGColor ziel)
    {
        return ziel.r(((rgb >> 16) & 0xFF) / 255F).g(((rgb >> 8) & 0xFF) / 255F).b((rgb & 0xFF) / 255F).a(alpha);
    }

    /**
     * box-shadow als Box-Verlauf, um {@code dy} versetzt. Die Form selbst bleibt ausgespart
     * (wie bei CSS liegt ein aeusserer Schatten nie UNTER dem Element — sonst dunkelte er den
     * halbdurchsichtigen Grund ab).
     */
    void schatten(long vg, float x, float y, float w, float h, float r, float dy, float weich, int rgb, float alpha)
    {
        nvgBeginPath(vg);
        nvgRect(vg, x - weich, y + dy - weich, w + 2F * weich, h + 2F * weich);
        nvgRoundedRect(vg, x, y, w, h, r);
        nvgPathWinding(vg, NVG_HOLE);
        nvgBoxGradient(vg, x, y + dy, w, h, r + weich * 0.5F, weich, this.rgba(rgb, alpha, this.a), this.rgba(rgb, 0F, this.b), this.farbe);
        nvgFillPaint(vg, this.farbe);
        nvgFill(vg);
    }

    void rundeck(long vg, float x, float y, float w, float h, float r, int rgb, float alpha)
    {
        nvgBeginPath(vg);
        nvgRoundedRect(vg, x, y, w, h, r);
        nvgFillColor(vg, this.rgba(rgb, alpha, this.a));
        nvgFill(vg);
    }

    void ring(long vg, float x, float y, float w, float h, float r, float breite, int rgb, float alpha)
    {
        nvgBeginPath(vg);
        nvgRoundedRect(vg, x, y, w, h, Math.max(0F, r));
        nvgStrokeColor(vg, this.rgba(rgb, alpha, this.a));
        nvgStrokeWidth(vg, breite);
        nvgStroke(vg);
    }

    void kreis(long vg, float x, float y, float r, int rgb, float alpha)
    {
        nvgBeginPath(vg);
        nvgCircle(vg, x, y, r);
        nvgFillColor(vg, this.rgba(rgb, alpha, this.a));
        nvgFill(vg);
    }

    void schrift(long vg, String name, float groesse, float sperrung)
    {
        nvgFontFace(vg, name);
        nvgFontSize(vg, groesse);
        nvgTextLetterSpacing(vg, sperrung);
    }

    float breite(long vg, String name, float groesse, float sperrung, String text)
    {
        this.schrift(vg, name, groesse, sperrung);

        return nvgTextBounds(vg, 0F, 0F, text, (FloatBuffer) null);
    }

    /** Wie text-overflow: ellipsis bei max-width (Schrift muss gesetzt sein). */
    String kuerzen(long vg, String text, float platz)
    {
        if (nvgTextBounds(vg, 0F, 0F, text, (FloatBuffer) null) <= platz)
        {
            return text;
        }

        for (int ende = text.length() - 1; ende > 0; ende--)
        {
            String kurz = text.substring(0, ende) + "…";

            if (nvgTextBounds(vg, 0F, 0F, kurz, (FloatBuffer) null) <= platz)
            {
                return kurz;
            }
        }

        return "…";
    }

    /** Typ-Symbole des Designs (24er-Raster, Strich 1,9): Pin, Auge, Flagge. */
    void symbol(long vg, RegieAnsicht.Art art, float cx, float cy, float groesse, int c)
    {
        float e = groesse / 24F;

        nvgSave(vg);
        nvgTranslate(vg, cx - 12F * e, cy - 12F * e);
        nvgScale(vg, e, e);
        nvgBeginPath(vg);

        if (art == RegieAnsicht.Art.ORT)
        {
            /* M12 21s-6.2-5.7-6.2-11.1a6.2 6.2 0 0 1 12.4 0C18.2 15.3 12 21 12 21z + Kreis r2.3 */
            nvgMoveTo(vg, 12F, 21F);
            nvgBezierTo(vg, 12F, 21F, 5.8F, 15.3F, 5.8F, 9.9F);
            nvgArc(vg, 12F, 9.9F, 6.2F, (float) Math.PI, 0F, NVG_CW);
            nvgBezierTo(vg, 18.2F, 15.3F, 12F, 21F, 12F, 21F);
            nvgClosePath(vg);
            nvgCircle(vg, 12F, 9.9F, 2.3F);
        }
        else if (art == RegieAnsicht.Art.BLICK)
        {
            /* M2.5 12S6 5.6 12 5.6 21.5 12 21.5 12 18 18.4 12 18.4 2.5 12 2.5 12z + Kreis r3 */
            nvgMoveTo(vg, 2.5F, 12F);
            nvgBezierTo(vg, 2.5F, 12F, 6F, 5.6F, 12F, 5.6F);
            nvgBezierTo(vg, 18F, 5.6F, 21.5F, 12F, 21.5F, 12F);
            nvgBezierTo(vg, 21.5F, 12F, 18F, 18.4F, 12F, 18.4F);
            nvgBezierTo(vg, 6F, 18.4F, 2.5F, 12F, 2.5F, 12F);
            nvgClosePath(vg);
            nvgCircle(vg, 12F, 12F, 3F);
        }
        else
        {
            /* M5.5 21V3.8 und M5.5 4.2h11.3l-2.2 4.1 2.2 4.1H5.5 */
            nvgMoveTo(vg, 5.5F, 21F);
            nvgLineTo(vg, 5.5F, 3.8F);
            nvgMoveTo(vg, 5.5F, 4.2F);
            nvgLineTo(vg, 16.8F, 4.2F);
            nvgLineTo(vg, 14.6F, 8.3F);
            nvgLineTo(vg, 16.8F, 12.4F);
            nvgLineTo(vg, 5.5F, 12.4F);
        }

        nvgLineCap(vg, NVG_ROUND);
        nvgLineJoin(vg, NVG_ROUND);
        nvgStrokeColor(vg, this.rgba(c, 1F, this.a));
        nvgStrokeWidth(vg, 1.9F);
        nvgStroke(vg);
        nvgRestore(vg);
    }

    /** 12x12-Kachel: 7 px volle Farbe, 5 px auf 55 % abgedunkelt (wird mit der Markerfarbe getoent). */
    int streifen(long vg)
    {
        if (this.streifenKontext == vg && this.streifen != 0)
        {
            return this.streifen;
        }

        ByteBuffer pixel = MemoryUtil.memAlloc(12 * 12 * 4);

        try
        {
            for (int i = 0; i < 12 * 12; i++)
            {
                byte hell = (byte) (i % 12 < 7 ? 255 : 140);

                pixel.put(hell).put(hell).put(hell).put((byte) 255);
            }

            pixel.flip();
            this.streifen = nvgCreateImageRGBA(vg, 12, 12, NVG_IMAGE_REPEATX | NVG_IMAGE_REPEATY, pixel);
            this.streifenKontext = vg;

            if (this.streifen == 0)
            {
                throw new IllegalStateException("NanoVG did not create the stripe image");
            }

            return this.streifen;
        }
        finally
        {
            MemoryUtil.memFree(pixel);
        }
    }
}
