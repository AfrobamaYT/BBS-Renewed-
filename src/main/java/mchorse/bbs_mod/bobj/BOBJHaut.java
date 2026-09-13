package mchorse.bbs_mod.bobj;

import mchorse.bbs_mod.cubic.ik.RigBBone;
import mchorse.bbs_mod.cubic.ik.RigMathe;

import org.joml.Matrix3f;
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
 * <p>Nur fuer den Render-Thread bzw. eine Sonde nacheinander gedacht: die Rechenpuffer sind
 * statisch, damit je Vertex und Bild nichts alloziert wird.
 */
public final class BOBJHaut
{
    private static final Vector4f SUMME = new Vector4f();
    private static final Vector3f SUMME_N = new Vector3f();
    private static final Matrix3f DREI = new Matrix3f();

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
        SUMME.set(pos[i * 3], pos[i * 3 + 1], pos[i * 3 + 2], 1F);
        m.transform(SUMME);
        result.add(SUMME.mul(w));

        SUMME_N.set(norm[i * 3], norm[i * 3 + 1], norm[i * 3 + 2]);
        DREI.set(m).transform(SUMME_N);
        resultNormal.add(SUMME_N.mul(w));
    }
}
