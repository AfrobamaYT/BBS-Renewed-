package mchorse.bbs_mod.compat.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import mchorse.bbs_mod.compat.PoseAlt;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(PoseStack.Pose.class)
public abstract class PoseAltMixin implements PoseAlt
{}
