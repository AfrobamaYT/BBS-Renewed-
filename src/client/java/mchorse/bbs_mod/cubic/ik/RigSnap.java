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
        return fkAnIk(armature, mechanik, pose, schalter, prop, null);
    }

    /** Mit {@code neuAuswerten} — siehe {@link #schnappe}. */
    public static int fkAnIk(BOBJArmature armature, RigMechanik mechanik, Pose pose,
        String schalter, String prop, Runnable neuAuswerten)
    {
        return schnappe(armature, mechanik, pose, schalter, prop, true, neuAuswerten);
    }

    /** IK-Kette auf die aktuelle FK-Lage setzen — die Gegenrichtung. */
    public static int ikAnFk(BOBJArmature armature, RigMechanik mechanik, Pose pose,
        String schalter, String prop)
    {
        return ikAnFk(armature, mechanik, pose, schalter, prop, null);
    }

    /** Mit {@code neuAuswerten} — siehe {@link #schnappe}. */
    public static int ikAnFk(BOBJArmature armature, RigMechanik mechanik, Pose pose,
        String schalter, String prop, Runnable neuAuswerten)
    {
        return schnappe(armature, mechanik, pose, schalter, prop, false, neuAuswerten);
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
     * Die Knochen, auf die ein Snap dieser Richtung WIRKLICH schreibt.
     *
     * <p>⛔ Nicht die rohe Paar-Liste: der Snap leitet auf den schreibbaren Control um
     * ({@link #schreibbar}), und in Richtung IK &lt;- FK kommt der Schwenk dazu — ohne
     * Pol der Basis-Control (steht schon in der Liste), mit Pol dessen Pol-Control, der
     * in keinem Paar vorkommt. Wer die Ergebnisse zurueckkopiert und diesen einen
     * vergisst, verliert genau den Schwenk und merkt es nicht.
     */
    public static List<String> geschrieben(RigMechanik mechanik, String schalter, String prop,
        boolean nachFk)
    {
        List<String[]> paare = paare(mechanik, schalter, prop);
        List<String> raus = new ArrayList<>();

        for (String[] p : paare)
        {
            raus.add(schreibbar(mechanik, p[nachFk ? 0 : 1]));
        }

        if (!nachFk && paare.size() >= 2)
        {
            String pol = mechanik.polVon(paare.get(1)[1]);

            if (pol != null && !raus.contains(pol))
            {
                raus.add(pol);
            }
        }

        return raus;
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

    /**
     * ⛔ {@code neuAuswerten} ist NICHT optional, sondern der Unterschied zwischen
     * "sieht gerechnet aus" und "stimmt".
     *
     * <p>Die Schleife setzte frueher nach jedem Paar {@code armature.zwang.auswerten(...)}
     * ab, mit dem Kommentar, der naechste Knochen brauche die eben gesetzte Elternlage.
     * Das rechnet aber nur die CONSTRAINTS auf den vorhandenen Matrizen neu — die
     * geschriebene POSE wirkt erst, wenn {@code applyPose} wieder laeuft. Control 2 und 3
     * rechneten also gegen einen veralteten Stand. Gemessen am Arm: die IK-Kette landete
     * 0.163 bzw. 0.279 neben der FK-Kette, obwohl "3 Controls geschnappt" gemeldet wurde.
     *
     * <p>Der Aufrufer reicht deshalb den Weg des Renderers herein (Ruhepose, Form-Pose,
     * {@code setupMatrices}). Ohne ihn bleibt es beim alten, schwaecheren Verhalten.
     */
    private static int schnappe(BOBJArmature armature, RigMechanik mechanik, Pose pose,
        String schalter, String prop, boolean fkFolgtIk, Runnable neuAuswerten)
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
            /* ⛔ Auf den SCHREIBBAREN Control umleiten — siehe {@link #schreibbar}. */
            String name = schreibbar(mechanik, paare.get(i)[fkFolgtIk ? 0 : 1]);
            BOBJBone ziel = armature.bones.get(name);

            if (ziel != null && ziele[i] != null
                && setzeWeltlage(ziel, name, ziele[i], pose))
            {
                gezaehlt++;
            }

            /* Nach jedem Schritt neu auswerten: der naechste Knochen der Kette haengt
             * an der eben gesetzten Elternlage. */
            if (neuAuswerten != null)
            {
                neuAuswerten.run();
            }
            else
            {
                armature.zwang.auswerten(armature);
            }
        }

        /* ⛔ Der SCHWENK zum Schluss — Blenders `compute_base_rotation` steht in
         * `apply_frame_state` ebenfalls NACH dem Setzen von Spitze und Basis.
         * Wurzel und Spitze sitzen danach exakt, die Ebene der Kette noch nicht:
         * gemessen blieben Ellbogen 0.03618 und Knie 0.06667 stehen. Nur in dieser
         * Richtung — beim Weg FK <- IK stellt die FK-Kette ihre Gelenke selbst. */
        if (!fkFolgtIk && paare.size() >= 2 && neuAuswerten != null
            && ziele[0] != null && ziele[1] != null)
        {
            RigSchwenk.schwenke(armature, mechanik, pose,
                schreibbar(mechanik, paare.get(0)[1]), paare.get(0)[1], paare.get(1)[1],
                ziele[0], ziele[1], neuAuswerten);
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
    /**
     * Der Knochen, auf den man WIRKLICH schreiben kann.
     *
     * <p>⛔ Gemessen 2026-08-15: der Snap schrieb auf {@code MCH-upper_arm_ik_target.R},
     * und der traegt ein COPY_LOCATION von {@code hand_ik.R} mit Staerke 1 — der Wert war
     * im naechsten Bild weg. Ergebnis: die IK-Kette landete 0.189 neben der FK-Kette,
     * obwohl der Snap "3 Controls geschnappt" meldete. Ein Schreibvorgang, den ein
     * Constraint sofort ueberschreibt, ist derselbe stille Fehlschlag wie ein Schieber,
     * der nichts bewegt.
     *
     * <p>Aufgeloest wird ueber den Constraint-Graphen, nicht ueber Namen: traegt der
     * Knochen ein COPY_LOCATION oder COPY_TRANSFORMS mit voller Staerke, ist dessen QUELLE
     * der schreibbare Control. Mehrfach, falls die Kette laenger ist.
     */
    public static String schreibbar(RigMechanik mechanik, String bone)
    {
        String aktuell = bone;

        for (int hop = 0; hop < 4; hop++)
        {
            String quelle = null;

            for (MapType c : mechanik.constraintsVon(aktuell))
            {
                String typ = c.getString("typ");

                if (("COPY_LOCATION".equals(typ) || "COPY_TRANSFORMS".equals(typ))
                    && c.getFloat("einfluss", 1F) > 0.999F
                    && c.getString("ziel") != null && !c.getString("ziel").isEmpty())
                {
                    quelle = c.getString("ziel");
                }
            }

            if (quelle == null || quelle.equals(aktuell))
            {
                return aktuell;
            }

            aktuell = quelle;
        }

        return aktuell;
    }

    private static boolean setzeWeltlage(BOBJBone bone, String name, Matrix4f zielWelt, Pose pose)
    {
        return setze(bone, name, zielWelt, zielWelt.getTranslation(new Vector3f()), pose);
    }

    /**
     * Nur den ORT setzen, die Drehung unangetastet lassen — Blenders
     * {@code set_pose_translation}, wie {@link RigSchwenk} den Pol-Control setzt. Rigify
     * keyt ihn ausdruecklich mit {@code no_rot}: der Pol bestimmt die Ebene ueber seine
     * LAGE, seine Eigendrehung gehoert dem Animator.
     */
    static boolean setzeWeltOrt(BOBJBone bone, String name, Vector3f zielOrt, Pose pose)
    {
        return setze(bone, name, null, zielOrt, pose);
    }

    /**
     * @param zielWelt {@code null} = nur den Ort setzen; die Drehung bleibt dann auch
     *                 waehrend der MESSUNG stehen, damit die gemessene Abbildung die ist,
     *                 die gerade wirklich gilt
     */
    private static boolean setze(BOBJBone bone, String name, Matrix4f zielWelt,
        Vector3f zielOrt, Pose pose)
    {
        if (pose == null)
        {
            return false;
        }

        /* ⛔ Die Vorwaertsabbildung wird GEMESSEN, nicht ein zweites Mal hingeschrieben.
         *
         * BBS hat zwei Wege vom Kanal zur Weltmatrix. Der Normalfall ist
         * {@code welt = elter.mat * relBoneMat * T(translate) * R} — den kann man von Hand
         * umkehren, und genau das tat diese Methode. 52 der 706 Rigify-Knochen laufen aber
         * ueber {@code BOBJElternTransform}, weil ihre Elternuebergabe abweicht
         * (Blenders use_local_location / inherit_scale): dort kommt die ORTSSPALTE aus
         * einer eigenen Basis und die Drehung aus einer anderen. Die Handumkehrung ist
         * dort falsch — gemessen an `hand_ik.R` (elternraumVerschiebung=true): der Snap
         * schrieb den richtigen BETRAG (0.1886 = Abstand zum FK-Ziel) auf die falsche
         * Achse, der Knochen wanderte 0.19 in Welt-Y statt zum Ziel. `upper_arm_ik.R`
         * ohne diese Flags landete im selben Lauf exakt (0.00000).
         *
         * Darum: die Basis am Knochen ABFRAGEN. Kanaele auf neutral, Weltmatrix lesen —
         * das ist die Basis; je Achse eine Einheit Verschiebung, die Differenz ist die
         * Ortsspalte. Das kehrt um, was der Knochen WIRKLICH rechnet, auf beiden Wegen
         * und auch nach kuenftigen Aenderungen. */
        Vector3f altT = new Vector3f(bone.transform.translate);
        Transform.RotationMode altM = bone.transform.rotationMode;
        Quaternionf altQ = new Quaternionf(bone.transform.quat);
        Vector3f altR = new Vector3f(bone.transform.rotate);
        Matrix4f altMat = new Matrix4f(bone.mat);
        Matrix4f altOrigin = new Matrix4f(bone.originMat);

        Matrix4f basis;
        org.joml.Matrix3f ortSpalten = new org.joml.Matrix3f();

        try
        {
            bone.transform.translate.set(0F, 0F, 0F);

            /* ⛔ Die Drehung nur neutralisieren, wenn sie auch GESETZT wird. Beim reinen
             * Ortssetzen muss die Abbildung mit der Drehung gemessen werden, die gerade
             * gilt — sonst misst man einen Zustand, in dem der Knochen nie steht. */
            if (zielWelt != null)
            {
                bone.transform.rotationMode = Transform.RotationMode.QUATERNION;
                bone.transform.quat.identity();
            }

            basis = bone.computeMatrix(new Matrix4f());

            Vector3f null0 = basis.getTranslation(new Vector3f());

            for (int achse = 0; achse < 3; achse++)
            {
                bone.transform.translate.set(achse == 0 ? 1F : 0F, achse == 1 ? 1F : 0F,
                    achse == 2 ? 1F : 0F);

                Vector3f t = bone.computeMatrix(new Matrix4f()).getTranslation(new Vector3f());

                ortSpalten.setColumn(achse, t.sub(null0));
            }

            if (Math.abs(ortSpalten.determinant()) < 1.0E-9F)
            {
                return false;
            }

            Vector3f loc = new org.joml.Matrix3f(ortSpalten).invert()
                .transform(new Vector3f(zielOrt).sub(null0));

            PoseTransform pt = pose.get(name);

            pt.translate.set(loc);

            if (zielWelt != null)
            {
                Quaternionf dreh = new org.joml.Matrix3f(basis.get3x3(new org.joml.Matrix3f()))
                    .invert().mul(zielWelt.get3x3(new org.joml.Matrix3f()))
                    .getNormalizedRotation(new Quaternionf());

                /* Quaternion statt Euler: die Ruecktransformation der ZYX-Kanaele haette eine
                 * Ordnungs-Annahme gebraucht, und Rigifys Controls stehen in Blender ohnehin
                 * auf Quaternion. applyPose setzt daraus selbst das passende orient. */
                pt.rotationMode = Transform.RotationMode.QUATERNION;
                pt.quat.set(dreh);
            }
        }
        finally
        {
            bone.transform.translate.set(altT);
            bone.transform.rotationMode = altM;
            bone.transform.quat.set(altQ);
            bone.transform.rotate.set(altR);
            bone.mat.set(altMat);
            bone.originMat.set(altOrigin);
        }

        return true;
    }
}
