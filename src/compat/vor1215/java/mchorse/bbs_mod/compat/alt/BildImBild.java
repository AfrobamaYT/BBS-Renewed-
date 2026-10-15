package mchorse.bbs_mod.compat.alt;

import com.mojang.blaze3d.vertex.PoseStack;
import mchorse.bbs_mod.compat.gpu.SofortSammler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;

/**
 * 26.2's PictureInPictureRenderer shape vor 1.21.5: die Submits zeichnet der SofortSammler
 * sofort in den Pufferquell der Basisklasse, die ihn danach abschliesst.
 */
public abstract class BildImBild<T extends PictureInPictureRenderState> extends PictureInPictureRenderer<T>
{
    protected BildImBild()
    {
        super(Minecraft.getInstance().renderBuffers().bufferSource());
    }

    @Override
    protected final void renderToTexture(T zustand, PoseStack pose)
    {
        this.renderToTexture(zustand, pose, new SofortSammler(this.bufferSource));
    }

    protected abstract void renderToTexture(T zustand, PoseStack pose, SubmitNodeCollector sammler);
}
