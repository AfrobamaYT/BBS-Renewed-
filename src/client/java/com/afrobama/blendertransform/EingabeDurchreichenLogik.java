package com.afrobama.blendertransform;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.graphics.window.Window;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Logik hinter {@code MouseMixin} und {@code KeyboardMixin} — Maus- und Tastatureingaben an
 * BBS' eigenes Fenster-Modell durchreichen.
 *
 * <p>Beide sind Eingabe-Weichen an derselben Stelle des Bildes und stehen deshalb zusammen.</p>
 *
 * <p>⚠️ NUR DAS EIGENE FENSTER. Die Fensterpruefung beim Scrollen ist kein Beiwerk: ohne sie
 * wuerde jedes Scroll-Ereignis aus einem beliebigen Fenster in BBS' Zustand landen.</p>
 */
public final class EingabeDurchreichenLogik
{
    private EingabeDurchreichenLogik() {}

    /** {@code MouseHandler.onScroll} */
    public static void scrollen(long fenster, double senkrecht)
    {
        if (fenster == Window.getWindow())
        {
            Window.setVerticalScroll((int) senkrecht);
        }
    }

    /**
     * {@code KeyboardHandler.keyPress} @HEAD — nur merken, was fuer eine Aktion es war.
     *
     * <p>Der Wert wird an anderer Stelle im selben Bild gelesen; er MUSS vor dem Rumpf gesetzt
     * werden, sonst sieht der Leser den Stand des vorigen Tastendrucks.</p>
     */
    public static void tasteBeginn(int aktion)
    {
        BBSRendering.lastAction = aktion;
    }

    /** {@code KeyboardHandler.keyPress} @TAIL */
    public static void tasteEnde(long fenster, KeyEvent ereignis, int aktion, CallbackInfo info)
    {
        BBSModClient.onEndKey(fenster, ereignis.key(), ereignis.keycode(), aktion,
            ereignis.modifiers(), info);
    }
}
