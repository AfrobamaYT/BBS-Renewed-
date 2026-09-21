package mchorse.bbs_mod.bobj;

import mchorse.bbs_mod.cubic.ik.RigBBone;
import mchorse.bbs_mod.cubic.ik.RigMathe;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.Arrays;

/**
 * BBS' Skinning eines Vertex — DIE eine Stelle, die der VAO (Zeichnen) und die Mess-Sonde
 * (Vergleich gegen Blenders ausgewertetes Netz) teilen. Blenders {@code armature_deform}:
 * lineare Mischung der Verform-Matrizen; bei Bendy Bones ({@code b_bone_deform}) die zwei
 * Segmentmatrizen um den Ruhe-Ort des Vertex, nach seiner Lage entlang des Knochens gemischt.
 *
 * <p>Thread-safe: all mutable result vectors belong to the caller. This lets large meshes
 * split independent vertices across workers without copying the armature.
 */
public final class BOBJHaut
{
    /** B-Bone-Segment je Vertex-Gewicht (Blenders Segmentindex und Mischfaktor); -1 = ungerechnet. */
    public static final class Segmente
    {
        public int[] seg;
        public float[] blend;

        public void sichern(int count)
        {
            if (this.seg == null || this.seg.length != count * 4)
            {
                this.seg = new int[count * 4];
                this.blend = new float[count * 4];
                Arrays.fill(this.seg, -1);
            }
        }
    }

    private BOBJHaut()
    {}

    /**
     * Haeutet Vertex {@code i}: Ort und Normale aus den bis zu vier Gewichten.
     *
     * @param pos      Ausgangslagen (3 je Vertex), auch die Quelle der B-Bone-Segmentwahl
     * @param norm     Ausgangsnormalen (3 je Vertex)
     * @param w        Gewichte (4 je Vertex), 0 = leer
     * @param bi       Knochenindizes (4 je Vertex), -1 = leer
     * @param mats     Haut-Matrizen je Knochen ({@code mat x inv(rest)})
     * @param cache    darf null sein, wenn das Modell keine Bendy Bones hat
     * @return der Knochen mit dem groessten Gewicht (Licht/Picking), -1 ohne Gewicht
     */
    public static int vertex(BOBJArmature arm, float[] pos, float[] norm, float[] w, int[] bi, Matrix4f[] mats,
                             int i, Segmente cache, Vector4f result, Vector3f resultNormal)
    {
        result.set(0F, 0F, 0F, 0F);
        resultNormal.set(0F, 0F, 0F);

        int count = 0;
        float maxWeight = -1F;
        int lightBone = -1;

        for (int k = 0; k < 4; k++)
        {
            float weight = w[i * 4 + k];

            if (weight <= 0F)
            {
                continue;
            }

            int index = bi[i * 4 + k];
            BOBJBBone bb = bbone(arm, index);

            if (bb != null && cache != null)
            {
                int s = segment(arm, pos, index, i, k, cache);
                float blend = cache.blend[i * 4 + k];

                anteil(bb.verformJoml[s + 1], weight * (1F - blend), pos, norm, i, result, resultNormal);
                anteil(bb.verformJoml[s + 2], weight * blend, pos, norm, i, result, resultNormal);
            }
            else
            {
                anteil(mats[index], weight, pos, norm, i, result, resultNormal);
            }

            count++;

            if (weight > maxWeight)
            {
                lightBone = index;
                maxWeight = weight;
            }
        }

        if (count == 0)
        {
            result.set(pos[i * 3], pos[i * 3 + 1], pos[i * 3 + 2], 1F);
            resultNormal.set(norm[i * 3], norm[i * 3 + 1], norm[i * 3 + 2]);
        }

        result.x /= result.w;
        result.y /= result.w;
        result.z /= result.w;

        return lightBone;
    }

    /** Der Bendy-Bone hinter einem Gewichtsindex — oder null, wenn der Knochen keiner ist. */
    private static BOBJBBone bbone(BOBJArmature arm, int index)
    {
        if (index < 0 || index >= arm.orderedBones.size())
        {
            return null;
        }

        BOBJBBone d = arm.orderedBones.get(index).bbone;

        return d != null && d.aktiv() && d.verformJoml != null ? d : null;
    }

    /**
     * {@code BKE_pchan_bbone_deform_segment_index} fuer Vertex i, Gewicht k — aus der Ruhelage,
     * darum einmal gerechnet und gemerkt. Der Rahmenwechsel in Blenders Pose-Raum kommt von der
     * Rig-Mechanik ({@link BOBJArmature#rigPoseWandler}); ohne Rig gibt es keine B-Bones.
     */
    private static int segment(BOBJArmature arm, float[] pos, int index, int i, int k, Segmente cache)
    {
        int n = i * 4 + k;

        if (cache.seg[n] < 0)
        {
            float[] p = {pos[i * 3], pos[i * 3 + 1], pos[i * 3 + 2]};
            float[] gw = arm.rigPoseWandler;
            float[] co = gw == null ? p : RigMathe.punkt(gw, p);
            int[] idx = new int[1];
            float[] bl = new float[1];

            RigBBone.segmentIndex(arm.orderedBones.get(index), co, idx, bl);
            cache.seg[n] = idx[0];
            cache.blend[n] = bl[0];
        }

        return cache.seg[n];
    }

    /** Ein Gewichtsanteil: Ort und Normale durch {@code m}, mit {@code w} aufaddiert. */
    private static void anteil(Matrix4f m, float w, float[] pos, float[] norm, int i, Vector4f result, Vector3f resultNormal)
    {
        int n = i * 3;
        float x = pos[n];
        float y = pos[n + 1];
        float z = pos[n + 2];
        float nx = norm[n];
        float ny = norm[n + 1];
        float nz = norm[n + 2];

        /* JOML uses column-major component access. This is exactly Matrix4f.transform
         * and Matrix3f.set(m).transform, expanded to avoid rebuilding a Matrix3f and
         * mutating two shared scratch vectors for every single bone weight. */
        float tx = org.joml.Math.fma(m.m00(), x,
            org.joml.Math.fma(m.m10(), y, org.joml.Math.fma(m.m20(), z, m.m30())));
        float ty = org.joml.Math.fma(m.m01(), x,
            org.joml.Math.fma(m.m11(), y, org.joml.Math.fma(m.m21(), z, m.m31())));
        float tz = org.joml.Math.fma(m.m02(), x,
            org.joml.Math.fma(m.m12(), y, org.joml.Math.fma(m.m22(), z, m.m32())));
        float tw = org.joml.Math.fma(m.m03(), x,
            org.joml.Math.fma(m.m13(), y, org.joml.Math.fma(m.m23(), z, m.m33())));

        result.x += tx * w;
        result.y += ty * w;
        result.z += tz * w;
        result.w += tw * w;

        float tnx = org.joml.Math.fma(m.m00(), nx,
            org.joml.Math.fma(m.m10(), ny, m.m20() * nz));
        float tny = org.joml.Math.fma(m.m01(), nx,
            org.joml.Math.fma(m.m11(), ny, m.m21() * nz));
        float tnz = org.joml.Math.fma(m.m02(), nx,
            org.joml.Math.fma(m.m12(), ny, m.m22() * nz));

        resultNormal.x += tnx * w;
        resultNormal.y += tny * w;
        resultNormal.z += tnz * w;
    }
}
