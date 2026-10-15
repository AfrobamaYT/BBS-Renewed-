package mchorse.bbs_mod.ui.texturstudio;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIMessageOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.utils.icons.Icons;

/**
 * vor1215: das Texture Studio braucht Rinku (Chromium im Spiel), das es nur fuer 26.3 gibt.
 * Der Knopf bleibt, meldet aber laut, dass das Studio hier nicht verfuegbar ist.
 */
public final class TexturStudio
{
    private TexturStudio()
    {}

    public static boolean verfuegbar()
    {
        return false;
    }

    public static UIElement mitPinsel(UIElement reihe, UITexturePicker waehler)
    {
        UIIcon studio = new UIIcon(Icons.BRUSH, (b) -> oeffnen(waehler.getContext(), waehler.multiLink == null ? waehler.current : null));

        studio.tooltip(IKey.raw("Open in Texture Studio (Photopea with 3D preview)"));
        reihe.addBefore(waehler.pixelEdit, studio);

        return reihe;
    }

    public static void oeffnen(UIContext context, Link textur)
    {
        UIOverlay.addOverlay(context, new UIMessageOverlayPanel(IKey.raw("Texture Studio"),
            IKey.raw("The Texture Studio needs the Rinku mod (an in-game browser), which is only available for Minecraft 26.3.")));
    }
}
