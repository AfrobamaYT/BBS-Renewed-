// Coded by Afrobama/Claude — flankenerkennung fuer KeyBindings, die auch bei
// offenem UI-screen feuert.
package com.afrobama.bbstools.client.keybinds;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

import org.lwjgl.glfw.GLFW;

/**
 * Liest den PHYSISCHEN zustand der taste, auf die ein {@link KeyBinding}
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
 * <p>Die bindung selbst bleibt ein echtes {@link KeyBinding}: sie steht
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
    private final KeyBinding binding;
    private final boolean requireNoModifiers;

    private boolean wasDown;

    public ScreenSafeKeyPoll(KeyBinding binding, boolean requireNoModifiers)
    {
        this.binding = binding;
        this.requireNoModifiers = requireNoModifiers;
    }

    /**
     * @return true genau einmal pro tastendruck (off&rarr;on-flanke),
     *         unabhaengig davon ob ein screen offen ist.
     */
    public boolean pressed(MinecraftClient mc)
    {
        /* Vanilla-schlange leeren (FALLE 2) — der rueckgabewert interessiert
         * uns nicht, wir entscheiden allein ueber den physischen zustand. */
        if (this.binding != null)
        {
            while (this.binding.wasPressed()) { /* drain */ }
        }

        if (mc == null || mc.getWindow() == null) return false;

        long handle = mc.getWindow().getHandle();
        boolean down = this.pollDown(handle);
        boolean edge = down && !this.wasDown;

        this.wasDown = down;

        if (!edge) return false;
        if (this.requireNoModifiers && anyModifierDown(handle)) return false;

        return true;
    }

    private boolean pollDown(long handle)
    {
        InputUtil.Key key;

        try
        {
            key = KeyBindingHelper.getBoundKeyOf(this.binding);
        }
        catch (Throwable t)
        {
            return false;
        }

        if (key == null || key == InputUtil.UNKNOWN_KEY) return false;

        int code = key.getCode();
        if (code < 0) return false;

        /* Maus-codes (0..7) ueberschneiden sich mit GLFW-tastencodes, deshalb
         * entscheidet die kategorie ueber den abfrage-weg. */
        if (key.getCategory() == InputUtil.Type.MOUSE)
        {
            return GLFW.glfwGetMouseButton(handle, code) == GLFW.GLFW_PRESS;
        }

        return InputUtil.isKeyPressed(handle, code);
    }

    private static boolean anyModifierDown(long handle)
    {
        return InputUtil.isKeyPressed(handle, GLFW.GLFW_KEY_LEFT_SHIFT)
            || InputUtil.isKeyPressed(handle, GLFW.GLFW_KEY_RIGHT_SHIFT)
            || InputUtil.isKeyPressed(handle, GLFW.GLFW_KEY_LEFT_CONTROL)
            || InputUtil.isKeyPressed(handle, GLFW.GLFW_KEY_RIGHT_CONTROL)
            || InputUtil.isKeyPressed(handle, GLFW.GLFW_KEY_LEFT_ALT)
            || InputUtil.isKeyPressed(handle, GLFW.GLFW_KEY_RIGHT_ALT);
    }
}
