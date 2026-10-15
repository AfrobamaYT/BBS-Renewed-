package mchorse.bbs_mod.compat.alt;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

/** Das leere Eckenformat; vor 1.21.5 ersetzt eine gleichnamige Datei es (DefaultVertexFormat.EMPTY fehlt dort). */
public final class FormatLeer
{
    public static final VertexFormat EMPTY = DefaultVertexFormat.EMPTY;

    private FormatLeer()
    {}
}
