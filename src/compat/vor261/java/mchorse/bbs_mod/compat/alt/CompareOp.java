package mchorse.bbs_mod.compat.alt;

import com.mojang.blaze3d.platform.DepthTestFunction;

/**
 * 26.2's depth comparison, for the OpenGL versions before 26.1. 26.2 computes depth reversed
 * (nearer = larger, cleared to 0), these versions the GL way (nearer = smaller, cleared to 1),
 * so {@link #alt()} mirrors the comparison: GREATER_THAN_OR_EQUAL is the old LEQUAL.
 */
public enum CompareOp
{
    ALWAYS_PASS, LESS_THAN, LESS_THAN_OR_EQUAL, EQUAL, NOT_EQUAL, GREATER_THAN_OR_EQUAL, GREATER_THAN, NEVER_PASS;

    public DepthTestFunction alt()
    {
        return switch (this)
        {
            case ALWAYS_PASS -> DepthTestFunction.NO_DEPTH_TEST;
            case GREATER_THAN_OR_EQUAL -> DepthTestFunction.LEQUAL_DEPTH_TEST;
            case GREATER_THAN -> DepthTestFunction.LESS_DEPTH_TEST;
            case LESS_THAN -> DepthTestFunction.GREATER_DEPTH_TEST;
            case EQUAL -> DepthTestFunction.EQUAL_DEPTH_TEST;
            /* Mojang's DepthTestFunction before 26.1 has no GEQUAL, NOTEQUAL or NEVER. */
            default -> throw new UnsupportedOperationException("Depth test " + this + " has no counterpart before 26.1");
        };
    }

    public static CompareOp von(DepthTestFunction function)
    {
        return switch (function)
        {
            case NO_DEPTH_TEST -> ALWAYS_PASS;
            case LEQUAL_DEPTH_TEST -> GREATER_THAN_OR_EQUAL;
            case LESS_DEPTH_TEST -> GREATER_THAN;
            case GREATER_DEPTH_TEST -> LESS_THAN;
            case EQUAL_DEPTH_TEST -> EQUAL;
        };
    }
}
