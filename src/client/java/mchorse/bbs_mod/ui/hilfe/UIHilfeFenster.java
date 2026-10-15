package mchorse.bbs_mod.ui.hilfe;

import com.mcstudio.nano.NanoDesign;
import com.mcstudio.nano.NanoFlaeche;
import com.mcstudio.nano.NanoStifte;
import com.mojang.blaze3d.Blaze3D;
import de.keksuccino.rinku.Rinku;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.EditorZustaende;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.texturstudio.UIBrowserFlaeche;

import java.net.URI;

/**
 * The Help window (Claude Design, spec section 5): head with the Help title and the way to the
 * community, the Tutorial body — McHorse's guide with our chapter timeline, transport, "In this
 * version" and the chapter list — and a foot with the connection state. The video is YouTube's
 * own player in Rinku, placed exactly on its rectangle; nothing of ours is drawn over it.
 */
public class UIHilfeFenster extends UIOverlayPanel
{
    private static final float KOPF_H = 36F;
    private static final float FUSS_H = 28F;

    private final HilfeSitzung sitzung;
    private final NanoFlaeche flaeche;
    private final UIBrowserFlaeche video;
    private final HilfeZeitleiste zeitleiste = new HilfeZeitleiste();
    private final HilfeTasten tasten = new HilfeTasten();

    /* Layout in GUI px relative to the window, set in resize() */
    private float vx, vy, vw, vh, lx, lw;
    private boolean zuKlein;
    /** × stops the video; Esc, a click beside the window or Mini-player keep it playing small. */
    private boolean stoppen;
    private boolean klein;
    /** tutorial | clips | neu; on the others a playing video shrinks to picture-in-picture (spec 5.3) */
    private String reiter = "tutorial";
    private float px, py, pw, ph;
    private HilfeFlug flug;

    /** False when Rinku cannot make a browser yet (first start downloads it): then the caller opens YouTube. */
    static boolean oeffnen(UIContext context, Guides.Guide guide, int sekunde)
    {
        if (!Rinku.isInitialized() && !Rinku.initialize())
        {
            return false;
        }

        HilfeErststart.hilfeGeoeffnet();

        /* An open session (the mini-player) goes to the asked place too: Shift+F1 and the menu's
         * chapter entry jumped nowhere while it played elsewhere (measured 2026-10-14) */
        boolean offen = HilfeSitzung.aktiv() != null && HilfeSitzung.aktiv().guide == guide;
        HilfeSitzung sitzung = HilfeSitzung.guide(guide, sekunde);

        if (offen && Math.abs(sitzung.kanal.zeit - sekunde) > 3D)
        {
            sitzung.kanal.springe(guide.video(), sekunde);
        }

        zeigen(context, sitzung);

        return true;
    }

    /** The window for a session that may be playing already (back from the mini-player). */
    static void zeigen(UIContext context, HilfeSitzung sitzung)
    {
        int w = Math.min(1040, context.menu.width - 40);
        int h = Math.min(596, context.menu.height - 40);

        float[] von = UIHilfeMini.videoRechteck();
        UIHilfeFenster fenster = new UIHilfeFenster(sitzung);

        UIHilfeMini.schliessen();
        UIOverlay.addOverlay(context, fenster, w, h);
        fenster.flug = HilfeFlug.starten(sitzung.kanal.browser, von, () -> fenster.hasParent() && fenster.zeigtVideo() ? fenster.video.area : null);
    }

    private UIHilfeFenster(HilfeSitzung sitzung)
    {
        super(IKey.EMPTY);

        this.sitzung = sitzung;
        this.title.setVisible(false);
        this.icons.setVisible(false);
        this.content.relative(this).xy(0, 0).w(1F).h(1F);

        this.flaeche = new NanoFlaeche(this::zeichnen);
        this.flaeche.beimTippen((fokus, c) -> this.tasten.tippen(this.flaeche, fokus, c));
        this.flaeche.relative(this.content).w(1F).h(1F);
        this.video = new UIBrowserFlaeche(sitzung.kanal.browser);
        this.content.add(this.flaeche, this.video);

        this.onClose((e) ->
        {
            if (!this.stoppen && (this.klein || this.sitzung.spielt()))
            {
                UIHilfeMini.zeigen(this.sitzung, HilfeFlug.rechteck(this.video));
            }
            else
            {
                HilfeSitzung.beenden();
            }
        });
    }

