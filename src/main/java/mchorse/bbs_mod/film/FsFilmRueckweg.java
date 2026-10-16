package mchorse.bbs_mod.film;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.replays.FsSpurListe;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Films saved by BBS FS in format 2 or 3, brought down to the format 1 this mod reads. Without it
 * every such film was refused as "saved by a newer version".
 *
 * <p>The exact inverse of BBS FS's own ladder (Wemppy4/bbs-fs, MIT): {@code FilmLegacy.migrateAnchor}
 * (2 to 3, the root form's anchor and its track moved to the replay) and {@code FilmStableIds}
 * (1 to 2, replays and body parts addressed by stable id instead of list position). Track keys
 * are left to {@link FsSpurListe#koerperteilPfade}, which every replay passes on load anyway; this
 * rewrites what lives outside a replay's own tracks: anchors and camera clip selectors.</p>
 */
public final class FsFilmRueckweg
{
    /** The newest BBS FS format this knows the way back from. */
    public static final int FS_HOECHSTE = 3;

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("bbs/fs-rueckweg");

    private FsFilmRueckweg()
    {}

    /** Rewrites the raw film in place; false when {@code version} is beyond what this knows. */
    public static boolean herunter(MapType film, int version)
    {
        if (version > FS_HOECHSTE)
        {
            return false;
        }

        ListType replays = film.has("replays") && film.get("replays").isList() ? film.getList("replays") : new ListType();

        if (version >= 3)
        {
            for (BaseType r : replays)
            {
                if (r.isMap())
                {
                    ankerZurForm(r.asMap());
                }
            }
        }

        List<String> ids = new ArrayList<>();
        List<Map<String, String>> karten = new ArrayList<>();

        for (BaseType r : replays)
        {
            MapType replay = r.isMap() ? r.asMap() : new MapType();

            ids.add(replay.getString("id"));
            karten.add(FsSpurListe.koerperteilKarte(replay.has("form") && replay.get("form").isMap() ? replay.getMap("form") : null));
        }

        for (BaseType r : replays)
        {
            if (!r.isMap())
            {
                continue;
            }

            MapType replay = r.asMap();

            if (replay.has("properties") && replay.get("properties").isMap())
            {
                ankerSpuren(replay.getMap("properties"), ids, karten);
            }

            if (replay.has("form") && replay.get("form").isMap())
            {
                formAnker(replay.getMap("form"), ids, karten);
            }
        }

        for (BaseType c : film.has("camera") && film.get("camera").isList() ? film.getList("camera") : new ListType())
        {
            if (c.isMap() && c.asMap().has("selector") && BaseType.isString(c.asMap().get("selector")))
            {
                MapType clip = c.asMap();
                int index = ids.indexOf(clip.getString("selector"));

                clip.putInt("selector", index);

                if (index >= 0 && clip.has("group"))
                {
                    clip.putString("group", FsSpurListe.pfad(clip.getString("group"), karten.get(index)));
                }
            }
        }

        LOGGER.warn("[fs-rueckweg] film from BBS FS format {} read as format 1: {} replays", version, replays.size());

        return true;
    }

    /** Format 3 to 2: the replay's anchor and anchor track go back to its root form. */
    private static void ankerZurForm(MapType replay)
    {
        MapType form = replay.has("form") && replay.get("form").isMap() ? replay.getMap("form") : null;

        if (form != null && replay.has("anchor") && !form.has("anchor"))
        {
            form.put("anchor", replay.get("anchor").copy());
        }

        replay.remove("anchor");

        MapType keyframes = replay.has("keyframes") && replay.get("keyframes").isMap() ? replay.getMap("keyframes") : null;

        if (keyframes == null || !keyframes.has("anchor"))
        {
            return;
        }

        BaseType kanal = keyframes.get("anchor").copy();
        BaseType props = replay.get("properties");

        keyframes.remove("anchor");

        if (props != null && props.isMap() && FsSpurListe.ist(props.asMap()))
        {
            MapType spur = new MapType();

            spur.putString("kind", "property");
            spur.putString("subject", "anchor");
            spur.put("channel", kanal);
            props.asMap().getList(FsSpurListe.TRACKS).add(spur);
        }
        else
        {
            if (props == null || !props.isMap())
            {
                props = new MapType();
                replay.put("properties", props);
            }

            props.asMap().put("anchor", kanal);
        }
    }

    private static void formAnker(MapType form, List<String> ids, List<Map<String, String>> karten)
    {
        if (form.has("anchor") && form.get("anchor").isMap())
        {
            anker(form.getMap("anchor"), ids, karten);
        }

        for (BaseType state : form.has("states") && form.get("states").isList() ? form.getList("states") : new ListType())
        {
            if (state.isMap() && state.asMap().has("properties") && state.asMap().get("properties").isMap())
            {
                ankerSpuren(state.asMap().getMap("properties"), ids, karten);
            }
        }

        ListType teile = form.has("bodyParts") && form.get("bodyParts").isMap() && form.getMap("bodyParts").has("parts")
            ? form.getMap("bodyParts").getList("parts") : form.has("parts") && form.get("parts").isList() ? form.getList("parts") : new ListType();

        for (BaseType teil : teile)
        {
            if (teil.isMap() && teil.asMap().has("form") && teil.asMap().get("form").isMap())
            {
                formAnker(teil.asMap().getMap("form"), ids, karten);
            }
        }
    }

    /** Anchor keyframe channels in a track map or an FS track list. */
    private static void ankerSpuren(MapType props, List<String> ids, List<Map<String, String>> karten)
    {
        List<MapType> kanaele = new ArrayList<>();

        if (FsSpurListe.ist(props))
        {
            for (BaseType spur : props.getList(FsSpurListe.TRACKS))
            {
                if (spur.isMap() && spur.asMap().has("channel") && spur.asMap().get("channel").isMap())
                {
                    kanaele.add(spur.asMap().getMap("channel"));
                }
            }
        }
        else
        {
            for (String key : props.keys())
            {
                if (props.get(key).isMap())
                {
                    kanaele.add(props.getMap(key));
                }
            }
        }

        for (MapType kanal : kanaele)
        {
            if (!kanal.getString("type").equals("anchor"))
            {
                continue;
            }

            for (BaseType k : kanal.getList("keyframes"))
            {
                if (k.isMap() && k.asMap().has("value") && k.asMap().get("value").isMap())
                {
                    anker(k.asMap().getMap("value"), ids, karten);
                }
            }
        }
    }

    /** One anchor: target replay id to its index, attachment path against the target's form. */
    private static void anker(MapType anker, List<String> ids, List<Map<String, String>> karten)
    {
        if (!anker.has("actor") || !BaseType.isString(anker.get("actor")))
        {
            return;
        }

        String id = anker.getString("actor");
        int index = id.isEmpty() ? -1 : ids.indexOf(id);

        if (!id.isEmpty() && index < 0)
        {
            LOGGER.warn("[fs-rueckweg] anchor points at replay '{}' which is not in the film; unanchored", id);
        }

        anker.putInt("actor", index);

        if (index >= 0 && anker.has("attachment"))
        {
            anker.putString("attachment", FsSpurListe.pfad(anker.getString("attachment"), karten.get(index)));
        }
    }
}
