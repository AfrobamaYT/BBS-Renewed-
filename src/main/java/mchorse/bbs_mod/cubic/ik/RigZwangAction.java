package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.bobj.BOBJBasisMathe;
import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.pose.Transform;

import java.util.List;
import static mchorse.bbs_mod.cubic.ik.RigZwangBasis.*;

/**
 * Blenders Action-Constraint ({@code constraint.cc}: actcon_get_tarmat): Zielkanal zu
 * Action-Zeit, Kurven zur Kanalmatrix im Drehmodus des Knochens.
 */
public final class RigZwangAction
{
    private RigZwangAction()
    {}

    /**
     * {@code actcon_get_tarmat}: aus dem Zielkanal die Action-Zeit, daraus die Kanaele des
     * Besitzers ({@code BKE_pchan_to_mat4} mit seiner Drehreihenfolge).
     */
    public static float[] actionMatrix(Ziel ziel, MapType c, Besitzer b, float zeitErsatz)
    {
        float s;

        if (flag(c, "use_eval_time", null, false))
        {
            s = zahl(c, "eval_time", null, 0F);
        }
        else
        {
            if (ziel == null)
            {
                return null;
            }

            String kanal = text(c, "transform_channel", null, "ROTATION_X");
            int achse = kanal.charAt(kanal.length() - 1) - 'X';
            float[] vec;

            if (kanal.startsWith("ROTATION"))
            {
                vec = RigZerlegung.euler(ziel.matrix(), "XYZ");

                for (int i = 0; i < 3; i++)
                {
                    vec[i] = (float) Math.toDegrees(vec[i]);
                }
            }
            else if (kanal.startsWith("SCALE"))
            {
                vec = RigMathe.groesse(ziel.matrix());
            }
            else
            {
                vec = RigMathe.ort(ziel.matrix());
            }

            float min = zahl(c, "min", null, 0F);
            float max = zahl(c, "max", null, 180F);
            float range = max - min;

            s = range == 0F || achse < 0 || achse > 2 ? 0F : (vec[achse] - min) / range;
        }

        s = Math.max(0F, Math.min(1F, s));

        float start = zahl(c, "frame_start", null, 1F);
        float end = zahl(c, "frame_end", null, 250F);
        float t = s * (end - start) + start;
        ListType kurven = c.getList("action_kurven");
        float[] loc = new float[3];
        float[] eul = new float[3];
        float[] quat = {1F, 0F, 0F, 0F};
        float[] axisAngle = {0F, 0F, 1F, 0F};
        float[] size = {1F, 1F, 1F};

        for (int i = 0; kurven != null && i < kurven.size(); i++)
        {
            MapType k = kurven.getMap(i);
            String feld = k.getString("feld", "");
            int index = k.getInt("index", 0);
            float wert = RigKurve.lesen(k).wertZeit(t);

            switch (feld)
            {
                case "location" -> loc[Math.min(2, index)] = wert;
                case "rotation_euler" -> eul[Math.min(2, index)] = wert;
                case "rotation_quaternion" -> quat[Math.min(3, index)] = wert;
                case "rotation_axis_angle" -> axisAngle[Math.min(3, index)] = wert;
                case "scale" -> size[Math.min(2, index)] = wert;
                default ->
                {}
            }
        }

        /* tchan->rotmode = pchan->rotmode: BLENDERS Modus des Knochens entscheidet, welche
         * Kurven wirken — nicht der BBS-Kanalmodus. */
        float[] rot;
        String modus = b.bone().drehModus == null ? "XYZ" : b.bone().drehModus;

        if ("QUATERNION".equals(modus))
        {
            rot = RigQuaternion.quaternionZuMatrix(RigQuaternion.normalisiereQuat(quat));
        }
        else if ("AXIS_ANGLE".equals(modus))
        {
            float[] achse = {axisAngle[1], axisAngle[2], axisAngle[3]};

            RigVektor.normalisiereV(achse);
            rot = RigAchse.achseWinkelZuMatrix(achse, axisAngle[0]);
        }
        else
        {
            rot = RigEuler.zuMatrix(eul, modus);
        }

        float[] m = RigZerlegung.zusammen(b.bone().verbunden ? new float[3] : loc, rot, size);

        return m;
    }
}
