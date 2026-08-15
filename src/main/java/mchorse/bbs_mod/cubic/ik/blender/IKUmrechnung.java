/* Bruecke zwischen BBS' JOML-Matrizen und dem Port von Blenders intern/iksolver.
 * SPDX-License-Identifier: GPL-2.0-or-later */
package mchorse.bbs_mod.cubic.ik.blender;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * ⛔ Die einzige Stelle, an der zwei Matrix-Konventionen aufeinandertreffen.
 * JOML adressiert {@code get(spalte, zeile)}, dieser Port {@code get(zeile, spalte)}.
 * Deshalb steht die Vertauschung hier EINMAL, sichtbar, statt verstreut in der
 * Bruecke — dort waere sie ein stiller Transpositionsfehler, der ein Bein
 * plausibel in die falsche Ebene knickt.
 */
public final class IKUmrechnung
{
    /** IK_STRETCH_STIFF_EPS/_MIN/_MAX aus Blenders IK_Solver.h. */
    /* ⛔ Blenders IK_STRETCH_STIFF_EPS ist 0.001, nicht 0.01 (iksolver_plugin.cc).
     * Mit 0.01 war das Dehngewicht nach unten auf 0.01 gedeckelt: ein Knochen mit
     * ik_stretch 0.05 bekam 0.01 statt 0.0025 und dehnte sich VIERMAL so leicht.
     * Gemessen an der Umrechnung selbst: 0.05 -> 0.01000 statt 0.00250. */
    private static final double DEHN_EPS = 0.001D;
    public static final double DEHN_MIN = 0.001D;
    public static final double DEHN_MAX = 1e10D;

    private IKUmrechnung()
    {}

    /** Drehteil einer JOML-Matrix als zeilenmajore 3x3. */
    public static IKMat3 zuMat3(Matrix4f m)
    {
        IKMat3 r = new IKMat3();

        for (int zeile = 0; zeile < 3; zeile++)
        {
            for (int spalte = 0; spalte < 3; spalte++)
            {
                r.set(zeile, spalte, m.get(spalte, zeile));
            }
        }

        return r;
    }

    public static IKVec3 zuVec3(Vector3f v)
    {
        return new IKVec3(v.x, v.y, v.z);
    }

    public static Vector3f zuJoml(IKVec3 v)
    {
        return new Vector3f((float) v.x, (float) v.y, (float) v.z);
    }

    /** 3x3 als JOML-Matrix4f (Drehteil gesetzt, Verschiebung 0). */
    public static Matrix4f zuJoml(IKMat3 m)
    {
        Matrix4f r = new Matrix4f();

        for (int zeile = 0; zeile < 3; zeile++)
        {
            for (int spalte = 0; spalte < 3; spalte++)
            {
                r.set(spalte, zeile, (float) m.get(zeile, spalte));
            }
        }

        return r;
    }

    /** Spalten auf Laenge 1 bringen — Blenders {@code normalize_m3}. */
    public static IKMat3 normalisiert(IKMat3 m)
    {
        IKMat3 r = new IKMat3(m);

        for (int spalte = 0; spalte < 3; spalte++)
        {
            double laenge = Math.sqrt(
                r.get(0, spalte) * r.get(0, spalte)
                + r.get(1, spalte) * r.get(1, spalte)
                + r.get(2, spalte) * r.get(2, spalte));

            if (laenge == 0D)
            {
                continue;
            }

            for (int zeile = 0; zeile < 3; zeile++)
            {
                r.set(zeile, spalte, r.get(zeile, spalte) / laenge);
            }
        }

        return r;
    }

    /** Laenge der Y-Spalte — Blenders {@code len_v3(R_bonemat[1])}, der Skalenanteil. */
    public static double ySpaltenLaenge(IKMat3 m)
    {
        return Math.sqrt(m.get(0, 1) * m.get(0, 1)
            + m.get(1, 1) * m.get(1, 1)
            + m.get(2, 1) * m.get(2, 1));
    }

    /**
     * Segmenttyp aus den gesperrten Achsen, wie {@code IK_CreateSegment}:
     * 1 freier Grad = Scharnier, 3 freie = Kugel. Zwei freie Grade (Swing/Elbow)
     * sind noch nicht portiert und scheitern LAUT statt still als Kugel
     * durchzurutschen — eine falsch geratene Biegeebene sieht man erst am Bild.
     */
    public static IKQSegment segment(boolean sperrX, boolean sperrY, boolean sperrZ)
    {
        int frei = (sperrX ? 0 : 1) + (sperrY ? 0 : 1) + (sperrZ ? 0 : 1);

        if (frei == 3)
        {
            return new IKQSphericalSegment();
        }

        if (frei == 1)
        {
            return new IKQRevoluteSegment(!sperrX ? 0 : !sperrY ? 1 : 2);
        }

        throw new UnsupportedOperationException(
            "IK-Gelenk mit " + frei + " freien Achsen (sperr=" + sperrX + "," + sperrY + ","
            + sperrZ + "): Swing-, Elbow- und Null-Segment sind noch nicht "
            + "aus Blender portiert.");
    }

    /**
     * Dasselbe mit Dehnung: Blenders {@code IK_CreateSegment} haengt dem
     * Drehsegment ein Verschiebe-Segment als KIND an und meldet beide nach
     * aussen als ein Glied.
     *
     * ⛔ Folge fuer den Kettenbau: der naechste Knochen muss an das COMPOSITE
     * gehaengt werden, nicht ans Drehsegment — sonst erbt er die Dehnung nicht
     * und die Kette reisst genau dort, wo sie laenger werden soll.
     */
    public static IKQSegment segment(boolean sperrX, boolean sperrY, boolean sperrZ,
                                     boolean dehnbar)
    {
        IKQSegment dreh = segment(sperrX, sperrY, sperrZ);

        if (!dehnbar)
        {
            return dreh;
        }

        IKQSegment schub = new IKQTranslateSegment(1);

        dreh.setComposite(schub);
        schub.setzeElter(dreh);

        return dreh;
    }

    /**
     * Blenders {@code IK_SetStiffness}: Steifigkeit wird zum Gewicht, gedeckelt
     * knapp unter 1 — ein Gewicht von exakt 0 nimmt dem Jacobi den Grad ganz.
     */
    public static void steifigkeit(IKQSegment seg, int achse, double steifigkeit)
    {
        if (steifigkeit < 0D)
        {
            return;
        }

        IKQSegment ziel = fuerAchse(seg, achse);

        if (ziel != null)
        {
            ziel.setzeGewicht(achse % 3, 1D - Math.min(steifigkeit, 1D - DEHN_EPS));
        }
    }

    /** Blenders {@code IK_SetLimit}, mit derselben Achsen-Weiche. */
    public static void grenze(IKQSegment seg, int achse, double lmin, double lmax)
    {
        IKQSegment ziel = fuerAchse(seg, achse);

        if (ziel != null)
        {
            ziel.setzeGrenze(achse % 3, lmin, lmax);
        }
    }

    /** Achse 0..2 dreht, 3..5 verschiebt — letztere gehoeren ans Composite. */
    private static IKQSegment fuerAchse(IKQSegment seg, int achse)
    {
        if (achse < 3 || seg.translational())
        {
            return seg;
        }

        return seg.composite() != null && seg.composite().translational()
            ? seg.composite() : null;
    }
}
