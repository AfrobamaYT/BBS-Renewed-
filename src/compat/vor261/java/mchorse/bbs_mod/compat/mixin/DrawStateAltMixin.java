package mchorse.bbs_mod.compat.mixin;

import com.mojang.blaze3d.vertex.MeshData;
import mchorse.bbs_mod.compat.DrawStateAlt;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(MeshData.DrawState.class)
public abstract class DrawStateAltMixin implements DrawStateAlt
{}
