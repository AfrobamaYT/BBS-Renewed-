package mchorse.bbs_mod.mixin.client;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogParameters;
import net.minecraft.client.renderer.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vor 1.21.5: 1.21.4s setupFog gibt FogParameters zurueck; OrthoNebelLogik ersetzt sie. */
@Mixin(FogRenderer.class)
public class BackgroundRendererMixin
{
    @Inject(method = "setupFog", at = @At("RETURN"), cancellable = true)
    private static void onApplyFog(Camera kamera, FogRenderer.FogMode modus, Vector4f farbe, float sichtweite, boolean dicht, float teilbild,
        CallbackInfoReturnable<FogParameters> info)
    {
        if (MixinBruecke.rufe("OrthoNebelLogik", "nebelWegschieben", info.getReturnValue()) instanceof FogParameters weg)
        {
            info.setReturnValue(weg);
        }
    }
}
