// Coded by Afrobama/Claude — BBS <-> DaVinci Resolve live link (task 79, docs/DAVINCI-LINK-PLAN.md).
package com.davincilink;

import com.google.gson.JsonObject;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.camera.clips.misc.AudioClip;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.clips.Clip;

import java.io.File;

/** One BBS clip as the link sends it to Resolve, and as Resolve's edits are checked against. */
public final class DavinciSnapshot
{
    private DavinciSnapshot()
    {}

    public static JsonObject encode(Clip clip)
    {
        JsonObject out = new JsonObject();
        boolean audio = clip instanceof AudioClip;

        out.addProperty("uuid", DavinciFilmSync.uuidOf(clip));
        out.addProperty("kind", audio ? "audio" : "camera");
        out.addProperty("layer", clip.layer.get());
        out.addProperty("tick", clip.tick.get());
        out.addProperty("duration", clip.duration.get());
        out.addProperty("title", name(clip));
        out.addProperty("enabled", clip.enabled.get());

        if (audio)
        {
            AudioClip sound = (AudioClip) clip;
            File file = sound.audio.get() == null ? null : BBSMod.getProvider().getFile(sound.audio.get());

            out.addProperty("offset", sound.offset.get());
            /* A clip whose file is gone (deleted, renamed, never copied) says so instead of
             * sending a path Resolve cannot import. */
            out.addProperty("audio_path", file == null || !file.isFile() ? "" : file.getAbsolutePath());
        }

        return out;
    }

    /** The name Resolve shows: the clip's title, or its type when it has none. */
    public static String name(Clip clip)
    {
        if (!clip.title.get().isEmpty())
        {
            return clip.title.get();
        }

        Link type = BBSMod.getFactoryCameraClips().getTypeSilent(clip);

        return type == null ? "clip" : type.path;
    }
}
