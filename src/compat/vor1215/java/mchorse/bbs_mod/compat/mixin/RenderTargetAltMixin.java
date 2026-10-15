package mchorse.bbs_mod.compat.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import mchorse.bbs_mod.compat.RenderTargetAlt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderTarget.class)
public abstract class RenderTargetAltMixin implements RenderTargetAlt
{
    /* Minecraft loescht die Texturen; GL vergibt die Namen ggf. neu (auch bei gleicher Groesse).
     * Ohne neue Huelle fuehrte der FBO-Cache (RahmenPuffer) noch die verwaiste Textur. */
    @Inject(method = "destroyBuffers", at = @At("HEAD"))
    private void bbs$huellenWeg(CallbackInfo info)
    {
        RenderTargetAlt.HUELLEN.remove((RenderTarget) (Object) this);
    }
}
