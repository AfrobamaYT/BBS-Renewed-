package mchorse.bbs_mod.compat.alt;

/**
 * 26.2's depth state, for the OpenGL versions before 26.1. Its comparison and bias are in
 * 26.2's reversed depth; {@link mchorse.bbs_mod.compat.RenderPipelineAlt} converts them when
 * it sets them on Mojang's builder.
 */
public record DepthStencilState(CompareOp depthTest, boolean writeDepth, float depthBiasScaleFactor, float depthBiasConstant)
{
    public static final DepthStencilState DEFAULT = new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true);

    public DepthStencilState(CompareOp depthTest, boolean writeDepth)
    {
        this(depthTest, writeDepth, 0F, 0F);
    }
}
