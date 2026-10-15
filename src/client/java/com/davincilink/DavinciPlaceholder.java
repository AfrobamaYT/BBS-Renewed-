// Coded by Afrobama/Claude — BBS <-> DaVinci Resolve live link (task 79, docs/DAVINCI-LINK-PLAN.md).
package com.davincilink;

import com.mojang.logging.LogUtils;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.utils.FFMpegUtils;
import org.slf4j.Logger;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * The neutral video a camera clip is cut from in Resolve until BBS renders real footage.
 *
 * <p>It must run at the timeline's own rate: Resolve counts a clip's source range in the media's
 * frames, so a 60 fps placeholder in a 24 fps timeline would come out at the wrong length. A still
 * image does not work either -- Resolve gives every still 300 frames whatever length is asked
 * (measured 2026-10-11). BBS makes one per rate with the ffmpeg it already uses for video export,
 * once, in the background; 30 minutes is the longest camera clip it can carry.</p>
 */
public final class DavinciPlaceholder
{
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final int SECONDS = 30 * 60;

    private static final Map<String, String> ERRORS = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> RUNNING = new ConcurrentHashMap<>();

    private DavinciPlaceholder()
    {}

    /** The file for this rate if it exists; otherwise starts making it and returns null. */
    public static File get(String fps)
    {
        File file = file(fps);

        if (file.isFile())
        {
            return file;
        }
        if (!ERRORS.containsKey(fps) && RUNNING.putIfAbsent(fps, true) == null)
        {
            Thread thread = new Thread(() -> make(fps, file), "davincilink-placeholder");

            thread.setDaemon(true);
            thread.start();
        }

        return null;
    }

    /** Why the placeholder for this rate could not be made, or "" while none failed. */
    public static String error(String fps)
    {
        return ERRORS.getOrDefault(fps, "");
    }

    public static File file(String fps)
    {
        return new File(BBSMod.getSettingsFolder(), "davinci/placeholder-" + fps.replaceAll("[^0-9.]", "_") + ".mp4");
    }

    private static void make(String fps, File file)
    {
        File temporary = new File(file.getPath() + ".part.mp4");

        try
        {
            Files.createDirectories(file.getParentFile().toPath());

            Process process = new ProcessBuilder(FFMpegUtils.getFFMPEG(), "-v", "error", "-y",
                "-f", "lavfi", "-i", "color=c=0x2b3440:s=192x108:r=" + fps + ":d=" + SECONDS,
                "-c:v", "libx264", "-preset", "ultrafast", "-pix_fmt", "yuv420p", temporary.getPath())
                .redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes());

            if (!process.waitFor(10, TimeUnit.MINUTES) || process.exitValue() != 0 || !temporary.isFile())
            {
                throw new IllegalStateException("ffmpeg failed: " + output.trim());
            }

            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            LOGGER.info("[davincilink] placeholder for {} fps ready: {}", fps, file);
        }
        catch (Exception e)
        {
            ERRORS.put(fps, e.getMessage() == null ? e.toString() : e.getMessage());
            LOGGER.error("[davincilink] could not make the {} fps placeholder with '{}'", fps, FFMpegUtils.getFFMPEG(), e);
        }
        finally
        {
            RUNNING.remove(fps);
        }
    }
}
