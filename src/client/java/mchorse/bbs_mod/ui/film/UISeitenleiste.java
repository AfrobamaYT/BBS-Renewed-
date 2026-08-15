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

    /** Breite des Reiter-Streifens am Aussenrand. */
    private static final int STREIFEN = 58;

    private static Map<String, Supplier<UIElement>> reiter;

    private String aktiv = "";
    private String gebautFuer = "";
    /* ⛔ Zu bis jemand sie aufmacht — wie Blenders N-Panel. Offen als Vorgabe verschluckt
     * kleine Vorschauen: bei 215 px Breite blieben vom Inhalt 15 px uebrig (gemessen). */
    private boolean offen = false;
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
        /* ⛔ Hausmuster: relative(..) + x/y/w/h, wie ueberall im Editor. Mein erster
         * Versuch steckte Streifen und Inhalt in einen row-Resizer, der die Hoehe selbst
         * bestimmt — der Inhalt bekam 190x34 und die 14 Layer-Zeilen wurden
         * abgeschnitten (gemessen). Der Reiter-Streifen sitzt AUSSEN rechts wie in
         * Blender, der Inhalt links davon. */
        this.streifen.relative(this).x(1F, -STREIFEN).y(0).w(STREIFEN).h(1F);
        this.streifen.column(2).stretch().padding(3);

        this.inhalt.relative(this).x(0).y(0).w(1F, -STREIFEN).h(1F);
        this.streifen.setVisible(true);

        /* Hintergrund, damit es ein Panel ist und keine schwebenden Knoepfe. */
        this.add(new mchorse.bbs_mod.ui.framework.elements.utils.UIRenderable((context) ->
            context.batcher.box(this.inhalt.area.x, this.inhalt.area.y,
                this.inhalt.area.ex(), this.inhalt.area.ey(),
                mchorse.bbs_mod.utils.colors.Colors.A75 | mchorse.bbs_mod.BBSSettings.primaryColor(0))));
        this.add(this.inhalt, this.streifen);
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

        /* Zu: nur der Streifen belegt Platz. Offen: Streifen plus ein brauchbarer
         * Inhaltsbereich, aber nie mehr als die halbe Vorschau. */
        if (this.getParent() != null)
        {
            int haelfte = Math.max(this.getParent().area.w / 2, STREIFEN + 60);

            this.w(this.offen ? Math.min(STREIFEN + 210, haelfte) : STREIFEN);
        }

        if (this.offen)
        {
            UIElement panel = reiter().get(this.aktiv).get();

            this.gezeigt = panel;

            if (panel != null)
            {
                /* ⛔ Der Rollbereich braucht eine EIGENE Groesse. Unter dem
                 * column-Resizer des Inhalts kam er auf 202x0 — das Panel darin war
                 * fertig (194x263, 14 Zeilen) und trotzdem unsichtbar (gemessen). */
                UIElement roll = UI.scrollView(4, 4, panel);

                roll.relative(this.inhalt).x(0).y(0).w(1F).h(1F);
                this.inhalt.add(roll);
            }
        }

        this.resize();
    }
}
