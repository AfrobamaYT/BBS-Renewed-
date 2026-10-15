package mchorse.bbs_mod.compat.alt;

import com.mojang.blaze3d.textures.GpuTextureView;

/** 26.2's RenderPass.RenderArea, for the OpenGL versions before 26.1. */
public record RenderArea(int x, int y, int width, int height)
{
    public boolean fillsTexture(GpuTextureView view)
    {
        return this.x == 0 && this.y == 0 && this.width == view.getWidth(0) && this.height == view.getHeight(0);
    }
}
