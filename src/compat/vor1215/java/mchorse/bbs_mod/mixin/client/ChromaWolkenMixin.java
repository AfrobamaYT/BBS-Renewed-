package mchorse.bbs_mod.mixin.client;

import com.afrobama.blendertransform.ChromaHimmelLogik;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Before 26.1 (replaces the shared mixin): CloudRenderer.render(colour, status, height, camera, time, partial). */
@Mixin(CloudRenderer.class)
public class ChromaWolkenMixin
{
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void bbs$chromaWolken(int farbe, CloudStatus art, float hoehe, org.joml.Matrix4f ansicht, org.joml.Matrix4f projektion, Vec3 kameraPosition, float teilbild, CallbackInfo info)
    {
        if (ChromaHimmelLogik.gelaendeVerbergen())
        {
            info.cancel();
        }
    }
}
