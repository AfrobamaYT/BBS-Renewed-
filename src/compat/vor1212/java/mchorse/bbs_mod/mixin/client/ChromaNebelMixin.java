package mchorse.bbs_mod.mixin.client;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/* Vor 1.21.2 berechnet setupColor die Nebelfarbe in statische Felder und setzt die Loeschfarbe. */
@Mixin(FogRenderer.class)
public class ChromaNebelMixin
{
    @Shadow
    private static float fogRed;
    @Shadow
    private static float fogGreen;
    @Shadow
    private static float fogBlue;

    @Inject(method = "setupColor", at = @At("RETURN"))
    private static void bbs$chromaNebel(Camera kamera, float teilbild, ClientLevel welt, int sichtweite, float dunkelheit, CallbackInfo info)
    {
        Vector4f farbe = new Vector4f(fogRed, fogGreen, fogBlue, 1F);

        MixinBruecke.rufe("ChromaHimmelLogik", "nebel", farbe);

        if (farbe.x != fogRed || farbe.y != fogGreen || farbe.z != fogBlue)
        {
            fogRed = farbe.x;
            fogGreen = farbe.y;
            fogBlue = farbe.z;
            RenderSystem.clearColor(fogRed, fogGreen, fogBlue, 0F);
        }
    }
}
