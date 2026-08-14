package mchorse.bbs_mod.bobj;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Blenders {@code BoneParentTransform}: wie die Elternlage an ein Kind weitergegeben wird.
 * ZWEI Matrizen statt einer — {@code rotskal} traegt Drehung und Skalierung, {@code ort}
 * traegt allein die Ortsspalte, und Blender nimmt aus jeder nur ihren Teil.
 *
 * ⛔ Bei vollem Skalen-Erbe UND lokal wirkender Verschiebung sind beide gleich; dann ist
 * dieser Apparat ueberfluessig und {@link BOBJBone#computeMatrix} nimmt den alten,
 * allokationsfreien Weg. Das gilt fuer 654 von 706 Rigify-Knochen und fuer JEDES Modell
 * ohne Rig-Mechanik — der Umbau kann also nichts Bestehendes verschieben.
 *
 * Quelle: {@code BKE_bone_parent_transform_calc_from_matrices} und
 * {@code BKE_bone_parent_transform_apply}, blenkernel/intern/armature.cc (v5.2.0).
 * Anordnung wie in {@link BOBJBasisMathe}: {@code a[i * 4 + j]} ist {@code R[i][j]}.
 */
public final class BOBJElternTransform
{
    private BOBJElternTransform()
    {}

    /** Weltmatrix nach {@code m}; {@link BOBJBone#originMat} wird mitgezogen. */
    static Matrix4f rechne(BOBJBone b, Matrix4f m)
    {
        float[] rotskal = new float[16];
        float[] ort = new float[16];
        float[] nachSkala = {1F, 1F, 1F};

        zerlege(b, rotskal, ort, nachSkala);

        /* Zweimal angewandt: mit den Kanaelen die echte Lage, ohne sie originMat —
         * daran haengen Control-Gizmos und Picking, die den Knochen ohne seine
         * eigene Drehung meinen. */
        anwenden(rotskal, ort, nachSkala, b.transform.translate, null, b.originMat);
        anwenden(rotskal, ort, nachSkala, b.transform.translate,
            b.kanalDrehung(new Matrix4f()), m);

        b.mat.set(m);

        return m;
    }

    private static void zerlege(BOBJBone b, float[] rotskal, float[] ort, float[] nachSkala)
    {
        float[] eigen = new float[16];

        b.relBoneMat.get(eigen);

        if (b.parentBone == null)
        {
            System.arraycopy(eigen, 0, rotskal, 0, 16);

            if (b.elternraumVerschiebung)
            {
                einheit(ort);
                ort[12] = eigen[12];
                ort[13] = eigen[13];
                ort[14] = eigen[14];
            }

            return;
        }

        float[] p = new float[16];

        b.parentBone.mat.get(p);

        String modus = b.skalenErbe;
        boolean voll = modus == null || "FULL".equals(modus);

        if (voll)
        {
            mul(p, eigen, rotskal);
        }
        else
        {
            float[] t = p.clone();

            switch (modus)
            {
                case "NONE", "AVERAGE" -> BOBJBasisMathe.orthogonalisiereUmY(t, true);
                case "ALIGNED" ->
                {
                    BOBJBasisMathe.orthogonalisiereUmY(t, false);
                    BOBJBasisMathe.normalisiereMitSkala(t, nachSkala);
                }
                case "NONE_LEGACY" -> BOBJBasisMathe.normalisiereMitSkala(t, new float[3]);
                /* FIX_SHEAR behaelt Skala und Scherung hier und raeumt unten auf;
                 * ein unbekannter Wert rechnet wie FULL statt still falsch. */
                default ->
                {}
            }

            if ("AVERAGE".equals(modus))
            {
                BOBJBasisMathe.skaliereBasis(t,
                    (float) Math.cbrt(Math.abs(BOBJBasisMathe.determinante3(p))));
            }

            mul(t, eigen, rotskal);

            if ("FIX_SHEAR".equals(modus))
            {
                BOBJBasisMathe.orthogonalisiereUmY(rotskal, false);
            }
        }

        if (b.elternraumVerschiebung)
        {
            /* ⛔ DIE Stelle: die Verschiebung wirkt im ELTERNRAUM, ohne die eigene
             * Ruhedrehung. Rigify setzt das auf seine IK-Griffe (hand_ik, foot_ik,
             * *_ik_target), damit sie sich in geraden Achsen ziehen lassen. BBS
             * drehte sie stattdessen durch die Ruhebasis — bei foot_ik ist die die
             * Einheitsmatrix und der Fehler unsichtbar, bei hand_ik nicht. */
            einheit(ort);

            for (int i = 0; i < 3; i++)
            {
                System.arraycopy(p, i * 4, ort, i * 4, 3);
            }

            ort[12] = p[0] * eigen[12] + p[4] * eigen[13] + p[8] * eigen[14] + p[12];
            ort[13] = p[1] * eigen[12] + p[5] * eigen[13] + p[9] * eigen[14] + p[13];
            ort[14] = p[2] * eigen[12] + p[6] * eigen[13] + p[10] * eigen[14] + p[14];
        }
        else if (!voll)
        {
            /* Die Skalen-Flags treffen die Lage nicht — schlichter Elternraum. */
            mul(p, eigen, ort);
        }
        else
        {
            System.arraycopy(rotskal, 0, ort, 0, 16);
        }
    }

    private static void anwenden(float[] rotskal, float[] ort, float[] nachSkala,
                                 Vector3f loc, Matrix4f kanal, Matrix4f raus)
    {
        raus.set(rotskal);

        if (kanal != null)
        {
            raus.mul(kanal);
        }

        /* Die Ortsspalte kommt AUSSCHLIESSLICH aus ort. Genau hier trennt Blender
         * Verschiebung von Drehung — und genau das fehlte BBS. */
        raus.setTranslation(
            ort[0] * loc.x + ort[4] * loc.y + ort[8] * loc.z + ort[12],
            ort[1] * loc.x + ort[5] * loc.y + ort[9] * loc.z + ort[13],
            ort[2] * loc.x + ort[6] * loc.y + ort[10] * loc.z + ort[14]);

        if (nachSkala[0] != 1F || nachSkala[1] != 1F || nachSkala[2] != 1F)
        {
            raus.scale(nachSkala[0], nachSkala[1], nachSkala[2]);
        }
    }

    private static void mul(float[] a, float[] b, float[] raus)
    {
        new Matrix4f().set(a).mul(new Matrix4f().set(b)).get(raus);
    }

    private static void einheit(float[] a)
    {
        java.util.Arrays.fill(a, 0F);

        a[0] = a[5] = a[10] = a[15] = 1F;
    }
}
