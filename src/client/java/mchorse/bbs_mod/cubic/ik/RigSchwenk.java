package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.bobj.BOBJArmature;
import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import mchorse.bbs_mod.utils.pose.Transform;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.function.DoubleUnaryOperator;

/**
 * Der SCHWENK des mittleren Gelenks beim Snap IK &lt;- FK — Rigifys
 * {@code compute_base_rotation}.
 *
 * <p>Wurzel und Spitze setzt der Paar-Snap exakt (gemessen 0.00000/0.00000); was
 * danach stehen blieb, war allein die Drehung der Kette um die Linie Wurzel-Spitze:
 * Ellbogen 0.03618, Knie 0.06667 (2026-08-16, Instanz 2). Die Ebene bestimmt in
 * Blender entweder der Pol-Control oder — ohne Pol — die Eigendrehung des
 * Basis-Controls, und Rigify SUCHT den Winkel, statt ihn zu rechnen: Klammer in
 * Achtelbogen-Schritten, dann Drittelsuche bis auf ein Grad.
 *
 * <p>⛔ ABWEICHUNG VON RIGIFY, gemessen begruendet: Rigify vergleicht {@code bone.vector}
 * des ersten IK-Knochens mit der Y-Spalte der FK-Matrix. In BBS zeigt die Y-Spalte des
 * geloesten Kettenwurzel-Knochens NICHT laengs seines eigenen Stuecks — gemessen an
 * {@code upper_arm_ik.R}: Y-Spalte gegen die eigene Strecke Kopf-&gt;Ellbogen 0.7953,
 * waehrend die Strecke selbst mit der FK-Strecke 0.9999 deckungsgleich ist. Wer auf die
 * Spalte optimiert, findet das Maximum an der falschen Stelle (gemessen: Bestwert bei
 * Winkel 0, waehrend das Gelenk erst bei -0.26 trifft) und macht den Rest GROESSER
 * (0.03618 -&gt; 0.05303). Verglichen werden deshalb die STRECKEN Kopf-&gt;Kopf, also
 * genau die Groesse, die Blenders {@code bone.vector} meint. Die Verformung bleibt davon
 * unberuehrt: {@code DEF-upper_arm.R} traegt Y gegen die eigene Strecke 1.0000.
 */
public final class RigSchwenk
{
    /** Blenders {@code find_min_range}-Schrittweite. */
    private static final double SCHRITT = Math.PI / 8;
    /** Blenders {@code ternarySearch}-Genauigkeit. */
    private static final double GENAU = Math.PI / 180;
    /** Blender laeuft die Klammer ueber +-2pi, das sind bei pi/8 genau 16 Schritte. */
    private static final int SCHRITTE = 16;

    private RigSchwenk()
    {}

    /**
     * @param ikErster  erster Knochen der IK-Kette (Rigifys {@code ik_bones[0]})
     * @param ikLetzter der Knochen, der den IK-Constraint TRAEGT ({@code ik_bones[1]})
     * @param fkErster  Weltmatrix des ersten FK-Knochens VOR dem Snap
     * @param fkLetzter Weltmatrix des zweiten FK-Knochens VOR dem Snap
     * @return erreichte Deckung der beiden Strecken (1 = genau), NaN wenn nichts ging
     */
    public static double schwenke(BOBJArmature armature, RigMechanik mechanik, Pose pose,
        String basisControl, String ikErster, String ikLetzter,
        Matrix4f fkErster, Matrix4f fkLetzter, Runnable neuAuswerten)
    {
        BOBJBone erst = armature == null ? null : armature.bones.get(ikErster);
        BOBJBone letzt = armature == null ? null : armature.bones.get(ikLetzter);

        if (erst == null || letzt == null || pose == null || neuAuswerten == null
            || fkErster == null || fkLetzter == null)
        {
            return Double.NaN;
        }

        Vector3f ziel = fkLetzter.getTranslation(new Vector3f())
            .sub(fkErster.getTranslation(new Vector3f()));

        if (ziel.lengthSquared() < 1.0E-12F)
        {
            return Double.NaN;
        }

        ziel.normalize();

        String pol = mechanik == null ? null : mechanik.polVon(ikLetzter);
        DoubleUnaryOperator abstand = pol == null
            ? ohnePol(pose, basisControl, erst, letzt, ziel, neuAuswerten)
            : mitPol(armature, pose, pol, erst, letzt, ziel, neuAuswerten);

        return abstand == null ? Double.NaN : -abstand.applyAsDouble(bestenWinkel(abstand));
    }

    /**
     * Rigifys {@code correct_rotation}: den Basis-Control um seine EIGENE Y-Achse drehen.
     * Ohne Pol bestimmt genau diese Eigendrehung die Ebene der Kette (gemessen: eine
     * halbe Radiante bewegt das Gelenk um 0.02492, die Spitze um 0.00000).
     */
    private static DoubleUnaryOperator ohnePol(Pose pose, String basisControl,
        BOBJBone erst, BOBJBone letzt, Vector3f ziel, Runnable neuAuswerten)
    {
        PoseTransform pt = pose.get(basisControl);
        Quaternionf start = new Quaternionf(pt.quat);

        /* Der Paar-Snap schreibt Quaternion; Euler hier waere ein stiller Nulleffekt. */
        pt.rotationMode = Transform.RotationMode.QUATERNION;

        return (winkel) ->
        {
            pt.quat.set(start).rotateY((float) winkel);
            neuAuswerten.run();

            return -richtung(erst, letzt).dot(ziel);
        };
    }

