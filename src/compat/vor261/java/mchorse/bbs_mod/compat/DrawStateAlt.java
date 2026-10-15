package mchorse.bbs_mod.compat;

import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;

/** 26.2 names MeshData.DrawState's mode primitiveTopology(). Injected into MeshData.DrawState. */
public interface DrawStateAlt
{
    default VertexFormat.Mode primitiveTopology()
    {
        return ((MeshData.DrawState) (Object) this).mode();
    }
}
