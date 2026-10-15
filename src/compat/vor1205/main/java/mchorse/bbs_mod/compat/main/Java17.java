package mchorse.bbs_mod.compat.main;

/**
 * vor1205: Minecraft 1.20.x laeuft beim Spieler mit Java 17 -- die Jar wird dafuer mit release 17
 * gebaut. Hier die Java-20/21-Methoden, die BBS benutzt, mit demselben Verhalten wie im JDK.
 */
public final class Java17
{
    private Java17()
    {}

    public static float clamp(float wert, float min, float max)
    {
        if (min > max) throw new IllegalArgumentException(min + " > " + max);

        return Math.min(max, Math.max(wert, min));
    }

    public static double clamp(double wert, double min, double max)
    {
        if (min > max) throw new IllegalArgumentException(min + " > " + max);

        return Math.min(max, Math.max(wert, min));
    }

    public static int clamp(long wert, int min, int max)
    {
        if (min > max) throw new IllegalArgumentException(min + " > " + max);

        return (int) Math.min(max, Math.max(wert, min));
    }

    public static long clamp(long wert, long min, long max)
    {
        if (min > max) throw new IllegalArgumentException(min + " > " + max);

        return Math.min(max, Math.max(wert, min));
    }

    /** IEEE-754 binary16 nach float (wie Float.float16ToFloat, Java 20). */
    public static float float16ToFloat(short halb)
    {
        int bits = halb & 0xFFFF;
        int vorzeichen = (bits >>> 15) << 31;
        int exponent = (bits >>> 10) & 0x1F;
        int mantisse = bits & 0x3FF;

        if (exponent == 0x1F)
        {
            return Float.intBitsToFloat(vorzeichen | 0x7F800000 | (mantisse << 13));
        }

        if (exponent == 0)
        {
            if (mantisse == 0) return Float.intBitsToFloat(vorzeichen);

            /* Subnormal: Wert = mantisse * 2^-24. */
            float wert = mantisse * 0x1p-24F;

            return vorzeichen != 0 ? -wert : wert;
        }

        return Float.intBitsToFloat(vorzeichen | ((exponent + 112) << 23) | (mantisse << 13));
    }

    /** float nach IEEE-754 binary16, rundet zur naechsten geraden Zahl (wie Float.floatToFloat16). */
    public static short floatToFloat16(float wert)
    {
        int bits = Float.floatToRawIntBits(wert);
        int vorzeichen = (bits >>> 16) & 0x8000;
        int exponent = (bits >>> 23) & 0xFF;
        int mantisse = bits & 0x7FFFFF;

        if (exponent == 0xFF)
        {
            /* Unendlich bleibt unendlich, NaN bleibt NaN (oberste Mantissenbits erhalten, mindestens eins). */
            return (short) (vorzeichen | 0x7C00 | (mantisse == 0 ? 0 : (mantisse >>> 13) | 0x200));
        }

        int e = exponent - 127 + 15;

        if (e >= 0x1F)
        {
            return (short) (vorzeichen | 0x7C00);
        }

        if (e <= 0)
        {
            if (e < -10) return (short) vorzeichen;

            int m = mantisse | 0x800000;
            int schiebe = 14 - e;
            int ergebnis = m >>> schiebe;
            int rest = m & ((1 << schiebe) - 1);
            int haelfte = 1 << (schiebe - 1);

            if (rest > haelfte || (rest == haelfte && (ergebnis & 1) != 0)) ergebnis++;

            return (short) (vorzeichen | ergebnis);
        }

        int ergebnis = (e << 10) | (mantisse >>> 13);
        int rest = mantisse & 0x1FFF;

        if (rest > 0x1000 || (rest == 0x1000 && (ergebnis & 1) != 0)) ergebnis++;

        return (short) (vorzeichen | ergebnis);
    }
}