    /**
     * Rigifys {@code match_pole_target}: den Pol-Control auf einen Punkt neben die Mitte
     * der Linie Wurzel-Spitze setzen. Der Ort steht wie bei Blender (Mitte plus ein
     * Lot in Kettenlaenge), gedreht wird er um dieselbe Linie.
     *
     * <p>⛔ Blender rechnet den Winkel aus einer Matrix-Differenz und probiert nur beide
     * Vorzeichen. Das setzt voraus, dass die Matrix des Wurzelknochens laengs des
     * Knochens liegt — in BBS tut sie das nicht (siehe Klassenkopf), die Differenz waere
     * um einen festen Betrag daneben. Hier laeuft darum dieselbe SUCHE wie ohne Pol, nur
     * mit dem Pol-Ort als Stellgroesse; das Ziel ist dasselbe.
     */
    private static DoubleUnaryOperator mitPol(BOBJArmature armature, Pose pose,
        String polControl, BOBJBone erst, BOBJBone letzt, Vector3f ziel, Runnable neuAuswerten)
    {
        BOBJBone polBone = armature.bones.get(polControl);

        if (polBone == null)
        {
            return null;
        }

        Vector3f a = erst.mat.getTranslation(new Vector3f());
        /* Blenders `bone.vector`: Y-Spalte MIT Betrag (die Streckung steckt darin) mal
         * Ruhelaenge — damit die Spitze des letzten Kettenknochens. */
        Vector3f b = letzt.mat.getTranslation(new Vector3f())
            .add(letzt.mat.getColumn(1, new Vector3f()).mul(letzt.restLength));
        Vector3f ikv = new Vector3f(b).sub(a);
        Vector3f mitte = new Vector3f(a).add(new Vector3f(ikv).mul(0.5F));
        Vector3f pv = senkrecht(ikv).normalize().mul(erst.restLength + letzt.restLength);
        Vector3f achse = new Vector3f(ikv).normalize();

        return (winkel) ->
        {
            Vector3f p = new Vector3f(pv).rotateAxis((float) winkel, achse.x, achse.y, achse.z);

            RigSnap.setzeWeltOrt(polBone, polControl, new Vector3f(mitte).add(p), pose);
            neuAuswerten.run();

            return -richtung(erst, letzt).dot(ziel);
        };
    }

    /** Blenders {@code bone.vector}, geometrisch: von Kopf zu Kopf. */
    private static Vector3f richtung(BOBJBone erst, BOBJBone letzt)
    {
        Vector3f v = letzt.mat.getTranslation(new Vector3f())
            .sub(erst.mat.getTranslation(new Vector3f()));

        return v.lengthSquared() < 1.0E-12F ? v : v.normalize();
    }

    /** Blenders {@code perpendicular_vector}. */
    private static Vector3f senkrecht(Vector3f v)
    {
        Vector3f hilf = Math.abs(v.x) < Math.abs(v.y)
            ? new Vector3f(1F, 0F, 0F) : new Vector3f(0F, 1F, 0F);

        return new Vector3f(v).cross(hilf);
    }

    /**
     * Blenders {@code find_min_range} + {@code ternarySearch}. Findet die Klammer keine
     * Mulde, bleibt der beste ABGETASTETE Winkel stehen — Blender liefe dort in einen
     * Fehler, und ein Abbruch mitten im Snap laesst das Glied schlechter stehen als vorher.
     */
    private static double bestenWinkel(DoubleUnaryOperator f)
    {
        double winkel = 0D;
        double besterWert = Double.MAX_VALUE;
        double bester = 0D;

        for (int i = 0; i < SCHRITTE; i++)
        {
            double l = f.applyAsDouble(winkel - SCHRITT);
            double c = f.applyAsDouble(winkel);
            double r = f.applyAsDouble(winkel + SCHRITT);

            if (c < besterWert)
            {
                besterWert = c;
                bester = winkel;
            }

            if (c <= l && c <= r)
            {
                return ternaer(f, winkel - SCHRITT, winkel + SCHRITT);
            }

            winkel += SCHRITT;
        }

        return bester;
    }

    private static double ternaer(DoubleUnaryOperator f, double links, double rechts)
    {
        while (Math.abs(rechts - links) >= GENAU)
        {
            double drittelL = links + (rechts - links) / 3D;
            double drittelR = rechts - (rechts - links) / 3D;

            if (f.applyAsDouble(drittelL) > f.applyAsDouble(drittelR))
            {
                links = drittelL;
            }
            else
            {
                rechts = drittelR;
            }
        }

        return (links + rechts) / 2D;
    }
}
