package mchorse.bbs_mod.mixin.client;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vor 1.21.5 (ersetzt den gemeinsamen Haken auf tryExtractRenderState): 1.21.4 zeichnet in render. */
@Mixin(BlockEntityRenderDispatcher.class)
public class BlockEntityRenderDispatcherMixin
{
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onRenderMain(CallbackInfo info)
    {
        if (MixinBruecke.rufeUndAbbrechen("WeltUeberschreibungLogik", "blockentitiesUnterdruecken"))
        {
            info.cancel();
        }
    }
}
