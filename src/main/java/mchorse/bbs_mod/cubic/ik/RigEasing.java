package mchorse.bbs_mod.cubic.ik;

import static mchorse.bbs_mod.cubic.ik.RigEasingElastisch.*;
import static mchorse.bbs_mod.cubic.ik.RigEasingKurven.*;

/**
 * Blenders {@code blenlib/intern/easing.cc} — die Easing-Gleichungen, wie F-Kurven sie fuer
 * die Interpolationsarten BACK, BOUNCE, CIRC, CUBIC, ELASTIC, EXPO, QUAD, QUART, QUINT und
 * SINE benutzen, samt Blenders Vorgabe je Art, wenn das Easing auf AUTO steht.
 */
public final class RigEasing
{
    private RigEasing()
    {}

    /** {@code fcurve_eval_keyframes_interpolate}, der Easing-Teil. */
    public static float wert(String interp, String easing, float time, float begin, float change,
                             float duration, float back, float amplitude, float period)
    {
        boolean ein = "EASE_IN".equals(easing);
        boolean aus = "EASE_OUT".equals(easing);
        boolean beides = "EASE_IN_OUT".equals(easing);

        switch (interp)
        {
            case "BACK":
                if (ein) return backEaseIn(time, begin, change, duration, back);
                if (beides) return backEaseInOut(time, begin, change, duration, back);
                return backEaseOut(time, begin, change, duration, back);
            case "BOUNCE":
                if (ein) return bounceEaseIn(time, begin, change, duration);
                if (beides) return bounceEaseInOut(time, begin, change, duration);
                return bounceEaseOut(time, begin, change, duration);
            case "CIRC":
                if (aus) return circEaseOut(time, begin, change, duration);
                if (beides) return circEaseInOut(time, begin, change, duration);
                return circEaseIn(time, begin, change, duration);
            case "CUBIC":
                if (aus) return cubicEaseOut(time, begin, change, duration);
                if (beides) return cubicEaseInOut(time, begin, change, duration);
                return cubicEaseIn(time, begin, change, duration);
            case "ELASTIC":
                if (ein) return elasticEaseIn(time, begin, change, duration, amplitude, period);
                if (beides) return elasticEaseInOut(time, begin, change, duration, amplitude, period);
                return elasticEaseOut(time, begin, change, duration, amplitude, period);
            case "EXPO":
                if (aus) return expoEaseOut(time, begin, change, duration);
                if (beides) return expoEaseInOut(time, begin, change, duration);
                return expoEaseIn(time, begin, change, duration);
            case "QUAD":
                if (aus) return quadEaseOut(time, begin, change, duration);
                if (beides) return quadEaseInOut(time, begin, change, duration);
                return quadEaseIn(time, begin, change, duration);
            case "QUART":
                if (aus) return quartEaseOut(time, begin, change, duration);
                if (beides) return quartEaseInOut(time, begin, change, duration);
                return quartEaseIn(time, begin, change, duration);
            case "QUINT":
                if (aus) return quintEaseOut(time, begin, change, duration);
                if (beides) return quintEaseInOut(time, begin, change, duration);
                return quintEaseIn(time, begin, change, duration);
            case "SINE":
                if (aus) return sineEaseOut(time, begin, change, duration);
                if (beides) return sineEaseInOut(time, begin, change, duration);
                return sineEaseIn(time, begin, change, duration);
            default:
                return begin;
        }
    }

    static float backEaseIn(float time, float begin, float change, float duration, float overshoot)
    {
        time /= duration;

        return change * time * time * ((overshoot + 1F) * time - overshoot) + begin;
    }

    static float backEaseOut(float time, float begin, float change, float duration, float overshoot)
    {
        time = time / duration - 1F;

        return change * (time * time * ((overshoot + 1F) * time + overshoot) + 1F) + begin;
    }

    static float backEaseInOut(float time, float begin, float change, float duration, float overshoot)
    {
        overshoot *= 1.525F;

        if ((time /= duration / 2F) < 1F)
        {
            return change / 2F * (time * time * ((overshoot + 1F) * time - overshoot)) + begin;
        }

        time -= 2F;

        return change / 2F * (time * time * ((overshoot + 1F) * time + overshoot) + 2F) + begin;
    }

    static float bounceEaseOut(float time, float begin, float change, float duration)
    {
        time /= duration;

        if (time < 1F / 2.75F)
        {
            return change * (7.5625F * time * time) + begin;
        }

        if (time < 2F / 2.75F)
        {
            time -= 1.5F / 2.75F;

            return change * ((7.5625F * time) * time + 0.75F) + begin;
        }

        if (time < 2.5F / 2.75F)
        {
            time -= 2.25F / 2.75F;

            return change * ((7.5625F * time) * time + 0.9375F) + begin;
        }

        time -= 2.625F / 2.75F;

        return change * ((7.5625F * time) * time + 0.984375F) + begin;
    }

    static float bounceEaseIn(float time, float begin, float change, float duration)
    {
        return change - bounceEaseOut(duration - time, 0F, change, duration) + begin;
    }

    static float bounceEaseInOut(float time, float begin, float change, float duration)
    {
        if (time < duration / 2F)
        {
            return bounceEaseIn(time * 2F, 0F, change, duration) * 0.5F + begin;
        }

        return bounceEaseOut(time * 2F - duration, 0F, change, duration) * 0.5F + change * 0.5F + begin;
    }

    static float circEaseIn(float time, float begin, float change, float duration)
    {
        time /= duration;

        return -change * ((float) Math.sqrt(1F - time * time) - 1F) + begin;
    }

    static float circEaseOut(float time, float begin, float change, float duration)
    {
        time = time / duration - 1F;

        return change * (float) Math.sqrt(1F - time * time) + begin;
    }

    static float circEaseInOut(float time, float begin, float change, float duration)
    {
        if ((time /= duration / 2F) < 1F)
        {
            return -change / 2F * ((float) Math.sqrt(1F - time * time) - 1F) + begin;
        }

        time -= 2F;

        return change / 2F * ((float) Math.sqrt(1F - time * time) + 1F) + begin;
    }
}

