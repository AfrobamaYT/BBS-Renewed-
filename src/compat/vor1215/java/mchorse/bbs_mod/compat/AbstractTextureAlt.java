package mchorse.bbs_mod.compat;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.opengl.GlTextureView;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import mchorse.bbs_mod.compat.gpu.Vorgaben;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.lwjgl.opengl.GL11;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * vor1215: 1.21.11s getTexture/getTextureView/getSampler an 1.21.4s AbstractTexture. Groesse
 * und Mip-Stufen liest die Huelle aus GL; der Sampler folgt den Filtern der Textur (1.21.4
 * stellt sie an der Textur selbst ein, Wiederholen ist dort Standard).
 */
public interface AbstractTextureAlt
{
    Map<AbstractTexture, GlTextureView> SICHTEN = new WeakHashMap<>();

    default GpuTexture getTexture()
    {
        return this.getTextureView().texture();
    }

    default GpuTextureView getTextureView()
    {
        AbstractTexture textur = (AbstractTexture) this;
        int id = textur.getId();
        GlTextureView sicht = SICHTEN.get(textur);

        if (sicht == null || sicht.texture().id != id)
        {
            GlStateManager._bindTexture(id);

            int breite = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            int hoehe = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            int mips = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, 33085) + 1;
            GlTexture huelle = new GlTexture(id, textur.getClass().getSimpleName(), TextureFormat.RGBA8, Math.max(1, breite), Math.max(1, hoehe), Math.max(1, Math.min(mips, 16)));

            sicht = new GlTextureView(huelle, 0, huelle.getMipLevels());
            SICHTEN.put(textur, sicht);
        }

        return sicht;
    }

    default GpuSampler getSampler()
    {
        GlStateManager._bindTexture(((AbstractTexture) this).getId());

        int mag = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER);
        int min = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER);
        boolean mips = min != GL11.GL_LINEAR && min != GL11.GL_NEAREST;

        return Vorgaben.samplerCache().getRepeat(mag == GL11.GL_LINEAR ? FilterMode.LINEAR : FilterMode.NEAREST, mips);
    }
}
