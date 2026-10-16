package mchorse.bbs_mod.mixin.client.vitrail;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Map;

@Mixin(targets = "dev.vitrail.render.EntityDraw", remap = false)
public abstract class BbsEntityRegistrationMixin
{
    @Shadow @Final private static Map<RenderPipeline, ?> ELEMENTS;
    @Shadow @Final private static Map<RenderPipeline, ?> BLOCK_ELEMENTS;
    @Shadow @Final private static Map<RenderPipeline, ?> SHADOW_ELEMENTS;
    @Inject(method = "readPrograms(Ldev/vitrail/pack/source/OpenedPack;)V", at = @At("HEAD"))
    private void bbs$nativeRows(CallbackInfo ci) throws Throwable
    { MixinBruecke.rufePflicht("VitrailGeometryLogik", "register", ELEMENTS, BLOCK_ELEMENTS, SHADOW_ELEMENTS); }
}
