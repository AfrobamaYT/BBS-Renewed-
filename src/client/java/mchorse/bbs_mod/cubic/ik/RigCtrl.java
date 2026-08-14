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

    public record Eintrag(String name, String anker, String pick, int farbeN, int farbeA,
        float breite, String schalter, boolean ik, float[][] linien) {}

    /** Das Rig, wenn die Armature ein Mixamo-Skelett mit unseren IK-Bones ist, sonst null. */
    public static List<Eintrag> fuer(BOBJArmature armature)
    {
        if (armature == null || armature.bones == null
            || !armature.bones.containsKey("mixamorig:Hips")
            || !armature.bones.containsKey("IK.hand.L"))
        {
            return null;
        }

        return alle();
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

        List<Eintrag> neu = new ArrayList<>();

        try (InputStream rein = RigCtrl.class.getResourceAsStream("/assets/bbs/rig/mixamo_ctrl.json"))
        {
            MapType wurzel = (MapType) DataToString.fromString(new String(rein.readAllBytes(), StandardCharsets.UTF_8));
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
                    c.getBool("ik", false), zuege));
            }
        }
        catch (Exception e)
        {
            /* Laut scheitern statt still leeres Rig — sonst raetselt man am Bild. */
            System.err.println("[RigCtrl] mixamo_ctrl.json unlesbar: " + e);
            e.printStackTrace();
        }

        liste = Collections.unmodifiableList(neu);

        return liste;
    }
}
