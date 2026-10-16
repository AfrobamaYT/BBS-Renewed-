// Coded by Afrobama/Claude — BBS <-> DaVinci Resolve live link (task 79, docs/DAVINCI-LINK-PLAN.md).
package com.davincilink;

import com.google.gson.JsonObject;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.forms.VideoForm;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.video.VideoInfo;
import mchorse.bbs_mod.video.VideoTextures;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Videos on Resolve's video tracks, as replays with a {@link VideoForm} in BBS.
 *
 * <p>A video has a place in the world (the replay, moved with G/R/S like any form) and a place on
 * the timeline (the form's start, length, offset and track). Only the timeline part travels: it is
 * sent and edited exactly like a sound clip, under kind "video".</p>
 */
public final class DavinciVideos
{
    /** Videos adopted from Resolve are copied here, so they stay in BBS without DaVinci. */
    public static final String MEDIA_FOLDER = "videos/davinci";

    /** How far in front of the editor camera a video from Resolve appears. */
    private static final double DISTANCE = 4D;
    private static final float HEIGHT = 2F;

    private DavinciVideos()
    {}

    /** Video replays that are on the timeline: a length (a clip, not a looping prop) and a file. */
    public static List<Replay> linked(Film film)
    {
        List<Replay> out = new ArrayList<>();

        for (Replay replay : film.replays.getList())
        {
            if (replay.form.get() instanceof VideoForm video && video.length.get() > 0 && file(video).isFile())
            {
                out.add(replay);
            }
        }

        return out;
    }

    public static JsonObject encode(Replay replay)
    {
        VideoForm video = (VideoForm) replay.form.get();
        JsonObject out = new JsonObject();

        out.addProperty("uuid", DavinciFilmSync.uuidOf(replay));
        out.addProperty("kind", "video");
        out.addProperty("layer", video.track.get());
        out.addProperty("tick", video.start.get());
        out.addProperty("duration", video.length.get());
        out.addProperty("title", name(replay));
        out.addProperty("enabled", replay.enabled.get());
        out.addProperty("offset", video.offset.get());
        out.addProperty("video_path", file(video).getAbsolutePath());

        return out;
    }

    /** Puts a video where Resolve has it; the place in the world stays. */
    public static void place(Replay replay, JsonObject state)
    {
        VideoForm video = (VideoForm) replay.form.get();

        video.track.set(state.get("layer").getAsInt());
        video.start.set(state.get("tick").getAsInt());
        video.length.set(state.get("duration").getAsInt());
        replay.enabled.set(state.get("enabled").getAsBoolean());

        if (state.has("offset"))
        {
            video.offset.set(state.get("offset").getAsInt());
        }

        String title = state.get("title").getAsString();

        if (!title.equals(name(replay)))
        {
            replay.label.set(title);
        }
    }

    /** A new replay for a video the user put on Resolve's timeline, in front of the editor camera. */
    public static Replay add(UIFilmPanel panel, Film film, String path, JsonObject state)
    {
        Replay replay = film.replays.addReplay();
        VideoForm video = new VideoForm();

        video.video.set(path);
        aspect(video);
        replay.form.set(video);
        place(replay, state);

        Vector3d position = new Vector3d(panel.getCamera().position);
        Vector3f look = panel.getCamera().getLookDirection();

        position.add(look.x * DISTANCE, look.y * DISTANCE, look.z * DISTANCE);
        replay.keyframes.x.insert(0, position.x);
        replay.keyframes.y.insert(0, position.y);
        replay.keyframes.z.insert(0, position.z);

        return replay;
    }

    /** Two blocks high, as wide as the video's picture; a square when ffmpeg cannot tell. */
    private static void aspect(VideoForm video)
    {
        try
        {
            VideoInfo info = VideoInfo.probe(file(video));

            video.transform.get().scale.set(HEIGHT * info.width / info.height, HEIGHT, 1F);
            video.transform.get().translate.set(0F, HEIGHT / 2F, 0F);
        }
        catch (Exception e)
        {
            /* The decoder reports an unreadable file in the log; the replay still appears. */
        }
    }

    /** After replays were added or removed: the list and the world entities follow. */
    public static void refresh(UIFilmPanel panel)
    {
        panel.replayEditor.replaysList.replays.refreshReplayList();
        panel.getController().createEntities();
    }

    public static Replay find(Film film, String uuid)
    {
        for (Replay replay : linked(film))
        {
            if (uuid.equals(DavinciFilmSync.idOf(replay)))
            {
                return replay;
            }
        }

        return null;
    }

    private static String name(Replay replay)
    {
        return replay.label.get().isEmpty() ? replay.form.get().getDisplayName() : replay.label.get();
    }

    private static File file(VideoForm video)
    {
        return VideoTextures.resolve(video.video.get());
    }
}
