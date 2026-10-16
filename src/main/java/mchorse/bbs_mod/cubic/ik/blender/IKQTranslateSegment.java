/* Uebersetzt aus Blenders intern/iksolver/intern/IK_QSegment.{h,cpp}
 * (IK_QTranslateSegment).
 * SPDX-FileCopyrightText: 2001-2002 NaN Holding BV. All rights reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Direct-Blender-Port: https://projects.blender.org/blender/blender/src/commit/67807e1800cc48cc7bff3c793525e1179a4d64ca/intern/iksolver/intern/IK_QSegment.cpp
 * BBS-Modification: 2026-10-15 - Revalidated against this baseline; original revision unknown.
 * See docs/BLENDER-IK-PROVENANCE-2026-10-15.md for scope and parity. */
package mchorse.bbs_mod.cubic.ik.blender;

/**
 * Verschiebe-Segment: ein Glied, das seine LAENGE aendert statt seiner Drehung.
 * Blender baut damit die IK-Dehnung — der Knochen bekommt ein zweites Segment
 * angehaengt, das nur entlang Y translatiert.
 *
 * ⛔ Die Laenge sitzt dann NICHT mehr im Drehsegment: das bekommt Laenge 0, und
 * die volle Knochenlaenge steht als {@code translation = (0, laenge, 0)} hier.
 * Wer beiden eine Laenge gibt, bekommt eine doppelt so lange Kette.
 *
 * Nur der 1-Achsen-Fall ist portiert — mehr erzeugt Blenders Posen-IK nicht:
 * {@code IK_CreateSegment} setzt bei Dehnung genau {@code IK_TRANS_YDOF}.
 */
public final class IKQTranslateSegment extends IKQSegment
{
    private final int[] achsen = new int[3];
    private final boolean[] achseAn = new boolean[3];
    private final boolean[] grenze = new boolean[3];
    private final double[] min = new double[3];
    private final double[] max = new double[3];

    private IKVec3 neueTranslation = new IKVec3();

    public IKQTranslateSegment(int achse)
    {
        super(1, true);

        this.achseAn[achse] = true;
        this.achsen[0] = achse;
    }

    @Override
    public IKVec3 achse(int dof)
    {
        return this.globalTransform.linear.spalte(this.achsen[dof]);
    }

    @Override
    public boolean aktualisiereWinkel(IKQJacobian jacobian, IKVec3 delta, boolean[] klemm)
    {
        int dofId = this.doFId;
        int dof = 0;
        boolean geklemmt = false;

        for (int i = 0; i < 3; i++)
        {
            if (!this.achseAn[i])
            {
                this.neueTranslation.set(i, this.translation.get(i));

                continue;
            }

            klemm[dof] = false;

            /* ⛔ Eine GESPERRTE Achse laesst Blender hier unangetastet — die alte
             * neueTranslation bleibt stehen. Wer sie stattdessen zuruecksetzt,
             * gibt der Achse in der naechsten Klemmrunde ihre Sperre zurueck. */
            if (!this.locked[dof])
            {
                this.neueTranslation.set(i,
                    this.translation.get(i) + jacobian.winkelAenderung(dofId));

                if (this.grenze[i])
                {
                    if (this.neueTranslation.get(i) > this.max[i])
                    {
                        delta.set(dof, this.max[i] - this.translation.get(i));
                        this.neueTranslation.set(i, this.max[i]);
                        geklemmt = klemm[dof] = true;
                    }
                    else if (this.neueTranslation.get(i) < this.min[i])
                    {
                        delta.set(dof, this.min[i] - this.translation.get(i));
                        this.neueTranslation.set(i, this.min[i]);
                        geklemmt = klemm[dof] = true;
                    }
                }
            }

            dofId++;
            dof++;
        }

        return geklemmt;
    }

    @Override
    public void aktualisiereWinkelAnwenden()
    {
        this.translation = new IKVec3(this.neueTranslation);
    }

    @Override
    public void sperren(int dof, IKQJacobian jacobian, IKVec3 delta)
    {
        this.locked[dof] = true;

        jacobian.sperren(this.doFId + dof, delta.get(dof));
    }

    @Override
    public void setzeGewicht(int achse, double gewicht)
    {
        for (int i = 0; i < this.numDoF; i++)
        {
            if (this.achsen[i] == achse)
            {
                this.weight[i] = gewicht;
            }
        }
    }

    @Override
    public void setzeGrenze(int achse, double lmin, double lmax)
    {
        if (lmax < lmin)
        {
            return;
        }

        this.min[achse] = lmin;
        this.max[achse] = lmax;
        this.grenze[achse] = true;
    }

    @Override
    public void skalieren(double skala)
    {
        super.skalieren(skala);

        /* ⛔ Das ist Blenders Schleife, samt ihres Indexfehlers: sie skaliert
         * dreimal min[0] und max[1] und laesst min[1], min[2], max[0], max[2]
         * unberuehrt. Nachgebaut, weil hier PARITAET zaehlt und nicht
         * Korrektheit — ein stiller "Fix" waere eine Abweichung, die man erst
         * an einer leicht anderen Dehngrenze saehe. Der Solver skaliert vor dem
         * Loesen hin und danach zurueck, also hebt sich der Fehler hier auf. */
        for (int i = 0; i < 3; i++)
        {
            this.min[0] *= skala;
            this.max[1] *= skala;
        }

        this.neueTranslation = this.neueTranslation.mal(skala);
    }
}
