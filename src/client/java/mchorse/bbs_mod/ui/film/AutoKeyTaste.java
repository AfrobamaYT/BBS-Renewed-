package mchorse.bbs_mod.ui.film;

import com.mojang.blaze3d.platform.InputConstants;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.keys.KeyCombo;

/** Katalog C2: Shift+I toggles auto-key (beside I, insert key); before it was only the timeline button. */
public final class AutoKeyTaste
{
    public static final KeyCombo TASTE = new KeyCombo("auto_key_toggle", IKey.raw("Toggle auto key"),
        InputConstants.KEY_I, InputConstants.KEY_LSHIFT).categoryKey("film_controller");

    private AutoKeyTaste()
    {}

    public static void umschalten(UIFilmPanel panel)
    {
        FilmAutoKey.toggle();
        panel.getContext().notifyInfo(IKey.raw(FilmAutoKey.isEnabled() ? "Auto key on: edits key at the playhead" : "Auto key off"));
        UIUtils.playClick();
    }
}
