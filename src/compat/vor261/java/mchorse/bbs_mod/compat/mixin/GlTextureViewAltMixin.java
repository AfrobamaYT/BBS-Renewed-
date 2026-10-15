package mchorse.bbs_mod.compat.mixin;

import com.mojang.blaze3d.opengl.GlTextureView;
import mchorse.bbs_mod.compat.GlTextureViewAlt;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(GlTextureView.class)
public abstract class GlTextureViewAltMixin implements GlTextureViewAlt
{}
