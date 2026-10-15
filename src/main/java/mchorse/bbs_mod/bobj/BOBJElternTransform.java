package mchorse.bbs_mod.bobj;

import mchorse.bbs_mod.cubic.ik.RigMathe;
import mchorse.bbs_mod.cubic.ik.RigMatrix3;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Blenders {@code BoneParentTransform}: wie die Elternlage an ein Kind weitergegeben wird.
 * ZWEI Matrizen statt einer — {@code rotskal} traegt Drehung und Skalierung, {@code ort}
 * traegt allein die Ortsspalte, und Blender nimmt aus jeder nur ihren Teil.
 *
 * ⛔ Bei vollem Skalen-Erbe UND lokal wirkender Verschiebung UND geerbter Drehung sind beide
 * gleich; dann ist dieser Apparat ueberfluessig und {@link BOBJBone#computeMatrix} nimmt den
 * alten, allokationsfreien Weg. Das gilt fuer die meisten Knochen und fuer JEDES Modell ohne
 * Rig-Mechanik — der Umbau kann also nichts Bestehendes verschieben.
 *
 * Quelle: {@code BKE_bone_parent_transform_calc_from_matrices}, {@code _invert} und
 * {@code _apply}, blenkernel/intern/armature.cc (v5.2.1). Anordnung wie in
 * {@link BOBJBasisMathe}: {@code a[i * 4 + j]} ist {@code R[i][j]}.
 */
public final class BOBJElternTransform
{
    /** Blenders {@code BoneParentTransform}. */
    public static final class Bpt
    {
        public float[] rotskal = RigMathe.einheit();
        public float[] ort = RigMathe.einheit();
        public float[] nachSkala = {1F, 1F, 1F};

        /** {@code BKE_bone_parent_transform_invert}. */
        public Bpt invertiert()
        {
            Bpt b = new Bpt();

            b.rotskal = RigMathe.invertiert(this.rotskal);
            b.ort = RigMathe.invertiert(this.ort);

            for (int i = 0; i < 3; i++)
            {
                b.nachSkala[i] = this.nachSkala[i] == 0F ? 0F : 1F / this.nachSkala[i];
            }

            return b;
        }

        /** {@code BKE_bone_parent_transform_apply}. */
        public float[] anwenden(float[] inmat)
        {
            float[] out = RigMathe.mul(this.rotskal, inmat);

            RigMathe.setzeOrt(out, RigMathe.punkt(this.ort, RigMathe.ort(inmat)));
            RigMathe.skaliere(out, this.nachSkala);

            return out;
        }
    }

    private BOBJElternTransform()
    {}

    /**
     * {@code BKE_bone_parent_transform_calc_from_matrices} — rahmenunabhaengig: alle Eingaben
     * im selben Raum (BBS-Armature oder Blender-Pose), das Ergebnis gilt dort.
     *
     * @param offs      {@code offs_bone}: Ruhelage relativ zum Elter (bei Wurzeln: arm_mat)
     * @param elternPose Pose-Matrix des Elters, null bei Wurzeln
     * @param elternArm  Ruhe-Matrix des Elters im Armature-Raum (fuer Hinge), null bei Wurzeln
     */
    public static Bpt berechne(float[] offs, float[] elternPose, float[] elternArm,
                               boolean erbtDrehung, String skalenErbe, boolean lokaleVerschiebung)
    {
        Bpt r = new Bpt();

        if (elternPose == null)
        {
            r.rotskal = RigMathe.kopie(offs);

            if (!lokaleVerschiebung)
            {
                r.ort = RigMathe.einheit();
                RigMathe.setzeOrt(r.ort, RigMathe.ort(offs));
            }
            else
            {
                r.ort = RigMathe.kopie(r.rotskal);
            }

            return r;
        }

        String modus = skalenErbe == null || skalenErbe.isEmpty() ? "FULL" : skalenErbe;
        boolean voll = erbtDrehung && "FULL".equals(modus);

        if (voll)
        {
            r.rotskal = RigMathe.mul(elternPose, offs);
        }
        else
        {
            float[] t;

            if (erbtDrehung)
            {
                t = RigMathe.kopie(elternPose);

                switch (modus)
                {
                    case "NONE", "AVERAGE" -> BOBJBasisMathe.orthogonalisiereUmY(t, true);
                    case "ALIGNED" ->
                    {
                        BOBJBasisMathe.orthogonalisiereUmY(t, false);
                        BOBJBasisMathe.normalisiereMitSkala(t, r.nachSkala);
                    }
                    case "NONE_LEGACY" -> BOBJBasisMathe.normalisiereMitSkala(t, new float[3]);
                    default ->
                    {}
                }
            }
            else
            {
                /* BONE_HINGE: die RUHE-Drehung des Elters statt seiner Pose-Drehung. */
                t = RigMathe.kopie(elternArm);

                switch (modus)
                {
                    case "FULL" -> RigMathe.skaliere(t, RigMathe.groesse(elternPose));
                    case "FIX_SHEAR" -> RigMathe.skaliere(t, RigMatrix3.groesseOhneScherung(elternPose));
                    case "ALIGNED" -> r.nachSkala = RigMatrix3.groesseOhneScherung(elternPose);
                    default ->
                    {}
                }
            }

            if ("AVERAGE".equals(modus))
            {
                BOBJBasisMathe.skaliereBasis(t,
                    (float) Math.cbrt(Math.abs(BOBJBasisMathe.determinante3(elternPose))));
            }

            r.rotskal = RigMathe.mul(t, offs);

            if ("FIX_SHEAR".equals(modus))
            {
                BOBJBasisMathe.orthogonalisiereUmY(r.rotskal, false);
            }
        }

        if (!lokaleVerschiebung)
        {
            /* Die Verschiebung wirkt im ELTERNRAUM, ohne die eigene Ruhedrehung: Ort =
             * Elternpose x Kopf, 3x3 = die 3x3 der Elternpose. */
            r.ort = RigMathe.einheit();

            for (int i = 0; i < 3; i++)
            {
                System.arraycopy(elternPose, i * 4, r.ort, i * 4, 3);
            }

            RigMathe.setzeOrt(r.ort, RigMathe.punkt(elternPose, RigMathe.ort(offs)));
        }
        else if (!voll)
        {
            r.ort = RigMathe.mul(elternPose, offs);
        }
        else
        {
            r.ort = RigMathe.kopie(r.rotskal);
        }

        return r;
    }

    /** Die Elternuebergabe DIESES Knochens im BBS-Armature-Raum. */
    public static Bpt berechne(BOBJBone b)
    {
        float[] offs = b.relBoneMat.get(new float[16]);

        if (b.parentBone == null)
        {
            return berechne(offs, null, null, !b.keineElternDrehung, b.skalenErbe, !b.elternraumVerschiebung);
        }

        return berechne(offs, b.parentBone.mat.get(new float[16]), b.parentBone.boneMat.get(new float[16]),
            !b.keineElternDrehung, b.skalenErbe, !b.elternraumVerschiebung);
    }

    /** Weltmatrix nach {@code m}; {@link BOBJBone#originMat} wird mitgezogen. */
    static Matrix4f rechne(BOBJBone b, Matrix4f m)
    {
        Bpt bpt = berechne(b);

        /* Zweimal angewandt: mit den Kanaelen die echte Lage, ohne sie originMat —
         * daran haengen Control-Gizmos und Picking, die den Knochen ohne seine
         * eigene Drehung meinen. */
        anwenden(bpt, b, null, b.originMat);
        anwenden(bpt, b, b.kanalDrehung(new Matrix4f()), m);

        b.mat.set(m);

        return m;
    }

    private static void anwenden(Bpt bpt, BOBJBone b, Matrix4f kanal, Matrix4f raus)
    {
        Vector3f loc = b.verbunden ? new Vector3f() : b.transform.translate;

        raus.set(bpt.rotskal);

        if (kanal != null)
        {
            raus.mul(kanal);
        }

        float[] o = bpt.ort;

        /* Die Ortsspalte kommt AUSSCHLIESSLICH aus ort. Genau hier trennt Blender
         * Verschiebung von Drehung — und genau das fehlte BBS. */
        raus.setTranslation(
            o[0] * loc.x + o[4] * loc.y + o[8] * loc.z + o[12],
            o[1] * loc.x + o[5] * loc.y + o[9] * loc.z + o[13],
            o[2] * loc.x + o[6] * loc.y + o[10] * loc.z + o[14]);

        if (bpt.nachSkala[0] != 1F || bpt.nachSkala[1] != 1F || bpt.nachSkala[2] != 1F)
        {
            raus.scale(bpt.nachSkala[0], bpt.nachSkala[1], bpt.nachSkala[2]);
        }
    }
}
