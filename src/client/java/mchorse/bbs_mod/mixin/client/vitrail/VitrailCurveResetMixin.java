package mchorse.bbs_mod.mixin.client.vitrail;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "dev.vitrail.render.PackChoice", remap = false)
public abstract class VitrailCurveResetMixin
{
    @Inject(method = "load", at = @At("HEAD"))
    private static void bbs$reset(CallbackInfo callback) throws Throwable
    { MixinBruecke.rufePflicht("VitrailShaderCurveLogik", "reset"); }
}
