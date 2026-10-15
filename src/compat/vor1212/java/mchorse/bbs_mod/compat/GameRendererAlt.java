package mchorse.bbs_mod.compat;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.textures.GpuTextureView;
import mchorse.bbs_mod.compat.gpu.Beleuchtung;
import mchorse.bbs_mod.compat.mixin.LightTextureAccessor;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.opengl.GlTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;

/**
 * vor1215: 26.2s GameRenderer-Zugriffe fuer 1.21.4. Den Feature-Dispatcher von 1.21.9+ gibt es
 * hier nicht; die Lichtkarte ist das Ziel der LightTexture.
 */
public interface GameRendererAlt
{
    Map<Integer, GlTextureView> LICHTKARTEN = new HashMap<>();

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
        /* vor1212: Lichtkarte ist eine DynamicTexture; ihre GL-Textur einmal als Sicht huellen. */
        int id = ((LightTextureAccessor) ((GameRenderer) this).lightTexture()).bbs$textur().getId();

        return LICHTKARTEN.computeIfAbsent(id, (k) -> new GlTextureView(new GlTexture(k, "lightmap", TextureFormat.RGBA8, 16, 16, 1), 0, 1));
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
