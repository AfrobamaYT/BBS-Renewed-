// Coded by Afrobama/Claude — Blender<->BBS live-sync bridge (Task 3.4 per coordinator; plan-v2's
// own doc lists this as the second half of Task 3.3 "Face-merge + hollow-shell-entscheid" — the
// cave-culling half landed 2026-07-17 as SkyReachability, this is the face-merge half).
package com.blenderbridge.world;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.resources.Identifier;

/**
 * FaceMerger — Task 3.4 (research R1: Mineways merges before writing, "142.428&rarr;15.726
 * dreiecke, -89%"; Bret-B {@code MeshOptimizer.java} edge-adjacency merge as a second reference
 * pattern): takes one column's already-meshed, not-yet-written {@link
 * StreamingWorldExporter.PendingQuad}-equivalent quads (see {@link Quad}, this class's own copy of
 * that shape — see WHY NOT REUSE below) and greedily combines coplanar, same-material neighbours
 * into maximal rectangles, exactly like a voxel-engine "greedy meshing" pass over one column's
 * worth of unit block faces. {@link StreamingWorldExporter#meshColumn} calls {@link #merge} once,
 * per column, right before handing quads off to the (existing, unmodified) write step, gated by
 * {@code fields.mergeFaces} (this class's {@link #armNextJob}/{@link #isArmed}).
 *
 * <h2>WHY A NEW, SELF-CONTAINED CLASS (SkyReachability's "DEPLOY-WINDOW-FREE WIRING" pattern)</h2>
 * <p>Same deploy model as Task 3.3: this session's JVM already has {@code StreamingWorldExporter}
 * loaded, body-only hotswap is always safe, but a NEW field/method/class-visibility-change on an
 * already-loaded class risks needing a restart (forbidden short of a real crash — project hard
 * rule). Every bit of Task 3.4 state therefore lives in this brand-new class instead of touching
 * {@code StreamingWorldExporter}'s shape. Unlike {@link SkyReachability}, this class needs NO
 * per-worker-thread {@code ThreadLocal} and no lazy-holder dance at all: {@link #merge} is a pure,
 * stateless transform (input list in, output list out, zero fields touched during the call except
 * one read of the arm flag) — see THREADING below.</p>
 *
 * <h2>WHY NOT REUSE {@code StreamingWorldExporter.PendingQuad} DIRECTLY</h2>
 * <p>That nested class is declared {@code private static final class PendingQuad} — private to
 * its OUTER class, not just package-private, so code in this file cannot even name that type,
 * let alone construct one. Changing that class's own access modifier would itself be a shape
 * change to an already-loaded class (exactly the kind of edit this deploy model avoids). Instead,
 * this class declares its own value-object {@link Quad} with the IDENTICAL field shape, and {@code
 * StreamingWorldExporter.meshColumn}'s body (which, being code INSIDE that class, can already see
 * {@code PendingQuad} freely) does a small copy-in/copy-out conversion immediately around the
 * {@link #merge} call — a body-only change, no signature anywhere is touched.</p>
 *
 * <h2>ALGORITHM ("2D sweep per plane+material", mineways-style greedy meshing)</h2>
 * <ol>
 * <li><b>Classify</b> ({@link #classify}) each quad: is its normal EXACTLY axis-aligned (one
 * component &plusmn;1, the other two &asymp;0)? Is it a UNIT (1&times;1) square in the plane
 * perpendicular to that normal? Is its UV exactly a single full [0,1]&times;[0,1] tile (not some
 * sub-rectangle of a shared/atlas UV)? If ANY of these fail, the quad is never merged — see KNOWN
 * LIMITS.</li>
 * <li><b>Layer key</b> ({@link #layerKey}): texture identity + tint state + emissive + normal
 * direction (6-way, not just axis) + the plane's fixed world coordinate along that normal. Two
 * quads only ever compete to merge if they are coplanar AND texture-identical AND
 * direction-identical — deliberately IGNORES the underlying block type, so e.g. two different
 * block types that happen to render the same top texture at the same height DO merge (matching
 * what Mineways/any texture-based merger does: the OBJ has no concept of "block type", only faces
 * and materials).</li>
 * <li><b>UV-to-world mapping</b> ({@link #resolveMapping}): derived PURELY from the observed
 * geometry of each layer's first ("template") quad — which in-plane axis U tracks, which V
 * tracks, and whether each is inverted — rather than hard-coding an assumption about vanilla's
 * vertex winding order. This makes the merge self-adapting to whatever winding a given block's
 * baked model actually uses instead of risking a silently-wrong guess.</li>
 * <li><b>Greedy 2D sweep</b> ({@link Layer#emitMerged}): standard greedy-meshing — scan occupied
 * cells in a fixed deterministic order, grow a rectangle's width while the next cell along axis1
 * is occupied+unvisited, then grow its height while the ENTIRE current width-row along axis2 is
 * occupied+unvisited, mark the whole rectangle visited, emit one merged quad for it. Every
 * occupied cell ends up in EXACTLY one output rectangle — this is what makes the merge lossless
 * (see VERLUSTFREIHEIT below): nothing is discarded, ever.</li>
 * <li><b>UV-repeat, not UV-stretch</b> ({@link Layer#buildMergedQuad}): a merged rectangle spanning
 * {@code width}&times;{@code height} unit cells gets UV coordinates scaled by that same
 * {@code width}/{@code height} (e.g. U in [0,3] instead of [0,1] for a 3-wide merge) rather than
 * being renormalised back to [0,1] — i.e. TEXTURE REPEAT, not a stretched single copy of the
 * texture across the whole rectangle. This depends on the destination texture sampler actually
 * tiling U/V &gt; 1 (see UV-REPEAT DEPENDENCY below).</li>
 * </ol>
 *
 * <h2>UV-REPEAT DEPENDENCY (measured, not assumed)</h2>
 * <p>{@code world_import.py} sets each MC material's Image Texture node's {@code interpolation =
 * 'Closest'} (the pixelated MC look) but never touches {@code extension} — Blender's Image Texture
 * node default is {@code extension = 'REPEAT'}, which is exactly what a UV &gt; 1.0 needs to tile
 * instead of clamping/stretching. If a future addon change ever sets {@code extension = 'CLIP'}/
 * {@code 'EXTEND'} for some unrelated reason, merged faces would visibly break (stretch or clamp)
 * even though this class's own OBJ/UV math stays correct — that dependency is exactly why proof
 * (d) (the Blender-import silhouette/no-stretch check) is part of this task's gate, not a
 * Java-side-only gate.</p>
 *
 * <h2>KNOWN LIMITS (documented, not hidden)</h2>
 * <ul>
 * <li><b>Per-column scope only, v1 (deliberate, matching the task instruction).</b> {@link #merge}
 * is called once per column's own {@code pending} list; it NEVER sees another column's quads, so a
 * flat plain that crosses a 16-wide column boundary still emits one merged rectangle PER COLUMN
 * rather than one giant rectangle spanning the whole exported region. This keeps the pass local,
 * deterministic per-column (matching {@link StreamingWorldExporter}'s existing per-column-worker
 * parallelism — no cross-column shared state, no new synchronisation needed), at the cost of a
 * smaller reduction than a whole-region merge would achieve. A future v2 could merge across column
 * boundaries; explicitly out of scope here.</li>
 * <li><b>Only axis-aligned, unit-size, full-single-tile-UV quads ever merge.</b> Slabs/stairs/
 * fences/carpets/cross-models (grass, flowers) and any quad whose UV occupies less than a full
 * [0,1]&times;[0,1] tile (e.g. a connected-texture or multi-part sprite) fail {@link #classify} and
 * pass through completely UNCHANGED into the output — never lost, never altered, just never
 * combined with a neighbour. This is what keeps the merge safe by construction rather than needing
 * a large exception list: anything that doesn't cleanly fit the "one full block face" shape simply
 * opts itself out.</li>
 * <li><b>Fluids merge only when genuinely flat.</b> A still, level fluid surface (all 4 corners at
 * the same height, full [0,1] sprite UV) passes {@link #classify} like any other quad and CAN
 * merge with its neighbours; a flowing/sloped fluid surface (corners at different heights per
 * {@code ObjVertexConsumer}'s real corner-height sampling) fails the "planar" check inside {@link
 * #classify} and is left unmerged — correct by the same "doesn't fit the shape, opts out" logic,
 * no fluid-specific code needed.</li>
 * <li><b>Overlapping/duplicate cells never happen in practice</b> (two quads should never legally
 * occupy the exact same unit cell+direction+material), but {@link Layer#accept} treats a collision
 * as "reject the second one to passthrough" rather than silently overwriting — a defensive
 * safety net, not an expected code path.</li>
 * </ul>
 *
 * <h2>VERLUSTFREIHEIT (losslessness, by construction)</h2>
 * <p>Every input {@link Quad} ends up in the output EXACTLY once: either merged into a rectangle
 * (as part of the cell set {@link Layer#emitMerged} sweeps, which partitions the FULL occupied set
 * of its layer into disjoint rectangles — no cell is ever skipped or double-counted) or copied
 * verbatim into {@code passthrough} the moment {@link #classify} or {@link Layer#accept} rejects
 * it. Nothing is ever dropped. A 1&times;1 "merge" (an isolated quad with no mergeable neighbour)
 * short-circuits in {@link Layer#emitMerged} to reuse the ORIGINAL quad object unchanged (not a
 * geometrically-equivalent reconstruction) — this avoids introducing tiny floating-point UV drift
 * (e.g. snapping an atlas-UV's {@code 0.00003} to a clean {@code 0.0}) on quads that were never
 * actually combined with anything.</p>
 *
 * <h2>THREADING</h2>
 * <p>{@link #merge} touches ZERO shared mutable state beyond one read of {@link #armedMergeFaces}
 * (done by the CALLER via {@link #isArmed} before ever calling {@link #merge} — {@link #merge}
 * itself doesn't re-check the flag, keeping it a pure argument-in/result-out function, callable
 * from any {@code StreamingWorldExporter} worker thread concurrently with any other thread's call,
 * exactly like every other per-column worker computation in that class's design). {@link
 * #armedMergeFaces} itself is declared WITHOUT a field initializer (bytecode default {@code
 * false}), following the hardened rule from {@link SkyReachability}'s own class doc (the
 * {@code <clinit>}-doesn't-rerun-on-hotswap trap that crashed the game once already this session)
 * — this class is brand-new so the trap does not apply on its FIRST load, but keeping the same
 * discipline preemptively costs nothing and protects any LATER same-session hotswap iteration on
 * this class.</p>
 */
