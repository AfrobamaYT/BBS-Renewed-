package mchorse.bbs_mod.compat.mixin;

import com.mojang.blaze3d.textures.GpuTexture;
import mchorse.bbs_mod.compat.GpuTextureAlt;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(GpuTexture.class)
public abstract class GpuTextureAltMixin implements GpuTextureAlt
{}
