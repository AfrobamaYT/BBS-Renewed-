package mchorse.bbs_mod.compat.vertex;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;

/** vor1205: 1.21s BufferBuilder#build/buildOrThrow (MeshData) auf end/endOrDiscardIfEmpty. */
public interface BufferBuilderAlt
{
    default MeshData build()
    {
        BufferBuilder.RenderedBuffer fertig = ((BufferBuilder) this).endOrDiscardIfEmpty();

        return fertig == null ? null : new MeshData(fertig);
    }

    default MeshData buildOrThrow()
    {
        MeshData netz = this.build();

        if (netz == null)
        {
            throw new IllegalStateException("BufferBuilder was empty");
        }

        return netz;
    }
}
