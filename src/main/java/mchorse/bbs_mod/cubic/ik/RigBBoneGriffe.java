package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.bobj.BOBJArmature;
import mchorse.bbs_mod.bobj.BOBJBBone;
import mchorse.bbs_mod.bobj.BOBJBone;
import static mchorse.bbs_mod.cubic.ik.RigBBoneParam.*;

/**
 * Blenders {@code BKE_pchan_bbone_handles_get} und {@code _spline_params_get}: die Griffknochen
 * und die Spline-Parameter eines Bendy Bones im eigenen Knochenraum.
 */
public final class RigBBoneGriffe
{
    private RigBBoneGriffe()
    {}

    /** {@code BKE_pchan_bbone_handles_get}. */
    static BOBJBone[] griffe(BOBJBone b)
    {
        BOBJBBone d = b.bbone;
        BOBJBone prev = "AUTO".equals(d.griffTyp[0]) ? (b.verbunden ? b.parentBone : null) : d.griffKnochen[0];
        BOBJBone next = "AUTO".equals(d.griffTyp[1]) ? b.verbundenesKind : d.griffKnochen[1];

        return new BOBJBone[] {prev, next};
    }

    static float[] kopf(float[] mat)
    {
        return RigMathe.ort(mat);
    }

    static float[] schwanz(float[] mat, float laenge)
    {
        return RigVektor.summe(RigMathe.ort(mat), RigVektor.mal(RigMathe.spalte(mat, 1), laenge));
    }

    /** {@code BKE_pchan_bbone_spline_params_get}. */
    static Param params(RigRahmen rahmen, BOBJBone b, boolean rest)
    {
        BOBJBBone d = b.bbone;
        Param p = new Param();

        p.segments = d.segmente;
        p.length = b.restLength;

        float[] poseMat = rahmen.poseMat(b);
        float[] armMat = rahmen.armMat(b);

        if (!rest)
        {
            float[] scale = RigMathe.groesse(poseMat);

            if (Math.abs(scale[0] - scale[1]) > 1.0E-6F || Math.abs(scale[1] - scale[2]) > 1.0E-6F)
            {
                p.doScale = true;
                p.scale = scale;
            }
        }

        BOBJBone[] g = griffe(b);
        BOBJBone prev = g[0];
        BOBJBone next = g[1];
        float[] imat;

        if (rest)
        {
            imat = RigMathe.invertiert(armMat);
        }
        else if (p.doScale)
        {
            float[] n = RigMathe.kopie(poseMat);

            RigMathe.normalisiere(n);
            imat = RigMathe.invertiert(n);
        }
        else
        {
            imat = RigMathe.invertiert(poseMat);
        }

        float[] prevScale = {1F, 1F, 1F};
        float[] nextScale = {1F, 1F, 1F};
        float[] eigenKopf = rest ? kopf(armMat) : kopf(poseMat);
        float[] eigenSchwanz = rest ? schwanz(armMat, p.length) : schwanz(poseMat, p.length);

        if (prev != null)
        {
            float[] prevPose = rahmen.poseMat(prev);
            float[] prevArm = rahmen.armMat(prev);
            float[] h1 = null;

            p.usePrev = true;

            if ("RELATIVE".equals(d.griffTyp[0]))
            {
                if (rest)
                {
                    p.prevH = new float[3];
                }
                else
                {
                    float[] delta = RigVektor.differenz(kopf(prevPose), kopf(prevArm));

                    h1 = RigVektor.differenz(kopf(poseMat), delta);
                }
            }
            else if ("TANGENT".equals(d.griffTyp[0]))
            {
                float[] delta = rest
                    ? RigVektor.differenz(schwanz(prevArm, prev.restLength), kopf(prevArm))
                    : RigVektor.differenz(schwanz(prevPose, prev.restLength), kopf(prevPose));

                h1 = RigVektor.differenz(eigenKopf, delta);
            }
            else
            {
                p.prevBbone = prev.bbone != null && prev.bbone.segmente > 1;
                h1 = rest ? kopf(prevArm) : kopf(prevPose);
            }

            if (h1 != null)
            {
                p.prevH = RigMathe.punkt(imat, h1);
            }

            if (!p.prevBbone)
            {
                p.prevMat = RigMathe.mul(imat, rest ? prevArm : prevPose);

                if (!rest && (d.griffSkala[0][0] || d.griffSkala[0][1] || d.griffSkala[0][2] || d.griffEase[0]))
                {
                    prevScale = RigMathe.groesse(RigRaum.poseZuKnochen(rahmen, prev, prevPose));
                }
            }
        }

        if (next != null)
        {
            float[] nextPose = rahmen.poseMat(next);
            float[] nextArm = rahmen.armMat(next);
            float[] h2 = null;

            p.useNext = true;

            if ("RELATIVE".equals(d.griffTyp[1]))
            {
                if (rest)
                {
                    p.nextH = new float[] {0F, p.length, 0F};
                }
                else
                {
                    float[] delta = RigVektor.differenz(kopf(nextPose), kopf(nextArm));

                    h2 = RigVektor.summe(schwanz(poseMat, p.length), delta);
                }
            }
            else if ("TANGENT".equals(d.griffTyp[1]))
            {
                float[] delta = rest
                    ? RigVektor.differenz(schwanz(nextArm, next.restLength), kopf(nextArm))
                    : RigVektor.differenz(schwanz(nextPose, next.restLength), kopf(nextPose));

                h2 = RigVektor.summe(eigenSchwanz, delta);
            }
            else
            {
                p.nextBbone = next.bbone != null && next.bbone.segmente > 1;
                h2 = rest ? schwanz(nextArm, next.restLength) : schwanz(nextPose, next.restLength);
            }

            if (h2 != null)
            {
                p.nextH = RigMathe.punkt(imat, h2);
            }

            p.nextMat = RigMathe.mul(imat, rest ? nextArm : nextPose);

            if (!rest && (d.griffSkala[1][0] || d.griffSkala[1][1] || d.griffSkala[1][2] || d.griffEase[1]))
            {
                nextScale = RigMathe.groesse(RigRaum.poseZuKnochen(rahmen, next, nextPose));
            }
        }

        eigenschaften(p, d, rest, prev, prevScale, nextScale);

        return p;
    }
}
