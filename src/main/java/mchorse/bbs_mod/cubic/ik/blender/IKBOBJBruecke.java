/* Uebersetzt aus Blenders source/blender/ikplugin/intern/iksolver_plugin.cc
 * (execute_posetree / make_dmats / where_is_ik_bone) und IK_Solver.cpp.
 * SPDX-FileCopyrightText: 2001-2002 NaN Holding BV. All rights reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Direct-Blender-Port: https://projects.blender.org/blender/blender/src/commit/67807e1800cc48cc7bff3c793525e1179a4d64ca/source/blender/ikplugin/intern/iksolver_plugin.cc
 * BBS-Modification: 2026-10-15 - Revalidated against this baseline; original revision unknown.
 * See docs/BLENDER-IK-PROVENANCE-2026-10-15.md for scope and parity. */
package mchorse.bbs_mod.cubic.ik.blender;

import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.cubic.ik.RigMathe;
import mchorse.bbs_mod.cubic.ik.RigZerlegung;
import mchorse.bbs_mod.cubic.ik.RigVektor;
import mchorse.bbs_mod.cubic.ik.RigQuaternion;
import mchorse.bbs_mod.cubic.ik.RigMatrix3;
import mchorse.bbs_mod.cubic.ik.RigRahmen;

import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loest einen ganzen IK-BAUM (Blenders {@code PoseTree}: mehrere Ketten mit gemeinsamer
 * Wurzel, mehrere Ziele) mit dem portierten Solver und schreibt das Ergebnis zurueck.
 *
 * ⛔ Das ist die Stelle, an der Paritaet entsteht oder verlorengeht. Blenders Reihenfolge ist
 * bindend: Segmente aus den POSE-Matrizen (Blender-Pose-Raum ueber {@link RigRahmen}), Ziele in
 * den Raum der Wurzel (Elternmatrix normalisiert, Ort = Wurzelkopf), Einfluss ohne Pol als
 * Zielmischung, mit Pol als Ergebnismischung, danach {@code make_dmats} und
 * {@code where_is_ik_bone} samt der Kompensation fuer ungleichmaessige Skalierung.
 */
public final class IKBOBJBruecke
{
    /** Was Blender ueber ein Gelenk weiss; aus rig.json, Feld {@code ik_gelenke}. */
    public record Gelenk(boolean[] sperre, double[] steifigkeit, boolean[] grenze,
                         double[] min, double[] max, double dehnung)
    {}

    /** Blenders {@code PoseTarget} samt der in DIESEM Bild ausgewerteten Constraint-Felder. */
    public record Ziel(int tip, float[] zielWelt, float[] polWelt, float einfluss, boolean aus,
                       boolean position, boolean rotation, float gewicht, float orientGewicht,
                       double polWinkel)
    {}

    /** Blenders {@code PoseTree}: Kanaele wurzel-zuerst, Elternindex je Kanal, Ziele. */
    public static final class Baum
    {
        public final List<BOBJBone> pchan = new ArrayList<>();
        public final List<Integer> parent = new ArrayList<>();
        public final List<Ziel> ziele = new ArrayList<>();
        public int iterations;
        public boolean stretch;

        public boolean wirkt()
        {
            for (Ziel z : this.ziele)
            {
                if (!z.aus() && z.einfluss() != 0F)
                {
                    return true;
                }
            }

            return false;
        }
    }

    private IKBOBJBruecke()
    {}

