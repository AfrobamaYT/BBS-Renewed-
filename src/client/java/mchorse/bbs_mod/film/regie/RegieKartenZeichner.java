package mchorse.bbs_mod.film.regie;

import java.util.Locale;

import static org.lwjgl.nanovg.NanoVG.*;

/**
 * Zeichnet eine Weltkarte mit Stiel und Bodenpunkt — Werte aus dem Design-CSS (.rg-wl-*),
 * in CSS-Pixeln mal Kartenskala.
 */
final class RegieKartenZeichner
{
    private final RegieStift stift;

    RegieKartenZeichner(RegieStift stift)
    {
        this.stift = stift;
    }

    void karte(long vg, RegieKarte karte, float zeit)
    {
        RegieStift st = this.stift;
        float s = karte.skala;
        float x = karte.x;
        float y = karte.y;
        float w = karte.breite;
        float h = karte.hoehe;
        float r = 14F * s;
        int c = karte.farbe & 0xFFFFFF;
        boolean aktiv = karte.phase == RegieAnsicht.Phase.AKTIV;
        boolean start = karte.art == RegieAnsicht.Art.START;

        nvgSave(vg);
        nvgGlobalAlpha(vg, (karte.phase == RegieAnsicht.Phase.ERLEDIGT ? 0.55F : 1F) * (karte.verdeckt ? 0.78F : 1F));

        /* box-shadow: 0 12px 34px rgba(0,0,0,.42), aktiv/ok dazu das Leuchten in Markerfarbe */
        st.schatten(vg, x, y, w, h, r, 12F * s, 34F * s, 0x000000, 0.42F);

        if (aktiv || karte.ok)
        {
            st.schatten(vg, x, y, w, h, r, 0F, (aktiv ? 30F : 22F) * s, c, aktiv ? 0.45F : 0.35F);
        }

        st.rundeck(vg, x, y, w, h, r, RegieStift.GRUND, karte.verdeckt ? 0.6F : 0.84F);

        /* Glanz: linear-gradient(180deg, rgba(255,255,255,.055), transparent 46%) */
        nvgBeginPath(vg);
        nvgRoundedRect(vg, x, y, w, h, r);
        nvgLinearGradient(vg, x, y, x, y + h * 0.46F, st.rgba(0xFFFFFF, 0.055F, st.a), st.rgba(0xFFFFFF, 0F, st.b), st.farbe);
        nvgFillPaint(vg, st.farbe);
        nvgFill(vg);

        st.ring(vg, x + 0.5F * s, y + 0.5F * s, w - s, h - s, r - 0.5F * s, s, 0xFFFFFF, 0.09F);

        if (aktiv || karte.ok)
        {
            st.ring(vg, x + s, y + s, w - 2F * s, h - 2F * s, r - s, 2F * s, c, 1F);
        }

        /* Badge mit Ziffer und Typ-Symbol; aktiv pulsiert es (1 s, bis 107 %) */
        float bs = karte.badge * s;
        float bx = x + karte.padLinks * s;
        float by = y + (h - bs) / 2F;
        float puls = aktiv ? 1F + 0.07F * (0.5F - 0.5F * (float) Math.cos(zeit * 2D * Math.PI)) : 1F;
        float is = 20F * s;
        float ie = bs / 2F - 14F * s;

        nvgSave(vg);
        nvgTranslate(vg, bx + bs / 2F, by + bs / 2F);
        nvgScale(vg, puls, puls);
        st.rundeck(vg, -bs / 2F, -bs / 2F, bs, bs, (karte.badge >= 44F ? 12F : 9F) * s, c, 1F);
        st.schrift(vg, RegieStift.ZIFFER, karte.badgeSchrift * s, 0F);
        nvgTextAlign(vg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
        nvgFillColor(vg, st.rgba(RegieStift.DUNKEL, 1F, st.a));
        nvgText(vg, 0F, 0F, karte.nummer);
        st.rundeck(vg, ie, ie, is, is, 7F * s, RegieStift.DUNKEL, 1F);
        st.ring(vg, ie - 0.75F * s, ie - 0.75F * s, is + 1.5F * s, is + 1.5F * s, 7.75F * s, 1.5F * s, c, 1F);
        st.symbol(vg, karte.art, ie + is / 2F, ie + is / 2F, 12F * s, c);
        nvgRestore(vg);

        /* Hauptspalte: Titel, Metazeile, Balken — senkrecht mittig wie align-items:center */
        float hx = bx + bs + 12F * s;
        float hr = x + w - karte.padRechts * s;
        float hy = y + (h - karte.hauptHoehe() * s) / 2F;
        float titel = karte.titelGroesse * s;

        st.schrift(vg, RegieStift.TITEL, titel, 0.005F * titel);
        nvgTextAlign(vg, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        nvgFillColor(vg, st.rgba(0xFFFFFF, 1F, st.a));
        nvgText(vg, hx, hy + titel * 0.54F, st.kuerzen(vg, karte.titel, 300F * s));
        hy += titel * 1.08F;

        if (karte.meta)
        {
            float meta = 12.5F * s;
            float mitte = hy + 4F * s + meta / 2F;

            st.schrift(vg, RegieStift.TITEL, meta, 0.07F * meta);
            nvgTextAlign(vg, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
            nvgFillColor(vg, st.rgba(start ? 0xFFFFFF : c, start ? 0.7F : 1F, st.a));
            nvgText(vg, hx, mitte, karte.status.toUpperCase(Locale.ROOT));

            if (karte.fensterZeigen)
            {
                st.schrift(vg, RegieStift.MONO, 11.5F * s, 0F);
                nvgTextAlign(vg, NVG_ALIGN_RIGHT | NVG_ALIGN_MIDDLE);
                nvgFillColor(vg, st.rgba(0xFFFFFF, 0.55F, st.a));
                nvgText(vg, hr, mitte, karte.fenster);
            }

            hy += 16.5F * s;
        }

        if (karte.balken)
        {
            this.balken(vg, karte, hx, hy + karte.balkenAbstand * s, hr - hx, karte.balkenHoehe * s, c, zeit);
        }

        /* Stiel 4 px, Verlauf Markerfarbe -> 10 %; Bodenpunkt 10 px mit 3 px Ring (nicht bei Blick) */
        float unten = y + h;
        float stiel = karte.stiel + s;

        if (stiel > 0F)
        {
            nvgBeginPath(vg);
            nvgRoundedRect(vg, karte.px - 2F * s, unten - s, 4F * s, stiel, 2F * s);
            nvgLinearGradient(vg, 0F, unten, 0F, unten + stiel, st.rgba(c, 1F, st.a), st.rgba(c, 0.1F, st.b), st.farbe);
            nvgFillPaint(vg, st.farbe);
            nvgFill(vg);
        }

        if (karte.art != RegieAnsicht.Art.BLICK)
        {
            st.kreis(vg, karte.px, karte.py, 8F * s, RegieStift.GRUND, 0.6F);
            st.kreis(vg, karte.px, karte.py, 5F * s, c, 1F);
        }

        nvgRestore(vg);
    }

    /** Fortschritt: bevorstehend laufende Streifen, aktiv mit wanderndem Glanz. */
    private void balken(long vg, RegieKarte karte, float x, float y, float w, float h, int c, float zeit)
    {
        RegieStift st = this.stift;
        float r = Math.min(5F * karte.skala, h / 2F);
        float fw = w * Math.max(0F, Math.min(1F, karte.fortschritt));

        st.rundeck(vg, x, y, w, h, r, 0xFFFFFF, 0.12F);

        if (fw <= 0.5F)
        {
            return;
        }

        nvgBeginPath(vg);
        nvgRoundedRect(vg, x, y, fw, h, Math.min(r, fw / 2F));

        if (karte.phase == RegieAnsicht.Phase.BEVOR)
        {
            /* repeating-linear-gradient(-55deg, c 0 7px, c*55% 7px 12px), laeuft 28 px je 1,1 s */
            float periode = 12F * karte.skala;
            float lauf = (zeit / 1.1F * 28F * karte.skala) % (periode / (float) Math.cos(Math.toRadians(35D)));

            nvgImagePattern(vg, x - lauf, y, periode, periode, (float) Math.toRadians(35D), st.streifen(vg), 1F, st.farbe);
            st.rgba(c, 1F, st.farbe.innerColor());
            st.rgba(c, 1F, st.farbe.outerColor());
            nvgFillPaint(vg, st.farbe);
        }
        else
        {
            nvgFillColor(vg, st.rgba(c, 1F, st.a));
        }

        nvgFill(vg);

        if (karte.phase != RegieAnsicht.Phase.AKTIV)
        {
            return;
        }

        /* Glanz 60 px breit, laeuft in 1,6 s von -60 bis Breite+60, Deckung .85 */
        float g = 30F * karte.skala;
        float mitte = -2F * g + ((zeit % 1.6F) / 1.6F) * (fw + 4F * g);

        for (int seite = 0; seite < 2; seite++)
        {
            float von = seite == 0 ? mitte - g : mitte;

            nvgSave(vg);
            nvgScissor(vg, von, y, g, h);
            nvgBeginPath(vg);
            nvgRoundedRect(vg, x, y, fw, h, Math.min(r, fw / 2F));
            nvgLinearGradient(vg, von, 0F, von + g, 0F, st.rgba(0xFFFFFF, seite == 0 ? 0F : 0.47F, st.a),
                st.rgba(0xFFFFFF, seite == 0 ? 0.47F : 0F, st.b), st.farbe);
            nvgFillPaint(vg, st.farbe);
            nvgFill(vg);
            nvgRestore(vg);
        }
    }
}
