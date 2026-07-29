package mchorse.bbs_mod.ui.utils.keys;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;
import java.util.function.IntPredicate;
import java.util.function.Supplier;

/**
 * Keybind class
 */
public class Keybind
{
    private IKey label;
    private IKey category;

    private KeyCombo combo;
    public Runnable callback;
    public boolean inside;
    public boolean strict;
    public Supplier<Boolean> active;

    /** Die tastenkombination dieses keybinds.
     *
     *  MERGE 2026-07-27: getter aus dem CML-fork — unsere autonomie-schicht (UiInvoke) liest
     *  darueber die belegung, um keybinds programmatisch auszuloesen. */
    public KeyCombo getCombo()
    {
        return this.combo;
    }

    public Keybind(KeyCombo combo, Runnable callback)
    {
        this.combo = combo;
        this.callback = callback;
    }

    public Keybind inside()
    {
        this.inside = true;

        return this;
    }

    /**
     * Require an exact modifier match: the keybind won't fire if a shift/ctrl/alt
     * key is held that isn't part of its combo. Use this on a plain-key bind that
     * would otherwise shadow a longer combo on the same key living in another
     * element (e.g. plain {@code T} vs {@code Shift + T}).
     */
    public Keybind strict()
    {
        this.strict = true;

        return this;
    }

    public Keybind active(Supplier<Boolean> active)
    {
        this.active = active;

        return this;
    }

    public Keybind label(IKey label)
    {
        this.label = label;

        return this;
    }

    public Keybind category(IKey category)
    {
        this.category = category;

        return this;
    }

    public int getScore()
    {
        return this.combo.keys.size();
    }

    public IKey getLabel()
    {
        return this.label == null ? this.combo.label : this.label;
    }

    public IKey getCategory()
    {
        return this.category == null ? this.combo.category : this.category;
    }

    public String getKeyCombo()
    {
        return this.combo.getKeyCombo();
    }

    public boolean check(int keyCode, KeyAction keyAction, boolean inside)
    {
        if (keyAction == KeyAction.REPEAT && !this.combo.repeatable)
        {
            return false;
        }

        if (keyCode != this.combo.getMainKey())
        {
            return false;
        }

        for (int i = 1; i < this.combo.keys.size(); i++)
        {
            if (!this.isKeyDown(this.combo.keys.get(i)))
            {
                return false;
            }
        }

        if (this.strict && this.hasExtraModifier())
        {
            return false;
        }

        if (this.beschattetVonLaengeremCombo())
        {
            return false;
        }

        return this.inside ? inside : true;
    }

    /**
     * Whether a longer combo on the same key is fully held right now — then this
     * shorter one steps aside.
     *
     * <p>Camera mode on {@code 3} and the pose tab on {@code Shift + 3} live on
     * different elements, and {@link KeybindManager} only ranks the binds of ONE
     * element. Without this the plain key wins by arriving first and Shift+3 opens
     * the camera mode UI. {@link #strict} solves the same thing but has to be set
     * by hand per bind — useless once the user rebinds freely, which is the point
     * of the keybind editor.</p>
     *
     * <p>Only asked when a modifier is held that this combo doesn't list, so a bind
     * without any competitor is never slowed down or changed.</p>
     */
    private boolean beschattetVonLaengeremCombo()
    {
        return BBSSettings.editorKeybindSpecificWins.get()
            && istBeschattet(this.combo, KeyCombo.getRegistrierte(), KeyCombo::isKeyDown);
    }

    /**
     * The rule itself, with the key state and the list of rivals handed in.
     *
     * <p>Separated so it can be checked against a made-up key state — pressing real
     * Shift+3 into the running client would need the window in the foreground, and
     * the client belongs to whoever is working in it.</p>
     */
    public static boolean istBeschattet(KeyCombo combo, Iterable<KeyCombo> alle, IntPredicate gedrueckt)
    {
        int haupt = combo.getMainKey();

        for (KeyCombo anderes : alle)
        {
            if (anderes == combo
                || anderes.getMainKey() != haupt
                || anderes.keys.size() <= combo.keys.size())
            {
                continue;
            }

            /* isHeld prueft ALLE tasten des laengeren combos, haupttaste eingeschlossen —
             * die ist gerade gedrueckt, sonst waeren wir nicht hier. */
            if (anderes.isHeld(gedrueckt))
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Whether a modifier (shift/ctrl/alt) is currently held that the combo does
     * not list — used by {@link #strict} binds to step aside for longer combos.
     */
    private boolean hasExtraModifier()
    {
        if (Window.isShiftPressed() && !this.comboHas(GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT))
        {
            return true;
        }

        if (Window.isCtrlPressed() && !this.comboHas(GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL))
        {
            return true;
        }

        return Window.isAltPressed() && !this.comboHas(GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT);
    }

    private boolean comboHas(int left, int right)
    {
        return this.combo.keys.contains(left) || this.combo.keys.contains(right);
    }

    public boolean checkMouse(int mouseButton, boolean inside)
    {
        /* ⛔ MAUSTASTEN SIND NEGATIV KODIERT (Mouse2 = -1, Mouse4 = -3 …), und genau daran
         * hing der teuerste UI-fehler dieser woche: die LINKE maustaste ist GLFW-button 0,
         * und -0 ist wieder 0. Eine combo, in der versehentlich die 0 landet, traf damit
         * JEDEN linksklick — beim nutzer oeffnete sich die Undo-History bei jedem klick auf
         * jedes menue, auch auf dem schliess-kreuz, sodass das fenster unschliessbar wirkte
         * (gemessen 2026-07-29 in seiner instanz: preset "Left-Hand + Mouse" enthielt
         * "bbstools.undo_history_open": [0]).
         *
         * Dasselbe eine stelle weiter: getMainKey() liefert -1, wenn eine combo GAR KEINE
         * taste hat — und -1 ist der rechtsklick. Jede nie belegte combo haette also auf
         * jeden rechtsklick gefeuert.
         *
         * Beide faelle sterben hier: nur eine NEGATIVE haupttaste ist eine maus-belegung,
         * und eine leere combo bindet nichts. Die pruefung heilt auch bereits verdorbene
         * konfigurationen, ohne dass jemand eine json anfassen muss. */
        if (this.combo.keys.isEmpty())
        {
            return false;
        }

        int haupt = this.combo.getMainKey();

        if (haupt >= 0 || haupt != -mouseButton)
        {
            return false;
        }

        for (int i = 1; i < this.combo.keys.size(); i++)
        {
            if (!this.isKeyDown(this.combo.keys.get(i)))
            {
                return false;
            }
        }

        /* Gilt genauso fuer maustasten: das preset "Left-Hand + Mouse" legt
         * Mouse4 und Shift+Mouse4 auf verschiedene aktionen. */
        if (this.beschattetVonLaengeremCombo())
        {
            return false;
        }

        return this.inside ? inside : true;
    }

    protected boolean isKeyDown(int key)
    {
        return KeyCombo.isKeyDown(key);
    }

    public boolean isActive()
    {
        return this.active == null || this.active.get();
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj instanceof Keybind)
        {
            Keybind keybind = (Keybind) obj;

            return Objects.equals(this.combo.keys, keybind.combo.keys) && this.inside == keybind.inside;
        }

        return super.equals(obj);
    }
}