package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.bobj.BOBJArmature;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Das Mixamo-Control-Rig aus Blenders "mixamo_rig"-Addon, 1:1 live extrahiert
 * (tools/rig-extrakt.py): je Control die Original-Wire-Polylinien BONE-LOKAL im
 * BOBJ-Bone-Raum seines Ankers, dazu die Addon-Farben und der IK/FK-Schalter.
 * Ein leerer Anker heisst: das Control haengt an der ganzen Form (Ctrl_Master).
 */
public class RigCtrl
{
    /** Rig-Layer -> sichtbar. Blenders Bone-Collections, wie in seiner Seitenleiste.
     * ⛔ OHNE Initialisierer und lazy geholt: ein neues statisches Feld MIT Zuweisung
     * waere nach einem Hotswap null, weil `<clinit>` nicht erneut laeuft. */
    private static java.util.Map<String, Boolean> lagen;
    private static List<Lage> lagenReihen;

    /* Lazy (Hotswap: neue statics sind null). Leere Liste = Modell hat kein eigenes rig.json. */
    private static java.util.Map<String, List<Eintrag>> proModell;

    public record Eintrag(String name, String anker, String pick, int farbeN, int farbeA,
        float breite, String schalter, boolean ik, boolean dragRot, boolean fest,
        List<String> lagen, float[][] linien) {}

    /** Das importierte Control-Rig dieses Modells, sonst null. Einziges Kriterium:
     *  das Modell bringt ein rig.json mit — BBS erzeugt selbst keine Rigs mehr. */
    public static List<Eintrag> fuer(BOBJArmature armature, String modellId)
    {
        if (armature == null || modellId == null || modellId.isEmpty())
        {
            return null;
        }

        List<Eintrag> eigen = eigenes(modellId);

        return eigen.isEmpty() ? null : eigen;
    }

    /** Beim Models-Reload rufen: die modell-eigenen rig.json werden neu gelesen. */
    public static void leeren()
    {
        proModell = null;
    }

    private static List<Eintrag> eigenes(String modellId)
    {
        if (proModell == null)
        {
            proModell = new java.util.HashMap<>();
        }

        return proModell.computeIfAbsent(modellId, id ->
        {
            try (InputStream rein = mchorse.bbs_mod.BBSMod.getProvider()
                .getAsset(mchorse.bbs_mod.resources.Link.assets("models/" + id + "/rig.json")))
            {
                return parse(new String(rein.readAllBytes(), StandardCharsets.UTF_8));
            }
            catch (Exception e)
            {
                return Collections.emptyList();
            }
        });
    }

    /** Der Weg ueber die Entity — fuer Aufrufer, die nur sie haben. */
    public static List<Eintrag> fuerEntity(mchorse.bbs_mod.forms.entities.IEntity entity)
    {
        if (entity == null
            || !(entity.getForm() instanceof mchorse.bbs_mod.forms.forms.ModelForm form)
            || !(mchorse.bbs_mod.forms.FormUtilsClient.getRenderer(form) instanceof mchorse.bbs_mod.forms.renderers.ModelFormRenderer renderer))
        {
            return null;
        }

        mchorse.bbs_mod.cubic.ModelInstance instance = renderer.getModel();

        return instance != null && instance.model instanceof mchorse.bbs_mod.cubic.model.bobj.BOBJModel bobj
            ? fuer(bobj.getArmature(), form.model.get()) : null;
    }

    /** Gate fuer Gizmo/Umriss — je Frame billig (die Liste ist gecacht). */
    public static boolean aktivFuer(mchorse.bbs_mod.forms.entities.IEntity entity)
    {
        return fuerEntity(entity) != null;
    }

    /**
     * Blender-Kanon fuer LMB-Drag auf dem Control dieses PICK-Bones: translate (G) —
     * ausser das Control ist location-gelockt (die FK-Ringe), dann dreht der Drag (R).
     */
    /**
     * Haelt Blender dieses Control fest? Die Zahnrad-Widgets eines Glieds
     * ({@code upper_arm_parent.R}) tragen alle neun Sperren: anklickbar, damit man ihre
     * Regler sieht, aber unbeweglich. Ohne diese Pruefung liess BBS sie ziehen und das
     * Rig verhielt sich anders als in Blender (gemeldet 2026-08-14).
     */
    public static boolean gesperrt(List<Eintrag> rig, String pickBone)
    {
        for (Eintrag e : rig == null ? Collections.<Eintrag>emptyList() : rig)
        {
            if (e.pick.equals(pickBone))
            {
                return e.fest;
            }
        }

        return false;
    }

