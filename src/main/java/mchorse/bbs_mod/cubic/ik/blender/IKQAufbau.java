/* Uebersetzt aus Blenders intern/iksolver/intern/IK_QJacobianSolver.cpp (Setup).
 * SPDX-FileCopyrightText: 2001-2002 NaN Holding BV. All rights reserved.
 * SPDX-License-ResourceLocation: GPL-2.0-or-later */
package mchorse.bbs_mod.cubic.ik.blender;

import java.util.List;

/**
 * Einmaliger Aufbau vor dem Loesen: jedem Freiheitsgrad und jeder Aufgabe eine
 * Nummer im Jacobi geben, die Aufgabengewichte auf Summe 1 normieren und die
 * Matrizen passend dimensionieren.
 *
 * ⛔ Die Normierung auf Summe 1 trennt PRIMAERE und ZWEITRANGIGE Aufgaben — zwei
 * getrennte Toepfe. Wer ueber alle normiert, gewichtet die Nebenaufgabe still
 * mit und bekommt eine Kette, die ihr Hauptziel nie ganz erreicht.
 */
final class IKQAufbau
{
    private IKQAufbau()
    {}

    /** @return true, wenn es zweitrangige Aufgaben gibt; null, wenn nicht loesbar. */
    static Boolean aufbauen(List<IKQSegment> segmente, IKQJacobian jacobian,
                            IKQJacobian jacobianSub, List<IKQTask> aufgaben)
    {
        int numDoF = 0;

        for (IKQSegment seg : segmente)
        {
            seg.setDoFId(numDoF);
            numDoF += seg.numberOfDoF();
        }

        if (numDoF == 0)
        {
            return null;
        }

        int primaerGroesse = 0;
        int zweitGroesse = 0;
        int zweitAnzahl = 0;
        double primaerGewicht = 0D;
        double zweitGewicht = 0D;

        for (IKQTask t : aufgaben)
        {
            if (t.primary())
            {
                t.setzeId(primaerGroesse);
                primaerGroesse += t.size();
                primaerGewicht += t.gewicht();
            }
            else
            {
                t.setzeId(zweitGroesse);
                zweitGroesse += t.size();
                zweitGewicht += t.gewicht();
                zweitAnzahl++;
            }
        }

        if (primaerGroesse == 0 || IKMath.fuzzyNull(primaerGewicht))
        {
            return null;
        }

        double primaerNeu = 1D / primaerGewicht;
        double zweitNeu = IKMath.fuzzyNull(zweitGewicht) ? 0D : 1D / zweitGewicht;

        for (IKQTask t : aufgaben)
        {
            t.setzeGewicht(t.gewicht() * (t.primary() ? primaerNeu : zweitNeu));
        }

        jacobian.armMatrices(numDoF, primaerGroesse);

        if (zweitAnzahl > 0)
        {
            jacobianSub.armMatrices(numDoF, zweitGroesse);
        }

        for (IKQSegment seg : segmente)
        {
            for (int i = 0; i < seg.numberOfDoF(); i++)
            {
                jacobian.setzeDoFGewicht(seg.doFId() + i, seg.gewicht(i));
            }
        }

        return zweitAnzahl > 0;
    }
}
