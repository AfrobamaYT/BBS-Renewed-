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

    /**
     * Static vertex-to-matrix layout plus the current flat matrix palette.
     *
     * <p>The expensive questions (ordinary bone or B-Bone, segment pair and split
     * weights) depend only on imported weights and rest positions. Resolve them once;
     * each pose then copies the current matrices into a compact primitive array.</p>
     */
    public static final class Palette
    {
        private static final int STRIDE = 12;

        private final int boneCount;
        private final int[] bendyOffset;
        private final BOBJBBone[] bendyBones;
        private final int[] vertexOffset;
        private final int[] matrixIndex;
        private final float[] influenceWeight;
        private final float[] palette;

        private Palette(BOBJArmature armature, float[] pos, float[] weights,
                        int[] boneIndices, int count, Segmente cache)
        {
            this.boneCount = armature.orderedBones.size();
            this.bendyOffset = new int[this.boneCount];
            this.bendyBones = new BOBJBBone[this.boneCount];

            Arrays.fill(this.bendyOffset, -1);

            int matrixCount = this.boneCount;

            for (int i = 0; i < this.boneCount; i++)
            {
                BOBJBBone bendy = armature.orderedBones.get(i).bbone;

                if (bendy != null && bendy.aktiv() && bendy.verformJoml != null)
                {
                    this.bendyOffset[i] = matrixCount;
                    this.bendyBones[i] = bendy;
                    matrixCount += bendy.segmente + 1;
                }
            }

            this.palette = new float[matrixCount * STRIDE];
            this.vertexOffset = new int[count + 1];

            int[] matrixIndex = new int[count * 8];
            float[] influenceWeight = new float[count * 8];
            int influenceCount = 0;

            cache.sichern(count);

            for (int i = 0; i < count; i++)
            {
                this.vertexOffset[i] = influenceCount;

                for (int k = 0; k < 4; k++)
                {
                    int n = i * 4 + k;
                    float weight = weights[n];
                    int bone = boneIndices[n];

                    if (weight <= 0F || bone < 0 || bone >= this.boneCount)
                    {
                        continue;
                    }

                    int offset = this.bendyOffset[bone];

                    if (offset >= 0)
                    {
                        int segment = segment(armature, pos, bone, i, k, cache);
                        float blend = cache.blend[n];

                        matrixIndex[influenceCount] = offset + segment;
                        influenceWeight[influenceCount++] = weight * (1F - blend);
                        matrixIndex[influenceCount] = offset + segment + 1;
                        influenceWeight[influenceCount++] = weight * blend;
                    }
                    else
                    {
                        matrixIndex[influenceCount] = bone;
                        influenceWeight[influenceCount++] = weight;
                    }
                }
            }

            this.vertexOffset[count] = influenceCount;
            this.matrixIndex = Arrays.copyOf(matrixIndex, influenceCount);
            this.influenceWeight = Arrays.copyOf(influenceWeight, influenceCount);
        }

        public static Palette erstellen(BOBJArmature armature, float[] pos, float[] weights,
                                        int[] boneIndices, int count, Segmente cache)
        {
            return new Palette(armature, pos, weights, boneIndices, count, cache);
        }

        public void aktualisieren(Matrix4f[] matrices)
        {
            for (int i = 0; i < this.boneCount; i++)
            {
                Matrix4f matrix = matrices[i];

                if (matrix != null)
                {
                    kopiere(matrix, this.palette, i * STRIDE);
                }
            }

            for (int i = 0; i < this.boneCount; i++)
            {
                BOBJBBone bendy = this.bendyBones[i];

                if (bendy == null || bendy.verformJoml == null)
                {
                    continue;
                }

                int offset = this.bendyOffset[i];

                for (int s = 0; s <= bendy.segmente; s++)
                {
                    Matrix4f matrix = bendy.verformJoml[s + 1];

                    if (matrix != null)
                    {
                        kopiere(matrix, this.palette, (offset + s) * STRIDE);
                    }
                }
            }
        }

        public void vertex(float[] pos, float[] norm, int i, float[] outPos, float[] outNorm)
        {
            int source = i * 3;
            float x = pos[source];
            float y = pos[source + 1];
            float z = pos[source + 2];
            float nx = norm[source];
            float ny = norm[source + 1];
            float nz = norm[source + 2];
            float rw = 0F;
            float m0 = 0F;
            float m1 = 0F;
            float m2 = 0F;
            float m3 = 0F;
            float m4 = 0F;
            float m5 = 0F;
            float m6 = 0F;
            float m7 = 0F;
            float m8 = 0F;
            float m9 = 0F;
            float m10 = 0F;
            float m11 = 0F;

            int influenceEnd = this.vertexOffset[i + 1];

            for (int n = this.vertexOffset[i]; n < influenceEnd; n++)
            {
                int p = this.matrixIndex[n] * STRIDE;
                float w = this.influenceWeight[n];
                m0 += this.palette[p] * w;
                m1 += this.palette[p + 1] * w;
                m2 += this.palette[p + 2] * w;
                m3 += this.palette[p + 3] * w;
                m4 += this.palette[p + 4] * w;
                m5 += this.palette[p + 5] * w;
                m6 += this.palette[p + 6] * w;
                m7 += this.palette[p + 7] * w;
                m8 += this.palette[p + 8] * w;
                m9 += this.palette[p + 9] * w;
                m10 += this.palette[p + 10] * w;
                m11 += this.palette[p + 11] * w;
                rw += w;
            }

            if (rw == 0F)
            {
                outPos[source] = x;
                outPos[source + 1] = y;
                outPos[source + 2] = z;
                outNorm[source] = nx;
                outNorm[source + 1] = ny;
                outNorm[source + 2] = nz;

                return;
            }

            outPos[source] = org.joml.Math.fma(m0, x,
                org.joml.Math.fma(m1, y, org.joml.Math.fma(m2, z, m3))) / rw;
            outPos[source + 1] = org.joml.Math.fma(m4, x,
                org.joml.Math.fma(m5, y, org.joml.Math.fma(m6, z, m7))) / rw;
            outPos[source + 2] = org.joml.Math.fma(m8, x,
                org.joml.Math.fma(m9, y, org.joml.Math.fma(m10, z, m11))) / rw;
            outNorm[source] = org.joml.Math.fma(m0, nx,
                org.joml.Math.fma(m1, ny, m2 * nz));
            outNorm[source + 1] = org.joml.Math.fma(m4, nx,
                org.joml.Math.fma(m5, ny, m6 * nz));
            outNorm[source + 2] = org.joml.Math.fma(m8, nx,
                org.joml.Math.fma(m9, ny, m10 * nz));
        }

        private static float punkt(float[] matrix, int p, float x, float y, float z)
        {
            return org.joml.Math.fma(matrix[p], x,
                org.joml.Math.fma(matrix[p + 1], y,
                    org.joml.Math.fma(matrix[p + 2], z, matrix[p + 3])));
        }

        private static float richtung(float[] matrix, int p, float x, float y, float z)
        {
            return org.joml.Math.fma(matrix[p], x,
                org.joml.Math.fma(matrix[p + 1], y, matrix[p + 2] * z));
        }

        private static void kopiere(Matrix4f matrix, float[] out, int p)
        {
            out[p] = matrix.m00();
            out[p + 1] = matrix.m10();
            out[p + 2] = matrix.m20();
            out[p + 3] = matrix.m30();
            out[p + 4] = matrix.m01();
            out[p + 5] = matrix.m11();
            out[p + 6] = matrix.m21();
            out[p + 7] = matrix.m31();
            out[p + 8] = matrix.m02();
            out[p + 9] = matrix.m12();
            out[p + 10] = matrix.m22();
            out[p + 11] = matrix.m32();
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
