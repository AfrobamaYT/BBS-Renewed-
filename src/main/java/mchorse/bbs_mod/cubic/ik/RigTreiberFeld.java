package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ByteType;
import mchorse.bbs_mod.data.types.IntType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.data.types.NumericType;

/**
 * Getriebene Constraint-Felder: An/Aus ({@code mute}/{@code enabled}), {@code treiber_felder}
 * typgerecht in die {@code rna}-Werte geschrieben und mit Blenders harten RNA-Bereichen geklemmt —
 * so, wie Blenders Treiber vor der Auswertung in die DNA-Felder schreiben.
 */
final class RigTreiberFeld
{
    private RigTreiberFeld()
    {}

    /**
     * Getriebenes An/Aus eines Constraints. ⛔ {@code mute} und {@code enabled} sind
     * gegenlaeufig — ohne das Feld schaltet Rigifys {@code pole_vector} genau falsch herum.
     * Fehlt der Treiber, gilt der feste Zustand aus der Datei.
     */
    static boolean abgeschaltet(RigTreiber treiber, MapType c)
    {
        MapType t = c.has("treiber_aus") ? c.getMap("treiber_aus") : null;

        if (t == null || !t.has("typ"))
        {
            return c.getBool("aus", false);
        }

        boolean an = treiber.wert(t, 0F) > RigTreiber.BOOL_SCHWELLE;

        return "enabled".equals(t.getString("feld")) ? !an : an;
    }

    /**
     * Schreibt alle getriebenen Constraint-Felder ({@code treiber_felder}) typgerecht in die
     * {@code rna}-Werte des Constraints — so, wie Blenders Treiber vor der Auswertung in die
     * DNA-Felder schreiben. Danach liest jeder Rechner den getriebenen Wert ueber
     * {@link RigZwangBasis#zahl}/{@link RigZwangBasis#flag}, ohne die Treiber zu kennen.
     */
    static void felderSetzen(RigTreiber treiber, MapType c)
    {
        MapType tf = c.has("treiber_felder") ? c.getMap("treiber_felder") : null;

        if (tf == null)
        {
            return;
        }

        MapType rna = RigZwangBasis.rna(c);

        for (String feld : tf.keys())
        {
            BaseType alt = rna.get(feld);
            float fest = alt instanceof NumericType n ? n.floatValue() : 0F;
            float v = klemme(feld, treiber.wert(tf.getMap(feld), fest));

            if (alt instanceof ByteType)
            {
                rna.putBool(feld, v > RigTreiber.BOOL_SCHWELLE);
            }
            else if (alt instanceof IntType)
            {
                rna.putInt(feld, (int) v);
            }
            else
            {
                rna.putFloat(feld, v);
            }
        }
    }

    /**
     * Blenders harte RNA-Bereiche der ueblichen getriebenen Felder: ein Treiber schreibt ueber
     * {@code RNA_property_float_set}, und das klemmt. ⛔ Gemessen am Testrig 2026-09-13: ein
     * AVERAGE-Treiber lieferte 1,25 auf {@code influence}; Blender rechnete mit 1,0, BBS mit
     * 1,25 — das IK-Ziel wurde ueber sich hinaus extrapoliert.
     */
    static float klemme(String feld, float v)
    {
        float min;
        float max;

        switch (feld == null ? "" : feld)
        {
            case "influence", "head_tail", "weight", "orient_weight", "eval_time", "bulge_min", "bulge_smooth",
                "ik_stretch" ->
            {
                min = 0F;
                max = 1F;
            }
            case "iterations" ->
            {
                min = 1F;
                max = 10000F;
            }
            case "chain_count" ->
            {
                min = 0F;
                max = 255F;
            }
            case "pole_angle", "ik_min_x", "ik_min_y", "ik_min_z", "ik_max_x", "ik_max_y", "ik_max_z" ->
            {
                min = -(float) Math.PI;
                max = (float) Math.PI;
            }
            case "bulge", "bulge_max" ->
            {
                min = 1F;
                max = 100F;
            }
            case "rest_length", "distance" ->
            {
                min = 0F;
                max = Float.MAX_VALUE;
            }
            case "ik_stiffness_x", "ik_stiffness_y", "ik_stiffness_z" ->
            {
                min = 0F;
                max = 0.99F;
            }
            default ->
            {
                return v;
            }
        }

        return Math.max(min, Math.min(max, v));
    }

    /** Der Einfluss eines Constraints in DIESEM Bild — getrieben und auf 0..1 geklemmt. */
    static float einfluss(RigTreiber treiber, MapType c)
    {
        return klemme("influence", treiber.wert(c.getMap("treiber"), c.getFloat("einfluss", 1F)));
    }

    /** Ein Constraint-Feld aus {@code rna}, wahlweise getrieben ({@code treiber_felder}). */
    static float feld(RigTreiber treiber, MapType c, String rnaName, float vorgabe)
    {
        float fest = RigZwangBasis.zahl(c, rnaName, null, vorgabe);
        MapType tf = c.has("treiber_felder") ? c.getMap("treiber_felder") : null;

        if (tf != null && tf.has(rnaName))
        {
            return klemme(rnaName, treiber.wert(tf.getMap(rnaName), fest));
        }

        return fest;
    }
}
