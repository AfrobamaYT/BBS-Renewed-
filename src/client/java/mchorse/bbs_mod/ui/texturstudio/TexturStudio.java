package mchorse.bbs_mod.ui.texturstudio;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIMessageOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Einstieg ins Texture Studio: eine Textur in einem eingebetteten Bildeditor (Photopea) bearbeiten,
 * daneben das Modell in 3D, das jede Aenderung sofort zeigt -- wie Blockbench.
 *
 * <p>⛔ Diese Klasse beruehrt KEINE Rinku-Klasse. Rinku ist eine Zusatz-Mod; alles, was sie
 * braucht, steckt in {@link UITexturStudio} und wird erst nach {@link #verfuegbar()} geladen.</p>
 */
public final class TexturStudio
{
    private TexturStudio()
    {}

    /** Rinku (Chromium im Spiel) ist installiert. */
    public static boolean verfuegbar()
    {
        return FabricLoader.getInstance().isModLoaded("rinku");
    }

    /**
     * Pinsel "Open in Texture Studio" vor dem Bleistift eines Texturwaehlers -- im Texture Manager
     * wie im Wahlfenster aus Edit Form -> Pick a texture (Nutzer 2026-10-14: dort fehlte er). Haengt
     * am Waehler selbst, damit jede Stelle, die ihn oeffnet, den Pinsel hat.
     */
    public static UIElement mitPinsel(UIElement reihe, UITexturePicker waehler)
    {
        UIIcon studio = new UIIcon(Icons.BRUSH, (b) -> oeffnen(waehler.getContext(), waehler.multiLink == null ? waehler.current : null));

        studio.tooltip(IKey.raw("Open in Texture Studio (Photopea with 3D preview)"));
        reihe.addBefore(waehler.pixelEdit, studio);

        return reihe;
    }

    public static void oeffnen(UIContext context, Link textur)
    {
        if (!verfuegbar())
        {
            UIOverlay.addOverlay(context, new UIMessageOverlayPanel(IKey.raw("Texture Studio"),
                IKey.raw("The Texture Studio needs the Rinku mod (an in-game browser). Install Rinku for this Minecraft version and restart.")));

            return;
        }

        File datei = textur == null ? null : BBSMod.getProvider().getFile(textur);

        if (datei == null || !datei.isFile())
        {
            UIOverlay.addOverlay(context, new UIMessageOverlayPanel(IKey.raw("Texture Studio"),
                IKey.raw("Select a PNG texture first.")));

            return;
        }

        try
        {
            UITexturStudio.oeffnen(context, textur, datei, Files.readAllBytes(datei.toPath()));
        }
        catch (IOException e)
        {
            UIOverlay.addOverlay(context, new UIMessageOverlayPanel(IKey.raw("Texture Studio"),
                IKey.raw("Could not read " + datei.getName() + ": " + e.getMessage())));
        }
    }
}
