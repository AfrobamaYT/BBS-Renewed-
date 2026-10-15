// Coded by Afrobama/Claude — Blender<->BBS live-sync bridge (Phase 2 world export).
package com.blenderbridge.world;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * ObjVertexConsumer — a {@link VertexConsumer} that captures the quads vanilla's FLUID renderer
 * emits, so {@link WorldExporter} can write water and lava to the OBJ.
 *
 * <p>WHY THIS EXISTS: fluids have NO baked model. {@code FluidBlock.getRenderType()} is
 * {@code INVISIBLE} (verified in the 1.21.1 bytecode) because the game draws them procedurally in
 * {@code FluidRenderer}, which computes the surface height from the neighbouring fluid levels and
 * picks the still/flow sprite per face. The only way to get that geometry without re-deriving the
 * fluid maths by hand (what the external tools — jmc2obj, Mineways — are forced to do, because they
 * have no running client) is to CALL the renderer and catch what it hands to its vertex consumer.
 * We have a running client, so we ask the game.</p>
 *
 * <h2>The 1.21.1 VertexConsumer contract (this is the trap)</h2>
 * <p>Up to 1.20.4 a vertex was finished with an explicit {@code next()}/{@code endVertex()} call.
 * In 1.21.1 THAT METHOD IS GONE — verified with {@code javap} against the yarn 1.21.1+build.3 jar,
 * the interface is exactly six abstract methods:</p>
 * <pre>
 *   VertexConsumer vertex(float, float, float)
 *   VertexConsumer color(int, int, int, int)
 *   VertexConsumer texture(float, float)
 *   VertexConsumer overlay(int, int)
 *   VertexConsumer light(int, int)
 *   VertexConsumer normal(float, float, float)
 * </pre>
 * <p>A vertex is finished IMPLICITLY by the next {@code vertex()} call, so there is no callback that
 * says "this quad is done". {@code FluidRenderer} feeds us, per vertex and in this exact order
 * (read out of its private {@code vertex(VertexConsumer, float x9, int)} helper's bytecode):
 * {@code vertex} &rarr; {@code color} &rarr; {@code texture} &rarr; {@code light} &rarr;
 * {@code normal} — always four vertices per quad.</p>
 *
 * <p>So we flush on the FIFTH {@code vertex()} call (i.e. when a new vertex arrives while four are
 * already buffered), NOT on the fourth. Flushing on the fourth would emit that vertex before its
 * own {@code texture()} call had arrived and every quad would carry one stale UV. The last quad has
 * no fifth vertex to trigger it, so {@link #finish()} flushes the tail — it MUST be called after
 * {@code renderFluid} returns.</p>
 *
 * <h2>What we deliberately drop</h2>
 * <ul>
 * <li>{@code color()} — NO-OP, and this is a decision, not an oversight. It looks like the free
 * source of the biome water tint, but {@code FluidRenderer} calls
 * {@code BlockRenderView.getBrightness(Direction, boolean)} four times and multiplies that
 * directional shade into r/g/b BEFORE every vertex (bytecode-verified), so the colour that reaches
 * us is {@code tint x shade} — top faces bright, bottom faces half-dark. Baking that into the
 * material would double-shade the water once Blender lights the scene, and would split one material
 * per shade level. {@link WorldExporter} takes the PURE tint from
 * {@code FluidRenderHandler.getFluidColor} instead, which is the very value the renderer shaded.</li>
 * <li>{@code normal()} — NO-OP: {@code FluidRenderer}'s helper hardcodes {@code (0, 1, 0)} for
 * EVERY vertex including the side faces, so it carries no information. {@link WorldExporter}
 * derives the real normal from the quad winding (cross product), as it already does for baked
 * quads.</li>
 * <li>{@code light()} / {@code overlay()} — NO-OP: lightmap/overlay coordinates are a real-time
 * shading input, and the exporter deliberately ships an unlit scene for Blender to relight.</li>
 * </ul>
 *
 * <p>THREADING / LIFETIME: single-use and single-threaded. Build one per fluid block, pass it to
 * {@code renderFluid}, call {@link #finish()}, read the quads, drop it.</p>
 */
final class ObjVertexConsumer extends mchorse.bbs_mod.compat.vertex.NeuerVertexVerbraucher
{
    /** Vertices per quad — {@code FluidRenderer} only ever emits quads. */
    private static final int QUAD_VERTICES = 4;

    /**
     * Slack when testing whether a UV falls inside a sprite's atlas rect. The still-fluid path uses
     * {@code Sprite.getMinU()}/{@code getMaxU()} verbatim, so its UVs sit exactly ON the boundary
     * and float rounding can drop them a hair outside. A sprite is ~0.015 of the atlas wide, so this
     * is far too small to reach a neighbouring sprite and cause a wrong match.
     */
    private static final float UV_EPSILON = 1.0E-5F;

    /**
     * The sprites this fluid may have used, from
     * {@code FluidRenderHandler.getFluidSprites(view, pos, state)} — {still, flow} or
     * {still, flow, overlay}. Which one a given quad actually used is only knowable from its UVs,
     * hence {@link #resolveSprite()}.
     */
    private final TextureAtlasSprite[] candidates;

    /*
     * The chunk-section origin, added back onto every captured position — see the class doc of
     * WorldExporter's emitFluid. FluidRenderer emits SECTION-LOCAL coordinates.
     */
    private final float originX;
    private final float originY;
    private final float originZ;

    /* The 4-vertex ring: filled by vertex()/texture(), drained by flush(). */
    private final float[] xs = new float[QUAD_VERTICES];
    private final float[] ys = new float[QUAD_VERTICES];
    private final float[] zs = new float[QUAD_VERTICES];
    private final float[] us = new float[QUAD_VERTICES];
    private final float[] vs = new float[QUAD_VERTICES];

    private final List<FluidQuad> quads = new ArrayList<FluidQuad>();

    /** How many vertices of the current quad are buffered (0..4). */
    private int count;

    /** Quads thrown away because no candidate sprite owned their UVs. Diagnostics only. */
    private int dropped;

    /**
     * @param candidates the fluid's possible sprites; must be non-empty or every quad is dropped
     * @param originX    chunk-section origin X ({@code pos.getX() & ~15})
     * @param originY    chunk-section origin Y ({@code pos.getY() & ~15})
     * @param originZ    chunk-section origin Z ({@code pos.getZ() & ~15})
     */
    ObjVertexConsumer(TextureAtlasSprite[] candidates, int originX, int originY, int originZ)
    {
        this.candidates = candidates;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
    }

    /**
     * Start a vertex — and, because 1.21.1 has no {@code next()}, this is also what finishes the
     * PREVIOUS one. When four are already buffered the quad is complete, so flush it first.
     */
    @Override
    public VertexConsumer addVertex(float x, float y, float z)
    {
        if (this.count == QUAD_VERTICES)
        {
            this.flush();
        }

        /* Section-local -> ABSOLUTE MC world space, matching what the baked-model path writes. */
        this.xs[this.count] = x + this.originX;
        this.ys[this.count] = y + this.originY;
        this.zs[this.count] = z + this.originZ;

        /* Reset the UV: if texture() never arrives for this vertex we want a defined 0, not the
         * previous quad's leftover. */
        this.us[this.count] = 0F;
        this.vs[this.count] = 0F;

        this.count++;

        return this;
    }

    /** ATLAS UVs for the vertex opened by the last {@link #addVertex} call. */
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

    /** NO-OP — carries {@code tint x directional shade}, not the tint. See the class doc. */
    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alpha)
    {
        return this;
    }

    /**
     * NO-OP — same reason as the four-argument {@code setColor}: what arrives here is
     * {@code tint x directional shade}, not the tint. See the class doc.
     *
     * <p>⚠️ 26.2: {@code setColor(int)} war frueher eine Vorgabe-Methode der Schnittstelle und
     * musste hier nicht stehen; jetzt ist sie abstrakt, also muss sie implementiert werden.</p>
     */
    @Override
    public VertexConsumer setColor(int argb)
    {
        return this;
    }

    /**
     * NO-OP — betrifft nur Linien-Zeichenwege.
     *
     * <p>⚠️ 26.2: {@code setLineWidth(float)} ist NEU und abstrakt in VertexConsumer (die
     * Linienstaerke reist jetzt mit dem Vertex-Strom statt im globalen GL-Zustand). Der
     * Fluessigkeits-Renderer, der uns fuettert, schickt ausschliesslich Vierecke, hier kommt
     * also nichts an — und selbst wenn: eine Linienstaerke hat im OBJ keine Entsprechung.</p>
     */
    @Override
    public VertexConsumer setLineWidth(float width)
    {
        return this;
    }

    /** NO-OP — real-time shading input; the export is unlit. */
    @Override
    public VertexConsumer setUv1(int u, int v)
    {
        return this;
    }

    /** NO-OP — real-time shading input; the export is unlit. */
    @Override
    public VertexConsumer setUv2(int u, int v)
    {
        return this;
    }

    /** NO-OP — {@code FluidRenderer} hardcodes (0,1,0) here; the real normal comes from the winding. */
    @Override
    public VertexConsumer setNormal(float x, float y, float z)
    {
        return this;
    }

    //? if >=26.3 {
    /*@Override
    *///?}
    public VertexConsumer setUv3(float u, float v)
    {
        /* OBJ exports the base material UVs captured in setUv; UV3 is the separate
         * vanilla effect/glint coordinate and has no channel in this export format. */
        return this;
    }

    /**
     * Flush the trailing quad and hand over everything captured. MUST be called after
     * {@code renderFluid} returns — the last quad has no following {@code vertex()} to flush it.
     *
     * @return the captured quads, in emission order; empty when the fluid drew nothing (fully
     *         enclosed by neighbours, which is the common case deep inside an ocean)
     */
    List<FluidQuad> finish()
    {
        if (this.count == QUAD_VERTICES)
        {
            this.flush();
        }
        else if (this.count != 0)
        {
            /* Shouldn't happen -> a partial quad means FluidRenderer emitted a non-quad primitive
             * and our 4-vertex assumption is wrong. Drop it rather than write a broken face. */
            this.dropped++;
            this.count = 0;
        }

        return this.quads;
    }

    /** Quads discarded for want of a matching sprite or a complete vertex set. */
    int getDropped()
    {
        return this.dropped;
    }

    /** Turn the four buffered vertices into a quad and reset the ring. */
    private void flush()
    {
        TextureAtlasSprite sprite = this.resolveSprite();

        this.count = 0;

        if (sprite == null)
        {
            /* No candidate owns these UVs, so we cannot say which PNG to point the material at.
             * Dropping beats guessing a texture and silently exporting the wrong one. */
            this.dropped++;
            return;
        }

        this.quads.add(new FluidQuad(this.xs, this.ys, this.zs, this.us, this.vs, sprite));
    }

    /**
     * Work out WHICH sprite the buffered quad used, by asking which candidate's atlas rectangle
     * contains its UVs.
     *
     * <p>This is the only way to know: {@code FluidRenderer} chooses still vs flow (vs overlay) per
     * face from the flow direction and the neighbouring block, and tells us nothing but the
     * resulting atlas coordinates. Sprites occupy disjoint rectangles of the atlas, so at most one
     * candidate can contain all four UVs and the answer is unambiguous.</p>
     *
     * @return the owning sprite, or null when none matches
     */
    private TextureAtlasSprite resolveSprite()
    {
        for (int i = 0; i < this.candidates.length; i++)
        {
            TextureAtlasSprite sprite = this.candidates[i];

            if (sprite != null && this.contains(sprite))
            {
                return sprite;
            }
        }

        return null;
    }

    /** True when all four buffered UVs lie inside {@code sprite}'s atlas rectangle. */
    private boolean contains(TextureAtlasSprite sprite)
    {
        float minU = sprite.getU0() - UV_EPSILON;
        float maxU = sprite.getU1() + UV_EPSILON;
        float minV = sprite.getV0() - UV_EPSILON;
        float maxV = sprite.getV1() + UV_EPSILON;

        for (int i = 0; i < QUAD_VERTICES; i++)
        {
            if (this.us[i] < minU || this.us[i] > maxU || this.vs[i] < minV || this.vs[i] > maxV)
            {
                return false;
            }
        }

        return true;
    }

    /**
     * One captured fluid quad: four ABSOLUTE world-space positions, four ATLAS UVs and the sprite
     * those UVs belong to. Copies the arrays out of the consumer's reused ring buffer, so it stays
     * valid after the next quad arrives.
     */
    static final class FluidQuad
    {
        final float[] xs;
        final float[] ys;
        final float[] zs;
        final float[] us;
        final float[] vs;
        final TextureAtlasSprite sprite;

        private FluidQuad(float[] xs, float[] ys, float[] zs, float[] us, float[] vs, TextureAtlasSprite sprite)
        {
            this.xs = xs.clone();
            this.ys = ys.clone();
            this.zs = zs.clone();
            this.us = us.clone();
            this.vs = vs.clone();
            this.sprite = sprite;
        }
    }
}
