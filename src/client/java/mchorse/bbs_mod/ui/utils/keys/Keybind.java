package mchorse.bbs_mod.ui.utils.keys;

import com.mojang.blaze3d.platform.InputConstants;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;

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

    /** Die combo, die beim letzten {@link #check}/{@link #checkMouse} GETROFFEN hat — Erst- oder
     *  Zweitbelegung. Nur fuer {@link #getScore()} da.
     *  ⛔ FALLE: nur unmittelbar nach einem true gueltig. Genau so wird es benutzt
     *  ({@code KeybindManager.checkKeybinds} fragt den Rang direkt nach dem Treffer), und beide
     *  Pruefwege setzen das Feld zu Beginn auf null, damit ein alter Wert nie in eine spaetere
     *  Rangfolge leckt. */
    private KeyCombo getroffen;

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
        /* Der Rang ist die Tastenzahl der combo, die WIRKLICH getroffen hat. Sonst traete eine
         * Zweitbelegung Shift+E mit dem Rang der Erstbelegung E an und verloere im
         * KeybindManager gegen eine echte 1-Tasten-Bindung. */
        KeyCombo getroffen = this.getroffen == null ? this.combo : this.getroffen;

        return getroffen.keys.size();
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
        KeyCombo zweit = Zweitbelegungen.zweit(this.combo);

        /* Die Kurzhilfe (UIKeybinds) muss beide Wege zeigen, sonst behauptet sie etwas
         * Unvollstaendiges: der Nutzer legt eine Zweitbelegung an, sieht sie nirgends und haelt
         * sie fuer wirkungslos. */
        return zweit == null || zweit.keys.isEmpty()
            ? this.combo.getKeyCombo()
            : this.combo.getKeyCombo() + " / " + zweit.getKeyCombo();
    }

    /**
     * Passt die Taste — und wenn ja, ausführen. Ohne Rücksicht auf {@link #isActive()}.
     *
     * <p>Für Fälle, in denen ein ANDERER Teil der Oberfläche entscheidet, ob das Kürzel gerade
     * gelten soll. Der Replay-Editor macht das mit den Clip-Kürzeln: dort hängt „darf gelten"
     * an einem gewählten Kamera-Clip, während {@code isActive()} an der Sichtbarkeit der
     * Kamera-Zeitleiste hängt und deshalb immer false ist (gemessen 2026-07-29, docs/REIBUNGEN
     * R12). Wer das hier benutzt, muss seine eigene Bedingung vorher geprüft haben.</p>
     */
    public boolean feuern(int keyCode, KeyAction keyAction, boolean inside)
    {
        /* ⛔ Loslassen löst KEIN Kürzel aus. {@link #check} prüft das nicht — im normalen Weg
         * fragt der KeybindManager ohnehin nur beim Drücken. Wer check() selbst aufruft, muss
         * es hier tun, sonst feuert jedes Kürzel zweimal pro Anschlag. Gemessen 2026-07-29:
         * „Toggle enable" schaltete beim Drücken UND beim Loslassen, der Clip stand danach
         * wieder wie vorher — das sah aus, als täte die Taste gar nichts. */
        if (keyAction == KeyAction.RELEASED)
        {
            return false;
        }

        if (this.callback == null || !this.check(keyCode, keyAction, inside))
        {
            return false;
        }

        this.callback.run();

        return true;
    }

    /**
     * Passt die Taste auf die Erst- ODER die Zweitbelegung dieser Aktion?
     *
     * <p><b>Warum die Zweitbelegung hier haengt und nicht als zweiter Keybind daneben steht.</b>
     * Ein zweiter Keybind bekaeme die nachtraeglich angehaengten Flags nicht mit — der Aufrufer
     * schreibt {@code keys().register(...).active(...).inside().strict()} NACH dem Registrieren.
     * Die Kopie wuerde also in Zusammenhaengen feuern, in denen die Erstbelegung schweigt. So
     * durchlaufen beide Belegungen garantiert dieselben Tore.</p>
     */
    public boolean check(int keyCode, KeyAction keyAction, boolean inside)
    {
        this.getroffen = null;

        if (com.mcstudio.nano.NanoTextFokus.tippt())
        {
            return false;
        }

        if (this.passt(this.combo, keyCode, keyAction, inside))
        {
            this.getroffen = this.combo;

            return true;
        }

        KeyCombo zweit = Zweitbelegungen.zweit(this.combo);

        if (zweit != null && this.passt(zweit, keyCode, keyAction, inside))
        {
            this.getroffen = zweit;

            return true;
        }

        return false;
    }

    private boolean passt(KeyCombo combo, int keyCode, KeyAction keyAction, boolean inside)
    {
        if (combo.keys.isEmpty())
        {
            /* Eine combo ohne Tasten bindet nichts. Ohne diese Sperre traefe ihr getMainKey()
             * von -1 den GLFW-Code GLFW_KEY_UNKNOWN — dieselbe Falle, die checkMouse unten
             * schon fuer den Rechtsklick schliesst. Mit den Zweitbelegungen gibt es leere
             * combos jetzt auch im Normalbetrieb (frisch angelegt, noch nicht aufgenommen). */
            return false;
        }

        if (keyAction == KeyAction.REPEAT && !combo.repeatable)
        {
            return false;
        }

        if (keyCode != combo.getMainKey())
        {
            return false;
        }

        for (int i = 1; i < combo.keys.size(); i++)
        {
            if (!this.isKeyDown(combo.keys.get(i)))
            {
                return false;
            }
        }

        if (this.strict && this.hasExtraModifier(combo))
        {
            return false;
        }

        if (this.beschattet(combo))
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
    private boolean beschattet(KeyCombo combo)
    {
        if (!BBSSettings.editorKeybindSpecificWins.get())
        {
            return false;
        }

        if (istBeschattet(combo, KeyCombo.getRegistrierte(), KeyCombo::isKeyDown))
        {
            return true;
        }

        /* ⛔ Zweitbelegungen stehen NICHT in KeyCombo.getRegistrierte() — sie entstehen ueber den
         * namenlosen Konstruktor, damit sie nicht als Geisterzeile im Keybind-Editor auftauchen.
         * Beschatten muessen sie trotzdem: liegt eine Zweitbelegung auf Shift+E und eine fremde
         * Erstbelegung auf blankem E, feuerte sonst BEIDES bei Shift+E.
         * Zweitbelegungen.alle() ist bei ausgeschaltetem Feature leer — Ausloesen und Beschatten
         * haengen damit am SELBEN Tor und koennen nicht auseinanderlaufen. */
        return istBeschattet(combo, Zweitbelegungen.alle(), KeyCombo::isKeyDown);
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

            /* isHeld prueft ALLE tasten des laengeren combos. Die haupttaste ist gerade gedrueckt
             * (ihr ereignis laeuft) und zaehlt darum als gehalten: nach dem aktuellen zustand war
             * sie bei kurzem antippen schon wieder oben (druecken und loslassen im selben bild),
             * und blankes M schlug Umschalt+Strg+M (gemessen 3 von 6). */
            if (anderes.isHeld(k -> k == haupt || gedrueckt.test(k)))
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
    private boolean hasExtraModifier(KeyCombo combo)
    {
        if (KeyCombo.isKeyDown(InputConstants.KEY_LSHIFT) && !comboHas(combo, InputConstants.KEY_LSHIFT, InputConstants.KEY_RSHIFT))
        {
            return true;
        }

        if (KeyCombo.isKeyDown(InputConstants.KEY_LCONTROL) && !comboHas(combo, InputConstants.KEY_LCONTROL, InputConstants.KEY_RCONTROL))
        {
            return true;
        }

        return KeyCombo.isKeyDown(InputConstants.KEY_LALT) && !comboHas(combo, InputConstants.KEY_LALT, InputConstants.KEY_RALT);
    }

    private static boolean comboHas(KeyCombo combo, int left, int right)
    {
        return combo.keys.contains(left) || combo.keys.contains(right);
    }

    public boolean checkMouse(int mouseButton, boolean inside)
    {
        this.getroffen = null;

        if (this.passtMaus(this.combo, mouseButton, inside))
        {
            this.getroffen = this.combo;

            return true;
        }

        KeyCombo zweit = Zweitbelegungen.zweit(this.combo);

        if (zweit != null && this.passtMaus(zweit, mouseButton, inside))
        {
            this.getroffen = zweit;

            return true;
        }

        return false;
    }

    private boolean passtMaus(KeyCombo combo, int mouseButton, boolean inside)
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
         * jeden rechtsklick gefeuert. Seit den Zweitbelegungen ist das kein Randfall mehr:
         * ein frisch angelegtes zweites Feld ist genau so eine leere combo.
         *
         * Beide faelle sterben hier: nur eine NEGATIVE haupttaste ist eine maus-belegung,
         * und eine leere combo bindet nichts. Die pruefung heilt auch bereits verdorbene
         * konfigurationen, ohne dass jemand eine json anfassen muss. */
        if (combo.keys.isEmpty())
        {
            return false;
        }

        int haupt = combo.getMainKey();

        if (haupt >= 0 || haupt != -mouseButton)
        {
            return false;
        }

        for (int i = 1; i < combo.keys.size(); i++)
        {
            if (!this.isKeyDown(combo.keys.get(i)))
            {
                return false;
            }
        }

        /* Gilt genauso fuer maustasten: das preset "Left-Hand + Mouse" legt
         * Mouse4 und Shift+Mouse4 auf verschiedene aktionen. */
        if (this.beschattet(combo))
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
