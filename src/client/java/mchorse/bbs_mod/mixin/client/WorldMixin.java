package mchorse.bbs_mod.mixin.client;

import com.afrobama.bbstools.client.blender.MixinBruecke;

import mchorse.bbs_mod.client.BBSRendering;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public class WorldMixin
{
    @Inject(method = "getRainLevel", at = @At("HEAD"), cancellable = true)
    public void onGetRainGradient(CallbackInfoReturnable<Float> info)
    {
        Object pegel = MixinBruecke.rufe("WetterUeberschreibungLogik", "regenpegel");

        if (pegel != null)
        {
            info.setReturnValue((Float) pegel);
        }
    }
}