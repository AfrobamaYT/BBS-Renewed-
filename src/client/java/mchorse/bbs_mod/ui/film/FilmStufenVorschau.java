package mchorse.bbs_mod.ui.film;

import com.mojang.blaze3d.platform.InputConstants;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.keys.KeyCombo;

/** Alt+K turns Maya's stepped preview on and off (see KeyframeStufen); keys keep their interpolation. */
public final class FilmStufenVorschau
{
    public static final KeyCombo TASTE = new KeyCombo("stepped_preview", IKey.raw("Stepped preview: hold each key until the next (blocking)"),
        InputConstants.KEY_K, InputConstants.KEY_LALT).categoryKey("keyframes");

    private FilmStufenVorschau()
    {}

    public static void umschalten(UIFilmPanel panel)
    {
        boolean an = !BBSSettings.editorSteppedPreview.get();

        BBSSettings.editorSteppedPreview.set(an);
        panel.getContext().notifyInfo(IKey.raw(an ? "Stepped preview on: keys hold until the next key" : "Stepped preview off"));
        UIUtils.playClick();
    }
}
