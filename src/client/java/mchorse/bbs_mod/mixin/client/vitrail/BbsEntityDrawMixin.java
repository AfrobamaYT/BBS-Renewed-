package mchorse.bbs_mod.mixin.client.vitrail;

import com.mojang.renderpearl.api.commands.RenderPass;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.afrobama.bbstools.client.blender.MixinBruecke;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(targets = "dev.vitrail.render.EntityDraw", remap = false)
public abstract class BbsEntityDrawMixin
{
    @Inject(method = "opaqueFeatures(Z)V", at = @At("RETURN"))
    private static void bbs$opaque(boolean open, CallbackInfo ci) throws Throwable
    { MixinBruecke.rufePflicht("VitrailEntityDrawLogik", "opaque", open); }

    @Inject(method = "rotate()V", at = @At("HEAD"))
    private void bbs$reset(CallbackInfo ci) throws Throwable
    { MixinBruecke.rufePflicht("VitrailEntityDrawLogik", "reset"); }

    @WrapOperation(method = "record", at = @At(value = "INVOKE",
        target = "Lcom/mojang/renderpearl/api/commands/RenderPass;drawIndexed(IIIII)V"))
    private void bbs$drawActor(RenderPass pass, int count, int instances, int firstIndex,
        int baseVertex, int firstInstance, Operation<Void> original) throws Throwable
    {
        MixinBruecke.rufePflicht("VitrailEntityDrawLogik", "draw", pass,
            count, instances, firstIndex, baseVertex, firstInstance, original);
    }
}
