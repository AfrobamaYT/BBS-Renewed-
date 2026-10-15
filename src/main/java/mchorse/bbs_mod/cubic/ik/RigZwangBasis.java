package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.bobj.BOBJBasisMathe;
import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.pose.Transform;

import java.util.List;

/**
 * Blenders Constraint-Rechner aus {@code blenkernel/intern/constraint.cc} (v5.2.1), Typ fuer
 * Typ wortgetreu portiert: Copy Location/Rotation/Scale/Transforms, Limit Location/Rotation/
 * Scale/Distance, Track To, Locked Track, Damped Track, Stretch To, Child Of, Transformation,
 * Maintain Volume, Floor, Pivot, Action, Armature. IK loest {@link RigIKKetten}.
 *
 * <p>Alle Matrizen hier sind Blender-Matrizen im jeweiligen Constraint-Raum (Layout
 * {@link RigMathe}); die Raum- und Rahmenwechsel macht {@link RigMechanik} ueber
 * {@link RigRaum}/{@link RigRahmen}. Der Besitzer kommt als {@code cob} herein und geht
 * veraendert zurueck — genau wie {@code cob->matrix}.
 */
public final class RigZwangBasis
{
    /** Was ein Rechner ueber den Besitzer wissen muss — Blenders {@code bConstraintOb}. */
    public record Besitzer(RigRahmen rahmen, BOBJBone bone, String rotOrder)
    {}

    /** Ein aufgeloestes Ziel: Matrix im Zielraum, Knochen (null = Armature-Objekt), Euler-Ordnung. */
    public record Ziel(float[] matrix, BOBJBone bone, String rotOrder)
    {}

    private RigZwangBasis()
    {}

    public static MapType rna(MapType c)
    {
        mchorse.bbs_mod.data.types.BaseType wert = c.get("rna");

        if (wert == null)
        {
            MapType rna = new MapType();
            c.put("rna", rna);
            return rna;
        }

        return wert instanceof MapType rna ? rna : new MapType();
    }

    public static boolean flag(MapType c, String rnaName, String altName, boolean vorgabe)
    {
        MapType r = rna(c);

        if (r.has(rnaName))
        {
            return r.getBool(rnaName, vorgabe);
        }

        return altName != null && c.has(altName) ? c.getBool(altName, vorgabe) : vorgabe;
    }

    public static float zahl(MapType c, String rnaName, String altName, float vorgabe)
    {
        MapType r = rna(c);

        if (r.has(rnaName))
        {
            return r.getFloat(rnaName, vorgabe);
        }

        return altName != null && c.has(altName) ? c.getFloat(altName, vorgabe) : vorgabe;
    }

    public static String text(MapType c, String rnaName, String altName, String vorgabe)
    {
        MapType r = rna(c);

        if (r.has(rnaName) && r.getString(rnaName) != null)
        {
            return r.getString(rnaName);
        }

        return altName != null && c.has(altName) && c.getString(altName) != null ? c.getString(altName) : vorgabe;
    }

    public static float[] vektor(MapType c, String rnaName, float[] vorgabe)
    {
        ListType l = rna(c).getList(rnaName);

        if (l == null || l.size() < vorgabe.length)
        {
            return vorgabe;
        }

        float[] v = new float[vorgabe.length];

        for (int i = 0; i < v.length; i++)
        {
            v[i] = l.getFloat(i);
        }

        return v;
    }

    /** Blenders "beim ersten Lauf gemessen und zurueckgeschrieben" (orglength, dist). */
    public static void merke(MapType c, String rnaName, String altName, float wert)
    {
        rna(c).putFloat(rnaName, wert);

        if (altName != null)
        {
            c.putFloat(altName, wert);
        }
    }

    /** Eine 4x4 aus Blenders Zeilenschreibweise (RNA liefert row-major) ins Spaltenlayout. */
    public static float[] matrix(MapType c, String rnaName)
    {
        ListType l = rna(c).getList(rnaName);

        if (l == null || l.size() != 16)
        {
            l = c.getList("inverse");
        }

        float[] m = RigMathe.einheit();

        if (l == null || l.size() != 16)
        {
            return m;
        }

        for (int r = 0; r < 4; r++)
        {
            for (int col = 0; col < 4; col++)
            {
                m[col * 4 + r] = l.getFloat(r * 4 + col);
            }
        }

        return m;
    }

    /** Euler-Ordnung eines Constraint-Felds: AUTO faellt auf die des Besitzers. */
    public static String ordnung(MapType c, String feld, Besitzer b)
    {
        String o = text(c, feld, "euler_ordnung", "AUTO");

        return o == null || o.isEmpty() || "AUTO".equals(o) ? b.rotOrder() : o;
    }

    /** Spurachsen-Index wie Blenders TRACK_X..TRACK_nZ (0..5). */
    public static int spurIndex(String achse)
    {
        return switch (achse == null ? "" : achse)
        {
            case "TRACK_X", "FLOOR_X", "UP_X", "LOCK_X", "X" -> 0;
            case "TRACK_Z", "FLOOR_Z", "UP_Z", "LOCK_Z", "Z" -> 2;
            case "TRACK_NEGATIVE_X", "FLOOR_NEGATIVE_X" -> 3;
            case "TRACK_NEGATIVE_Y", "FLOOR_NEGATIVE_Y" -> 4;
            case "TRACK_NEGATIVE_Z", "FLOOR_NEGATIVE_Z" -> 5;
            default -> 1;
        };
    }

    static final float[][] SPUR_VEKTOREN = {
        {1F, 0F, 0F}, {0F, 1F, 0F}, {0F, 0F, 1F}, {-1F, 0F, 0F}, {0F, -1F, 0F}, {0F, 0F, -1F}};

}
