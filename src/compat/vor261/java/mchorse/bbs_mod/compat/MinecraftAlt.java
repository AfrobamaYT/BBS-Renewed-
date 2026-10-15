package mchorse.bbs_mod.compat;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.textures.GpuTextureView;

/**
 * 26.2's window surface (blitFromTexture); before 26.1 the encoder presents a texture to the
 * window, which is what RenderTarget.blitToScreen does there. Injected into Minecraft.
 */
public interface MinecraftAlt
{
    default Fenster windowSurface()
    {
        return new Fenster();
    }

    final class Fenster
    {
        public void blitFromTexture(CommandEncoder encoder, GpuTextureView ansicht)
        {
            encoder.presentTexture(ansicht);
        }
    }
}
