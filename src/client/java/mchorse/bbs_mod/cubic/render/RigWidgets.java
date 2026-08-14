package mchorse.bbs_mod.cubic.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.settings.values.ui.ValueDebugElement;
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
    /** Lazy statt final-Initializer: ein Hotswap laesst neue statics null — genau so
     *  starb die Instanz am 2026-08-13 (NPE je Frame, crash-20.45.47). */
    private static float[] OCTA;
    private static float[] OCTA_SOLID;

    /* Blenders Pose-Mode-Theme (userdef_default_theme.c): Skelett-Bones grau, Auswahl
     * HELLBLAU, aktiv cyan — die Farben, die jeder aus dem Blender-Viewport kennt. */
    public static final int POSE_UNSEL = 0xb2b2b2;
    public static final int POSE_AKTIV = 0x8cffff;

    /* Linienquerschnitt in Welt bei 5 Bloecken/FOV50 (getAxesDistanceScale-Referenz):
     * ~2.7px opaker Kern + ~1.8px Alpha-Auslauf je Seite — Blender faehrt 2px, hier
     * bewusst kraeftiger: die Editor-Fenster sind oft klein (854er-Boot) und die
     * Widgets muessen auf dunklen Skins ohne Outline bestehen. */
    private static final float HALB_KERN = 0.0060F;
    private static final float HALB_AUSSEN = 0.0085F;

    private static final Vector3f P1 = new Vector3f();
    private static final Vector3f P2 = new Vector3f();

    /** Video-zu-Anzeige-Verhaeltnis: die px-Konstanz der Baender gilt im RENDERZIEL
     *  (Video, z.B. 3840), das aufs Vorschaufeld (z.B. 454px) verkleinert wird — ohne
     *  diesen Faktor schrumpfen 2.7px auf unter 1px Anzeige. Setzt der Editor-Pass je
     *  Frame; 0 (frisch gehotswappte Klasse) faellt auf 1 zurueck. */
    private static float breitenSkala = 1F;

    public static void setBreitenSkala(float skala)
    {
        breitenSkala = skala;
    }

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
     *
     * <p>Gezeichnet als kameragerichtete Bänder mit Alpha-Auslauf an den Rändern
     * (Blenders Wire-AA, CPU-seitig): {@code kameraRaum} = Ansicht x PoseStack bringt die
     * Punkte in den Kamera-Raum, dort ist der Blickstrahl der Punkt selbst und die
     * Bandbreite über {@link BBSSettings#getAxesDistanceScale} bildschirm-konstant.
     * Der Zug dazu: TRIANGLES mit Blend, Modellansicht IDENTITÄT (alles steckt schon
     * in den Ecken).
     */
    public static void emit(BufferBuilder builder, Matrix4f kameraRaum, int shape, CubicRenderer.PivotFrame frame, float size, float[] col, float a)
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
            transform(segments, i, pos, rot, scale, size, kameraRaum, P1);
            transform(segments, i + 3, pos, rot, scale, size, kameraRaum, P2);

            band(builder, P1, P2, col, a);
        }
    }

    private static void transform(float[] segments, int i, Vector3f pos, Quaternionf rot, Vector3f scale, float size, Matrix4f kameraRaum, Vector3f out)
    {
        out.set(segments[i] * scale.x, segments[i + 1] * scale.y, segments[i + 2] * scale.z).mul(size);
        rot.transform(out);
        out.add(pos);
        kameraRaum.transformPosition(out);
    }

    /**
     * Wie {@link #emit}, aber mit einer FERTIG kombinierten Matrix (Kamera-Raum x
     * Bone-Welt) — der Weg des Editor-Passes, der die Welt-Matrix je Ziel-Bone ueber
     * {@code getBoneCompositeMatrix} bezieht statt ueber model-lokale PivotFrames.
     */
    public static void emit(BufferBuilder builder, Matrix4f kombi, int shape, float size, float[] col, float a)
    {
        float[] segments = shape == ValueDebugElement.SHAPE_WIRE_SPHERE ? SPHERE
            : shape == ValueDebugElement.SHAPE_WIRE_CIRCLE ? CIRCLE : CUBE;

        for (int i = 0; i < segments.length; i += 6)
        {
            P1.set(segments[i] * size, segments[i + 1] * size, segments[i + 2] * size);
            kombi.transformPosition(P1);
            P2.set(segments[i + 3] * size, segments[i + 4] * size, segments[i + 5] * size);
            kombi.transformPosition(P2);

            band(builder, P1, P2, col, a);
        }
    }

    /**
     * Ein Segment als kameragerichtetes Band aus drei Streifen: opaker Kern, links und
     * rechts je ein Streifen, dessen Aussenkante auf Alpha 0 läuft — der Rasterizer
     * interpoliert dazwischen, die Kante wird weich (Blenders smoothstep, linear genähert).
     */
    private static void band(BufferBuilder builder, Vector3f p1, Vector3f p2, float[] col, float a)
    {
        band(builder, p1, p2, col, a, 1F);
    }

    private static void band(BufferBuilder builder, Vector3f p1, Vector3f p2, float[] col, float a, float breite)
    {
        /* Lokale Scratches (hotswap-sicher: neue statics waeren nach einem Swap null). */
        Vector3f mitte = new Vector3f(p1).add(p2).mul(0.5F);

        float dist = mitte.length();

        /* seite = segment x blickstrahl — im Kamera-Raum ist der Strahl die Mitte selbst. */
        Vector3f seite = new Vector3f(p2).sub(p1).cross(mitte);

        float len = seite.length();

        if (len < 1.0E-6F)
        {
            return;
        }

        float skala = breitenSkala <= 0F ? 1F : breitenSkala;
        float scale = BBSSettings.getAxesDistanceScale(dist) * skala * breite;
        float kern = HALB_KERN * scale / len;
        float aussen = HALB_AUSSEN * scale / len;

        float kx = seite.x * kern, ky = seite.y * kern, kz = seite.z * kern;
        float ax = seite.x * aussen, ay = seite.y * aussen, az = seite.z * aussen;

        /* Drei Quads quer über das Band: aussenL(0) kernL(a) kernR(a) aussenR(0). */
        streifen(builder, p1, p2, ax, ay, az, kx, ky, kz, col, 0F, a);
        streifen(builder, p1, p2, kx, ky, kz, -kx, -ky, -kz, col, a, a);
        streifen(builder, p1, p2, -kx, -ky, -kz, -ax, -ay, -az, col, a, 0F);
    }

    /** Ein Quad zwischen den Querversätzen {@code l} und {@code r}, Alpha je Kante. */
    private static void streifen(BufferBuilder builder, Vector3f p1, Vector3f p2,
        float lx, float ly, float lz, float rx, float ry, float rz, float[] col, float aL, float aR)
    {
        float x1l = p1.x + lx, y1l = p1.y + ly, z1l = p1.z + lz;
        float x1r = p1.x + rx, y1r = p1.y + ry, z1r = p1.z + rz;
        float x2l = p2.x + lx, y2l = p2.y + ly, z2l = p2.z + lz;
        float x2r = p2.x + rx, y2r = p2.y + ry, z2r = p2.z + rz;

        builder.addVertex(x1l, y1l, z1l).setColor(col[0], col[1], col[2], aL);
        builder.addVertex(x1r, y1r, z1r).setColor(col[0], col[1], col[2], aR);
        builder.addVertex(x2r, y2r, z2r).setColor(col[0], col[1], col[2], aR);

        builder.addVertex(x1l, y1l, z1l).setColor(col[0], col[1], col[2], aL);
        builder.addVertex(x2r, y2r, z2r).setColor(col[0], col[1], col[2], aR);
        builder.addVertex(x2l, y2l, z2l).setColor(col[0], col[1], col[2], aL);
    }

    /* Einheitsgeometrie — Ecken und Kanten exakt wie Rigify. */

    /**
     * Ein Bone als Blender-Octahedron: Einheitslaenge entlang +Y, Querschnitt skaliert
     * mit — {@code size} ist die BONE-LAENGE. Kombi-Matrix wie bei {@link #emit}.
     */
    public static void emitOcta(BufferBuilder builder, Matrix4f kombi, float laenge, float[] col, float a)
    {
        if (OCTA == null)
        {
            OCTA = buildOcta();
        }

        for (int i = 0; i < OCTA.length; i += 6)
        {
            P1.set(OCTA[i] * laenge, OCTA[i + 1] * laenge, OCTA[i + 2] * laenge);
            kombi.transformPosition(P1);
            P2.set(OCTA[i + 3] * laenge, OCTA[i + 4] * laenge, OCTA[i + 5] * laenge);
            kombi.transformPosition(P2);

            /* Haarfeiner Umriss (Blenders 1px-Bone-Wire): halbe Breite der IK-Kurven,
             * sonst frisst die Outline die duennen Knochenkoerper schwarz. */
            band(builder, P1, P2, col, a, 0.5F);
        }
    }

    /**
     * Der SOLIDE Blender-Knochen: die 8 Dreiecke aus {@code bone_octahedral_solid_tris},
     * flat geshadet gegen die Kamera (Blenders bone_solid-Eindruck), volle Deckkraft.
     * Der Wire-Umriss ({@link #emitOcta}) kommt in der Outline-Farbe darueber — zusammen
     * ist das Blenders zweistufiges Bone-Rendering.
     */
    public static void emitOctaSolid(BufferBuilder builder, Matrix4f kombi, float laenge, float grundGrau, float a)
    {
        if (OCTA_SOLID == null)
        {
            float[][] v = {
                {0, 0, 0}, {0.1F, 0.1F, 0.1F}, {0.1F, 0.1F, -0.1F},
                {-0.1F, 0.1F, -0.1F}, {-0.1F, 0.1F, 0.1F}, {0, 1, 0},
            };
            int[][] t = {
                {2, 1, 0}, {3, 2, 0}, {4, 3, 0}, {1, 4, 0},
                {5, 1, 2}, {5, 2, 3}, {5, 3, 4}, {5, 4, 1},
            };

            OCTA_SOLID = new float[t.length * 9];

            for (int i = 0; i < t.length; i++)
            {
                for (int e = 0; e < 3; e++)
                {
                    System.arraycopy(v[t[i][e]], 0, OCTA_SOLID, i * 9 + e * 3, 3);
                }
            }
        }

        Vector3f a1 = new Vector3f();
        Vector3f b1 = new Vector3f();
        Vector3f c1 = new Vector3f();

        for (int i = 0; i < OCTA_SOLID.length; i += 9)
        {
            a1.set(OCTA_SOLID[i] * laenge, OCTA_SOLID[i + 1] * laenge, OCTA_SOLID[i + 2] * laenge);
            kombi.transformPosition(a1);
            b1.set(OCTA_SOLID[i + 3] * laenge, OCTA_SOLID[i + 4] * laenge, OCTA_SOLID[i + 5] * laenge);
            kombi.transformPosition(b1);
            c1.set(OCTA_SOLID[i + 6] * laenge, OCTA_SOLID[i + 7] * laenge, OCTA_SOLID[i + 8] * laenge);
            kombi.transformPosition(c1);

            /* Flat-Licht aus Kamerarichtung (im Kamera-Raum ist das die z-Komponente der
             * Flaechennormale) — genug fuer Blenders matten Knochen-Eindruck. */
            float nx = (b1.y - a1.y) * (c1.z - a1.z) - (b1.z - a1.z) * (c1.y - a1.y);
            float ny = (b1.z - a1.z) * (c1.x - a1.x) - (b1.x - a1.x) * (c1.z - a1.z);
            float nz = (b1.x - a1.x) * (c1.y - a1.y) - (b1.y - a1.y) * (c1.x - a1.x);
            float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            float licht = l < 1.0E-9F ? 0.6F : 0.45F + 0.55F * Math.abs(nz / l);
            float g = grundGrau * licht;

            builder.addVertex(a1.x, a1.y, a1.z).setColor(g, g, g, a);
            builder.addVertex(b1.x, b1.y, b1.z).setColor(g, g, g, a);
            builder.addVertex(c1.x, c1.y, c1.z).setColor(g, g, g, a);
        }
    }

    /** Blenders bone_octahedral_verts (draw_cache.cc): Kopf (0,0,0), Ring bei y=0.1
     *  mit ±0.1, Spitze (0,1,0) — 12 Wire-Kanten. */
    private static float[] buildOcta()
    {
        float[][] v = {
            {0, 0, 0}, {0.1F, 0.1F, 0.1F}, {0.1F, 0.1F, -0.1F},
            {-0.1F, 0.1F, -0.1F}, {-0.1F, 0.1F, 0.1F}, {0, 1, 0},
        };
        int[][] e = {
            {0, 1}, {0, 2}, {0, 3}, {0, 4},
            {1, 2}, {2, 3}, {3, 4}, {4, 1},
            {5, 1}, {5, 2}, {5, 3}, {5, 4},
        };

        float[] out = new float[e.length * 6];

        for (int i = 0; i < e.length; i++)
        {
            System.arraycopy(v[e[i][0]], 0, out, i * 6, 3);
            System.arraycopy(v[e[i][1]], 0, out, i * 6 + 3, 3);
        }

        return out;
    }

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
