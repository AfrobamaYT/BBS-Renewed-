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
 * <p>Die Paarung links/rechts ist ABGELEITET, nicht abgetippt: zwei Layer bilden eine
 * Zeile, wenn sich ihre Namen nur in {@code .L}/{@code .R} unterscheiden. Damit stimmt das
 * Layout auch fuer Rigs, deren Layer anders heissen — ein fest verdrahtetes Rigify-Layout
 * waere genau die Eigenerfindung, die beim naechsten Rig danebenliegt.
 */
public class UIRigLagen extends UIElement
{
    private String gebautFuer = "";

    public UIRigLagen()
    {
        this.column().vertical().stretch();
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

        for (List<String> zeile : paare(new ArrayList<>(lagen.keySet())))
        {
            UIElement reihe = UI.row(2);

            for (String name : zeile)
            {
                reihe.add(this.knopf(name));
            }

            this.add(reihe);
        }

        this.resize();
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

    private UIButton knopf(String name)
    {
        boolean an = RigCtrl.lagen().getOrDefault(name, true);
        /* Blender faerbt eingeschaltete Layer blau. BBS' Knopf kennt keine zwei Farben,
         * also traegt der ausgeblendete Zustand ein Minus im Text — sichtbar, ohne eine
         * eigene Zeichenroutine zu erfinden. */
        UIButton b = new UIButton(IKey.raw(an ? name : "— " + name), (x) ->
        {
            RigCtrl.schalteLage(name, !RigCtrl.lagen().getOrDefault(name, true));
            this.gebautFuer = "";
            this.aktualisiere();
        });

        b.tooltip(IKey.raw(an ? name + " — sichtbar, klicken zum Ausblenden"
            : name + " — ausgeblendet, klicken zum Einblenden"));

        return b;
    }
}
