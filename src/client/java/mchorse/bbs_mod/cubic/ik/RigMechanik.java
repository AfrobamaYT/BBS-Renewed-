package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.bobj.BOBJArmature;
import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.cubic.model.bobj.BOBJModel;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.resources.Link;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fuehrt den importierten Constraint-Graphen des Blender-Rigs aus — BBS erzeugt keine
 * Rig-Mechanik mehr, es vollzieht Blenders nach. Uebernimmt den ganzen Matrix-Durchlauf,
 * weil die Reihenfolge eine andere ist als BBS' Eltern-vor-Kind: das Werkzeug hat sie
 * topologisch vorberechnet (Ziel vor Besitzer), sonst laesen 94 von 116 Constraints
 * veraltete Matrizen — im BOBJ stehen die Deform-Knochen VOR ihren Controls.
 *
 * <p>Stufe 1 wertet die vier Kopier-Typen im WELT-Raum aus (80 von 116 im Mixamo-Rig,
 * darunter alle, die das Mesh an die Controls haengen). IK bleibt beim vorhandenen
 * Solver (arm_ik). Alles andere wird GEZAEHLT, nie stillschweigend uebergangen:
 * {@link #unbekannt} sagt, was noch fehlt.
 */
public final class RigMechanik implements BOBJArmature.Zwang
{
    private record Zwang(String typ, String ziel, float einfluss, boolean welt,
        boolean nutzX, boolean nutzY, boolean nutzZ, String spur, String sperre,
        MapType daten) {}

    /** modellId -> Mechanik; leerer Eintrag = Modell hat keine. Lazy (hotswap-sicher). */
    private static Map<String, RigMechanik> proModell;

    private final List<String> reihenfolge;
    private final Map<String, List<Zwang>> proBone;
    /** Typen, die dieses Rig braucht und Stufe 1 noch nicht kann — mit Anzahl. */
    public final Map<String, Integer> unbekannt = new HashMap<>();

    /** Blenders eigener Solver, portiert — ersetzt BBS' IKTreeSolver. */
    private RigIKKetten ketten;

    private RigMechanik(List<String> reihenfolge, Map<String, List<Zwang>> proBone)
    {
        this.reihenfolge = reihenfolge;
        this.proBone = proBone;
    }

    /** Beim Models-Reload rufen. */
    public static void leeren()
    {
        proModell = null;
    }

    /** Haengt die Mechanik an die Armature, falls das Modell eine mitbringt. Je Frame billig. */
    public static void installieren(BOBJModel model, String modellId)
    {
        BOBJArmature armature = model == null ? null : model.getArmature();

        if (armature == null || armature.zwang != null || modellId == null || modellId.isEmpty())
        {
            return;
        }

        RigMechanik mechanik = laden(modellId);

        if (mechanik != null)
        {
            armature.zwang = mechanik;
        }
    }

    private static RigMechanik laden(String modellId)
    {
        if (proModell == null)
        {
            proModell = new HashMap<>();
        }

        if (proModell.containsKey(modellId))
        {
            return proModell.get(modellId);
        }

        RigMechanik mechanik = null;

        try (InputStream rein = BBSMod.getProvider()
            .getAsset(Link.assets("models/" + modellId + "/rig.json")))
        {
            mechanik = parse((MapType) DataToString.fromString(
                new String(rein.readAllBytes(), StandardCharsets.UTF_8)));
        }
        catch (Exception e)
        {
            /* Kein rig.json = kein Blender-Rig; das ist der Normalfall fuer alte Modelle. */
        }

        proModell.put(modellId, mechanik);

        return mechanik;
    }

    private static RigMechanik parse(MapType wurzel)
    {
        ListType ordnung = wurzel.getList("auswertung");
        ListType mechanik = wurzel.getList("mechanik");

        if (ordnung == null || ordnung.size() == 0 || mechanik == null)
        {
            return null;
        }

        List<String> reihenfolge = new ArrayList<>(ordnung.size());

        for (int i = 0; i < ordnung.size(); i++)
        {
            reihenfolge.add(ordnung.getString(i));
        }

        Map<String, List<Zwang>> proBone = new HashMap<>();
        RigMechanik gebaut = new RigMechanik(reihenfolge, proBone);

        for (int i = 0; i < mechanik.size(); i++)
        {
            MapType c = (MapType) mechanik.get(i);
            String typ = c.getString("typ");
            String ziel = c.getString("ziel");
            float einfluss = c.getFloat("einfluss", 1F);

            /* IK loest der portierte Blender-Solver, unten als eigene Kette. */
            if ("IK".equals(typ))
            {
                continue;
            }

            boolean welt = armaturRaum(c.getString("raum_o", "WORLD"))
                && armaturRaum(c.getString("raum_z", "WORLD"));

            /* Die COPY_-Typen kann BBS laengst — bisher scheiterten sie nur am RAUM.
             * Blenders LOCAL heisst: die Abweichung von der Ruhelage, im Elternraum. */
            boolean kopie = typ != null && typ.startsWith("COPY_");
            boolean lokal = "TRANSFORM".equals(typ) || "LIMIT_ROTATION".equals(typ) || kopie;
            /* Limit Rotation ist der einzige Typ hier OHNE Ziel — es klemmt nur sich selbst. */
            boolean brauchtZiel = !"LIMIT_ROTATION".equals(typ);

            if ((brauchtZiel && (ziel == null || ziel.isEmpty())) || (!welt && !lokal) || !kann(typ))
            {
                gebaut.unbekannt.merge(typ + (welt ? "" : "/raum"), 1, Integer::sum);

                continue;
            }

            proBone.computeIfAbsent(c.getString("bone"), k -> new ArrayList<>())
                .add(new Zwang(typ, ziel, einfluss, welt,
                    c.getBool("nutz_x", true), c.getBool("nutz_y", true), c.getBool("nutz_z", true),
                    c.getString("spur_achse", "TRACK_Y"), c.getString("sperr_achse", "LOCK_Y"), c));
        }

        gebaut.ketten = RigIKKetten.lesen(mechanik, wurzel.getMap("ik_gelenke"));

        return gebaut;
    }

    /** BBS' bone.mat ist Armature-Raum — Blenders POSE. WORLD faellt hier zusammen,
     *  weil der Exporter Objekt- und Achsenmatrix in die Ruhelagen gebacken hat. */
    private static boolean armaturRaum(String raum)
    {
        return "WORLD".equals(raum) || "POSE".equals(raum);
    }

    private static boolean kann(String typ)
    {
        return "COPY_TRANSFORMS".equals(typ) || "COPY_LOCATION".equals(typ)
            || "COPY_ROTATION".equals(typ) || "COPY_SCALE".equals(typ)
            || "CHILD_OF".equals(typ) || "TRACK_TO".equals(typ) || "LOCKED_TRACK".equals(typ)
            || "TRANSFORM".equals(typ) || "LIMIT_ROTATION".equals(typ)
            || "DAMPED_TRACK".equals(typ) || "STRETCH_TO".equals(typ);
    }

    @Override
    public boolean auswerten(BOBJArmature armature)
    {
        for (String name : this.reihenfolge)
        {
            BOBJBone bone = armature.bones.get(name);

            if (bone == null)
            {
                continue;
            }

            armature.matrices[bone.index] = bone.compute();

            List<Zwang> liste = this.proBone.get(name);

            /* ⛔ KEIN vorzeitiges continue mehr, wenn die Liste leer ist. Genau daran
             * lief die IK ins Leere: die vier Knochen, die eine IK tragen, haben
             * SONST keinen Constraint — ihre Liste ist also null, und der
             * IK-Block darunter wurde nie erreicht (gemessen 2026-08-14:
             * Ziel wanderte, Kette stand still). */
            if (liste != null)
            {
                Matrix4f vorher = new Matrix4f(bone.mat);

                for (Zwang z : liste)
                {
                    BOBJBone ziel = z.ziel() == null ? bone : armature.bones.getOrDefault(z.ziel(), bone);

                    if (z.einfluss() > 0F)
                    {
                        anwenden(bone, ziel, z);
                    }
                }

                /* ⛔ originMat MUSS mitwandern. Das Mesh liest mat, die CONTROL-WIDGETS
                 * lesen originMat (ModelInstance.captureMatrices -> MatrixCache.origin);
                 * ohne das blieben genau die Child-Of-Controls stehen, waehrend die Figur
                 * mit dem Master wegging (gemessen 2026-08-14). Derselbe Welt-Effekt auf
                 * beide: das Delta, das die Constraints erzeugt haben. */
                bone.originMat.set(new Matrix4f(bone.mat)
                    .mul(vorher.invert())
                    .mul(bone.originMat));

                armature.matrices[bone.index] = bone.hautMatrix();
            }

            /* IK schreibt die GANZE Kette neu, also auch Knochen, die in dieser
             * Reihenfolge schon dran waren — genau wie Blenders IK-Durchgang. */
            if (this.ketten != null && this.ketten.hat(name))
            {
                for (BOBJBone kettenBone : this.ketten.loese(armature, name))
                {
                    armature.matrices[kettenBone.index] = kettenBone.hautMatrix();
                }
            }
        }

        return true;
    }

    /** Blenders Constraints im Armature-Raum, auf die Matrix des Besitzers. */
    private static void anwenden(BOBJBone eigenBone, BOBJBone zielBone, Zwang z)
    {
        Matrix4f eigen = eigenBone.mat;
        Matrix4f ziel = zielBone.mat;

        /* ⛔ LOKALER RAUM: Blender kopiert dann nicht die Weltlage, sondern die
         * ABWEICHUNG VON DER RUHELAGE. Wer das im Weltraum rechnet, zieht den
         * Knochen an die Stelle des Ziels statt ihn gleich weit auszulenken —
         * sichtbar erst an Fingern und Zehen, die ineinanderfallen. */
        if (!z.welt() && z.typ() != null && z.typ().startsWith("COPY_"))
        {
            Matrix4f eigenBasis = RigZwaenge.ruheBasis(eigenBone);
            Matrix4f lokalEigen = new Matrix4f(eigenBasis).invert().mul(eigen);
            Matrix4f lokalZiel = new Matrix4f(RigZwaenge.ruheBasis(zielBone))
                .invert().mul(ziel);

            kopieren(lokalEigen, lokalZiel, z);
            eigen.set(eigenBasis.mul(lokalEigen));

            return;
        }

        /* Die Fuss-Roll-Mechanik rechnet LOKAL (relativ zur Ruhelage): der Ort des
         * Roll-Cursors wird auf die Drehung der Fersen-/Zehenknochen abgebildet und
         * anschliessend je Achse geklemmt. */
        if ("TRANSFORM".equals(z.typ()) || "LIMIT_ROTATION".equals(z.typ()))
        {
            lokalerZwang(eigenBone, zielBone, z);

            return;
        }

        /* Child Of: der Besitzer erbt die ABWEICHUNG des Ziels von dessen Ruhelage —
         * Blenders inverse_matrix wird bei generierten Rigs in der Ruhepose gesetzt,
         * und genau das ist ziel.mat x inv(ruhe). So haengen die IK-Griffe am Master. */
        if ("CHILD_OF".equals(z.typ()))
        {
            eigen.mulLocal(new Matrix4f(ziel).mul(zielBone.invBoneMat));

            return;
        }

        if ("TRACK_TO".equals(z.typ()) || "LOCKED_TRACK".equals(z.typ()))
        {
            zielen(eigen, ziel, z);

            return;
        }

        if ("DAMPED_TRACK".equals(z.typ()))
        {
            RigZwaenge.dampedTrack(eigen, ziel, z.spur(), Math.min(1F, z.einfluss()));

            return;
        }

        if ("STRETCH_TO".equals(z.typ()))
        {
            /* Volumen 1.0 = Blenders Vorgabe VOLUME_XZX; die Ruhelaenge kommt
             * aus dem BOBJ-Tail, ohne sie gaebe es kein Dehnverhaeltnis. */
            RigZwaenge.stretchTo(eigen, ziel, z.spur(), eigenBone.restLength,
                z.daten() == null ? 1F : z.daten().getFloat("volumen", 1F),
                Math.min(1F, z.einfluss()));

            return;
        }

        kopieren(eigen, ziel, z);
    }

    /** Der Kopier-Kern — im Welt- wie im Lokalraum derselbe, nur mit anderen Matrizen. */
    private static void kopieren(Matrix4f eigen, Matrix4f ziel, Zwang z)
    {
        Vector3f t = eigen.getTranslation(new Vector3f());
        Quaternionf q = eigen.getUnnormalizedRotation(new Quaternionf());
        Vector3f s = eigen.getScale(new Vector3f());
        Vector3f zt = zielPunkt(ziel, z);
        Quaternionf zq = ziel.getUnnormalizedRotation(new Quaternionf());
        Vector3f zs = ziel.getScale(new Vector3f());
        float w = Math.min(1F, z.einfluss());

        switch (z.typ())
        {
            case "COPY_TRANSFORMS" ->
            {
                t.lerp(zt, w);
                q.slerp(zq, w);
                s.lerp(zs, w);
            }
            case "COPY_LOCATION" -> achsen(t, zt, w, z);
            case "COPY_ROTATION" -> q.slerp(zq, w);
            case "COPY_SCALE" -> achsen(s, zs, w, z);
            default ->
            {
                return;
            }
        }

        eigen.translationRotateScale(t, q, s);
    }

    /**
     * Track To / Locked Track: die Spur-Achse des Besitzers zeigt aufs Ziel. Bei
     * LOCKED_TRACK bleibt die Sperr-Achse stehen, es wird nur um sie gedreht.
     * Achsennamen sind BONE-LOKAL; nach der Achsenwandlung ist unser Oben +Y.
     */
    private static void zielen(Matrix4f eigen, Matrix4f ziel, Zwang z)
    {
        Vector3f pos = eigen.getTranslation(new Vector3f());
        Vector3f skala = eigen.getScale(new Vector3f());
        Vector3f richtung = zielPunkt(ziel, z).sub(pos);

        if (richtung.lengthSquared() < 1.0E-10F)
        {
            return;
        }

        richtung.normalize();

        Quaternionf q = eigen.getUnnormalizedRotation(new Quaternionf());
        Vector3f spur = achse(q, z.spur());

        if ("LOCKED_TRACK".equals(z.typ()))
        {
            /* Nur um die Sperr-Achse: beide Richtungen in deren Ebene projizieren. */
            Vector3f sperre = achse(q, z.sperre().replace("LOCK_", "TRACK_"));

            richtung.sub(new Vector3f(sperre).mul(richtung.dot(sperre)));
            spur.sub(new Vector3f(sperre).mul(spur.dot(sperre)));

            if (richtung.lengthSquared() < 1.0E-10F || spur.lengthSquared() < 1.0E-10F)
            {
                return;
            }

            richtung.normalize();
            spur.normalize();
        }

        q.premul(new Quaternionf().rotationTo(spur, richtung));
        eigen.translationRotateScale(pos, q, skala);
    }

    /**
     * Der Punkt, auf den ein Constraint zielt: Blenders head_tail waehlt zwischen
     * Kopf und ENDE des Zielknochens. Ohne ihn zog Copy Location den Fuss ans Knie
     * statt an den Knoechel — die Unterschenkel fielen zusammen (2026-08-14).
     * Das Werkzeug liefert den Offset bereits BONE-LOKAL.
     */
    private static Vector3f zielPunkt(Matrix4f ziel, Zwang z)
    {
        ListType o = z.daten() == null ? null : z.daten().getList("ziel_offset");

        if (o == null || o.size() < 3)
        {
            return ziel.getTranslation(new Vector3f());
        }

        return ziel.transformPosition(new Vector3f(o.getFloat(0), o.getFloat(1), o.getFloat(2)));
    }

    /** Ruhe-Basis des Knochens im Armature-Raum: Eltern-Weltmatrix x Ruhelage-relativ. */
    private static Matrix4f basis(BOBJBone bone)
    {
        return bone.parentBone != null
            ? new Matrix4f(bone.parentBone.mat).mul(bone.relBoneMat)
            : new Matrix4f(bone.relBoneMat);
    }

    private static void lokalerZwang(BOBJBone eigenBone, BOBJBone zielBone, Zwang z)
    {
        Matrix4f basis = basis(eigenBone);
        Matrix4f lokal = new Matrix4f(basis).invert().mul(eigenBone.mat);

        if ("TRANSFORM".equals(z.typ()))
        {
            /* Quelle ist die LOKALE Verschiebung des Ziels — beim Roll-Cursor genau das,
             * was der Nutzer zieht. Je Zielachse speist eine benannte Quellachse einen
             * Bereich in einen anderen (Blender klemmt ohne Extrapolation). */
            Vector3f q = zielBone.transform.translate;
            float[] quelle = {q.x, q.y, q.z};
            float[] winkel = new float[3];

            for (int a = 0; a < 3; a++)
            {
                String achse = "xyz".substring(a, a + 1);
                int s = index(z.daten().getString("quelle_" + achse, achse.toUpperCase()));
                float vonMin = z.daten().getFloat("from_min_" + "xyz".charAt(s), 0F);
                float vonMax = z.daten().getFloat("from_max_" + "xyz".charAt(s), 0F);
                float nachMin = z.daten().getFloat("to_min_" + achse, 0F);
                float nachMax = z.daten().getFloat("to_max_" + achse, 0F);
                float spanne = vonMax - vonMin;
                float anteil = spanne == 0F ? 0F
                    : Math.max(0F, Math.min(1F, (quelle[s] - vonMin) / spanne));

                winkel[a] = nachMin + anteil * (nachMax - nachMin);
            }

            lokal.rotateXYZ(winkel[0], winkel[1], winkel[2]);
        }
        else
        {
            Vector3f pos = lokal.getTranslation(new Vector3f());
            Vector3f skala = lokal.getScale(new Vector3f());
            Vector3f e = lokal.getEulerAnglesXYZ(new Vector3f());

            e.x = klemme(e.x, z.daten(), "x");
            e.y = klemme(e.y, z.daten(), "y");
            e.z = klemme(e.z, z.daten(), "z");
            lokal.translationRotateScale(pos, new Quaternionf().rotationXYZ(e.x, e.y, e.z), skala);
        }

        eigenBone.mat.set(basis).mul(lokal);
    }

    private static float klemme(float wert, MapType d, String achse)
    {
        return d.getBool("grenz_" + achse, false)
            ? Math.max(d.getFloat("min_" + achse, 0F), Math.min(d.getFloat("max_" + achse, 0F), wert))
            : wert;
    }

    private static int index(String achse)
    {
        return "Y".equals(achse) ? 1 : "Z".equals(achse) ? 2 : 0;
    }

    /** Die genannte BONE-LOKALE Achse als Richtung im Armature-Raum. */
    private static Vector3f achse(Quaternionf rot, String name)
    {
        Vector3f a = switch (name == null ? "TRACK_Y" : name)
        {
            case "TRACK_X" -> new Vector3f(1F, 0F, 0F);
            case "TRACK_Z" -> new Vector3f(0F, 0F, 1F);
            case "TRACK_NEGATIVE_X" -> new Vector3f(-1F, 0F, 0F);
            case "TRACK_NEGATIVE_Y" -> new Vector3f(0F, -1F, 0F);
            case "TRACK_NEGATIVE_Z" -> new Vector3f(0F, 0F, -1F);
            default -> new Vector3f(0F, 1F, 0F);
        };

        return rot.transform(a).normalize();
    }

    private static void achsen(Vector3f eigen, Vector3f ziel, float w, Zwang z)
    {
        if (z.nutzX()) eigen.x += (ziel.x - eigen.x) * w;
        if (z.nutzY()) eigen.y += (ziel.y - eigen.y) * w;
        if (z.nutzZ()) eigen.z += (ziel.z - eigen.z) * w;
    }
}
