package com.afrobama.blendertransform;

import com.mojang.blaze3d.platform.InputConstants;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIScreen;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;

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
     * Übernimmt die Eingabe, solange ein Darsteller gesteuert wird.
     *
     * <p>26.2: die acht einzelnen Felder von {@code KeyboardInput} gibt es nicht mehr. Der
     * Tastenzustand steckt jetzt im öffentlichen Feld {@code keyPresses}, einem
     * <b>unveränderlichen</b> {@code Input}-Record — man setzt also einen neuen, statt Felder
     * einzeln zu beschreiben. Die beiden Impulse sind zu einem {@code moveVector}
     * zusammengefasst, und der ist <b>geschützt</b>: schreiben kann ihn nur, wer in der Klasse
     * steht. Deshalb gibt diese Methode ihn zurück, statt ihn selbst zu setzen — das Mixin
     * hält dafür einen {@code @Shadow}.</p>
     *
     * <p>⚠️ Die Verlangsamung beim Schleichen ist hier ersatzlos weg. {@code tick} nahm früher
     * {@code slowDown}/{@code slowDownFactor} entgegen; in 26.2 ist der Rückruf parameterlos,
     * Minecraft rechnet die Dämpfung inzwischen selbst am Spieler. Wir bekommen sie also gar
     * nicht mehr gereicht und dürfen sie hier auch nicht nachbauen.</p>
     *
     * @return der neue Bewegungsvektor, oder {@code null} wenn gerade nichts gesteuert wird —
     *         dann bleibt Minecrafts eigene Eingabe unangetastet.
     */
    public static Vec2 uebernehmen(KeyboardInput input)
    {
        UIBaseMenu menu = UIScreen.getCurrentMenu();

        if (!(menu instanceof UIDashboard dashboard)
            || !(dashboard.getPanels().panel instanceof UIFilmPanel filmPanel)
            || !filmPanel.getController().isControlling())
        {
            return null;
        }

        boolean vor = Window.isKeyPressed(InputConstants.KEY_W);
        boolean zurueck = Window.isKeyPressed(InputConstants.KEY_S);
        boolean links = Window.isKeyPressed(InputConstants.KEY_A);
        boolean rechts = Window.isKeyPressed(InputConstants.KEY_D);

        /* Sprinten fassen wir nicht an — das tat die alte Fassung auch nicht. Der Wert kommt
         * aus Minecrafts eigenem Durchlauf und wird hier nur durchgereicht. */
        input.keyPresses = new Input(vor, zurueck, links, rechts,
            Window.isKeyPressed(InputConstants.KEY_SPACE),
            Window.isKeyPressed(InputConstants.KEY_LSHIFT),
            input.keyPresses.sprint());

        /* Reihenfolge und Normalisierung sind aus KeyboardInput.tick abgelesen (Bytecode):
         * new Vec2(seitwaerts, vorwaerts).normalized(). Das normalized() ist neu -- diagonal
         * ist jetzt genauso schnell wie geradeaus, vorher war es Wurzel zwei mal schneller.
         * Das ist Minecrafts Verhalten, und der Darsteller soll sich steuern wie der Spieler. */
        return new Vec2(richtung(links, rechts), richtung(vor, zurueck)).normalized();
    }

    /** Beide oder keine gedrückt heißt still — sonst zieht eine Richtung dauerhaft. */
    private static float richtung(boolean vor, boolean zurueck)
    {
        return vor == zurueck ? 0F : (vor ? 1F : -1F);
    }
}
