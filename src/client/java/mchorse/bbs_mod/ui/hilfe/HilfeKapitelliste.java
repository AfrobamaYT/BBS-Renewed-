package mchorse.bbs_mod.ui.hilfe;

import com.mcstudio.nano.NanoDesign;
import com.mcstudio.nano.NanoFlaeche;
import com.mcstudio.nano.NanoStifte;

import java.util.function.Consumer;

/**
 * The guide's chapters on the right — current one blue, the one about the user's editor orange
 * with "YOUR EDITOR", seen ones with a progress line or a check — and under the video the "In this
 * version" bar: where the chapter's feature lives in BBS Renewed (spec 5.2).
 */
final class HilfeKapitelliste
{
    private static final int AKTUELL = 0x614772B3;
    private static final int KONTEXT = 0x21E8862D;

    private HilfeKapitelliste()
    {}

    /** With Show me on the right when the chapter's feature has a place on screen (spec section 7). */
    static void inDieserVersion(long vg, NanoFlaeche f, float k, HilfeSitzung s, float x, float y, float w, Consumer<String> zeigen)
    {
        Guides.Guide g = s.guide;
        int kapitel = g.kapitelBei(s.kanal.video.equals(g.video()) ? s.kanal.zeit : 0D);
        Guides.Kapitel c = g.kapitel().get(kapitel);
        String text = c.hier() == null ? "Works the same as in the video." : c.hier();
        String knopf = c.zeige() == null ? null : c.zeige().equals("shorts") ? "Open short clips" : "Show me";
        float kw = knopf == null ? 0F : NanoStifte.textBreite(vg, NanoDesign.SANS, 11F * k, knopf) / k + 20F + 12F;
        float th = HilfeNano.textHoehe(vg, 11.5F * k, (w - 40F - kw) * k, text);
        float h = Math.max(46F * k, 9F * k + 14F * k + th + 9F * k);

        NanoStifte.flaeche(vg, x * k, y * k, w * k, h, 6F * k, NanoDesign.PANEL);
        NanoStifte.kreis(vg, (x + 19F) * k, (y + 16F) * k, 4.5F * k, NanoDesign.ORANGE);
        NanoStifte.linie(vg, (x + 19F) * k, (y + 20F) * k, (x + 19F) * k, (y + 26F) * k, 1.5F * k, NanoDesign.ORANGE);
        NanoStifte.textInBox(vg, NanoDesign.BOLD, 9.5F * k, (x + 32F) * k, (y + 15F) * k, NanoDesign.TXT_DIM,
            "IN THIS VERSION · CHAPTER " + (kapitel + 1));
        HilfeNano.text(vg, NanoDesign.SANS, 11.5F * k, (x + 32F) * k, (y + 23F) * k, (w - 40F - kw) * k, NanoDesign.TXT, text);

        if (knopf != null)
        {
            HilfeNano.knopf(vg, f, "zeige", knopf, (x + w - kw) * k, y * k + h / 2F - 11F * k, false, k, () -> zeigen.accept(c.zeige()));
        }
    }