    @Override
    public void resize()
    {
        /* The window's own size first: it comes from the overlay's layout */
        super.resize();

        float w = this.area.w, h = this.area.h;
        float unten = h - FUSS_H - 14F;

        this.vx = 15F;
        this.vy = KOPF_H + 14F + 22F + 6F;
        /* Under the video: 7 + timeline 18 + transport 28 + 9 + "In this version" 46 */
        this.vh = Math.min(360F, unten - this.vy - 108F);
        this.vw = this.vh * 16F / 9F;

        if (this.vx + this.vw + 15F + 200F + 14F > w)
        {
            this.vw = w - this.vx - 15F - 200F - 14F;
            this.vh = this.vw * 9F / 16F;
        }

        this.lx = this.vx + this.vw + 15F;
        this.lw = w - this.lx - 14F;
        /* YouTube: the player at least 200 x 200 physical pixels */
        this.zuKlein = this.vh * BBSModClient.getGUIScale() < 200F;
        /* Picture-in-picture bottom right, never under YouTube's 200 physical px */
        this.ph = Math.max(144F, (float) Math.ceil(200F / BBSModClient.getGUIScale()));
        this.pw = this.ph * 16F / 9F;
        this.px = w - 15F - this.pw;
        this.py = h - FUSS_H - 15F - this.ph;
        this.videoPlatzieren();
    }

    private void videoPlatzieren()
    {
        boolean bild = !this.reiter.equals("tutorial");

        this.video.relative(this.content).xy((int) (bild ? this.px : this.vx), (int) (bild ? this.py : this.vy))
            .w((int) (bild ? this.pw : this.vw)).h((int) (bild ? this.ph : this.vh));
        this.video.resize();
    }

    /** Show me: the window gives way to the mini-player, then the place gets its ring. */
    private void zeigeMir(String ziel)
    {
        if (ziel.equals("shorts"))
        {
            this.wechseln("clips");

            return;
        }

        this.klein = true;
        this.close();
        UIHilfeSpot.zeigen(ziel);
    }

    private void wechseln(String reiter)
    {
        this.reiter = reiter;
        /* Each tab starts at its top: the Shortcuts list's scroll would carry over */
        this.flaeche.rollGrenze(0);

        if (reiter.equals("neu"))
        {
            EditorZustaende.hilfeNeuGesehen.set(HilfeReiter.version());
        }

        this.videoPlatzieren();
    }

    @Override
    protected void renderBackground(UIContext context)
    {
        /* The NanoVG surface paints the window */
    }

    @Override
    public void render(UIContext context)
    {
        this.sitzung.tick();
        this.video.setVisible(this.zeigtVideo() && (this.flug == null || !this.flug.fliegt()));
        this.flaeche.neuMalen();
        super.render(context);
    }

    private boolean zeigtVideo()
    {
        return !this.zuKlein && !this.sitzung.vorspann && (this.reiter.equals("tutorial") || this.sitzung.spielt());
    }

