package mchorse.bbs_mod.mixin.client;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.joml.Vector4f;

/**
 * Vor 26.1 gibt {@code setupFog} die Nebelfarbe zurueck; {@link FogData} ist dort eine lokale
 * Variable, die noch in derselben Methode in den Uniformpuffer geht. Deshalb greift dieser
 * Ersatz das Datenobjekt direkt vor dem Hochladen ab (mapBuffer), statt am RETURN.
 */
@Mixin(FogRenderer.class)
public class BackgroundRendererMixin
{
    @Inject(method = "setupFog", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/GpuDevice;createCommandEncoder()Lcom/mojang/blaze3d/systems/CommandEncoder;"))
    private void onApplyFog(CallbackInfoReturnable<Vector4f> info, @Local FogData daten)
    {
        MixinBruecke.rufe("OrthoNebelLogik", "nebelWegschieben", daten);
    }
}
