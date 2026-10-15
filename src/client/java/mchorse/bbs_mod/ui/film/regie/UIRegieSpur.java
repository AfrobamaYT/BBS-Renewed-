package mchorse.bbs_mod.ui.film.regie;

import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.regie.Regie;
import mchorse.bbs_mod.film.regie.RegieAnsicht;
import mchorse.bbs_mod.film.regie.RegieMarker;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.UIClips;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.renderers.TimelineRulerRenderer;

import java.util.List;

/**
 * Regie-Spuren der Kamera-Zeitleiste (Design: „Abschnitt" und „Regie-Marker"): ein Band gleich
 * unter dem Lineal — wie die Merkstellen-Spur und wie Schnittprogramme Bereichsmarker zeigen.
 * Oben der Abschnitt (Vorlauf links vom Anfang), darunter die Zeitfenster als Farbbalken,
 * ueberlappende in eigenen Zeilen. Balken ziehen = verschieben, Enden ziehen = Dauer,
 * Abschnitt ziehen = Anfang. Ein Klick daneben geht an die Zeitleiste weiter.
 */
public class UIRegieSpur extends UIElement
{
    private static final int ZEILE = 12;
    private static final int ABSTAND = 2;
    /** Greifbreite der Balkenenden in GUI-Pixeln. */
    private static final int RAND = 3;
    private static final int TEXT = 0xFF0A0C10;

    private final UIClips clips;
    private final IUIClipsDelegate delegate;
    private final UIFilmPanel panel;
    /** Gefasster Marker; null und {@link #abschnitt} false = nichts gefasst. */
    private RegieMarker gezogen;
    private boolean abschnitt;
    /** 0 = verschieben, 1 = Beginn ziehen, 2 = Ende ziehen. */
    private int art;
    private int griff;
    private int dauer;

    public UIRegieSpur(UIClips clips, IUIClipsDelegate delegate, UIFilmPanel panel)
    {
        this.clips = clips;
        this.delegate = delegate;
        this.panel = panel;
    }

    private Regie regie()
    {
        Film film = this.delegate.getFilm();
        Regie regie = film == null ? null : film.regie;

        return regie == null || (regie.marker.getList().isEmpty() && !regie.startGesetzt.get()) ? null : regie;
    }

    private int oben()
    {
        return TimelineRulerRenderer.getRulerBottom(this.area) + 1;
    }

    /** Oberkante der Markerzeile z. */
    private int zeileY(int z)
    {
        return this.oben() + (z + 1) * (ZEILE + ABSTAND);
    }

    @Override
    public void render(UIContext context)
    {
        Regie regie = this.regie();

        if (regie != null)
        {
            if (this.gezogen != null || this.abschnitt)
            {
                this.ziehen(context, regie);
            }

            context.batcher.clip(this.area, context);
            this.zeichnen(context, regie);
            context.batcher.unclip(context);
        }

        super.render(context);
    }

