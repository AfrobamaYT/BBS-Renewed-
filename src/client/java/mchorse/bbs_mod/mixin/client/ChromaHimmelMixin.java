package mchorse.bbs_mod.mixin.client;

import com.afrobama.blendertransform.ChromaHimmelLogik;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Chroma-Himmel, 26.2-Fassung: die eingesammelte Himmelsfarbe wird ueberschrieben.
 *
 * <p>⛔ AM ZUSTAND, NICHT AM ZEICHNEN. {@code addSkyPass} ist privat und haengt im Bildgraphen;
 * der oeffentliche Punkt davor ist {@code extractRenderState}, das den {@link SkyRenderState}
 * fuellt. Wer dort {@code skyColor} setzt, faerbt den Himmel, ohne einen einzigen Zeichenaufruf
 * anzufassen.
 */
@Mixin(SkyRenderer.class)
public class ChromaHimmelMixin
{
    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void bbs$chromaHimmel(ClientLevel welt, float teilbild, Camera kamera,
        SkyRenderState zustand, CallbackInfo info)
    {
        Integer farbe = ChromaHimmelLogik.farbeOderNull();

        if (farbe != null)
        {
            zustand.skyColor = farbe;
        }
    }
}