    /**
     * Ist dieser Knochen in IRGENDEINEM geladenen Rig ein gesperrtes Control?
     *
     * <p>Fuer Aufrufer tief im UI-Baum, die kein Modell zur Hand haben (die Pose-Tasten
     * etwa). ⚠️ Gleiche Knochennamen in zwei verschiedenen Rigs teilen sich damit das
     * Urteil — bei Rigify/mixamo_rig sind die Namen rig-typisch, in der Praxis also
     * eindeutig. Wer es exakt braucht, nimmt {@link #gesperrt} mit der Rig-Liste.
     */
    public static boolean gesperrtIrgendwo(String bone)
    {
        if (bone == null || bone.isEmpty() || proModell == null)
        {
            return false;
        }

        for (List<Eintrag> rig : proModell.values())
        {
            if (rig != null && gesperrt(rig, bone))
            {
                return true;
            }
        }

        return false;
    }

    public static boolean dragRotiert(List<Eintrag> rig, String pickBone)
    {
        for (Eintrag e : rig == null ? Collections.<Eintrag>emptyList() : rig)
        {
            if (e.pick.equals(pickBone))
            {
                return e.dragRot;
            }
        }

        return false;
    }

    /**
     * Zeigt BBS dieses Control gerade? Ein Control liegt in einer oder mehreren
     * Rig-Layers (Blenders Bone-Collections); sichtbar ist es, sobald EINE davon an ist —
     * genau wie in Blender. Ohne Layer-Angabe immer sichtbar.
     */
    public static boolean sichtbar(Eintrag e)
    {
        if (e.lagen == null || e.lagen.isEmpty())
        {
            return true;
        }

        for (String lage : e.lagen)
        {
            if (lagen().getOrDefault(lage, true))
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Ein Layer so, wie Rigify ihn im Panel anordnet.
     *
     * @param zeile Zeile im Rig-Layers-Panel; 0 heisst "kommt dort nicht vor" (ORG/MCH/DEF).
     *              Luecken zwischen zwei Zeilennummern sind Absicht und werden zu Abstand.
     * @param titel Beschriftung aus dem Rig ("(Primary)"), nicht der Collection-Name.
     */
    public record Lage(String name, int zeile, String titel) {}

    /** Die Layer mit Rigifys Zeilen-Layout, in Blenders Reihenfolge. */
    public static List<Lage> lagenReihen()
    {
        if (lagenReihen == null)
        {
            lagenReihen = new ArrayList<>();
        }

        return lagenReihen;
    }

    /** Die Layer in Blenders Reihenfolge, mit ihrem Zustand — fuer die Knopfleiste. */
    public static java.util.Map<String, Boolean> lagen()
    {
        if (lagen == null)
        {
            lagen = new java.util.LinkedHashMap<>();
        }

        return lagen;
    }

    public static void schalteLage(String name, boolean an)
    {
        lagen().put(name, an);
    }

    /** ListType -> List<String>; null wird zur leeren Liste. */
    private static List<String> strings(ListType liste)
    {
        List<String> raus = new ArrayList<>();

        for (int i = 0; liste != null && i < liste.size(); i++)
        {
            raus.add(liste.getString(i));
        }

        return raus;
    }

    private static List<Eintrag> parse(String json)
    {
        List<Eintrag> neu = new ArrayList<>();

        {
            MapType wurzel = (MapType) DataToString.fromString(json);
            ListType ctrls = wurzel.getList("ctrls");
            ListType layer = wurzel.getList("lagen");

            lagenReihen().clear();

            /* Blenders Reihenfolge UND sein Sichtbar-Zustand als Vorgabe. Ein Rig, das
             * schon in Blender Layer ausgeblendet hatte, kommt hier genauso an. */
            for (int i = 0; layer != null && i < layer.size(); i++)
            {
                MapType l = layer.getMap(i);

                String name = l.getString("name");

                lagen().putIfAbsent(name, l.getBool("an", true));

                /* ⚠️ `zeile` fehlt in aelteren Extrakten — dann bleibt sie 0 und die
                 * Knopfleiste faellt auf ihre eigene Paarbildung zurueck. */
                lagenReihen().add(new Lage(name, l.getInt("zeile", 0),
                    l.has("titel") ? l.getString("titel") : name));
            }

            for (int i = 0; i < ctrls.size(); i++)
            {
                MapType c = (MapType) ctrls.get(i);
                MapType farbe = c.getMap("farbe");
                ListType linien = c.getList("linien");
                float[][] zuege = new float[linien.size()][];

                for (int l = 0; l < linien.size(); l++)
                {
                    ListType zug = (ListType) linien.get(l);
                    zuege[l] = new float[zug.size()];

                    for (int v = 0; v < zug.size(); v++)
                    {
                        zuege[l][v] = zug.getFloat(v);
                    }
                }

                neu.add(new Eintrag(c.getString("name"), c.getString("anker"), c.getString("pick"),
                    Integer.parseInt(farbe.getString("normal"), 16),
                    Integer.parseInt(farbe.getString("aktiv"), 16),
                    c.getFloat("breite", 1F), c.getString("schalter"),
                    c.getBool("ik", false), c.getBool("dragRot", false),
                    c.getBool("fest", false), strings(c.getList("lagen")), zuege));
            }
        }

        return Collections.unmodifiableList(neu);
    }
}
