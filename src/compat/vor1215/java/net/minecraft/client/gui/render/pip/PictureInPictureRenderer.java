package net.minecraft.client.gui.render.pip;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import mchorse.bbs_mod.compat.gpu.Vorgaben;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.BlitRenderState;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import com.mojang.blaze3d.textures.FilterMode;
import org.joml.Matrix4f;

/**
 * BBS-Shim fuer Minecraft vor 1.21.6: rendert ein Element in ein eigenes Ziel und blittet es in
 * die GUI. Ablauf wie 1.21.11 (Ortho 0..w, h..0, z -1000..1000; Verschiebung (w/2, getTranslateY);
 * Skalierung (s, s, -s) mit s = GUI-Skala * scale; Blit mit gespiegeltem V), gezeichnet ueber
 * 1.21.4s Puffer und ein TextureTarget.
 */
public abstract class PictureInPictureRenderer<T extends PictureInPictureRenderState> implements AutoCloseable
{
    protected final MultiBufferSource.BufferSource bufferSource;
    private TextureTarget ziel;

    protected PictureInPictureRenderer(MultiBufferSource.BufferSource bufferSource)
    {
        this.bufferSource = bufferSource;
    }

    public void prepare(T zustand, GuiRenderState gui, int guiSkala)
    {
        int breite = (zustand.x1() - zustand.x0()) * guiSkala;
        int hoehe = (zustand.y1() - zustand.y0()) * guiSkala;

        if (breite <= 0 || hoehe <= 0)
        {
            return;
        }

        boolean bereit = this.ziel != null && this.ziel.width == breite && this.ziel.height == hoehe;

        if (!bereit || !this.textureIsReadyToBlit(zustand))
        {
            if (this.ziel == null) this.ziel = mchorse.bbs_mod.compat.gpu.Ziele.neu(breite, hoehe);
            else if (!bereit) this.ziel.resize(breite, hoehe);

            var hauptziel = Minecraft.getInstance().getMainRenderTarget();

            this.ziel.setClearColor(0F, 0F, 0F, 0F);
            this.ziel.clear();
            this.ziel.bindWrite(true);
            RenderSystem.backupProjectionMatrix();
            mchorse.bbs_mod.compat.gpu.Ziele.projektion(new Matrix4f().setOrtho(0F, breite, hoehe, 0F, -1000F, 1000F), ProjectionType.ORTHOGRAPHIC);
            RenderSystem.getModelViewStack().pushMatrix();
            RenderSystem.getModelViewStack().identity();

            PoseStack pose = new PoseStack();
            float s = guiSkala * zustand.scale();

            pose.translate(breite / 2F, this.getTranslateY(hoehe, guiSkala), 0F);
            pose.scale(s, s, -s);
            this.renderToTexture(zustand, pose);
            this.bufferSource.endBatch();

            RenderSystem.getModelViewStack().popMatrix();
            RenderSystem.restoreProjectionMatrix();
            hauptziel.bindWrite(true);
        }

        this.blitTexture(zustand, gui);
    }

    protected void blitTexture(T zustand, GuiRenderState gui)
    {
        gui.submitBlitToCurrentLayer(new BlitRenderState(RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA,
            TextureSetup.singleTexture(this.ziel.getColorTextureView(), Vorgaben.samplerCache().getRepeat(FilterMode.NEAREST)),
            zustand.pose(), zustand.x0(), zustand.y0(), zustand.x1(), zustand.y1(), 0F, 1F, 1F, 0F, -1, zustand.scissorArea(), null));
    }

    protected boolean textureIsReadyToBlit(T zustand)
    {
        return false;
    }

    protected float getTranslateY(int hoehe, int guiSkala)
    {
        return hoehe;
    }

    @Override
    public void close()
    {
        if (this.ziel != null)
        {
            this.ziel.destroyBuffers();
            this.ziel = null;
        }
    }

    public abstract Class<T> getRenderStateClass();

    protected abstract void renderToTexture(T zustand, PoseStack pose);

    protected abstract String getTextureLabel();
}
