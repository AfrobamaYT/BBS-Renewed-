package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.resources.Link;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Liest {@code rig.json} und baut daraus eine {@link RigMechanik}. Getrennt von ihr,
 * weil das EINLESEN einer Datei etwas anderes ist als das Rechnen damit.
 */
final class RigDatei
{
    /** Constraint-Typen, die {@link RigZwangBasis} rechnet; IK loest {@link RigIKKetten}. */
    private static final Set<String> TYPEN = Set.of("COPY_LOCATION", "COPY_ROTATION", "COPY_SCALE",
        "COPY_TRANSFORMS", "LIMIT_LOCATION", "LIMIT_ROTATION", "LIMIT_SCALE", "LIMIT_DISTANCE",
        "TRACK_TO", "LOCKED_TRACK", "DAMPED_TRACK", "STRETCH_TO", "CHILD_OF", "TRANSFORM",
        "MAINTAIN_VOLUME", "FLOOR", "PIVOT", "ACTION", "ARMATURE");

    /** modellId -> Mechanik; leerer Eintrag = Modell hat keine. Lazy (hotswap-sicher). */
    private static Map<String, RigMechanik> proModell;

    private RigDatei()
    {}

    static void leeren()
    {
        proModell = null;
    }

    static RigMechanik laden(String modellId)
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

        try (InputStream rein = BBSMod.getProvider().getAsset(Link.assets("models/" + modellId + "/rig.json")))
        {
            mechanik = parse((MapType) DataToString.fromString(new String(rein.readAllBytes(), StandardCharsets.UTF_8)));
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

        Map<String, List<MapType>> proBone = new HashMap<>();
        RigMechanik gebaut = new RigMechanik(reihenfolge, proBone);

        gebaut.rahmen = RigRahmen.lesen(wurzel.getList("G"), wurzel.getList("W"));
        gebaut.ruhelage = "REST".equals(wurzel.getString("pose_position", "POSE"));

        MapType flags = wurzel.getMap("knochen");

        for (String name : flags == null ? List.<String>of() : flags.keys())
        {
            gebaut.knochenFlags.put(name, flags.getMap(name));
        }

        MapType huellen = wurzel.getMap("huellen");

        for (String name : huellen == null ? List.<String>of() : huellen.keys())
        {
            gebaut.huellen.put(name, huellen.getMap(name));
        }

        listen(wurzel.getList("kanal_treiber"), k -> gebaut.kanalTreiber
            .computeIfAbsent(k.getString("bone"), x -> new ArrayList<>()).add(k));
        listen(wurzel.getList("prop_treiber"), gebaut.propTreiber::add);
        listen(wurzel.getList("bbone_treiber"), gebaut.bboneTreiber::add);

        MapType kanaele = wurzel.getMap("ruhekanal");

        for (String name : kanaele == null ? List.<String>of() : kanaele.keys())
        {
            MapType k = kanaele.getMap(name);
            ListType ort = k.getList("ort");
            ListType quat = k.getList("quat");
            ListType skala = k.getList("skala");

            /* Blender legt das Quaternion als (w,x,y,z) ab, JOML als (x,y,z,w). */
            gebaut.ruhekanal.put(name, new Matrix4f()
                .translate(ort.getFloat(0), ort.getFloat(1), ort.getFloat(2))
                .rotate(new Quaternionf(quat.getFloat(1), quat.getFloat(2), quat.getFloat(3), quat.getFloat(0)))
                .scale(skala.getFloat(0), skala.getFloat(1), skala.getFloat(2)));
        }

        for (int i = 0; i < mechanik.size(); i++)
        {
            MapType c = mechanik.getMap(i);
            String typ = c.getString("typ", "");

            if ("IK".equals(typ))
            {
                continue;
            }

            if (!TYPEN.contains(typ))
            {
                gebaut.unbekannt.merge(typ, 1, Integer::sum);

                continue;
            }

            proBone.computeIfAbsent(c.getString("bone"), k -> new ArrayList<>()).add(c);
        }

        MapType shapes = wurzel.getMap("shape_treiber");

        for (String name : shapes == null ? List.<String>of() : shapes.keys())
        {
            gebaut.shapeTreiber.put(name, shapes.getMap(name));
        }

        gebaut.ketten = RigIKKetten.lesen(mechanik, wurzel.getMap("ik_gelenke"), wurzel.getList("ik_gruppen"));
        gebaut.treiber.lade(wurzel.getMap("regler"));
        gebaut.incremental = new RigIncrementalPose(wurzel);

        return gebaut;
    }

    private static void listen(ListType l, java.util.function.Consumer<MapType> ziel)
    {
        for (int i = 0; l != null && i < l.size(); i++)
        {
            ziel.accept(l.getMap(i));
        }
    }
}
