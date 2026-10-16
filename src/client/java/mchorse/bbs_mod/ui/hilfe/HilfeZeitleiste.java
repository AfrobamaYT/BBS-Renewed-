package mchorse.bbs_mod.ui.hilfe;

import com.mcstudio.nano.NanoDesign;
import com.mcstudio.nano.NanoFlaeche;
import com.mcstudio.nano.NanoStifte;

/**
 * Around the video, never on it (YouTube: nothing in front of the player): the bar above with the
 * "original BBS" plaque and the chapter chip, our chapter timeline under the picture, and the
 * transport that drives the player through its API (spec 5.2, revised).
 */
final class HilfeZeitleiste
{
    private int letztesKapitel = -1;
    private long kapitelSeit;

    /** All in GUI px relative to the window; k turns them into the surface's pixels. */
    void zeichnen(long vg, NanoFlaeche f, float k, HilfeSitzung s, float vx, float vy, float vw, float vh)
    {
        Guides.Guide g = s.guide;
        double zeit = s.kanal.video.equals(g.video()) ? s.kanal.zeit : 0D;
        int kapitel = g.kapitelBei(zeit);
        long jetzt = System.currentTimeMillis();

        if (kapitel != this.letztesKapitel)
        {
            this.kapitelSeit = this.letztesKapitel < 0 ? 0L : jetzt;
            this.letztesKapitel = kapitel;
        }

        /* Bar above: plaque left, chapter chip right — lit orange for 3.4 s when the chapter changes */
        float by = (vy - 28F) * k;
        String plakette = "ORIGINAL BBS";
        float pw = NanoStifte.textBreite(vg, NanoDesign.BOLD, 10F * k, plakette) + 34F * k;

        NanoStifte.flaeche(vg, vx * k, by, pw, 20F * k, 4F * k, NanoDesign.PANEL);
        HilfeNano.uhr(vg, vx * k + 13F * k, by + 10F * k, 11F * k, NanoDesign.ORANGE, k);
        NanoStifte.textInBox(vg, NanoDesign.BOLD, 10F * k, vx * k + 23F * k, by + 10F * k, NanoDesign.TXT, plakette);
        String chip = "Chapter " + (kapitel + 1) + " · " + g.kapitel().get(kapitel).titel();
        float cw = NanoStifte.textBreite(vg, NanoDesign.SANS, 11F * k, chip) + 16F * k;

        /* The note gives way to the chip, never runs under it */
        NanoStifte.textInBox(vg, NanoDesign.SANS, 11F * k, vx * k + pw + 8F * k, by + 10F * k, NanoDesign.TXT_DIM,
            HilfeNano.kuerzen(vg, NanoDesign.SANS, 11F * k, "Looks different from yours — works the same.", vw * k - pw - cw - 16F * k));
        boolean leuchtet = jetzt - this.kapitelSeit < 3400L;

        NanoStifte.flaeche(vg, (vx + vw) * k - cw, by, cw, 20F * k, 4F * k, leuchtet ? NanoDesign.ORANGE : NanoDesign.REGION);
        NanoStifte.textInBox(vg, NanoDesign.SANS, 11F * k, (vx + vw) * k - cw + 8F * k, by + 10F * k, leuchtet ? 0x151515 : NanoDesign.TXT_DIM, chip);

        this.zeitleiste(vg, f, k, s, vx, vy + vh + 7F, vw, zeit, kapitel);
        this.transport(vg, f, k, s, vx, vy + vh + 25F, vw, zeit, kapitel);
    }

    private void zeitleiste(long vg, NanoFlaeche f, float k, HilfeSitzung s, float x, float y, float w, double zeit, int kapitel)
    {
        Guides.Guide g = s.guide;
        int n = g.kapitel().size();
        float frei = w - (n - 1) * 2F;
        float sx = x;
        int kontext = s.kontextKapitel();
        float knopfX = x;

        for (int i = 0; i < n; i++)
        {
            int dauer = g.dauer(i);
            float sw = frei * dauer / g.laenge();
            float bar = y + 6.5F;
            int start = g.kapitel().get(i).sekunde();

            NanoStifte.flaeche(vg, sx * k, bar * k, sw * k, 5F * k, 1F * k, s.gesehen[i] > 0 ? 0x5B5B5B : 0x3A3A3A);

            if (zeit >= start)
            {
                float teil = (float) Math.min(1D, (zeit - start) / dauer);

                NanoStifte.flaeche(vg, sx * k, bar * k, sw * teil * k, 5F * k, 1F * k, NanoDesign.BLAU_HELL);

                if (i == kapitel)
                {
                    knopfX = sx + sw * teil;
                }
            }

            if (i == kontext)
            {
                NanoStifte.flaeche(vg, sx * k, (bar + 8F) * k, sw * k, 2F * k, 0F, NanoDesign.ORANGE);
            }

            final float segX = sx, segW = sw;
            final int segStart = start, segDauer = dauer;

            f.zone("zeit" + i, sx * k, y * k, sw * k, 18F * k, () ->
                s.kanal.springe(g.video(), segStart + Math.max(0F, Math.min(1F, (f.letzterKlickX() / k - segX) / segW)) * segDauer));
            sx += sw + 2F;
        }

        NanoStifte.kreis(vg, knopfX * k, (y + 9F) * k, 7.5F * k, 0x000000);
        NanoStifte.kreis(vg, knopfX * k, (y + 9F) * k, 5.5F * k, NanoDesign.TXT_MAX);
    }

