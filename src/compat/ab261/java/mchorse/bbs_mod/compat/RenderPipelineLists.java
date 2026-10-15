package mchorse.bbs_mod.compat;

import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;

import java.util.Arrays;
import java.util.List;

/**
 * Minecraft 26.3 returns a pipeline's colour targets and vertex formats as lists, before that
 * as arrays. stonecutter.gradle points the 26.3 calls at these before 26.3. Arrays.asList keeps
 * null entries, which mark unused colour targets (MRT holes).
 */
public interface RenderPipelineLists
{
    default List<ColorTargetState> bbsColorTargetStates()
    {
        return Arrays.asList(((RenderPipeline) this).getColorTargetStates());
    }

    default List<VertexFormat> bbsVertexFormatBindings()
    {
        return Arrays.asList(((RenderPipeline) this).getVertexFormatBindings());
    }
}
