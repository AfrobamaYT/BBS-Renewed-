package mchorse.bbs_mod.ui.film.regie;

import mchorse.bbs_mod.film.regie.Regie;
import mchorse.bbs_mod.film.regie.RegieMarker;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;

/** Felder des Regie-Reiters: der gewaehlte Marker oben, der Startpunkt darunter. */
public class UIRegieFelder extends UIElement
{
    public final UITextbox name;
    public final UITrackpad start;
    public final UITrackpad ende;
    public final UIButton hierher;
    public final UIButton entfernen;
    public final UITrackpad anfang;
    public final UITrackpad vorlauf;
    public final UITrackpad richtung;
    private final UIElement marker = new UIElement();

    UIRegieFelder(RegieBedienung bedienung)
    {
        this.name = new UITextbox(120, (text) -> this.mitMarker(bedienung, (m) -> bedienung.aendern(m.name, (w) -> w.set(text))));
        this.start = new UITrackpad((wert) -> bedienung.zeit(false, wert));
        this.ende = new UITrackpad((wert) -> bedienung.zeit(true, wert));
        this.start.limit(0, Integer.MAX_VALUE / 20D).increment(0.05D);
        this.ende.limit(0, Integer.MAX_VALUE / 20D).increment(0.05D);
        this.hierher = new UIButton(IKey.raw("Move to camera view"), (b) -> bedienung.hierher());
        this.entfernen = new UIButton(IKey.raw("Remove marker"), (b) -> bedienung.entfernen());
        this.anfang = new UITrackpad((wert) -> bedienung.aendern(bedienung.regie().anfang, (w) -> w.set((int) Math.round(wert * 20D))));
        this.vorlauf = new UITrackpad((wert) -> bedienung.aendern(bedienung.regie().vorlauf, (w) -> w.set((int) Math.round(wert * 20D))));
        this.anfang.limit(0, Integer.MAX_VALUE / 20D).increment(0.05D);
        this.vorlauf.limit(0, 30D).increment(0.05D);
        this.richtung = new UITrackpad((wert) -> bedienung.aendern(bedienung.regie().startYaw, (w) -> w.set(wert.floatValue())));
        this.richtung.limit(-180D, 180D).increment(5D);

        this.marker.column(3).vertical().stretch();
        this.marker.add(this.name, new UILabel(IKey.raw("Start (s)")).h(12), this.start,
            new UILabel(IKey.raw("End (s)")).h(12), this.ende, this.hierher, this.entfernen);
        this.column(3).vertical().stretch();
        this.add(this.marker, new UILabel(IKey.raw("Section start (s)")).h(12), this.anfang,
            new UILabel(IKey.raw("Lead-in (s)")).h(12), this.vorlauf,
            new UILabel(IKey.raw("Start facing (°)")).h(12), this.richtung);
    }

    private void mitMarker(RegieBedienung bedienung, java.util.function.Consumer<RegieMarker> was)
    {
        RegieMarker m = bedienung.gewaehlt();

        if (m != null && !m.name.get().equals(this.name.getText()))
        {
            was.accept(m);
        }
    }

    public void fuellen(Regie regie, RegieMarker m)
    {
        this.setVisible(regie != null);

        /* Aushaengen statt verstecken: der Spalten-Resizer zaehlt unsichtbare Kinder mit, die
         * leere Flaeche schob die Startpunkt-Felder aus dem Bild (gemessen). */
        if ((m != null) != (this.marker.getParent() == this))
        {
            if (m != null)
            {
                this.prepend(this.marker);
            }
            else
            {
                this.marker.removeFromParent();
            }
        }

        if (m != null)
        {
            if (!this.name.isFocused())
            {
                this.name.setText(m.name.get());
            }

            this.start.setValue(m.start.get() / 20D);
            this.ende.setValue(m.ende.get() / 20D);
        }

        if (regie != null)
        {
            this.anfang.setValue(regie.anfang.get() / 20D);
            this.vorlauf.setValue(regie.vorlauf.get() / 20D);
            this.richtung.setValue(net.minecraft.util.Mth.wrapDegrees(regie.startYaw.get()));
        }
    }
}
