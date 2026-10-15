package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.bobj.BOBJBasisMathe;
import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.pose.Transform;

import java.util.List;
import static mchorse.bbs_mod.cubic.ik.RigZwangBasis.*;

/**
 * Blenders Track To, Locked Track und Damped Track ({@code constraint.cc}: vectomat,
 * trackto_, locktrack_, damptrack_evaluate).
 */
public final class RigZwangSpur
{
    private RigZwangSpur()
    {}

    /** {@code basis_cross}. */
    static int basisKreuz(int n, int m)
    {
        return switch (n - m)
        {
            case 1, -2 -> 1;
            case -1, 2 -> -1;
            default -> 0;
        };
    }

    /** {@code vectomat}. */
    static float[] vektorZuMatrix(float[] vec, float[] targetUp, int axis, int upflag, boolean targetZ)
    {
        float[] n = vec.clone();

        if (RigVektor.normalisiereV(n) == 0F)
        {
            n = new float[] {0F, 0F, 1F};
        }

        if (axis > 2)
        {
            axis -= 3;
        }
        else
        {
            n = RigVektor.mal(n, -1F);
        }

        float[] u = targetZ ? targetUp.clone() : new float[] {0F, 0F, 1F};
        float[] proj = RigVektor.differenz(u, RigVektor.projiziere(u, n));

        if (RigVektor.normalisiereV(proj) == 0F)
        {
            proj = new float[] {0F, 1F, 0F};
        }

        float[] right = RigVektor.einheitsvektor(RigVektor.kreuz(proj, n));
        float[] m = RigMatrix3.einheit3();

        if (axis != upflag)
        {
            int rightIndex = 3 - axis - upflag;
            float neg = basisKreuz(axis, upflag);

            m[rightIndex * 3] = neg * right[0];
            m[rightIndex * 3 + 1] = neg * right[1];
            m[rightIndex * 3 + 2] = neg * right[2];
            System.arraycopy(proj, 0, m, upflag * 3, 3);
            System.arraycopy(n, 0, m, axis * 3, 3);
        }

        return m;
    }

    /** {@code trackto_evaluate}. */
    public static void trackTo(float[] cob, Ziel ziel, MapType c)
    {
        float[] size = RigMathe.groesse(cob);

        for (int i = 0; i < 3; i++)
        {
            for (int j = 0; j < 3; j++)
            {
                cob[i * 4 + j] = i == j ? size[i] : 0F;
            }
        }

        float[] vec = RigVektor.differenz(RigMathe.ort(cob), RigMathe.ort(ziel.matrix()));
        int track = Math.max(0, Math.min(5, spurIndex(text(c, "track_axis", "spur_achse", "TRACK_Y"))));
        int up = Math.max(0, Math.min(2, spurIndex(text(c, "up_axis", "hoch_achse", "UP_Z"))));
        float[] totmat = vektorZuMatrix(vec, RigMathe.spalte(ziel.matrix(), 2), track, up,
            flag(c, "use_target_z", "ziel_z", false));

        System.arraycopy(RigMathe.mul3x4(totmat, cob), 0, cob, 0, 16);
    }

    /** {@code locktrack_evaluate}. */
    public static void lockedTrack(float[] cob, Ziel ziel, MapType c)
    {
        float[] vec = RigVektor.differenz(RigMathe.ort(ziel.matrix()), RigMathe.ort(cob));
        int lock = Math.max(0, Math.min(2, spurIndex(text(c, "lock_axis", "sperr_achse", "LOCK_Z"))));
        int track = spurIndex(text(c, "track_axis", "spur_achse", "TRACK_Y"));
        int trackAchse = track % 3;
        boolean negativ = track > 2;
        float[] totmat = RigMatrix3.einheit3();

        if (trackAchse != lock)
        {
            float[] lockVec = RigMathe.spalte(cob, lock);
            float[] t = RigVektor.differenz(vec, RigVektor.projiziere(vec, lockVec));

            RigVektor.normalisiereV(t);

            if (negativ)
            {
                t = RigVektor.mal(t, -1F);
            }

            float[] l = RigVektor.einheitsvektor(lockVec);
            int dritte = 3 - lock - trackAchse;
            /* Reihenfolge der Kreuzprodukte wie in Blenders 12 Faellen: bei zyklischem
             * (lock, track) ist die dritte = lock x track, sonst track x lock. */
            boolean zyklisch = trackAchse == (lock + 1) % 3;
            float[] d = zyklisch ? RigVektor.kreuz(l, t) : RigVektor.kreuz(t, l);

            System.arraycopy(t, 0, totmat, trackAchse * 3, 3);
            System.arraycopy(l, 0, totmat, lock * 3, 3);
            System.arraycopy(d, 0, totmat, dritte * 3, 3);
        }

        float[] tmp = RigMatrix3.normalisiere3(RigMathe.drei(cob));
        float[] delta = RigMatrix3.mul3(totmat, RigMatrix3.invertiert3(tmp));

        if (RigMatrix3.determinante(delta) == 0F)
        {
            delta = RigMatrix3.einheit3();
        }

        System.arraycopy(RigMathe.mul3x4(delta, cob), 0, cob, 0, 16);
    }

    /** {@code damptrack_do_transform}. */
    public static void dampedTrackTransform(float[] matrix, float[] tarvecIn, int trackAxis)
    {
        float[] tarvec = tarvecIn.clone();

        if (RigVektor.normalisiereV(tarvec) == 0F)
        {
            return;
        }

        float[] obvec = RigMathe.richtung(matrix, SPUR_VEKTOREN[trackAxis]);

        if (RigVektor.normalisiereV(obvec) == 0F)
        {
            obvec = SPUR_VEKTOREN[trackAxis].clone();
        }

        float[] obloc = RigMathe.ort(matrix);
        float[] raxis = RigVektor.kreuz(obvec, tarvec);
        float rangle = (float) Math.acos(Math.max(-1F, Math.min(1F, RigVektor.punktprodukt(obvec, tarvec))));
        float norm = RigVektor.normalisiereV(raxis);

        if (norm < 1.1920929E-7F)
        {
            if (Math.abs(rangle) < Math.PI - 0.01D)
            {
                return;
            }

            rangle = (float) Math.PI;

            float[] tmpvec = RigMathe.richtung(matrix, SPUR_VEKTOREN[(trackAxis + 1) % 6]);

            raxis = RigVektor.kreuz(obvec, tmpvec);

            if (RigVektor.normalisiereV(raxis) == 0F)
            {
                return;
            }
        }
        else if (norm < 0.1F)
        {
            rangle = rangle > Math.PI / 2D ? (float) (Math.PI - Math.asin(norm)) : (float) Math.asin(norm);
        }

        float[] rmat = RigAchse.achseWinkelZuMatrix(raxis, rangle);
        float[] tmat = RigMathe.mul3x4(rmat, matrix);

        System.arraycopy(tmat, 0, matrix, 0, 16);
        RigMathe.setzeOrt(matrix, obloc);
    }

    /** {@code damptrack_evaluate}. */
    public static void dampedTrack(float[] cob, Ziel ziel, MapType c)
    {
        float[] tarvec = RigVektor.differenz(RigMathe.ort(ziel.matrix()), RigMathe.ort(cob));

        dampedTrackTransform(cob, tarvec, spurIndex(text(c, "track_axis", "spur_achse", "TRACK_Y")));
    }
}