    /**
     * {@code iksolver_execute_tree}, Schritte 5 und 6, fuer einen Baum, dessen Knochen alle
     * schon ihre Pose (samt Constraints) haben.
     *
     * @return true, wenn der Solver konvergiert ist
     */
    public static boolean loese(RigRahmen rahmen, Baum baum, Map<String, Gelenk> gelenke)
    {
        int n = baum.pchan.size();

        if (n == 0)
        {
            return false;
        }

        /* ⛔ ERST FRAGEN, OB SICH UEBERHAUPT ETWAS GEAENDERT HAT. Gemessen 2026-08-21 mit
         * async-profiler: ~700 MB/s Allokation, 88,7 % des Prozesses, bei STEHENDEM Film —
         * identische Eingabe, identisches Ergebnis, alle ~350 ms ein voller Jacobian-Lauf.
         * Gleiche Eingaben -> gespeichertes Ergebnis zurueckschreiben. */
        long signatur = signatur(baum, gelenke);
        String schluessel = schluessel(baum);
        Ergebnis zwischen = cache().get(schluessel);

        if (zwischen != null && zwischen.signatur == signatur && zwischen.passt(baum.pchan))
        {
            zwischen.anwenden(baum.pchan);

            return zwischen.geloest;
        }

        float[][] pose = new float[n][];
        Map<BOBJBone, float[]> alt = new IdentityHashMap<>();

        for (int a = 0; a < n; a++)
        {
            pose[a] = rahmen.poseMat(baum.pchan.get(a));
            alt.put(baum.pchan.get(a), pose[a]);
        }

        IKQSegment[] segs = new IKQSegment[n];
        double[] laengen = new double[n];
        double[] dehnungen = new double[n];

        if (!baueSegmente(rahmen, baum, gelenke, pose, alt, segs, laengen, dehnungen))
        {
            return false;
        }

        /* rootmat: Drehteil des Elters der Wurzel (normalisiert, damit dessen Skala nicht
         * in die Segmentbasis geraet — Blenders #31008), Ort = Kopf der Wurzel. */
        BOBJBone wurzel = baum.pchan.get(0);
        float[] rootmat = RigMathe.einheit();

        if (wurzel.parentBone != null)
        {
            rootmat = RigMathe.kopie(elternPose(wurzel, alt, rahmen));
            RigMathe.normalisiere(rootmat);
        }

        RigMathe.setzeOrt(rootmat, RigMathe.ort(pose[0]));

        float[] goalinv = RigMathe.invertiert(RigMathe.mul(rahmen.welt, rootmat));
        IKQJacobianSolver solver = new IKQJacobianSolver();
        List<IKQTask> aufgaben = new ArrayList<>();
        boolean resultblend = false;
        float resultinf = 0F;

        for (Ziel z : baum.ziele)
        {
            float[] goal = RigMathe.mul(goalinv, z.zielWelt());
            float[] goalpos = RigMathe.ort(goal);
            float[] goalrot = RigMatrix3.normalisiere3(RigMathe.drei(goal));
            float[] polepos = null;

            if (z.polWelt() != null)
            {
                polepos = RigMathe.ort(RigMathe.mul(goalinv, z.polWelt()));
                /* Mit Pol wird das ERGEBNIS gemischt, nicht das Ziel — sonst gibt es keinen
                 * weichen Uebergang (Blenders eigener Kommentar). */
                resultblend = true;
                resultinf = z.aus() ? 0F : z.einfluss();
            }

            if (!resultblend && (z.aus() || z.einfluss() != 1F))
            {
                float fac = z.aus() ? 0F : z.einfluss();
                float mfac = 1F - fac;
                BOBJBone spitze = baum.pchan.get(z.tip());
                float[] endPose = RigMathe.kopie(pose[z.tip()]);

                RigMathe.setzeOrt(endPose, schwanz(endPose, spitze.restLength));

                float[] worldPose = RigMathe.mul(RigMathe.mul(goalinv, rahmen.welt), endPose);

                for (int i = 0; i < 3; i++)
                {
                    goalpos[i] = fac * goalpos[i] + mfac * worldPose[12 + i];
                }

                float[] q = RigQuaternion.slerp(RigQuaternion.quaternion3(goalrot), RigQuaternion.quaternion(worldPose), mfac);

                goalrot = RigQuaternion.quaternionZuMatrix(q);
            }

            IKQSegment spitze = segs[z.tip()];

            /* Bei Dehnung ist die SPITZE das Composite: dort sitzt die Laenge. */
            if (spitze.composite() != null)
            {
                spitze = spitze.composite();
            }

            if (z.position() && z.gewicht() != 0F)
            {
                if (polepos != null)
                {
                    solver.setzePolZwang(spitze, vec(goalpos), vec(polepos), z.polWinkel(), false);
                }

                IKQTask.Position p = new IKQTask.Position(true, spitze, vec(goalpos));

                p.setzeGewicht(z.gewicht());
                aufgaben.add(p);
            }

            if (z.rotation() && z.orientGewicht() != 0F)
            {
                IKQTask.Orientierung o = new IKQTask.Orientierung(true, spitze, zuIK(goalrot));

                o.setzeGewicht(z.orientGewicht());
                aufgaben.add(o);
            }
        }

        if (mitschrift != null)
        {
            schreibeGeometrie(baum, segs, dehnungen, gelenke);
        }

        if (!solver.aufbauen(segs[0], aufgaben))
        {
            return false;
        }

        boolean geloest = solver.loesen(segs[0], aufgaben, baum.iterations);

        schreibeZurueck(rahmen, baum, segs, laengen, dehnungen, pose, alt, resultblend, resultinf);
        cache().put(schluessel, new Ergebnis(signatur, baum.pchan, geloest));

        return geloest;
    }