    private void transport(long vg, NanoFlaeche f, float k, HilfeSitzung s, float x, float y, float w, double zeit, int kapitel)
    {
        Guides.Guide g = s.guide;
        float my = (y + 14F) * k;
        boolean spielt = s.spielt();

        knopf(vg, f, "spiel", x, y + 2F, 32F, k, true, () -> { if (spielt) s.kanal.pause(); else s.kanal.spielen(); });
        HilfeNano.spielSymbol(vg, (x + 16F) * k, my, 10F * k, spielt, NanoDesign.TXT_MAX);
        knopf(vg, f, "zurueck", x + 36F, y + 2F, 26F, k, false, () -> s.kanal.springe(g.video(), Math.max(0D, zeit - 10D)));
        NanoStifte.textMitteInBox(vg, NanoDesign.SANS, 10F * k, (x + 49F) * k, my, NanoDesign.TXT, "−10");
        knopf(vg, f, "vor", x + 66F, y + 2F, 26F, k, false, () -> s.kanal.springe(g.video(), zeit + 10D));
        NanoStifte.textMitteInBox(vg, NanoDesign.SANS, 10F * k, (x + 79F) * k, my, NanoDesign.TXT, "+10");

        String uhr = HilfeNano.zeit(zeit) + " / " + HilfeNano.zeit(g.laenge());
        float tx = (x + 102F) * k;

        NanoStifte.textInBox(vg, NanoDesign.SANS, 11F * k, tx, my, NanoDesign.TXT, uhr);
        tx += NanoStifte.textBreite(vg, NanoDesign.SANS, 11F * k, uhr) + 12F * k;
        NanoStifte.flaeche(vg, tx, my - 7F * k, Math.max(1F, k), 14F * k, 0F, NanoDesign.LINIE);
        tx += 12F * k;

        /* Right: speed segment and the way out to YouTube at the current second */
        float rx = (x + w) * k;

        rx -= 74F * k;
        knopf(vg, f, "youtube", rx / k, y + 2F, 74F, k, false, () -> Guides.imBrowser(g.video(), (int) zeit));
        NanoStifte.textInBox(vg, NanoDesign.SANS, 11F * k, rx + 8F * k, my, NanoDesign.TXT, "YouTube");
        HilfeNano.raus(vg, rx + 62F * k, my, 9F * k, NanoDesign.TXT, k);

        for (double tempo : new double[] {2D, 1.5D, 1D})
        {
            String text = (tempo == 1D ? "1" : tempo == 2D ? "2" : "1.5") + "×";

            rx -= 34F * k;
            knopf(vg, f, "tempo" + tempo, rx / k, y + 2F, 32F, k, s.tempo == tempo, () -> { s.tempo = tempo; s.kanal.tempo(tempo); });
            NanoStifte.textMitteInBox(vg, NanoDesign.SANS, 10.5F * k, rx + 16F * k, my, NanoDesign.TXT, text);
        }

        String label = s.kanal.zustand == 3 ? "Loading…" : "Chapter " + (kapitel + 1) + " · " + g.kapitel().get(kapitel).titel();

        NanoStifte.textInBox(vg, NanoDesign.SANS, 11F * k, tx, my, NanoDesign.TXT_DIM,
            HilfeNano.kuerzen(vg, NanoDesign.SANS, 11F * k, label, rx - tx - 10F * k));
    }

    private static void knopf(long vg, NanoFlaeche f, String id, float x, float y, float w, float k, boolean blau, Runnable aktion)
    {
        boolean ueber = f.istHover(id);
        int farbe = blau ? (ueber ? 0x5A86C8 : NanoDesign.BLAU) : (ueber ? NanoDesign.WIDGET_HI : NanoDesign.WIDGET);

        NanoStifte.widget(vg, x * k, y * k, w * k, 24F * k, 4F * k, farbe, k);
        f.zone(id, x * k, y * k, w * k, 24F * k, aktion);
    }
}
