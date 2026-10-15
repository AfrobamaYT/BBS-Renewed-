package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.bobj.BOBJArmature;
import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Blenders Treiber ({@code fcurve_driver.cc}, v5.2.1): Typen SUM/AVERAGE/MIN/MAX/SCRIPTED,
 * Variablen SINGLE_PROP/TRANSFORMS/ROTATION_DIFF/LOC_DIFF, der einfache Ausdruck
 * Variablen rechnet {@link RigTreiberVariable} (Lagen: {@link RigTreiberLage}), den Ausdruck
 * {@link RigTreiberSkript}, die getriebenen Constraint-Felder {@link RigTreiberFeld}.
 * ({@link RigAusdruck}) und die F-Kurve dahinter ({@link RigKurve}).
 *
 * <p>Was hier fehlt, wird in {@link #unbekannt} GEZAEHLT und liefert den Ersatzwert (den
 * ausgewerteten Stand aus der Exportdatei) — nie still 0: ein unverstandener Schalter
 * friert sonst das Rig ein, ohne dass es jemand sieht.
 */
public final class RigTreiber
{
    /** Die Werte, auf die sich die Treiber beziehen — Blenders Custom Properties. */
    public final RigRegler regler = new RigRegler();
    /** Formen, die dieses Rig braucht und die hier fehlen — mit Anzahl. */
    public final Map<String, Integer> unbekannt = new HashMap<>();
    /** Die Armature, aus der Knochen-Variablen ihre Lagen lesen. */
    public BOBJArmature knochen;
    /** Blenders Rahmen (G, W): Weltraum-Variablen brauchen {@code object_to_world}. */
    public RigRahmen rahmen;
    /** Blenders {@code frame} im Ausdruck — die Szenenzeit. */
    public float bild;

    final Map<String, RigAusdruck> ausdruecke = new HashMap<>();
    private final Map<MapType, RigKurve> kurven = new IdentityHashMap<>();
    /** rig.json ist bis zum Models-Reload unveraenderlich; Laufzeitwerte bleiben im Regler/Bone. */
    private Map<MapType, RigTreiberVariable.Beschreibung> variablen;

    RigTreiberVariable.Beschreibung variable(MapType daten)
    {
        if (this.variablen == null)
        {
            this.variablen = new IdentityHashMap<>();
        }

        return this.variablen.computeIfAbsent(daten, RigTreiberVariable.Beschreibung::new);
    }

    public void lade(MapType regler)
    {
        this.regler.lade(regler);
    }

    /**
     * Der Wert, den dieser Treiber gerade liefert.
     *
     * @param treiber Beschreibung aus rig.json ({@code knapp}), darf null sein
     * @param ersatz  was gilt, wenn kein Treiber dranhaengt oder er hier nicht auswertbar ist
     */
    public float wert(MapType treiber, float ersatz)
    {
        /* ⛔ `getMap` liefert fuer einen FEHLENDEN Schluessel ein LEERES MapType, nicht null:
         * ein reiner Null-Check laesst das durch und jeder Constraint OHNE Treiber bekaeme
         * Einfluss 0 (gemessen 2026-08-14). */
        if (treiber == null || !treiber.has("typ"))
        {
            return ersatz;
        }

        float x = this.treiberwert(treiber);

        if (Float.isNaN(x))
        {
            return ersatz;
        }

        if (treiber.has("kurve"))
        {
            return this.kurve(treiber).wert(x);
        }

        return polynom(treiber, x);
    }

    /** Aeltere Exportdateien ohne {@code kurve}: nur das GENERATOR-Polynom, sonst Identitaet. */
    private static float polynom(MapType treiber, float x)
    {
        ListType poly = treiber.has("poly") ? treiber.getList("poly") : null;

        if (poly == null || poly.size() == 0)
        {
            return x;
        }

        float y = 0F;
        float p = 1F;

        for (int i = 0; i < poly.size(); i++)
        {
            y += poly.getFloat(i) * p;
            p *= x;
        }

        return y;
    }

