package mchorse.bbs_mod.compat.mixin;

import com.mojang.blaze3d.buffers.GpuBuffer;
import mchorse.bbs_mod.compat.GpuBufferAlt;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(GpuBuffer.class)
public abstract class GpuBufferAltMixin implements GpuBufferAlt
{}
