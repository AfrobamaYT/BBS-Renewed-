package mchorse.bbs_mod.ui.film.audio;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.audio.Wave;
import mchorse.bbs_mod.audio.wav.WaveWriter;
import mchorse.bbs_mod.camera.clips.misc.AudioClientClip;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.utils.clips.Clip;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

/** Each take has its own asset; retakes mute only this session's previous clip. */
public final class VoiceoverTake
{
    private VoiceoverTake() {}

    public static AudioClientClip save(UIFilmPanel panel, Film film, int tick, String name,
        AudioClientClip previous, Wave wave) throws IOException
    {
        if (panel.getData() != film)
        {
            throw new IOException("Film changed; voice-over was not added to another film");
        }
        if (wave == null || wave.data.length == 0)
        {
            throw new IOException("Take contains no audio samples");
        }

        String folder = film.getId() == null ? "untitled" : film.getId().replaceAll("[^a-zA-Z0-9_-]", "_");
        String asset = "voiceover/" + folder + "/" + UUID.randomUUID() + ".wav";
        Path path = BBSMod.getAudioFolder().toPath().resolve(asset);

        Files.createDirectories(path.getParent());
        try (OutputStream stream = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW))
        {
            WaveWriter.write(stream, wave);
        }

        AudioClientClip clip = new AudioClientClip();

        clip.audio.set(Link.assets("audio/" + asset));
        clip.title.set(name.isBlank() ? "Voice-over" : name);
        clip.tick.set(tick);
        clip.duration.set(Math.max(1, (int) Math.ceil(wave.getDuration() * 20D)));
        clip.layer.set(film.camera.getTopLayer() + 1);
        fence(panel);
        BaseValue.edit(film.camera, (clips) ->
        {
            if (previous != null)
            {
                for (Clip item : clips.getClips(Clip.class))
                {
                    if (item instanceof AudioClientClip audio && audio.audio.get().equals(previous.audio.get()))
                    {
                        audio.enabled.set(false);
                    }
                }
            }

            clips.addClip(clip);
        });
        fence(panel);
        panel.cameraEditor.clips.clearSelection();
        panel.cameraEditor.clips.pickClip(clip);
        panel.save();

        return clip;
    }

    private static void fence(UIFilmPanel panel)
    {
        if (panel.getUndoHandler() != null)
        {
            panel.getUndoHandler().submitUndo();
            panel.getUndoHandler().getUndoManager().markLastUndoNoMerging();
        }
    }
}