    /** Blenders Segmentaufbau aus {@code execute_posetree}, Kanal fuer Kanal. */
    private static boolean baueSegmente(RigRahmen rahmen, Baum baum, Map<String, Gelenk> gelenke, float[][] pose,
                                        Map<BOBJBone, float[]> alt, IKQSegment[] segs, double[] laengen,
                                        double[] dehnungen)
    {
        for (int a = 0; a < baum.pchan.size(); a++)
        {
            BOBJBone b = baum.pchan.get(a);
            Gelenk g = gelenke == null ? null : gelenke.get(b.name);
            /* Blenders {@code tree->stretch && pchan->ikstretch > 0}: erst wenn BEIDES gesetzt
             * ist, bekommt das Glied den vierten Freiheitsgrad. */
            boolean dehnbar = baum.stretch && g != null && g.dehnung() > 0D;
            IKQSegment seg;

            try
            {
                seg = g == null ? new IKQSphericalSegment()
                    : IKUmrechnung.segment(g.sperre()[0], g.sperre()[1], g.sperre()[2], dehnbar);
            }
            catch (UnsupportedOperationException e)
            {
                System.err.println("[Blender-IK] " + b.name + ": " + e.getMessage());

                return false;
            }

            /* An das COMPOSITE des Elters haengen, wo es eines gibt (IK_SetParent). */
            IKQSegment elter = a == 0 ? null : segs[baum.parent.get(a)];

            seg.setzeElter(elter != null && elter.composite() != null ? elter.composite() : elter);

            float[] bonemat = RigMathe.drei(pose[a]);
            float[] parmat = b.parentBone == null ? RigMatrix3.einheit3() : RigMathe.drei(elternPose(b, alt, rahmen));
            float[] start = new float[3];

            if (b.parentBone != null && a > 0)
            {
                float[] pp = elternPose(b, alt, rahmen);

                start = RigVektor.differenz(RigMathe.ort(pose[a]), schwanz(pp, b.parentBone.restLength));
            }

            double laenge = b.restLength * RigVektor.laenge(RigMathe.spalte(pose[a], 1));

            bonemat = RigMatrix3.normalisiere3(bonemat);
            parmat = RigMatrix3.normalisiere3(parmat);

            float[] rest = RigMathe.drei(b.relBoneMat.get(new float[16]));
            float[] iparmat = RigMatrix3.invertiert3(parmat);
            float[] basis = RigMatrix3.mul3(RigMatrix3.transponiert3(rest), RigMatrix3.mul3(iparmat, bonemat));
            IKVec3 startLokal = vec(RigMatrix3.mul3v(iparmat, start));

            /* ⛔ Bei Dehnung geht die LAENGE ans Composite und das Drehsegment bekommt 0 —
             * sonst zaehlt die Kette jeden Knochen doppelt. */
            if (dehnbar && seg.composite() != null)
            {
                seg.setzeTransform(startLokal, zuIK(rest), zuIK(basis), 0D);
                seg.composite().setzeTransform(new IKVec3(), IKMat3.identitaet(), IKMat3.identitaet(), laenge);
            }
            else
            {
                /* Auch das reine Verschiebe-Glied (alle Drehachsen gesperrt) traegt die Laenge selbst. */
                seg.setzeTransform(startLokal, zuIK(rest), zuIK(basis), laenge);
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
                IKUmrechnung.steifigkeit(seg, 4, 1D - g.dehnung() * g.dehnung());
                IKUmrechnung.grenze(seg, 4, IKUmrechnung.DEHN_MIN, IKUmrechnung.DEHN_MAX);
            }

            segs[a] = seg;
            laengen[a] = laenge;
            dehnungen[a] = dehnbar ? g.dehnung() : 0D;
        }

        return true;
    }

