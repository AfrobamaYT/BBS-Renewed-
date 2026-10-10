/* Uebersetzt aus Blenders intern/iksolver/intern/IK_QJacobianSolver.cpp.
 * SPDX-FileCopyrightText: 2001-2002 NaN Holding BV. All rights reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Direct-Blender-Port: https://projects.blender.org/blender/blender/src/commit/67807e1800cc48cc7bff3c793525e1179a4d64ca/intern/iksolver/intern/IK_QJacobianSolver.cpp
 * BBS-Modification: 2026-10-10 - Re-audited against pinned Blender; original revision unknown.
 * See docs/BLENDER-IK-PROVENANCE-2026-10-10.md for scope and parity evidence. */
package mchorse.bbs_mod.cubic.ik.blender;

import java.util.ArrayList;
import java.util.List;

/**
 * Blenders Standard-Solver ("Legacy"), der Loeser hinter jedem IK-Constraint,
 * dessen Armature nicht ausdruecklich auf iTaSC steht.
 *
 * ⛔ Abbruch ist {@code norm < 1e-3 && iterationen > 10} — die zehn Runden sind
 * eine Untergrenze, keine Obergrenze. Wer sie fuer eine Optimierung haelt und
 * frueher abbricht, bekommt Ketten, die bei kleinen Zielaenderungen zittern.
 */
public final class IKQJacobianSolver
{
    private final List<IKQSegment> segments = new ArrayList<>();
    private final IKQJacobian jacobian = new IKQJacobian();
    private final IKQJacobian jacobianSub = new IKQJacobian();

    private boolean zweitrangigAktiv;

    private final IKQPolZwang pol = new IKQPolZwang();

    private IKAffine wurzelMatrix = new IKAffine();

    private double berechneSkala()
    {
        double laenge = 0D;

        for (IKQSegment seg : this.segments)
        {
            laenge += seg.maxExtension();
        }

        return laenge == 0D ? 1D : 1D / laenge;
    }

    private void skalieren(double skala, List<IKQTask> aufgaben)
    {
        for (IKQTask t : aufgaben)
        {
            t.skalieren(skala);
        }

        for (IKQSegment seg : this.segments)
        {
            seg.skalieren(skala);
        }

        this.wurzelMatrix.translation = this.wurzelMatrix.translation.mal(skala);
        this.pol.skalieren(skala);
    }

    private void sammleSegmente(IKQSegment seg)
    {
        this.segments.add(seg);

        for (IKQSegment kind = seg.child(); kind != null; kind = kind.sibling())
        {
            this.sammleSegmente(kind);
        }
    }

    public boolean aufbauen(IKQSegment wurzel, List<IKQTask> aufgaben)
    {
        this.segments.clear();
        this.sammleSegmente(wurzel);

        Boolean zweitrangig = IKQAufbau.aufbauen(
            this.segments, this.jacobian, this.jacobianSub, aufgaben);

        if (zweitrangig == null)
        {
            return false;
        }

        this.zweitrangigAktiv = zweitrangig;

        return true;
    }

    public void setzePolZwang(IKQSegment spitze, IKVec3 ziel, IKVec3 polZiel,
                              double polWinkel, boolean winkelHolen)
    {
        this.pol.setzen(spitze, ziel, polZiel, polWinkel, winkelHolen);
    }

    /** Innere Klemmschleife: verletzte Grenzen sperren, bis nichts mehr klemmt. */
    private boolean winkelAktualisieren(double[] norm)
    {
        IKQSegment minSeg = null;
        double minAbsDelta = 1e10;
        IKVec3 minDelta = new IKVec3();
        int minDof = 0;
        boolean gesperrt = false;

        IKVec3 delta = new IKVec3();
        boolean[] klemm = new boolean[3];

        for (IKQSegment seg : this.segments)
        {
            if (!seg.aktualisiereWinkel(this.jacobian, delta, klemm))
            {
                continue;
            }

            for (int i = 0; i < seg.numberOfDoF(); i++)
            {
                if (!klemm[i] || seg.gesperrt(i))
                {
                    continue;
                }

                double absDelta = Math.abs(delta.get(i));

                if (absDelta < IKMath.IK_EPSILON)
                {
                    seg.sperren(i, this.jacobian, delta);
                    gesperrt = true;
                }
                else if (absDelta < minAbsDelta)
                {
                    minAbsDelta = absDelta;
                    minDelta = new IKVec3(delta);
                    minSeg = seg;
                    minDof = i;
                }
            }
        }

        if (minSeg != null)
        {
            minSeg.sperren(minDof, this.jacobian, minDelta);
            gesperrt = true;

            norm[0] = Math.max(minAbsDelta, norm[0]);
        }

        if (!gesperrt)
        {
            for (IKQSegment seg : this.segments)
            {
                seg.entsperren();
                seg.aktualisiereWinkelAnwenden();
            }
        }

        return gesperrt;
    }

    public boolean loesen(IKQSegment wurzel, List<IKQTask> aufgaben, int maxIterationen)
    {
        double skala = this.berechneSkala();
        boolean geloest = false;

        this.skalieren(skala, aufgaben);
        this.wurzelMatrix = this.pol.anwenden(wurzel, aufgaben, this.wurzelMatrix);

        wurzel.aktualisiereTransform(this.wurzelMatrix);

        for (int iteration = 0; iteration < maxIterationen; iteration++)
        {
            wurzel.aktualisiereTransform(this.wurzelMatrix);

            for (IKQTask t : aufgaben)
            {
                t.berechneJacobian(t.primary() ? this.jacobian : this.jacobianSub);
            }

            double[] norm = {0D};

            do
            {
                this.jacobian.invertieren();

                if (this.zweitrangigAktiv)
                {
                    this.jacobian.unteraufgabe(this.jacobianSub);
                }
            }
            while (this.winkelAktualisieren(norm));

            for (IKQSegment seg : this.segments)
            {
                seg.entsperren();
            }

            norm[0] = Math.max(this.jacobian.winkelAenderungsNorm(), norm[0]);

            if (norm[0] < 1e-3 && iteration > 10)
            {
                geloest = true;

                break;
            }
        }

        if (this.pol.aktiv)
        {
            wurzel.basisVoranstellen(this.wurzelMatrix.linear);
        }

        this.skalieren(1D / skala, aufgaben);

        return geloest;
    }
}
