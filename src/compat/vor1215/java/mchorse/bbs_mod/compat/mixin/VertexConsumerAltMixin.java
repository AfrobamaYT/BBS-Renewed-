package mchorse.bbs_mod.compat.mixin;

import com.mojang.blaze3d.vertex.VertexConsumer;
import mchorse.bbs_mod.compat.VertexConsumerAlt;
import org.spongepowered.asm.mixin.Mixin;

/** Fuegt VertexConsumerAlt zur Laufzeit an 1.21.4s VertexConsumer (Loom spritzt es fuers Kompilieren ein). */
@Mixin(VertexConsumer.class)
public interface VertexConsumerAltMixin extends VertexConsumerAlt
{}
