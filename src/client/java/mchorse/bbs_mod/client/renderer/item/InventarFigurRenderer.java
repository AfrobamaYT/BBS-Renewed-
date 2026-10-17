package mchorse.bbs_mod.client.renderer.item;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import mchorse.bbs_mod.graphics.Zeichenbereich;
import mchorse.bbs_mod.graphics.Zeichenziel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.GuiEntityRenderer;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.gui.pip.GuiEntityRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/** Keeps immediate BBS held-form draws inside the vanilla inventory entity texture. */
public class InventarFigurRenderer extends GuiEntityRenderer
{
    private static Object registriertesGui;
    private static Field farbFeld;
    private static Field tiefenFeld;

    public InventarFigurRenderer()
    {
        super(Minecraft.getInstance().getEntityRenderDispatcher());
    }

    /**
     * Vanilla's renderer table is immutable and Fabric's registry rejects duplicate state
     * classes. Replace only the vanilla entry before GUI preparation; this also works in
     * an already running client. The GUI owns and closes the replacement's textures.
     */
    @SuppressWarnings("unchecked")
    public static void vorbereiten()
    {
        Object gui = MixinBruecke.feldLesen(Minecraft.getInstance().gameRenderer, "guiRenderer");

        if (gui == registriertesGui || gui == null)
        {
            return;
        }

        Map<Class<? extends PictureInPictureRenderState>, PictureInPictureRenderer<?>> vorher =
            (Map<Class<? extends PictureInPictureRenderState>, PictureInPictureRenderer<?>>)
                MixinBruecke.feldLesen(gui, "pictureInPictureRenderers");
        PictureInPictureRenderer<?> figur = vorher.get(GuiEntityRenderState.class);

        if (figur instanceof InventarFigurRenderer)
        {
            registriertesGui = gui;
            return;
        }

        if (figur == null || figur.getClass() != GuiEntityRenderer.class)
        {
            throw new IllegalStateException("Cannot scope foreign inventory entity renderer: " + figur);
        }

        Map<Class<? extends PictureInPictureRenderState>, PictureInPictureRenderer<?>> nachher = new HashMap<>(vorher);
        nachher.put(GuiEntityRenderState.class, new InventarFigurRenderer());

        if (!MixinBruecke.feldSchreiben(gui, "pictureInPictureRenderers", Map.copyOf(nachher)))
        {
            throw new IllegalStateException("Cannot install inventory entity render target scope");
        }

        figur.close();
        registriertesGui = gui;
    }

    @Override
    protected void renderToTexture(GuiEntityRenderState zustand, PoseStack stapel, SubmitNodeCollector sammler)
    {
        GpuTextureView alteFarbe = Zeichenziel.farbe();
        GpuTextureView alteTiefe = Zeichenziel.tiefe();
        RenderPass.RenderArea alterBereich = Zeichenbereich.aktiv();
        boolean vorherAktiv = mchorse.bbs_mod.forms.FormTranslucentQueue.suspend();

        try
        {
            Zeichenziel.setzen(this.textur(true), this.textur(false));
            Zeichenbereich.freigeben();
            super.renderToTexture(zustand, stapel, sammler);
        }
        finally
        {
            Zeichenziel.setzen(alteFarbe, alteTiefe);

            if (alterBereich == null)
            {
                Zeichenbereich.freigeben();
            }
            else
            {
                Zeichenbereich.setzen(alterBereich.x(), alterBereich.y(), alterBereich.width(), alterBereich.height());
            }

            mchorse.bbs_mod.forms.FormTranslucentQueue.restore(vorherAktiv);
        }
    }

    private GpuTextureView textur(boolean farbe)
    {
        try
        {
            if (farbFeld == null)
            {
                farbFeld = PictureInPictureRenderer.class.getDeclaredField("textureView");
                tiefenFeld = PictureInPictureRenderer.class.getDeclaredField("depthTextureView");
                farbFeld.setAccessible(true);
                tiefenFeld.setAccessible(true);
            }

            GpuTextureView textur = (GpuTextureView) (farbe ? farbFeld : tiefenFeld).get(this);

            if (textur == null)
            {
                throw new IllegalStateException("Inventory entity texture was not prepared");
            }

            return textur;
        }
        catch (ReflectiveOperationException e)
        {
            throw new IllegalStateException("Cannot read inventory entity texture", e);
        }
    }
}
