package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.pose.Transform;

/**
 * Lage-Variablen der Treiber: TRANSFORMS ({@code dvar_eval_transChan}) in WORLD/TRANSFORM/LOCAL
 * SPACE samt Drehkanaelen, ROTATION_DIFF ({@code dvar_eval_rotDiff}) und LOC_DIFF
 * ({@code dvar_eval_locDiff}) — alles in Blenders Raeumen ueber {@link RigRahmen}.
 */
final class RigTreiberLage
{
    private RigTreiberLage()
    {}

    /** TRANSFORMS ({@code dvar_eval_transChan}). */
    static float transformKanal(RigTreiber t, MapType z)
    {
        BOBJBone b = t.bone(z.getString("bone"));

        if (b == null)
        {
            t.zaehle("TRANSFORMS-ohne-knochen:" + z.getString("bone"));

            return 0F;
        }

        String raum = z.getString("raum", "WORLD_SPACE");
        float[] mat;

        if ("LOCAL_SPACE".equals(raum))
        {
            /* "just like how the constraints do it": pose_mat nach LOCAL gewandelt. */
            mat = RigRaum.poseZuKnochen(t.rahmen(), b, t.rahmen().poseMat(b));
        }
        else if ("TRANSFORM_SPACE".equals(raum))
        {
            mat = RigTreiberVariable.kanalMatrix(b);
        }
        else
        {
            mat = t.rahmen().weltMat(b);
        }

        String wandler = z.getString("wandler", "LOC_X");

        switch (wandler == null ? "LOC_X" : wandler)
        {
            case "LOC_X":
                return mat[12];
            case "LOC_Y":
                return mat[13];
            case "LOC_Z":
                return mat[14];
            case "SCALE_X":
                return RigVektor.laenge(RigMathe.spalte(mat, 0));
            case "SCALE_Y":
                return RigVektor.laenge(RigMathe.spalte(mat, 1));
            case "SCALE_Z":
                return RigVektor.laenge(RigMathe.spalte(mat, 2));
            case "SCALE_AVG":
                return (float) Math.cbrt(RigMathe.volumen(mat));
            case "ROT_W":
            case "ROT_X":
            case "ROT_Y":
            case "ROT_Z":
            {
                int kanal = "ROT_W".equals(wandler) ? 0 : wandler.charAt(4) - 'X' + 1;
                String modus = z.getString("dreh_modus", "AUTO");
                boolean eulers = b.transform.rotationMode != Transform.RotationMode.QUATERNION;
                float[] buf = RigZwangTransform.rotationsKanaele(mat, RigTreiber.ordnung(b), modus, kanal, false);

                if (eulers && (modus == null || modus.isEmpty() || "AUTO".equals(modus)))
                {
                    float[] e = {buf[1], buf[2], buf[3]};

                    RigEuler.vertraeglich(e, new float[] {b.transform.rotate.x, b.transform.rotate.y, b.transform.rotate.z});
                    System.arraycopy(e, 0, buf, 1, 3);
                }

                return buf[kanal];
            }
            default:
                t.zaehle("wandler:" + wandler);

                return 0F;
        }
    }

    /** Pose-Matrix eines Ziels (Knochen) bzw. die Objektmatrix — {@code dvar_eval_rotDiff}. */
    private static float[] zielPose(RigTreiber t, MapType z, String art)
    {
        if (z == null)
        {
            t.zaehle(art + ":ziel-fehlt");

            return null;
        }

        String name = z.getString("bone");

        if (name == null || name.isEmpty())
        {
            return RigMathe.kopie(t.rahmen().welt);
        }

        BOBJBone b = t.bone(name);

        if (b == null)
        {
            t.zaehle(art + "-ohne-knochen:" + name);

            return null;
        }

        return t.rahmen().poseMat(b);
    }

    /** ROTATION_DIFF: Winkel zwischen zwei Pose-Matrizen (Blender rechnet hier im Pose-Raum). */
    static float drehDifferenz(RigTreiber t, MapType z0, MapType z1)
    {
        float[] m0 = zielPose(t, z0, "ROTATION_DIFF");
        float[] m1 = zielPose(t, z1, "ROTATION_DIFF");

        if (m0 == null || m1 == null)
        {
            return 0F;
        }

        float[] q1 = RigQuaternion.konjugiert(RigQuaternion.quaternion(m0));
        float[] q2 = RigQuaternion.quaternion(m1);
        float[] q = RigQuaternion.mulQuat(q1, q2);
        float angle = Math.abs(2F * RigAchse.sicherAcos(q[0]));

        return angle > (float) Math.PI ? (float) (2D * Math.PI) - angle : angle;
    }

    /** LOC_DIFF ({@code dvar_eval_locDiff}). */
    static float ortDifferenz(RigTreiber t, MapType z0, MapType z1)
    {
        float[][] loc = new float[2][];

        for (int i = 0; i < 2; i++)
        {
            MapType z = i == 0 ? z0 : z1;

            if (z == null)
            {
                t.zaehle("LOC_DIFF:ziel-fehlt");

                return 0F;
            }

            String name = z.getString("bone");
            String raum = z.getString("raum", "WORLD_SPACE");

            if (name == null || name.isEmpty())
            {
                if (!"WORLD_SPACE".equals(raum))
                {
                    t.zaehle("LOC_DIFF-objekt:" + raum);
                }

                loc[i] = RigMathe.ort(t.rahmen().welt);

                continue;
            }

            BOBJBone b = t.bone(name);

            if (b == null)
            {
                t.zaehle("LOC_DIFF-ohne-knochen:" + name);

                return 0F;
            }

            if ("LOCAL_SPACE".equals(raum))
            {
                loc[i] = RigMathe.ort(RigRaum.poseZuKnochen(t.rahmen(), b, t.rahmen().poseMat(b)));
            }
            else if ("TRANSFORM_SPACE".equals(raum))
            {
                loc[i] = new float[] {b.transform.translate.x, b.transform.translate.y, b.transform.translate.z};
            }
            else
            {
                loc[i] = RigMathe.ort(t.rahmen().weltMat(b));
            }
        }

        return RigVektor.laenge(RigVektor.differenz(loc[0], loc[1]));
    }
}
