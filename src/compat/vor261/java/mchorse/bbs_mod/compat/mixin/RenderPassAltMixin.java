package mchorse.bbs_mod.compat.mixin;

import com.mojang.blaze3d.systems.RenderPass;
import mchorse.bbs_mod.compat.RenderPassAlt;
import org.spongepowered.asm.mixin.Mixin;

/** Adds {@link RenderPassAlt} to Mojang's RenderPass interface at runtime (Loom injects it for compiling). */
@Mixin(RenderPass.class)
public interface RenderPassAltMixin extends RenderPassAlt
{}
