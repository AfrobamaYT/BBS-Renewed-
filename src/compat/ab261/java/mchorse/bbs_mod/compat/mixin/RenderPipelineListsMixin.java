package mchorse.bbs_mod.compat.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import mchorse.bbs_mod.compat.RenderPipelineLists;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(RenderPipeline.class)
public abstract class RenderPipelineListsMixin implements RenderPipelineLists
{}
