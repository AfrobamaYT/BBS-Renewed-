package mchorse.bbs_mod.compat.alt;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;

/**
 * 26.2's PictureInPictureRenderer shape (no-argument constructor, renderToTexture with a submit
 * collector) on the class before 26.1, which takes a buffer source and draws immediately. The
 * submits go to the game's feature dispatcher and are drawn right after, as 1.21.11's own
 * GuiEntityRenderer does; the base class ends the buffer batch afterwards.
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
        FeatureRenderDispatcher merkmale = Minecraft.getInstance().gameRenderer.getFeatureRenderDispatcher();

        this.renderToTexture(zustand, pose, merkmale.getSubmitNodeStorage());
        merkmale.renderAllFeatures();
    }

    protected abstract void renderToTexture(T zustand, PoseStack pose, SubmitNodeCollector sammler);
}
