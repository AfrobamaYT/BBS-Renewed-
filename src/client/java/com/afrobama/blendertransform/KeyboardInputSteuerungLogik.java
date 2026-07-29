package com.afrobama.blendertransform;

import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIScreen;

import net.minecraft.client.input.KeyboardInput;

import org.lwjgl.glfw.GLFW;

/**
 * Logik der Tastatur-Steuerung beim Steuern eines Darstellers — im Kernel-Paket, damit sie
 * hotswapbar ist.
 *
 * <p>Herausgelöst aus {@code KeyboardInputMixin} (2026-07-30, Aufgabe #56).</p>
 *
 * <p><b>Wofür:</b> steuert man im Film-Editor einen Darsteller, soll WASD ihn bewegen und
 * nicht den Spieler. Minecraft hat seine Eingabe zu diesem Zeitpunkt schon ausgewertet;
 * deshalb wird sie am Ende von {@code KeyboardInput.tick} überschrieben — direkt aus dem
 * Fenster-Zustand, damit auch gilt, was BBS an Tasten abgefangen hat.</p>
 *
 * <p><b>Warum diese Stelle die Brücke verträgt:</b> {@code tick} läuft 20×/s, nicht pro Frame
 * und nicht pro Objekt. Der Brückenaufruf fällt nicht ins Gewicht — und wie sich das Steuern
 * anfühlt, will man beim Ausprobieren sofort sehen, nicht nach einem Boot.</p>
 *
 * <p>⛔ NUR WÄHREND DES STEUERNS. Ohne die drei Bedingungen (Dashboard offen, Film-Panel
 * aktiv, Controller im Steuer-Modus) bleibt Minecrafts eigene Eingabe unangetastet. Fällt eine
 * weg, würde man dem Spieler die Bewegung wegnehmen, während er gar nichts steuert.</p>
 */
public final class KeyboardInputSteuerungLogik
{
    private KeyboardInputSteuerungLogik() {}

    /**
     * @return {@code true}, wenn die Eingabe übernommen wurde — nur zur Beobachtung; der
     *         Handler wertet es nicht aus.
     */
    public static boolean uebernehmen(KeyboardInput input, boolean slowDown, float slowDownFactor)
    {
        UIBaseMenu menu = UIScreen.getCurrentMenu();

        if (!(menu instanceof UIDashboard dashboard)
            || !(dashboard.getPanels().panel instanceof UIFilmPanel filmPanel)
            || !filmPanel.getController().isControlling())
        {
            return false;
        }

        input.pressingForward = Window.isKeyPressed(GLFW.GLFW_KEY_W);
        input.pressingBack = Window.isKeyPressed(GLFW.GLFW_KEY_S);
        input.pressingLeft = Window.isKeyPressed(GLFW.GLFW_KEY_A);
        input.pressingRight = Window.isKeyPressed(GLFW.GLFW_KEY_D);
        input.movementForward = richtung(input.pressingForward, input.pressingBack);
        input.movementSideways = richtung(input.pressingLeft, input.pressingRight);
        input.jumping = Window.isKeyPressed(GLFW.GLFW_KEY_SPACE);
        input.sneaking = Window.isKeyPressed(GLFW.GLFW_KEY_LEFT_SHIFT);

        if (slowDown)
        {
            input.movementSideways *= slowDownFactor;
            input.movementForward *= slowDownFactor;
        }

        return true;
    }

    /** Beide oder keine gedrückt heißt still — sonst zieht eine Richtung dauerhaft. */
    private static float richtung(boolean vor, boolean zurueck)
    {
        return vor == zurueck ? 0F : (vor ? 1F : -1F);
    }
}
