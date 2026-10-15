package mchorse.bbs_mod.cubic.ik;

/**
 * Blenders {@code easing.cc}: die Potenz- und Sinus-Kurven (cubic, quad, quart, quint, sine).
 */
public final class RigEasingKurven
{
    private RigEasingKurven()
    {}

    static float cubicEaseIn(float time, float begin, float change, float duration)
    {
        time /= duration;

        return change * time * time * time + begin;
    }

    static float cubicEaseOut(float time, float begin, float change, float duration)
    {
        time = time / duration - 1F;

        return change * (time * time * time + 1F) + begin;
    }

    static float cubicEaseInOut(float time, float begin, float change, float duration)
    {
        if ((time /= duration / 2F) < 1F)
        {
            return change / 2F * time * time * time + begin;
        }

        time -= 2F;

        return change / 2F * (time * time * time + 2F) + begin;
    }

    static float quadEaseIn(float time, float begin, float change, float duration)
    {
        time /= duration;

        return change * time * time + begin;
    }

    static float quadEaseOut(float time, float begin, float change, float duration)
    {
        time /= duration;

        return -change * time * (time - 2F) + begin;
    }

    static float quadEaseInOut(float time, float begin, float change, float duration)
    {
        if ((time /= duration / 2F) < 1F)
        {
            return change / 2F * time * time + begin;
        }

        time -= 1F;

        return -change / 2F * (time * (time - 2F) - 1F) + begin;
    }

    static float quartEaseIn(float time, float begin, float change, float duration)
    {
        time /= duration;

        return change * time * time * time * time + begin;
    }

    static float quartEaseOut(float time, float begin, float change, float duration)
    {
        time = time / duration - 1F;

        return -change * (time * time * time * time - 1F) + begin;
    }

    static float quartEaseInOut(float time, float begin, float change, float duration)
    {
        if ((time /= duration / 2F) < 1F)
        {
            return change / 2F * time * time * time * time + begin;
        }

        time -= 2F;

        return -change / 2F * (time * time * time * time - 2F) + begin;
    }

    static float quintEaseIn(float time, float begin, float change, float duration)
    {
        time /= duration;

        return change * time * time * time * time * time + begin;
    }

    static float quintEaseOut(float time, float begin, float change, float duration)
    {
        time = time / duration - 1F;

        return change * (time * time * time * time * time + 1F) + begin;
    }

    static float quintEaseInOut(float time, float begin, float change, float duration)
    {
        if ((time /= duration / 2F) < 1F)
        {
            return change / 2F * time * time * time * time * time + begin;
        }

        time -= 2F;

        return change / 2F * (time * time * time * time * time + 2F) + begin;
    }

    static float sineEaseIn(float time, float begin, float change, float duration)
    {
        return -change * (float) Math.cos(time / duration * (Math.PI / 2D)) + change + begin;
    }

    static float sineEaseOut(float time, float begin, float change, float duration)
    {
        return change * (float) Math.sin(time / duration * (Math.PI / 2D)) + begin;
    }

    static float sineEaseInOut(float time, float begin, float change, float duration)
    {
        return -change / 2F * ((float) Math.cos(Math.PI * time / duration) - 1F) + begin;
    }
}