public final class FaceMerger
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Vertices per quad — matches {@code StreamingWorldExporter.QUAD_VERTICES}. */
    private static final int VERTICES = 4;

    /** Tolerance for "is this exactly axis-aligned / exactly planar / exactly a unit square". */
    private static final float EPS = 1.0E-3F;

    /** Slightly looser tolerance for UV corner-code classification (atlas UV has more float noise
     * than raw world-space block coordinates). */
    private static final float UV_EPS = 2.0E-2F;

    /** See class doc THREADING — deliberately no initializer. */
    private static volatile boolean armedMergeFaces;

    private FaceMerger()
    {
        /* static-only helper */
    }

    /**
     * Arm (or disarm) face merging for the NEXT streaming job that starts. Call once, from {@code
     * WorldExportBridge.export}'s body, before {@code StreamingWorldExporter.start} — same call
     * shape as {@link SkyReachability#armNextJob}.
     */
    public static void armNextJob(boolean mergeFaces)
    {
        armedMergeFaces = mergeFaces;
    }

    /** Whether the current/most recent job requested face merging — checked by {@code
     * StreamingWorldExporter.meshColumn}'s body before doing the {@link Quad} conversion + {@link
     * #merge} call at all (so the whole pass is a true no-op, byte-identical to pre-3.4 behaviour,
     * when merging is off). */
    public static boolean isArmed()
    {
        return armedMergeFaces;
    }

    /** Same value as {@link #isArmed} — separate accessor only so log/status call sites read
     * "lastArmed..." like {@link SkyReachability#lastArmedCullCaves}, for symmetry/searchability. */
    public static boolean lastArmedMergeFaces()
    {
        return armedMergeFaces;
    }

    /**
     * Merge one column's quads. Pure function: builds and returns a NEW list, never mutates
     * {@code input} or any of its elements. Safe to call from any worker thread (see class doc
     * THREADING).
     *
     * @param input this column's fully-meshed, not-yet-written quads (any order)
     * @return a list covering the exact same geometry, with coplanar/same-material/axis-aligned/
     *         unit-size neighbours combined into larger rectangles where possible
     */
    public static List<Quad> merge(List<Quad> input)
    {
        if (input.isEmpty())
        {
            return input;
        }

        Map<String, Layer> layers = new LinkedHashMap<>();
        List<Quad> out = new ArrayList<>();
        int mergeable = 0;

        for (int i = 0; i < input.size(); i++)
        {
            Quad q = input.get(i);
            Shape shape = classify(q);

            if (shape == null)
            {
                out.add(q);
                continue;
            }

            String key = layerKey(q, shape);
            Layer layer = layers.get(key);

            if (layer == null)
            {
                layer = new Layer(shape);
                layers.put(key, layer);
            }

            if (layer.accept(q, shape))
            {
                mergeable++;
            }
            else
            {
                out.add(q);
            }
        }

        for (Layer layer : layers.values())
        {
            layer.emitMerged(out);
        }

        LOGGER.debug("[blenderbridge] FaceMerger: {} in -> {} out ({} mergeable-eligible, {} layers)", input.size(), out.size(), mergeable, layers.size());

        return out;
    }

    /**
     * Classify one quad: is it eligible to ever merge with a neighbour at all? Returns {@code null}
     * (never merged, always passthrough) unless the quad is EXACTLY axis-aligned, planar, a unit
     * (1&times;1) square in its own plane, and carries a full single-tile [0,1]&times;[0,1] UV — see
     * class doc KNOWN LIMITS for what this excludes and why that's safe.
     */
    private static Shape classify(Quad q)
    {
        int axis;
        int sign;

        if (Math.abs(q.nx) > 1F - EPS && Math.abs(q.ny) < EPS && Math.abs(q.nz) < EPS)
        {
            axis = 0;
            sign = q.nx > 0 ? 1 : -1;
        }
        else if (Math.abs(q.ny) > 1F - EPS && Math.abs(q.nx) < EPS && Math.abs(q.nz) < EPS)
        {
            axis = 1;
            sign = q.ny > 0 ? 1 : -1;
        }
        else if (Math.abs(q.nz) > 1F - EPS && Math.abs(q.nx) < EPS && Math.abs(q.ny) < EPS)
        {
            axis = 2;
            sign = q.nz > 0 ? 1 : -1;
        }
        else
        {
            return null;
        }

        int axis1 = axis == 0 ? 1 : 0;
        int axis2 = axis == 2 ? 1 : 2;

        float[] a1 = axisArray(q, axis1);
        float[] a2 = axisArray(q, axis2);
        float[] normalComp = axisArray(q, axis);

        float plane = normalComp[0];

        for (int i = 1; i < VERTICES; i++)
        {
            if (Math.abs(normalComp[i] - plane) > EPS)
            {
                return null;
            }
        }

        float min1 = min(a1);
        float max1 = max(a1);
        float min2 = min(a2);
        float max2 = max(a2);

        if (Math.abs((max1 - min1) - 1F) > EPS || Math.abs((max2 - min2) - 1F) > EPS)
        {
            return null;
        }

        float minU = min(q.u);
        float maxU = max(q.u);
        float minV = min(q.v);
        float maxV = max(q.v);

        if (Math.abs((maxU - minU) - 1F) > UV_EPS || Math.abs((maxV - minV) - 1F) > UV_EPS)
        {
            return null;
        }

        int[] c1 = new int[VERTICES];
        int[] c2 = new int[VERTICES];
        int[] cu = new int[VERTICES];
        int[] cv = new int[VERTICES];

        for (int i = 0; i < VERTICES; i++)
        {
            c1[i] = Math.abs(a1[i] - min1) < EPS ? 0 : 1;
            c2[i] = Math.abs(a2[i] - min2) < EPS ? 0 : 1;
            cu[i] = Math.abs(q.u[i] - minU) < UV_EPS ? 0 : 1;
            cv[i] = Math.abs(q.v[i] - minV) < UV_EPS ? 0 : 1;
        }

        UvMapping mapping = resolveMapping(c1, c2, cu, cv);

        if (mapping == null)
        {
            return null;
        }

        Shape shape = new Shape();
        shape.axis = axis;
        shape.sign = sign;
        shape.axis1 = axis1;
        shape.axis2 = axis2;
        shape.plane = plane;
        shape.cellA = Math.round(min1);
        shape.cellB = Math.round(min2);
        shape.c1 = c1;
        shape.c2 = c2;
        shape.mapping = mapping;

        return shape;
    }

    /**
     * Work out which in-plane axis (1 or 2) U tracks and which V tracks, and whether each is
     * inverted, PURELY from this quad's own observed corner pattern — see class doc ALGORITHM
     * point 3 for why this is derived rather than assumed. Returns {@code null} if U and V don't
     * cleanly resolve to the two DIFFERENT in-plane axes (an atypical/rotated mapping) — such a
     * quad is never merged (passthrough).
     */
    private static UvMapping resolveMapping(int[] c1, int[] c2, int[] cu, int[] cv)
    {
        int uAxis;
        boolean uInverted;

        if (arraysEqual(cu, c1))
        {
            uAxis = 1;
            uInverted = false;
        }
        else if (arraysEqual(cu, invert(c1)))
        {
            uAxis = 1;
            uInverted = true;
        }
        else if (arraysEqual(cu, c2))
        {
            uAxis = 2;
            uInverted = false;
        }
        else if (arraysEqual(cu, invert(c2)))
        {
            uAxis = 2;
            uInverted = true;
        }
        else
        {
            return null;
        }

        int vAxis;
        boolean vInverted;

        if (arraysEqual(cv, c1))
        {
            vAxis = 1;
            vInverted = false;
        }
        else if (arraysEqual(cv, invert(c1)))
        {
            vAxis = 1;
            vInverted = true;
        }
        else if (arraysEqual(cv, c2))
        {
            vAxis = 2;
            vInverted = false;
        }
        else if (arraysEqual(cv, invert(c2)))
        {
            vAxis = 2;
            vInverted = true;
        }
        else
        {
            return null;
        }

        if (uAxis == vAxis)
        {
            /* Degenerate: U and V would both be constant along the axis they DIDN'T resolve to,
             * meaning this quad's UV doesn't actually vary across one whole in-plane dimension —
             * not a shape this merger understands, leave it alone. */
            return null;
        }

        UvMapping mapping = new UvMapping();
        mapping.uAxis = uAxis;
        mapping.uInverted = uInverted;
        mapping.vAxis = vAxis;
        mapping.vInverted = vInverted;

        return mapping;
    }

    /**
     * The grouping key: two quads only ever compete to merge if every one of these matches exactly
     * — see class doc ALGORITHM point 2 for why block-type identity is deliberately absent.
     */
    private static String layerKey(Quad q, Shape shape)
    {
        return q.spriteId + "|" + q.tinted + "|" + q.tint + "|" + q.emissive + "|" + shape.axis + "|" + shape.sign + "|" + Math.round(shape.plane * 1000F);
    }

    private static long packCell(int cellA, int cellB)
    {
        return ((long) cellA << 32) | (cellB & 0xFFFFFFFFL);
    }

    private static float[] axisArray(Quad q, int axis)
    {
        return axis == 0 ? q.x : axis == 1 ? q.y : q.z;
    }

    private static void setAxis(Quad q, int axis, int i, float value)
    {
        if (axis == 0)
        {
            q.x[i] = value;
        }
        else if (axis == 1)
        {
            q.y[i] = value;
        }
        else
        {
            q.z[i] = value;
        }
    }

    private static float min(float[] values)
    {
        float m = values[0];

        for (int i = 1; i < values.length; i++)
        {
            m = Math.min(m, values[i]);
        }

        return m;
    }

    private static float max(float[] values)
    {
        float m = values[0];

        for (int i = 1; i < values.length; i++)
        {
            m = Math.max(m, values[i]);
        }

        return m;
    }

    private static boolean arraysEqual(int[] a, int[] b)
    {
        for (int i = 0; i < a.length; i++)
        {
            if (a[i] != b[i])
            {
                return false;
            }
        }

        return true;
    }

    private static int[] invert(int[] code)
    {
        int[] out = new int[code.length];

        for (int i = 0; i < code.length; i++)
        {
            out[i] = 1 - code[i];
        }

        return out;
    }

    /**
     * One column-local unit-quad's classification result — everything {@link Layer} needs to
     * decide whether a LATER quad is compatible, and to reconstruct a merged rectangle's geometry.
     */
    private static final class Shape
    {
        int axis;
        int sign;
        int axis1;
        int axis2;
        float plane;
        int cellA;
        int cellB;
        int[] c1;
        int[] c2;
        UvMapping mapping;
    }

    /** Which in-plane axis U/V track, and whether each is inverted — see {@link #resolveMapping}. */
    private static final class UvMapping
    {
        int uAxis;
        boolean uInverted;
        int vAxis;
        boolean vInverted;
    }

    /**
     * One (texture, tint, emissive, direction, plane) group. Collects unit cells into a sorted
     * grid ({@link #cells}, keyed by {@link #packCell}) as {@link #accept} is called for each
     * candidate quad, then {@link #emitMerged} sweeps the WHOLE grid exactly once at the end.
     */
    private static final class Layer
    {
        private final Shape template;
        private final Map<Long, Quad> cells = new TreeMap<>();

        Layer(Shape template)
        {
            this.template = template;
        }

        /**
         * True if {@code q} joined this layer's grid (same shape pattern as the template, cell not
         * already occupied). False means the caller must treat {@code q} as passthrough — this
         * layer's grid is unaffected either way (a rejected quad never corrupts an accepted one).
         */
        boolean accept(Quad q, Shape shape)
        {
            if (shape.axis != this.template.axis
                || shape.sign != this.template.sign
                || Math.abs(shape.plane - this.template.plane) > EPS
                || !arraysEqual(shape.c1, this.template.c1)
                || !arraysEqual(shape.c2, this.template.c2)
                || shape.mapping.uAxis != this.template.mapping.uAxis
                || shape.mapping.uInverted != this.template.mapping.uInverted
                || shape.mapping.vAxis != this.template.mapping.vAxis
                || shape.mapping.vInverted != this.template.mapping.vInverted)
            {
                return false;
            }

            long key = packCell(shape.cellA, shape.cellB);

            if (this.cells.containsKey(key))
            {
                /* Two quads legally claiming the exact same unit cell+direction+material should
                 * never happen — defensive safety net (class doc KNOWN LIMITS), not an expected
                 * path. Reject the SECOND one to passthrough rather than silently overwrite. */
                return false;
            }

            this.cells.put(key, q);

            return true;
        }

        /**
         * Greedy 2D sweep over every cell in this layer's grid (class doc ALGORITHM point 4),
         * appending one merged (or, for an isolated cell, the original untouched) {@link Quad} per
         * rectangle into {@code out}. Every key in {@link #cells} is visited by EXACTLY one
         * rectangle — this is the losslessness guarantee (class doc VERLUSTFREIHEIT).
         */
        void emitMerged(List<Quad> out)
        {
            Set<Long> visited = new HashSet<>();

            for (Long keyObj : this.cells.keySet())
            {
                long key = keyObj;

                if (visited.contains(key))
                {
                    continue;
                }

                int cellA = (int) (key >> 32);
                int cellB = (int) (long) key;

                int width = 1;

                while (this.cells.containsKey(packCell(cellA + width, cellB)) && !visited.contains(packCell(cellA + width, cellB)))
                {
                    width++;
                }

                int height = 1;

                growHeight:
                while (true)
                {
                    for (int a = 0; a < width; a++)
                    {
                        long k = packCell(cellA + a, cellB + height);

                        if (!this.cells.containsKey(k) || visited.contains(k))
                        {
                            break growHeight;
                        }
                    }

                    height++;
                }

                for (int a = 0; a < width; a++)
                {
                    for (int b = 0; b < height; b++)
                    {
                        visited.add(packCell(cellA + a, cellB + b));
                    }
                }

                if (width == 1 && height == 1)
                {
                    /* Isolated cell — reuse the ORIGINAL quad unchanged (class doc VERLUSTFREIHEIT:
                     * avoids introducing float drift on a quad that was never actually combined). */
                    out.add(this.cells.get(key));
                }
                else
                {
                    out.add(buildMergedQuad(this.template, this.cells.get(key), cellA, cellB, width, height));
                }
            }
        }

        /**
         * Reconstruct one merged rectangle's geometry + UV (repeat, not stretch — class doc
         * ALGORITHM point 5). Material fields (texture/tint/emissive/normal) are copied from
         * {@code representative} — guaranteed identical to every other cell in this layer by
         * construction (the {@link #accept} grouping already enforced it).
         */
        private static Quad buildMergedQuad(Shape template, Quad representative, int cellA, int cellB, int width, int height)
        {
            Quad out = new Quad();
            out.nx = representative.nx;
            out.ny = representative.ny;
            out.nz = representative.nz;
            out.spriteId = representative.spriteId;
            out.frameWidth = representative.frameWidth;
            out.frameHeight = representative.frameHeight;
            out.tinted = representative.tinted;
            out.tint = representative.tint;
            out.emissive = representative.emissive;

            for (int i = 0; i < VERTICES; i++)
            {
                float a1 = template.c1[i] == 0 ? cellA : cellA + width;
                float a2 = template.c2[i] == 0 ? cellB : cellB + height;

                setAxis(out, template.axis1, i, a1);
                setAxis(out, template.axis2, i, a2);
                setAxis(out, template.axis, i, template.plane);

                int uRaw = template.mapping.uAxis == 1 ? template.c1[i] : template.c2[i];
                int uSpan = template.mapping.uAxis == 1 ? width : height;
                out.u[i] = (template.mapping.uInverted ? (1 - uRaw) : uRaw) * uSpan;

                int vRaw = template.mapping.vAxis == 1 ? template.c1[i] : template.c2[i];
                int vSpan = template.mapping.vAxis == 1 ? width : height;
                out.v[i] = (template.mapping.vInverted ? (1 - vRaw) : vRaw) * vSpan;
            }

            return out;
        }
    }

    /**
     * One quad, field-shape-identical to {@code StreamingWorldExporter.PendingQuad} (see class doc
     * WHY NOT REUSE) — fields deliberately unmodified-access (package-private, no explicit
     * modifier), matching that class's own convention, since only same-package code ever touches
     * them.
     */
    public static final class Quad
    {
        final float[] x = new float[VERTICES];
        final float[] y = new float[VERTICES];
        final float[] z = new float[VERTICES];
        final float[] u = new float[VERTICES];
        final float[] v = new float[VERTICES];
        float nx;
        float ny;
        float nz;
        Identifier spriteId;
        int frameWidth;
        int frameHeight;
        boolean tinted;
        int tint;
        boolean emissive;
    }
}
