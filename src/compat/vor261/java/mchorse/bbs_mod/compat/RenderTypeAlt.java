package mchorse.bbs_mod.compat;

import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.rendertype.RenderType;

/** 26.2 names RenderType's mode primitiveTopology(). Injected into RenderType. */
public interface RenderTypeAlt
{
    default VertexFormat.Mode primitiveTopology()
    {
        return ((RenderType) this).mode();
    }
}
