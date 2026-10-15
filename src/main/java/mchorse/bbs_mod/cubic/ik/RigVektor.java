package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.bobj.BOBJBasisMathe;
import mchorse.bbs_mod.cubic.ik.blender.IKMatN;
import mchorse.bbs_mod.cubic.ik.blender.IKSvd;

import org.joml.Matrix4f;

/**
 * Blenders Vektorrechnung auf float[3] ({@code math_vector.cc}, {@code math_geom.cc}): Laenge,
 * Produkte, Projektion, Mischung, Huellen-Gewicht ({@code distfactor_to_bone}) und der
 * Kreis-Tangentenfaktor der Bendy Bones.
 */
public final class RigVektor
{
    private RigVektor()
    {}

    public static float punktprodukt(float[] a, float[] b)
    {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    public static float[] kreuz(float[] a, float[] b)
    {
        return new float[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    public static float[] differenz(float[] a, float[] b)
    {
        return new float[] {a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    public static float[] summe(float[] a, float[] b)
    {
        return new float[] {a[0] + b[0], a[1] + b[1], a[2] + b[2]};
    }

    public static float[] mal(float[] a, float f)
    {
        return new float[] {a[0] * f, a[1] * f, a[2] * f};
    }

    public static float[] mischeV(float[] a, float[] b, float t)
    {
        return new float[] {a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
    }

    /** Normalisiert; gibt die Laenge zurueck (0 laesst den Vektor auf 0). */
    public static float normalisiereV(float[] v)
    {
        float l = laenge(v);

        if (l != 0F)
        {
            v[0] /= l;
            v[1] /= l;
            v[2] /= l;
        }

        return l;
    }

    public static float[] einheitsvektor(float[] v)
    {
        float[] e = v.clone();

        normalisiereV(e);

        return e;
    }

    /** {@code project_v3_v3v3(r, a, b)} — a auf b projiziert. */
    public static float[] projiziere(float[] a, float[] b)
    {
        float bb = punktprodukt(b, b);

        return bb == 0F ? new float[3] : mal(b, punktprodukt(a, b) / bb);
    }

    /** {@code distfactor_to_bone} — Huellen-Gewicht eines Punktes zu einem Knochen. */
    public static float huellenFaktor(float[] p, float[] kopf, float[] schwanz, float rKopf,
                                      float rSchwanz, float abstand)
    {
        float[] achse = differenz(schwanz, kopf);
        float laenge = normalisiereV(achse);
        float hoehe = punktprodukt(differenz(p, kopf), achse);

        if (hoehe < 0F)
        {
            return huellenAbfall(abstandQuadrat(p, kopf), rKopf, abstand);
        }

        if (hoehe > laenge)
        {
            return huellenAbfall(abstandQuadrat(schwanz, p), rSchwanz, abstand);
        }

        float d2 = abstandQuadrat(p, kopf) - hoehe * hoehe;
        float radius = laenge != 0F ? rKopf + (rSchwanz - rKopf) * (hoehe / laenge) : rKopf;

        return huellenAbfall(d2, radius, abstand);
    }

    static float abstandQuadrat(float[] a, float[] b)
    {
        float[] d = differenz(a, b);

        return punktprodukt(d, d);
    }

    static float huellenAbfall(float d2, float radius, float abstand)
    {
        if (d2 < radius * radius)
        {
            return 1F;
        }

        if (abstand == 0F || d2 >= (radius + abstand) * (radius + abstand))
        {
            return 0F;
        }

        float d = (float) Math.sqrt(d2) - radius;

        return 1F - (d * d) / (abstand * abstand);
    }

    /** {@code cubic_tangent_factor_circle_v3}. */
    public static float kreisTangentenFaktor(float[] tanL, float[] tanR)
    {
        final float eps = 1.0E-5F;
        float dot = punktprodukt(tanL, tanR);

        if (dot > 1F - eps)
        {
            return (1F / 3F) * 0.75F;
        }

        if (dot < -1F + eps)
        {
            return 0.5F;
        }

        float winkel = (float) Math.acos(dot) / 2F;
        float sin = (float) Math.sin(winkel);
        float cos = (float) Math.cos(winkel);

        return ((1F - cos) / (sin * 2F)) / sin;
    }

    public static float laenge(float[] v)
    {
        return (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
    }
}
