package mchorse.bbs_mod.cubic.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import mchorse.bbs_mod.settings.values.ui.ValueDebugElement;
import mchorse.bbs_mod.utils.colors.Colors;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Blenders Rig-Controls, 1:1 nachgebaut: die Kurven-Griffe, mit denen Blender/Rigify und
 * Maya IK-Ziele anfassbar machen. Geometrie exakt aus Rigifys {@code widgets_basic.py}
 * (Kreis 32 Punkte · Wuerfel 8 Ecken/12 Kanten · Kugel = 3 senkrechte Kreise a 16 Punkte),
 * gezeichnet als Haarlinien (DEBUG_LINES) in der Orientierung des Bones — Blenders
 * Custom-Shape-Matrix ist pose_mat x Offsets, unsere ist der PivotFrame des Ziel-Bones.
 *
 * <p>Farben nach Blenders Wire-Regel aus {@code overlay_armature.cc}: der selektierte
 * (= aktive) Bone traegt die Active-Farbe seines Farbsets, unselektierte die Solid-Farbe
 * um −50 geschattet ({@code cp_shade_color3ub}). Die Farbsets sind Blenders
 * Default-Theme-Sets ({@code userdef_default_theme.c}), Set 1 (Rot) ist die
 * IK-Konvention aus Rigify.
 */
public final class RigWidgets
{
    /** Blenders 15 belegte Bone-Farbsets: {solid, select, active}. Sets 16-20 sind leer. */
    private static final int[][] COLOR_SETS = {
        {0x9a0000, 0xbd1111, 0xf70a0a}, {0xf74018, 0xf66913, 0xfa9900},
        {0x1e9109, 0x59b70b, 0x83ef1d}, {0x0a3694, 0x3667df, 0x5ec1ef},
        {0xa9294e, 0xc1416a, 0xf05d91}, {0x430c78, 0x543aa3, 0x8764d5},
        {0x24785a, 0x3c9579, 0x6fb6ab}, {0x4b707c, 0x6a8691, 0x9bc2cd},
        {0xf4c90c, 0xeec236, 0xf3ff00}, {0x1e2024, 0x484c56, 0xffffff},
        {0x6f2f6a, 0x9845be, 0xd330d6}, {0x6c8e22, 0x7fb022, 0xbbef5b},
        {0x8d8d8d, 0xb0b0b0, 0xdedede}, {0x834326, 0x8b5811, 0xbd6a11},
        {0x08310e, 0x1c430b, 0x34622b},
    };

    /** Einheitsgeometrie als Segmentliste {x1,y1,z1, x2,y2,z2} je Kante. */
    private static final float[] CUBE = buildCube();
    private static final float[] SPHERE = buildSphere();
    private static final float[] CIRCLE = buildCircle();

    private static final Vector3f P1 = new Vector3f();
    private static final Vector3f P2 = new Vector3f();

    private RigWidgets()
    {
    }

    public static boolean isWire(int shape)
    {
        return shape == ValueDebugElement.SHAPE_WIRE_CUBE
            || shape == ValueDebugElement.SHAPE_WIRE_SPHERE
            || shape == ValueDebugElement.SHAPE_WIRE_CIRCLE;
    }

    /**
     * Blenders Wire-Farbregel: selektiert (bei Einzelauswahl zugleich aktiv) → Active-Farbe
     * des Sets, dessen Solid-Farbe {@code solid} ist (fremde Farben: +80 aufgehellt);
     * unselektiert → Solid-Farbe um −50 geschattet.
     */
    public static float[] wireColor(int solid, boolean selected)
    {
        if (selected)
        {
            for (int[] set : COLOR_SETS)
            {
                if (set[0] == solid)
                {
                    return DebugOverlay.rgb(set[2]);
                }
            }

            return DebugOverlay.rgb(shade(solid, 80));
        }

        return DebugOverlay.rgb(shade(solid, -50));
    }

