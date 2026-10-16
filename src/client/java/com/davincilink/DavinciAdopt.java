// Coded by Afrobama/Claude — BBS <-> DaVinci Resolve live link (task 79, docs/DAVINCI-LINK-PLAN.md).
package com.davincilink;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.FFMpegUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/** Sounds from Resolve, taken into BBS' own assets so the film keeps them without DaVinci. */
public final class DavinciAdopt
{
    /** Sounds adopted from Resolve are copied here, so they stay in BBS without DaVinci. */
    public static final String MEDIA_FOLDER = "audio/davinci";

    private DavinciAdopt()
    {}

    /** A video from Resolve, copied as it is (the video form decodes any format ffmpeg reads);
     * returns the path relative to the assets folder, as the video form keeps it. */
    public static String adoptVideo(File source)
    {
        File folder = new File(BBSMod.getAssetsFolder(), DavinciVideos.MEDIA_FOLDER);
        String name = source.getName();
        int dot = name.lastIndexOf('.');

        try
        {
            Files.createDirectories(folder.toPath());

            return DavinciVideos.MEDIA_FOLDER + "/" + copyInto(folder, source, dot > 0 ? name.substring(0, dot) : name, dot > 0 ? name.substring(dot) : "");
        }
        catch (IOException e)
        {
            throw new IllegalStateException("could not copy " + source + " into BBS assets", e);
        }
    }

    /* Never overwrite: two different files called "Audio 1.wav" would otherwise swap the first
     * clip's media behind its back. The same content reuses its copy, other content gets the
     * next free "-n" name. */
    private static String copyInto(File folder, File source, String stem, String extension) throws IOException
    {
        for (int n = 1; ; n++)
        {
            File target = new File(folder, n == 1 ? stem + extension : stem + "-" + n + extension);

            if (!target.exists())
            {
                Files.copy(source.toPath(), target.toPath());

                return target.getName();
            }
            if (Files.mismatch(source.toPath(), target.toPath()) == -1L)
            {
                return target.getName();
            }
        }
    }

    /** Copies a file from Resolve into BBS' assets once; the link stays valid without DaVinci. */
    public static Link adopt(File source)
    {
        File folder = new File(BBSMod.getAssetsFolder(), MEDIA_FOLDER);
        String name = source.getName();
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        String extension = dot > 0 ? name.substring(dot) : "";

        try
        {
            Files.createDirectories(folder.toPath());

            /* BBS plays only .wav and .ogg; DaVinci's pool is full of .mp3, .m4a, .flac. Convert
             * the way BBS' own audio import does (ToWAVImporter: ffmpeg, mono), then treat the
             * result like any other file. */
            if (!extension.equalsIgnoreCase(".wav") && !extension.equalsIgnoreCase(".ogg"))
            {
                File converted = new File(BBSMod.getSettingsFolder(), "davinci/convert/" + stem + ".wav");

                Files.createDirectories(converted.getParentFile().toPath());
                if (!FFMpegUtils.execute(converted.getParentFile(), "-y", "-v", "error", "-i",
                    source.getAbsolutePath(), "-ac", "1", converted.getAbsolutePath()) || !converted.isFile())
                {
                    throw new IllegalStateException("ffmpeg could not turn " + source + " into a .wav (see converter.log)");
                }

                source = converted;
                extension = ".wav";
                name = stem + extension;
            }

            return Link.assets(MEDIA_FOLDER + "/" + copyInto(folder, source, stem, extension));
        }
        catch (IOException e)
        {
            throw new IllegalStateException("could not copy " + source + " into BBS assets", e);
        }
    }
}
