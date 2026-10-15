package mchorse.bbs_mod.compat.vertex.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import mchorse.bbs_mod.compat.vertex.BufferBuilderAlt;
import mchorse.bbs_mod.compat.vertex.FormatZugriff;
import mchorse.bbs_mod.compat.vertex.Vertexpuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * vor1205: leert den offenen 1.21-Vertex (Vertexpuffer) bevor alte Aufrufe denselben Puffer
 * beschreiben oder abschliessen, und gibt Vertexpuffer das Format.
 */
@Mixin(BufferBuilder.class)
public abstract class BufferBuilderVertexMixin implements BufferBuilderAlt, FormatZugriff
{
    @Override
    @Accessor("format")
    public abstract VertexFormat bbs$format();

    @Shadow
    public abstract void putFloat(int stelle, float wert);

    @Shadow
    public abstract void nextElement();

    /** Ueberschreibt BufferVertexConsumer#vertex (Default-Methode): erst leeren, dann wie dort. */
    public VertexConsumer vertex(double x, double y, double z)
    {
        Vertexpuffer.abschliessen();
        this.putFloat(0, (float) x);
        this.putFloat(4, (float) y);
        this.putFloat(8, (float) z);
        this.nextElement();

        return (VertexConsumer) this;
    }

    @Inject(method = "vertex(FFFFFFFFFIIFFF)V", at = @At("HEAD"))
    private void bbs$vorGanzemVertex(CallbackInfo info)
    {
        Vertexpuffer.abschliessen();
    }

    @Inject(method = {"end", "endOrDiscardIfEmpty"}, at = @At("HEAD"))
    private void bbs$vorEnde(CallbackInfoReturnable<BufferBuilder.RenderedBuffer> info)
    {
        Vertexpuffer.abschliessen();
    }
}
