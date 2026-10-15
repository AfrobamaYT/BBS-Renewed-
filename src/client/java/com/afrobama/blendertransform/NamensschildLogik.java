package com.afrobama.blendertransform;

import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.MobForm;

/**
 * Logik hinter {@code EntityRendererMixin}, herausgeloest damit sie hotswapbar ist.
 *
 * <p>Unterdrueckt Vanillas Namensschild, wenn gerade eine BBS-MobForm in Spielergestalt
 * gezeichnet wird. Vanilla wuerde sonst den Namen der ECHTEN Entity darueberschreiben — im Film
 * steht dort ein Schild, das niemand bestellt hat.</p>
 */
public final class NamensschildLogik
{
    private NamensschildLogik() {}

    /**
     * @return {@code true}, wenn das Namensschild uebersprungen werden soll
     */
    public static boolean unterdruecken()
    {
        return FormUtilsClient.getCurrentForm() instanceof MobForm form && form.isPlayer();
    }
}