    /** Basisaenderungen einsammeln, {@code make_dmats}, {@code where_is_ik_bone}. */
    private static void schreibeZurueck(RigRahmen rahmen, Baum baum, IKQSegment[] segs, double[] laengen,
                                        double[] dehnungen, float[][] pose, Map<BOBJBone, float[]> alt,
                                        boolean resultblend, float resultinf)
    {
        int n = baum.pchan.size();
        float[][] bc = new float[n][];
        double[] ikstretch = new double[n];
        boolean hasstretch = false;

        for (double d : dehnungen)
        {
            hasstretch |= d > 0D;
        }

        for (int a = 0; a < n; a++)
        {
            bc[a] = ausIK(segs[a].basisAenderung());

            if (hasstretch)
            {
                /* "have to compensate for scaling received from parent" */
                int p = baum.parent.get(a);
                double parentstretch = p >= 0 ? ikstretch[p] : 1D;

                IKQSegment schub = segs[a].composite() != null ? segs[a].composite() : segs[a];

                if (dehnungen[a] > 0D && schub.translational())
                {
                    /* IK_GetTranslationChange: das Composite, sonst das Glied selbst. */
                    double trans = schub.translationsAenderung().y;

                    ikstretch[a] = laengen[a] == 0D ? 1D : (trans + laengen[a]) / laengen[a];
                }
                else
                {
                    ikstretch[a] = 1D;
                }

                double stretch = parentstretch == 0D ? 1D : ikstretch[a] / parentstretch;

                for (int k = 0; k < 9; k++)
                {
                    bc[a][k] *= (float) stretch;
                }
            }

            if (resultblend && resultinf != 1F)
            {
                bc[a] = RigZerlegung.mische3(RigMatrix3.einheit3(), bc[a], resultinf);
            }
        }

        /* make_dmats: das Delta jedes Kanals aus den Matrizen VOR dem Loesen. */
        float[][] chan = new float[n][];

        for (int a = 0; a < n; a++)
        {
            BOBJBone b = baum.pchan.get(a);

            chan[a] = b.parentBone == null ? pose[a]
                : RigMathe.mul(RigMathe.invertiert(elternPose(b, alt, rahmen)), pose[a]);
        }

        Map<BOBJBone, float[]> neu = new IdentityHashMap<>();

        for (int a = 0; a < n; a++)
        {
            BOBJBone b = baum.pchan.get(a);
            float[] p = b.parentBone == null ? RigMathe.kopie(chan[a])
                : RigMathe.mul(neu.getOrDefault(b.parentBone, elternPose(b, alt, rahmen)), chan[a]);

            /* USE_NONUNIFORM_SCALE: der Solver kennt nur gleichmaessige Skalierung, also
             * X und Z vorher auf die Y-Skala setzen und danach wieder herausrechnen. */
            float[] scale = RigMathe.groesse(p);

            RigMathe.setzeSpalte(p, 0, normLaenge(RigMathe.spalte(p, 0), scale[1]));
            RigMathe.setzeSpalte(p, 2, normLaenge(RigMathe.spalte(p, 2), scale[1]));
            p = RigMathe.mul(p, RigMathe.vier(bc[a]));

            float[] ikScale = RigMatrix3.groesse3(bc[a]);

            RigMathe.setzeSpalte(p, 0, normLaenge(RigMathe.spalte(p, 0), scale[0] * ikScale[0]));
            RigMathe.setzeSpalte(p, 2, normLaenge(RigMathe.spalte(p, 2), scale[2] * ikScale[2]));
            neu.put(b, p);

            /* ⛔ originMat MUSS denselben Weltversatz mitgehen: das Mesh liest mat, die
             * Control-Gizmos und das Picking lesen originMat (gemessen 2026-08-14: die vier
             * IK-Spitzen standen bis zu 0,3465 daneben). */
            Matrix4f vorher = new Matrix4f(b.mat);

            b.mat.set(RigMathe.zuJoml(rahmen.ausPose(p)));
            b.originMat.set(new Matrix4f(b.mat).mul(vorher.invert()).mul(b.originMat));
        }
    }

