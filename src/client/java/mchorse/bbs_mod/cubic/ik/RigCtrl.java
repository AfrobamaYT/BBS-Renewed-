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
    /** Blender-Zustand der vier ik_fk_switch-Properties: 0 = IK (Addon-Default). */
    private static boolean fkModus;

    private static List<Eintrag> liste;
    /* Lazy (Hotswap: neue statics sind null). Leere Liste = Modell hat kein eigenes rig.json. */
    private static java.util.Map<String, List<Eintrag>> proModell;

    public record Eintrag(String name, String anker, String pick, int farbeN, int farbeA,
        float breite, String schalter, boolean ik, boolean dragRot, float[][] linien) {}

    /** Das Rig, wenn die Armature ein Mixamo-Skelett mit unseren IK-Bones ist, sonst null. */
    public static List<Eintrag> fuer(BOBJArmature armature, String modellId)
    {
        if (armature == null || armature.bones == null
            || !armature.bones.containsKey("mixamorig:Hips")
            || !armature.bones.containsKey("IK.hand.L"))
        {
            return null;
        }

        /* Modell-eigenes rig.json zuerst — die Shapes sind in RUHEPOSEN-Koordinaten
         * gebacken, ein fremdes Modell (andere Proportionen) truege sie falsch. */
        if (modellId != null && !modellId.isEmpty())
        {
            List<Eintrag> eigen = eigenes(modellId);

            if (!eigen.isEmpty())
            {
                return eigen;
            }
        }

        return alle();
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

    /** Hat das aktuelle Modell der Entity ein Control-Rig? (Gizmo/Umriss-Gate, je Frame billig.) */
    public static boolean aktivFuer(mchorse.bbs_mod.forms.entities.IEntity entity)
    {
        if (entity == null
            || !(entity.getForm() instanceof mchorse.bbs_mod.forms.forms.ModelForm form)
            || !(mchorse.bbs_mod.forms.FormUtilsClient.getRenderer(form) instanceof mchorse.bbs_mod.forms.renderers.ModelFormRenderer renderer))
        {
            return false;
        }

        mchorse.bbs_mod.cubic.ModelInstance instance = renderer.getModel();

        return instance != null
            && instance.model instanceof mchorse.bbs_mod.cubic.model.bobj.BOBJModel bobj
            && fuer(bobj.getArmature(), form.model.get()) != null;
    }

    /**
     * Blender-Kanon fuer LMB-Drag auf dem Control dieses PICK-Bones: translate (G) —
     * ausser das Control ist location-gelockt (die FK-Ringe), dann dreht der Drag (R).
     */
    public static boolean dragRotiert(String pickBone)
    {
        for (Eintrag e : alle())
        {
            if (e.pick.equals(pickBone))
            {
                return e.dragRot;
            }
        }

        return false;
    }

    public static boolean sichtbar(Eintrag e)
    {
        return e.schalter.isEmpty() || e.ik != fkModus;
    }

    /* Lazy — auch damit die Liste einen Hotswap dieser Klasse ueberlebt (neue
     * statics sind nach einem Swap null, der naechste Zugriff laedt neu). */
    private static List<Eintrag> alle()
    {
        if (liste != null)
        {
            return liste;
        }

        try (InputStream rein = RigCtrl.class.getResourceAsStream("/assets/bbs/rig/mixamo_ctrl.json"))
        {
            liste = parse(new String(rein.readAllBytes(), StandardCharsets.UTF_8));
        }
        catch (Exception e)
        {
            System.err.println("[RigCtrl] mixamo_ctrl.json unlesbar: " + e);
            liste = Collections.emptyList();
        }

        return liste;
    }

    private static List<Eintrag> parse(String json)
    {
        List<Eintrag> neu = new ArrayList<>();

        {
            MapType wurzel = (MapType) DataToString.fromString(json);
            ListType ctrls = wurzel.getList("ctrls");

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
                    c.getBool("ik", false), c.getBool("dragRot", false), zuege));
            }
        }

        return Collections.unmodifiableList(neu);
    }
}
