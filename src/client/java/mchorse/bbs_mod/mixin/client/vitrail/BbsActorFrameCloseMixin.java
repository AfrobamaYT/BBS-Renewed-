package mchorse.bbs_mod.mixin.client.vitrail;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "dev.vitrail.platform.EngineStages", remap = false)
public abstract class BbsActorFrameCloseMixin
{
    @Inject(method = "afterLevel()V", at = @At("RETURN"))
    private static void bbs$close(CallbackInfo callback) throws Throwable
    { MixinBruecke.rufePflicht("VitrailEntityDrawLogik", "closeActorFrame"); }

    @WrapOperation(method = "afterTranslucentFeatures()V", require = 1, at = @At(value = "INVOKE",
        target = "Ldev/vitrail/render/EntityDraw;translucentFeatures(Z)V"))
    private static void bbs$normalClose(boolean open, Operation<Void> original) throws Throwable
    { MixinBruecke.rufePflicht("VitrailEntityDrawLogik", "normalClose", open, original); }
}