    /** Blenders {@code cp_shade_color3ub}: Offset je Kanal, geklemmt auf 0..255. */
    private static int shade(int color, int offset)
    {
        int r = Math.min(255, Math.max(0, ((color >> 16) & 0xFF) + offset));
        int g = Math.min(255, Math.max(0, ((color >> 8) & 0xFF) + offset));
        int b = Math.min(255, Math.max(0, (color & 0xFF) + offset));

        return r << 16 | g << 8 | b;
    }

    /**
     * Ein Widget am Bone: Einheitsgeometrie x Bone-Skalierung x {@code size}, gedreht mit
     * der gelösten Bone-Orientierung, an dessen Position — Blenders Custom-Shape-Matrix
     * ohne die (hier nicht konfigurierbaren) Translations-/Rotations-Offsets.
     */
    public static void emit(BufferBuilder builder, Matrix4f matrix, int shape, CubicRenderer.PivotFrame frame, float size, float[] col, float a)
    {
        if (frame == null)
        {
            return;
        }

        float[] segments = shape == ValueDebugElement.SHAPE_WIRE_SPHERE ? SPHERE
            : shape == ValueDebugElement.SHAPE_WIRE_CIRCLE ? CIRCLE : CUBE;

        Vector3f pos = frame.position();
        Quaternionf rot = frame.worldRotation();
        Vector3f scale = frame.scale();

        for (int i = 0; i < segments.length; i += 6)
        {
            transform(segments, i, pos, rot, scale, size, P1);
            transform(segments, i + 3, pos, rot, scale, size, P2);

            DebugOverlay.line(builder, matrix, P1, P2, col, a);
        }
    }

    private static void transform(float[] segments, int i, Vector3f pos, Quaternionf rot, Vector3f scale, float size, Vector3f out)
    {
        out.set(segments[i] * scale.x, segments[i + 1] * scale.y, segments[i + 2] * scale.z).mul(size);
        rot.transform(out);
        out.add(pos);
    }

    /* Einheitsgeometrie — Ecken und Kanten exakt wie Rigify. */

    /** Rigify create_cube_widget: 8 Ecken (±1)³, 12 Kanten. */
    private static float[] buildCube()
    {
        float[][] v = {
            {1, 1, 1}, {1, -1, 1}, {-1, -1, 1}, {-1, 1, 1},
            {1, 1, -1}, {1, -1, -1}, {-1, -1, -1}, {-1, 1, -1},
        };
        int[][] e = {
            {0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6},
            {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7},
        };

        float[] out = new float[e.length * 6];

        for (int i = 0; i < e.length; i++)
        {
            System.arraycopy(v[e[i][0]], 0, out, i * 6, 3);
            System.arraycopy(v[e[i][1]], 0, out, i * 6 + 3, 3);
        }

        return out;
    }

    /** Rigify create_sphere_widget: drei senkrechte Kreise à 16 Punkte. */
    private static float[] buildSphere()
    {
        float[] out = new float[3 * 16 * 6];
        int offset = 0;

        offset = circle(out, offset, 16, 0);
        offset = circle(out, offset, 16, 1);
        circle(out, offset, 16, 2);

        return out;
    }

    /** Rigify create_circle_widget: ein Kreis mit 32 Punkten in der Bone-Querebene (XZ). */
    private static float[] buildCircle()
    {
        float[] out = new float[32 * 6];

        circle(out, 0, 32, 1);

        return out;
    }

    /** Ein geschlossener Kreis als Segmentpaare; {@code plane}: 0 = YZ, 1 = XZ, 2 = XY. */
    private static int circle(float[] out, int offset, int steps, int plane)
    {
        for (int i = 0; i < steps; i++)
        {
            offset = point(out, offset, steps, i, plane);
            offset = point(out, offset, steps, i + 1, plane);
        }

        return offset;
    }

    private static int point(float[] out, int offset, int steps, int i, int plane)
    {
        float angle = (float) (i % steps) / steps * (float) (Math.PI * 2);
        float sin = (float) Math.sin(angle);
        float cos = (float) Math.cos(angle);

        out[offset] = plane == 0 ? 0F : sin;
        out[offset + 1] = plane == 1 ? 0F : (plane == 0 ? sin : cos);
        out[offset + 2] = plane == 2 ? 0F : cos;

        return offset + 3;
    }
}