    private void zeichnen(long vg, int b, int h, float mausX, float mausY, NanoFlaeche f)
    {
        float k = Math.max(1F, f.skala());
        float w = this.area.w * k, hh = this.area.h * k;

        NanoStifte.flaeche(vg, 0F, 0F, w, hh, 9F * k, NanoDesign.REGION);
        NanoStifte.rahmen(vg, 0F, 0F, w, hh, 9F * k, Math.max(1F, k), 0x000000);

        /* Head */
        NanoStifte.flaeche(vg, 0F, 0F, w, KOPF_H * k, 9F * k, NanoDesign.EDITOR);
        NanoStifte.flaeche(vg, 0F, (KOPF_H - 9F) * k, w, 9F * k, 0F, NanoDesign.EDITOR);
        NanoStifte.rahmen(vg, 14F * k, 11F * k, 14F * k, 14F * k, 7F * k, 1.4F * k, NanoDesign.TXT);
        NanoStifte.textMitteInBox(vg, NanoDesign.BOLD, 10F * k, 21F * k, 18F * k, NanoDesign.TXT, "?");
        NanoStifte.textInBox(vg, NanoDesign.BOLD, 13F * k, 36F * k, 18F * k, NanoDesign.TXT_MAX, "Help");

        /* Segment tabs */
        float tx = 92F * k;

        for (String[] r : new String[][] {{"tutorial", "Tutorial"}, {"clips", "Short clips"}, {"tasten", "Shortcuts"}, {"neu", "What's new"}})
        {
            float tw = NanoStifte.textBreite(vg, NanoDesign.SANS, 11F * k, r[1]) + 20F * k;
            boolean an = this.reiter.equals(r[0]);

            NanoStifte.flaeche(vg, tx, 7F * k, tw, 22F * k, 4F * k, an ? NanoDesign.BLAU : f.istHover("reiter-" + r[0]) ? NanoDesign.WIDGET_HI : NanoDesign.WIDGET);
            NanoStifte.textMitteInBox(vg, NanoDesign.SANS, 11F * k, tx + tw / 2F, 18F * k, an ? NanoDesign.TXT_MAX : NanoDesign.TXT, r[1]);

            if (r[0].equals("neu") && !HilfeReiter.version().equals(EditorZustaende.hilfeNeuGesehen.get()))
            {
                NanoStifte.kreis(vg, tx + tw - 5F * k, 10F * k, 3F * k, NanoDesign.ORANGE);
            }

            f.zone("reiter-" + r[0], tx, 7F * k, tw, 22F * k, () -> this.wechseln(r[0]));
            tx += tw + 4F * k;
        }

        float rx = w - 34F * k;

        f.zone("zu", rx, 6F * k, 24F * k, 24F * k, () -> { this.stoppen = true; this.close(); });
        NanoStifte.linie(vg, rx + 7F * k, 13F * k, rx + 17F * k, 23F * k, 1.6F * k, f.istHover("zu") ? NanoDesign.ROT : NanoDesign.TXT);
        NanoStifte.linie(vg, rx + 17F * k, 13F * k, rx + 7F * k, 23F * k, 1.6F * k, f.istHover("zu") ? NanoDesign.ROT : NanoDesign.TXT);

        float cw = NanoStifte.textBreite(vg, NanoDesign.SANS, 11F * k, "Community") + 30F * k;

        rx -= cw + 8F * k;
        NanoStifte.widget(vg, rx, 7F * k, cw, 22F * k, 4F * k, f.istHover("community") ? NanoDesign.WIDGET_HI : NanoDesign.WIDGET, k);
        NanoStifte.textInBox(vg, NanoDesign.SANS, 11F * k, rx + 9F * k, 18F * k, NanoDesign.TXT, "Community");
        HilfeNano.raus(vg, rx + cw - 12F * k, 18F * k, 9F * k, NanoDesign.TXT, k);
        f.zone("community", rx, 7F * k, cw, 22F * k, () -> net.minecraft.Util.getPlatform().openUri(URI.create(Guides.community())));

        float mw = NanoStifte.textBreite(vg, NanoDesign.SANS, 11F * k, "Mini-player") + 18F * k;

        rx -= mw + 6F * k;
        NanoStifte.widget(vg, rx, 7F * k, mw, 22F * k, 4F * k, f.istHover("mini") ? NanoDesign.WIDGET_HI : NanoDesign.WIDGET, k);
        NanoStifte.textInBox(vg, NanoDesign.SANS, 11F * k, rx + 9F * k, 18F * k, NanoDesign.TXT, "Mini-player");
        f.zone("mini", rx, 7F * k, mw, 22F * k, () -> { this.klein = true; this.close(); });

        /* Foot: whether YouTube answers */
        float fy = hh - FUSS_H * k;
        boolean offline = this.sitzung.kanal.fehler != null;

        NanoStifte.flaeche(vg, 0F, fy, w, FUSS_H * k, 9F * k, NanoDesign.EDITOR);
        NanoStifte.flaeche(vg, 0F, fy, w, 9F * k, 0F, NanoDesign.EDITOR);
        NanoStifte.kreis(vg, 17F * k, fy + 14F * k, 3F * k, offline ? NanoDesign.ROT : NanoDesign.GRUEN);
        NanoStifte.textInBox(vg, NanoDesign.SANS, 10.5F * k, 26F * k, fy + 14F * k, NanoDesign.TXT_DIM,
            offline ? "Offline — can't reach YouTube" : "Streams from YouTube");

        String tipp = "Shift+F1 opens the chapter for the editor you're in  ·  Esc keeps the video playing in a mini-player";

        NanoStifte.textInBox(vg, NanoDesign.SANS, 10.5F * k, w - 14F * k - NanoStifte.textBreite(vg, NanoDesign.SANS, 10.5F * k, tipp),
            fy + 14F * k, NanoDesign.TXT_OFF, tipp);

        if (!this.reiter.equals("tutorial"))
        {
            this.andererReiter(vg, f, k);

            return;
        }

        if (this.sitzung.vorspann && !this.zuKlein)
        {
            this.vorspann(vg, f, k);
        }
        else if (this.zuKlein || offline)
        {
            this.ersatz(vg, f, k, offline);
        }

        this.zeitleiste.zeichnen(vg, f, k, this.sitzung, this.vx, this.vy, this.vw, this.vh);
        HilfeKapitelliste.inDieserVersion(vg, f, k, this.sitzung, this.vx, this.vy + this.vh + 62F, this.vw, this::zeigeMir);
        HilfeKapitelliste.liste(vg, f, k, this.sitzung, this.lx, KOPF_H + 14F, this.lw, this.area.h - KOPF_H - FUSS_H - 28F);
    }

