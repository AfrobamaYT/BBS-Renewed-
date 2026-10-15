package mchorse.bbs_mod.compat.mixin;

import mchorse.bbs_mod.compat.ModelBlockRendererAlt;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ModelBlockRenderer.class)
public abstract class ModelBlockRendererAltMixin implements ModelBlockRendererAlt
{}
