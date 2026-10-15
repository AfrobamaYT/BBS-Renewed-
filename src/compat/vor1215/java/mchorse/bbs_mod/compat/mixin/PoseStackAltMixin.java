package mchorse.bbs_mod.compat.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import mchorse.bbs_mod.compat.PoseStackAlt;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(PoseStack.class)
public abstract class PoseStackAltMixin implements PoseStackAlt
{}
