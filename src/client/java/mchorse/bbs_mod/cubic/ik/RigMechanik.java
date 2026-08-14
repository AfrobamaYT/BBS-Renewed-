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
        boolean nutzX, boolean nutzY, boolean nutzZ) {}

    /** modellId -> Mechanik; leerer Eintrag = Modell hat keine. Lazy (hotswap-sicher). */
    private static Map<String, RigMechanik> proModell;

    private final List<String> reihenfolge;
    private final Map<String, List<Zwang>> proBone;
    /** Typen, die dieses Rig braucht und Stufe 1 noch nicht kann — mit Anzahl. */
    public final Map<String, Integer> unbekannt = new HashMap<>();

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

            /* IK laeuft ueber den vorhandenen Solver (arm_ik) — hier waere es doppelt. */
            if ("IK".equals(typ))
            {
                continue;
            }

            boolean welt = "WORLD".equals(c.getString("raum_o", "WORLD"))
                && "WORLD".equals(c.getString("raum_z", "WORLD"));

            if (ziel == null || ziel.isEmpty() || !welt || !kann(typ))
            {
                gebaut.unbekannt.merge(typ + (welt ? "" : "/raum"), 1, Integer::sum);

                continue;
            }

            proBone.computeIfAbsent(c.getString("bone"), k -> new ArrayList<>())
                .add(new Zwang(typ, ziel, einfluss,
                    true, c.getBool("nutz_x", true), c.getBool("nutz_y", true), c.getBool("nutz_z", true)));
        }

        return gebaut;
    }

    private static boolean kann(String typ)
    {
        return "COPY_TRANSFORMS".equals(typ) || "COPY_LOCATION".equals(typ)
            || "COPY_ROTATION".equals(typ) || "COPY_SCALE".equals(typ);
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

            if (liste == null)
            {
                continue;
            }

            for (Zwang z : liste)
            {
                BOBJBone ziel = armature.bones.get(z.ziel());

                if (ziel != null && z.einfluss() > 0F)
                {
                    anwenden(bone.mat, ziel.mat, z);
                }
            }

            armature.matrices[bone.index] = bone.hautMatrix();
        }

        return true;
    }

    /** Blenders Kopier-Constraints im Weltraum, auf die Weltmatrix des Besitzers. */
    private static void anwenden(Matrix4f eigen, Matrix4f ziel, Zwang z)
    {
        Vector3f t = eigen.getTranslation(new Vector3f());
        Quaternionf q = eigen.getUnnormalizedRotation(new Quaternionf());
        Vector3f s = eigen.getScale(new Vector3f());
        Vector3f zt = ziel.getTranslation(new Vector3f());
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

    private static void achsen(Vector3f eigen, Vector3f ziel, float w, Zwang z)
    {
        if (z.nutzX()) eigen.x += (ziel.x - eigen.x) * w;
        if (z.nutzY()) eigen.y += (ziel.y - eigen.y) * w;
        if (z.nutzZ()) eigen.z += (ziel.z - eigen.z) * w;
    }
}
