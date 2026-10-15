/* Uebersetzt aus Blenders intern/iksolver/intern/IK_Math.h.
 * SPDX-FileCopyrightText: 2001-2002 NaN Holding BV. All rights reserved.
 * SPDX-License-ResourceLocation: GPL-2.0-or-later */
package mchorse.bbs_mod.cubic.ik.blender;

/**
 * Zeile-fuer-Zeile aus IK_Math.h. Nichts hier ist erfunden oder "verbessert" —
 * Abweichungen aendern das Ergebnis der Jacobi-Iteration, und zwar unauffaellig.
 */
public final class IKMath
{
    public static final double IK_EPSILON = 1e-20;

    private IKMath()
    {}

    public static boolean fuzzyNull(double x)
    {
        return Math.abs(x) < IK_EPSILON;
    }

    public static double klemme(double x, double min, double max)
    {
        return x < min ? min : x > max ? max : x;
    }

    public static IKMat3 drehmatrix(double sinus, double kosinus, int achse)
    {
        if (achse == 0)
        {
            return IKMat3.von(1D, 0D, 0D, 0D, kosinus, -sinus, 0D, sinus, kosinus);
        }

        if (achse == 1)
        {
            return IKMat3.von(kosinus, 0D, sinus, 0D, 1D, 0D, -sinus, 0D, kosinus);
        }

        return IKMat3.von(kosinus, -sinus, 0D, sinus, kosinus, 0D, 0D, 0D, 1D);
    }

    public static IKMat3 drehmatrix(double winkel, int achse)
    {
        return drehmatrix(Math.sin(winkel), Math.cos(winkel), achse);
    }

    public static double eulerWinkelAusMatrix(IKMat3 R, int achse)
    {
        double t = Math.sqrt(R.get(0, 0) * R.get(0, 0) + R.get(0, 1) * R.get(0, 1));

        if (t > 16D * IK_EPSILON)
        {
            if (achse == 0) return -Math.atan2(R.get(1, 2), R.get(2, 2));
            if (achse == 1) return Math.atan2(-R.get(0, 2), t);

            return -Math.atan2(R.get(0, 1), R.get(0, 0));
        }

        if (achse == 0) return -Math.atan2(-R.get(2, 1), R.get(1, 1));
        if (achse == 1) return Math.atan2(-R.get(0, 2), t);

        return 0D;
    }

    /** acos, das bei Rundungsfehlern kein NaN liefert. */
    public static double sicheresAcos(double f)
    {
        if (f <= -1D) return Math.PI;
        if (f >= 1D) return 0D;

        return Math.acos(f);
    }

    /** Normalisieren, das bei Laenge 0 auch (0,0,0) liefert — nicht (1,0,0). */
    public static IKVec3 normalisiert(IKVec3 v)
    {
        double len = v.laenge();

        return fuzzyNull(len) ? new IKVec3(0D, 0D, 0D) : v.mal(1D / len);
    }

    public static double winkel(IKVec3 v1, IKVec3 v2)
    {
        return sicheresAcos(v1.punkt(v2));
    }

    public static double berechneTwist(IKMat3 R)
    {
        double qy = R.get(0, 2) - R.get(2, 0);
        double qw = R.get(0, 0) + R.get(1, 1) + R.get(2, 2) + 1D;

        return 2D * Math.atan2(qy, qw);
    }

    public static IKMat3 twistMatrix(double tau)
    {
        return drehmatrix(tau, 1);
    }

    /** R := R * T^T, wobei T der Twist um Y ist. */
    public static IKMat3 ohneTwist(IKMat3 R)
    {
        return R.mal(twistMatrix(berechneTwist(R)).transponiert());
    }

    /** (ax, tau, az) — Swing-Parameter plus Twist, wie SphericalRangeParameters. */
    public static IKVec3 sphaerischeBereichsParameter(IKMat3 R)
    {
        double tau = berechneTwist(R);
        double num = 2D * (1D + R.get(1, 1));

        /* Singularitaet bei pi: Blender gibt hier eine Drehung um Z zurueck und
         * merkt selbst an, dass die Achse beliebig sein koennte. Uebernommen. */
        if (Math.abs(num) < IK_EPSILON)
        {
            return new IKVec3(0D, tau, 1D);
        }

        num = 1D / Math.sqrt(num);

        return new IKVec3(-R.get(2, 1) * num, tau, R.get(0, 1) * num);
    }

    public static IKMat3 swingMatrix(double ax, double az)
    {
        double sine2 = ax * ax + az * az;
        double cosine2 = Math.sqrt(sine2 >= 1D ? 0D : 1D - sine2);

        /* Eigen::Quaterniond(w, x, y, z) mit w = -cosine2, y = 0. */
        return ausQuaternion(-cosine2, ax, 0D, az);
    }

    /** Quaternion (w, x, y, z) als Drehmatrix, zeilenmajor. */
    public static IKMat3 ausQuaternion(double w, double x, double y, double z)
    {
        return IKMat3.von(
            1D - 2D * (y * y + z * z), 2D * (x * y - w * z), 2D * (x * z + w * y),
            2D * (x * y + w * z), 1D - 2D * (x * x + z * z), 2D * (y * z - w * x),
            2D * (x * z - w * y), 2D * (y * z + w * x), 1D - 2D * (x * x + y * y));
    }

    public static IKVec3 matrixZuAchsenwinkel(IKMat3 R)
    {
        IKVec3 delta = new IKVec3(
            R.get(2, 1) - R.get(1, 2),
            R.get(0, 2) - R.get(2, 0),
            R.get(1, 0) - R.get(0, 1));

        double c = sicheresAcos((R.get(0, 0) + R.get(1, 1) + R.get(2, 2) - 1D) / 2D);
        double l = delta.laenge();

        return fuzzyNull(l) ? delta : delta.mal(c / l);
    }

    /**
     * Klemmt (ax, az) in die Ellipse aus amin/amax. Rueckgabe: wurde geklemmt?
     * Die geklemmten Werte stehen danach in {@code werte[0]} und {@code werte[1]} —
     * Javas fehlende Referenzparameter, sonst unveraendert uebernommen.
     */
    public static boolean ellipseKlemmen(double[] werte, double[] amin, double[] amax)
    {
        double ax = werte[0];
        double az = werte[1];
        double xlim;
        double zlim;
        double x;
        double z;

        if (ax < 0D) { x = -ax; xlim = -amin[0]; }
        else { x = ax; xlim = amax[0]; }

        if (az < 0D) { z = -az; zlim = -amin[1]; }
        else { z = az; zlim = amax[1]; }

        if (fuzzyNull(xlim) || fuzzyNull(zlim))
        {
            if (x <= xlim && z <= zlim)
            {
                return false;
            }

            x = Math.min(x, xlim);
            z = Math.min(z, zlim);
        }
        else
        {
            double invx = 1D / (xlim * xlim);
            double invz = 1D / (zlim * zlim);

            if (x * x * invx + z * z * invz <= 1D)
            {
                return false;
            }

            if (fuzzyNull(x))
            {
                x = 0D;
                z = zlim;
            }
            else
            {
                double rico = z / x;
                double altX = x;

                x = Math.sqrt(1D / (invx + invz * rico * rico));

                if (altX < 0D)
                {
                    x = -x;
                }

                z = rico * x;
            }
        }

        werte[0] = ax < 0D ? -x : x;
        werte[1] = az < 0D ? -z : z;

        return true;
    }
}
