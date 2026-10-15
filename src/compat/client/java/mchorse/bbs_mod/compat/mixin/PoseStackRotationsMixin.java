package mchorse.bbs_mod.compat.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import mchorse.bbs_mod.compat.PoseStackRotations;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(PoseStack.class)
public abstract class PoseStackRotationsMixin implements PoseStackRotations
{}
