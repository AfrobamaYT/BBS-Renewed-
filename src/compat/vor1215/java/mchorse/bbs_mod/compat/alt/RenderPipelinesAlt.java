package mchorse.bbs_mod.compat.alt;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;

/**
 * Vor 1.21.5 (ersetzt die vor261-Fassung): 26.2s entity_translucent_cull auf der eigenen
 * GL-Schicht. Eine Iris-Zuordnung entfaellt, Iris fuer 1.21.4 kennt keine RenderPipelines.
 */
public final class RenderPipelinesAlt
{
    public static final RenderPipeline ENTITY_TRANSLUCENT_CULL = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
        .withLocation("pipeline/entity_translucent_cull")
        .withShaderDefine("ALPHA_CUTOUT", 0.1F)
        .withSampler("Sampler1")
        .withBlend(BlendFunction.TRANSLUCENT)
        .build();

    private RenderPipelinesAlt()
    {}
}
