package mchorse.bbs_mod.mixin.client;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import com.mojang.blaze3d.pipeline.RenderTarget;
import org.joml.Matrix4f;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vor 1.21.2 (ersetzt die vor1215-Fassung): wie dort, an 1.21.1s renderLevel (mit LightTexture,
 * ohne Zuteiler); der Himmel heisst renderSky. Urspruenglich: dieselben Brueckenaufrufe an 1.21.4s
 * renderLevel(Zuteiler, Takt, Umriss, Kamera, GameRenderer, Blick, Projektion). Nebel steht
 * am Kopf von renderLevel noch nicht fest, die Messung bekommt dafuer null.
 */
@Mixin(LevelRenderer.class)
public class WorldRendererMixin
{
    @Shadow
    private RenderTarget entityTarget;

    @Inject(method = "renderSky", at = @At("HEAD"))
    private void bbs$himmelspassGemessen(CallbackInfo info)
    {
        MixinBruecke.rufe("WeltRendererLogik", "himmelspass");
    }

    @Inject(method = "renderLevel", at = @At("HEAD"))
    public void onRenderWorldStart(CallbackInfo info)
    {
        MixinBruecke.rufe("WeltRendererLogik", "renderAnfang");
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    public void onRenderWorldEnd(CallbackInfo info)
    {
        MixinBruecke.rufe("WeltRendererLogik", "renderEnde");
    }

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void onSetupFrustum(com.mojang.blaze3d.vertex.PoseStack pose, float teilbild, long zeit, boolean bloecke,
        net.minecraft.client.Camera kamera, net.minecraft.client.renderer.GameRenderer spielRenderer,
        net.minecraft.client.renderer.LightTexture licht, Matrix4f projektion, CallbackInfo info)
    {
        /* vor1205: die Blickmatrix steckt im PoseStack. */
        MixinBruecke.rufe("WeltRendererLogik", "projektionStempeln", bloecke, projektion, new Matrix4f(pose.last().pose()), null, null, true);
    }

    @Inject(at = @At("RETURN"), method = "resize")
    private void onResized(CallbackInfo info)
    {
        MixinBruecke.rufe("WeltRendererLogik", "groesseGeaendert", this.entityTarget);
    }
}
