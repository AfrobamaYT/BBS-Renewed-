package mchorse.bbs_mod.ui.hilfe;

import com.mcstudio.nano.NanoDesign;
import com.mcstudio.nano.NanoFlaeche;
import com.mcstudio.nano.NanoStifte;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.settings.EditorZustaende;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.context.UIContextMenu;
import mchorse.bbs_mod.ui.texturstudio.UIBrowserFlaeche;
import mchorse.bbs_mod.ui.utils.Area;
import net.minecraft.client.renderer.RenderPipelines;

/**
 * The guide keeps playing in a small window over the editor while you work (spec 5.4, revised):
 * a grip bar with the plaque, ⤢ back to the Help window and ×; YouTube's player 356 × 200 with
 * nothing on it; a control bar with progress, play/pause, −10, time and the chapter. Dragged by
 * its bars only — the picture belongs to YouTube — and it remembers where it was put.
 */
public class UIHilfeMini extends UIElement
{
    private static final float BREITE = 356F, GRIFF = 26F, STEUER = 32F;

    private static UIHilfeMini offen;

    private final HilfeSitzung sitzung;
    private final NanoFlaeche flaeche;
    private final UIBrowserFlaeche video;
    private float videoH;
    private boolean zieht;
    private HilfeFlug flug;
    private int zugX, zugY;

    static void zeigen(HilfeSitzung sitzung)
    {
        zeigen(sitzung, null);
    }

    /** {@code von}: where the video was (the big window) — it flies here from there. */
    static void zeigen(HilfeSitzung sitzung, float[] von)
    {
        UIDashboard dashboard = BBSModClient.getDashboard();

        schliessen();

        if (dashboard != null)
        {
            /* On top of everything, the overlay included: nothing may draw over the video (YouTube's
             * embed rules). Menus step aside instead (menuDaneben). */
            offen = new UIHilfeMini(sitzung);
            dashboard.getRoot().add(offen);
            offen.platzieren(dashboard.context);

            UIHilfeMini mini = offen;

            mini.flug = HilfeFlug.starten(sitzung.kanal.browser, von, () -> mini.hasParent() ? mini.video.area : null);
        }
    }

    static void schliessen()
    {
        if (offen != null)
        {
            offen.removeFromParent();
            offen = null;
        }
    }

    /** Show me: when it covers the place being shown (ring and label included), it steps to the
     * first clear corner. Not saved — the user's own position stays. */
    static void ausweichen(float[] r)
    {
        if (offen == null || r == null)
        {
            return;
        }

        UIContext context = BBSModClient.getDashboard().context;
        Area platz = new Area((int) r[0] - 8, (int) r[1] - 38, (int) r[2] + 16, (int) r[3] + 76);
        Area a = offen.area;
        int w = a.w, h = a.h, rechts = context.menu.width - w - 48, unten = context.menu.height - 32 - h;

        if (!a.intersects(platz))
        {
            return;
        }

        for (int[] ecke : new int[][] {{48, unten}, {rechts, unten}, {rechts, 48}, {48, 48}})
        {
            if (!new Area(ecke[0], ecke[1], w, h).intersects(platz))
            {
                offen.set(ecke[0], ecke[1], w, h);
                offen.resize();

                return;
            }
        }
    }

    /** A menu that would open under the mini-player moves beside it: left, right, above or below. */
    static void menuDaneben(UIContextMenu menu)
    {
        if (offen == null || !menu.area.intersects(offen.area))
        {
            return;
        }

        Area m = menu.area, v = offen.area;
        int b = offen.getContext().menu.width, h = offen.getContext().menu.height;

        if (v.x - m.w - 4 >= 5)
        {
            menu.xy(v.x - m.w - 4, m.y);
        }
        else if (v.ex() + 4 + m.w <= b - 5)
        {
            menu.xy(v.ex() + 4, m.y);
        }
        else if (v.y - m.h - 4 >= 5)
        {
            menu.xy(m.x, v.y - m.h - 4);
        }
        else if (v.ey() + 4 + m.h <= h - 5)
        {
            menu.xy(m.x, v.ey() + 4);
        }

        menu.resize();
    }

