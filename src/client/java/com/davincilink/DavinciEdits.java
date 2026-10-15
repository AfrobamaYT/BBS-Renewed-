// Coded by Afrobama/Claude — BBS <-> DaVinci Resolve live link (task 79, docs/DAVINCI-LINK-PLAN.md).
package com.davincilink;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import mchorse.bbs_mod.camera.clips.misc.AudioClip;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.utils.clips.Clip;
import org.slf4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Edits made in Resolve, applied to the open film (the Resolve -&gt; BBS half of the link).
 *
 * <p>{@code CLIPS_CHANGED} carries each edit with the state it started from. An edit is applied
 * only if the clip in BBS is still in that state; otherwise BBS wins, the conflict is reported back
 * and the next {@code FILM} restores Resolve. Every applied batch is one undo step, backed up and
 * saved, exactly like the Blender bridge's marker write.</p>
 */
public final class DavinciEdits
{
    private static final Logger LOGGER = LogUtils.getLogger();

    private DavinciEdits()
    {}

    public static JsonObject apply(JsonObject fields)
    {
        JsonObject result = new JsonObject();
        JsonArray conflicts = new JsonArray();
        long incoming = fields.has("seq") ? fields.get("seq").getAsLong() : 0;
        UIFilmPanel panel = DavinciFilmSync.editorPanel();
        Film film = panel == null ? null : panel.getData();
        int applied = 0;

        result.addProperty("seq", incoming);

        if (film == null)
        {
            return reject(result, "no film editor open");
        }
        if (!film.getId().equals(fields.get("film_id").getAsString()))
        {
            return reject(result, "film mismatch: edit was for '" + fields.get("film_id").getAsString()
                + "', open film is '" + film.getId() + "'");
        }

        List<Runnable> writes = new ArrayList<>();

        for (JsonElement element : fields.getAsJsonArray("changed"))
        {
            JsonObject change = element.getAsJsonObject();
            Clip clip = find(film, change.get("uuid").getAsString());

            if (clip == null || !DavinciSnapshot.encode(clip).equals(withUuid(change.getAsJsonObject("base"), clip)))
            {
                conflicts.add(change.get("uuid"));
                continue;
            }

            JsonObject now = change.getAsJsonObject("now");
            writes.add(() -> place(clip, now));
        }

        for (JsonElement element : fields.getAsJsonArray("removed"))
        {
            JsonObject removal = element.getAsJsonObject();
            Clip clip = find(film, removal.get("uuid").getAsString());

            if (clip == null)
            {
                continue;
            }
            if (!DavinciSnapshot.encode(clip).equals(withUuid(removal.getAsJsonObject("base"), clip)))
            {
                conflicts.add(removal.get("uuid"));
                continue;
            }

            writes.add(() -> film.camera.remove(clip));
        }

        JsonArray refused = new JsonArray();

        for (JsonElement element : fields.getAsJsonArray("added"))
        {
            JsonObject sound = element.getAsJsonObject();
            Link link;

            /* One sound BBS cannot take (unreadable, ffmpeg missing) is refused on its own: the
             * rest still lands, and the answer still goes out -- without it Resolve would wait
             * for an acknowledgement forever and never take another film. */
            try
            {
                link = DavinciAdopt.adopt(new File(sound.get("path").getAsString()));
            }
            catch (RuntimeException e)
            {
                JsonObject no = new JsonObject();

                no.add("uuid", sound.get("uuid"));
                no.addProperty("error", e.getMessage());
                refused.add(no);
                LOGGER.warn("[davincilink] sound from Resolve refused: {}", e.getMessage());
                continue;
            }

            writes.add(() ->
            {
                Clip clip = film.camera.getFactory().create(Link.bbs("audio"));

                ((AudioClip) clip).audio.set(link);
                place(clip, sound);
                film.camera.addClip(clip);
                DavinciFilmSync.bind(clip, sound.get("uuid").getAsString());
            });
        }

        if (!writes.isEmpty())
        {
            if (panel.getUndoHandler() != null)
            {
                panel.getUndoHandler().getUndoManager().markLastUndoNoMerging();
            }

            writes.forEach(Runnable::run);
            applied = writes.size();

            if (panel.getUndoHandler() != null)
            {
                panel.getUndoHandler().submitUndo();
                panel.getUndoHandler().getUndoManager().markLastUndoNoMerging();
            }

            panel.save();
            /* Edits from Resolve change the film on disk; without this line a film cut short by Resolve
             * left no trace of who did it (2026-10-12). */
            LOGGER.info("[davincilink] applied from Resolve: {} changed, {} removed, {} added -> {} clips",
                fields.getAsJsonArray("changed").size(), fields.getAsJsonArray("removed").size(),
                fields.getAsJsonArray("added").size(), film.camera.get().size());
        }

        DavinciFilmSync.acknowledge(incoming);
        result.addProperty("ok", true);
        result.addProperty("applied", applied);
        result.add("conflicts", conflicts);
        result.add("refused", refused);

        if (conflicts.size() > 0)
        {
            LOGGER.warn("[davincilink] {} edit(s) from Resolve lost to newer BBS changes: {}", conflicts.size(), conflicts);
        }

        return result;
    }

    private static void place(Clip clip, JsonObject state)
    {
        clip.layer.set(state.get("layer").getAsInt());
        clip.tick.set(state.get("tick").getAsInt());
        clip.duration.set(state.get("duration").getAsInt());
        clip.enabled.set(state.get("enabled").getAsBoolean());

        String title = state.get("title").getAsString();

        if (!title.equals(DavinciSnapshot.name(clip)))
        {
            clip.title.set(title);
        }
        if (clip instanceof AudioClip sound && state.has("offset"))
        {
            sound.offset.set(state.get("offset").getAsInt());
        }
    }

    /** The base as Resolve saw it, completed with what Resolve does not carry for a clip. */
    private static JsonObject withUuid(JsonObject base, Clip clip)
    {
        JsonObject full = base.deepCopy();
        JsonObject current = DavinciSnapshot.encode(clip);

        full.addProperty("uuid", DavinciFilmSync.idOf(clip));
        if (current.has("audio_path"))
        {
            full.add("audio_path", current.get("audio_path"));
        }

        return full;
    }

    private static Clip find(Film film, String uuid)
    {
        for (Clip clip : film.camera.get())
        {
            if (uuid.equals(DavinciFilmSync.idOf(clip)))
            {
                return clip;
            }
        }

        return null;
    }

    private static JsonObject reject(JsonObject result, String reason)
    {
        LOGGER.warn("[davincilink] edits from Resolve rejected: {}", reason);
        result.addProperty("ok", false);
        result.addProperty("error", reason);
        result.addProperty("applied", 0);

        return result;
    }
}
