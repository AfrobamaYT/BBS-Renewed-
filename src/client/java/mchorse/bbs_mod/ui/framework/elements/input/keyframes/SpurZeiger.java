package mchorse.bbs_mod.ui.framework.elements.input.keyframes;

import mchorse.bbs_mod.ui.framework.UIContext;

/**
 * Etwas, das in den Spurzeilen der Zeitleiste liegt und selbst auf die Maus hört — die
 * Kamera-Clips im Replay-Editor sind der erste Fall.
 *
 * <p>Die Keyframes haben immer Vorrang: {@link #klick} wird erst gefragt, wenn der Graph den
 * Klick nicht schon verbraucht hat.</p>
 */
public interface SpurZeiger
{
    /**
     * Gezeichnet wird IN der Spurzeile: nachdem sie ihren Hintergrund gemalt hat, bevor sie
     * ihre Keyframes zeichnet.
     *
     * <p>Zwei Anläufe waren vorher falsch. Vor allen Spuren gezeichnet übermalt der deckende
     * Zeilenhintergrund alles — von zwei Kamera-Clips blieb nur der sichtbar, der unterhalb
     * der letzten Spur lag. Nach allen Spuren gezeichnet legt sich der Balken ÜBER deren
     * Keyframes, und die verschwinden (Einwand des Users 2026-07-29: „jetzt verdeckt es sie
     * nur"). Die Zeile malt Hintergrund und Keyframes in einem Zug — dazwischen kommt man nur
     * von innen.</p>
     *
     * @param zeile Index der Spurzeile — die Kamera-Clips vergleichen ihn mit ihrem Layer.
     */
    void zeichneZeile(UIContext context, int zeile, int y, int hoehe);

    boolean klick(UIContext context);

    void zug(UIContext context);

    void losgelassen(UIContext context);
}
