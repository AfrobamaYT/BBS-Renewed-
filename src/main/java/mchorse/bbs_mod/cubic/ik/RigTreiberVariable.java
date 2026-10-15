package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.bobj.BOBJBBone;
import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.pose.Transform;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Die Treiber-Variablen ({@code driver_get_variable_value}): SINGLE_PROP aus Custom Property,
 * Pose-Kanal oder B-Bone-Kanal ({@code dtar_get_prop_val}); TRANSFORMS, ROTATION_DIFF und
 * LOC_DIFF rechnet {@link RigTreiberLage}. Unbekanntes zaehlt der Treiber, nie still 0.
 */
final class RigTreiberVariable
{
    private RigTreiberVariable()
    {}

    /** {@code driver_get_variable_value}. */
    static float wert(RigTreiber t, MapType v)
    {
        Beschreibung daten = t.variable(v);
        String typ = daten.typ;
        MapType z0 = daten.z0;
        MapType z1 = daten.z1;

        switch (typ == null ? "SINGLE_PROP" : typ)
        {
            case "TRANSFORMS":
                return RigTreiberLage.transformKanal(t, z0);
            case "ROTATION_DIFF":
                return RigTreiberLage.drehDifferenz(t, z0, z1);
            case "LOC_DIFF":
                return RigTreiberLage.ortDifferenz(t, z0, z1);
            case "CONTEXT_PROP":
                t.zaehle("CONTEXT_PROP:" + daten.pfad);

                return 0F;
            default:
                return eigenschaft(t, daten);
        }
    }

    /** Nur die statische Beschreibung einer rig.json-Variable; Pose und Custom Properties
     *  werden weiterhin bei JEDEM Aufruf aus dem aktuellen Actor gelesen. */
    static final class Beschreibung
    {
        final String typ;
        final MapType z0;
        final MapType z1;
        final String bone;
        final String prop;
        final String kanal;
        final String pfad;
        final String bboneFeld;
        final int kanalAchse;

        Beschreibung(MapType v)
        {
            this.typ = v.getString("typ", "SINGLE_PROP");
            ListType ziele = v.getList("ziele");
            this.z0 = ziele != null && ziele.size() > 0 ? ziele.getMap(0) : v;
            this.z1 = ziele != null && ziele.size() > 1 ? ziele.getMap(1) : null;
            this.bone = this.z0.getString("bone");
            this.prop = this.z0.getString("prop");
            this.kanal = this.z0.getString("kanal");
            this.pfad = this.z0.getString("pfad", "");
            int bb = this.pfad == null ? -1 : this.pfad.indexOf(".bbone_");
            this.bboneFeld = bb < 0 ? null : this.pfad.substring(bb + 1);
            this.kanalAchse = this.z0.getInt("kanal_achse", 0);
        }
    }

    /** SINGLE_PROP ({@code dtar_get_prop_val}): Custom Property, Pose-Kanal oder B-Bone-Kanal. */
    private static float eigenschaft(RigTreiber t, Beschreibung daten)
    {
        String bone = daten.bone;
        String prop = daten.prop;
        String kanal = daten.kanal;
        String pfad = daten.pfad;

        if (prop != null && !prop.isEmpty())
        {
            if (bone == null || bone.isEmpty())
            {
                t.zaehle("SINGLE_PROP:" + pfad);

                return 0F;
            }

            if (!t.regler.hat(bone, prop))
            {
                t.zaehle("regler:" + bone + "." + prop);

                return 0F;
            }

            return t.regler.wertVon(bone, prop, 0F);
        }

        BOBJBone b = t.bone(bone);

        if (b == null)
        {
            t.zaehle("SINGLE_PROP:" + pfad);

            return 0F;
        }

        if (kanal != null && !kanal.isEmpty())
        {
            return kanalwert(b, kanal, daten.kanalAchse);
        }

        if (daten.bboneFeld != null)
        {
            return bboneWert(b, daten.bboneFeld);
        }

        t.zaehle("SINGLE_PROP:" + pfad);

        return 0F;
    }

    /** Ein Pose-Kanal des Knochens (location, scale, rotation_euler, rotation_quaternion). */
    static float kanalwert(BOBJBone b, String kanal, int achse)
    {
        int i = Math.max(0, Math.min(2, achse));

        switch (kanal)
        {
            case "location":
                return b.transform.translate.get(i);
            case "scale":
                return b.transform.scale.get(i);
            case "rotation_quaternion":
            {
                Quaternionf q = b.transform.rotationMode == Transform.RotationMode.QUATERNION
                    ? b.transform.quat : new Quaternionf();
                int w = Math.max(0, Math.min(3, achse));

                return w == 0 ? q.w : w == 1 ? q.x : w == 2 ? q.y : q.z;
            }
            default:
                return b.transform.rotate.get(i);
        }
    }

    /** {@code pose.bones[].bbone_*}: die Pose-Ebene der B-Bone-Kanaele. */
    static float bboneWert(BOBJBone b, String feld)
    {
        BOBJBBone d = b.bbone;

        if (d == null)
        {
            return 0F;
        }

        int klammer = feld.indexOf('[');
        int index = 0;

        if (klammer >= 0)
        {
            try
            {
                index = Integer.parseInt(feld.substring(klammer + 1, feld.indexOf(']')));
            }
            catch (RuntimeException e)
            {
                index = 0;
            }

            feld = feld.substring(0, klammer);
        }

        index = Math.max(0, Math.min(2, index));

        return switch (feld)
        {
            case "bbone_easein" -> d.poseEase[0];
            case "bbone_easeout" -> d.poseEase[1];
            case "bbone_rollin" -> d.poseRoll[0];
            case "bbone_rollout" -> d.poseRoll[1];
            case "bbone_curveinx" -> d.poseKurveIn[0];
            case "bbone_curveinz" -> d.poseKurveIn[1];
            case "bbone_curveoutx" -> d.poseKurveOut[0];
            case "bbone_curveoutz" -> d.poseKurveOut[1];
            case "bbone_scalein" -> d.poseSkalaIn[index];
            case "bbone_scaleout" -> d.poseSkalaOut[index];
            default -> 0F;
        };
    }

    /** {@code BKE_pchan_to_mat4}: die Kanalmatrix — Ort (ausser verbunden), Drehung, Groesse. */
    static float[] kanalMatrix(BOBJBone b)
    {
        Matrix4f m = new Matrix4f();

        if (!b.verbunden)
        {
            m.translate(b.transform.translate);
        }

        m.rotate(b.evaluatedRotation());
        m.scale(b.transform.scale);

        return m.get(new float[16]);
    }
}
