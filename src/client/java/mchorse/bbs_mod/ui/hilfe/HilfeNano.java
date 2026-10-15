package mchorse.bbs_mod.ui.hilfe;

import com.mcstudio.nano.NanoDesign;
import com.mcstudio.nano.NanoFlaeche;
import com.mcstudio.nano.NanoStifte;
import org.lwjgl.nanovg.NVGColor;
import org.lwjgl.nanovg.NVGPaint;
import org.lwjgl.system.MemoryStack;

import static org.lwjgl.nanovg.NanoVG.*;

/** NanoVG pieces the help surfaces share: written-out buttons, wrapped text, the menu shadow. */
final class HilfeNano
{
    private HilfeNano()
    {}

    /** A 22 high button; primary is blue. Returns its right edge. */
    static float knopf(long vg, NanoFlaeche f, String id, String text, float x, float y, boolean primaer, float k, Runnable aktion)
    {
        float w = NanoStifte.textBreite(vg, NanoDesign.SANS, 11F * k, text) + 20F * k;
        float h = 22F * k;
        boolean ueber = f.istHover(id);
        int farbe = primaer ? (ueber ? 0x5A86C8 : NanoDesign.BLAU) : (ueber ? NanoDesign.WIDGET_HI : NanoDesign.WIDGET);

        NanoStifte.widget(vg, x, y, w, h, 4F * k, farbe, k);
        NanoStifte.textMitteInBox(vg, NanoDesign.SANS, 11F * k, x + w / 2F, y + h / 2F, NanoDesign.TXT_MAX, text);
        f.zone(id, x, y, w, h, aktion);

        return x + w;
    }

    /** The menu family's shadow: 0 10 34 rgba(0,0,0,.55). */
    static void schatten(long vg, float x, float y, float w, float h, float k)
    {
        try (MemoryStack s = MemoryStack.stackPush())
        {
            NVGPaint paint = NVGPaint.malloc(s);

            nvgBoxGradient(vg, x, y + 10F * k, w, h, 6F * k, 34F * k, NanoStifte.farbe(0x000000, 140, NVGColor.malloc(s)),
                NanoStifte.farbe(0x000000, 0, NVGColor.malloc(s)), paint);
            nvgBeginPath(vg);
            nvgRect(vg, x - 40F * k, y - 30F * k, w + 80F * k, h + 80F * k);
            nvgFillPaint(vg, paint);
            nvgFill(vg);
        }
    }

    /** How high {@link #text} will wrap this text. */
    static float textHoehe(long vg, float groesse, float breite, String text)
    {
        float[] grenzen = new float[4];

        schrift(vg, NanoDesign.SANS, groesse);
        nvgTextBoxBounds(vg, 0F, 0F, breite, text, grenzen);

        return grenzen[3] - grenzen[1];
    }

    /** Text wrapped to a width, top-left at x, y. */
    static void text(long vg, String schrift, float groesse, float x, float y, float breite, int rgb, String text)
    {
        try (MemoryStack s = MemoryStack.stackPush())
        {
            schrift(vg, schrift, groesse);
            nvgFillColor(vg, NanoStifte.farbe(rgb, 255, NVGColor.malloc(s)));
            nvgTextBox(vg, x, y, breite, text);
        }
    }

    /** The text cut to a width with an ellipsis. */
    static String kuerzen(long vg, String schrift, float groesse, String text, float breite)
    {
        if (NanoStifte.textBreite(vg, schrift, groesse, text) <= breite)
        {
            return text;
        }

        int n = text.length();

        while (n > 1 && NanoStifte.textBreite(vg, schrift, groesse, text.substring(0, n) + "…") > breite)
        {
            n--;
        }

        return text.substring(0, n) + "…";
    }

    /** m:ss, or h:mm:ss from an hour on. */
    static String zeit(double sekunden)
    {
        int s = (int) Math.max(0, sekunden);

        return s >= 3600 ? String.format("%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) : String.format("%d:%02d", s / 60, s % 60);
    }

    /** Under a minute "44 s", else rounded minutes "18 min". */
    static String dauer(int sekunden)
    {
        return sekunden < 60 ? sekunden + " s" : Math.round(sekunden / 60F) + " min";
    }

    /** Play triangle or pause bars, centred. */
    static void spielSymbol(long vg, float cx, float cy, float gr, boolean pause, int rgb)
    {
        if (pause)
        {
            NanoStifte.flaeche(vg, cx - gr * 0.4F, cy - gr / 2F, gr * 0.28F, gr, 0F, rgb);
            NanoStifte.flaeche(vg, cx + gr * 0.12F, cy - gr / 2F, gr * 0.28F, gr, 0F, rgb);
        }
        else
        {
            NanoStifte.dreieck(vg, cx - gr * 0.35F, cy - gr / 2F, cx - gr * 0.35F, cy + gr / 2F, cx + gr * 0.5F, cy, rgb);
        }
    }

    /** A clock (the "original BBS" plaque). */
    static void uhr(long vg, float cx, float cy, float gr, int rgb, float k)
    {
        NanoStifte.rahmen(vg, cx - gr / 2F, cy - gr / 2F, gr, gr, gr / 2F, 1.3F * k, rgb);
        NanoStifte.linie(vg, cx, cy, cx, cy - gr * 0.3F, 1.3F * k, rgb);
        NanoStifte.linie(vg, cx, cy, cx + gr * 0.22F, cy + gr * 0.12F, 1.3F * k, rgb);
    }

    /** A check mark (chapter seen). */
    static void haken(long vg, float cx, float cy, float gr, int rgb, float k)
    {
        NanoStifte.linie(vg, cx - gr * 0.4F, cy, cx - gr * 0.1F, cy + gr * 0.3F, 2F * k, rgb);
        NanoStifte.linie(vg, cx - gr * 0.1F, cy + gr * 0.3F, cx + gr * 0.45F, cy - gr * 0.35F, 2F * k, rgb);
    }

    /** ↗: leaves BBS (browser). */
    static void raus(long vg, float cx, float cy, float gr, int rgb, float k)
    {
        NanoStifte.linie(vg, cx - gr * 0.35F, cy + gr * 0.35F, cx + gr * 0.35F, cy - gr * 0.35F, 1.3F * k, rgb);
        NanoStifte.linie(vg, cx - gr * 0.05F, cy - gr * 0.35F, cx + gr * 0.35F, cy - gr * 0.35F, 1.3F * k, rgb);
        NanoStifte.linie(vg, cx + gr * 0.35F, cy - gr * 0.35F, cx + gr * 0.35F, cy + gr * 0.05F, 1.3F * k, rgb);
    }

    private static void schrift(long vg, String schrift, float groesse)
    {
        nvgFontFace(vg, schrift);
        nvgFontSize(vg, groesse);
        nvgTextLineHeight(vg, 1.3F);
        nvgTextAlign(vg, NVG_ALIGN_LEFT | NVG_ALIGN_TOP);
    }
}
