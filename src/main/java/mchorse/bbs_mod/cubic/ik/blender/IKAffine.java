/* Ersatz fuer Eigen::Affine3d im Port von Blenders intern/iksolver.
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Direct-Blender-Port: https://projects.blender.org/blender/blender/src/commit/67807e1800cc48cc7bff3c793525e1179a4d64ca/intern/iksolver/intern/IK_Math.h
 * Package baseline only: independent Java support, not copied Eigen source.
 * BBS-Modification: 2026-10-10 - Re-audited against pinned Blender; original revision unknown.
 * See docs/BLENDER-IK-PROVENANCE-2026-10-10.md for scope and parity evidence. */
package mchorse.bbs_mod.cubic.ik.blender;

/**
 * Affine Abbildung als Drehteil + Verschiebung, getrennt gehalten wie
 * {@code Eigen::Affine3d} sie ueber {@code .linear()} und {@code .translation()}
 * anbietet.
 *
 * ⛔ {@link #verschiebeLokal} entspricht Eigens {@code translate(v)} und rechnet
 * <b>im lokalen Rahmen</b>: {@code translation += linear * v}. Wer es global
 * addiert, verliert die Knochenlaenge in gedrehten Ketten — der Fehler ist bei
 * ungedrehter Ruhepose unsichtbar.
 */
public final class IKAffine
{
    public IKMat3 linear = IKMat3.identitaet();
    public IKVec3 translation = new IKVec3();

    public IKAffine()
    {}

    public IKAffine(IKAffine other)
    {
        this.set(other);
    }

    public IKAffine set(IKAffine other)
    {
        this.linear = new IKMat3(other.linear);
        this.translation = new IKVec3(other.translation);

        return this;
    }

    public void verschiebeLokal(IKVec3 v)
    {
        this.translation = this.translation.plus(this.linear.mal(v));
    }

    /** this * other, wie das Produkt zweier Affine3d. */
    public IKAffine mal(IKAffine other)
    {
        IKAffine r = new IKAffine();

        r.linear = this.linear.mal(other.linear);
        r.translation = this.translation.plus(this.linear.mal(other.translation));

        return r;
    }

    @Override
    public String toString()
    {
        return "IKAffine[t=" + this.translation + ", " + this.linear + "]";
    }
}
