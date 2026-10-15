package mchorse.bbs_mod.ui.hilfe;

import com.mcstudio.nano.NanoDesign;
import com.mcstudio.nano.NanoFlaeche;
import com.mcstudio.nano.NanoStifte;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.UIZeitleisteNano;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.utils.Area;

/**
 * Empty states that point at the tutorial (spec section 6): over the timeline's body, while the
 * film has no replays (Dope Sheet, Animated only) or no camera clips (Camera clips), a small card
 * says so and opens the chapter that shows how. Hung into the timeline from outside — that file
 * may only shrink. Away, it has no area: clicks there stay with the timeline.
 */
public class UIHilfeLeer extends UIElement
{
    private static final int B = 330, H = 64;

    private final UIZeitleisteNano zeitleiste;
    private final NanoFlaeche flaeche;
    private String leer = "";

    /** Once per film editor; called whenever the dashboard opens. */
    static void anhaengen(UIDashboard d)
    {
        UIFilmPanel film = d.getPanels().getPanel(UIFilmPanel.class);

        if (film != null && film.zeitleisteNano instanceof UIZeitleisteNano z && z.getChildren(UIHilfeLeer.class).isEmpty())
        {
            z.add(new UIHilfeLeer(z));
        }
    }

    private UIHilfeLeer(UIZeitleisteNano zeitleiste)
    {
        this.zeitleiste = zeitleiste;
        this.flaeche = new NanoFlaeche(this::zeichnen);
        this.flaeche.relative(this).w(1F).h(1F);
        this.add(this.flaeche);
    }

    /** "replays", "camera" or "" when nothing the timeline shows is empty. */
    private String leer()
    {
        Film film = BBSModClient.getDashboard().getPanels().panel instanceof UIFilmPanel panel ? panel.getData() : null;

        if (film == null)
        {
            return "";
        }

        if (this.zeitleiste.modus == 2)
        {
            return film.camera.get().isEmpty() ? "camera" : "";
        }

        return film.replays.getList().isEmpty() ? "replays" : "";
    }

    @Override
    public boolean canBeRendered(Area viewport)
    {
        return true;
    }

    @Override
    public void render(UIContext context)
    {
        String leer = this.leer();
        Area z = this.zeitleiste.area;
        int kopf = (int) NanoDesign.H_KOPF;
        boolean passt = !leer.isEmpty() && z.w >= B + 20 && z.h >= kopf + H + 16;
        int x = passt ? z.x + (z.w - B) / 2 : 0, y = passt ? z.y + kopf + (z.h - kopf - H) / 2 : 0;
        int w = passt ? B : 0, h = passt ? H : 0;

        if (!leer.equals(this.leer))
        {
            this.leer = leer;
            this.flaeche.neuMalen();
        }

        if (this.area.x != x || this.area.y != y || this.area.w != w || this.area.h != h)
        {
            this.set(x, y, w, h);
            this.resize();
        }

        if (passt)
        {
            super.render(context);
        }
    }

    private void zeichnen(long vg, int b, int h, float mausX, float mausY, NanoFlaeche f)
    {
        if (this.leer.isEmpty())
        {
            return;
        }

        float k = Math.max(1F, f.skala());
        boolean kamera = this.leer.equals("camera");
        Guides.Guide guide = Guides.alle().get(0);
        Guides.Kapitel kapitel = guide.kapitel().stream().filter((c) -> (kamera ? "camera" : "replays").equals(c.thema())).findFirst().orElse(null);

        NanoStifte.flaeche(vg, 0, 0, b, h, 6F * k, NanoDesign.PANEL);
        NanoStifte.rahmen(vg, 0, 0, b, h, 6F * k, Math.max(1F, k), 0x1A1A1A);
        NanoStifte.textInBox(vg, NanoDesign.BOLD, 12F * k, 14F * k, 18F * k, NanoDesign.TXT_MAX, kamera ? "No camera clips yet" : "No replays yet");

        if (kapitel != null)
        {
            String text = "See how in the tutorial · " + kapitel.titel() + " · " + HilfeNano.zeit(kapitel.sekunde());

            HilfeNano.knopf(vg, f, "leer-kapitel", text, 14F * k, 30F * k, true, k,
                () -> Guides.oeffnen(this.getContext(), guide, kapitel.sekunde()));
        }
    }
}
