package mchorse.bbs_mod.mixin.client.vitrail;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class BbsActorCollectionMixin
{
    @Inject(method = "submitFeatures", at = @At("HEAD"))
    private void bbs$begin(CallbackInfo callback) throws Throwable
    { MixinBruecke.rufePflicht("VitrailFrameLogik", "begin"); }
}