    private void zeichnen(UIContext context, Regie regie)
    {
        FontRenderer font = context.batcher.getFont();
        List<RegieMarker> sortiert = RegieAnsicht.sortiert(regie);
        int[] zeile = RegieAnsicht.zeilen(sortiert);
        int zeilen = 0;

        for (int z : zeile)
        {
            zeilen = Math.max(zeilen, z + 1);
        }

        int y = this.oben();
        int ty = (ZEILE - font.getHeight()) / 2 + 1;

        context.batcher.box(this.area.x, y - 1, this.area.ex(), this.zeileY(zeilen) + 1, 0xB00E1014);

        if (regie.startGesetzt.get())
        {
            int a = this.clips.toGraphX(regie.anfang.get());
            int e = this.clips.toGraphX(RegieAnsicht.abschnittEnde(regie));
            int v = this.clips.toGraphX(regie.anfang.get() - regie.vorlauf.get());
            String text = "Start point · " + RegieAnsicht.zahl(regie.anfang.get() / 20F) + "–"
                + RegieAnsicht.zahl(RegieAnsicht.abschnittEnde(regie) / 20F) + " s";

            context.batcher.box(v, y + 3, a, y + ZEILE - 3, 0x40FFFFFF);
            context.batcher.rundeck(a, y, e - a, ZEILE, 3F, 0x26FFFFFF);
            context.batcher.outline(a, y, e, y + ZEILE, 0x60FFFFFF);
            context.batcher.rundeck(a, y, ZEILE, ZEILE, 3F, 0xFFF2F5FA);
            context.batcher.text("S", a + (ZEILE - font.getWidth("S")) / 2F, y + ty, TEXT);
            context.batcher.text(font.limitToWidth(text, Math.max(0, e - a - ZEILE - 6)), a + ZEILE + 4, y + ty, 0xFFFFFFFF);
        }

        for (int i = 0; i < sortiert.size(); i++)
        {
            RegieMarker m = sortiert.get(i);
            int x1 = this.clips.toGraphX(m.start.get());
            int x2 = this.clips.toGraphX(m.ende.get());
            int my = this.zeileY(zeile[i]);
            String text = (i + 1) + " · " + m.name.get();

            context.batcher.rundeck(x1, my, x2 - x1, ZEILE, 3F, 0xFF000000 | m.farbe.get());
            context.batcher.box(x1 + 1, my + 3, x1 + 2, my + ZEILE - 3, 0x590A0C10);
            context.batcher.box(x2 - 2, my + 3, x2 - 1, my + ZEILE - 3, 0x590A0C10);
            context.batcher.text(font.limitToWidth(text, Math.max(0, x2 - x1 - 8)), x1 + 4, my + ty, TEXT);

            if (m == this.gezogen)
            {
                context.batcher.outline(x1, my, x2, my + ZEILE, 0xFFFFFFFF);
            }
        }

        /* Der Abspielkopf laeuft ueber das Band, sonst verschwaende er darunter. */
        int cx = this.clips.toGraphX(this.delegate.getCursor());

        context.batcher.box(cx - 1, y - 1, cx + 1, this.zeileY(zeilen) + 1, 0xFF57A0FF);
    }

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        Regie regie = this.regie();

        if (regie == null || context.mouseButton != 0 || !this.area.isInside(context))
        {
            return false;
        }

        List<RegieMarker> sortiert = RegieAnsicht.sortiert(regie);
        int[] zeile = RegieAnsicht.zeilen(sortiert);
        int tick = this.clips.fromGraphX(context.mouseX);

        for (int i = sortiert.size() - 1; i >= 0; i--)
        {
            RegieMarker m = sortiert.get(i);
            int x1 = this.clips.toGraphX(m.start.get());
            int x2 = this.clips.toGraphX(m.ende.get());
            int my = this.zeileY(zeile[i]);

            if (context.mouseX >= x1 - RAND && context.mouseX <= x2 + RAND && context.mouseY >= my && context.mouseY < my + ZEILE)
            {
                this.gezogen = m;
                this.art = context.mouseX <= x1 + RAND ? 1 : context.mouseX >= x2 - RAND ? 2 : 0;
                this.griff = tick - m.start.get();
                this.dauer = m.ende.get() - m.start.get();
                RegieBedienung.zaun(this.panel);

                return true;
            }
        }

        int y = this.oben();

        if (regie.startGesetzt.get() && context.mouseY >= y && context.mouseY < y + ZEILE
            && context.mouseX >= this.clips.toGraphX(regie.anfang.get()) && context.mouseX <= this.clips.toGraphX(RegieAnsicht.abschnittEnde(regie)))
        {
            this.abschnitt = true;
            this.griff = tick - regie.anfang.get();
            RegieBedienung.zaun(this.panel);

            return true;
        }

        return false;
    }

    private void ziehen(UIContext context, Regie regie)
    {
        int tick = this.clips.fromGraphX(context.mouseX);

        if (this.abschnitt)
        {
            int anfang = Math.max(0, tick - this.griff);

            if (anfang != regie.anfang.get())
            {
                BaseValue.edit(regie.anfang, (w) -> w.set(anfang));
            }

            return;
        }

        RegieMarker m = this.gezogen;
        int start = this.art == 2 ? m.start.get() : this.art == 1 ? Math.min(Math.max(0, tick), m.ende.get() - 1) : Math.max(0, tick - this.griff);
        int ende = this.art == 2 ? Math.max(tick, m.start.get() + 1) : this.art == 1 ? m.ende.get() : start + this.dauer;

        if (start != m.start.get() || ende != m.ende.get())
        {
            BaseValue.edit(m, (w) ->
            {
                w.start.set(start);
                w.ende.set(ende);
            });
        }
    }

    @Override
    protected boolean subMouseReleased(UIContext context)
    {
        if (this.gezogen == null && !this.abschnitt)
        {
            return false;
        }

        this.gezogen = null;
        this.abschnitt = false;
        RegieBedienung.zaun(this.panel);

        return true;
    }
}