    static void liste(long vg, NanoFlaeche f, float k, HilfeSitzung s, float x, float y, float w, float h)
    {
        Guides.Guide g = s.guide;
        int n = g.kapitel().size();
        int kapitel = g.kapitelBei(s.kanal.video.equals(g.video()) ? s.kanal.zeit : 0D);
        int kontext = s.kontextKapitel();
        String rechts = n + " · " + HilfeNano.zeit(g.laenge()).replaceFirst("^(\\d+):(\\d+):\\d+$", "$1 h $2 min");

        NanoStifte.textInBox(vg, NanoDesign.BOLD, 9.5F * k, x * k, (y + 8F) * k, NanoDesign.TXT_DIM, "CHAPTERS");
        NanoStifte.textInBox(vg, NanoDesign.SANS, 10.5F * k, (x + w) * k - NanoStifte.textBreite(vg, NanoDesign.SANS, 10.5F * k, rechts),
            (y + 8F) * k, NanoDesign.TXT_DIM, rechts);

        float ly = y + 20F;
        float legende = kontext >= 0 ? 30F : 0F;
        float zeile = Math.min(40F, (h - 20F - legende - 6F - n) / n);

        NanoStifte.flaeche(vg, x * k, ly * k, w * k, (zeile * n + n + 5F) * k, 6F * k, NanoDesign.EDITOR);

        for (int i = 0; i < n; i++)
        {
            Guides.Kapitel c = g.kapitel().get(i);
            float zy = ly + 3F + i * (zeile + 1F);
            String id = "kapitel" + i;
            float rx = (x + 3F) * k, rw = (w - 6F) * k;

            if (i == kapitel)
            {
                NanoStifte.flaecheArgb(vg, rx, zy * k, rw, zeile * k, 4F * k, AKTUELL);
            }
            else if (i == kontext)
            {
                NanoStifte.flaecheArgb(vg, rx, zy * k, rw, zeile * k, 4F * k, KONTEXT);
            }
            else if (f.istHover(id))
            {
                NanoStifte.flaecheArgb(vg, rx, zy * k, rw, zeile * k, 4F * k, 0x0BFFFFFF);
            }

            float mitte = zy + zeile / 2F;
            boolean zwei = zeile >= 32F;

            NanoStifte.textInBox(vg, NanoDesign.SANS, 10.5F * k, (x + 12F) * k, mitte * k, NanoDesign.TXT_DIM, String.format("%02d", i + 1));
            NanoStifte.textInBox(vg, i == kapitel ? NanoDesign.BOLD : NanoDesign.SANS, 12F * k, (x + 34F) * k,
                (zwei ? mitte - 6F : mitte) * k, i == kapitel ? NanoDesign.TXT_MAX : NanoDesign.TXT, c.titel());

            if (zwei)
            {
                NanoStifte.textInBox(vg, NanoDesign.SANS, 10.5F * k, (x + 34F) * k, (mitte + 8F) * k, i == kapitel ? 0xC9D9F2 : NanoDesign.TXT_DIM,
                    HilfeNano.zeit(c.sekunde()) + " · " + HilfeNano.dauer(g.dauer(i)));
            }

            float ende = (x + w - 12F) * k;
            double anteil = s.gesehen[i] / g.dauer(i);

            if (i == kapitel)
            {
                HilfeNano.spielSymbol(vg, ende - 6F * k, mitte * k, 10F * k, s.spielt(), NanoDesign.TXT_MAX);
            }
            else if (anteil >= 0.97D)
            {
                HilfeNano.haken(vg, ende - 6F * k, mitte * k, 12F * k, NanoDesign.GRUEN, k);
            }
            else if (i == kontext)
            {
                String chip = "YOUR EDITOR";
                float cw = NanoStifte.textBreite(vg, NanoDesign.BOLD, 9F * k, chip) + 10F * k;

                NanoStifte.flaeche(vg, ende - cw, (mitte - 7F) * k, cw, 14F * k, 3F * k, NanoDesign.ORANGE);
                NanoStifte.textMitteInBox(vg, NanoDesign.BOLD, 9F * k, ende - cw / 2F, mitte * k, 0x151515, chip);
            }

            if (anteil > 0D && anteil < 0.97D)
            {
                NanoStifte.flaeche(vg, (x + 37F) * k, (zy + zeile - 2F) * k, (float) ((w - 49F) * anteil) * k, 2F * k, 0F, NanoDesign.BLAU_HELL);
            }

            f.zone(id, rx, zy * k, rw, zeile * k, () -> s.kanal.springe(g.video(), c.sekunde()));
        }

        if (kontext >= 0)
        {
            float gy = ly + zeile * n + n + 14F;
            String ort = switch (String.valueOf(HilfeSitzung.thema()))
            {
                case "camera" -> "timeline set to Camera clips";
                case "replays" -> "timeline set to Replays";
                case "model_blocks" -> "the Blocks workspace";
                case "forms" -> "the Morphing workspace";
                default -> "the editor you have open";
            };

            NanoStifte.flaeche(vg, x * k, (gy - 4F) * k, 8F * k, 8F * k, 1F * k, NanoDesign.ORANGE);
            HilfeNano.text(vg, NanoDesign.SANS, 10.5F * k, (x + 14F) * k, (gy - 7F) * k, (w - 14F) * k, NanoDesign.TXT_DIM,
                "Your editor: " + ort + " — chapter " + (kontext + 1) + ", " + g.kapitel().get(kontext).titel());
        }
    }
}
