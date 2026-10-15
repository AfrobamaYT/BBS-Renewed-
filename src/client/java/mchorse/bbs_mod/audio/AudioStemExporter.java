package mchorse.bbs_mod.audio;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.audio.wav.WaveWriter;
import mchorse.bbs_mod.camera.clips.misc.AudioClip;
import mchorse.bbs_mod.camera.utils.TimeUtils;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.StringUtils;
import org.lwjgl.system.MemoryUtil;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Writes every audio clip of a film into its OWN wav file ("stems"), already cut to that clip's
 * length, instead of the single mixdown that {@link AudioRenderer} produces.
 *
 * <p>WHY a separate class instead of a flag inside AudioRenderer: the mixdown owns one shared
 * target buffer and layers everything into it; a stem owns one buffer per clip and never mixes.
 * Those are two different loops, and folding them together would have meant a second meaning for
 * every local in renderAudio.</p>
 *
 * <p>INVARIANT: a stem starts at sample 0 of its own file. The clip's position on the film
 * timeline is NOT baked in as leading silence - it is carried in the file NAME (the {@code 120t}
 * part). Anyone importing the stems into a DAW must place them at that tick; that is the whole
 * point of the "one clip = one file" mode, otherwise every file would be film-length again.</p>
 */
public class AudioStemExporter
{
    /** Ticks per second - BBS clip ticks, same unit {@link TimeUtils#toSeconds} converts from. */
    private static final float TICKS_PER_SECOND = 20F;

    /** Keep the recognizable part of a name short enough that path length stays sane on Windows. */
    private static final int MAX_LABEL_LENGTH = 48;

    /**
     * Export one wav per enabled audio clip into {@code folder}.
     *
     * @param from start of the render window in SECONDS, same convention as
     *             {@link AudioRenderer#renderAudio}: {@code from == to} (or a negative value)
     *             means "no window, take everything". A clip that does not overlap the window
     *             is skipped entirely; a clip that overlaps partially is cut to the overlap.
     * @param to   end of the render window in seconds, see {@code from}.
     * @return how many files were actually written. 0 means nothing usable was found - the caller
     *         should report that as an error, because an empty folder looks like success otherwise.
     */
    public static int exportStems(File folder, List<AudioClip> clips, int sampleRate, float from, float to)
    {
        List<AudioClip> ordered = new ArrayList<>();

        for (AudioClip clip : clips)
        {
            /* Disabled clips are muted in the mixdown too - staying consistent avoids the
             * "why is there a file for a clip I turned off" surprise. */
            if (clip.enabled.get() && clip.audio.get() != null)
            {
                ordered.add(clip);
            }
        }

        if (ordered.isEmpty())
        {
            return 0;
        }

        /* Copy first, sort second: the caller's list is the film's clip list, sorting it in place
         * would reorder the timeline data. */
        ordered.sort(Comparator.comparingInt((AudioClip clip) -> clip.tick.get()).thenComparingInt((AudioClip clip) -> clip.layer.get()));

        if (!folder.exists() && !folder.mkdirs())
        {
            return 0;
        }

        boolean windowed = from != to && from >= 0F && to >= 0F;
        float windowStart = Math.min(from, to);
        float windowEnd = Math.max(from, to);

        ByteBuffer buffer = MemoryUtil.memAlloc(2);
        int written = 0;
        int index = 0;

        try
        {
            for (AudioClip clip : ordered)
            {
                index += 1;

                float clipStart = TimeUtils.toSeconds(clip.tick.get());
                float clipEnd = clipStart + TimeUtils.toSeconds(clip.duration.get());
                float start = windowed ? Math.max(clipStart, windowStart) : clipStart;
                float end = windowed ? Math.min(clipEnd, windowEnd) : clipEnd;

                if (end <= start)
                {
                    continue;
                }

                if (writeStem(buffer, folder, clip, index, sampleRate, clipStart, start, end - start))
                {
                    written += 1;
                }
            }
        }
        finally
        {
            MemoryUtil.memFree(buffer);
        }

        return written;
    }

    /**
     * @param clipStart where the clip sits on the timeline, in seconds - needed because a window
     *                  that cuts the clip's head off must advance the source offset by the same
     *                  amount, otherwise the stem would start with audio the user cut away.
     * @param start     where this stem starts on the timeline, in seconds.
     * @param duration  stem length in seconds.
     */
    private static boolean writeStem(ByteBuffer buffer, File folder, AudioClip clip, int index, int sampleRate, float clipStart, float start, float duration)
    {
        Wave source;

        try
        {
            source = AudioReader.read(BBSMod.getProvider(), clip.audio.get());
        }
        catch (Exception e)
        {
            e.printStackTrace();

            return false;
        }

        if (source == null)
        {
            return false;
        }

        int byteRate = sampleRate * 2;
        int totalBytes = (int) Math.ceil(duration * byteRate);

        if (totalBytes <= 0)
        {
            return false;
        }

        /* Byte alignment: Wave.add truncates every index to an even number, an odd-length
         * buffer would drop its last sample and confuse the length in the header. */
        byte[] bytes = new byte[totalBytes + (totalBytes % 2)];
        Wave stem = new Wave(1, 1, sampleRate, 16, bytes);
        float shift = TimeUtils.toSeconds(clip.offset.get()) + (start - clipStart);

        try
        {
            /* Target offset is 0: the stem IS the clip, it does not sit inside a film-long buffer. */
            stem.add(buffer, source, 0F, shift, duration, clip.volume.get());

            WaveWriter.write(new File(folder, buildFileName(clip, index, start)), stem);

            return true;
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Build a file name of the shape {@code 03_120t_narrator_intro.wav}.
     *
     * <p>Three parts, each earning its place:</p>
     * <ul>
     *   <li>the zero-padded running index makes the folder sort in TIMELINE order in any file
     *       browser - plain tick numbers would sort "100" before "20" as text;</li>
     *   <li>the tick is the number the user actually reads off the BBS timeline, so it is what
     *       they need to place the stem again in a DAW;</li>
     *   <li>the label (clip title, or the source file's base name when the title is empty) is
     *       what makes the file recognizable at a glance.</li>
     * </ul>
     *
     * The index also guarantees uniqueness, so two clips with the same title at the same tick
     * cannot overwrite each other.
     */
    private static String buildFileName(AudioClip clip, int index, float startSeconds)
    {
        int tick = Math.round(startSeconds * TICKS_PER_SECOND);
        String label = StringUtils.sanitizeFilename(clip.title.get()).trim();

        if (label.isEmpty())
        {
            Link link = clip.audio.get();
            String file = link == null ? "" : StringUtils.removeExtension(StringUtils.fileName(link.path));

            label = StringUtils.sanitizeFilename(file).trim();
        }

        if (label.isEmpty())
        {
            label = "audio";
        }

        if (label.length() > MAX_LABEL_LENGTH)
        {
            label = label.substring(0, MAX_LABEL_LENGTH).trim();
        }

        return StringUtils.leftPad(String.valueOf(index), 2, "0") + "_" + tick + "t_" + label + ".wav";
    }
}