    /** Short clips or What's new; a playing guide sits bottom right with a bar above it (never on it). */
    private void andererReiter(long vg, NanoFlaeche f, float k)
    {
        /* While the guide plays bottom right, the content keeps left of it: nothing may be under the player */
        float x = 15F, y = KOPF_H + 14F, w = this.area.w - 30F - (this.sitzung.spielt() ? this.pw + 15F : 0F), h = this.area.h - KOPF_H - FUSS_H - 28F;

        if (this.reiter.equals("clips"))
        {
            HilfeReiter.kurzclips(vg, f, k, x, y, w, h, this::zeigeMir);
        }
        else if (this.reiter.equals("tasten"))
        {
            this.tasten.zeichnen(vg, f, k, this.getContext(), x, y, w, h);
        }
        else
        {
            HilfeReiter.neuigkeiten(vg, f, k, x, y, w, h);
        }

        if (this.sitzung.spielt())
        {
            float bx = this.px * k, by = (this.py - 24F) * k, bw = this.pw * k;

            NanoStifte.flaeche(vg, bx, by, bw, 24F * k, 4F * k, NanoDesign.MENUE);
            NanoStifte.textInBox(vg, NanoDesign.SANS, 10.5F * k, bx + 8F * k, by + 12F * k, NanoDesign.TXT,
                f.istHover("zurueck-tutorial") ? "Back to the tutorial" : "Tutorial · " + HilfeNano.zeit(this.sitzung.kanal.zeit));
            f.zone("zurueck-tutorial", bx, by, bw, 24F * k, () -> this.wechseln("tutorial"));
        }
    }

    /** Once, before the guide's first play, instead of the player: it shows the original BBS. */
    private void vorspann(long vg, NanoFlaeche f, float k)
    {
        float x = this.vx * k, y = this.vy * k, w = this.vw * k, h = this.vh * k;
        float rand = Math.min(48F * k, w / 10F), tw = w - rand * 2F;
        String text = "McHorse recorded it in the first version of BBS. Yours looks different, but every feature in the video works the same way here.";
        String pin = "Below the video, In this version tells you where each feature lives now.";
        float th = HilfeNano.textHoehe(vg, 12F * k, tw, text);
        float ty = y + h / 2F - (40F * k + th + 54F * k + 22F * k) / 2F;

        NanoStifte.flaeche(vg, x, y, w, h, 6F * k, NanoDesign.EDITOR);
        NanoStifte.textInBox(vg, NanoDesign.BOLD, 10F * k, x + rand, ty, NanoDesign.ORANGE, "BEFORE YOU START");
        NanoStifte.textInBox(vg, NanoDesign.BOLD, 19F * k, x + rand, ty + 20F * k, NanoDesign.TXT_MAX, "This tutorial shows the original BBS");
        HilfeNano.text(vg, NanoDesign.SANS, 12F * k, x + rand, ty + 40F * k, tw, NanoDesign.TXT_DIM, text);
        NanoStifte.kreis(vg, x + rand + 4F * k, ty + 58F * k + th, 3.5F * k, NanoDesign.ORANGE);
        HilfeNano.text(vg, NanoDesign.SANS, 11F * k, x + rand + 14F * k, ty + 51F * k + th, tw - 14F * k, NanoDesign.TXT_DIM, pin);
        HilfeNano.knopf(vg, f, "vorspann", "Got it — load the video", x + rand, ty + 76F * k + th, true, k, this.sitzung::vorspannGesehen);
    }

    /** Instead of the player (never on it): too small for YouTube's 200 px, or no connection. */
    private void ersatz(long vg, NanoFlaeche f, float k, boolean offline)
    {
        float x = this.vx * k, y = this.vy * k, w = this.vw * k, h = this.vh * k;
        Guides.Guide g = this.sitzung.guide;

        NanoStifte.flaeche(vg, x, y, w, h, 6F * k, NanoDesign.EDITOR);
        NanoStifte.textMitteInBox(vg, NanoDesign.BOLD, 13F * k, x + w / 2F, y + h / 2F - 26F * k, NanoDesign.TXT,
            offline ? "Can't reach YouTube" : "This window is too small for the video");
        HilfeNano.knopf(vg, f, "browser", "Open in browser", x + w / 2F - 60F * k, y + h / 2F, true, k,
            () -> Guides.imBrowser(g.video(), (int) this.sitzung.kanal.zeit));

        if (offline)
        {
            HilfeNano.knopf(vg, f, "retry", "Retry", x + w / 2F + 66F * k, y + h / 2F, false, k, this.sitzung.kanal::neuLaden);
        }
    }
}