    private RigKurve kurve(MapType treiber)
    {
        RigKurve k = this.kurven.get(treiber);

        if (k == null)
        {
            MapType kurve = treiber.getMap("kurve");

            /* F-Modifier, die RigKurveModifier nicht rechnet (NOISE, CYCLES, ENVELOPE, PYTHON),
             * liessen die Kurve sonst STILL unveraendert — Stufe 1 exportiert alle Typen. */
            for (String typ : RigKurve.modifierTypen(kurve))
            {
                if (!RigKurveModifier.KANN.contains(typ))
                {
                    this.zaehle("fmodifier:" + typ);
                }
            }

            k = RigKurve.lesen(kurve);
            this.kurven.put(treiber, k);
        }

        return k;
    }

    /** {@code evaluate_driver}: NaN heisst "hier nicht auswertbar" (Aufrufer nimmt den Ersatz). */
    private float treiberwert(MapType t)
    {
        ListType vs = t.getList("vars");
        int n = vs == null ? 0 : vs.size();
        float[] werte = new float[n];

        for (int i = 0; i < n; i++)
        {
            werte[i] = RigTreiberVariable.wert(this, vs.getMap(i));
        }

        String typ = t.getString("typ", "SUM");

        switch (typ == null ? "SUM" : typ)
        {
            case "SCRIPTED":
                return RigTreiberSkript.wert(this, t, vs, werte);
            case "MIN":
            case "MAX":
            {
                float v = 0F;

                for (int i = 0; i < n; i++)
                {
                    v = i == 0 ? werte[i] : "MAX".equals(typ) ? Math.max(werte[i], v) : Math.min(werte[i], v);
                }

                return v;
            }
            case "AVERAGE":
            case "SUM":
            {
                float s = 0F;

                for (float v : werte)
                {
                    s += v;
                }

                return "AVERAGE".equals(typ) && n > 1 ? s / n : s;
            }
            default:
                this.zaehle("typ:" + typ);

                return Float.NaN;
        }
    }

    BOBJBone bone(String name)
    {
        return this.knochen == null || name == null || name.isEmpty() ? null : this.knochen.bones.get(name);
    }

    RigRahmen rahmen()
    {
        if (this.rahmen == null)
        {
            this.rahmen = RigRahmen.einheit();
        }

        return this.rahmen;
    }

    /** Blenders Euler-Ordnung des Knochens; ein Quaternion-Knochen faellt auf XYZ. */
    static String ordnung(BOBJBone b)
    {
        return b.eulerOrdnung == null || b.eulerOrdnung.isEmpty() ? "XYZ" : b.eulerOrdnung;
    }

    /** Blenders {@code ANIMSYS_FLOAT_AS_BOOL}: ab hier ist ein getriebener Schalter "an". */
    public static final float BOOL_SCHWELLE = (1F - 1.1920929E-7F) * 0.5F;

    /** Getriebenes An/Aus eines Constraints — {@link RigTreiberFeld#abgeschaltet}. */
    public boolean abgeschaltet(MapType c)
    {
        return RigTreiberFeld.abgeschaltet(this, c);
    }

    /** Schreibt alle getriebenen Constraint-Felder in die {@code rna}-Werte — {@link RigTreiberFeld#felderSetzen}. */
    public void felderSetzen(MapType c)
    {
        RigTreiberFeld.felderSetzen(this, c);
    }

    /** Blenders harte RNA-Bereiche getriebener Felder — {@link RigTreiberFeld#klemme}. */
    public static float klemme(String feld, float v)
    {
        return RigTreiberFeld.klemme(feld, v);
    }

    /** Der Einfluss eines Constraints in DIESEM Bild — getrieben und auf 0..1 geklemmt. */
    public float einfluss(MapType c)
    {
        return RigTreiberFeld.einfluss(this, c);
    }

    /** Ein Constraint-Feld aus {@code rna}, wahlweise getrieben ({@code treiber_felder}). */
    public float feld(MapType c, String rnaName, float vorgabe)
    {
        return RigTreiberFeld.feld(this, c, rnaName, vorgabe);
    }

    void zaehle(String was)
    {
        this.unbekannt.merge(was, 1, Integer::sum);
    }
}
