package mchorse.bbs_mod.ui.texturstudio;

import de.keksuccino.rinku.Rinku;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.EditorZustaende;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.utils.UIFormRenderer;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIListOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIMessageOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.resources.Pixels;
import mchorse.bbs_mod.utils.resources.PixelsImageBridge;
import org.lwjgl.opengl.GL11;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;

/**
 * Das Texture Studio: links der Bildeditor im eingebetteten Browser, rechts das Modell in 3D
 * (mit der Maus drehbar), das jede Aenderung sofort zeigt. Speichern schreibt die PNG zurueck;
 * BBS' Watchdog laedt die echte Textur dann ueberall nach.
 *
 * <p>Das Bild kommt als PNG aus dem Editor ({@link TexturStudioKanal}) und landet in einer
 * eigenen Textur, die die Vorschau unter einem Wegwerf-Link sieht -- dasselbe Muster wie
 * {@code UIModelPreviewPanel} im Mal-Editor.</p>
 */
public class UITexturStudio extends UIOverlayPanel
{
    private final File datei;
    private final Link vorschauLink = Link.create("bbs:texturstudio_" + UUID.randomUUID());
    private final Texture vorschauTextur = new Texture();
    private final ModelForm form = new ModelForm();
    private final TexturStudioKanal kanal;
    private final UIBrowserFlaeche flaeche;
    private final UIFormRenderer vorschau;
    private byte[] png;

    static void oeffnen(UIContext context, Link textur, File datei, byte[] png)
    {
        if (!Rinku.isInitialized() && !Rinku.initialize())
        {
            UIOverlay.addOverlay(context, new UIMessageOverlayPanel(IKey.raw("Texture Studio"),
                IKey.raw("Rinku is still setting up its browser (first start downloads it). Try again in a moment.")));

            return;
        }

        UIOverlay.addOverlay(context, new UITexturStudio(textur, datei, png), 0.96F, 0.92F);
    }

    private UITexturStudio(Link textur, File datei, byte[] png)
    {
        super(IKey.raw("Texture Studio: " + textur.path));

        this.datei = datei;
        this.png = png;
        this.vorschauTextur.setFilter(GL11.GL_NEAREST);
        this.zeige(png);
        BBSModClient.getTextures().textures.put(this.vorschauLink, this.vorschauTextur);

        this.kanal = new TexturStudioKanal(1, 1, this::zeige);
        this.flaeche = new UIBrowserFlaeche(this.kanal.browser());
        this.flaeche.relative(this.content).w(0.68F).h(1F);

        this.form.texture.set(this.vorschauLink);
        this.form.model.set(EditorZustaende.texturStudioModell.get());
        this.vorschau = new UIFormRenderer();
        this.vorschau.form = this.form;
        this.vorschau.relative(this.content).x(0.68F, 4).w(0.32F, -4).h(1F);

        UIIcon modell = new UIIcon(Icons.POSE, (b) -> this.modellWaehlen());
        UIIcon speichern = new UIIcon(Icons.SAVED, (b) -> this.speichern());

        modell.tooltip(IKey.raw("Preview model"));
        speichern.tooltip(IKey.raw("Save texture"));
        this.icons.add(modell, speichern);
        this.content.add(this.flaeche, this.vorschau);

        this.onClose((e) -> this.aufraeumen());
        this.kanal.lade(png);
    }

    /** Neues Bild aus dem Editor (oder das Startbild) in die Vorschau-Textur. */
    private void zeige(byte[] png)
    {
        try
        {
            Pixels pixels = PixelsImageBridge.fromPNGBytes(png);

            this.vorschauTextur.updateTexture(pixels);
            pixels.delete();
            this.png = png;
        }
        catch (IOException e)
        {
            /* Ein kaputtes Zwischenbild verwirft nur diesen Stand, der naechste kommt gleich. */
        }
    }

    private void modellWaehlen()
    {
        UIListOverlayPanel liste = new UIListOverlayPanel(UIKeys.FORMS_EDITOR_MODEL_MODELS, (modell) ->
        {
            EditorZustaende.texturStudioModell.set(modell); /* ueberlebt den Neustart */
            this.form.model.set(modell);
        });

        liste.addValues(BBSModClient.getModels().getAvailableKeys());
        liste.list.list.sort();
        UIOverlay.addOverlay(this.getContext(), liste);
    }

    private void speichern()
    {
        try
        {
            Files.write(this.datei.toPath(), this.png);
        }
        catch (IOException e)
        {
            UIOverlay.addOverlay(this.getContext(), new UIMessageOverlayPanel(IKey.raw("Texture Studio"),
                IKey.raw("Could not save " + this.datei.getName() + ": " + e.getMessage())));
        }
    }

    private void aufraeumen()
    {
        this.kanal.close();
        BBSModClient.getTextures().textures.remove(this.vorschauLink);
        this.vorschauTextur.delete();
    }
}
