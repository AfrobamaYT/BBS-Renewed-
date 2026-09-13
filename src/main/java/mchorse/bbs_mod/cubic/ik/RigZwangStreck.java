package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.bobj.BOBJBasisMathe;
import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.pose.Transform;

import java.util.List;
import static mchorse.bbs_mod.cubic.ik.RigZwangBasis.*;
import static mchorse.bbs_mod.cubic.ik.RigZwangSpur.*;

/**
 * Blenders Stretch To ({@code constraint.cc}: stretchto_evaluate) mit keep_axis, Volumen
 * und Bulge-Grenzen; die Ruhelaenge wird beim ersten Lauf gemessen wie in Blender.
 */
public final class RigZwangStreck
{
    private RigZwangStreck()
    {}

    /** {@code stretchto_evaluate}. */
    public static void stretchTo(float[] cob, Ziel ziel, MapType c, BOBJBone besitzer)
    {
        String plane = text(c, "keep_axis", null, "SWING_Y");

        if ("SWING_Y".equals(plane))
        {
            BOBJBasisMathe.orthogonalisiereUmY(cob, false);
        }

        float[] size = RigMathe.normalisiere(cob);
        float[] xx = RigMathe.spalte(cob, 0);
        float[] zz = RigMathe.spalte(cob, 2);
        float[] vec = RigVektor.differenz(RigMathe.ort(ziel.matrix()), RigMathe.ort(cob));
        float dist = RigVektor.normalisiereV(vec);

        dist = size[1] == 0F ? 0F : dist / size[1];

        float orglength = zahl(c, "rest_length", "ruhe_laenge", 0F);

        /* data->orglength == 0 heisst "beim ersten Lauf messen" — Blender schreibt den
         * Wert zurueck und rechnet ab da damit. */
        if (orglength == 0F)
        {
            orglength = dist;
            merke(c, "rest_length", "ruhe_laenge", dist);
        }

        if (orglength == 0F)
        {
            return;
        }

        float[] scale = new float[3];

        scale[1] = dist / orglength;

        float bulge = (float) Math.pow(orglength / dist, zahl(c, "bulge", "wulst", 1F));

        if (bulge > 1F && flag(c, "use_bulge_max", null, false))
        {
            float bulgeMax = Math.max(zahl(c, "bulge_max", null, 1F), 1F);
            float hard = Math.min(bulge, bulgeMax);
            float range = bulgeMax - 1F;
            float scaleFac = range > 0F ? 1F / range : 0F;
            float soft = 1F + range * (float) Math.atan((bulge - 1F) * scaleFac) / (float) (Math.PI / 2D);
            float smooth = zahl(c, "bulge_smooth", null, 0F);

            bulge = soft + (hard - soft) * (1F - smooth);
        }

        if (bulge < 1F && flag(c, "use_bulge_min", null, false))
        {
            float bulgeMin = Math.max(0F, Math.min(1F, zahl(c, "bulge_min", null, 1F)));
            float hard = Math.max(bulge, bulgeMin);
            float range = 1F - bulgeMin;
            float scaleFac = range > 0F ? 1F / range : 0F;
            float soft = 1F - range * (float) Math.atan((1F - bulge) * scaleFac) / (float) (Math.PI / 2D);
            float smooth = zahl(c, "bulge_smooth", null, 0F);

            bulge = soft + (hard - soft) * (1F - smooth);
        }

        switch (text(c, "volume", "volumen", "VOLUME_XZX"))
        {
            case "VOLUME_X" ->
            {
                scale[0] = bulge;
                scale[2] = 1F;
            }
            case "VOLUME_Z" ->
            {
                scale[0] = 1F;
                scale[2] = bulge;
            }
            case "NO_VOLUME" ->
            {
                scale[0] = 1F;
                scale[2] = 1F;
            }
            default ->
            {
                scale[0] = (float) Math.sqrt(bulge);
                scale[2] = scale[0];
            }
        }

        for (int i = 0; i < 3; i++)
        {
            size[i] *= scale[i];
        }

        switch (plane)
        {
            case "PLANE_X" ->
            {
                RigMathe.setzeSpalte(cob, 1, vec);

                float[] orth = RigVektor.einheitsvektor(RigVektor.kreuz(xx, vec));

                RigMathe.setzeSpalte(cob, 2, orth);
                RigMathe.setzeSpalte(cob, 0, RigVektor.einheitsvektor(RigVektor.kreuz(vec, orth)));
            }
            case "PLANE_Z" ->
            {
                RigMathe.setzeSpalte(cob, 1, vec);

                float[] orth = RigVektor.einheitsvektor(RigVektor.kreuz(zz, vec));

                RigMathe.setzeSpalte(cob, 0, RigVektor.mal(orth, -1F));
                RigMathe.setzeSpalte(cob, 2, RigVektor.einheitsvektor(RigVektor.kreuz(vec, orth)));
            }
            default -> dampedTrackTransform(cob, vec, 1);
        }

        RigMathe.skaliere(cob, size);
    }
}
