package mchorse.bbs_mod.ui.utils.context;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.keys.KeyCodes;
import mchorse.bbs_mod.utils.colors.Colors;
import com.mcstudio.nano.NanoDesign;

/**
 * Eine Menuezeile nach dem Entwurf (foundations/menues-overlays.html):
 * Symbol + Text + <b>Kuerzel rechtsbuendig</b>, Ueber-Zustand als blaues Rundeck,
 * zerstoerende Aktion rot, gesperrter Eintrag auf 38 %, Trennfuge ueber der Zeile.
 *
 * <p>⛔ Das Kuerzel wird NICHT neu erfunden: {@link #keys} traegt es laengst — der
 * {@link ContextMenuManager} registriert damit schon die Taste, solange das Menue offen ist.
 * Es wurde nur nie gezeichnet. Deshalb erscheint es ohne eine einzige geaenderte Aufrufstelle.</p>
 */
public class ContextAction
{
    /** Zeilenhoehe des Entwurfs. */
    public static final int HOEHE = 22;

    public Icon icon;
    public IKey label;
    public Runnable runnable;

    public IKey keyCategory;
    public int[] keys;
    public int order = -1;

    /** Zerstoerend — Text und Symbol rot, im Entwurf immer als letzter Eintrag. */
    public boolean rot;
    /** Gesperrt — 38 %, kein Ueber-Zustand, nicht ausloesbar. */
    public boolean gesperrt;
    /** Trennfuge ueber dieser Zeile: 1 px an der Oberkante statt einer leeren Zeile. */
    public boolean trennerDavor;
    /** Untermenue — rechts steht ein Pfeil statt eines Kuerzels. */
    public boolean untermenue;
    /** Lebende Tastenbelegung fuer das Kuerzel rechts. */
    public mchorse.bbs_mod.ui.utils.keys.KeyCombo combo;

    public ContextAction(Icon icon, IKey label, Runnable runnable)
    {
        this.icon = icon;
        this.label = label;
        this.runnable = runnable;
    }

    public ContextAction key(IKey keyCategory, int... keys)
    {
        this.keyCategory = keyCategory;

        return this.key(keys);
    }

    public ContextAction key(int... keys)
    {
        this.keys = keys;

        return this;
    }

    public ContextAction order(int order)
    {
        this.order = order;

        return this;
    }

    /**
     * Kuerzel aus der ECHTEN Tastenbelegung. ⛔ Nicht den Text fest verdrahten: der Nutzer
     * kann jede Taste umlegen, und die JSON schlaegt den Quelltext.
     */
    public ContextAction taste(mchorse.bbs_mod.ui.utils.keys.KeyCombo combo)
    {
        this.combo = combo;

        return this;
    }

    public ContextAction rot()
    {
        this.rot = true;

        return this;
    }

    public ContextAction gesperrt()
    {
        this.gesperrt = true;

        return this;
    }

    public ContextAction trenner()
    {
        this.trennerDavor = true;

        return this;
    }

    public ContextAction untermenue()
    {
        this.untermenue = true;

        return this;
    }

    /** Anwaehlbar? Gruppentitel und gesperrte Eintraege sind es nicht. */
    public boolean waehlbar()
    {
        return !this.gesperrt && this.runnable != null;
    }

    /** Das Kuerzel als Text, aus {@link #keys}. Leer, wenn die Aktion keine Taste hat. */
    public String kuerzel()
    {
        if (this.combo != null)
        {
            return this.combo.getKeyCombo();
        }

        if (this.keys == null || this.keys.length == 0)
        {
            return "";
        }

        StringBuilder b = new StringBuilder(KeyCodes.getName(this.keys[0]));

        for (int i = 1; i < this.keys.length; i++)
        {
            b.insert(0, KeyCodes.getName(this.keys[i]) + " + ");
        }

        return b.toString();
    }

    public int getWidth(FontRenderer font)
    {
        String k = this.kuerzel();
        int rechts = k.isEmpty() ? (this.untermenue ? 16 : 0) : font.getWidth(k) + 14;

        return 40 + font.getWidth(this.label.get()) + rechts;
    }

    public void render(UIContext context, FontRenderer font, int x, int y, int w, int h, boolean hover, boolean selected)
    {
        if (this.trennerDavor)
        {
            context.batcher.box(x + 6, y, x + w - 6, y + 1, Colors.A100 | NanoDesign.KOPF);
        }

        this.renderBackground(context, x, y, w, h, hover, selected);

        boolean an = hover && this.waehlbar();
        int textF = an ? Colors.WHITE : (this.rot ? NanoDesign.ROT : NanoDesign.TXT);
        int symbolF = an ? Colors.WHITE : (this.rot ? NanoDesign.ROT : NanoDesign.TXT_DIM);
        int kuerzelF = an ? Colors.WHITE : NanoDesign.TXT_OFF;
        int a = this.gesperrt ? Colors.A100 & 0x61000000 : Colors.A100;

        context.batcher.icon(this.icon, a | (symbolF & 0xFFFFFF), x + 8, y + h / 2, 0, 0.5F);
        context.batcher.text(this.label.get(), x + 30, y + (h - font.getHeight()) / 2 + 1, a | (textF & 0xFFFFFF), false);

        String k = this.kuerzel();

        if (!k.isEmpty())
        {
            context.batcher.text(k, x + w - 8 - font.getWidth(k),
                y + (h - font.getHeight()) / 2 + 1, a | (kuerzelF & 0xFFFFFF), false);
        }
    }

    protected void renderBackground(UIContext context, int x, int y, int w, int h, boolean hover, boolean selected)
    {
        if (hover && this.waehlbar())
        {
            context.batcher.rundeck(x + 2, y + 1, w - 4, h - 2, 4F, Colors.A100 | BBSSettings.primaryColor.get());
        }
    }
}
