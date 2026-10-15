package mchorse.bbs_mod.compat.vertex.mixin;

import com.mojang.blaze3d.vertex.VertexConsumer;
import mchorse.bbs_mod.compat.vertex.VertexNeu;
import org.spongepowered.asm.mixin.Mixin;

/* Loom-Interface-Injection wirkt nur beim Kompilieren; zur Laufzeit haengt erst dieser Mixin
 * VertexNeu an VertexConsumer. Ohne ihn: NoSuchMethodError VertexConsumer.addVertex(FFF) beim
 * ersten Dashboard-Bild in echter 1.20.1-Instanz (2026-10-14; Dev-Laeufe zeigen es nicht). */
@Mixin(VertexConsumer.class)
public interface VertexConsumerNeuMixin extends VertexNeu
{}
