package mchorse.bbs_mod.film.replays;

import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.FormUtils;

/**
 * Reads the track list BBS FS writes since 2.6 ({@code "tracks": [{kind, form, subject, prop,
 * channel}]}) into the map of track key to channel that {@link FormProperties} keeps.
 *
 * <p>Without it a film saved by BBS FS 2.6 or newer opened here with no keyframes at all: the
 * loader only knew the map, read {@code "tracks"} as a channel named "tracks" and dropped it.
 * The key rules are BBS FS's {@code TrackId.toKey()} (Wemppy4/bbs-fs, MIT), which its own
 * documentation calls the exact inverse of the older key strings this mod still uses.</p>
 */
public final class FsSpurListe
{
    public static final String TRACKS = "tracks";

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("bbs/fs-spuren");

    private FsSpurListe()
    {}

    public static boolean ist(MapType data)
    {
        return data.has(TRACKS) && data.get(TRACKS).isList();
    }

    /** The same tracks in the map form; tracks of a kind this build has no key for are reported and left out. */
    public static MapType alsKarte(ListType liste)
    {
        MapType karte = new MapType();

        for (int i = 0; i < liste.size(); i++)
        {
            if (!liste.get(i).isMap())
            {
                continue;
            }

            MapType spur = liste.get(i).asMap();
            String schluessel = schluessel(spur.getString("kind"), spur.getString("form"),
                spur.getString("subject"), spur.getString("prop"));

            if (schluessel == null)
            {
                LOGGER.warn("[fs-spuren] track of unknown kind '{}' ({} / {}) left out", spur.getString("kind"),
                    spur.getString("form"), spur.getString("subject"));

                continue;
            }

            if (spur.has("channel") && spur.get("channel").isMap())
            {
                karte.put(schluessel, spur.getMap("channel"));
            }
        }

        return karte;
    }

    /**
     * BBS FS 2.6+ addresses body parts by stable ids (eight hex digits with a letter, written as
     * "id" next to each part) instead of their index, also inside track paths. This mod indexes
     * them, so a replay's raw data gets its track paths rewritten from id paths to index paths
     * before it is read. Works on the raw data in place; data without such ids is left alone.
     */
    public static void koerperteilPfade(MapType replay)
    {
        java.util.Map<String, String> pfade = new java.util.HashMap<>();

        if (replay.has("form") && replay.get("form").isMap())
        {
            sammeln(replay.getMap("form"), "", "", pfade);
        }

        if (pfade.isEmpty() || !replay.has("properties"))
        {
            return;
        }

        if (replay.get("properties").isMap() && ist(replay.getMap("properties")))
        {
            ListType liste = replay.getMap("properties").getList(TRACKS);

            for (int i = 0; i < liste.size(); i++)
            {
                if (liste.get(i).isMap() && liste.get(i).asMap().has("form"))
                {
                    MapType spur = liste.get(i).asMap();

                    spur.putString("form", pfade.getOrDefault(spur.getString("form"), spur.getString("form")));
                }
            }
        }
        else if (replay.get("properties").isMap())
        {
            MapType alt = replay.getMap("properties");
            MapType neu = new MapType();

            for (String key : alt.keys())
            {
                neu.put(umschreiben(key, pfade), alt.get(key));
            }

            replay.put("properties", neu);
        }
    }

    /** Body part id path to index path for a form tree, e.g. "a1b2c3d4/e5f60718" to "0/2". */
    public static java.util.Map<String, String> koerperteilKarte(MapType form)
    {
        java.util.Map<String, String> pfade = new java.util.HashMap<>();

        if (form != null)
        {
            sammeln(form, "", "", pfade);
        }

        return pfade;
    }

    /** {@link #umschreiben} for attachment paths of anchors and camera clips. */
    public static String pfad(String pfad, java.util.Map<String, String> karte)
    {
        return pfad == null || pfad.isEmpty() ? pfad : umschreiben(pfad, karte);
    }

    private static void sammeln(MapType form, String idPfad, String indexPfad, java.util.Map<String, String> pfade)
    {
        ListType teile = form.has("bodyParts") && form.get("bodyParts").isMap() && form.getMap("bodyParts").has("parts")
            ? form.getMap("bodyParts").getList("parts") : form.has("parts") && form.get("parts").isList() ? form.getList("parts") : null;

        for (int i = 0; teile != null && i < teile.size(); i++)
        {
            if (!teile.get(i).isMap())
            {
                continue;
            }

            MapType teil = teile.get(i).asMap();
            String id = teil.getString("id");
            String index = indexPfad.isEmpty() ? String.valueOf(i) : indexPfad + FormUtils.PATH_SEPARATOR + i;
            String idHier = stabil(id) ? (idPfad.isEmpty() ? id : idPfad + FormUtils.PATH_SEPARATOR + id) : index;

            if (stabil(id))
            {
                pfade.put(idHier, index);
            }

            if (teil.has("form") && teil.get("form").isMap())
            {
                sammeln(teil.getMap("form"), idHier, index, pfade);
            }
        }
    }

    /** Longest matching id path prefix of a track key, replaced by its index path. */
    private static String umschreiben(String key, java.util.Map<String, String> pfade)
    {
        String treffer = null;

        for (String id : pfade.keySet())
        {
            if ((key.equals(id) || key.startsWith(id + FormUtils.PATH_SEPARATOR)) && (treffer == null || id.length() > treffer.length()))
            {
                treffer = id;
            }
        }

        return treffer == null ? key : pfade.get(treffer) + key.substring(treffer.length());
    }

    /** BBS FS StableIds.isStableId: eight hex digits with at least one letter, so never a number. */
    static boolean stabil(String id)
    {
        return id != null && id.matches("[0-9a-f]{8}") && id.matches(".*[a-f].*");
    }

    /** BBS FS {@code TrackId.toKey()}; null for a kind it does not know either. */
    static String schluessel(String art, String form, String subjekt, String eigenschaft)
    {
        String rest = switch (art)
        {
            case "property" -> subjekt;
            case "bone" -> "pose.bones." + subjekt;
            case "bone_constraint" -> "constraints.bones." + subjekt;
            case "material_texture" -> "texture.materials." + subjekt;
            case "material_prop" -> "materials." + (subjekt.isEmpty() ? "*" : subjekt) + "." + eigenschaft;
            case "ik_target" -> "ik_targets" + FormUtils.PATH_SEPARATOR + subjekt;
            case "pole_target" -> "pole_targets" + FormUtils.PATH_SEPARATOR + subjekt;
            case "physics_target" -> "physics_targets" + FormUtils.PATH_SEPARATOR + subjekt;
            case "ik_controls", "physics_controls", "wind_controls" -> art;
            default -> null;
        };

        if (rest == null)
        {
            return null;
        }

        return form.isEmpty() ? rest : form + FormUtils.PATH_SEPARATOR + rest;
    }
}
