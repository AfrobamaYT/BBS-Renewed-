package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.bobj.BOBJArmature;
import mchorse.bbs_mod.bobj.BOBJBBone;
import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.cubic.model.bobj.BOBJModel;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;

import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fuehrt den importierten Constraint-Graphen des Blender-Rigs aus — BBS erzeugt keine
 * Rig-Mechanik mehr, es vollzieht Blenders nach: {@code BKE_pose_where_is_bone} je Knochen
 * (Kanal-Treiber, Elternuebergabe, Constraint-Stapel in Blenders WELTRAUM ueber
 * {@link RigRahmen}/{@link RigRaum}, Einfluss als {@code interp_m4_m4m4}), IK-Baeume an der
 * Stelle, an der ihre Gruppe faellig ist ({@link RigIKKetten}), danach die B-Bone-Segmente
 * ({@link RigBBone}). Was ein Typ noch nicht kann, wird in {@link #unbekannt} GEZAEHLT.
 */
public final class RigMechanik implements BOBJArmature.Zwang
{
    private final List<String> reihenfolge;
    /** Constraint-Stapel je Knochen (ohne IK), in Blenders Stapelreihenfolge. */
    private final Map<String, List<MapType>> proBone;
    /** Typen, die dieses Rig braucht und die Laufzeit nicht kann — mit Anzahl. */
    public final Map<String, Integer> unbekannt = new HashMap<>();
    /** Blenders Treiber: IK/FK, Space-Switching, Stretch. Der Schaltmechanismus. */
    public final RigTreiber treiber = new RigTreiber();

    RigIKKetten ketten;
    final Map<String, MapType> shapeTreiber = new HashMap<>();
    final Map<String, MapType> knochenFlags = new HashMap<>();
    final Map<String, MapType> huellen = new HashMap<>();
    /** Pose-Kanaele, die das Rig AUSGELIEFERT mitbringt (Blenders {@code matrix_basis}). */
    final Map<String, Matrix4f> ruhekanal = new HashMap<>();
    final Map<String, List<MapType>> kanalTreiber = new HashMap<>();
    final List<MapType> propTreiber = new ArrayList<>();
    final List<MapType> bboneTreiber = new ArrayList<>();
    /** Lazy auch fuer vor dem Hotswap geladene Rigs: die Liste ist nach dem Import unveraenderlich. */
    private Map<String, List<MapType>> bboneTreiberNachBone;
    RigRahmen rahmen = RigRahmen.einheit();
    /** {@code pose_position == REST}: Blender zeigt dann die Ruhelage, ohne Pose und Constraints. */
    boolean ruhelage;

    private final List<String> bboneKnochen = new ArrayList<>();

    RigMechanik(List<String> reihenfolge, Map<String, List<MapType>> proBone)
    {
        this.reihenfolge = reihenfolge;
        this.proBone = proBone;
    }

    @Override
    public long eingabeSignatur()
    {
        return this.treiber.regler.eingabeSignatur() * 31L
            + Float.floatToIntBits(this.treiber.bild);
    }

    /** Beim Models-Reload rufen. */
    public static void leeren()
    {
        RigDatei.leeren();
    }

    /**
     * Haengt die Mechanik an die Armature, falls das Modell eine mitbringt.
     *
     * ⛔ Die ARMATURE haelt die Mechanik fest, nicht der Cache: nach {@link #leeren()}
     * (Hotswap, neu exportiertes rig.json) muss die ALTE Fassung ersetzt werden — verglichen
     * wird darum gegen das, was der Loader JETZT liefert.
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

    /** Setzt Blenders Knochen-Flags, Huellen und B-Bone-Daten auf DIESE Armature. */
    private void flaggen(BOBJArmature armature)
    {
        this.treiber.rahmen = this.rahmen;
        armature.rigPoseWandler = this.rahmen.poseWandler();
        this.bboneKnochen.clear();

        for (BOBJBone bone : armature.orderedBones)
        {
            MapType f = this.knochenFlags.get(bone.name);
            String skala = f == null ? null : f.getString("skala");
            String modus = f == null ? null : f.getString("dreh_modus");

            bone.elternraumVerschiebung = f != null && !f.getBool("lokal_loc", true);
            bone.skalenErbe = skala == null || skala.isEmpty() || "FULL".equals(skala) ? null : skala;
            bone.verbunden = f != null && f.getBool("verbunden", false);
            bone.keineElternDrehung = f != null && !f.getBool("erbt_dreh", true);
            bone.eulerOrdnung = modus != null && modus.length() == 3 && !"XYZ".equals(modus) ? modus : null;
            bone.drehModus = modus == null || modus.isEmpty() || "XYZ".equals(modus) ? null : modus;
            bone.bbone = f != null && f.has("bbone") ? bbone(f.getMap("bbone")) : null;

            if (bone.bbone != null)
            {
                this.bboneKnochen.add(bone.name);
            }

            MapType h = this.huellen.get(bone.name);

            bone.huelle = h == null ? null : new float[] {h.getFloat("kopf_radius", 0F),
                h.getFloat("schwanz_radius", 0F), h.getFloat("abstand", 0F)};
            bone.huelleMult = h != null && h.getBool("mult_huelle", false);
        }

        for (String name : this.bboneKnochen)
        {
            BOBJBBone d = armature.bones.get(name).bbone;

            for (int i = 0; i < 2; i++)
            {
                d.griffKnochen[i] = d.griff[i] == null ? null : armature.bones.get(d.griff[i]);
            }
        }

        RigBBone.kinderZuweisen(armature);
    }

    private static BOBJBBone bbone(MapType m)
    {
        BOBJBBone d = new BOBJBBone();

        d.segmente = m.getInt("segmente", 1);
        d.x = m.getFloat("x", 0F);
        d.z = m.getFloat("z", 0F);
        d.mapping = m.getString("mapping", "STRAIGHT");
        d.endrollAlsInroll = m.getBool("endroll_als_inroll", false);
        d.skaliertEasing = m.getBool("skaliert_easing", false);

        ListType typ = m.getList("griff_typ");
        ListType griff = m.getList("griff");
        ListList skala = new ListList(m.getList("griff_skala"));
        ListType ease = m.getList("griff_ease");

        for (int i = 0; i < 2; i++)
        {
            d.griffTyp[i] = typ == null || i >= typ.size() ? "AUTO" : typ.getString(i);
            d.griff[i] = griff == null || i >= griff.size() ? null : griff.getString(i);
            d.griffEase[i] = ease != null && i < ease.size() && ease.getBool(i);

            for (int k = 0; k < 3; k++)
            {
                d.griffSkala[i][k] = skala.bool(i, k);
            }
        }

        ebene(m.getMap("ruhe"), d.ruheEase, d.ruheRoll, d.ruheKurveIn, d.ruheKurveOut, d.ruheSkalaIn, d.ruheSkalaOut);
        ebene(m.getMap("pose"), d.poseEase, d.poseRoll, d.poseKurveIn, d.poseKurveOut, d.poseSkalaIn, d.poseSkalaOut);

        return d;
    }

    private record ListList(ListType l)
    {
        boolean bool(int i, int k)
        {
            ListType inner = this.l == null || i >= this.l.size() ? null : this.l.getList(i);

            return inner != null && k < inner.size() && inner.getBool(k);
        }
    }

    private static void ebene(MapType m, float[] ease, float[] roll, float[] kurveIn, float[] kurveOut,
                              float[] skalaIn, float[] skalaOut)
    {
        if (m == null)
        {
            return;
        }

        fuelle(m.getList("ease"), ease);
        fuelle(m.getList("roll"), roll);
        fuelle(m.getList("kurve_in"), kurveIn);
        fuelle(m.getList("kurve_out"), kurveOut);
        fuelle(m.getList("skala_in"), skalaIn);
        fuelle(m.getList("skala_out"), skalaOut);
    }

    private static void fuelle(ListType l, float[] ziel)
    {
        for (int i = 0; l != null && i < ziel.length && i < l.size(); i++)
        {
            ziel[i] = l.getFloat(i);
        }
    }

    /* ================== Auswertung ================== */

    @Override
    public boolean auswerten(BOBJArmature armature)
    {
        this.treiber.knochen = armature;
        this.treiber.rahmen = this.rahmen;

        if (this.ruhelage)
        {
            for (BOBJBone bone : armature.orderedBones)
            {
                bone.mat.set(bone.boneMat);
                bone.originMat.set(bone.boneMat);
                armature.matrices[bone.index] = bone.hautMatrix();
            }

            return true;
        }

        this.eigenschaftsTreiber();

        java.util.Set<BOBJBone> verarbeitet = new java.util.HashSet<>();
        Map<String, List<MapType>> bboneNachBone = this.bboneTreiberNachBone;

        if (bboneNachBone == null)
        {
            bboneNachBone = new HashMap<>();

            for (MapType treiber : this.bboneTreiber)
            {
                String bone = treiber.getString("bone");

                if (bone != null)
                {
                    List<MapType> liste = bboneNachBone.get(bone);

                    if (liste == null)
                    {
                        liste = new ArrayList<>();
                        bboneNachBone.put(bone, liste);
                    }

                    liste.add(treiber);
                }
            }

            this.bboneTreiberNachBone = bboneNachBone;
        }

        for (String name : this.reihenfolge)
        {
            BOBJBone bone = armature.bones.get(name);

            if (bone == null)
            {
                continue;
            }

            /* ⛔ VOR compute(): Kanal-Treiber schreiben die Pose, aus der compute() rechnet. */
            for (MapType k : this.kanalTreiber.getOrDefault(name, List.of()))
            {
                kanalSetzen(bone, k, this.treiber.wert(k.getMap("treiber"), 0F));
            }

            for (MapType k : bboneNachBone.getOrDefault(name, List.of()))
            {
                bboneSetzen(bone, k, this.treiber.wert(k.getMap("treiber"), 0F));
            }

            armature.matrices[bone.index] = bone.compute();

            Matrix4f kanal = this.ruhekanal.get(name);

            if (kanal != null)
            {
                /* Wirkt wie Blenders matrix_basis: im eigenen Ruheraum, VOR den Constraints. */
                bone.mat.mul(kanal);
                bone.originMat.mul(kanal);
            }

            List<MapType> liste = this.proBone.get(name);

            if (liste != null && !liste.isEmpty())
            {
                this.zwaenge(armature, bone, liste);
            }

            armature.matrices[bone.index] = bone.hautMatrix();

            verarbeitet.add(bone);

            if (this.ketten != null && this.ketten.hat(name))
            {
                List<BOBJBone> kette = this.ketten.loese(armature, name, this.treiber, this.rahmen);

                for (BOBJBone b : kette)
                {
                    armature.matrices[b.index] = b.hautMatrix();
                }

                this.nachrechnen(armature, kette, verarbeitet);
            }
        }

        /* Segmente NACH der ganzen Pose: die Griffe sind fremde Knochen mit Endlage. */
        for (String name : this.bboneKnochen)
        {
            RigBBone.cacheBerechnen(this.rahmen, armature.bones.get(name));
        }

        return true;
    }

    /**
     * Nach einer IK-Loesung: Knochen, die schon gerechnet waren und unter einem Kettenknochen
     * haengen, ohne selbst zur Kette zu gehoeren (Spitze bei {@code use_tail=False}, Kinder) —
     * in Blender haengt ihr BONE_DONE hinter dem IK-Solver, hier wird nachgerechnet.
     */
    private void nachrechnen(BOBJArmature armature, List<BOBJBone> kette, java.util.Set<BOBJBone> verarbeitet)
    {
        java.util.Set<BOBJBone> ketteSet = new java.util.HashSet<>(kette);

        for (String name : this.reihenfolge)
        {
            BOBJBone bone = armature.bones.get(name);

            if (bone == null || !verarbeitet.contains(bone) || ketteSet.contains(bone))
            {
                continue;
            }

            boolean nachfahre = false;

            for (BOBJBone p = bone.parentBone; p != null && !nachfahre; p = p.parentBone)
            {
                nachfahre = ketteSet.contains(p);
            }

            if (!nachfahre)
            {
                continue;
            }

            armature.matrices[bone.index] = bone.compute();

            Matrix4f kanal = this.ruhekanal.get(name);

            if (kanal != null)
            {
                bone.mat.mul(kanal);
                bone.originMat.mul(kanal);
            }

            List<MapType> liste = this.proBone.get(name);

            if (liste != null && !liste.isEmpty())
            {
                this.zwaenge(armature, bone, liste);
            }

            armature.matrices[bone.index] = bone.hautMatrix();
            ketteSet.add(bone);
        }
    }

    /** Getriebene Custom Properties (Regler treibt Regler) — typgerecht wie Blender. */
    private void eigenschaftsTreiber()
    {
        for (MapType p : this.propTreiber)
        {
            String bone = p.getString("bone");
            String prop = p.getString("prop");
            float alt = this.treiber.regler.wertVon(bone, prop, 0F);
            float v = this.treiber.wert(p.getMap("treiber"), alt);
            String art = this.treiber.regler.artVon(bone, prop);
            MapType schema = this.treiber.regler.reglerVon(bone).get(prop);

            /* Custom Properties tragen ihren harten Bereich (id_properties_ui min/max). */
            if (schema != null && schema.has("min"))
            {
                v = Math.max(schema.getFloat("min", -Float.MAX_VALUE), v);
            }

            if (schema != null && schema.has("max"))
            {
                v = Math.min(schema.getFloat("max", Float.MAX_VALUE), v);
            }

            if ("int".equals(art))
            {
                v = (int) v;
            }
            else if ("bool".equals(art))
            {
                v = v > RigTreiber.BOOL_SCHWELLE ? 1F : 0F;
            }

            if (!this.treiber.regler.setze(bone, prop, v))
            {
                this.unbekannt.merge("prop-treiber:" + bone + "." + prop, 1, Integer::sum);
            }
        }
    }

    /**
     * Einen getriebenen Kanalwert in die Pose schreiben — Blenders Treiber ERSETZT den Kanal.
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

    /** B-Bone-Kanal der Pose- oder Knochen-Ebene, getrieben (Rigifys rubber_tweak). */
    private static void bboneSetzen(BOBJBone bone, MapType k, float wert)
    {
        BOBJBBone d = bone.bbone;

        if (d == null)
        {
            return;
        }

        boolean ruhe = k.getBool("ruhe", false);
        int achse = Math.max(0, Math.min(2, k.getInt("achse", 0)));

        switch (k.getString("feld", ""))
        {
            case "bbone_easein" -> (ruhe ? d.ruheEase : d.poseEase)[0] = wert;
            case "bbone_easeout" -> (ruhe ? d.ruheEase : d.poseEase)[1] = wert;
            case "bbone_rollin" -> (ruhe ? d.ruheRoll : d.poseRoll)[0] = wert;
            case "bbone_rollout" -> (ruhe ? d.ruheRoll : d.poseRoll)[1] = wert;
            case "bbone_curveinx" -> (ruhe ? d.ruheKurveIn : d.poseKurveIn)[0] = wert;
            case "bbone_curveinz" -> (ruhe ? d.ruheKurveIn : d.poseKurveIn)[1] = wert;
            case "bbone_curveoutx" -> (ruhe ? d.ruheKurveOut : d.poseKurveOut)[0] = wert;
            case "bbone_curveoutz" -> (ruhe ? d.ruheKurveOut : d.poseKurveOut)[1] = wert;
            case "bbone_scalein" -> (ruhe ? d.ruheSkalaIn : d.poseSkalaIn)[achse] = wert;
            case "bbone_scaleout" -> (ruhe ? d.ruheSkalaOut : d.poseSkalaOut)[achse] = wert;
            default ->
            {}
        }
    }

    /** {@code BKE_constraints_solve} auf dem Stapel EINES Knochens, in Blenders Weltraum. */
    private void zwaenge(BOBJArmature armature, BOBJBone bone, List<MapType> liste)
    {
        float[] cob = this.rahmen.weltMat(bone);
        float[] kopfVorher = RigMathe.ort(cob);
        Matrix4f vorher = new Matrix4f(bone.mat);

        for (MapType c : liste)
        {
            if (c.getBool("ungueltig", false) || this.treiber.abgeschaltet(c))
            {
                continue;
            }

            /* ⛔ Treiber ZUERST: der IK/FK-Wechsel ist ein Treiber auf DIESER Influence, und
             * getriebene Felder (head_tail, eval_time, ...) stehen erst danach im Constraint. */
            float enf = this.treiber.einfluss(c);

            this.treiber.felderSetzen(c);

            if (enf == 0F)
            {
                continue;
            }

            float[] eigenRaum = RigZiel.eigenRaum(this.rahmen, armature, c);
            float[] oldmat = RigMathe.kopie(cob);
            String eigen = c.getString("raum_o", "WORLD");

            cob = RigRaum.wandle(this.rahmen, bone, bone, eigenRaum, cob, RigRaum.WELT, eigen, false);

            boolean einmal = this.anwenden(armature, bone, cob, c, eigenRaum);

            if (!einmal)
            {
                cob = RigRaum.wandle(this.rahmen, bone, bone, eigenRaum, cob, eigen, RigRaum.WELT, false);
            }

            /* Einfluss < 1 mischt in Blender das ERGEBNIS mit dem Zustand davor — polar
             * zerlegt, nicht elementweise (interp_m4_m4m4 seit 4.2). */
            if (enf < 1F)
            {
                cob = RigZerlegung.interpoliere(oldmat, cob, enf);
            }
        }

        /* "prevent constraints breaking a chain": ein verbundener Kopf bleibt am Elternende. */
        if (bone.verbunden)
        {
            RigMathe.setzeOrt(cob, kopfVorher);
        }

        this.rahmen.setzeWeltMat(bone, cob);

        /* ⛔ originMat MUSS mitwandern: das Mesh liest mat, die Control-Widgets originMat. */
        bone.originMat.set(new Matrix4f(bone.mat).mul(vorher.invert()).mul(bone.originMat));
    }

    /** Ein Constraint auf {@code cob} (im Besitzerraum); true = Child Of hat SPACEONCE gesetzt. */
    private boolean anwenden(BOBJArmature armature, BOBJBone bone, float[] cob, MapType c, float[] eigenRaum)
    {
        String typ = c.getString("typ", "");
        RigZwangBasis.Besitzer b = new RigZwangBasis.Besitzer(this.rahmen, bone, RigTreiber.ordnung(bone));

        switch (typ)
        {
            case "LIMIT_LOCATION" -> RigZwangGrenze.limitLocation(cob, c);
            case "LIMIT_ROTATION" -> RigZwangGrenze.limitRotation(cob, c, b);
            case "LIMIT_SCALE" -> RigZwangGrenze.limitScale(cob, c);
            case "MAINTAIN_VOLUME" -> RigZwangLage.maintainVolume(cob, c);
            case "ARMATURE" -> this.armatur(armature, bone, cob, c);
            case "PIVOT" -> RigZwangLage.pivot(cob, this.ziel(armature, c, bone, eigenRaum, typ), c, b);
            case "ACTION" ->
            {
                float[] m = RigZwangAction.actionMatrix(this.ziel(armature, c, bone, eigenRaum, typ), c, b, 0F);

                if (m != null)
                {
                    RigZwangKopie.copyTransforms(cob, m, RigZwangBasis.text(c, "mix_mode", "mischung", "AFTER_FULL"), false);
                }
            }
            default ->
            {
                RigZwangBasis.Ziel z = this.ziel(armature, c, bone, eigenRaum, typ);

                if (z == null)
                {
                    return false;
                }

                return this.mitZiel(typ, cob, z, c, b, bone);
            }
        }

        return false;
    }

    private boolean mitZiel(String typ, float[] cob, RigZwangBasis.Ziel z, MapType c, RigZwangBasis.Besitzer b, BOBJBone bone)
    {
        switch (typ)
        {
            case "COPY_LOCATION" -> RigZwangKopie.copyLocation(cob, z, c);
            case "COPY_ROTATION" -> RigZwangKopie.copyRotation(cob, z, c, b);
            case "COPY_SCALE" -> RigZwangKopie.copyScale(cob, z, c);
            case "COPY_TRANSFORMS" -> RigZwangKopie.copyTransforms(cob, z.matrix(),
                RigZwangBasis.text(c, "mix_mode", "mischung", "REPLACE"),
                RigZwangBasis.flag(c, "remove_target_shear", null, false));
            case "LIMIT_DISTANCE" -> RigZwangGrenze.limitDistance(cob, z, c);
            case "TRACK_TO" -> RigZwangSpur.trackTo(cob, z, c);
            case "LOCKED_TRACK" -> RigZwangSpur.lockedTrack(cob, z, c);
            case "DAMPED_TRACK" -> RigZwangSpur.dampedTrack(cob, z, c);
            case "STRETCH_TO" -> RigZwangStreck.stretchTo(cob, z, c, bone);
            case "TRANSFORM" -> RigZwangTransform.transformation(cob, z, c, b);
            case "FLOOR" -> RigZwangLage.floor(cob, z, c);
            case "CHILD_OF" ->
            {
                RigZwangLage.childOf(cob, z, c, b);

                return true;
            }
            default -> this.unbekannt.merge(typ, 1, Integer::sum);
        }

        return false;
    }

    /** {@code constraint_target_to_mat4} + Zielraum; null = kein gueltiges Ziel (Blender rechnet dann nicht). */
    private RigZwangBasis.Ziel ziel(BOBJArmature armature, MapType c, BOBJBone besitzer, float[] eigenRaum, String typ)
    {
        String name = c.getString("ziel", "");
        boolean objekt = c.getBool("ziel_objekt", false);
        BOBJBone zb = name == null || name.isEmpty() ? null : armature.bones.get(name);

        if (zb == null && !objekt)
        {
            return null;
        }

        float[] m = RigZiel.zielMatrix(this.rahmen, zb, besitzer, eigenRaum, c.getString("raum_z", "WORLD"),
            RigZwangBasis.zahl(c, "head_tail", "kopf_schwanz", 0F), RigZwangBasis.flag(c, "use_bbone_shape", null, false),
            "COPY_TRANSFORMS".equals(typ));

        return new RigZwangBasis.Ziel(m, zb, zb == null ? "XYZ" : RigTreiber.ordnung(zb));
    }

    /** Armature-Constraint: gewichtete Verform-Matrizen der Ziele, Gewichte getrieben. */
    private void armatur(BOBJArmature armature, BOBJBone bone, float[] cob, MapType c)
    {
        ListType ziele = c.getList("ziele");

        if (ziele == null)
        {
            return;
        }

        List<BOBJBone> bs = new ArrayList<>(ziele.size());
        float[] gewichte = new float[ziele.size()];

        for (int i = 0; i < ziele.size(); i++)
        {
            MapType t = ziele.getMap(i);

            gewichte[i] = RigTreiber.klemme("weight", this.treiber.wert(t.getMap("treiber"), t.getFloat("gewicht", 0F)));
            bs.add(armature.bones.get(t.getString("bone", "")));
        }

        boolean quaternion = c.getBool("quaternion", RigZwangBasis.flag(c, "use_deform_preserve_volume", null, false));
        boolean huellen = c.getBool("huellen", RigZwangBasis.flag(c, "use_bone_envelopes", null, false));
        float[] eigenKopf = RigZwangBasis.flag(c, "use_current_location", null, false)
            ? RigMathe.ort(cob) : RigMathe.ort(this.rahmen.zuWelt(bone.boneMat.get(new float[16])));

        RigZwangArmature.armature(cob, bs, gewichte, this.rahmen, quaternion, huellen, eigenKopf);
    }

    /* ================== Schnittstelle fuer Snap, Regler, Zeichner ================== */

    /** Rohe Constraint-Daten eines Knochens (ohne IK), in Stapelreihenfolge — fuer {@link RigSnap}. */
    public List<MapType> constraintsVon(String bone)
    {
        return new ArrayList<>(this.proBone.getOrDefault(bone, List.of()));
    }

    /** Die topologische Auswertungsreihenfolge — Ziel vor Besitzer. */
    public List<String> reihenfolge()
    {
        return this.reihenfolge;
    }

    /** Schreibt die getriebenen Shape-Key-Werte in {@code ziel}, ⛔ NUR die getriebenen. */
    public int shapeKeys(BOBJArmature armature, mchorse.bbs_mod.obj.shapes.ShapeKeys ziel)
    {
        this.treiber.knochen = armature;
        this.treiber.rahmen = this.rahmen;

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

    public boolean treibtShapeKeys()
    {
        return !this.shapeTreiber.isEmpty();
    }

    /** Der Pol-Control, der an {@code bone} GERADE wirkt — oder {@code null}. */
    public String polVon(String bone)
    {
        return this.ketten == null ? null : this.ketten.polVon(bone, this.treiber);
    }
}
