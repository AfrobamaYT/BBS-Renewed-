package mchorse.bbs_mod.ui.hilfe;

import com.mcstudio.nano.NanoDesign;
import com.mcstudio.nano.NanoFlaeche;
import com.mcstudio.nano.NanoStifte;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.vorschau.Miniaturschlange;
import mchorse.bbs_mod.ui.utils.Area;

import static org.lwjgl.nanovg.NanoVG.*;

/**
 * The first-run hint: a small card hanging from the Help label (Claude Design, variant A — it
 * teaches where help lives while it offers it). Never modal: no dimming, no focus, clicks next
 * to it reach the editor; only its three buttons take clicks. Whether it is owed: {@link HilfeErststart}.
 */
public class UIHilfeHinweis extends UIElement
{
    private static final float BREITE = 318F;
    private static final float RAND = 24F;

    private static final String TEXT = "McHorse, who wrote the original BBS, walks through everything in one video tutorial. Jump straight to the chapters you need.";
    private static final String DECKT = "Covers Basics · Intermediary · Advanced · Model blocks · Forms · Camera clips";
    private static final String INFO = "Recorded in the original BBS. Buttons look different here — everything works the same.";

    private final UIDashboard dashboard;
    private final NanoFlaeche flaeche;
    private final long beginn = System.currentTimeMillis() + 650L;
    private float hoehe = 210F;
    private float labelBreite = 40F;
    private long ende;
    private boolean fliegt;

    UIHilfeHinweis(UIDashboard dashboard)
    {
        this.dashboard = dashboard;
        this.flaeche = new NanoFlaeche(this::zeichnen);
        this.flaeche.relative(this).w(1F).h(1F);
        this.add(this.flaeche);
    }

    /** Fade out (260 ms), or for Later fly into the Help label (440 ms). */
    void schliessen(boolean fliegen)
    {
        if (this.ende == 0L)
        {
            this.fliegt = fliegen;
            this.ende = System.currentTimeMillis() + (fliegen ? 440L : 260L);
        }
    }

    private void antworten(String antwort)
    {
        if (this.ende == 0L)
        {
            HilfeErststart.beantwortet(antwort);
            this.schliessen(antwort.equals("later"));
        }
    }

    /** It places itself in render(): until then its area is empty, and an empty area is culled. */
    @Override
    public boolean canBeRendered(Area viewport)
    {
        return true;
    }

    @Override
    public void render(UIContext context)
    {
        float[] label = this.dashboard.getPanels().topbar.hilfeLabel();
        long jetzt = System.currentTimeMillis();

        if (label == null || jetzt < this.beginn)
        {
            return;
        }

        if (this.ende != 0L && jetzt >= this.ende)
        {
            if (this.fliegt)
            {
                HilfeErststart.punktSetzen(true);
            }

            this.removeFromParent();

            return;
        }

        /* Card left edge = Help label − 14, 6 under the 34 high topbar; room around it for the shadow */
        int x = Math.round(label[0] - 14F - RAND), y = Math.round(NanoDesign.H_TOPBAR + 6F - 12F);
        int h = Math.round(this.hoehe + 12F + RAND);

        this.labelBreite = label[2];

        if (this.area.x != x || this.area.y != y || this.area.h != h)
        {
            this.set(x, y, Math.round(BREITE + RAND * 2), h);
            this.resize();
        }

        /* Over the film tiles: their waiting models go first, as UIOverlay does */
        Miniaturschlange.abgeben(context, context.menu.width, context.menu.height);
        this.flaeche.neuMalen();
        super.render(context);
    }

