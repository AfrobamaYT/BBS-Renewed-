package mchorse.bbs_mod.ui.film.markers;

import mchorse.bbs_mod.film.markers.Marker;
import mchorse.bbs_mod.film.markers.Markers;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.utils.UI;

/**
 * Was hinter einem Klick auf eine Merkstelle steckt: Text, Farbe, Tick, Löschen.
 *
 * <p>Die Farbe hängt an {@link UIColor} — dasselbe Feld wie in den Einstellungen, also
 * BBS' eigener HSV-Wähler mit Alpha. Der Nutzer soll Merkstellen nach Art einfärben
 * („rot = kaputt", „grün = fertig"), deshalb ist die Farbe kein Beiwerk, sondern das
 * zweite Feld gleich unter dem Text.</p>
 */
public class UIMarkerPanel extends UIOverlayPanel
{
    public UITextbox label;
    public UIColor color;
    public UITrackpad tick;
    public UIButton remove;

    public UIMarkerPanel(Markers markers, Marker marker, Runnable nachAenderung)
    {
        super(MarkerKeys.TITLE);

        this.label = new UITextbox(200, (t) ->
        {
            marker.label.set(t);
            nachAenderung.run();
        });
        this.label.setText(marker.label.get());
        this.label.tooltip(MarkerKeys.LABEL);

        this.color = new UIColor((c) ->
        {
            marker.color.set(c);
            nachAenderung.run();
        });
        this.color.withAlpha();
        this.color.setColor(marker.color.get());
        this.color.tooltip(MarkerKeys.COLOR);

        this.tick = new UITrackpad((v) ->
        {
            marker.tick.set(Math.max(0, v.intValue()));
            nachAenderung.run();
        });
        this.tick.limit(0).integer();
        this.tick.setValue(marker.tick.get());
        this.tick.tooltip(MarkerKeys.TICK);

        this.remove = new UIButton(MarkerKeys.REMOVE, (b) ->
        {
            markers.remove(marker);
            nachAenderung.run();
            this.close();
        });

        UIScrollView scroll = UI.scrollView(5, 6, this.label, this.color, this.tick, this.remove);

        scroll.full(this.content);
        this.content.add(scroll);
    }
}
