package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.bobj.BOBJArmature;
import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.pose.Transform;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Snap zwischen FK und IK — der Schritt, ohne den jedes Umschalten das Glied springen laesst.
 *
 * <p>Der Ablauf ist in jeder Rigify-Anleitung derselbe: <b>erst schnappen, dann schalten,
 * dann beides keyframen.</b> Wer nur schaltet, bekommt den klassischen Ein-Bild-Sprung,
 * weil die inaktive Kette irgendwo anders steht als die aktive.
 *
 * <p>⛔ Die Zuordnung FK-Knochen &lt;-&gt; IK-Knochen kommt aus dem Constraint-Graphen
 * ({@link #paare}), nicht aus Namensmustern.
 */
public final class RigSnap
{
    private RigSnap()
    {}

    /**
     * FK-Controls auf die aktuelle IK-Lage setzen. Danach kann auf FK geschaltet werden,
     * ohne dass sich etwas bewegt.
     *
     * @return wie viele Controls geschnappt wurden
     */
    public static int fkAnIk(BOBJArmature armature, RigMechanik mechanik, String schalter, String prop)
    {
        return schnappe(armature, mechanik, schalter, prop, true);
    }

    /** IK-Kette auf die aktuelle FK-Lage setzen — die Gegenrichtung. */
    public static int ikAnFk(BOBJArmature armature, RigMechanik mechanik, String schalter, String prop)
    {
        return schnappe(armature, mechanik, schalter, prop, false);
    }

    /**
     * Die FK/IK-Paare eines Glieds — aus dem Constraint-Graphen, NICHT aus Namensmustern.
     *
     * <p>Rigifys ORG-Kette traegt je Knochen zwei COPY_TRANSFORMS: der erste kopiert von
     * der FK-Kette, der zweite von der IK-Kette, und nur der zweite haengt am IK_FK-Treiber.
     * Wer stattdessen {@code *_fk.L} auf {@code *_ik.L} reimt, hat eine Eigenerfindung
     * gebaut, die beim ersten anders benannten Rig still danebengreift.
     *
     * @return je Eintrag {FK-Knochen, IK-Knochen}, in Auswertungsreihenfolge
     */
    public static List<String[]> paare(RigMechanik mechanik, String schalter, String prop)
    {
        List<String[]> paare = new ArrayList<>();

        for (String bone : mechanik.reihenfolge())
        {
            List<MapType> liste = mechanik.constraintsVon(bone);

            for (MapType c : liste)
            {
                if (!"COPY_TRANSFORMS".equals(c.getString("typ"))
                    || !treibtVon(c.getMap("treiber"), schalter, prop))
                {
                    continue;
                }

                int nr = c.getInt("nr", -1);
                String fk = null;

                for (MapType v : liste)
                {
                    if (v.getInt("nr", -1) == nr - 1 && "COPY_TRANSFORMS".equals(v.getString("typ")))
                    {
                        fk = v.getString("ziel");
                    }
                }

                if (fk != null && c.getString("ziel") != null)
                {
                    paare.add(new String[] {fk, c.getString("ziel")});
                }
            }
        }

        return paare;
    }

    private static boolean treibtVon(MapType treiber, String bone, String prop)
    {
        if (treiber == null)
        {
            return false;
        }

        for (int i = 0; i < treiber.getList("vars").size(); i++)
        {
            MapType v = treiber.getList("vars").getMap(i);

            if (bone.equals(v.getString("bone")) && prop.equals(v.getString("prop")))
            {
                return true;
            }
        }

        return false;
    }

    private static int schnappe(BOBJArmature armature, RigMechanik mechanik,
        String schalter, String prop, boolean fkFolgtIk)
    {
        List<String[]> paare = paare(mechanik, schalter, prop);
        int gezaehlt = 0;

        /* Die Weltlagen ZUERST alle einsammeln. Wer im selben Durchgang liest und
         * schreibt, schnappt Kind-Knochen gegen eine Elternlage, die der vorherige
         * Schritt schon verschoben hat — das Ergebnis sieht plausibel aus und ist falsch. */
        Matrix4f[] ziele = new Matrix4f[paare.size()];

        for (int i = 0; i < paare.size(); i++)
        {
            BOBJBone quelle = armature.bones.get(paare.get(i)[fkFolgtIk ? 1 : 0]);

            ziele[i] = quelle == null ? null : new Matrix4f(quelle.mat);
        }

        for (int i = 0; i < paare.size(); i++)
        {
            BOBJBone ziel = armature.bones.get(paare.get(i)[fkFolgtIk ? 0 : 1]);

            if (ziel != null && ziele[i] != null && setzeWeltlage(ziel, ziele[i]))
            {
                gezaehlt++;
            }

            /* Nach jedem Schritt neu auswerten: der naechste Knochen der Kette haengt
             * an der eben gesetzten Elternlage. */
            armature.zwang.auswerten(armature);
        }

        return gezaehlt;
    }

    /**
     * Setzt die Kanaele eines Knochens so, dass seine Weltmatrix {@code zielWelt} wird.
     *
     * <p>{@link BOBJBone#applyTransformations} multipliziert die Kanaele NACHTRAEGLICH
     * auf die schon fertige Elternlage: {@code welt = basis * T * R * S}. Die Basis ist
     * also aus der aktuellen Weltlage und den aktuellen Kanaelen rueckrechenbar — man
     * braucht keinen zweiten Auswertungsdurchgang mit genullten Kanaelen.
     */
    private static boolean setzeWeltlage(BOBJBone bone, Matrix4f zielWelt)
    {
        /* ⛔ orient ueberschreibt den Drehkanal komplett (siehe kanalDrehung): ein
         * Schreiben auf transform.quat waere dort ein stiller Nulleffekt. Lieber laut
         * nichts tun als leise das Falsche. */
        if (bone.orient != null)
        {
            return false;
        }

        Matrix4f lokalAlt = new Matrix4f().translate(bone.transform.translate);

        bone.kanalDrehung(lokalAlt);

        Matrix4f basis = new Matrix4f(bone.mat).mul(lokalAlt.invert());
        Matrix4f lokalNeu = basis.invert().mul(zielWelt);

        Vector3f ort = lokalNeu.getTranslation(new Vector3f());
        Quaternionf dreh = lokalNeu.getNormalizedRotation(new Quaternionf());

        bone.transform.translate.set(ort);
        /* Quaternion statt Euler: die Ruecktransformation der ZYX-Kanaele haette eine
         * Ordnungs-Annahme gebraucht, und Rigifys FK-Controls stehen in Blender ohnehin
         * auf Quaternion. */
        bone.transform.rotationMode = Transform.RotationMode.QUATERNION;
        bone.transform.quat.set(dreh);

        return true;
    }
}