    private UIHilfeMini(HilfeSitzung sitzung)
    {
        this.sitzung = sitzung;
        this.flaeche = new NanoFlaeche(this::zeichnen);
        this.flaeche.relative(this).w(1F).h(1F);
        this.video = new UIBrowserFlaeche(sitzung.kanal.browser);
        this.add(this.flaeche, this.video);
    }

    /** Where it was put last, else the viewport's bottom left; always inside the window. */
    private void platzieren(UIContext context)
    {
        /* YouTube: at least 200 physical pixels high */
        this.videoH = Math.max(200F, (float) Math.ceil(200F / BBSModClient.getGUIScale()));

        int w = Math.round(this.videoH * 16F / 9F), h = Math.round(GRIFF + this.videoH + STEUER);
        int x = 48, y = context.menu.height - 22 - 10 - h;
        String[] pos = EditorZustaende.hilfeMiniPos.get().split(",");

        if (pos.length == 2)
        {
            try
            {
                x = Integer.parseInt(pos[0]);
                y = Integer.parseInt(pos[1]);
            }
            catch (NumberFormatException e)
            {
                /* the default stays */
            }
        }

        this.set(Math.max(0, Math.min(x, context.menu.width - w)), Math.max(34, Math.min(y, context.menu.height - h)), w, h);
        this.video.relative(this).xy(0, (int) GRIFF).w(w).h((int) this.videoH);
        this.resize();
    }

    @Override
    public boolean canBeRendered(Area viewport)
    {
        return true;
    }

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        /* Only the bars move it: over the picture the clicks belong to YouTube */
        if (this.area.isInside(context) && !this.video.area.isInside(context) && context.mouseButton == 0)
        {
            this.zieht = true;
            this.zugX = context.mouseX - this.area.x;
            this.zugY = context.mouseY - this.area.y;

            return true;
        }

