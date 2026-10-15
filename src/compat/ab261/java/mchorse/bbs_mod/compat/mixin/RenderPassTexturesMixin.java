package mchorse.bbs_mod.compat.mixin;

import com.mojang.blaze3d.systems.RenderPass;
import mchorse.bbs_mod.compat.RenderPassTextures;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(RenderPass.class)
public abstract class RenderPassTexturesMixin implements RenderPassTextures
{}
