package mchorse.bbs_mod.ui.film.regie;

import mchorse.bbs_mod.film.regie.Regie;
import mchorse.bbs_mod.film.regie.RegieAnsicht;
import mchorse.bbs_mod.film.regie.RegieMarker;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.UISeitenleiste;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;
import mchorse.bbs_mod.ui.framework.elements.utils.UIText;
import mchorse.bbs_mod.ui.hilfe.Guides;
import mchorse.bbs_mod.ui.hilfe.UIHilfeClipKopf;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.List;

/**
 * Reiter „Regie" der Viewport-Seitenleiste (ersetzt den alten Timers-Reiter): Marker anlegen,
 * benennen, ihr Zeitfenster setzen, entfernen, und den Startpunkt setzen. Nummern wie auf den
 * Karten: nach Beginn sortiert. Was die Knoepfe am Film tun, steht in {@link RegieBedienung}.
 */
public class UIRegieReiter extends UIElement implements UISeitenleiste.Auffrischbar, Guides.ClipQuelle
{
    final RegieBedienung bedienung;
    public final UIButton ort;
    public final UIButton blick;
    public final UIButton start;
    public final UIList<RegieMarker> liste;
    public final UIRegieFelder felder;
    public final UIText status = new UIText();
    private final UIFilmPanel panel;
    private String stand = "";

    public UIRegieReiter(UIFilmPanel panel)
    {
        this.panel = panel;
        this.bedienung = new RegieBedienung(panel);
        this.bedienung.geaendert = () ->
        {
            this.stand = "";
            this.auffrischen();
        };
        this.ort = new UIButton(IKey.raw("+ Location"), (b) -> this.bedienung.neu(RegieMarker.ORT));
        this.blick = new UIButton(IKey.raw("+ Look"), (b) -> this.bedienung.neu(RegieMarker.BLICK));
        this.start = new UIButton(IKey.raw("Set start here"), (b) -> this.bedienung.startHier());
        this.liste = new UIList<>((auswahl) ->
        {
            this.bedienung.gewaehlt = auswahl.isEmpty() ? "" : auswahl.get(0).uuid.get();
            this.bedienung.fehler = "";
            this.auffrischen();
        })
        {
            @Override
            protected String elementToString(UIContext context, int index, RegieMarker m)
            {
                return (index + 1) + " · " + m.name.get() + "   "
                    + RegieAnsicht.zahl(m.start.get() / 20F) + "–" + RegieAnsicht.zahl(m.ende.get() / 20F) + " s";
            }
        };
        this.liste.h(90);
        this.liste.background();
        this.felder = new UIRegieFelder(this.bedienung);

        this.column(4).vertical().stretch().padding(4);
        /* The status sits right under the buttons: at the bottom it was below the visible panel,
         * so a failed "+ Location" looked like nothing happened (audit 2026-10-14). */
        this.add(new UIHilfeClipKopf("Recording guide", this.hilfeClip()), UI.row(3, this.ort, this.blick), this.status, this.start, this.liste, this.felder);
        this.auffrischen();
    }

    @Override
    public String hilfeClip()
    {
        return "recording_guide";
    }

    @Override
    public void auffrischen()
    {
        Regie regie = this.bedienung.regie();
        RegieMarker m = this.bedienung.gewaehlt();
        List<RegieMarker> sortiert = regie == null ? List.of() : RegieAnsicht.sortiert(regie);
        StringBuilder neu = new StringBuilder(regie == null ? "-" : this.panel.getData().getId());

        if (regie != null)
        {
            neu.append('|').append(regie.startGesetzt.get()).append(regie.anfang.get()).append(':').append(regie.vorlauf.get())
                .append(':').append(regie.startYaw.get());

            for (RegieMarker w : sortiert)
            {
                neu.append('|').append(w.uuid.get()).append(w.name.get()).append(':').append(w.start.get()).append(':').append(w.ende.get());
            }
        }

        String fehler = this.bedienung.fehler;
        String stand = neu.append('|').append(this.bedienung.gewaehlt).append('|').append(fehler).toString();

        if (stand.equals(this.stand))
        {
            return;
        }

        this.stand = stand;
        this.ort.setEnabled(regie != null);
        this.blick.setEnabled(regie != null);
        this.start.setEnabled(regie != null);
        this.liste.setList(sortiert);
        this.liste.setCurrent(m);
        this.felder.fuellen(regie, m);
        this.status.text(!fehler.isEmpty() ? fehler
            : "New markers go where the camera looks; their window starts at the playhead.")
            .color(fehler.isEmpty() ? Colors.WHITE : Colors.RED, true);
        (this.getParent() == null ? this : this.getParent()).resize();
    }
}
