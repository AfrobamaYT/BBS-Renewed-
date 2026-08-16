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
 * Rig-Mechanik mehr, es vollzieht Blenders nach. Der ganze Matrix-Durchlauf liegt hier,
 * weil die Reihenfolge eine andere ist als BBS' Eltern-vor-Kind: das Werkzeug hat sie
 * topologisch vorberechnet (Ziel vor Besitzer). Was ein Typ noch nicht kann, wird in
 * {@link #unbekannt} GEZAEHLT, nie stillschweigend uebergangen.
 */
public final class RigMechanik implements BOBJArmature.Zwang
{
    record Zwang(String typ, String ziel, float einfluss, boolean welt,
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

    private final List<String> reihenfolge;
    private final Map<String, List<Zwang>> proBone;
    /** Typen, die dieses Rig braucht und Stufe 1 noch nicht kann — mit Anzahl. */
    public final Map<String, Integer> unbekannt = new HashMap<>();
    /** Blenders Treiber: IK/FK, Space-Switching, Stretch. Der Schaltmechanismus. */
    public final RigTreiber treiber = new RigTreiber();

    /** Blenders eigener Solver, portiert — ersetzt BBS' IKTreeSolver. */
    RigIKKetten ketten;

    /** Shape-Key-Name -> Treiber. Das ist die Mechanik eines Gesichts-Boards. */
    final Map<String, MapType> shapeTreiber = new HashMap<>();

    /**
     * Ruhedaten-Flags je Knochen, die von Blenders Vorgabe abweichen — nur die, die
     * abweichen, darum meist eine Handvoll. Sie gehoeren nicht in den Constraint-Stapel,
     * sondern an den Knochen selbst: sie aendern, wie die ELTERNLAGE weitergereicht wird.
     */
    final Map<String, MapType> knochenFlags = new HashMap<>();

    /** Pose-Kanaele, die das Rig AUSGELIEFERT mitbringt (Blenders {@code matrix_basis});
     *  der BOBJ traegt nur die Ruhelage. Rigify backt sie in `MCH-*_foot_parent.*`. */
    final Map<String, Matrix4f> ruhekanal = new HashMap<>();

    /** Knochen mit {@code use_connect}: ihr Kopf klebt am Elternende, auch gegen einen
     *  Constraint. Kommt aus {@code rig.json} (knochen.*.verbunden). */
    final java.util.Set<String> verbunden = new java.util.HashSet<>();

    /** Treiber, die direkt in die POSE eines Knochens schreiben statt in einen Constraint —
     *  Rigifys Fingerkruemmung, Auto-Rig Pros no-stretch-Griffe. */
    final Map<String, List<MapType>> kanalTreiber = new HashMap<>();

    RigMechanik(List<String> reihenfolge, Map<String, List<Zwang>> proBone)
    {
        this.reihenfolge = reihenfolge;
        this.proBone = proBone;
    }

    /** Beim Models-Reload rufen. */
    public static void leeren()
    {
        RigDatei.leeren();
    }

    /**
     * Haengt die Mechanik an die Armature, falls das Modell eine mitbringt. Je Frame billig
     * (nach dem ersten Laden ist es ein Map-Zugriff).
     *
     * ⛔ Die ARMATURE haelt die Mechanik fest, nicht der Cache. Wer hier nur auf
     * {@code zwang != null} prueft, laesst nach {@link #leeren()} — Hotswap, neu
     * exportiertes rig.json — die ALTE Fassung haengen: heute dreimal darauf
     * hereingefallen, einmal mit NullPointerException im Bild, weil ein neu
     * dazugekommenes Feld auf dem alten Objekt fehlte. Verglichen wird darum gegen das,
     * was der Loader JETZT liefert.
     */
    public static void installieren(BOBJModel model, String modellId)
    {
        BOBJArmature armature = model == null ? null : model.getArmature();

        if (armature == null || modellId == null || modellId.isEmpty())
        {
            return;
        }

        if (armature.zwang != null && !(armature.zwang instanceof RigMechanik))
        {
            return;
        }

        RigMechanik mechanik = RigDatei.laden(modellId);

        if (mechanik == null || armature.zwang == mechanik)
        {
            return;
        }

        armature.zwang = mechanik;
        mechanik.flaggen(armature);
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


    /**
     * Einen getriebenen Kanalwert in die Pose schreiben — Blenders Treiber ERSETZT den
     * Kanal, er addiert nicht.
     *
     * ⛔ Ein {@code rotation_euler}-Treiber wirkt in Blender nicht, wenn der Knochen im
     * Quaternion-Modus steht; darum bringt der Export den Modus mit.
     */
    private static void kanalSetzen(BOBJBone bone, MapType k, float wert)
    {
        int achse = Math.max(0, Math.min(2, k.getInt("achse", 0)));
        String art = k.getString("art");

        if ("kanal_skala".equals(art))
        {
            bone.transform.scale.setComponent(achse, wert);
        }
        else if ("kanal_ort".equals(art))
        {
            bone.transform.translate.setComponent(achse, wert);
        }
        else if (!"QUATERNION".equals(k.getString("modus", "XYZ")))
        {
            bone.transform.rotationMode = mchorse.bbs_mod.utils.pose.Transform.RotationMode.EULER;
            bone.transform.rotate.setComponent(achse, wert);
        }
    }

    @Override
    public boolean auswerten(BOBJArmature armature)
    {
        /* TRANSFORMS-Variablen lesen Knochenlagen — der Treiber braucht dafuer die
         * Armature, an der er gerade rechnet. */
        this.treiber.knochen = armature;

        for (String name : this.reihenfolge)
        {
            BOBJBone bone = armature.bones.get(name);

            if (bone == null)
            {
                continue;
            }

            /* ⛔ VOR compute(): ein Kanal-Treiber schreibt die Pose des Knochens, und
             * genau daraus rechnet compute() die Matrix. Danach waere es wirkungslos. */
            List<MapType> kanaele = this.kanalTreiber == null ? null : this.kanalTreiber.get(name);

            if (kanaele != null)
            {
                for (MapType k : kanaele)
                {
                    kanalSetzen(bone, k, this.treiber.wert(k.getMap("treiber"), 0F));
                }
            }

            armature.matrices[bone.index] = bone.compute();

            /* ⛔ null-fest: ein per Hotswap dazugekommenes Feld ist auf bestehenden
             * Instanzen null, nicht sein Initialisierer. */
            Matrix4f kanal = this.ruhekanal == null ? null : this.ruhekanal.get(name);

            if (kanal != null)
            {
                /* Der ausgelieferte Kanal wirkt wie Blenders matrix_basis: im eigenen
                 * Ruheraum, VOR den Constraints, und die Kinder erben ihn ueber mat. */
                bone.mat.mul(kanal);
                bone.originMat.mul(kanal);
                armature.matrices[bone.index] = bone.hautMatrix();
            }

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

                /* ⛔ Ein VERBUNDENER Knochen klebt mit dem Kopf am Elternende: Blender
                 * laesst einen Constraint ihn drehen, aber nicht verschieben. */
                if (this.verbunden != null && this.verbunden.contains(name))
                {
                    bone.mat.setTranslation(vorher.getTranslation(new Vector3f()));
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
                for (BOBJBone kettenBone : this.ketten.loese(armature, name, this.treiber))
                {
                    armature.matrices[kettenBone.index] = kettenBone.hautMatrix();
                }
            }
        }

        return true;
    }

    /** Blenders Constraints im Armature-Raum, auf die Matrix des Besitzers. */
    /** Rohe Constraint-Daten eines Knochens, in Auswertungsreihenfolge — fuer {@link RigSnap}. */
    public List<MapType> constraintsVon(String bone)
    {
        List<Zwang> l = this.proBone.get(bone);
        List<MapType> raus = new ArrayList<>();

        for (Zwang z : l == null ? List.<Zwang>of() : l)
        {
            raus.add(z.daten());
        }

        return raus;
    }

    /** Die topologische Auswertungsreihenfolge — Ziel vor Besitzer. */
    public List<String> reihenfolge()
    {
        return this.reihenfolge;
    }

    /** Schreibt die getriebenen Shape-Key-Werte in {@code ziel}, ⛔ NUR die getriebenen:
     *  was der Animator selbst gesetzt hat, bleibt stehen. Gibt deren Anzahl zurueck. */
    public int shapeKeys(BOBJArmature armature, mchorse.bbs_mod.obj.shapes.ShapeKeys ziel)
    {
        this.treiber.knochen = armature;

        if (ziel == null || this.shapeTreiber.isEmpty())
        {
            return 0;
        }

        int gesetzt = 0;

        for (Map.Entry<String, MapType> e : this.shapeTreiber.entrySet())
        {
            ziel.shapeKeys.put(e.getKey(), this.treiber.wert(e.getValue(), 0F));
            gesetzt++;
        }

        return gesetzt;
    }

    /** Ob dieses Rig ueberhaupt Shape Keys treibt — spart je Bild eine Kopie. */
    public boolean treibtShapeKeys()
    {
        return !this.shapeTreiber.isEmpty();
    }

    /**
     * Der Pol-Control, der an {@code bone} GERADE wirkt — oder {@code null}. Der Snap
     * schwenkt danach anders: mit Pol ueber dessen Ort, ohne Pol ueber die Drehung des
     * Basis-Controls (Rigifys zwei Zweige in {@code compute_base_rotation}).
     */
    public String polVon(String bone)
    {
        return this.ketten == null ? null : this.ketten.polVon(bone, this.treiber);
    }

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

    /**
     * ⛔ EINFLUSS WIRD ZENTRAL GEMISCHT, NICHT JE ZWANG: Blender wertet jeden Zwang mit
     * VOLLER Staerke aus und mischt danach das Ergebnis mit dem Zustand davor
     * ({@code blend_m4_m4m4} am Ende von {@code BKE_constraints_solve}).
     */
    private static void anwenden(BOBJArmature armature, BOBJBone eigenBone,
        BOBJBone zielBone, Zwang z)
    {
        float w = Math.min(1F, z.staerke());

        if (w <= 0F)
        {
            return;
        }

        if (w < 1F)
        {
            Matrix4f vorher = new Matrix4f(eigenBone.mat);

            vollAnwenden(armature, eigenBone, zielBone, z);
            RigZwaenge.mische(eigenBone.mat, vorher, w);

            return;
        }

        vollAnwenden(armature, eigenBone, zielBone, z);
    }

    private static void vollAnwenden(BOBJArmature armature, BOBJBone eigenBone,
        BOBJBone zielBone, Zwang z)
    {
        Matrix4f eigen = eigenBone.mat;
        Matrix4f ziel = zielBone.mat;
        String typ = z.typ() == null ? "" : z.typ();

        /* ⛔ LOKALER RAUM: Blender kopiert dann nicht die Weltlage, sondern die
         * ABWEICHUNG VON DER RUHELAGE. Im Weltraum gerechnet faellt der Knochen auf
         * die Stelle des Ziels, statt gleich weit auszulenken — sichtbar an Fingern
         * und Zehen, die ineinanderfallen. */
        if (!z.welt() && typ.startsWith("COPY_"))
        {
            Matrix4f basis = RigZwaenge.ruheBasis(eigenBone);
            Matrix4f lokalEigen = new Matrix4f(basis).invert().mul(eigen);

            kopieren(lokalEigen,
                new Matrix4f(RigZwaenge.ruheBasis(zielBone)).invert().mul(ziel), z);
            eigen.set(basis.mul(lokalEigen));

            return;
        }

        switch (typ)
        {
            /* Armature traegt seine Ziele als gewichtete LISTE, nicht ueber z.ziel(). */
            case "ARMATURE" -> armaturZwang(armature, eigen, z);
            case "LIMIT_DISTANCE" -> abstandsZwang(armature, eigen, ziel, z);
            /* Fuss-Rolle: der Ort des Roll-Cursors wird auf die Drehung der Fersen-
             * und Zehenknochen abgebildet und danach je Achse geklemmt. */
            case "TRANSFORM", "LIMIT_ROTATION", "LIMIT_LOCATION" ->
                RigLokal.anwenden(eigenBone, zielBone, typ, z.daten());
            /* Child Of: der Besitzer erbt die ABWEICHUNG des Ziels von dessen Ruhelage —
             * Blenders inverse_matrix steht bei erzeugten Rigs in der Ruhepose. */
            case "CHILD_OF" -> eigen.mulLocal(new Matrix4f(ziel).mul(zielBone.invBoneMat));
            case "TRACK_TO", "LOCKED_TRACK" -> zielen(eigen, ziel, z);
            case "DAMPED_TRACK" -> RigZwaenge.dampedTrack(eigen, spurZiel(ziel, z), z.spur(), 1F);
            case "STRETCH_TO" ->
            {
                MapType d = z.daten();
                /* ⛔ Blender misst gegen data->orglength, die Ruhelaenge des CONSTRAINTS,
                 * nicht gegen die Knochenlaenge: Rigifys VIS_*-Zeiger sind 0,045 lang und
                 * ueberspannen eine halbe Beinlaenge. ⛔ volumen ist ein ENUM-STRING; als
                 * Float gelesen kam still 0 heraus, also NO_VOLUME fuer alle 144. */
                float ruhe = d == null ? 0F : d.getFloat("ruhe_laenge", 0F);
                String volumen = d == null ? "VOLUME_XZX" : d.getString("volumen", "VOLUME_XZX");

                RigZwaenge.stretchTo(eigen, spurZiel(ziel, z), z.spur(),
                    ruhe > 0F ? ruhe : eigenBone.restLength,
                    "NO_VOLUME".equals(volumen) ? 0F : 1F, 1F);
            }
            default -> kopieren(eigen, ziel, z);
        }
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
            z.daten().getFloat("distanz", 0F), 1F);

        eigen.set(raum.mul(lokal));
    }

    /** Copy Rotation an {@link RigZwaenge#copyRotation}. ⛔ {@code euler_ordnung} AUTO
     *  heisst: die Ordnung des BESITZERS gilt — sie liegt als {@code eigner_ordnung}
     *  daneben, weil ein Quaternion-Knochen in Blender auf XYZ zurueckfaellt. */
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
            d == null ? "REPLACE" : d.getString("mischung", "REPLACE"), 1F);
    }

    /** Der Kopier-Kern — im Welt- wie im Lokalraum derselbe, nur mit anderen Matrizen. */
    private static void kopieren(Matrix4f eigen, Matrix4f ziel, Zwang z)
    {
        /* Volle Staerke — der Einfluss wird zentral in anwenden() gemischt. */
        Vector3f t = eigen.getTranslation(new Vector3f());
        Vector3f zt = zielPunkt(ziel, z);

        switch (z.typ())
        {
            case "COPY_TRANSFORMS" ->
            {
                /* ⛔ Blender KOPIERT die Matrix (translike_evaluate), es zerlegt sie
                 * nicht: ueber Ort/Drehung/Groesse zerlegt geht die SCHERUNG verloren,
                 * die ein STRETCH_TO erzeugt hat. */
                eigen.set(ziel).setTranslation(zt);
            }
            case "COPY_LOCATION" ->
            {
                /* ⛔ Blender setzt NUR die Ortsspalte (`copy_v3_v3(cob->matrix[3], ...)`).
                 * Der Weg ueber translationRotateScale baute die Matrix neu zusammen und
                 * warf dabei jede Scherung weg, die ein STRETCH_TO davor erzeugt hatte. */
                ortKopieren(t, zt, z.daten(), z);
                eigen.setTranslation(t);
            }
            case "COPY_SCALE" ->
            {
                MapType d = z.daten();

                RigZwaenge.copyScale(eigen, ziel,
                    new boolean[] {z.nutzX(), z.nutzY(), z.nutzZ()},
                    d != null && d.getBool("uniform", false),
                    d != null && d.getBool("versatz", false),
                    d == null || d.getBool("multiplizieren", true),
                    d == null ? 1F : d.getFloat("potenz", 1F), 1F);
            }
            case "COPY_ROTATION" ->
            {
                /* ⛔ Kein Slerp: Blender rechnet in Euler-Winkeln, je Achse einzeln,
                 * mit vier Mischmodi. Die nutz_*- und inv_*-Flags lagen bis
                 * 2026-08-14 ungelesen im Zwang-Record. */
                drehZwang(eigen, ziel, z);
            }
            default ->
            {
            }
        }
    }

    /** Track To / Locked Track — die Rechnung liegt in {@link RigZwaenge#trackTo}. */
    private static void zielen(Matrix4f eigen, Matrix4f ziel, Zwang z)
    {
        MapType d = z.daten();
        ListType hoch = d == null ? null : d.getList("hoch_vektor");

        /* Track To legt den Roll ueber die Hoch-Achse fest; ohne den Vektor (alte
         * Exportdatei) bleibt es beim bisherigen Weg. */
        if ("TRACK_TO".equals(z.typ()) && hoch != null && hoch.size() >= 3)
        {
            /* "Target Z": die Hoch-Referenz ist die Z-Achse des ZIELS, nicht die
             * Weltachse — beide Fremd-Rigs setzen das an ihren Zehen-Trackern. */
            Vector3f auf = d.getBool("ziel_z", false)
                ? new Vector3f(ziel.m20(), ziel.m21(), ziel.m22()).normalize()
                : new Vector3f(hoch.getFloat(0), hoch.getFloat(1), hoch.getFloat(2));

            RigZwaenge.trackToBasis(eigen, zielPunkt(ziel, z), z.spur(),
                d.getString("hoch_achse", "UP_Z"), auf);

            return;
        }

        RigZwaenge.trackTo(eigen, zielPunkt(ziel, z), z.spur(),
            "LOCKED_TRACK".equals(z.typ()) ? z.sperre().replace("LOCK_", "TRACK_") : null);
    }

    /** Zielmatrix mit dem Kopf-Schwanz-Punkt als Ort — Blenders {@code head_tail}.
     *  ⛔ Ohne ihn zielen die Spur-Constraints auf den KOPF, und Rigifys tweak-Knochen
     *  sitzen per Copy Location genau dort: sie zielten auf sich selbst. */
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

    /** Blenders {@code loclike_evaluate}: je Achse kopieren, wahlweise NEGIEREN,
     *  wahlweise auf den eigenen Ort ADDIEREN (invert_* und use_offset). */
    private static void ortKopieren(Vector3f eigen, Vector3f ziel, MapType d, Zwang z)
    {
        boolean versatz = d != null && d.getBool("versatz", false);
        float[] eigenAlt = {eigen.x, eigen.y, eigen.z};
        float[] neu = {ziel.x, ziel.y, ziel.z};

        for (int a = 0; a < 3; a++)
        {
            String achse = "xyz".substring(a, a + 1);

            if (!(a == 0 ? z.nutzX() : a == 1 ? z.nutzY() : z.nutzZ()))
            {
                neu[a] = eigenAlt[a];

                continue;
            }

            if (d != null && d.getBool("inv_" + achse, false))
            {
                neu[a] = -neu[a];
            }

            if (versatz)
            {
                neu[a] += eigenAlt[a];
            }
        }

        eigen.set(neu[0], neu[1], neu[2]);
    }

}
