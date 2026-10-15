package mchorse.bbs_mod.compat.mixin;

import com.mojang.blaze3d.systems.RenderPassDescriptor;
import mchorse.bbs_mod.compat.RenderPassDescriptorBuilder;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(RenderPassDescriptor.class)
public abstract class RenderPassDescriptorBuilderMixin implements RenderPassDescriptorBuilder
{}
