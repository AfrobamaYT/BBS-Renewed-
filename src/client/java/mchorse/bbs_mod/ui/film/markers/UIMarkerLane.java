package mchorse.bbs_mod.ui.film.markers;

import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.markers.Marker;
import mchorse.bbs_mod.film.markers.Markers;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.UIClips;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.renderers.TimelineRulerRenderer;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * Die Merkstellen-Spur über der Zeitleiste: zeichnet die Marker und nimmt Klicks darauf an.
 *
 * <p>Warum ein eigenes Kind-Element statt Code in {@link UIClips}: Kinder werden NACH dem
 * Inhalt gezeichnet, bekommen den Mausklick VOR dem Elternteil, und {@code UIClips} ist mit
 * über 2000 Zeilen längst zu groß, um noch etwas aufzunehmen. Die Spur liegt deckungsgleich
 * über der Zeitleiste, gibt aber jeden Klick weiter, der keine Merkstelle trifft — sonst
 * würde sie das Scrubbing schlucken.</p>
 *
 * <p>Bedienung: Strg+M setzt eine Merkstelle am Abspielkopf, Linksklick auf den Kopf öffnet
 * ihre Einstellungen, Rechtsklick löscht sie. Das folgt dem, was Schnittprogramme tun
 * (Premiere/Resolve setzen Marker per Taste am Abspielkopf und öffnen sie per Klick).</p>
 */
public class UIMarkerLane extends UIElement
{
    /** Höhe des anklickbaren Kopfes am oberen Rand der Spur. */
    private static final int KOPF_HOEHE = 9;

    /** Halbe Breite des Kopfes. Schmal genug, dass er einen Clip nicht verdeckt. */
    private static final int KOPF_BREITE = 4;

    /** Wie viele Zeilen des Kopfes zur Spitze zulaufen. */
    private static final int SPITZE = 3;

    private final UIClips clips;
    private final IUIClipsDelegate delegate;

    /** Der gerade gefasste Marker, oder null. */
    private Marker gezogen;

    /** Abstand zwischen Greifpunkt und Marker-Tick, damit er beim Ziehen nicht springt. */
    private int griffVersatz;

    /** Ob sich seit dem Fassen wirklich etwas bewegt hat — sonst war es ein Klick. */
    private boolean verschoben;

    public UIMarkerLane(UIClips clips, IUIClipsDelegate delegate)
    {
        this.clips = clips;
        this.delegate = delegate;

        this.keys().register(MarkerKeys.MARKER_ADD, this::setzeAmCursor)
            .category(UIKeys.CAMERA_EDITOR_KEYS_CLIPS_TITLE)
            .active(delegate::canUseKeybinds);
    }

    /**
     * Oberkante des Kopfes: direkt UNTER dem Lineal.
     *
     * <p>Ein Marker gehört nicht auf die Skala — dort sitzen die Tick-Zahlen und der Griff des
     * Abspielkopfes, und beides ist wichtiger. Schnittprogramme legen die Marker deshalb in ein
     * eigenes Band gleich unter dem Lineal; genau das tut das hier.</p>
     */
    /**
     * Ein Marker-Kopf: Fähnchen mit Spitze nach unten, dunkel umrandet, oben aufgehellt.
     *
     * <p>Warum nicht einfach ein Rechteck (so war es zuerst): ein flacher Farbklecks sieht
     * nach Platzhalter aus und geht auf einem gleichfarbigen Clip unter. Die drei Zutaten,
     * die daraus ein Bedienelement machen, sind dieselben, die BBS überall benutzt — eine
     * dunkle Kante für Kontrast auf jedem Untergrund, eine hellere Oberkante als Lichtkante,
     * und eine Spitze, die auf den Tick zeigt statt ihn zu überdecken.</p>
     *
     * <p>Gezeichnet wird zeilenweise, weil die Oberfläche pixelig ist: eine echte Diagonale
     * würde neben den Blockgrafiken von Minecraft falsch aussehen.</p>
     */
    private void fahne(UIContext context, int x, int oben, int farbe, boolean hervor)
    {
        int koerper = hervor ? Colors.lerp(farbe, Colors.WHITE, 0.25F) : farbe;
        int kante = Colors.A50;
        int licht = Colors.lerp(koerper, Colors.WHITE, 0.45F);

        int voll = KOPF_HOEHE - SPITZE;

        /* Erst die dunkle Kante als etwas größere Silhouette, dann den Körper darauf. */
        for (int i = 0; i <= SPITZE; i++)
        {
            int b = KOPF_BREITE - i;
            int y = oben + voll + i;

            context.batcher.box(x - b - 1, y, x + b + 1, y + 1, kante);
        }

        context.batcher.box(x - KOPF_BREITE - 1, oben - 1, x + KOPF_BREITE + 1, oben + voll, kante);
        context.batcher.box(x - KOPF_BREITE, oben, x + KOPF_BREITE, oben + voll, koerper);

        for (int i = 0; i < SPITZE; i++)
        {
            int b = KOPF_BREITE - i;
            int y = oben + voll + i;

            context.batcher.box(x - b, y, x + b, y + 1, koerper);
        }

        /* Lichtkante oben — ohne sie wirkt der Kopf flach. */
        context.batcher.box(x - KOPF_BREITE, oben, x + KOPF_BREITE, oben + 1, licht);
    }

