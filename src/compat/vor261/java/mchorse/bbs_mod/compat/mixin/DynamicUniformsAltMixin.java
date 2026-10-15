package mchorse.bbs_mod.compat.mixin;

import net.minecraft.client.renderer.DynamicUniforms;
import mchorse.bbs_mod.compat.DynamicUniformsAlt;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(DynamicUniforms.class)
public abstract class DynamicUniformsAltMixin implements DynamicUniformsAlt
{}
