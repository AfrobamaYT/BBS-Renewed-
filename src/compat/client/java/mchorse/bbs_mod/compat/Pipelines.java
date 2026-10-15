package mchorse.bbs_mod.compat;

import com.mojang.blaze3d.pipeline.RenderPipeline;

/**
 * Static calls Minecraft 26.3 added, for older versions. stonecutter.gradle points the
 * 26.3 call at these before 26.3; an interface cannot be injected for static methods.
 */
public class Pipelines
{
    /**
     * 26.3 compiles a pipeline before a pass accepts it; before that the pass takes the
     * pipeline itself.
     */
    public static RenderPipeline compiled(RenderPipeline pipeline)
    {
        return pipeline;
    }

    /**
     * 26.3 asks the device whether it can draw wireframes; OpenGL before that always could.
     */
    public static boolean isWireframeAvailable()
    {
        return true;
    }
}
