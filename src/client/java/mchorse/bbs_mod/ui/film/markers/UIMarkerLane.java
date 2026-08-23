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
public class UIMarkerLane extends UIElement implements com.mcstudio.nano.ZonenQuelle
{
    /** Höhe des anklickbaren Kopfes am oberen Rand der Spur. */
    private static final int KOPF_HOEHE = 6;

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
    /**
     * Die Merkstellen-Fahne: ein glatter Pfeil, kein Treppenbild.
     *
     * <p>⛔ Vorher lief die Spitze als Schleife von 1-Pixel-Kaesten — auf jeder Skala eine
     * sichtbare Treppe. Nutzer am 2026-08-21: „das war ein grauer pfeil der smooth war
     * anstatt den alten blauen pixel optischen marker." Jetzt ein konvexes Polygon ueber
     * {@code batcher.polygon} (P2, AA-Saum), also dieselbe Kantenglaettung wie Rundecke und
     * Kurven.</p>
     */
    private void fahne(UIContext context, int x, int oben, int farbe, boolean hervor)
    {
        /* ⛔ GRAU, nicht in der Merkstellen-Farbe. Nutzerwunsch 2026-08-21: der Kopf soll ein
         * ruhiges Bedienelement sein, keine Farbmarke — die Farbe traegt weiterhin die Linie
         * darunter, die zur Stelle fuehrt. */
        int grau = 0x9A9A9A;
        int koerper = hervor ? Colors.lerp(grau, Colors.WHITE, 0.3F) : grau;
        int licht = Colors.lerp(koerper, Colors.WHITE, 0.45F);

        float b = KOPF_BREITE;

        /* ⛔ DREIECK, nicht Fahne. Nutzerwunsch 2026-08-21: der Kopf soll als Zeiger lesbar
         * sein — breit oben, spitz zur Stelle hin. Die frueheren Rechteck-plus-Spitze-Form
         * trug oben zu viel Masse. */
        /* ⛔ Die Spitze liegt auf der MITTE der Linie, nicht auf ihrer linken Kante. Die
         * Linie laeuft `box(x, …, x + 1, …)`, ihr Zentrum ist also x + 0.5 — mit der Spitze
         * bei x stand der Zeiger einen halben Pixel daneben, und genau das ist beim
         * Hinsehen aufgefallen. */
        float mitte = x + 0.5F;
        float[] px = {mitte - b, mitte + b, mitte};
        float[] py = {oben, oben, oben + KOPF_HOEHE};

        context.batcher.polygon(px, py, Colors.A100 | (koerper & 0xFFFFFF));

        /* Lichtkante oben — ohne sie wirkt der Kopf flach. */
        context.batcher.rundeck(mitte - b, oben, b * 2F, 1.2F, 0.5F, licht);
    }

    /**
     * Oberkante des Merkstellen-Kopfes.
     *
     * <p>⛔ IN DER LINEAL-ZEILE, nicht darunter. Vorher sass der Kopf unter dem Lineal und
     * schob die Spuren nach unten; der Nutzer will ihn dort, wo die Skala steht — so macht
     * es auch jedes Schnittprogramm. Der Kopf endet knapp ueber der Linealunterkante,
     * damit die Zahlen lesbar bleiben.</p>
     */
    private int oben()
    {
        return TimelineRulerRenderer.getRulerBottom(this.area) - KOPF_HOEHE;
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
            float[] r = this.griffRechteck(marker, font);

            if (mouseX >= r[0] && mouseX <= r[0] + r[2])
            {
                return marker;
            }
        }

        return null;
    }

    /**
     * Das Griff-Rechteck eines Markers: {x, y, breite, hoehe}.
     *
     * <p>⛔ EINE Rechnung fuer Treffertest UND Abnahme-Zone. Zwei Rechnungen waeren zwei
     * Wahrheiten, und eine Zone, die neben dem Treffer liegt, meldet Fehler, die sie selbst
     * erzeugt hat.</p>
     */
    private float[] griffRechteck(Marker marker, FontRenderer font)
    {
        int x = this.clips.toGraphX(marker.tick.get());
        int breite = KOPF_BREITE * 2;
        String text = marker.label.get();

        if (!text.isEmpty())
        {
            breite += font.getWidth(text) + 5;
        }

        return new float[] {x - KOPF_BREITE, this.oben(), breite, KOPF_HOEHE};
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

    /** ⛔ Lazy: ein per Hotswap neu hinzugekommenes Feld ist auf bestehenden Instanzen null. */
    private com.mcstudio.nano.ZonenMelder zonen;

    private com.mcstudio.nano.ZonenMelder zonen()
    {
        if (this.zonen == null)
        {
            this.zonen = new com.mcstudio.nano.ZonenMelder();
        }

        return this.zonen;
    }

    @Override
    public java.util.List<com.mcstudio.nano.ZonenMelder.Zone> gemeldeteZonen()
    {
        return this.zonen().alle();
    }

    @Override
    public void render(UIContext context)
    {
        Markers markers = this.markers();
        com.mcstudio.nano.ZonenMelder melder = this.zonen();

        melder.beginnen();

        if (markers != null && !markers.getList().isEmpty())
        {
            this.ziehe(context);
            this.zeichne(context, markers);

            FontRenderer font = context.batcher.getFont();

            for (Marker marker : markers.getList())
            {
                float[] r = this.griffRechteck(marker, font);

                melder.melden("marker/" + marker.tick.get(), r[0], r[1], r[2], r[3]);
            }
        }

        melder.uebernehmen();

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
            /* ⛔ GRAU wie der Kopf. Vorher lief die Linie in der Merkstellen-Farbe und stach
             * neben dem grauen Zeiger als blauer Strich heraus — der Nutzer hat genau das
             * angestrichen. Die Farbe bleibt als Eigenschaft erhalten, sie faerbt nur nicht
             * mehr die Zeitleiste ein. */
            context.batcher.box(x, this.oben() + KOPF_HOEHE, x + 1, area.ey(),
                Colors.setA(0x9A9A9A, drunter ? 0.7F : 0.3F));

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
