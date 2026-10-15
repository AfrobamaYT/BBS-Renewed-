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

            /* Never overwrite: two different sounds called "Audio 1.wav" would otherwise swap
             * the first clip's sound behind its back. The same content reuses its copy, other
             * content gets the next free "-n" name. */
            for (int n = 1; ; n++)
            {
                File target = new File(folder, n == 1 ? name : stem + "-" + n + extension);

                if (!target.exists())
                {
                    Files.copy(source.toPath(), target.toPath());

                    return Link.assets(MEDIA_FOLDER + "/" + target.getName());
                }
                if (Files.mismatch(source.toPath(), target.toPath()) == -1L)
                {
                    return Link.assets(MEDIA_FOLDER + "/" + target.getName());
                }
            }
        }
        catch (IOException e)
        {
            throw new IllegalStateException("could not copy " + source + " into BBS assets", e);
        }
    }
}
