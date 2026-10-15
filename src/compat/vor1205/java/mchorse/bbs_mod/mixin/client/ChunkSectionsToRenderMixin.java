package mchorse.bbs_mod.mixin.client;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vor 1.21.6 (ersetzt den gemeinsamen Haken auf ChunkSectionsToRender.renderGroup): dieselben
 * zwei Haken auf LevelRenderer.renderSectionLayer, je Schicht. solid zaehlt als Gruppe OPAQUE,
 * translucent als TRANSLUCENT; das Gelaende-Verbergen gilt fuer jede Schicht.
 */
@Mixin(LevelRenderer.class)
public class ChunkSectionsToRenderMixin
{
    @Inject(method = mchorse.bbs_mod.compat.alt.MixinZiele.ABSCHNITT_EBENE, at = @At("HEAD"))
    private void onRenderGroup(RenderType ebene, com.mojang.blaze3d.vertex.PoseStack pose, double x, double y, double z, Matrix4f projektion, CallbackInfo info)
    {
        ChunkSectionLayerGroup gruppe = ebene == RenderType.translucent() ? ChunkSectionLayerGroup.TRANSLUCENT
            : ebene == RenderType.solid() ? ChunkSectionLayerGroup.OPAQUE : null;

        if (gruppe != null)
        {
            MixinBruecke.rufe("ChunkSectionsToRenderLogik", "renderGroup", gruppe);
        }
    }

    @Inject(method = mchorse.bbs_mod.compat.alt.MixinZiele.ABSCHNITT_EBENE, at = @At("HEAD"), cancellable = true)
    private void bbs$gelaendeVerbergen(CallbackInfo info)
    {
        if (com.afrobama.blendertransform.ChromaHimmelLogik.gelaendeVerbergen())
        {
            info.cancel();
        }
    }
}
