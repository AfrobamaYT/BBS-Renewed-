/* Uebersetzt aus Blenders intern/iksolver/intern/IK_QSegment.cpp / .h.
 * SPDX-FileCopyrightText: 2001-2002 NaN Holding BV. All rights reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later */
package mchorse.bbs_mod.cubic.ik.blender;

/**
 * Starres Glied: null Freiheitsgrade (alle drei IK-Achsen gesperrt, keine Dehnung). Es
 * traegt nur seine Ruhebasis und Laenge durch die Kette — Blenders {@code IK_QNullSegment}.
 */
public final class IKQNullSegment extends IKQSegment
{
    public IKQNullSegment()
    {
        super(0, false);
    }

    @Override
    public void setzeBasis(IKMat3 basis)
    {
        this.basis = IKMat3.identitaet();
    }

    @Override
    public IKVec3 achse(int dof)
    {
        return new IKVec3();
    }

    @Override
    public boolean aktualisiereWinkel(IKQJacobian jacobian, IKVec3 delta, boolean[] klemm)
    {
        return false;
    }

    @Override
    public void aktualisiereWinkelAnwenden()
    {}
}
