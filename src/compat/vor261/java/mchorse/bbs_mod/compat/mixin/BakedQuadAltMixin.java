package mchorse.bbs_mod.compat.mixin;

import net.minecraft.client.renderer.block.model.BakedQuad;
import mchorse.bbs_mod.compat.BakedQuadAlt;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BakedQuad.class)
public abstract class BakedQuadAltMixin implements BakedQuadAlt
{}
