package com.afrobama.bbstools.client.mixin;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import mchorse.bbs_mod.cubic.render.vao.BOBJModelVAO;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Shape wrappers/drivers always run; cache only the terminal evaluated matrix update. */
@Mixin(value = BOBJModelVAO.class, remap = false)
public abstract class BOBJModelVAOPausedCacheMixin
{
    @Unique
    private final Object[] bbs$zustand = new Object[2];

    @Inject(method = "updateMesh(Lmchorse/bbs_mod/ui/framework/elements/utils/StencilMap;[Lorg/joml/Matrix4f;Z)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    private void bbs$skipWhenPosesUnchanged(StencilMap stencil, Matrix4f[] matrices,
        boolean gpuAllowed, CallbackInfo ci)
    {
        if (MixinBruecke.rufeUndAbbrechen("BOBJModelVAOPausedCacheLogik", "skipWhenPosesUnchanged",
            this.bbs$zustand, stencil, (BOBJModelVAO) (Object) this, matrices, gpuAllowed)) ci.cancel();
    }

    @Inject(method = "updateMesh(Lmchorse/bbs_mod/ui/framework/elements/utils/StencilMap;[Lorg/joml/Matrix4f;Z)V",
        at = @At("RETURN"), remap = false)
    private void bbs$rememberCompletedPose(StencilMap stencil, Matrix4f[] matrices,
        boolean gpuAllowed, CallbackInfo ci)
    {
        MixinBruecke.rufe("BOBJModelVAOPausedCacheLogik", "rememberCompletedPose",
            this.bbs$zustand, stencil, (BOBJModelVAO) (Object) this, matrices, gpuAllowed);
    }
}
