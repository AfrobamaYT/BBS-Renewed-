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

/**
 * Liest {@code rig.json} und baut daraus eine {@link RigMechanik}. Getrennt von ihr,
 * weil das EINLESEN einer Datei etwas anderes ist als das Rechnen damit — die
 * Mechanik war mit beidem 856 Zeilen.
 */
final class RigDatei
{
    /** modellId -> Mechanik; leerer Eintrag = Modell hat keine. Lazy (hotswap-sicher). */
    private static Map<String, RigMechanik> proModell;

    private RigDatei()
    {}

    /** Beim Models-Reload rufen. */
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

        Map<String, List<RigMechanik.Zwang>> proBone = new HashMap<>();
        RigMechanik gebaut = new RigMechanik(reihenfolge, proBone);
        MapType flags = wurzel.getMap("knochen");

        for (String name : flags == null ? List.<String>of() : flags.keys())
        {
            gebaut.knochenFlags.put(name, flags.getMap(name));

            if (flags.getMap(name).getBool("verbunden", false))
            {
                gebaut.verbunden.add(name);
            }
        }

        ListType kanalTreiber = wurzel.getList("kanal_treiber");

        for (int i = 0; kanalTreiber != null && i < kanalTreiber.size(); i++)
        {
            MapType k = kanalTreiber.getMap(i);

            gebaut.kanalTreiber.computeIfAbsent(k.getString("bone"), x -> new ArrayList<>()).add(k);
        }

        MapType kanaele = wurzel.getMap("ruhekanal");

        for (String name : kanaele == null ? List.<String>of() : kanaele.keys())
        {
            MapType k = kanaele.getMap(name);
            ListType ort = k.getList("ort");
            ListType quat = k.getList("quat");
            ListType skala = k.getList("skala");

            /* Dieselbe Reihenfolge wie BOBJBone.applyTransformations: Ort, Drehung,
             * Groesse. Blender legt das Quaternion als (w,x,y,z) ab, JOML als (x,y,z,w). */
            gebaut.ruhekanal.put(name, new Matrix4f()
                .translate(ort.getFloat(0), ort.getFloat(1), ort.getFloat(2))
                .rotate(new Quaternionf(quat.getFloat(1), quat.getFloat(2),
                    quat.getFloat(3), quat.getFloat(0)))
                .scale(skala.getFloat(0), skala.getFloat(1), skala.getFloat(2)));
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
            boolean lokal = "TRANSFORM".equals(typ) || "LIMIT_ROTATION".equals(typ)
                || "LIMIT_LOCATION".equals(typ) || kopie;
            /* CUSTOM-Raum: ein benannter Knochen IST der Raum. Rigify rechnet
             * Limit Distance dort, und dort muss es auch gerechnet werden — eine
             * Strecke in einem skalierten Raum ist eine andere Strecke. */
            boolean eigenraum = !c.getString("raum_bone", "").isEmpty();
            /* Limit Rotation klemmt nur sich selbst, Armature traegt seine Ziele
             * in einer Liste — beide haben kein einzelnes ziel-Feld. */
            boolean brauchtZiel = !"LIMIT_ROTATION".equals(typ) && !"LIMIT_LOCATION".equals(typ)
                && !"ARMATURE".equals(typ);
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

            proBone.computeIfAbsent(c.getString("bone"), k -> new ArrayList<RigMechanik.Zwang>())
                .add(new RigMechanik.Zwang(typ, ziel, einfluss, welt,
                    c.getBool("nutz_x", true), c.getBool("nutz_y", true), c.getBool("nutz_z", true),
                    c.getString("spur_achse", "TRACK_Y"), c.getString("sperr_achse", "LOCK_Y"), c,
                    new float[] {einfluss}));
        }

        MapType shapes = wurzel.getMap("shape_treiber");

        for (String name : shapes == null ? List.<String>of() : shapes.keys())
        {
            gebaut.shapeTreiber.put(name, shapes.getMap(name));
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
            || "LIMIT_LOCATION".equals(typ)
            || "DAMPED_TRACK".equals(typ) || "STRETCH_TO".equals(typ)
            || "ARMATURE".equals(typ) || "LIMIT_DISTANCE".equals(typ);
    }
}
