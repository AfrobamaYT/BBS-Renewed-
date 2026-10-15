package mchorse.bbs_mod.ui.hilfe;

import com.mcstudio.nano.NanoDesign;
import com.mcstudio.nano.NanoFlaeche;
import com.mcstudio.nano.NanoStifte;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.utils.Tutorials;

import java.util.Locale;

/**
 * A panel's head line with its short clip (spec section 6): micro title left, a clip chip right —
 * screen-with-play and the length; blue under the mouse. The chip opens the clip (on Discord
 * until the clips are on YouTube) and is left out while the clip has no link.
 */
public class UIHilfeClipKopf extends UIElement
{
    private final String titel;
    private final String clip;

    public UIHilfeClipKopf(String titel, String clip)
    {
        this.titel = titel.toUpperCase(Locale.ROOT);
        this.clip = clip;

        NanoFlaeche flaeche = new NanoFlaeche(this::zeichnen);

        flaeche.relative(this).w(1F).h(1F);
        this.add(flaeche);
        this.h(20);
    }

    private void zeichnen(long vg, int b, int h, float mausX, float mausY, NanoFlaeche f)
    {
        float k = Math.max(1F, f.skala());

        /* Bright: the side bar has no background of its own, a dim title vanished against the sky */
        NanoStifte.textInBox(vg, NanoDesign.BOLD, 9.5F * k, 2F * k, h / 2F, NanoDesign.TXT, this.titel);

        Guides.Kurz kurz = Guides.kurzvideos().stream().filter((c) -> c.id().equals(this.clip)).findFirst().orElse(null);

        if (kurz == null || Tutorials.link(this.clip) == null)
        {
            return;
        }

        String laenge = HilfeNano.zeit(kurz.laenge());
        float cw = 8F * k + 13F * k + 4F * k + NanoStifte.textBreite(vg, NanoDesign.SANS, 10.5F * k, laenge) + 8F * k;
        float cx = b - cw, cy = (h - 18F * k) / 2F;
        boolean ueber = f.istHover("clip");
        int schrift = ueber ? NanoDesign.TXT_MAX : NanoDesign.TXT;

        NanoStifte.widget(vg, cx, cy, cw, 18F * k, 4F * k, ueber ? NanoDesign.BLAU : NanoDesign.WIDGET, k);

        /* Screen with play */
        NanoStifte.rahmen(vg, cx + 8F * k, cy + 4.5F * k, 13F * k, 9F * k, 1.5F * k, Math.max(1F, k), schrift);
        HilfeNano.spielSymbol(vg, cx + 14.8F * k, cy + 9F * k, 4.5F * k, false, schrift);
        NanoStifte.textInBox(vg, NanoDesign.SANS, 10.5F * k, cx + 25F * k, cy + 9F * k, schrift, laenge);

        f.zone("clip", cx, cy, cw, 18F * k, () -> Tutorials.open(this.clip), "Watch the " + kurz.laenge() + "-second clip: " + kurz.titel(), "");
    }
}
