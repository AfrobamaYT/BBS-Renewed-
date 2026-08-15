/* Uebersetzt aus Blenders source/blender/ikplugin/intern/iksolver_plugin.cc
 * (execute_posetree / make_dmats / where_is_ik_bone).
 * SPDX-License-Identifier: GPL-2.0-or-later */
package mchorse.bbs_mod.cubic.ik.blender;

import mchorse.bbs_mod.bobj.BOBJArmature;
import mchorse.bbs_mod.bobj.BOBJBone;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Setzt eine BOBJ-Knochenkette in Blenders Segmentmodell um, loest sie mit dem
 * portierten Solver und schreibt das Ergebnis zurueck.
 *
 * ⛔ Das ist die Stelle, an der Paritaet entsteht oder verlorengeht. Blenders
 * Reihenfolge ist bindend und steht hier Schritt fuer Schritt:
 * Delta-Matrizen VOR dem Loesen sichern, Segmente in ELTERN-Raum aufbauen,
 * Ziele in den Raum der Kettenwurzel legen, loesen, dann
 * {@code mat = elter.mat * delta * ikMat} von der Wurzel zur Spitze.
 */
public final class IKBOBJBruecke
{
    /** Was Blender ueber ein Gelenk weiss; aus rig.json, Feld {@code ik_gelenke}. */
    public record Gelenk(boolean[] sperre, double[] steifigkeit, boolean[] grenze,
                         double[] min, double[] max, double dehnung)
    {}

    private IKBOBJBruecke()
    {}

    /**
     * @param kettenLaenge Anzahl Knochen ab der Spitze aufwaerts (Blenders chain_count)
     * @param gelenke      je Knochenname; fehlt einer, ist er ein freies Kugelgelenk
     * @param dehnen       Blenders {@code use_stretch} am IK-Constraint
     * @return true, wenn der Solver konvergiert ist
     */
    public static boolean loese(BOBJArmature armature, String spitzeName, int kettenLaenge,
                                Matrix4f zielMat, Matrix4f polMat, double polWinkel,
                                double gewicht, Map<String, Gelenk> gelenke, boolean dehnen,
                                int iterationen)
    {
        List<BOBJBone> kette = sammleKette(armature, spitzeName, kettenLaenge);

        if (kette.size() < 2)
        {
            return false;
        }

        /* make_dmats: das lokale Delta JEDES Knochens sichern, bevor der Solver
         * die Matrizen anfasst — das Rueckschreiben braucht den Vorher-Zustand.
         * Aus demselben Grund stehen Laengen und Dehnfaktoren HIER und nicht im
         * Rueckschreiben: dort ist b.mat schon ueberschrieben. */
        List<Matrix4f> deltas = new ArrayList<>();
        double[] laengen = new double[kette.size()];
        double[] dehnungen = new double[kette.size()];

        for (int a = 0; a < kette.size(); a++)
        {
            BOBJBone b = kette.get(a);
            Gelenk g = gelenke == null ? null : gelenke.get(b.name);

            deltas.add(b.parentBone == null
                ? new Matrix4f(b.mat)
                : new Matrix4f(b.parentBone.mat).invert().mul(b.mat));

            laengen[a] = b.restLength * IKUmrechnung.ySpaltenLaenge(IKUmrechnung.zuMat3(b.mat));

            /* Blenders {@code tree->stretch && pchan->ikstretch > 0}: erst wenn
             * BEIDES gesetzt ist, bekommt das Glied den vierten Freiheitsgrad. */
            dehnungen[a] = dehnen && g != null ? g.dehnung() : 0D;
        }

        List<IKQSegment> segmente = baueSegmente(kette, gelenke, laengen, dehnungen);

        if (segmente == null)
        {
            return false;
        }

        /* rootmat: Drehteil des Elters der Kettenwurzel, Verschiebung deren Kopf.
         * Das Ziel wird da hineingerechnet, damit die Drehung des Elters NICHT
         * Teil der Segmentbasis wird — sonst greifen Gelenkgrenzen im falschen
         * Raum (Blenders eigener Kommentar an dieser Stelle). */
        BOBJBone wurzel = kette.get(0);
        Matrix4f wurzelMat = wurzel.parentBone == null
            ? new Matrix4f()
            : normalisiert(new Matrix4f(wurzel.parentBone.mat));

        wurzelMat.setTranslation(wurzel.mat.getTranslation(new Vector3f()));

        Matrix4f zielInv = new Matrix4f(wurzelMat).invert();
        Vector3f ziel = new Matrix4f(zielInv).mul(zielMat).getTranslation(new Vector3f());

        /* ⛔ Bei einem gedehnten Glied ist die SPITZE das Composite: dort sitzt
         * die Laenge, also auch das Kettenende. Wer aufs Drehsegment zielt,
         * zielt einen ganzen Knochen zu frueh (Blenders eigener Kommentar an
         * IK_SolverAddGoal). */
        IKQSegment spitzeSeg = segmente.get(segmente.size() - 1);

        if (spitzeSeg.composite() != null)
        {
            spitzeSeg = spitzeSeg.composite();
        }

        IKQJacobianSolver solver = new IKQJacobianSolver();

        List<IKQTask> aufgaben = new ArrayList<>();
        IKQTask.Position aufgabe = new IKQTask.Position(true, spitzeSeg, IKUmrechnung.zuVec3(ziel));

        aufgabe.setzeGewicht(gewicht);
        aufgaben.add(aufgabe);

        if (!solver.aufbauen(segmente.get(0), aufgaben))
        {
            return false;
        }

        if (polMat != null)
        {
            Vector3f pol = new Matrix4f(zielInv).mul(polMat).getTranslation(new Vector3f());

            solver.setzePolZwang(spitzeSeg, IKUmrechnung.zuVec3(ziel),
                IKUmrechnung.zuVec3(pol), polWinkel, false);
        }

        /* ⛔ Die Bruecke schreibt ihre EIGENEN Eingaben heraus — nicht ein zweites
         * Skript, das sie nachrechnet. Genau daran sind meine Vergleichsapparaturen
         * gescheitert (zwei Beschreibungen derselben Geometrie). Aus ist der Normalfall;
         * das Feld setzt nur die Messung. */
        if (mitschrift != null)
        {
            schreibeGeometrie(segmente, dehnungen, gelenke, kette, ziel, gewicht, iterationen);
        }

        boolean geloest = solver.loesen(segmente.get(0), aufgaben, iterationen);

        schreibeZurueck(kette, deltas, segmente, laengen, dehnungen);

        return geloest;
    }

