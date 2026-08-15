package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.bobj.BOBJArmature;
import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;
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
    public static int fkAnIk(BOBJArmature armature, RigMechanik mechanik, Pose pose,
        String schalter, String prop)
    {
        return schnappe(armature, mechanik, pose, schalter, prop, true);
    }

    /** IK-Kette auf die aktuelle FK-Lage setzen — die Gegenrichtung. */
    public static int ikAnFk(BOBJArmature armature, RigMechanik mechanik, Pose pose,
        String schalter, String prop)
    {
        return schnappe(armature, mechanik, pose, schalter, prop, false);
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

    /**
     * Der Reglerwert, bei dem die IK-Quelle ABGESCHALTET ist — also FK gilt.
     *
     * <p>⛔ Nicht Rigifys {@code IK_FK=1} annehmen: die Richtung steckt im
     * GENERATOR-Polynom der F-Kurve, und ein Rig kann sie umgekehrt ablegen. Hier wird
     * sie gemessen — Regler kurz auf {@code max} stellen, Treiber auswerten, Regler
     * zurueck. {@code Float.NaN} heisst: an diesem Regler haengt keine FK/IK-Umschaltung.
     */
    public static float fkWert(RigMechanik mechanik, String schalter, String prop,
        float min, float max)
    {
        for (String bone : mechanik.reihenfolge())
        {
            for (MapType c : mechanik.constraintsVon(bone))
            {
                if (!"COPY_TRANSFORMS".equals(c.getString("typ"))
                    || !treibtVon(c.getMap("treiber"), schalter, prop))
                {
                    continue;
                }

                float alt = mechanik.treiber.regler.wertVon(schalter, prop, min);

                mechanik.treiber.regler.setze(schalter, prop, max);

                float beiMax = mechanik.treiber.wert(c.getMap("treiber"), 1F);

                mechanik.treiber.regler.setze(schalter, prop, alt);

                return beiMax < 0.5F ? max : min;
            }
        }

        return Float.NaN;
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

    private static int schnappe(BOBJArmature armature, RigMechanik mechanik, Pose pose,
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

            if (ziel != null && ziele[i] != null
                && setzeWeltlage(ziel, paare.get(i)[fkFolgtIk ? 0 : 1], ziele[i], pose))
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
     * Schreibt die Pose so, dass die Weltmatrix des Knochens {@code zielWelt} wird.
     *
     * <p>⛔ Geschrieben wird die POSE, nicht {@code bone.transform}. Zwei gemessene Gruende
     * (2026-08-14, Instanz 2): {@code ModelFormRenderer} ruft JEDE Frame
     * {@code BOBJModel.applyPose} und ueberschreibt damit jeden Kanal-Schreibvorgang; und
     * sobald eine Pose lebt, ist {@code bone.orient} gesetzt — {@code orient} uebersteuert
     * den Drehkanal komplett, ein Schreiben dorthin waere ein stiller Nulleffekt.
     *
     * <p>Die Pose ADDIERT zwar auf die Kanaele, die Basis ist aber je Frame die Ruhelage:
     * gemessen kam ein Pose-{@code translate} von (0, 0.5, 0) unveraendert im Kanal an.
     * Darum darf die Ziel-Lokalmatrix direkt hinein, ohne Differenzrechnung.
     *
     * <p>{@link BOBJBone#applyTransformations} multipliziert die Kanaele NACHTRAEGLICH auf
     * die fertige Elternlage ({@code welt = basis * T * R * S}); die Basis ist deshalb aus
     * der aktuellen Weltlage und der aktuell WIRKENDEN lokalen Drehung rueckrechenbar —
     * {@link BOBJBone#kanalDrehung} liefert die inklusive {@code orient}.
     */
    private static boolean setzeWeltlage(BOBJBone bone, String name, Matrix4f zielWelt, Pose pose)
    {
        if (pose == null)
        {
            return false;
        }

        Matrix4f lokalAlt = new Matrix4f().translate(bone.transform.translate);

        bone.kanalDrehung(lokalAlt);

        Matrix4f basis = new Matrix4f(bone.mat).mul(lokalAlt.invert());
        Matrix4f lokalNeu = basis.invert().mul(zielWelt);

        PoseTransform pt = pose.get(name);

        pt.translate.set(lokalNeu.getTranslation(new Vector3f()));
        /* Quaternion statt Euler: die Ruecktransformation der ZYX-Kanaele haette eine
         * Ordnungs-Annahme gebraucht, und Rigifys FK-Controls stehen in Blender ohnehin
         * auf Quaternion. applyPose setzt daraus selbst das passende orient. */
        pt.rotationMode = Transform.RotationMode.QUATERNION;
        pt.quat.set(lokalNeu.getNormalizedRotation(new Quaternionf()));

        return true;
    }
}
