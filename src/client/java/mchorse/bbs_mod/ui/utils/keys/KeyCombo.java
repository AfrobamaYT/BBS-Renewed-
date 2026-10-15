package mchorse.bbs_mod.ui.utils.keys;

import com.mojang.blaze3d.platform.InputConstants;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntPredicate;

public class KeyCombo
{
    /**
     * The modifiers of the key event being dispatched right now, or null outside of one.
     *
     * <p>Ctrl/Shift/Alt of a combo were read as the CURRENT key state. The editor draws at
     * ~13 fps while idle and handles queued SDL events once per frame, so a quick human
     * Ctrl+Z (Ctrl released before that frame) arrived as a bare Z: 1 of 2 presses with a
     * 120 ms hold were lost (measured 2026-10-13, audit item 3). The event carries its own
     * modifiers; while it is dispatched, they are the truth.</p>
     */
    private static net.minecraft.client.input.KeyEvent ereignis;

    public static void ereignisBeginnen(int key, int scanCode, int mods)
    {
        ereignis = new net.minecraft.client.input.KeyEvent(key, scanCode, mods);
    }

    public static void ereignisBeenden()
    {
        ereignis = null;
    }

    private static Set<String> categoryKeys = new HashSet<>();

    /**
     * Every combo the user can see and rebind, in creation order.
     *
     * <p>Needed because a keybind has to know whether a LONGER combo on the same key
     * exists elsewhere — {@link KeybindManager} only ranks the binds of one element,
     * so a plain key on element A shadows the {@code Shift + key} on element B no
     * matter which is more specific. Rebinding writes through {@link #copy(KeyCombo)},
     * which mutates in place, so the entries stay current.</p>
     */
    private static final List<KeyCombo> registriert = new ArrayList<>();

    public String id = "";
    public IKey label;
    public IKey category = IKey.EMPTY;
    public String categoryKey;
    public boolean repeatable;
    public List<Integer> keys = new ArrayList<>();

    public static Set<String> getCategoryKeys()
    {
        return categoryKeys;
    }

    public static List<KeyCombo> getRegistrierte()
    {
        return registriert;
    }

    public KeyCombo(String id, IKey label, int... keys)
    {
        this(label, keys);

        this.id = id;

        this.categoryKey("all");

        /* Nur benannte combos: die namenlosen sind wegwerf-bindungen einzelner
         * elemente, die im keybind-editor nie auftauchen. */
        registriert.add(this);
    }

    public KeyCombo(IKey label, int... keys)
    {
        this.label = label;

        this.set(keys);
    }

    private void set(int... keys)
    {
        this.keys.clear();

        for (int key : keys)
        {
            this.keys.add(key);
        }
    }

    public KeyCombo repeatable()
    {
        this.repeatable = true;

        return this;
    }

    public KeyCombo category(IKey category)
    {
        this.category = category;

        return this;
    }

    public KeyCombo categoryKey(String categoryKey)
    {
        this.categoryKey = categoryKey;

        categoryKeys.add(categoryKey);

        return this;
    }

    public int getMainKey()
    {
        return this.keys.isEmpty() ? -1 : this.keys.get(0);
    }

    /**
     * Whether every key in this combo is currently held down — keyboard keys, modifiers and mouse
     * buttons alike (mouse buttons are stored as negative ids). Use this for hold-to-act behaviour,
     * since the keybind system only dispatches discrete presses, not holds.
     */
    public boolean isHeld()
    {
        return this.isHeld(KeyCombo::isKeyDown);
    }

    /**
     * Same, but asks the given predicate instead of the real keyboard — so the
     * shadowing rule in {@link Keybind} can be checked against a made-up key state
     * without pressing anything into the running client.
     */
    public boolean isHeld(IntPredicate gedrueckt)
    {
        if (this.keys.isEmpty())
        {
            return false;
        }

        for (int key : this.keys)
        {
            if (!gedrueckt.test(key))
            {
                return false;
            }
        }

        return true;
    }

    /**
     * Whether a single combo key is down right now. Negative ids are mouse buttons; the shift/ctrl/alt
     * modifiers map to the left/right-agnostic checks so either side counts. Shared by {@link #isHeld()}
     * and {@link Keybind} so the held-key logic lives in one place.
     */
    public static boolean isKeyDown(int key)
    {
        if (key < 0)
        {
            return Window.isMouseButtonPressed(-key);
        }

        net.minecraft.client.input.KeyEvent event = ereignis;

        if (event != null)
        {
            if (key == InputConstants.KEY_LSHIFT || key == InputConstants.KEY_RSHIFT) return event.hasShiftDown();
            if (key == InputConstants.KEY_LCONTROL || key == InputConstants.KEY_RCONTROL) return event.hasControlDown();
            if (key == InputConstants.KEY_LALT || key == InputConstants.KEY_RALT) return event.hasAltDown();
        }

        if (key == InputConstants.KEY_LSHIFT || key == InputConstants.KEY_RSHIFT)
        {
            return Window.isShiftPressed();
        }
        else if (key == InputConstants.KEY_LCONTROL || key == InputConstants.KEY_RCONTROL)
        {
            return Window.isCtrlPressed();
        }
        else if (key == InputConstants.KEY_LALT || key == InputConstants.KEY_RALT)
        {
            return Window.isAltPressed();
        }

        return Window.isKeyPressed(key);
    }

    public String getKeyCombo()
    {
        StringBuilder label = new StringBuilder(KeyCodes.getName(this.getMainKey()));

        for (int i = 1; i < this.keys.size(); i++)
        {
            label.insert(0, KeyCodes.getName(this.keys.get(i)) + " + ");
        }

        return label.toString();
    }

    public void copy(KeyCombo combo)
    {
        this.keys.clear();
        this.keys.addAll(combo.keys);
    }
}
