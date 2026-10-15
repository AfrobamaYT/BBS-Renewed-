package mchorse.bbs_mod.mixin.client;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Before 26.1 (replaces the shared mixin): computeFogColor returns the colour instead of filling a parameter. */
@Mixin(FogRenderer.class)
public class ChromaNebelMixin
{
    @Inject(method = "computeFogColor", at = @At("RETURN"))
    private void bbs$chromaNebel(Camera kamera, float teilbild, ClientLevel welt, int sichtweite, float dunkelheit,
        CallbackInfoReturnable<Vector4f> info)
    {
        MixinBruecke.rufe("ChromaHimmelLogik", "nebel", info.getReturnValue());
    }
}
