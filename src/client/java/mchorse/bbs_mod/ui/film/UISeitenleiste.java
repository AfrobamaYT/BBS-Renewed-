package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.utils.UI;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Blenders N-Panel: eine Reiter-Leiste am Rand der Vorschau, in die sich Panels einhaengen.
 *
 * <p>Der Zweck ist nicht das eine Panel, sondern die ANMELDESTELLE. In Blender haengen
 * Addons ihre Bedienung als eigenen Reiter dort hinein (Item / Tool / View / Extreme PBR /
 * Mixamo …) — genau das soll hier moeglich sein, ohne dass jemand am Editor-Layout
 * herumschraubt. Ein Reiter meldet sich mit {@link #anmelden(String, Supplier)} an; die
 * Leiste baut ihn beim naechsten Aufbau selbst.
 *
 * <p>⛔ Die Registrierung liegt in einem LAZY-Halter ohne Initialisierer: ein neues
 * statisches Feld mit Zuweisung waere nach einem Hotswap null, weil {@code <clinit>} nicht
 * erneut laeuft (in diesem Baum mehrfach bezahlt).
 */
public class UISeitenleiste extends UIElement
{
    /**
     * Ein Reiter-Panel, dessen Inhalt sich aendern kann. Die Leiste ruft es je Frame —
     * ⛔ ohne das wird ein Reiter EINMAL gebaut und bleibt so: die Rig-Layer-Knoepfe
     * blieben leer, weil beim Bau noch kein Rig geladen war (gemessen, leeres Panel im
     * Bild trotz vorhandener Leiste).
     */
    public interface Auffrischbar
    {
        void auffrischen();
    }

    private static Map<String, Supplier<UIElement>> reiter;

    private String aktiv = "";
    private String gebautFuer = "";
    private boolean offen = true;
    private final UIElement streifen = new UIElement();
    private final UIElement inhalt = new UIElement();
    private UIElement gezeigt;

    /**
     * Einen Reiter anmelden. Der Bauer wird erst gerufen, wenn der Reiter zum ersten Mal
     * gezeigt wird — ein Addon zahlt also nichts, solange niemand hinschaut.
     */
    public static void anmelden(String name, Supplier<UIElement> bauer)
    {
        reiter().put(name, bauer);
    }

    public static Map<String, Supplier<UIElement>> reiter()
    {
        if (reiter == null)
        {
            reiter = new LinkedHashMap<>();
        }

        return reiter;
    }

    public UISeitenleiste()
    {
        /* column(..) liefert den Resizer, nicht das Element — Breite deshalb getrennt
         * setzen, sonst laeuft die Kette ins Leere. */
        /* ⛔ Breite UND Hoehe setzen. Ohne die Hoehe sass der Inhalt auf 190x34 und die
         * 14 Layer-Zeilen wurden abgeschnitten — das Panel war gebaut, nur unsichtbar
         * (gemessen: gezeigt=UIRigLagen kinder=14, inhalt.area=190x34). */
        this.streifen.column(2).vertical().stretch();
        this.streifen.w(74).h(1F);
        this.inhalt.column(4).vertical().stretch();
        this.inhalt.w(190).h(1F);

        this.add(this.inhalt, this.streifen);
        this.row(0);
    }

    /**
     * ⛔ Die Leiste zieht sich SELBST nach, statt dass der Editor sie tickt. Grund ist
     * nicht Eleganz: `UIFilmPanel` ist ein gelisteter Monolith und darf nur schrumpfen —
     * ein Tick-Aufruf dort waere drei Zeilen Wachstum. So braucht der Editor null Zeilen.
     */
    @Override
    public void render(mchorse.bbs_mod.ui.framework.UIContext context)
    {
        this.aktualisiere();

        super.render(context);
    }

    /** Baut die Leiste neu, wenn sich der Reiter-Satz oder die Auswahl geaendert hat. */
    public void aktualisiere()
    {
        String kennung = String.join("|", reiter().keySet()) + "#" + this.aktiv + "#" + this.offen;

        if (kennung.equals(this.gebautFuer))
        {
            if (this.gezeigt instanceof Auffrischbar a)
            {
                a.auffrischen();
            }

            return;
        }

        this.gebautFuer = kennung;
        this.streifen.removeAll();
        this.inhalt.removeAll();

        List<String> namen = new ArrayList<>(reiter().keySet());

        if (namen.isEmpty())
        {
            this.setVisible(false);

            return;
        }

        this.setVisible(true);

        if (!namen.contains(this.aktiv))
        {
            this.aktiv = namen.get(0);
        }

        /* Der Klapp-Knopf sitzt oben im Streifen, wie Blenders Pfeil am Rand. */
        this.streifen.add(new UIButton(IKey.raw(this.offen ? ">" : "<"), (b) ->
        {
            this.offen = !this.offen;
            this.gebautFuer = "";
            this.aktualisiere();
        }));

        for (String name : namen)
        {
            String kurz = name.length() > 9 ? name.substring(0, 9) : name;

            this.streifen.add(new UIButton(
                IKey.raw(name.equals(this.aktiv) && this.offen ? "[" + kurz + "]" : kurz), (b) ->
            {
                this.aktiv = name;
                this.offen = true;
                this.gebautFuer = "";
                this.aktualisiere();
            }));
        }

        this.inhalt.setVisible(this.offen);

        if (this.offen)
        {
            UIElement panel = reiter().get(this.aktiv).get();

            this.gezeigt = panel;

            if (panel != null)
            {
                this.inhalt.add(UI.scrollView(4, 4, panel));
            }
        }

        this.resize();
    }
}
