package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.UISeitenleiste;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UISection;

/**
 * Der Rig-Reiter der Seitenleiste — beide Rigify-Panels in EINEM Reiter.
 *
 * <p>⛔ Warum zusammen: Rigify erzeugt fuer sein Rig zwei N-Panel-Bloecke im
 * SELBEN Reiter — {@code Rig Main Properties} (die Regler: IK/FK, IK_Stretch,
 * pole_vector …) und darunter {@code Rig Layers}. Bei uns hing nur die Lagen-
 * Leiste in der Seitenleiste; die Regler lagen ausschliesslich in der klassischen
 * Eigenschaften-Spalte, die im Editor meist zusammengeschoben ist (gemessen
 * 2026-08-15: dort waren ALLE Felder 33 px breit, nicht nur die Regler). Damit war
 * Blenders "IK/FK ist ein Klick" bei uns praktisch unerreichbar.
 *
 * <p>Der Reiter frischt selbst auf: {@link UIRigRegler} braucht die Entity je Bild,
 * und ein einmal gebauter Reiter bliebe sonst auf dem Rig stehen, das beim
 * Aufklappen gerade geladen war.
 */
public class UIRigReiter extends UIElement implements UISeitenleiste.Auffrischbar
{
    private final UIRigLagen lagen = new UIRigLagen();
    private final UIRigRegler regler = new UIRigRegler();

    public static UIElement neu()
    {
        return new UIRigReiter();
    }

    public UIRigReiter()
    {
        this.column(4).vertical().stretch();

        /* Reihenfolge zaehlt: auffrischen() holt die erste Sektion als die des Reglers. */
        UISection oben = new UISection(IKey.raw("Rig Main Properties"));
        UISection unten = new UISection(IKey.raw("Rig Layers"));

        oben.fields.add(this.regler);
        unten.fields.add(this.lagen);

        this.add(oben, unten);
        this.auffrischen();
    }

    @Override
    public void auffrischen()
    {
        this.lagen.aktualisiere();

        UIFilmPanel panel = mchorse.bbs_mod.BBSModClient.getDashboard() == null ? null
            : mchorse.bbs_mod.BBSModClient.getDashboard().panels.panel instanceof UIFilmPanel p
                ? p : null;

        if (panel == null)
        {
            return;
        }

        /* Die Pose der REPLAY, nicht die der Entity — dieselbe Quelle, aus der die
         * Eigenschaften-Spalte den Regler speist (siehe UIRigRegler#pose). */
        Replay replay = panel.replayEditor.getReplay();

        this.regler.aktualisiere(panel.getController().getCurrentEntity(),
            replay != null && replay.form.get() instanceof mchorse.bbs_mod.forms.forms.ModelForm mf
                ? mf.pose.get() : null);

        /* ⛔ Ohne Regler rechnet der Spalten-Resizer die Hoehe aus lauter Abstaenden und landet
         * bei -3; ein Element mit NEGATIVER Hoehe zeichnet einen Streifen ueber den Nachbarn —
         * sichtbar als loses Fragment oben in der Seitenleiste. Eine leere Sektion braucht
         * ohnehin niemand, also verschwindet sie ganz. */
        /* An der HOEHE messen, nicht an der Kinderzahl: der Regler behaelt beim Leeren
         * Platzhalter-Kinder, seine gerechnete Hoehe faellt aber auf -3. */
        boolean leer = this.regler.getChildren().isEmpty() || this.regler.area.h <= 0;
        /* ⛔ Die Sektion aus den KINDERN holen, nicht aus einem Feld: ein nachtraeglich
         * hinzugefuegtes Instanzfeld ist nach einem Hotswap null, und die Bedingung
         * uebersprang sich dann still (genau so lief der erste Anlauf ins Leere). */
        UISection oben = this.getChildren().isEmpty() ? null
            : this.getChildren().get(0) instanceof UISection sec ? sec : null;

        if (oben != null && oben.isVisible() == leer)
        {
            oben.setVisible(!leer);
            this.resize();
        }
    }
}
