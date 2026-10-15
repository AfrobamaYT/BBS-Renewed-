package mchorse.bbs_mod.compat;

import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;

/**
 * Minecraft 26.3 binds textures as combined image sampler uniforms; before that the pass
 * calls it bindTexture. Injected into RenderPass, see {@link PoseStackRotations}.
 */
public interface RenderPassTextures
{
    default void setUniform(String name, GpuTextureView view, GpuSampler sampler)
    {
        ((RenderPass) this).bindTexture(name, view, sampler);
    }
}
