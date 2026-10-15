package com.blenderbridge.entity;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.blenderbridge.entity.EntityExporter.CapturedQuad;
import java.util.ArrayList;
import java.util.List;

/**
 * Captures the quads {@code Cuboid.renderCuboid} emits for ONE cuboid. Same
 * "flush on the 5th vertex() call" contract as {@code world.ObjVertexConsumer} (1.21.1 has no
 * explicit end-of-vertex call — see that class's doc for the full reasoning) — a cuboid face is
 * always a quad, so this consumer is built fresh per cuboid and drained via {@link #finish()}.
 * Unlike the fluid case, cuboid faces DO carry a real, meaningful per-vertex normal (flat box
 * faces), so — unlike {@code ObjVertexConsumer} — we capture it here instead of recomputing from
 * winding.
 */
final class EntityQuadCapture implements VertexConsumer
{
    private static final int QUAD_VERTICES = 4;
    private final List<CapturedQuad> quads = new ArrayList<>();
    private final float[] xs = new float[QUAD_VERTICES];
    private final float[] ys = new float[QUAD_VERTICES];
    private final float[] zs = new float[QUAD_VERTICES];
    private final float[] us = new float[QUAD_VERTICES];
    private final float[] vs = new float[QUAD_VERTICES];
    private float nx;
    private float ny;
    private float nz;
    private int count;

    @Override
    public VertexConsumer addVertex(float x, float y, float z)
    {
        if (this.count == QUAD_VERTICES)
        {
            this.flush();
        }

        this.xs[this.count] = x;
        this.ys[this.count] = y;
        this.zs[this.count] = z;
        this.us[this.count] = 0F;
        this.vs[this.count] = 0F;
        this.count++;

        return this;
    }

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alpha)
    {
        /* NO-OP — base-texture export (task 4.1 v1 scope) carries no per-vertex tint. */
        return this;
    }

    @Override
    public VertexConsumer setColor(int argb)
    {
        /* PORT 26.2: neue pflicht-methode des interfaces — gepacktes ARGB statt vier bytes.
         * Gleiche antwort wie oben: der basis-textur-export trägt keinen vertex-tint. */
        return this;
    }

    @Override
    public VertexConsumer setLineWidth(float width)
    {
        /* PORT 26.2: neue pflicht-methode. Nur für LINES-layer sinnvoll; cuboid-flächen sind
         * quads, hier gibt es keine linien-breite. */
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v)
    {
        if (this.count > 0)
        {
            this.us[this.count - 1] = u;
            this.vs[this.count - 1] = v;
        }

        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v)
    {
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v)
    {
        return this;
    }

    //? if >=26.3 {
    @Override
    //?}
    public VertexConsumer setUv3(float u, float v)
    {
        /* The base-texture OBJ export records setUv, excluding vanilla effect UVs
         * together with the existing overlay/lightmap channels. */
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z)
    {
        /* Cuboid faces are flat — every vertex of a face reports the same normal, so simply
         * keeping the latest call's value is enough; used at flush() time. */
        this.nx = x;
        this.ny = y;
        this.nz = z;

        return this;
    }

    /** Flush the trailing quad (a cuboid always ends exactly on a 4th vertex) and hand over the list. */
    List<CapturedQuad> finish()
    {
        if (this.count == QUAD_VERTICES)
        {
            this.flush();
        }

        return this.quads;
    }

    private void flush()
    {
        this.quads.add(new CapturedQuad(this.xs, this.ys, this.zs, this.us, this.vs, this.nx, this.ny, this.nz));
        this.count = 0;
    }
}
