/* Uebersetzt aus Blenders intern/iksolver/intern/IK_QJacobianSolver.cpp
 * (SetPoleVectorConstraint / ConstrainPoleVector).
 * SPDX-FileCopyrightText: 2001-2002 NaN Holding BV. All rights reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Direct-Blender-Port: https://projects.blender.org/blender/blender/src/commit/67807e1800cc48cc7bff3c793525e1179a4d64ca/intern/iksolver/intern/IK_QJacobianSolver.cpp
 * BBS-Modification: 2026-10-10 - Re-audited against pinned Blender; original revision unknown.
 * See docs/BLENDER-IK-PROVENANCE-2026-10-10.md for scope and parity evidence. */
package mchorse.bbs_mod.cubic.ik.blender;

import java.util.List;

/**
 * Der Pol-Zwang: er dreht die WURZEL der Kette so, dass sie in der Ebene liegt,
 * die das Pol-Ziel aufspannt. Er loest also nicht mit, er richtet vorher aus.
 *
 * ⛔ Blender ruft das VOR und NACH dem Loesen. Vorher macht es die Loesung
 * vorhersagbar (die Kette dreht sich zur Loesung hin statt in eine beliebige
 * gleichwertige), nachher exakt. Nur einmal zu rufen sieht meistens richtig aus
 * und kippt das Knie gelegentlich auf die falsche Seite.
 */
final class IKQPolZwang
{
    boolean aktiv;

    private IKQSegment spitze;
    private IKVec3 ziel = new IKVec3();
    private IKVec3 polZiel = new IKVec3();
    private double polWinkel;
    private boolean winkelHolen;

    void setzen(IKQSegment spitze, IKVec3 ziel, IKVec3 polZiel, double polWinkel, boolean winkelHolen)
    {
        this.aktiv = true;
        this.spitze = spitze;
        this.ziel = new IKVec3(ziel);
        this.polZiel = new IKVec3(polZiel);
        this.polWinkel = winkelHolen ? 0D : polWinkel;
        this.winkelHolen = winkelHolen;
    }

    void skalieren(double skala)
    {
        this.ziel = this.ziel.mal(skala);
        this.polZiel = this.polZiel.mal(skala);
    }

    /** Liefert die neue Wurzelmatrix (unveraendert, wenn der Zwang nicht greift). */
    IKAffine anwenden(IKQSegment wurzel, List<IKQTask> aufgaben, IKAffine wurzelMatrix)
    {
        if (!this.aktiv)
        {
            return wurzelMatrix;
        }

        int positionsAufgaben = 0;

        for (IKQTask t : aufgaben)
        {
            if (t.positionsAufgabe())
            {
                positionsAufgaben++;
            }
        }

        /* Mit zwei Positionszielen ist die Pol-Ebene nicht mehr eindeutig. */
        if (positionsAufgaben >= 2)
        {
            this.aktiv = false;

            return wurzelMatrix;
        }

        wurzel.aktualisiereTransform(wurzelMatrix);

        IKVec3 wurzelPos = wurzel.globalStart();
        IKVec3 endPos = this.spitze.globalEnd();
        IKMat3 wurzelBasis = wurzel.globalTransform().linear;

        IKVec3 dir = IKMath.normalisiert(endPos.minus(wurzelPos));
        IKVec3 wurzelX = wurzelBasis.spalte(0);
        IKVec3 wurzelZ = wurzelBasis.spalte(2);
        IKVec3 up = wurzelX.mal(Math.cos(this.polWinkel))
            .plus(wurzelZ.mal(Math.sin(this.polWinkel)));

        IKVec3 polDir = this.winkelHolen ? dir : IKMath.normalisiert(this.ziel.minus(wurzelPos));
        IKVec3 polUp = IKMath.normalisiert(this.polZiel.minus(wurzelPos));

        /* "lookat"-Matrizen wie gluLookAt: Blickrichtung Wurzel -> Ende,
         * Oben-Richtung Wurzel -> Pol. */
        IKMat3 mat = new IKMat3();
        IKMat3 polMat = new IKMat3();

        mat.setzeZeile(0, IKMath.normalisiert(dir.kreuz(up)));
        mat.setzeZeile(1, mat.zeile(0).kreuz(dir));
        mat.setzeZeile(2, dir.mal(-1D));

        polMat.setzeZeile(0, IKMath.normalisiert(polDir.kreuz(polUp)));
        polMat.setzeZeile(1, polMat.zeile(0).kreuz(polDir));
        polMat.setzeZeile(2, polDir.mal(-1D));

        if (this.winkelHolen)
        {
            this.polWinkel = IKMath.winkel(mat.zeile(1), polMat.zeile(1));

            double dt = wurzelZ.punkt(mat.zeile(1).mal(Math.cos(this.polWinkel))
                .plus(mat.zeile(0).mal(Math.sin(this.polWinkel))));

            if (dt > 0D)
            {
                this.polWinkel = -this.polWinkel;
            }

            this.winkelHolen = false;

            return this.anwenden(wurzel, aufgaben, wurzelMatrix);
        }

        /* Transponiert statt invertiert: die Matrizen sind orthogonal, und im
         * singulaeren Fall gibt es so kein NaN. */
        IKAffine trans = new IKAffine();

        trans.linear = polMat.transponiert().mal(mat);

        return trans.mal(wurzelMatrix);
    }
}
