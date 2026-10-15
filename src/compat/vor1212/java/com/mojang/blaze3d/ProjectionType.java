package com.mojang.blaze3d;

import com.mojang.blaze3d.vertex.VertexSorting;

/** BBS-Shim fuer Minecraft vor 1.21.2: 1.21.4s Projektionsart; 1.21.1 kennt nur die Sortierung. */
public enum ProjectionType
{
    PERSPECTIVE(VertexSorting.DISTANCE_TO_ORIGIN), ORTHOGRAPHIC(VertexSorting.ORTHOGRAPHIC_Z);

    private final VertexSorting sortierung;

    ProjectionType(VertexSorting sortierung)
    {
        this.sortierung = sortierung;
    }

    /** Rueckweg fuer RenderSystem.getVertexSorting() von 1.21.1 (dort gibt es nur die Sortierung). */
    public static ProjectionType von(VertexSorting sortierung)
    {
        return sortierung == VertexSorting.ORTHOGRAPHIC_Z ? ORTHOGRAPHIC : PERSPECTIVE;
    }

    public VertexSorting vertexSorting()
    {
        return this.sortierung;
    }
}
