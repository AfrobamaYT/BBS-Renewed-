package mchorse.bbs_mod.compat;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.textures.GpuTextureView;
import mchorse.bbs_mod.compat.gpu.Beleuchtung;
import mchorse.bbs_mod.compat.mixin.LightTextureAccessor;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;

/**
 * vor1215: 26.2s GameRenderer-Zugriffe fuer 1.21.4. Den Feature-Dispatcher von 1.21.9+ gibt es
 * hier nicht; die Lichtkarte ist das Ziel der LightTexture.
 */
public interface GameRendererAlt
{
    default Camera mainCamera()
    {
        return ((GameRenderer) this).getMainCamera();
    }

    default Beleuchtung lighting()
    {
        return Beleuchtung.INSTANZ;
    }

    default GpuTextureView lightmap()
    {
        return ((LightTextureAccessor) ((GameRenderer) this).lightTexture()).bbs$ziel().getColorTextureView();
    }

    default net.minecraft.client.renderer.state.LevelRenderState getLevelRenderState()
    {
        return net.minecraft.client.renderer.state.LevelRenderState.INSTANZ;
    }

    default RenderTarget mainRenderTarget()
    {
        return Minecraft.getInstance().getMainRenderTarget();
    }
}
