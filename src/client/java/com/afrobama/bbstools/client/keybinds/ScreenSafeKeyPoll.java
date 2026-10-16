// Coded by Afrobama/Claude — flankenerkennung fuer KeyBindings, die auch bei
// offenem UI-screen feuert.
package com.afrobama.bbstools.client.keybinds;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * Liest den PHYSISCHEN zustand der taste, auf die ein {@link KeyMapping}
 * gerade gebunden ist, und meldet die off&rarr;on-flanke.
 *
 * <p>WARUM das noetig ist: Minecraft pumpt keybindings nur im screen-losen
 * zweig von {@code Keyboard.onKey}. Steht irgendein {@code Screen} offen
 * (BBS-dashboard, inventar, chat, unsere log-konsole), wird
 * {@code timesPressed} nie hochgezaehlt und {@code wasPressed()} bleibt
 * dauerhaft false. Fuer schalter, die man GERADE DANN braucht, wenn UI im
 * weg ist — z.B. das debug-overlay ausblenden — ist der vanilla-kanal
 * damit der falsche. Deshalb fragen wir GLFW direkt.</p>
 *
 * <p>Die bindung selbst bleibt ein echtes {@link KeyMapping}: sie steht
 * weiter im Controls-screen und ist dort umlegbar. Wir lesen nur bei jeder
 * abfrage neu, auf welche taste sie zeigt.</p>
 *
 * <p><b>FALLE 1 — modifier:</b> der physische zustand kennt keine
 * modifier-semantik. Ohne {@code requireNoModifiers} wuerde Shift+F12
 * (log-konsole) auch den blanken F12-schalter ausloesen. Bei gehaltenem
 * modifier verwerfen wir die flanke, schreiben {@code wasDown} aber
 * trotzdem fort — sonst feuert der schalter nachtraeglich, sobald der
 * nutzer den modifier loslaesst und die taste noch haelt.</p>
 *
 * <p><b>FALLE 2 — vanilla-warteschlange:</b> solange kein screen offen ist,
 * fuellt Minecraft {@code timesPressed} weiter. Wir leeren die schlange bei
 * jeder abfrage, sonst holt ein spaeterer {@code wasPressed()}-leser alte
 * druecke nach.</p>
 *
 * <p><b>Grenze:</b> tasten vom typ {@code SCANCODE} werden wie KEYSYM
 * behandelt (GLFW kann scancodes nicht direkt abfragen). Vanilla vergibt
 * diesen typ nur fuer tasten ohne keysym; F-tasten sind nie betroffen.</p>
 */
public final class ScreenSafeKeyPoll
{
    private final KeyMapping binding;
    private final boolean requireNoModifiers;

    private boolean wasDown;

    public ScreenSafeKeyPoll(KeyMapping binding, boolean requireNoModifiers)
    {
        this.binding = binding;
        this.requireNoModifiers = requireNoModifiers;
    }

    /**
     * @return true genau einmal pro tastendruck (off&rarr;on-flanke),
     *         unabhaengig davon ob ein screen offen ist.
     */
    public boolean pressed(Minecraft mc)
    {
        /* Vanilla-schlange leeren (FALLE 2) — der rueckgabewert interessiert
         * uns nicht, wir entscheiden allein ueber den physischen zustand. */
        if (this.binding != null)
        {
            while (this.binding.consumeClick()) { /* drain */ }
        }

        if (mc == null || mc.getWindow() == null) return false;

        long handle = mc.getWindow().handle();
        boolean down = this.pollDown(handle);
        boolean edge = down && !this.wasDown;

        this.wasDown = down;

        if (!edge) return false;
        if (this.requireNoModifiers && anyModifierDown(handle)) return false;

        return true;
    }

    private boolean pollDown(long handle)
    {
        InputConstants.Key key;

        try
        {
            /* ⚠️ Fabric hat die Keybinding-API auf die neuen Vanilla-Namen gezogen:
             * KeyBindingHelper heisst jetzt KeyMappingHelper (Paket ...client.keymapping.v1),
             * getBoundKeyOf bleibt gleich. */
            key = KeyMappingHelper.getBoundKeyOf(this.binding);
        }
        catch (Throwable t)
        {
            return false;
        }

        if (key == null || key == InputConstants.UNKNOWN) return false;

        int code = key.getValue();
        if (code < 0) return false;

        /* Maus-codes (0..7) ueberschneiden sich mit GLFW-tastencodes, deshalb
         * entscheidet die kategorie ueber den abfrage-weg. */
        if (key.getType() == InputConstants.Type.MOUSE)
        {
            return mchorse.bbs_mod.graphics.window.Window.isNativeMouseButtonPressed(code);
        }

        return InputConstants.isKeyDown(code);
    }

    private static boolean anyModifierDown(long handle)
    {
        return InputConstants.isKeyDown(InputConstants.KEY_LSHIFT)
            || InputConstants.isKeyDown(InputConstants.KEY_RSHIFT)
            || InputConstants.isKeyDown(InputConstants.KEY_LCONTROL)
            || InputConstants.isKeyDown(InputConstants.KEY_RCONTROL)
            || InputConstants.isKeyDown(InputConstants.KEY_LALT)
            || InputConstants.isKeyDown(InputConstants.KEY_RALT);
    }
}
