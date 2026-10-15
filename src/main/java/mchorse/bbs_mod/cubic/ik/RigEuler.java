package mchorse.bbs_mod.cubic.ik;

/**
 * Blenders Euler-Maschinerie aus {@code blenlib/intern/math_rotation_c.cc} — gebraucht
 * von Copy Rotation, das NICHT ueber Quaternionen rechnet, sondern in Euler-Winkeln
 * einer bestimmten Ordnung. Ohne sie bedeutet "nur X kopieren" etwas anderes als in
 * Blender, und das ist bei Rigify der Normalfall: von 223 Copy Rotation nutzen nur 91
 * ueberhaupt alle drei Achsen (gemessen 2026-08-14 ueber acht Rigs).
 *
 * ⛔ Anordnung ist Blenders: {@code a[i * 3 + j]} ist {@code M[i][j]}, Komponente j der
 * Basis-SPALTE i — dieselbe Lesart wie in {@code BOBJBasisMathe}.
 */
public final class RigEuler
{
    private static final double PI2 = 2D * Math.PI;

    private RigEuler()
    {}

    /** Blenders {@code rotOrders}: {i, j, k, paritaet}. Unbekanntes faellt auf XYZ. */
    private static int[] ordnung(String name)
    {
        return switch (name == null ? "XYZ" : name)
        {
            case "XZY" -> new int[] {0, 2, 1, 1};
            case "YXZ" -> new int[] {1, 0, 2, 1};
            case "YZX" -> new int[] {1, 2, 0, 0};
            case "ZXY" -> new int[] {2, 0, 1, 0};
            case "ZYX" -> new int[] {2, 1, 0, 1};
            default -> new int[] {0, 1, 2, 0};
        };
    }

    /** {@code eulO_to_mat3} — Euler auf eine normalisierte 3x3 (float[9]). */
    public static float[] zuMatrix(float[] e, String ord)
    {
        int[] r = ordnung(ord);
        int i = r[0], j = r[1], k = r[2];
        double ti = r[3] != 0 ? -e[i] : e[i];
        double tj = r[3] != 0 ? -e[j] : e[j];
        double th = r[3] != 0 ? -e[k] : e[k];
        double ci = Math.cos(ti), cj = Math.cos(tj), ch = Math.cos(th);
        double si = Math.sin(ti), sj = Math.sin(tj), sh = Math.sin(th);
        double cc = ci * ch, cs = ci * sh, sc = si * ch, ss = si * sh;
        float[] m = new float[9];

        m[i * 3 + i] = (float) (cj * ch);
        m[j * 3 + i] = (float) (sj * sc - cs);
        m[k * 3 + i] = (float) (sj * cc + ss);
        m[i * 3 + j] = (float) (cj * sh);
        m[j * 3 + j] = (float) (sj * ss + cc);
        m[k * 3 + j] = (float) (sj * cs - sc);
        m[i * 3 + k] = (float) -sj;
        m[j * 3 + k] = (float) (cj * si);
        m[k * 3 + k] = (float) (cj * ci);

        return m;
    }

    /** {@code mat3_normalized_to_eulo2} — BEIDE Loesungen; die Wahl trifft der Aufrufer. */
    private static void zweiEuler(float[] m, String ord, float[] e1, float[] e2)
    {
        int[] r = ordnung(ord);
        int i = r[0], j = r[1], k = r[2];
        float cy = (float) Math.hypot(m[i * 3 + i], m[i * 3 + j]);

        if (cy > 0.0000375F)
        {
            e1[i] = (float) Math.atan2(m[j * 3 + k], m[k * 3 + k]);
            e1[j] = (float) Math.atan2(-m[i * 3 + k], cy);
            e1[k] = (float) Math.atan2(m[i * 3 + j], m[i * 3 + i]);

            e2[i] = (float) Math.atan2(-m[j * 3 + k], -m[k * 3 + k]);
            e2[j] = (float) Math.atan2(-m[i * 3 + k], -cy);
            e2[k] = (float) Math.atan2(-m[i * 3 + j], -m[i * 3 + i]);
        }
        else
        {
            e1[i] = (float) Math.atan2(-m[k * 3 + j], m[j * 3 + j]);
            e1[j] = (float) Math.atan2(-m[i * 3 + k], cy);
            e1[k] = 0F;

            System.arraycopy(e1, 0, e2, 0, 3);
        }

        if (r[3] != 0)
        {
            for (int a = 0; a < 3; a++)
            {
                e1[a] = -e1[a];
                e2[a] = -e2[a];
            }
        }
    }

