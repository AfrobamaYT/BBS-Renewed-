package mchorse.bbs_mod.compat.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import mchorse.bbs_mod.compat.RenderPipelineBuilderAlt;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 26.2s GUI-Renderer zeichnet mit der Topologie der Pipeline; der von 1.21.11 nimmt fuer
 * GUI-Elemente immer Quads, ausser die Pipeline verlangt es anders (Fabric-Schalter).
 * Gemessen 2026-10-12: Dreiecks-Netze (Vektor-Schatten) zerfielen sonst in Diagonalen.
 * Eigener Mixin, weil Fabrics Schalter erst ab 1.21.5 existiert (vor1215 laesst ihn weg).
 */
@Mixin(RenderPipeline.Builder.class)
public abstract class RenderPipelineGuiTopologieMixin
{
    @Inject(method = "build", at = @At("HEAD"))
    private void bbs$guiTopologie(CallbackInfoReturnable<RenderPipeline> info)
    {
        if (((RenderPipelineBuilderAlt) this).bbs$topologie() != null)
        {
            ((FabricRenderPipeline.Builder) (Object) this).withUsePipelineDrawModeForGui(true);
        }
    }
}
