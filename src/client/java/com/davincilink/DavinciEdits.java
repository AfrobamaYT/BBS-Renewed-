// Coded by Afrobama/Claude — BBS <-> DaVinci Resolve live link (task 79, docs/DAVINCI-LINK-PLAN.md).
package com.davincilink;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import mchorse.bbs_mod.camera.clips.misc.AudioClip;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
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
        /* Videos are replays: when one comes or goes, the replay list and world entities follow. */
        boolean[] replays = {false};

        for (JsonElement element : fields.getAsJsonArray("changed"))
        {
            JsonObject change = element.getAsJsonObject();
            Object clip = find(film, change.get("uuid").getAsString());

            if (clip == null || !encode(clip).equals(withUuid(change.getAsJsonObject("base"), clip)))
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
            Object clip = find(film, removal.get("uuid").getAsString());

            if (clip == null)
            {
                continue;
            }
            if (!encode(clip).equals(withUuid(removal.getAsJsonObject("base"), clip)))
            {
                conflicts.add(removal.get("uuid"));
                continue;
            }

            if (clip instanceof Replay replay)
            {
                replays[0] = true;
                writes.add(() -> film.replays.remove(replay));
            }
            else
            {
                writes.add(() -> film.camera.remove((Clip) clip));
            }
        }

        JsonArray refused = new JsonArray();

        for (JsonElement element : fields.getAsJsonArray("added"))
        {
            JsonObject sound = element.getAsJsonObject();
            Link link = null;
            String video = null;

            /* One sound BBS cannot take (unreadable, ffmpeg missing) is refused on its own: the
             * rest still lands, and the answer still goes out -- without it Resolve would wait
             * for an acknowledgement forever and never take another film. */
            try
            {
                File source = new File(sound.get("path").getAsString());

                if ("video".equals(sound.get("kind").getAsString()))
                {
                    video = DavinciAdopt.adoptVideo(source);
                }
                else
                {
                    link = DavinciAdopt.adopt(source);
                }
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

            if (video != null)
            {
                String path = video;

                replays[0] = true;
                writes.add(() -> DavinciFilmSync.bind(DavinciVideos.add(panel, film, path, sound), sound.get("uuid").getAsString()));
                continue;
            }

            Link audio = link;

            writes.add(() ->
            {
                Clip clip = film.camera.getFactory().create(Link.bbs("audio"));

                ((AudioClip) clip).audio.set(audio);
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

            if (replays[0])
            {
                DavinciVideos.refresh(panel);
            }

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

    private static JsonObject encode(Object clip)
    {
        return clip instanceof Replay replay ? DavinciVideos.encode(replay) : DavinciSnapshot.encode((Clip) clip);
    }

    private static void place(Object target, JsonObject state)
    {
        if (target instanceof Replay replay)
        {
            DavinciVideos.place(replay, state);

            return;
        }

        Clip clip = (Clip) target;

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
    private static JsonObject withUuid(JsonObject base, Object clip)
    {
        JsonObject full = base.deepCopy();
        JsonObject current = encode(clip);

        full.addProperty("uuid", DavinciFilmSync.idOf(clip));
        for (String path : new String[] {"audio_path", "video_path"})
        {
            if (current.has(path))
            {
                full.add(path, current.get(path));
            }
        }

        return full;
    }

    private static Object find(Film film, String uuid)
    {
        for (Clip clip : film.camera.get())
        {
            if (uuid.equals(DavinciFilmSync.idOf(clip)))
            {
                return clip;
            }
        }

        return DavinciVideos.find(film, uuid);
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
