package mchorse.bbs_mod.compat.alt;

import com.mojang.blaze3d.vertex.VertexFormat;

/** Das leere Eckenformat vor 1.21.5, gebaut wie 1.21.5s DefaultVertexFormat.EMPTY. */
public final class FormatLeer
{
    public static final VertexFormat EMPTY = MojangFormat.LEER;

    private FormatLeer()
    {}
}
