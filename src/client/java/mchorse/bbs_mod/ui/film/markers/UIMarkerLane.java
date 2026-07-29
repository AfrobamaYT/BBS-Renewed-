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
    private static final int KOPF_BREITE = 3;

    private final UIClips clips;
    private final IUIClipsDelegate delegate;

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
        }
        else
        {
            UIOverlay.addOverlay(context, new UIMarkerPanel(markers, marker, this.delegate::fillData), 180, 130);
        }

        return true;
    }

    @Override
    public void render(UIContext context)
    {
        Markers markers = this.markers();

        if (markers != null && !markers.getList().isEmpty())
        {
            this.zeichne(context, markers);
        }

        super.render(context);
    }

    private void zeichne(UIContext context, Markers markers)
    {
        Area area = this.area;
        FontRenderer font = context.batcher.getFont();

        context.batcher.clip(area, context);

        for (Marker marker : markers.getList())
        {
            int x = this.clips.toGraphX(marker.tick.get());

            if (x < area.x - KOPF_BREITE || x > area.ex() + KOPF_BREITE)
            {
                continue;
            }

            int farbe = marker.color.get();

            /* Die Linie läuft durch die ganze Spur, aber halbdurchsichtig: sie soll die
             * Stelle zeigen, ohne die Clips darunter unlesbar zu machen. */
            context.batcher.box(x, this.oben() + KOPF_HOEHE, x + 1, area.ey(), Colors.setA(farbe, 0.4F));

            /* Der Kopf ist das, was man anklickt — deshalb voll deckend. */
            context.batcher.box(x - KOPF_BREITE, this.oben(), x + KOPF_BREITE, this.oben() + KOPF_HOEHE, farbe);

            String text = marker.label.get();

            if (!text.isEmpty())
            {
                context.batcher.text(text, x + KOPF_BREITE + 2, this.oben() + (KOPF_HOEHE - font.getHeight()) / 2 + 1, Colors.WHITE);
            }
        }

        context.batcher.unclip(context);
    }
}