    private static float[] elternPose(BOBJBone b, Map<BOBJBone, float[]> alt, RigRahmen rahmen)
    {
        float[] p = alt.get(b.parentBone);

        return p != null ? p : rahmen.poseMat(b.parentBone);
    }

    private static float[] schwanz(float[] mat, float laenge)
    {
        return RigVektor.summe(RigMathe.ort(mat), RigVektor.mal(RigMathe.spalte(mat, 1), laenge));
    }

    /** {@code normalize_v3_length}. */
    private static float[] normLaenge(float[] v, float laenge)
    {
        float d = RigVektor.punktprodukt(v, v);

        if (d > 1.0E-35F)
        {
            return RigVektor.mal(v, laenge / (float) Math.sqrt(d));
        }

        return new float[3];
    }

    private static IKVec3 vec(float[] v)
    {
        return new IKVec3(v[0], v[1], v[2]);
    }

    /** Blenders Spaltenlayout (float[9]) -> zeilenmajores IKMat3 (wie {@code IK_SetTransform}). */
    static IKMat3 zuIK(float[] m)
    {
        return IKMat3.von(m[0], m[3], m[6], m[1], m[4], m[7], m[2], m[5], m[8]);
    }

    /** Zeilenmajores IKMat3 -> Blenders Spaltenlayout (wie {@code IK_GetBasisChange}). */
    static float[] ausIK(IKMat3 m)
    {
        float[] r = new float[9];

        for (int c = 0; c < 3; c++)
        {
            for (int z = 0; z < 3; z++)
            {
                r[c * 3 + z] = (float) m.get(z, c);
            }
        }

        return r;
    }

    /* ================== Ergebnis-Cache ================== */

    /** Kein Initialisierer am Feld: nach einem Hotswap ist die Map null, siehe {@link #cache()}. */
    private static Map<String, Ergebnis> CACHE = new HashMap<>();

    private static Map<String, Ergebnis> cache()
    {
        if (CACHE == null)
        {
            CACHE = new HashMap<>();
        }

        return CACHE;
    }

    private static String schluessel(Baum baum)
    {
        StringBuilder sb = new StringBuilder();

        for (BOBJBone b : baum.pchan)
        {
            sb.append(b.name).append('/');
        }

        return sb.toString();
    }

    /**
     * Fingerabdruck ALLER Solver-Eingaben. ⛔ Die Ausgangs-Matrizen der Kette gehoeren dazu:
     * ohne sie schriebe der Cache eine alte Loesung auf eine neue Pose.
     */
    private static long signatur(Baum baum, Map<String, Gelenk> gelenke)
    {
        long h = 17L;

        h = h * 31L + baum.iterations;
        h = h * 31L + (baum.stretch ? 1L : 0L);

        for (BOBJBone b : baum.pchan)
        {
            h = h * 31L + b.mat.hashCode();

            Gelenk g = gelenke == null ? null : gelenke.get(b.name);

            if (g != null)
            {
                h = h * 31L + Arrays.hashCode(g.sperre()) + Arrays.hashCode(g.grenze());
                h = h * 31L + Arrays.hashCode(g.steifigkeit()) + Arrays.hashCode(g.min()) + Arrays.hashCode(g.max());
                h = h * 31L + Double.hashCode(g.dehnung());
            }
        }

        for (Ziel z : baum.ziele)
        {
            h = h * 31L + z.tip();
            h = h * 31L + Arrays.hashCode(z.zielWelt());
            h = h * 31L + (z.polWelt() == null ? 0 : Arrays.hashCode(z.polWelt()));
            h = h * 31L + Float.hashCode(z.einfluss()) + (z.aus() ? 7L : 0L);
            h = h * 31L + (z.position() ? 1L : 0L) + (z.rotation() ? 2L : 0L);
            h = h * 31L + Float.hashCode(z.gewicht()) + Float.hashCode(z.orientGewicht());
            h = h * 31L + Double.hashCode(z.polWinkel());
        }

        return h;
    }

