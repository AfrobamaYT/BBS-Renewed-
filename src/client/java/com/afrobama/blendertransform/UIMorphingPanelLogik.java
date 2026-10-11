package com.afrobama.blendertransform;

import com.afrobama.bbstools.client.mixin.UIMorphingPanelSetFormInvoker;
import mchorse.bbs_mod.ui.morphing.UIMorphingPanel;

/**
 * Logik hinter {@code UIMorphingPanelClickMorphMixin}, herausgeloest damit sie hotswapbar ist.
 *
 * <p>Stellt den Klick-zum-Morphen-Weg wieder her: die Palette meldet die gewaehlte Form direkt
 * an {@code setForm} des Panels.</p>
 *
 * <p>⚠️ LAEUFT IM KONSTRUKTOR des Panels — einmal je Panel, nicht je Bild. Der gesetzte
 * Callback ueberlebt danach; eine Aenderung hier wirkt erst auf das NAECHSTE Panel. Das ist
 * die eine Stelle, an der ein Swap nicht sofort sichtbar wird.</p>
 *
 * <p>{@link UIMorphingPanelSetFormInvoker} ist ein Mixin-Invoker-Interface im stabilen Loader,
 * kein Kernel-Typ — es darf deshalb hier stehen.</p>
 */
public final class UIMorphingPanelLogik
{
    private UIMorphingPanelLogik() {}

    /**
     * @param panel das frisch gebaute Panel; kommt als Argument, weil der Cast den gewobenen
     *              {@code this} braucht
     */
    public static void klickZumMorphen(Object panel)
    {
        if (!(panel instanceof UIMorphingPanel selbst))
        {
            return;
        }

        selbst.palette.callback = (form) ->
            ((UIMorphingPanelSetFormInvoker) selbst).bbstools$invokeSetForm(form);
    }
}
