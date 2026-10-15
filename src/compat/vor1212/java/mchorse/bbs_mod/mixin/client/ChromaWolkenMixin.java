package mchorse.bbs_mod.mixin.client;

import com.afrobama.blendertransform.ChromaHimmelLogik;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vor 1.21.2 (ersetzt die vor1215-Fassung): 1.21.1 zeichnet Wolken in LevelRenderer.renderClouds. */
@Mixin(LevelRenderer.class)
public class ChromaWolkenMixin
{
    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    private void bbs$chromaWolken(CallbackInfo info)
    {
        if (ChromaHimmelLogik.gelaendeVerbergen())
        {
            info.cancel();
        }
    }
}
