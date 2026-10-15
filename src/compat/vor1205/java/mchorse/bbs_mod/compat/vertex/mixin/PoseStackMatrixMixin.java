package mchorse.bbs_mod.compat.vertex.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import mchorse.bbs_mod.compat.vertex.MatrixStapelAlt;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(PoseStack.class)
public abstract class PoseStackMatrixMixin implements MatrixStapelAlt
{}
