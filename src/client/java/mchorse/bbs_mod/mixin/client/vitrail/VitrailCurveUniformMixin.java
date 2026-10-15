package mchorse.bbs_mod.mixin.client.vitrail;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.vitrail.uniform.UniformCatalog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "dev.vitrail.render.PackValues", remap = false)
public abstract class VitrailCurveUniformMixin
{
    @ModifyReturnValue(method = {"catalog", "geometryCatalog", "shadowGeometryCatalog"}, at = @At("RETURN"))
    private UniformCatalog bbs$catalog(UniformCatalog base) throws Throwable
    { return (UniformCatalog) MixinBruecke.rufePflicht("VitrailShaderCurveLogik", "catalog", base); }

    @Inject(method = "advance", at = @At("RETURN"))
    private void bbs$frame(CallbackInfo callback) throws Throwable
    { MixinBruecke.rufePflicht("VitrailShaderCurveLogik", "advance"); }
}
