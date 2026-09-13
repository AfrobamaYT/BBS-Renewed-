package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;

import java.util.ArrayList;
import java.util.List;

/**
 * Blenders F-Modifier ({@code fmodifier.cc}) auf einer Treiber- oder Action-Kurve: LIMITS und
 * STEPPED wirken auf die Zeit ({@code evaluate_time_fmodifiers}), GENERATOR, FNGENERATOR und
 * LIMITS auf den Wert ({@code evaluate_value_fmodifiers}), je ueber {@code eval_fmodifier_influence}
 * gemischt. Andere Typen (NOISE, CYCLES, ENVELOPE) lassen die Kurve unveraendert.
 */
final class RigKurveModifier
{
    private RigKurveModifier()
    {}

    private static boolean aktiv(MapType m, float evaltime)
    {
        if (m.getBool("mute", false))
        {
            return false;
        }

        return !m.getBool("use_restricted_range", false)
            || (m.getFloat("frame_start", 0F) <= evaltime && m.getFloat("frame_end", 0F) >= evaltime);
    }

    /** {@code eval_fmodifier_influence}. */
    private static float einfluss(MapType m, float evaltime)
    {
        float influence = m.getBool("use_influence", false) ? m.getFloat("influence", 1F) : 1F;

        if (m.getBool("use_restricted_range", false))
        {
            float sfra = m.getFloat("frame_start", 0F);
            float efra = m.getFloat("frame_end", 0F);
            float blendin = m.getFloat("blend_in", 0F);
            float blendout = m.getFloat("blend_out", 0F);

            if (evaltime < sfra || evaltime > efra)
            {
                return 0F;
            }

            if (blendin != 0F && evaltime >= sfra && evaltime <= sfra + blendin)
            {
                return influence * (evaltime - sfra) / blendin;
            }

            if (blendout != 0F && evaltime <= efra && evaltime >= efra - blendout)
            {
                return influence * (evaltime - efra) / (-blendout);
            }
        }

        return influence;
    }

    /** {@code evaluate_time_fmodifiers} — von hinten nach vorn. */
    static float zeit(List<MapType> modifier, float cvalue, float evaltime)
    {
        for (int i = modifier.size() - 1; i >= 0; i--)
        {
            MapType m = modifier.get(i);

            if (!aktiv(m, evaltime))
            {
                continue;
            }

            float nval;

            switch (m.getString("typ", ""))
            {
                case "LIMITS" ->
                {
                    nval = evaltime;

                    if (m.getBool("use_min_x", false) && evaltime < m.getFloat("min_x", 0F))
                    {
                        nval = m.getFloat("min_x", 0F);
                    }

                    if (m.getBool("use_max_x", false) && evaltime > m.getFloat("max_x", 0F))
                    {
                        nval = m.getFloat("max_x", 0F);
                    }
                }
                case "STEPPED" ->
                {
                    nval = evaltime;

                    boolean uebergehen = (m.getBool("use_frame_start", false) && evaltime < m.getFloat("frame_start", 0F))
                        || (m.getBool("use_frame_end", false) && evaltime > m.getFloat("frame_end", 0F));

                    if (!uebergehen)
                    {
                        float step = m.getFloat("frame_step", 2F);
                        float offset = m.getFloat("frame_offset", 0F);
                        int block = (int) ((evaltime - offset) / step);

                        nval = block * step + offset;
                    }
                }
                default ->
                {
                    continue;
                }
            }

            float f = einfluss(m, evaltime);

            evaltime = evaltime + (nval - evaltime) * f;
        }

        return evaltime;
    }

    /** {@code evaluate_value_fmodifiers} — von vorn nach hinten, je mit Einfluss gemischt. */
    static float wert(List<MapType> modifier, float cvalue, float evaltime)
    {
        for (MapType m : modifier)
        {
            if (!aktiv(m, evaltime))
            {
                continue;
            }

            float nval = cvalue;
            boolean additiv = m.getBool("use_additive", false);

            switch (m.getString("typ", ""))
            {
                case "GENERATOR" ->
                {
                    ListType k = m.getList("coefficients");

                    if (k == null || k.size() == 0)
                    {
                        continue;
                    }

                    if ("POLYNOMIAL_FACTORISED".equals(m.getString("mode", "POLYNOMIAL")))
                    {
                        float value = 1F;

                        for (int i = 0; i + 1 < k.size(); i += 2)
                        {
                            value *= k.getFloat(i) * evaltime + k.getFloat(i + 1);
                        }

                        nval = additiv ? cvalue + value : value;
                    }
                    else
                    {
                        float value = 0F;
                        float potenz = 1F;

                        for (int i = 0; i < k.size(); i++)
                        {
                            value += k.getFloat(i) * potenz;
                            potenz *= evaltime;
                        }

                        if (m.getInt("poly_order", 1) != 0)
                        {
                            nval = additiv ? cvalue + value : value;
                        }
                    }
                }
                case "FNGENERATOR" ->
                {
                    double arg = m.getFloat("phase_multiplier", 1F) * evaltime + m.getFloat("phase_offset", 0F);
                    Double fn = switch (m.getString("function_type", "SIN"))
                    {
                        case "SIN" -> Math.sin(arg);
                        case "COS" -> Math.cos(arg);
                        case "SINC" -> arg == 0D ? 1D : Math.sin(arg) / arg;
                        case "TAN" -> Math.abs(((arg - Math.PI / 2D) % Math.PI)) < 1.0E-9D ? null : Math.tan(arg);
                        case "LN" -> arg > 1D ? Math.log(arg) : null;
                        case "SQRT" -> arg > 0D ? Math.sqrt(arg) : null;
                        default -> null;
                    };

                    if (fn == null)
                    {
                        if (!additiv)
                        {
                            nval = 0F;
                        }
                    }
                    else
                    {
                        float value = (float) (m.getFloat("amplitude", 1F) * fn + m.getFloat("value_offset", 0F));

                        nval = additiv ? cvalue + value : value;
                    }
                }
                case "LIMITS" ->
                {
                    if (m.getBool("use_min_y", false) && nval < m.getFloat("min_y", 0F))
                    {
                        nval = m.getFloat("min_y", 0F);
                    }

                    if (m.getBool("use_max_y", false) && nval > m.getFloat("max_y", 0F))
                    {
                        nval = m.getFloat("max_y", 0F);
                    }
                }
                default ->
                {
                    continue;
                }
            }

            float f = einfluss(m, evaltime);

            cvalue = cvalue + (nval - cvalue) * f;
        }

        return cvalue;
    }
}
