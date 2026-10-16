package mchorse.bbs_mod.mixin.client;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import com.mojang.renderpearl.api.commands.RenderPass;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Chroma-Himmel ohne Gelaende: die Wolken fallen mit weg.
 *
 * <p>⛔ SONST SCHWEBEN SIE VOR DER FARBFLAECHE. Sie haengen nicht am Gelaende und blieben
 * deshalb stehen, auch wenn alles andere schon weg war (Nutzerbefund 2026-09-01).</p>
 */
@Mixin(CloudRenderer.class)
public class ChromaWolkenMixin
{
    @Inject(method = "render(Lnet/minecraft/client/CloudStatus;Lcom/mojang/renderpearl/api/commands/RenderPass;)V", at = @At("HEAD"), cancellable = true)
    private void bbs$chromaWolken(CloudStatus art, RenderPass pass, CallbackInfo info)
    {
        if (MixinBruecke.rufeUndAbbrechen("ChromaHimmelLogik", "gelaendeVerbergen"))
        {
            info.cancel();
        }
    }
}
