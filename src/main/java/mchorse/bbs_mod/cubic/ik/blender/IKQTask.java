/* Uebersetzt aus Blenders intern/iksolver/intern/IK_QTask.cpp.
 * SPDX-FileCopyrightText: 2001-2002 NaN Holding BV. All rights reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Direct-Blender-Port: https://projects.blender.org/blender/blender/src/commit/67807e1800cc48cc7bff3c793525e1179a4d64ca/intern/iksolver/intern/IK_QTask.cpp
 * BBS-Modification: 2026-10-10 - Re-audited against pinned Blender; original revision unknown.
 * See docs/BLENDER-IK-PROVENANCE-2026-10-10.md for scope and parity evidence. */
package mchorse.bbs_mod.cubic.ik.blender;

/**
 * Eine Zielvorgabe an die Kette. Position und Orientierung sind uebersetzt;
 * den Schwerpunkt-Task laesst Blender selbst als unfertig stehen
 * ("implementation not finished") und benutzt ihn nicht — er fehlt hier
 * deshalb bewusst, nicht aus Versehen.
 */
public abstract class IKQTask
{
    final int size;
    final boolean primary;
    final IKQSegment segment;

    int id;
    /**
     * ⛔ Wie Blenders {@code IK_QTask.h}: hier liegt die WURZEL des gesetzten Gewichts
     * ({@code SetWeight: m_weight = sqrt(weight)}, {@code Weight(): m_weight * m_weight}), und
     * mit dieser Wurzel werden Betas und Ableitungen skaliert. Mit dem Gewicht selbst skaliert
     * (bis 2026-09-13) wog ein Ziel im Baum quadratisch zu stark — Testrig: ik_b 0,13 daneben.
     */
    double weight = 1D;

    protected IKQTask(int size, boolean primary, IKQSegment segment)
    {
        this.size = size;
        this.primary = primary;
        this.segment = segment;
    }

    public int size()
    {
        return this.size;
    }

    public boolean primary()
    {
        return this.primary;
    }

    public void setzeId(int id)
    {
        this.id = id;
    }

    public double gewicht()
    {
        return this.weight * this.weight;
    }

    public void setzeGewicht(double gewicht)
    {
        this.weight = Math.sqrt(gewicht);
    }

    public boolean positionsAufgabe()
    {
        return false;
    }

    public abstract void berechneJacobian(IKQJacobian jacobian);

    public void skalieren(double skala)
    {}

    /** Ziel: ein Punkt. Der haeufige Fall — jede IK-Kette ohne Rotationsziel. */
    public static final class Position extends IKQTask
    {
        private IKVec3 ziel;
        private double klemmLaenge;

        public Position(boolean primary, IKQSegment segment, IKVec3 ziel)
        {
            super(3, primary, segment);

            this.ziel = new IKVec3(ziel);

            /* Klemmlaenge begrenzt den Schritt je Iteration auf die halbe
             * mittlere Gliedlaenge — ohne sie schiesst die Kette bei fernen
             * Zielen ueber und schwingt. */
            double summe = 0D;
            int anzahl = 0;

            for (IKQSegment seg = segment; seg != null; seg = seg.parent)
            {
                summe += seg.maxExtension();
                anzahl++;
            }

            this.klemmLaenge = summe / (2D * anzahl);
        }

        @Override
        public boolean positionsAufgabe()
        {
            return true;
        }

        @Override
        public void berechneJacobian(IKQJacobian jacobian)
        {
            IKVec3 pos = this.segment.globalEnd();
            IKVec3 dPos = this.ziel.minus(pos);
            double laenge = dPos.laenge();

            if (laenge > this.klemmLaenge)
            {
                dPos = dPos.mal(this.klemmLaenge / laenge);
            }

            jacobian.setzeBetas(this.id, dPos.mal(this.weight));

            for (IKQSegment seg = this.segment; seg != null; seg = seg.parent)
            {
                IKVec3 p = seg.globalStart().minus(pos);

                for (int i = 0; i < seg.numberOfDoF(); i++)
                {
                    IKVec3 achse = seg.achse(i).mal(this.weight);

                    if (seg.translational())
                    {
                        jacobian.setzeAbleitungen(this.id, seg.doFId() + i, achse, 1e2);
                    }
                    else
                    {
                        jacobian.setzeAbleitungen(this.id, seg.doFId() + i, p.kreuz(achse), 1e0);
                    }
                }
            }
        }

        @Override
        public void skalieren(double skala)
        {
            this.ziel = this.ziel.mal(skala);
            this.klemmLaenge *= skala;
        }
    }

    /** Ziel: eine Ausrichtung. Blenders {@code use_rotation} am IK-Constraint. */
    public static final class Orientierung extends IKQTask
    {
        private final IKMat3 ziel;

        public Orientierung(boolean primary, IKQSegment segment, IKMat3 ziel)
        {
            super(3, primary, segment);

            this.ziel = new IKMat3(ziel);
        }

        @Override
        public void berechneJacobian(IKQJacobian jacobian)
        {
            IKMat3 rot = this.segment.globalTransform().linear;
            IKMat3 dRotM = this.ziel.mal(rot.transponiert()).transponiert();

            IKVec3 dRot = new IKVec3(
                dRotM.get(2, 1) - dRotM.get(1, 2),
                dRotM.get(0, 2) - dRotM.get(2, 0),
                dRotM.get(1, 0) - dRotM.get(0, 1)).mal(-0.5D);

            jacobian.setzeBetas(this.id, dRot.mal(this.weight));

            for (IKQSegment seg = this.segment; seg != null; seg = seg.parent)
            {
                for (int i = 0; i < seg.numberOfDoF(); i++)
                {
                    if (seg.translational())
                    {
                        jacobian.setzeAbleitungen(this.id, seg.doFId() + i, new IKVec3(), 1e2);
                    }
                    else
                    {
                        jacobian.setzeAbleitungen(
                            this.id, seg.doFId() + i, seg.achse(i).mal(this.weight), 1e0);
                    }
                }
            }
        }
    }
}