    private int oben()
    {
        return TimelineRulerRenderer.getRulerBottom(this.area) + 1;
    }

    private Markers markers()
    {
        Film film = this.delegate.getFilm();

        return film == null ? null : film.markers;
    }

    private void setzeAmCursor()
    {
        Markers markers = this.markers();

        if (markers != null)
        {
            this.delegate.markLastUndoNoMerging();
            markers.setzeBei(this.delegate.getCursor());
        }
    }

    /**
     * Die Merkstelle unter dem Zeiger, oder {@code null}.
     *
     * <p>Getroffen wird der Kopf samt Beschriftung — nicht die dünne Linie darunter. Sonst
     * fängt die Spur Klicks ab, die dem Nutzer als Klick auf einen Clip gelten.</p>
     */
    private Marker unterZeiger(int mouseX, int mouseY)
    {
        Markers markers = this.markers();

        if (markers == null || mouseY < this.oben() || mouseY > this.oben() + KOPF_HOEHE)
        {
            return null;
        }

        FontRenderer font = this.getContext().batcher.getFont();

        for (Marker marker : markers.getList())
        {
            int x = this.clips.toGraphX(marker.tick.get());
            int breite = KOPF_BREITE * 2;
            String text = marker.label.get();

            if (!text.isEmpty())
            {
                breite += font.getWidth(text) + 5;
            }

            if (mouseX >= x - KOPF_BREITE && mouseX <= x - KOPF_BREITE + breite)
            {
                return marker;
            }
        }

        return null;
    }

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        Marker marker = this.unterZeiger(context.mouseX, context.mouseY);

        if (marker == null)
        {
            return false;
        }

        Markers markers = this.markers();

        this.delegate.markLastUndoNoMerging();

        if (context.mouseButton == 1)
        {
            markers.remove(marker);

            return true;
        }

        /* Linke Taste heisst erstmal NUR: der hier ist gefasst. Ob daraus ein Verschieben
         * oder ein Oeffnen wird, entscheidet das Loslassen — genau wie bei einem Clip. */
        this.gezogen = marker;
        this.griffVersatz = marker.tick.get() - this.clips.fromGraphX(context.mouseX);
        this.verschoben = false;

        return true;
    }

    @Override
    protected boolean subMouseReleased(UIContext context)
    {
        Marker marker = this.gezogen;

        this.gezogen = null;

        if (marker == null)
        {
            return false;
        }

        /* Losgelassen, ohne dass sich etwas bewegt hat → das war ein Klick, kein Zug. */
        if (!this.verschoben)
        {
            UIOverlay.addOverlay(context, new UIMarkerPanel(this.markers(), marker, this.delegate::fillData), 180, 130);
        }

        return true;
    }

    @Override
    public void render(UIContext context)
    {
        Markers markers = this.markers();

        if (markers != null && !markers.getList().isEmpty())
        {
            this.ziehe(context);
            this.zeichne(context, markers);
        }

        super.render(context);
    }

    /**
     * Den gefassten Marker der Maus folgen lassen.
     *
     * <p>Der Zug wird beim Zeichnen fortgeschrieben, nicht in einem Maus-Ereignis: BBS liefert
     * keine Bewegungs-Ereignisse an Elemente, {@code UIClips} macht sein Scrubbing aus demselben
     * Grund genauso (siehe {@code UIClips#handleInput}).</p>
     */
    private void ziehe(UIContext context)
    {
        if (this.gezogen == null)
        {
            return;
        }

        int neu = Math.max(0, this.clips.fromGraphX(context.mouseX) + this.griffVersatz);

        if (neu != this.gezogen.tick.get())
        {
            this.gezogen.tick.set(neu);
            this.verschoben = true;
        }
    }

    private void zeichne(UIContext context, Markers markers)
    {
        Area area = this.area;
        FontRenderer font = context.batcher.getFont();
        Marker ueberfahren = this.unterZeiger(context.mouseX, context.mouseY);

        context.batcher.clip(area, context);

        for (Marker marker : markers.getList())
        {
            int x = this.clips.toGraphX(marker.tick.get());

            if (x < area.x - KOPF_BREITE || x > area.ex() + KOPF_BREITE)
            {
                continue;
            }

            int farbe = marker.color.get();
            boolean drunter = ueberfahren == marker;

            this.fahne(context, x, this.oben(), farbe, drunter);

            /* Die Linie läuft durch die ganze Spur, aber halbdurchsichtig: sie zeigt die
             * Stelle, ohne die Clips darunter unlesbar zu machen. Unter dem Zeiger wird sie
             * kräftiger — so sieht man, welche Linie zu welchem Kopf gehört. */
            context.batcher.box(x, this.oben() + KOPF_HOEHE, x + 1, area.ey(),
                Colors.setA(farbe, drunter ? 0.75F : 0.35F));

            String text = marker.label.get();

            if (!text.isEmpty())
            {
                /* Auf dunklem Grund, sonst verschwindet heller Text auf hellen Clips. */
                context.batcher.textCard(text, x + KOPF_BREITE + 3,
                    this.oben() + (KOPF_HOEHE - font.getHeight()) / 2 + 1,
                    Colors.WHITE, Colors.A75, 2);
            }
        }

        context.batcher.unclip(context);
    }
}
