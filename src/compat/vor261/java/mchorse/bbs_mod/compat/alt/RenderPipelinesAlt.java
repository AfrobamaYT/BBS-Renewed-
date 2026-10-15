package mchorse.bbs_mod.compat.alt;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Vanilla pipelines 26.2 has and the versions before 26.1 lack, built from the same snippet and
 * settings as 26.2's RenderPipelines (decompiled 26.2: ENTITY_SNIPPET, ALPHA_CUTOUT 0.1,
 * Sampler1, translucent blend, culling on).
 *
 * <p>Same location as in 26.2 (minecraft namespace): IrisModelBuffers packs Iris vertices only
 * for minecraft entity pipelines, and Iris has to be told which program draws it, because its
 * own table only lists the pipelines of this Minecraft version.</p>
 */
public final class RenderPipelinesAlt
{
    public static final RenderPipeline ENTITY_TRANSLUCENT_CULL = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
        .withLocation(Identifier.withDefaultNamespace("pipeline/entity_translucent_cull"))
        .withShaderDefine("ALPHA_CUTOUT", 0.1F)
        .withSampler("Sampler1")
        .withBlend(BlendFunction.TRANSLUCENT)
        .build();

    static
    {
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("iris"))
        {
            IrisZuordnung.zuordnen();
        }
    }

    private RenderPipelinesAlt()
    {}

    /** Eigene Klasse, damit IrisApi nur geladen wird, wenn Iris da ist. */
    private static final class IrisZuordnung
    {
        static void zuordnen()
        {
            net.irisshaders.iris.api.v0.IrisApi.getInstance().assignPipeline(ENTITY_TRANSLUCENT_CULL,
                net.irisshaders.iris.api.v0.IrisProgram.ENTITIES_TRANSLUCENT);
        }
    }
}