    /**
     * Wohin die Eingaben geschrieben werden, wenn jemand messen will. {@code null} =
     * aus. Kein Initialisierer, damit ein Hotswap nichts kaputtmacht.
     */
    public static String mitschrift;

    /**
     * Die Eingaben dieser Loesung im Format von {@code tools/ikref/ikref.cpp}, damit
     * DIESELBE Datei durch Blenders eigene Loeser-Bibliothek und durch diesen Port
     * laufen kann.
     *
     * <p>⛔ Matrizen gehen TRANSPONIERT hinaus: Blenders {@code IK_SetTransform} liest
     * spaltenweise ({@code rest[0][0], rest[1][0], rest[2][0]} wird seine erste ZEILE),
     * unser {@link IKMat3} ist zeilenweise.
     */
    private static void schreibeGeometrie(List<IKQSegment> segmente, double[] dehnungen,
        Map<String, Gelenk> gelenke, List<BOBJBone> kette, Vector3f ziel, double gewicht,
        int iterationen)
    {
        StringBuilder sb = new StringBuilder(segmente.size() + "\n");

        for (int a = 0; a < segmente.size(); a++)
        {
            IKQSegment seg = segmente.get(a);
            Gelenk g = gelenke == null ? null : gelenke.get(kette.get(a).name);
            int flag = 0;

            for (int achse = 0; achse < 3; achse++)
            {
                if (g == null || !g.sperre()[achse])
                {
                    flag |= 1 << achse;
                }
            }

            sb.append(flag).append(' ').append(a - 1);
            /* Felder statt Gettern: dieselbe Paketebene, und es ist genau der Zustand,
             * den setzeTransform gerade hineingelegt hat. origBasis, weil `basis` beim
             * Loesen fortgeschrieben wird. Die LAENGE steckt bei Dehnung im Composite. */
            anhaengen(sb, seg.start);
            anhaengenT(sb, seg.restBasis);
            anhaengenT(sb, seg.origBasis);
            sb.append(String.format(java.util.Locale.ROOT, " %.7f %.7f",
                seg.composite() != null ? seg.composite().translation.y : seg.translation.y,
                dehnungen[a] > 0D ? dehnungen[a] : -1D));
            sb.append('\n');
        }

        sb.append(String.format(java.util.Locale.ROOT, "%.7f %.7f %.7f %.7f 0.0 %d\n",
            ziel.x, ziel.y, ziel.z, gewicht, iterationen));

        /* ⛔ EINE DATEI JE KETTE, benannt nach ihrer Spitze. Bei einem gemeinsamen
         * Namen gewinnt die zuletzt geloeste Kette, und man misst eine beliebige, ohne
         * es zu merken (einmal passiert: gemessen wurde das Vorderbein statt der
         * gesuchten Kette). */
        String datei = mitschrift + "-" + kette.get(kette.size() - 1).name.replace('/', '_');

        try
        {
            java.nio.file.Files.writeString(java.nio.file.Path.of(datei + ".txt"), sb.toString());

            /* ⛔ Daneben, WELCHE Kette das war. Ohne das misst man eine Kette und weiss
             * nicht welche — der Loeser schreibt bei jedem Aufruf, es gewinnt der
             * letzte. Nicht in die Geometriedatei: ikrefs fscanf kennt keine Kommentare. */
            StringBuilder namen = new StringBuilder();

            for (BOBJBone b : kette)
            {
                namen.append(b.name).append(' ').append(b.restLength).append('\n');
            }

            java.nio.file.Files.writeString(java.nio.file.Path.of(datei + ".ketten"),
                namen.toString());
        }
        catch (java.io.IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    private static void anhaengen(StringBuilder sb, IKVec3 v)
    {
        sb.append(String.format(java.util.Locale.ROOT, " %.7f %.7f %.7f", v.x, v.y, v.z));
    }

    private static void anhaengenT(StringBuilder sb, IKMat3 m)
    {
        for (int r = 0; r < 3; r++)
        {
            for (int c = 0; c < 3; c++)
            {
                sb.append(String.format(java.util.Locale.ROOT, " %.7f", m.get(c, r)));
            }
        }
    }

    /** Kette von der Spitze aufwaerts, zurueckgegeben WURZEL zuerst wie bei Blender. */
    private static List<BOBJBone> sammleKette(BOBJArmature armature, String spitzeName, int laenge)
    {
        List<BOBJBone> kette = new ArrayList<>();
        BOBJBone b = armature.bones.get(spitzeName);

        for (int i = 0; b != null && i < laenge; i++)
        {
            kette.add(0, b);
            b = b.parentBone;
        }

        return kette;
    }

    private static List<IKQSegment> baueSegmente(List<BOBJBone> kette, Map<String, Gelenk> gelenke,
                                                 double[] laengen, double[] dehnungen)
    {
        List<IKQSegment> segmente = new ArrayList<>();

        for (int a = 0; a < kette.size(); a++)
        {
            BOBJBone b = kette.get(a);
            Gelenk g = gelenke == null ? null : gelenke.get(b.name);
            boolean dehnbar = dehnungen[a] > 0D;

            IKQSegment seg;

            try
            {
                seg = g == null
                    ? new IKQSphericalSegment()
                    : IKUmrechnung.segment(g.sperre[0], g.sperre[1], g.sperre[2], dehnbar);
            }
            catch (UnsupportedOperationException e)
            {
                System.err.println("[Blender-IK] " + b.name + ": " + e.getMessage());

                return null;
            }

            /* An das COMPOSITE des Elters haengen, wo es eines gibt (IK_SetParent):
             * dort sitzt dessen Laenge, also auch der Ansatzpunkt dieses Glieds. */
            IKQSegment elter = a == 0 ? null : segmente.get(a - 1);

            seg.setzeElter(elter != null && elter.composite() != null
                ? elter.composite() : elter);

            IKMat3 bonemat = IKUmrechnung.zuMat3(b.mat);
            IKMat3 parmat = b.parentBone == null
                ? IKMat3.identitaet()
                : IKUmrechnung.zuMat3(b.parentBone.mat);

            /* Knochenversatz: Kopf dieses Knochens minus Ende des Elters. Nur
             * innerhalb der Kette — die Wurzel sitzt per Definition im Ursprung
             * ihres eigenen Raums. */
            IKVec3 start = new IKVec3();

            if (a > 0 && b.parentBone != null)
            {
                Vector3f kopf = b.mat.getTranslation(new Vector3f());
                Vector3f elterEnde = ende(b.parentBone);

                start = IKUmrechnung.zuVec3(kopf.sub(elterEnde));
            }

            bonemat = IKUmrechnung.normalisiert(bonemat);
            parmat = IKUmrechnung.normalisiert(parmat);

            IKMat3 restBasis = IKUmrechnung.zuMat3(b.relBoneMat);
            IKMat3 parmatInv = parmat.transponiert();
            IKMat3 basis = restBasis.transponiert().mal(parmatInv.mal(bonemat));

            /* ⛔ Bei Dehnung geht die LAENGE ans Composite und das Drehsegment
             * bekommt 0 — sonst zaehlt die Kette jeden Knochen doppelt. */
            if (dehnbar)
            {
                seg.setzeTransform(parmatInv.mal(start), restBasis, basis, 0D);
                seg.composite().setzeTransform(new IKVec3(), IKMat3.identitaet(),
                    IKMat3.identitaet(), laengen[a]);
            }
            else
            {
                seg.setzeTransform(parmatInv.mal(start), restBasis, basis, laengen[a]);
            }

            if (g != null)
            {
                for (int achse = 0; achse < 3; achse++)
                {
                    if (g.grenze()[achse])
                    {
                        IKUmrechnung.grenze(seg, achse, g.min()[achse], g.max()[achse]);
                    }

                    IKUmrechnung.steifigkeit(seg, achse, g.steifigkeit()[achse]);
                }
            }

            if (dehnbar)
            {
                /* Steifigkeit 1-s² heisst Gewicht s²: je groesser ik_stretch,
                 * desto lieber laengt die Kette statt zu drehen. */
                IKUmrechnung.steifigkeit(seg, 4, 1D - dehnungen[a] * dehnungen[a]);
                IKUmrechnung.grenze(seg, 4, IKUmrechnung.DEHN_MIN, IKUmrechnung.DEHN_MAX);
            }

            segmente.add(seg);
        }

        return segmente;
    }

    /** Ende eines Knochens in der aktuellen Pose: Kopf + Y-Richtung mal Ruhelaenge. */
    private static Vector3f ende(BOBJBone b)
    {
        Matrix4f m = b.mat;
        Vector3f y = new Vector3f(m.get(1, 0), m.get(1, 1), m.get(1, 2));

        return m.getTranslation(new Vector3f()).add(y.mul(b.restLength));
    }

    private static Matrix4f normalisiert(Matrix4f m)
    {
        return IKUmrechnung.zuJoml(IKUmrechnung.normalisiert(IKUmrechnung.zuMat3(m)))
            .setTranslation(m.getTranslation(new Vector3f()));
    }

    /** where_is_ik_bone: mat = elter.mat * delta * ikMat, Wurzel zuerst. */
    private static void schreibeZurueck(List<BOBJBone> kette, List<Matrix4f> deltas,
                                        List<IKQSegment> segmente, double[] laengen,
                                        double[] dehnungen)
    {
        double[] dehnFaktor = new double[kette.size()];

        for (int a = 0; a < kette.size(); a++)
        {
            BOBJBone b = kette.get(a);
            IKQSegment seg = segmente.get(a);
            Matrix4f ikMat = IKUmrechnung.zuJoml(seg.basisAenderung());

            /* Wie weit ist DIESES Glied laenger geworden? Die Y-Translation des
             * Composite steckt im gedehnten Fall die ganze Laenge. */
            dehnFaktor[a] = dehnungen[a] > 0D && seg.composite() != null && laengen[a] != 0D
                ? (seg.composite().translationsAenderung().y + laengen[a]) / laengen[a]
                : 1D;

            /* ⛔ Nur die RELATIVE Dehnung darf in die Matrix: die des Elters
             * steckt schon in dessen mat und kaeme sonst ein zweites Mal mit.
             * Genau das meint Blenders "compensate for scaling received from
             * parent"; ohne die Division waechst die Kette exponentiell. */
            double relativ = a == 0 || dehnFaktor[a - 1] == 0D
                ? dehnFaktor[a] : dehnFaktor[a] / dehnFaktor[a - 1];

            if (relativ != 1D)
            {
                ikMat.scale((float) relativ);
            }

            Matrix4f neu = b.parentBone == null
                ? new Matrix4f(deltas.get(a))
                : new Matrix4f(b.parentBone.mat).mul(deltas.get(a));

            /* ⛔ originMat MUSS denselben Weltversatz mitgehen. Das Mesh liest
             * mat, aber die Control-Gizmos (ModelPivotFrames) und das Picking
             * (ModelInstance.captureMatrices) lesen originMat — ohne das sitzt
             * der Griff neben dem Knochen, den er bewegt. Gemessen 2026-08-14:
             * die vier IK-Spitzen standen bis zu 0,3465 daneben. */
            Matrix4f vorher = new Matrix4f(b.mat);

            b.mat.set(neu.mul(ikMat));
            b.originMat.set(new Matrix4f(b.mat).mul(vorher.invert()).mul(b.originMat));
        }
    }
}
