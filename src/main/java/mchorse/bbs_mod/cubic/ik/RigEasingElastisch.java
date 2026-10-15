package mchorse.bbs_mod.cubic.ik;

/**
 * Blenders {@code easing.cc}: elastic (mit Amplitude/Periode) und expo.
 */
public final class RigEasingElastisch
{
    private RigEasingElastisch()
    {}

    static float elasticBlend(float time, float change, float duration, float amplitude, float s, float f)
    {
        if (change != 0F)
        {
            float t = Math.abs(s);

            if (amplitude != 0F)
            {
                f *= amplitude / Math.abs(change);
            }
            else
            {
                f = 0F;
            }

            if (Math.abs(time * duration) < t)
            {
                float l = Math.abs(time * duration) / t;

                f = (f * l) + (1F - l);
            }
        }

        return f;
    }

    static float elasticEaseIn(float time, float begin, float change, float duration, float amplitude, float period)
    {
        float s;
        float f = 1F;

        if (time == 0F)
        {
            return begin;
        }

        if ((time /= duration) == 1F)
        {
            return begin + change;
        }

        time -= 1F;

        if (period == 0F)
        {
            period = duration * 0.3F;
        }

        if (amplitude == 0F || amplitude < Math.abs(change))
        {
            s = period / 4F;
            f = elasticBlend(time, change, duration, amplitude, s, f);
            amplitude = change;
        }
        else
        {
            s = period / (2F * (float) Math.PI) * (float) Math.asin(change / amplitude);
        }

        return (-f * (amplitude * (float) Math.pow(2D, 10F * time)
            * (float) Math.sin((time * duration - s) * (2F * (float) Math.PI) / period))) + begin;
    }

    static float elasticEaseOut(float time, float begin, float change, float duration, float amplitude, float period)
    {
        float s;
        float f = 1F;

        if (time == 0F)
        {
            return begin;
        }

        if ((time /= duration) == 1F)
        {
            return begin + change;
        }

        time = -time;

        if (period == 0F)
        {
            period = duration * 0.3F;
        }

        if (amplitude == 0F || amplitude < Math.abs(change))
        {
            s = period / 4F;
            f = elasticBlend(time, change, duration, amplitude, s, f);
            amplitude = change;
        }
        else
        {
            s = period / (2F * (float) Math.PI) * (float) Math.asin(change / amplitude);
        }

        return (f * (amplitude * (float) Math.pow(2D, 10F * time)
            * (float) Math.sin((time * duration - s) * (2F * (float) Math.PI) / period))) + change + begin;
    }

    static float elasticEaseInOut(float time, float begin, float change, float duration, float amplitude, float period)
    {
        float s;
        float f = 1F;

        if (time == 0F)
        {
            return begin;
        }

        if ((time /= duration / 2F) == 2F)
        {
            return begin + change;
        }

        time -= 1F;

        if (period == 0F)
        {
            period = duration * (0.3F * 1.5F);
        }

        if (amplitude == 0F || amplitude < Math.abs(change))
        {
            s = period / 4F;
            f = elasticBlend(time, change, duration, amplitude, s, f);
            amplitude = change;
        }
        else
        {
            s = period / (2F * (float) Math.PI) * (float) Math.asin(change / amplitude);
        }

        if (time < 0F)
        {
            f *= -0.5F;

            return (f * (amplitude * (float) Math.pow(2D, 10F * time)
                * (float) Math.sin((time * duration - s) * (2F * (float) Math.PI) / period))) + begin;
        }

        time = -time;
        f *= 0.5F;

        return (f * (amplitude * (float) Math.pow(2D, 10F * time)
            * (float) Math.sin((time * duration - s) * (2F * (float) Math.PI) / period))) + change + begin;
    }

    static final float POW_MIN = 0.0009765625F;

    static final float POW_SCALE = 1F / (1F - 0.0009765625F);

    static float expoEaseIn(float time, float begin, float change, float duration)
    {
        if (time == 0F)
        {
            return begin;
        }

        return change * ((float) Math.pow(2D, 10F * (time / duration - 1F)) - POW_MIN) * POW_SCALE + begin;
    }

    static float expoEaseOut(float time, float begin, float change, float duration)
    {
        if (time == 0F)
        {
            return begin;
        }

        return change * (1F - ((float) Math.pow(2D, -10F * time / duration) - POW_MIN) * POW_SCALE) + begin;
    }

    static float expoEaseInOut(float time, float begin, float change, float duration)
    {
        float halb = duration / 2F;
        float changeHalb = change / 2F;

        if (time <= halb)
        {
            return expoEaseIn(time, begin, changeHalb, halb);
        }

        return expoEaseOut(time - halb, begin + changeHalb, changeHalb, halb);
    }
}
