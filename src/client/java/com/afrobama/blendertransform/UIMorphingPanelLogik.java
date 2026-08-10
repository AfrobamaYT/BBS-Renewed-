package com.afrobama.blendertransform;

import com.afrobama.bbstools.client.mixin.UIMorphingPanelSetFormInvoker;
import mchorse.bbs_mod.ui.morphing.UIMorphingPanel;


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
