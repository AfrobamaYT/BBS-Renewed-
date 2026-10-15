package mchorse.bbs_mod.compat.mixin;

import com.mojang.blaze3d.systems.GpuDevice;
import mchorse.bbs_mod.compat.GpuDeviceAlt;
import org.spongepowered.asm.mixin.Mixin;

/** Adds {@link GpuDeviceAlt} to Mojang's GpuDevice interface at runtime (Loom injects it for compiling). */
@Mixin(GpuDevice.class)
public interface GpuDeviceAltMixin extends GpuDeviceAlt
{}
