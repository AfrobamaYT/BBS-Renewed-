package mchorse.bbs_mod.mixin.client.vitrail;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "dev.vitrail.render.TargetSurface", remap = false)
public abstract class BbsTargetSurfaceMixin
{
    @Shadow private GpuTextureView view;
    @Shadow private GpuTextureView baseView;

    @Inject(method = "allocate(II)V", at = @At("RETURN"))
    private void bbs$views(int width, int height, CallbackInfo callback) throws Throwable
    { MixinBruecke.rufePflicht("VitrailFrameLogik", "views", this.view, this.baseView); }

    @Inject(method = "close()V", at = @At("HEAD"))
    private void bbs$forget(CallbackInfo callback) throws Throwable
    { MixinBruecke.rufePflicht("VitrailFrameLogik", "forget", this.view); }
}