    /** Ein gespeichertes Ergebnis: die Bone-Matrizen nach dem Loesen. */
    private static final class Ergebnis
    {
        final long signatur;
        final boolean geloest;
        final String[] namen;
        final Matrix4f[] matrizen;
        final Matrix4f[] origin;

        Ergebnis(long signatur, List<BOBJBone> kette, boolean geloest)
        {
            this.signatur = signatur;
            this.geloest = geloest;
            this.namen = new String[kette.size()];
            this.matrizen = new Matrix4f[kette.size()];
            this.origin = new Matrix4f[kette.size()];

            for (int i = 0; i < kette.size(); i++)
            {
                this.namen[i] = kette.get(i).name;
                this.matrizen[i] = new Matrix4f(kette.get(i).mat);
                this.origin[i] = new Matrix4f(kette.get(i).originMat);
            }
        }

        boolean passt(List<BOBJBone> kette)
        {
            if (kette.size() != this.namen.length)
            {
                return false;
            }

            for (int i = 0; i < this.namen.length; i++)
            {
                if (!this.namen[i].equals(kette.get(i).name))
                {
                    return false;
                }
            }

            return true;
        }

        void anwenden(List<BOBJBone> kette)
        {
            for (int i = 0; i < this.matrizen.length; i++)
            {
                kette.get(i).mat.set(this.matrizen[i]);
                kette.get(i).originMat.set(this.origin[i]);
            }
        }
    }

    /* ================== Mitschrift fuer tools/ikref ================== */

    /** Wohin die Eingaben geschrieben werden, wenn jemand messen will; {@code null} = aus. */
    public static String mitschrift;

    /**
     * Die Eingaben dieser Loesung im Format von {@code tools/ikref/ikref.cpp}, damit DIESELBE
     * Datei durch Blenders eigene Loeser-Bibliothek und durch diesen Port laufen kann.
     * ⛔ Matrizen gehen TRANSPONIERT hinaus: {@code IK_SetTransform} liest spaltenweise.
     */
    private static void schreibeGeometrie(Baum baum, IKQSegment[] segs, double[] dehnungen, Map<String, Gelenk> gelenke)
    {
        StringBuilder sb = new StringBuilder(segs.length + "\n");

        for (int a = 0; a < segs.length; a++)
        {
            IKQSegment seg = segs[a];
            Gelenk g = gelenke == null ? null : gelenke.get(baum.pchan.get(a).name);
            int flag = 0;

            for (int achse = 0; achse < 3; achse++)
            {
                if (g == null || !g.sperre()[achse])
                {
                    flag |= 1 << achse;
                }
            }

            sb.append(flag).append(' ').append(baum.parent.get(a));
            anhaengen(sb, seg.start);
            anhaengenT(sb, seg.restBasis);
            anhaengenT(sb, seg.origBasis);
            sb.append(String.format(java.util.Locale.ROOT, " %.7f %.7f",
                seg.composite() != null ? seg.composite().translation.y : seg.translation.y,
                dehnungen[a] > 0D ? dehnungen[a] : -1D));
            sb.append('\n');
        }

        Ziel erstes = baum.ziele.isEmpty() ? null : baum.ziele.get(0);

        sb.append(String.format(java.util.Locale.ROOT, "%d ziele, iterationen %d, gewicht %.7f\n",
            baum.ziele.size(), baum.iterations, erstes == null ? 0F : erstes.gewicht()));

        String datei = mitschrift + "-" + baum.pchan.get(baum.pchan.size() - 1).name.replace('/', '_');

        try
        {
            java.nio.file.Files.writeString(java.nio.file.Path.of(datei + ".txt"), sb.toString());

            StringBuilder namen = new StringBuilder();

            for (BOBJBone b : baum.pchan)
            {
                namen.append(b.name).append(' ').append(b.restLength).append('\n');
            }

            java.nio.file.Files.writeString(java.nio.file.Path.of(datei + ".ketten"), namen.toString());
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
}
