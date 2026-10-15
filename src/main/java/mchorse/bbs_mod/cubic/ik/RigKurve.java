package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;

import java.util.ArrayList;
import java.util.List;

/**
 * Blenders F-Kurve, wie sie ein Treiber benutzt: {@code evaluate_fcurve_driver} +
 * {@code evaluate_fcurve_ex} aus {@code fcurve.cc}, die Modifier aus {@code fmodifier.cc}
 * und die Easing-Gleichungen aus {@code easing.cc}. Eingabe ist der Treiberwert (bei
 * Treibern ersetzt er die Zeit), Ausgabe der Wert der Eigenschaft.
 *
 * <p>BEZIER-Stuetzpunkte werden wie in Blender ueber die Nullstelle des kubischen
 * X-Polynoms geloest ({@code findzero}/{@code berekeny}), nach {@code BKE_fcurve_correct_bezpart};
 * nicht ueber eine Bisektion mit Toleranz. Modifier: {@link RigKurveModifier}; Stuetzpunkt-
 * Interpolation: {@link RigKurveInterpolation}; kubische Loesung: {@link RigKurveKubisch}.
 */
public final class RigKurve
{
    /** Ein Stuetzpunkt ({@code BezTriple}): Punkt, linker und rechter Griff, Interpolation, Easing. */
    static final class Punkt
    {
        float x, y, hlx, hly, hrx, hry, back, amplitude, period;
        String interp = "BEZIER";
        String easing = "AUTO";
    }

    private final List<Punkt> punkte = new ArrayList<>();
    private final List<MapType> modifier = new ArrayList<>();
    private final boolean linearExtrapolation;

    private RigKurve(MapType kurve)
    {
        this.linearExtrapolation = kurve != null && "LINEAR".equals(kurve.getString("extrapolation"));

        ListType keys = kurve == null ? null : kurve.getList("keys");

        for (int i = 0; keys != null && i < keys.size(); i++)
        {
            MapType k = keys.getMap(i);
            Punkt p = new Punkt();

            p.x = k.getList("co").getFloat(0);
            p.y = k.getList("co").getFloat(1);
            p.hlx = k.getList("hl").getFloat(0);
            p.hly = k.getList("hl").getFloat(1);
            p.hrx = k.getList("hr").getFloat(0);
            p.hry = k.getList("hr").getFloat(1);
            p.interp = k.getString("interp", "BEZIER");
            p.easing = k.getString("easing", "AUTO");
            p.back = k.getFloat("back", 1.70158F);
            p.amplitude = k.getFloat("amplitude", 0F);
            p.period = k.getFloat("period", 0F);
            this.punkte.add(p);
        }

        ListType mods = kurve == null ? null : kurve.getList("modifiers");

        for (int i = 0; mods != null && i < mods.size(); i++)
        {
            this.modifier.add(mods.getMap(i));
        }
    }

    /** Aus dem exportierten {@code kurve}-Block; ein leerer Block ist die Identitaet. */
    public static RigKurve lesen(MapType kurve)
    {
        return new RigKurve(kurve);
    }

    /** Die {@code typ}-Werte der exportierten Modifier — damit der Treiber Unbekanntes zaehlen kann. */
    public static List<String> modifierTypen(MapType kurve)
    {
        List<String> typen = new ArrayList<>();
        ListType mods = kurve == null ? null : kurve.getList("modifiers");

        for (int i = 0; mods != null && i < mods.size(); i++)
        {
            typen.add(mods.getMap(i).getString("typ", ""));
        }

        return typen;
    }

    /** {@code evaluate_fcurve_driver}: Treiberwert rein, Eigenschaftswert raus. */
    public float wert(float treiberwert)
    {
        float evaltime = treiberwert;
        float cvalue = 0F;

        if (this.punkte.isEmpty())
        {
            boolean linear = true;

            for (MapType m : this.modifier)
            {
                if (m.getBool("use_restricted_range", false)
                    && !(m.getFloat("frame_start", 0F) <= evaltime && m.getFloat("frame_end", 0F) >= evaltime))
                {
                    linear = false;
                }
            }

            if (linear)
            {
                cvalue = evaltime;
            }
        }

        float devaltime = RigKurveModifier.zeit(this.modifier, cvalue, evaltime);

        if (!this.punkte.isEmpty())
        {
            cvalue = this.stuetzpunkte(devaltime);
        }

        return RigKurveModifier.wert(this.modifier, cvalue, devaltime);
    }

    /**
     * {@code evaluate_fcurve} fuer ACTION-Kurven: Zeit rein, Wert raus — ohne den
     * Treiber-Sonderfall "keine Stuetzpunkte = Identitaet" (dort bleibt der Wert 0).
     */
    public float wertZeit(float zeit)
    {
        float devaltime = RigKurveModifier.zeit(this.modifier, 0F, zeit);
        float cvalue = this.punkte.isEmpty() ? 0F : this.stuetzpunkte(devaltime);

        return RigKurveModifier.wert(this.modifier, cvalue, devaltime);
    }

    /* ================== Stuetzpunkte ================== */

    /** {@code fcurve_eval_keyframes}. */
    private float stuetzpunkte(float evaltime)
    {
        Punkt erst = this.punkte.get(0);
        Punkt letzt = this.punkte.get(this.punkte.size() - 1);

        if (evaltime <= erst.x)
        {
            return this.extrapoliere(0, 1, evaltime);
        }

        if (letzt.x <= evaltime)
        {
            return this.extrapoliere(this.punkte.size() - 1, -1, evaltime);
        }

        return RigKurveInterpolation.interpoliere(this.punkte, evaltime);
    }

    /** {@code fcurve_eval_keyframes_extrapolate}. */
    private float extrapoliere(int ende, int richtung, float evaltime)
    {
        Punkt p = this.punkte.get(ende);

        if ("CONSTANT".equals(p.interp) || !this.linearExtrapolation)
        {
            return p.y;
        }

        if ("LINEAR".equals(p.interp))
        {
            if (this.punkte.size() == 1)
            {
                return p.y;
            }

            Punkt nachbar = this.punkte.get(ende + richtung);
            float dx = p.x - evaltime;
            float fac = nachbar.x - p.x;

            if (fac == 0F)
            {
                return p.y;
            }

            fac = (nachbar.y - p.y) / fac;

            return p.y - fac * dx;
        }

        float hx = richtung > 0 ? p.hlx : p.hrx;
        float hy = richtung > 0 ? p.hly : p.hry;
        float dx = p.x - evaltime;
        float fac = p.x - hx;

        if (fac == 0F)
        {
            return p.y;
        }

        fac = (p.y - hy) / fac;

        return p.y - fac * dx;
    }
}
