package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.cubic.ik.RigCtrl;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.utils.UI;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rig Layers — Blenders Knopfleiste aus der Seitenleiste (Item &gt; Rig Layers).
 *
 * <p>Ein Rigify-Mensch hat 220 Controls in 20 Layern. Alle gleichzeitig zu sehen ist der
 * Grund, warum das Rig unbenutzbar wirkt; in Blender blendet man aus, woran man gerade
 * nicht arbeitet. Die Layer sind Blenders Bone-Collections, ihre Reihenfolge und ihr
 * Anfangszustand kommen 1:1 aus dem Export.
 *
 * <p>⭐ Das Layout wird NICHT abgeleitet, es steht im Rig: Rigify legt an jeder
 * Bone-Collection {@code rigify_ui_row} (die Zeile im Panel) und {@code rigify_ui_title}
 * (die dort gezeigte Beschriftung) ab. Der Export bringt beides mit, hier wird nur noch
 * danach angeordnet — deshalb steht "Face" breit und darunter "(Primary)"/"(Secondary)"
 * nebeneinander, genau wie in Blender. Luecken zwischen zwei Zeilennummern sind Absicht
 * und werden zu Abstand (Rigifys erzeugtes {@code rig_ui.py} legt fuer eine leere Zeile
 * eine leere Reihe an).
 *
 * <p>⚠️ Aeltere Extrakte kennen die Felder nicht (alle Zeilen 0). Dann faellt die Leiste
 * auf ihre eigene Paarbildung nach {@code .L}/{@code .R} zurueck, statt alles
 * untereinander zu stapeln.
 */
public class UIRigLagen extends UIElement implements mchorse.bbs_mod.ui.film.UISeitenleiste.Auffrischbar
{
    private String gebautFuer = "";

    public UIRigLagen()
    {
        this.column().vertical().stretch();
    }

    @Override
    public void auffrischen()
    {
        this.aktualisiere();
    }

    /** Baut die Leiste neu, wenn sich der Layer-Satz geaendert hat. Je Frame billig. */
    public void aktualisiere()
    {
        Map<String, Boolean> lagen = RigCtrl.lagen();
        String kennung = String.join("|", lagen.keySet());

        if (kennung.equals(this.gebautFuer))
        {
            return;
        }

        this.gebautFuer = kennung;
        this.removeAll();

        for (List<RigCtrl.Lage> zeile : this.zeilen())
        {
            UIElement reihe = UI.row(2);

            if (zeile.isEmpty())
            {
                /* Leere Rigify-Zeile = Gruppenabstand. Ohne Kind hat die Reihe keine
                 * Hoehe, darum eine halbe Knopfhoehe fest. */
                reihe.h(8);
            }

            for (RigCtrl.Lage lage : zeile)
            {
                reihe.add(this.knopf(lage.name(), lage.titel()));
            }

            this.add(reihe);
        }

        this.resize();
    }

    /**
     * Die Zeilen des Panels. Erste Wahl ist Rigifys eigenes Layout; nur wenn kein Layer
     * eine Zeile mitbringt, wird nach {@code .L}/{@code .R} gepaart.
     */
    private List<List<RigCtrl.Lage>> zeilen()
    {
        List<RigCtrl.Lage> info = RigCtrl.lagenReihen();
        int hoechste = 0;

        for (RigCtrl.Lage l : info)
        {
            hoechste = Math.max(hoechste, l.zeile());
        }

        if (hoechste == 0)
        {
            List<List<RigCtrl.Lage>> raus = new ArrayList<>();

            for (List<String> zeile : paare(new ArrayList<>(RigCtrl.lagen().keySet())))
            {
                List<RigCtrl.Lage> reihe = new ArrayList<>();

                for (String name : zeile)
                {
                    reihe.add(new RigCtrl.Lage(name, 0, name));
                }

                raus.add(reihe);
            }

            return raus;
        }

        List<List<RigCtrl.Lage>> raus = new ArrayList<>();

        for (int z = 1; z <= hoechste; z++)
        {
            List<RigCtrl.Lage> reihe = new ArrayList<>();

            for (RigCtrl.Lage l : info)
            {
                if (l.zeile() == z)
                {
                    reihe.add(l);
                }
            }

            raus.add(reihe);
        }

        return raus;
    }

    /**
     * Namen zu Zeilen: ein L/R-Paar teilt sich eine Zeile, alles andere steht allein.
     * Die Reihenfolge bleibt die aus Blender.
     */
    private static List<List<String>> paare(List<String> namen)
    {
        Map<String, List<String>> zeilen = new LinkedHashMap<>();

        for (String name : namen)
        {
            String schluessel = name.replace(".L", ".@").replace(".R", ".@");

            zeilen.computeIfAbsent(schluessel, k -> new ArrayList<>()).add(name);
        }

        return new ArrayList<>(zeilen.values());
    }

    private UIButton knopf(String name, String titel)
    {
        boolean an = RigCtrl.lagen().getOrDefault(name, true);
        UIButton b = new UIRundKnopf(IKey.raw(titel), (x) ->
        {
            RigCtrl.schalteLage(name, !RigCtrl.lagen().getOrDefault(name, true));
            this.gebautFuer = "";
            this.aktualisiere();
        });

        /* Blender faerbt eingeschaltete Layer BLAU und laesst ausgeschaltete grau —
         * UIButton kann genau das ueber color(). Vorher stand ein Minus im Text, was den
         * Namen verschob und schlechter zu lesen war. */
        b.color(an ? 0x4772b3 : 0x3a3a3a);
        /* Der Tooltip nennt den VOLLEN Collection-Namen — auf dem Knopf steht nur
         * Rigifys Kurztitel ("(Primary)"), der allein nicht sagt, wozu er gehoert. */
        b.tooltip(IKey.raw(an ? name + " — sichtbar, klicken zum Ausblenden"
            : name + " — ausgeblendet, klicken zum Einblenden"));

        return b;
    }
}
