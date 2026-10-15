package mchorse.bbs_mod.compat;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;

/** 26.2's GameRenderer accessors, for the versions before 26.1. Injected into GameRenderer. */
public interface GameRendererAlt
{
    default Camera mainCamera()
    {
        return ((GameRenderer) this).getMainCamera();
    }

    default Lighting lighting()
    {
        return ((GameRenderer) this).getLighting();
    }

    default GpuTextureView lightmap()
    {
        return ((GameRenderer) this).lightTexture().getTextureView();
    }

    /**
     * 26.2's feature dispatcher renders any submit storage; before 26.1 it renders its own. The
     * wrapper gives a storage of BBS' its own dispatcher (same arguments as GameRenderer builds
     * the game's) and ends the batch afterwards, as GameRenderer does after renderAllFeatures.
     */
    default Merkmale featureRenderDispatcher()
    {
        return Merkmale.INSTANZ;
    }

    final class Merkmale
    {
        private static final Merkmale INSTANZ = new Merkmale();
        private final java.util.Map<net.minecraft.client.renderer.SubmitNodeStorage, net.minecraft.client.renderer.feature.FeatureRenderDispatcher> eigene = new java.util.WeakHashMap<>();

        public void renderAllFeatures(net.minecraft.client.renderer.SubmitNodeStorage speicher)
        {
            Minecraft mc = Minecraft.getInstance();
            net.minecraft.client.renderer.feature.FeatureRenderDispatcher zeichner = this.eigene.computeIfAbsent(speicher, s ->
                new net.minecraft.client.renderer.feature.FeatureRenderDispatcher(s, mc.getBlockRenderer(), mc.renderBuffers().bufferSource(),
                    mc.getAtlasManager(), mc.renderBuffers().outlineBufferSource(), mc.renderBuffers().crumblingBufferSource(), mc.font));

            zeichner.renderAllFeatures();
            mc.renderBuffers().bufferSource().endBatch();
        }

        public void renderAllFeatures()
        {
            ((GameRenderer) (Object) Minecraft.getInstance().gameRenderer).getFeatureRenderDispatcher().renderAllFeatures();
        }
    }

    /** 26.2 keeps the main target on the game renderer, before that Minecraft holds it. */
    default RenderTarget mainRenderTarget()
    {
        return Minecraft.getInstance().getMainRenderTarget();
    }
}
