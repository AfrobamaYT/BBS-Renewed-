package mchorse.bbs_mod.compat.mixin;

import mchorse.bbs_mod.compat.RenderTypeAlt;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(RenderType.class)
public abstract class RenderTypeAltMixin implements RenderTypeAlt
{}