    /** {@code mat3_normalized_to_eulO} — die Loesung mit den kleineren Winkeln. */
    public static float[] ausMatrix(float[] m, String ord)
    {
        float[] e1 = new float[3];
        float[] e2 = new float[3];

        zweiEuler(m, ord, e1, e2);

        return summe(e1) > summe(e2) ? e2 : e1;
    }

    /**
     * {@code mat3_normalized_to_compatible_eulO} — die Loesung, die dem Bezugswinkel am
     * naechsten liegt.
     *
     * ⛔ Nicht durch {@link #ausMatrix} ersetzen: ohne den Bezug springt der Winkel um
     * 2*pi oder in die zweite Loesung, und der Knochen kippt einmal ganz herum.
     */
    public static float[] ausMatrixNahe(float[] m, float[] bezug, String ord)
    {
        float[] e1 = new float[3];
        float[] e2 = new float[3];

        zweiEuler(m, ord, e1, e2);
        vertraeglich(e1, bezug);
        vertraeglich(e2, bezug);

        return abstand(e1, bezug) > abstand(e2, bezug) ? e2 : e1;
    }

    /** {@code compatible_eul} — Vielfache von 2*pi und Achsenflips herausrechnen. */
    public static void vertraeglich(float[] eul, float[] alt)
    {
        float[] d = new float[3];

        for (int i = 0; i < 3; i++)
        {
            d[i] = eul[i] - alt[i];

            if (d[i] > (float) Math.PI)
            {
                eul[i] -= (float) (Math.floor(d[i] / PI2 + 0.5D) * PI2);
                d[i] = eul[i] - alt[i];
            }
            else if (d[i] < -(float) Math.PI)
            {
                eul[i] += (float) (Math.floor(-d[i] / PI2 + 0.5D) * PI2);
                d[i] = eul[i] - alt[i];
            }
        }

        for (int i = 0, j = 1, k = 2; i < 3; j = k, k = i++)
        {
            if (Math.abs(d[i]) > Math.PI && Math.abs(d[j]) < Math.PI / 2D
                && Math.abs(d[k]) < Math.PI / 2D)
            {
                eul[i] += d[i] > 0F ? -(float) PI2 : (float) PI2;
            }
        }
    }

    /** {@code rotate_eulO} — eine zusaetzliche Achsendrehung in den Euler hineinrechnen. */
    public static void drehen(float[] beul, String ord, int achse, float winkel)
    {
        float[] e = new float[3];

        e[achse] = winkel;

        float[] m1 = zuMatrix(e, ord);
        float[] m2 = zuMatrix(beul, ord);
        float[] ges = new float[9];

        /* mul_m3_m3m3(totmat, mat2, mat1) in Blenders Spaltenanordnung. */
        for (int c = 0; c < 3; c++)
        {
            for (int r2 = 0; r2 < 3; r2++)
            {
                float s = 0F;

                for (int t = 0; t < 3; t++)
                {
                    s += m2[t * 3 + r2] * m1[c * 3 + t];
                }

                ges[c * 3 + r2] = s;
            }
        }

        System.arraycopy(ausMatrix(ges, ord), 0, beul, 0, 3);
    }

    private static float summe(float[] e)
    {
        return Math.abs(e[0]) + Math.abs(e[1]) + Math.abs(e[2]);
    }

    private static float abstand(float[] e, float[] b)
    {
        return Math.abs(e[0] - b[0]) + Math.abs(e[1] - b[1]) + Math.abs(e[2] - b[2]);
    }
}