    private void zeichnen(long vg, int b, int h, float mausX, float mausY, NanoFlaeche f)
    {
        float k = Math.max(1F, f.skala());
        long jetzt = System.currentTimeMillis();
        float ein = Math.min(1F, (jetzt - this.beginn) / 260F);
        float aus = this.ende == 0L ? 0F : 1F - Math.max(0F, (this.ende - jetzt) / (this.fliegt ? 440F : 260F));
        float cx = RAND * k, cy = 12F * k, w = BREITE * k;
        float spitzeX = cx + (14F + this.labelBreite / 2F) * k;

        nvgSave(vg);
        nvgGlobalAlpha(vg, ein * (1F - aus));

        if (this.fliegt)
        {
            /* Later: the card flies into the Help label, origin at its tip, shrinking to 4 % */
            float t = aus * aus * aus;
            float s = 1F - 0.96F * t;

            nvgTranslate(vg, spitzeX, cy - 30F * k * t);
            nvgScale(vg, s, s);
            nvgTranslate(vg, -spitzeX, 0F);
        }
        else
        {
            nvgTranslate(vg, 0F, -8F * k * (1F - ein));
        }

        float innen = w - 24F * k;
        float y = cy + 12F * k;

        /* Height first, so the shadow and frame fit what follows */
        float text = HilfeNano.textHoehe(vg, 11.5F * k, innen, TEXT);
        float deckt = HilfeNano.textHoehe(vg, 10.5F * k, innen, DECKT);
        float info = HilfeNano.textHoehe(vg, 10.5F * k, innen - 36F * k, INFO) + 14F * k;
        float kh = 12F * k + 16F * k + 9F * k + text + 9F * k + deckt + 9F * k + info + 9F * k + 22F * k + 11F * k;

        this.hoehe = kh / k;
        HilfeNano.schatten(vg, cx, cy, w, kh, k);
        NanoStifte.flaeche(vg, cx, cy, w, kh, 6F * k, NanoDesign.MENUE);
        NanoStifte.rahmen(vg, cx, cy, w, kh, 6F * k, Math.max(1F, k), 0x000000);

        /* The tip, 11 x 11 turned 45°, centred under the label */
        nvgSave(vg);
        nvgTranslate(vg, spitzeX, cy);
        nvgRotate(vg, (float) Math.PI / 4F);
        NanoStifte.flaeche(vg, -5.5F * k, -5.5F * k, 11F * k, 11F * k, 1F * k, NanoDesign.MENUE);
        nvgRestore(vg);

        /* Head: question mark in orange, title, facts on the right */
        float kx = cx + 12F * k;
        String fakten = "10 chapters · 2 h 07";

        NanoStifte.rahmen(vg, kx, y + 1F * k, 14F * k, 14F * k, 7F * k, 1.4F * k, NanoDesign.ORANGE);
        NanoStifte.textMitteInBox(vg, NanoDesign.BOLD, 10F * k, kx + 7F * k, y + 8F * k, NanoDesign.ORANGE, "?");
        NanoStifte.textInBox(vg, NanoDesign.BOLD, 13F * k, kx + 20F * k, y + 8F * k, NanoDesign.TXT_MAX, "New to BBS?");
        NanoStifte.textInBox(vg, NanoDesign.SANS, 10.5F * k, cx + w - 12F * k - NanoStifte.textBreite(vg, NanoDesign.SANS, 10.5F * k, fakten),
            y + 8F * k, NanoDesign.TXT_DIM, fakten);
        y += 16F * k + 9F * k;
        HilfeNano.text(vg, NanoDesign.SANS, 11.5F * k, kx, y, innen, NanoDesign.TXT_DIM, TEXT);
        y += text + 9F * k;
        HilfeNano.text(vg, NanoDesign.SANS, 10.5F * k, kx, y, innen, 0xBDBDBD, DECKT);
        y += deckt + 9F * k;

        /* Note box: recorded in the original BBS */
        NanoStifte.flaeche(vg, kx, y, innen, info, 4F * k, NanoDesign.REGION);
        NanoStifte.rahmen(vg, kx + 8F * k, y + 7F * k, 12F * k, 12F * k, 6F * k, 1.2F * k, NanoDesign.TXT_DIM);
        NanoStifte.textMitteInBox(vg, NanoDesign.BOLD, 8.5F * k, kx + 14F * k, y + 13F * k, NanoDesign.TXT_DIM, "i");
        HilfeNano.text(vg, NanoDesign.SANS, 10.5F * k, kx + 28F * k, y + 7F * k, innen - 36F * k, NanoDesign.TXT_DIM, INFO);
        y += info + 9F * k;

        /* Three equal ways, written out */
        float bx = HilfeNano.knopf(vg, f, "watch", "Watch tutorial", kx, y, true, k, () ->
        {
            this.antworten("watched");
            Guides.oeffnen(this.dashboard.context, Guides.alle().get(0), 0);
        });

        bx = HilfeNano.knopf(vg, f, "later", "Later", bx + 6F * k, y, false, k, () -> this.antworten("later"));
        HilfeNano.knopf(vg, f, "known", "I know BBS", bx + 6F * k, y, false, k, () -> this.antworten("known"));
        nvgRestore(vg);
    }
}
