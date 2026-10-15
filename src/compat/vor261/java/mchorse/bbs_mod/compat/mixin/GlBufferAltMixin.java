package mchorse.bbs_mod.compat.mixin;

import com.mojang.blaze3d.opengl.GlBuffer;
import mchorse.bbs_mod.compat.GlBufferAlt;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(GlBuffer.class)
public abstract class GlBufferAltMixin implements GlBufferAlt
{}
