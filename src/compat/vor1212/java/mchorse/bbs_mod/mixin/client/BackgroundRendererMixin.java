package mchorse.bbs_mod.mixin.client;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.compat.gpu.Nebel;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogParameters;
import net.minecraft.client.renderer.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/* Vor 1.21.2 gibt setupFog nichts zurueck, sondern setzt den Nebel direkt in RenderSystem. */
@Mixin(FogRenderer.class)
public class BackgroundRendererMixin
{
    @Inject(method = "setupFog", at = @At("RETURN"))
    private static void onApplyFog(Camera kamera, FogRenderer.FogMode modus, float sichtweite, boolean dicht, float teilbild, CallbackInfo info)
    {
        if (MixinBruecke.rufe("OrthoNebelLogik", "nebelWegschieben", Nebel.aktuell()) instanceof FogParameters weg)
        {
            RenderSystem.setShaderFogStart(weg.start());
            RenderSystem.setShaderFogEnd(weg.end());
            RenderSystem.setShaderFogShape(weg.shape());
        }
    }
}
