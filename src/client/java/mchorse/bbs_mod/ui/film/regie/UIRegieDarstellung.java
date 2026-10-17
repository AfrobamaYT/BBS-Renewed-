package mchorse.bbs_mod.ui.film.regie;

import mchorse.bbs_mod.film.regie.Regie;
import mchorse.bbs_mod.film.regie.RegieDarstellung;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.utils.UI;

/** Lazily added to existing sidebars too; all new controls/state live in this new class. */
public final class UIRegieDarstellung extends UIElement
{
    private final UIButton stil;
    private final UIToggle zeit;
    private final UIToggle text;

    private UIRegieDarstellung(RegieBedienung bedienung)
    {
        this.stil = new UIButton(IKey.raw("Marker style: Simple"), button ->
        {
            RegieDarstellung daten = RegieDarstellung.fuer(bedienung.regie());
            bedienung.aendern(daten.stil, wert -> wert.set(wert.get() == 0 ? 1 : 0));
        });
        this.zeit = new UIToggle(IKey.raw("Time bar"), button ->
            bedienung.aendern(RegieDarstellung.fuer(bedienung.regie()).zeit, wert -> wert.set(button.getValue())));
        this.text = new UIToggle(IKey.raw("Marker text"), button ->
            bedienung.aendern(RegieDarstellung.fuer(bedienung.regie()).text, wert -> wert.set(button.getValue())));
        this.column(4).vertical().stretch();
        this.add(this.stil, UI.row(4, this.zeit, this.text));
    }

    static void auffrischen(UIRegieReiter reiter, Regie regie)
    {
        java.util.List<UIRegieDarstellung> kinder = reiter.getChildren(UIRegieDarstellung.class);
        UIRegieDarstellung ui;

        if (kinder.isEmpty())
        {
            ui = new UIRegieDarstellung(reiter.bedienung);
            reiter.addBefore(reiter.ort.getParent(), ui);
            reiter.resize();
        }
        else ui = kinder.get(0);

        ui.stil.setEnabled(regie != null);
        boolean einfach = regie != null && RegieDarstellung.einfach(regie);
        ui.stil.label = IKey.raw(einfach ? "Marker style: Simple" : "Marker style: Detailed");
        ui.zeit.setEnabled(einfach);
        ui.text.setEnabled(einfach);
        ui.zeit.setValue(regie != null && RegieDarstellung.zeit(regie));
        ui.text.setValue(regie != null && RegieDarstellung.text(regie));
    }
}
