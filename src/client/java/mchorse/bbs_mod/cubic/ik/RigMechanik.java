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
        MapType daten, float[] jetzt)
    {
        /** Die Staerke DIESES Bildes — getrieben, wenn ein Treiber dranhaengt.
         * {@link #einfluss} ist nur der feste Wert aus der Datei. */
        float staerke()
        {
            return this.jetzt[0];
        }
    }

    /** modellId -> Mechanik; leerer Eintrag = Modell hat keine. Lazy (hotswap-sicher). */
    private static Map<String, RigMechanik> proModell;

    private final List<String> reihenfolge;
    private final Map<String, List<Zwang>> proBone;
    /** Typen, die dieses Rig braucht und Stufe 1 noch nicht kann — mit Anzahl. */
    public final Map<String, Integer> unbekannt = new HashMap<>();
    /** Blenders Treiber: IK/FK, Space-Switching, Stretch. Der Schaltmechanismus. */
    public final RigTreiber treiber = new RigTreiber();

    /** Blenders eigener Solver, portiert — ersetzt BBS' IKTreeSolver. */
    private RigIKKetten ketten;

    /**
     * Ruhedaten-Flags je Knochen, die von Blenders Vorgabe abweichen — nur die, die
     * abweichen, darum meist eine Handvoll. Sie gehoeren nicht in den Constraint-Stapel,
     * sondern an den Knochen selbst: sie aendern, wie die ELTERNLAGE weitergereicht wird.
     */
    private final Map<String, MapType> knochenFlags = new HashMap<>();

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
            mechanik.flaggen(armature);
        }
    }

    /**
     * Setzt die Ruhedaten-Flags auf die Knochen DIESER Armature. Muss je Armature laufen,
     * nicht je Modell-Id: die Mechanik ist geteilt, die Knochen sind es nicht.
     */
    private void flaggen(BOBJArmature armature)
    {
        for (Map.Entry<String, MapType> e : this.knochenFlags.entrySet())
        {
            BOBJBone bone = armature.bones.get(e.getKey());

            if (bone == null)
            {
                continue;
            }

            bone.elternraumVerschiebung = !e.getValue().getBool("lokal_loc", true);

            String skala = e.getValue().getString("skala");

            bone.skalenErbe = skala == null || skala.isEmpty() || "FULL".equals(skala)
                ? null : skala;
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
        MapType flags = wurzel.getMap("knochen");

        for (String name : flags == null ? List.<String>of() : flags.keys())
        {
            gebaut.knochenFlags.put(name, flags.getMap(name));
        }

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
            /* CUSTOM-Raum: ein benannter Knochen IST der Raum. Rigify rechnet
             * Limit Distance dort, und dort muss es auch gerechnet werden — eine
             * Strecke in einem skalierten Raum ist eine andere Strecke. */
            boolean eigenraum = !c.getString("raum_bone", "").isEmpty();
            /* Limit Rotation klemmt nur sich selbst, Armature traegt seine Ziele
             * in einer Liste — beide haben kein einzelnes ziel-Feld. */
            boolean brauchtZiel = !"LIMIT_ROTATION".equals(typ) && !"ARMATURE".equals(typ);
            /* Blenders Quaternion-/Huellen-Modus des Armature-Constraints ist
             * eine ANDERE Mathematik (Dual-Quaternionen, Huellgewichte) und nicht
             * portiert. Laut zaehlen statt still linear rechnen. */
            boolean andereMathe = "ARMATURE".equals(typ)
                && (c.getBool("quaternion", false) || c.getBool("huellen", false));

            String warum = !kann(typ) ? "" : andereMathe ? "/mathe"
                : !(welt || lokal || eigenraum) ? "/raum" : null;

            if (warum != null || (brauchtZiel && (ziel == null || ziel.isEmpty())))
            {
                gebaut.unbekannt.merge(typ + (warum == null ? "" : warum), 1, Integer::sum);

                continue;
            }

            proBone.computeIfAbsent(c.getString("bone"), k -> new ArrayList<>())
                .add(new Zwang(typ, ziel, einfluss, welt,
                    c.getBool("nutz_x", true), c.getBool("nutz_y", true), c.getBool("nutz_z", true),
                    c.getString("spur_achse", "TRACK_Y"), c.getString("sperr_achse", "LOCK_Y"), c,
                    new float[] {einfluss}));
        }

        gebaut.ketten = RigIKKetten.lesen(mechanik, wurzel.getMap("ik_gelenke"));
        gebaut.treiber.lade(wurzel.getMap("regler"));

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
            || "DAMPED_TRACK".equals(typ) || "STRETCH_TO".equals(typ)
            || "ARMATURE".equals(typ) || "LIMIT_DISTANCE".equals(typ);
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

                    /* ⛔ Treiber ZUERST. Der IK/FK-Wechsel ist kein Constraint, sondern
                     * ein Treiber auf DIESER Influence: die ORG-Kette kopiert von der
                     * FK- UND der IK-Kette, und der zweite laeuft auf 1-IK_FK. Steht er
                     * fest auf 1, gewinnt IK immer und die FK-Controls sind tot. */
                    z.jetzt()[0] = Math.min(1F,
                        this.treiber.wert(z.daten().getMap("treiber"), z.einfluss()));

                    if (z.jetzt()[0] > 0F && !this.abgeschaltet(z))
                    {
                        anwenden(armature, bone, ziel, z);
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
    /**
     * Getriebenes An/Aus. ⛔ {@code mute} und {@code enabled} sind gegenlaeufig —
     * ohne das Feld schaltet Rigifys {@code pole_vector} den Constraint genau
     * falsch herum. Fehlt der Treiber, gilt der feste Zustand aus der Datei.
     */
    private boolean abgeschaltet(Zwang z)
    {
        MapType t = z.daten().getMap("treiber_aus");

        if (t == null)
        {
            return false;
        }

        float v = this.treiber.wert(t, 0F);

        return "enabled".equals(t.getString("feld")) ? v < 0.5F : v >= 0.5F;
    }

    private static void anwenden(BOBJArmature armature, BOBJBone eigenBone,
        BOBJBone zielBone, Zwang z)
    {
        Matrix4f eigen = eigenBone.mat;
        Matrix4f ziel = zielBone.mat;

        /* Armature traegt seine Ziele als gewichtete LISTE — nicht ueber z.ziel()
         * erreichbar, deshalb vor allem anderen. */
        if ("ARMATURE".equals(z.typ()))
        {
            armaturZwang(armature, eigen, z);

            return;
        }

        if ("LIMIT_DISTANCE".equals(z.typ()))
        {
            abstandsZwang(armature, eigen, ziel, z);

            return;
        }

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
            RigZwaenge.dampedTrack(eigen, spurZiel(ziel, z), z.spur(),
                Math.min(1F, z.staerke()));

            return;
        }

        if ("STRETCH_TO".equals(z.typ()))
        {
            MapType d = z.daten();

            /* ⛔ Blender misst gegen {@code data->orglength}, die EIGENE Ruhelaenge
             * des Constraints — nicht gegen die Knochenlaenge. Rigifys VIS_*-Zeiger
             * sind 0,045 lange Knochen, die eine halbe Beinlaenge ueberspannen; mit
             * der Knochenlaenge gerechnet ist der Faktor um ein Vielfaches daneben. */
            float ruhe = d == null ? 0F : d.getFloat("ruhe_laenge", 0F);

            /* ⛔ volumen ist Blenders ENUM-STRING, kein Float. Als Float gelesen kam
             * still 0 heraus — also fuer JEDE der 144 Stretch-To der Querschnitt
             * eines NO_VOLUME, auch fuer die 110 volumenerhaltenden. */
            String volumen = d == null ? "VOLUME_XZX" : d.getString("volumen", "VOLUME_XZX");

            RigZwaenge.stretchTo(eigen, spurZiel(ziel, z), z.spur(),
                ruhe > 0F ? ruhe : eigenBone.restLength,
                "NO_VOLUME".equals(volumen) ? 0F : 1F, Math.min(1F, z.staerke()));

            return;
        }

        kopieren(eigen, ziel, z);
    }

    /**
     * Armature-Constraint: gewichteter Blend der DEFORM-Matrizen der Ziele.
     * {@code hautMatrix()} IST Blenders {@code chan_mat} — dieselbe Matrix, mit
     * der die Haut gerechnet wird. Der Constraint tut nichts anderes als der
     * Armature-MODIFIER, nur an einem Knochen statt an einem Vertex.
     */
    private static void armaturZwang(BOBJArmature armature, Matrix4f eigen, Zwang z)
    {
        ListType ziele = z.daten() == null ? null : z.daten().getList("ziele");

        if (ziele == null)
        {
            return;
        }

        List<Matrix4f> deform = new ArrayList<>(ziele.size());
        float[] gewichte = new float[ziele.size()];
        int n = 0;

        for (int i = 0; i < ziele.size(); i++)
        {
            MapType t = (MapType) ziele.get(i);
            float w = t.getFloat("gewicht", 0F);

            /* Gewicht 0 ueberspringt Blender GANZ — sein Ziel muss nicht einmal
             * existieren. Genau so haengen Rigifys abgewaehlte Eltern da. */
            if (w <= 0F)
            {
                continue;
            }

            BOBJBone b = armature.bones.get(t.getString("bone"));

            /* Fehlt ein GEWICHTETES Ziel, laesst Blender den ganzen Constraint
             * aus — nicht bloss dieses eine Ziel. */
            if (b == null)
            {
                return;
            }

            deform.add(b.hautMatrix());
            gewichte[n++] = w;
        }

        RigZwaenge.armature(eigen, deform, gewichte);
    }

    /** Limit Distance — bei Rigify im CUSTOM-Raum eines Knochens, siehe parse(). */
    private static void abstandsZwang(BOBJArmature armature, Matrix4f eigen,
        Matrix4f ziel, Zwang z)
    {
        String raumName = z.daten().getString("raum_bone", "");
        BOBJBone raumBone = raumName.isEmpty() ? null : armature.bones.get(raumName);

        /* Lieber gar nicht klemmen als im falschen Raum: eine Strecke ohne ihren
         * Raum ist eine andere Strecke. */
        if (!raumName.isEmpty() && raumBone == null)
        {
            return;
        }

        Matrix4f raum = raumBone == null ? new Matrix4f() : new Matrix4f(raumBone.mat);
        Matrix4f rein = new Matrix4f(raum).invert();
        Matrix4f lokal = new Matrix4f(rein).mul(eigen);

        RigZwaenge.limitDistance(lokal, rein.transformPosition(zielPunkt(ziel, z)),
            z.daten().getString("modus", "LIMITDIST_INSIDE"),
            z.daten().getFloat("distanz", 0F), Math.min(1F, z.staerke()));

        eigen.set(raum.mul(lokal));
    }

    /**
     * Copy Rotation: Achsen, Invertierung, Mischmodus und Euler-Ordnung aus den Daten
     * heraus an {@link RigZwaenge#copyRotation}.
     *
     * ⛔ {@code euler_ordnung} AUTO heisst: die Ordnung des BESITZERS gilt. Der
     * Extraktor legt sie als {@code eigner_ordnung} daneben, weil ein Quaternion-
     * Knochen in Blender auf XYZ zurueckfaellt und BBS das sonst raten muesste.
     */
    private static void drehZwang(Matrix4f eigen, Matrix4f ziel, Zwang z)
    {
        MapType d = z.daten();
        String ord = d == null ? "AUTO" : d.getString("euler_ordnung", "AUTO");

        if (ord == null || ord.isEmpty() || "AUTO".equals(ord))
        {
            ord = d == null ? "XYZ" : d.getString("eigner_ordnung", "XYZ");
        }

        RigZwaenge.copyRotation(eigen, ziel, ord,
            new boolean[] {z.nutzX(), z.nutzY(), z.nutzZ()},
            new boolean[] {d != null && d.getBool("inv_x", false),
                           d != null && d.getBool("inv_y", false),
                           d != null && d.getBool("inv_z", false)},
            d == null ? "REPLACE" : d.getString("mischung", "REPLACE"),
            Math.min(1F, z.staerke()));
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
        float w = Math.min(1F, z.staerke());

        switch (z.typ())
        {
            case "COPY_TRANSFORMS" ->
            {
                /* ⛔ Blender KOPIERT die Matrix (translike_evaluate: copy_m4_m4), es
                 * zerlegt sie nicht. Ueber Translation/Drehung/Skalierung zerlegt geht
                 * die SCHERUNG verloren — translationRotateScale kann sie gar nicht
                 * bilden. Gemessen an ORG-upper_arm.L: Blender traegt dort Scherung
                 * 0.46, BBS bekam 0.00 heraus, und das floss ueber DAMPED_TRACK in die
                 * ganze tweak-Kette. Erst unter Einfluss < 1 zerlegt auch Blender
                 * (blend_m4_m4m4) — genau der Zweig darunter. */
                if (w >= 1F)
                {
                    eigen.set(ziel).setTranslation(zt);

                    return;
                }

                t.lerp(zt, w);
                q.slerp(zq, w);
                s.lerp(zs, w);
            }
            case "COPY_LOCATION" -> achsen(t, zt, w, z);
            case "COPY_SCALE" ->
            {
                MapType d = z.daten();

                RigZwaenge.copyScale(eigen, ziel,
                    new boolean[] {z.nutzX(), z.nutzY(), z.nutzZ()},
                    d != null && d.getBool("uniform", false),
                    d != null && d.getBool("versatz", false),
                    d == null || d.getBool("multiplizieren", true),
                    d == null ? 1F : d.getFloat("potenz", 1F), w);

                return;
            }
            case "COPY_ROTATION" ->
            {
                /* ⛔ Kein Slerp: Blender rechnet in Euler-Winkeln, je Achse einzeln,
                 * mit vier Mischmodi. Die nutz_*- und inv_*-Flags lagen bis
                 * 2026-08-14 ungelesen im Zwang-Record. */
                drehZwang(eigen, ziel, z);

                return;
            }
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
    /**
     * Die Zielmatrix mit dem Kopf-Schwanz-Punkt als Ort — Blenders {@code head_tail}.
     *
     * ⛔ Die Spur-Constraints bekamen bisher die ROHE Zielmatrix, zielten also immer
     * auf den KOPF. Rigifys tweak-Knochen sitzen per Copy Location genau auf diesem
     * Kopf: sie zielten damit auf sich selbst, und die Spurachse blieb stehen, wo sie
     * gerade war (gemessen 2026-08-14: MCH-upper_arm_tweak.L 14,08 Grad daneben,
     * Blender 0,0000). Betrifft 4 Damped Track und 16 Stretch To.
     */
    private static Matrix4f spurZiel(Matrix4f ziel, Zwang z)
    {
        return new Matrix4f(ziel).setTranslation(zielPunkt(ziel, z));
    }

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
