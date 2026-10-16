package mchorse.bbs_mod.mixin.client;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Chroma-Himmel, zweite Haelfte: der Nebel bekommt dieselbe Farbe.
 *
 * <p>⛔ OHNE DAS BLEIBT EIN SAUM. Die 1.21er Fassung faerbte nur den Himmel; ferne Landschaft
 * blendete weiter in die Vanilla-Nebelfarbe, und genau dieser Rand liess sich nicht wegkeyen
 * (im Port-Vermerk von {@code WorldRendererMixin} als bekannter Mangel dokumentiert).
 */
@Mixin(FogRenderer.class)
public class ChromaNebelMixin
{
    @Inject(method = "computeFogColor", at = @At("RETURN"))
    private void bbs$chromaNebel(Camera kamera, float teilbild, ClientLevel welt,
        int sichtweite, float dunkelheit, Vector4f farbe, CallbackInfo info)
    {
        MixinBruecke.rufe("ChromaHimmelLogik", "nebel", farbe);
    }
}
