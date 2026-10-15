package mchorse.bbs_mod.film.regie;

import static org.lwjgl.nanovg.NanoVG.*;

/**
 * Zeichnet Randpfeile (.rg-edge), Wege-Chips (.rg-leg) und Blick-Fadenkreuze (.rg-ret) —
 * Werte aus dem Design-CSS, in CSS-Pixeln mal {@code k}.
 */
final class RegieMarkenZeichner
{
    private static final int OK = 0x7EF0B5;
    private static final int WARN = 0xFFC53D;
    private static final int SCHLECHT = 0xFF6B5E;
    private static final int GEDAEMPFT = 0xA3ABBB;

    private final RegieStift stift;

    RegieMarkenZeichner(RegieStift stift)
    {
        this.stift = stift;
    }

    void pfeil(long vg, RegiePfeil pfeil, float zeit, float k)
    {
        RegieStift st = this.stift;
        int c = pfeil.farbe & 0xFFFFFF;
        float puls = pfeil.aktiv ? 1F + 0.07F * (0.5F - 0.5F * (float) Math.cos(zeit * 2D * Math.PI)) : 1F;
        float b = 34F * k;

        nvgSave(vg);
        nvgTranslate(vg, pfeil.x, pfeil.y);

        /* polygon(50% 4%, 92% 72%, 50% 56%, 8% 72%) im 48er-Feld, 30 px nach aussen gedreht */
        nvgSave(vg);
        nvgRotate(vg, pfeil.winkel);
        nvgTranslate(vg, 0F, -30F * k);
        nvgBeginPath(vg);
        nvgMoveTo(vg, 0F, -22.08F * k);
        nvgLineTo(vg, 20.16F * k, 10.56F * k);
        nvgLineTo(vg, 0F, 2.88F * k);
        nvgLineTo(vg, -20.16F * k, 10.56F * k);
        nvgClosePath(vg);
        nvgStrokeColor(vg, st.rgba(0x000000, 0.6F, st.a));
        nvgStrokeWidth(vg, 2F * k);
        nvgStroke(vg);
        nvgFillColor(vg, st.rgba(c, 1F, st.a));
        nvgFill(vg);
        nvgRestore(vg);

        nvgScale(vg, puls, puls);
        st.rundeck(vg, -b / 2F, -b / 2F, b, b, 10F * k, pfeil.aktiv ? c : RegieStift.GRUND, pfeil.aktiv ? 1F : 0.88F);
        st.ring(vg, -b / 2F - k, -b / 2F - k, b + 2F * k, b + 2F * k, 11F * k, 2F * k, c, 1F);
        st.schrift(vg, RegieStift.ZIFFER, 17F * k, 0F);
        nvgTextAlign(vg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
        nvgFillColor(vg, st.rgba(pfeil.aktiv ? RegieStift.DUNKEL : c, 1F, st.a));
        nvgText(vg, 0F, 0F, pfeil.nummer);
        nvgRestore(vg);
        this.zeitSchild(vg, pfeil, b, k);
    }

    /**
     * Countdown neben dem Randpfeil, aufrecht und auf der Bildseite des Abzeichens (der Pfeil zeigt
     * nach aussen, das Schild nach innen) -- so rutscht es nie ueber den Bildrand. Gestaltung wie die
     * Wege-Chips: dunkler Grund, feiner Ring, Zahl in der Mono-Schrift.
     */
    private void zeitSchild(long vg, RegiePfeil pfeil, float b, float k)
    {
        if (pfeil.zeit == null)
        {
            return;
        }

        RegieStift st = this.stift;
        float t = st.breite(vg, RegieStift.MONO, 12F * k, 0F, pfeil.zeit);
        float w = t + 14F * k;
        float h = 20F * k;
        float abstand = b / 2F + h / 2F + 8F * k;
        float x = pfeil.x - (float) Math.sin(pfeil.winkel) * abstand;
        float y = pfeil.y + (float) Math.cos(pfeil.winkel) * abstand;
        int c = pfeil.farbe & 0xFFFFFF;

        st.rundeck(vg, x - w / 2F, y - h / 2F, w, h, 7F * k, RegieStift.GRUND, 0.86F);
        st.ring(vg, x - w / 2F + 0.5F * k, y - h / 2F + 0.5F * k, w - k, h - k, 6.5F * k, k, pfeil.aktiv ? c : 0xFFFFFF, pfeil.aktiv ? 0.9F : 0.1F);
        st.schrift(vg, RegieStift.MONO, 12F * k, 0F);
        nvgTextAlign(vg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
        nvgFillColor(vg, st.rgba(pfeil.aktiv ? c : 0xF2F5FA, 1F, st.a));
        nvgText(vg, x, y, pfeil.zeit);
    }

    void chip(long vg, RegieWeg chip, float k)
    {
        RegieStift st = this.stift;
        float d = st.breite(vg, RegieStift.TITEL, 12F * k, 0F, chip.distanz);
        float t = st.breite(vg, RegieStift.MONO, 11.5F * k, 0F, chip.zeit);
        float s = st.breite(vg, RegieStift.TITEL, 12F * k, 0F, chip.status);
        float w = (8F + 9F + 7F + 7F + 7F + 10F) * k + d + t + s;
        float h = 26F * k;
        float x = chip.x - w / 2F;
        float y = chip.y - h / 2F;
        int status = chip.urteil == RegieWeg.Urteil.MACHBAR ? OK : chip.urteil == RegieWeg.Urteil.KNAPP ? WARN : SCHLECHT;

        st.rundeck(vg, x, y, w, h, 9F * k, RegieStift.GRUND, 0.86F);
        st.ring(vg, x + 0.5F * k, y + 0.5F * k, w - k, h - k, 8.5F * k, k, 0xFFFFFF, 0.1F);
        x += 8F * k;
        st.rundeck(vg, x, chip.y - 4.5F * k, 9F * k, 9F * k, 3F * k, chip.farbe & 0xFFFFFF, 1F);
        x += 16F * k;
        nvgTextAlign(vg, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        st.schrift(vg, RegieStift.TITEL, 12F * k, 0F);
        nvgFillColor(vg, st.rgba(0xECEFF4, 1F, st.a));
        nvgText(vg, x, chip.y, chip.distanz);
        x += d + 7F * k;
        st.schrift(vg, RegieStift.MONO, 11.5F * k, 0F);
        nvgFillColor(vg, st.rgba(GEDAEMPFT, 1F, st.a));
        nvgText(vg, x, chip.y, chip.zeit);
        x += t + 7F * k;
        st.schrift(vg, RegieStift.TITEL, 12F * k, 0F);
        nvgFillColor(vg, st.rgba(status, 1F, st.a));
        nvgText(vg, x, chip.y, chip.status);
    }

    void kreuz(long vg, RegieKreuz kreuz, float zeit, float k)
    {
        RegieStift st = this.stift;
        int c = kreuz.farbe & 0xFFFFFF;
        boolean aktiv = kreuz.phase == RegieAnsicht.Phase.AKTIV;
        float atmen = aktiv ? 1F + 0.06F * (0.5F - 0.5F * (float) Math.cos(zeit / 1.2F * 2D * Math.PI)) : 1F;
        float g = kreuz.groesse * atmen;
        float laenge = g * (kreuz.fang ? 0.34F : 0.26F);
        float strich = (kreuz.fang ? 5F : 4F) * k;
        float x0 = kreuz.x - g / 2F + strich / 2F;
        float y0 = kreuz.y - g / 2F + strich / 2F;
        float x1 = kreuz.x + g / 2F - strich / 2F;
        float y1 = kreuz.y + g / 2F - strich / 2F;
        float r = 9F * k;

        nvgSave(vg);
        nvgGlobalAlpha(vg, kreuz.phase == RegieAnsicht.Phase.ERLEDIGT ? 0.3F : kreuz.phase == RegieAnsicht.Phase.BEVOR ? 0.55F : 1F);

        /* drop-shadow(0 0 6px Farbe 60 %), drop-shadow(0 0 1px #000), dann die Farbe */
        for (int lage = 0; lage < 3; lage++)
        {
            nvgBeginPath(vg);
            ecke(vg, x0, y0 + laenge, x0, y0, x0 + laenge, y0, r);
            ecke(vg, x1 - laenge, y0, x1, y0, x1, y0 + laenge, r);
            ecke(vg, x1, y1 - laenge, x1, y1, x1 - laenge, y1, r);
            ecke(vg, x0 + laenge, y1, x0, y1, x0, y1 - laenge, r);
            nvgLineCap(vg, NVG_BUTT);
            nvgLineJoin(vg, NVG_ROUND);
            nvgStrokeColor(vg, lage == 0 ? st.rgba(c, 0.25F, st.a) : lage == 1 ? st.rgba(0x000000, 0.7F, st.a) : st.rgba(c, 1F, st.a));
            nvgStrokeWidth(vg, lage == 0 ? strich + 8F * k : lage == 1 ? strich + 2F * k : strich);
            nvgStroke(vg);
        }

        if (kreuz.fang)
        {
            /* Schloss-Kreis oben rechts mit Haken, wenn der Blick sitzt */
            float lx = kreuz.x + g / 2F;
            float ly = kreuz.y - g / 2F;

            st.schatten(vg, lx - 14F * k, ly - 14F * k, 28F * k, 28F * k, 14F * k, 0F, 16F * k, c, 0.8F);
            st.kreis(vg, lx, ly, 17F * k, RegieStift.GRUND, 0.7F);
            st.kreis(vg, lx, ly, 14F * k, c, 1F);
            nvgBeginPath(vg);
            nvgMoveTo(vg, lx - 5.5F * k, ly + 0.5F * k);
            nvgLineTo(vg, lx - 1.5F * k, ly + 4.5F * k);
            nvgLineTo(vg, lx + 6F * k, ly - 4F * k);
            nvgLineCap(vg, NVG_ROUND);
            nvgStrokeColor(vg, st.rgba(RegieStift.DUNKEL, 1F, st.a));
            nvgStrokeWidth(vg, 2.4F * k);
            nvgStroke(vg);
        }

        nvgRestore(vg);
    }

    private static void ecke(long vg, float ax, float ay, float bx, float by, float cx, float cy, float r)
    {
        nvgMoveTo(vg, ax, ay);
        nvgArcTo(vg, bx, by, cx, cy, r);
        nvgLineTo(vg, cx, cy);
    }
}
