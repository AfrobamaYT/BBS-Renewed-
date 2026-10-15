package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.bobj.BOBJBasisMathe;
import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.pose.Transform;

import java.util.List;
import static mchorse.bbs_mod.cubic.ik.RigZwangBasis.*;

/**
 * Blenders Transformation-Constraint ({@code constraint.cc}: transform_evaluate) samt
 * {@code BKE_driver_target_matrix_to_rot_channels}, das auch die Treiber nutzen.
 */
public final class RigZwangTransform
{
    private RigZwangTransform()
    {}

    /**
     * {@code BKE_driver_target_matrix_to_rot_channels}: Drehkanaele einer Matrix als
     * (w|Winkel, x, y, z) — Euler in {@code rotationMode} (AUTO = {@code autoOrder}),
     * Quaternion oder Swing/Twist.
     */
    public static float[] rotationsKanaele(float[] mat, String autoOrder, String rotationMode, int kanal, boolean winkel)
    {
        float[] buf = new float[4];
        String mode = rotationMode == null || rotationMode.isEmpty() ? "AUTO" : rotationMode;

        if ("AUTO".equals(mode) || "XYZ".equals(mode) || "XZY".equals(mode) || "YXZ".equals(mode)
            || "YZX".equals(mode) || "ZXY".equals(mode) || "ZYX".equals(mode))
        {
            float[] e = RigZerlegung.euler(mat, "AUTO".equals(mode) ? autoOrder : mode);

            System.arraycopy(e, 0, buf, 1, 3);
        }
        else if ("QUATERNION".equals(mode))
        {
            float[] q = RigQuaternion.quaternion(mat);

            System.arraycopy(q, 0, buf, 0, 4);

            if (winkel)
            {
                RigAchse.quaternionZuWinkeln(buf, kanal);
            }
        }
        else if (mode.startsWith("SWING_TWIST_"))
        {
            int axis = mode.charAt(mode.length() - 1) - 'X';
            float[] raw = RigQuaternion.quaternion(mat);
            float twist;

            if (kanal == axis + 1)
            {
                twist = RigAchse.twist(raw, axis);
            }
            else
            {
                twist = RigAchse.twist(raw, axis);

                float[] sw = RigAchse.swing(raw, axis);

                System.arraycopy(sw, 0, buf, 0, 4);
                RigAchse.quaternionZuWinkeln(buf, kanal);
            }

            buf[axis + 1] = twist;
        }

        return buf;
    }

    /** {@code transform_evaluate}. */
    public static void transformation(float[] cob, Ziel ziel, MapType c, Besitzer b)
    {
        String from = text(c, "map_from", "von", "LOCATION");
        String to = text(c, "map_to", "nach", "LOCATION");
        float[] dvec;
        String suffixVon = "ROTATION".equals(from) ? "_rot" : "SCALE".equals(from) ? "_scale" : "";
        String suffixNach = "ROTATION".equals(to) ? "_rot" : "SCALE".equals(to) ? "_scale" : "";

        switch (from)
        {
            case "SCALE" ->
            {
                dvec = RigMathe.groesse(ziel.matrix());

                if (RigMathe.negativ(ziel.matrix()))
                {
                    dvec = RigVektor.mal(dvec, -1F);
                }
            }
            case "ROTATION" ->
            {
                float[] buf = rotationsKanaele(ziel.matrix(), b.rotOrder(), text(c, "from_rotation_mode", null, "AUTO"), -1, true);

                dvec = new float[] {buf[1], buf[2], buf[3]};
            }
            default -> dvec = RigMathe.ort(ziel.matrix());
        }

        String rotOrder = b.rotOrder();

        if ("ROTATION".equals(to))
        {
            rotOrder = ordnung(c, "to_euler_order", b);
        }

        float[] rot = new float[9];
        float[] size = new float[3];
        float[] loc = RigZerlegung.zerlege(cob, rot, size);
        String[] a = {"x", "y", "z"};
        float[] sval = new float[3];
        boolean expo = flag(c, "use_motion_extrapolate", null, false);

        for (int i = 0; i < 3; i++)
        {
            float min = zahl(c, "from_min_" + a[i] + suffixVon, "from_min_" + a[i], 0F);
            float max = zahl(c, "from_max_" + a[i] + suffixVon, "from_max_" + a[i], 0F);
            float v = dvec[i];

            if (!expo)
            {
                v = Math.max(min, Math.min(max, v));
            }

            sval[i] = max - min != 0F ? (v - min) / (max - min) : 0F;
        }

        int[] map = new int[3];

        for (int i = 0; i < 3; i++)
        {
            map[i] = spurIndex(text(c, "map_to_" + a[i] + "_from", "quelle_" + a[i], a[i].toUpperCase()));
        }

        float[] neu = new float[3];

        for (int i = 0; i < 3; i++)
        {
            float min = zahl(c, "to_min_" + a[i] + suffixNach, "to_min_" + a[i], 0F);
            float max = zahl(c, "to_max_" + a[i] + suffixNach, "to_max_" + a[i], 0F);

            neu[i] = min + sval[map[i]] * (max - min);
        }

        switch (to)
        {
            case "SCALE" ->
            {
                if ("MULTIPLY".equals(text(c, "mix_mode_scale", null, "REPLACE")))
                {
                    for (int i = 0; i < 3; i++)
                    {
                        size[i] *= neu[i];
                    }
                }
                else
                {
                    size = neu;
                }
            }
            case "ROTATION" ->
            {
                switch (text(c, "mix_mode_rot", null, "ADD"))
                {
                    case "REPLACE" -> rot = RigEuler.zuMatrix(neu, rotOrder);
                    case "BEFORE" -> rot = RigMatrix3.mul3(RigEuler.zuMatrix(neu, rotOrder), rot);
                    case "AFTER" -> rot = RigMatrix3.mul3(rot, RigEuler.zuMatrix(neu, rotOrder));
                    default ->
                    {
                        float[] old = RigEuler.ausMatrix(RigMatrix3.normalisiere3(rot), rotOrder);

                        for (int i = 0; i < 3; i++)
                        {
                            neu[i] += old[i];
                        }

                        rot = RigEuler.zuMatrix(neu, rotOrder);
                    }
                }
            }
            default ->
            {
                if ("REPLACE".equals(text(c, "mix_mode", null, "ADD")))
                {
                    loc = neu;
                }
                else
                {
                    loc = RigVektor.summe(loc, neu);
                }
            }
        }

        System.arraycopy(RigZerlegung.zusammen(loc, rot, size), 0, cob, 0, 16);
    }
}