        return super.subMouseClicked(context);
    }

    @Override
    protected boolean subMouseReleased(UIContext context)
    {
        if (this.zieht)
        {
            this.zieht = false;
            EditorZustaende.hilfeMiniPos.set(this.area.x + "," + this.area.y);
        }

        return super.subMouseReleased(context);
    }

    @Override
    public void render(UIContext context)
    {
        if (this.zieht)
        {
            int x = Math.max(0, Math.min(context.mouseX - this.zugX, context.menu.width - this.area.w));
            int y = Math.max(34, Math.min(context.mouseY - this.zugY, context.menu.height - this.area.h));

            if (x != this.area.x || y != this.area.y)
            {
                /* set(), not area: resize() rebuilds the area from what set() stored */
                this.set(x, y, this.area.w, this.area.h);
                this.resize();
            }
        }

        this.video.setVisible(this.flug == null || !this.flug.fliegt());
        this.sitzung.tick();
        this.flaeche.neuMalen();
        super.render(context);
    }

    /** Drawn again after tooltips and previews, which come after the UI tree: they must not cover the video. */
    static void obenauf(UIContext context)
    {
        if (offen != null && offen.hasParent() && context.menu == BBSModClient.getDashboard() && offen.video.isVisible()
            && offen.sitzung.kanal.browser.isTextureReady())
        {
            Area v = offen.video.area;

            context.batcher.getContext().blit(RenderPipelines.GUI_TEXTURED, offen.sitzung.kanal.browser.getTextureIdentifier(),
                v.x, v.y, 0F, 0F, v.w, v.h, v.w, v.h);
        }
    }

    /** Where the mini-player's video is, for the flight back into the window; null when it is closed. */
    static float[] videoRechteck()
    {
        return offen == null ? null : HilfeFlug.rechteck(offen.video);
    }

    private void zeichnen(long vg, int b, int h, float mausX, float mausY, NanoFlaeche f)
    {
        float k = Math.max(1F, f.skala());
        float w = this.area.w * k, vh = this.videoH * k, gh = GRIFF * k, sh = STEUER * k;
        Guides.Guide g = this.sitzung.guide;
        double zeit = this.sitzung.kanal.video.equals(g.video()) ? this.sitzung.kanal.zeit : 0D;
        int kapitel = g.kapitelBei(zeit);
        boolean spielt = this.sitzung.spielt();

        /* Grip bar: grip, plaque, back to the window, stop */
        NanoStifte.flaeche(vg, 0F, 0F, w, gh + 6F * k, 6F * k, NanoDesign.MENUE);
        for (int i = 0; i < 3; i++)
        {
            NanoStifte.flaeche(vg, 9F * k, (8F + i * 4F) * k, 10F * k, 1.5F * k, 0F, NanoDesign.TXT_OFF);
        }

        String plakette = "ORIGINAL BBS";
        float pw = NanoStifte.textBreite(vg, NanoDesign.BOLD, 9.5F * k, plakette) + 28F * k;

        NanoStifte.flaeche(vg, 26F * k, 4F * k, pw, 18F * k, 4F * k, NanoDesign.PANEL);
        HilfeNano.uhr(vg, 36F * k, 13F * k, 10F * k, NanoDesign.ORANGE, k);
        NanoStifte.textInBox(vg, NanoDesign.BOLD, 9.5F * k, 45F * k, 13F * k, NanoDesign.TXT, plakette);

        f.zone("mini-zu", w - 26F * k, 1F * k, 24F * k, 24F * k, () -> { schliessen(); HilfeSitzung.beenden(); });
        NanoStifte.linie(vg, w - 19F * k, 8F * k, w - 9F * k, 18F * k, 1.5F * k, f.istHover("mini-zu") ? NanoDesign.ROT : NanoDesign.TXT);
        NanoStifte.linie(vg, w - 9F * k, 8F * k, w - 19F * k, 18F * k, 1.5F * k, f.istHover("mini-zu") ? NanoDesign.ROT : NanoDesign.TXT);
        f.zone("mini-gross", w - 52F * k, 1F * k, 24F * k, 24F * k, () ->
        {
            schliessen();
            UIHilfeFenster.zeigen(BBSModClient.getDashboard().context, this.sitzung);
        });
        NanoStifte.rahmen(vg, w - 46F * k, 7F * k, 12F * k, 12F * k, 2F * k, 1.4F * k, f.istHover("mini-gross") ? NanoDesign.TXT_MAX : NanoDesign.TXT);

        /* Control bar under the picture: progress on its top edge, then the controls */
        float sy = gh + vh;

        NanoStifte.flaeche(vg, 0F, sy - 6F * k, w, sh + 6F * k, 6F * k, NanoDesign.MENUE);
        NanoStifte.flaeche(vg, 0F, sy, w, 3F * k, 0F, 0x3A3A3A);
        NanoStifte.flaeche(vg, 0F, sy, (float) (w * Math.min(1D, zeit / g.laenge())), 3F * k, 0F, NanoDesign.BLAU_HELL);

        float my = sy + 3F * k + (sh - 3F * k) / 2F;

        f.zone("mini-spiel", 6F * k, my - 11F * k, 26F * k, 22F * k, () -> { if (spielt) this.sitzung.kanal.pause(); else this.sitzung.kanal.spielen(); });
        HilfeNano.spielSymbol(vg, 19F * k, my, 10F * k, spielt, f.istHover("mini-spiel") ? NanoDesign.TXT_MAX : NanoDesign.TXT);
        f.zone("mini-zurueck", 34F * k, my - 11F * k, 30F * k, 22F * k, () -> this.sitzung.kanal.springe(g.video(), Math.max(0D, zeit - 10D)));
        NanoStifte.textMitteInBox(vg, NanoDesign.SANS, 10F * k, 49F * k, my, f.istHover("mini-zurueck") ? NanoDesign.TXT_MAX : NanoDesign.TXT, "−10");

        String uhr = HilfeNano.zeit(zeit);

        NanoStifte.textInBox(vg, NanoDesign.SANS, 10.5F * k, 72F * k, my, NanoDesign.TXT, uhr);

        float tx = 72F * k + NanoStifte.textBreite(vg, NanoDesign.SANS, 10.5F * k, uhr) + 10F * k;
        String label = this.sitzung.kanal.zustand == 3 ? "Loading…" : "Ch. " + (kapitel + 1) + " · " + g.kapitel().get(kapitel).titel();

        NanoStifte.textInBox(vg, NanoDesign.SANS, 10.5F * k, tx, my, NanoDesign.TXT_DIM, HilfeNano.kuerzen(vg, NanoDesign.SANS, 10.5F * k, label, w - tx - 10F * k));
    }
}
