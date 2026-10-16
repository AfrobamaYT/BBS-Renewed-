package mchorse.bbs_mod.video;

import com.mojang.logging.LogUtils;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.FFMpegUtils;
import org.slf4j.Logger;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A video clip's sound. BBS's audio reads WAV and OGG, not a video's audio track, so ffmpeg pulls
 * the track out once into assets/video_audio/, on a thread of its own, and the clip plays that
 * file through the usual audio path. Until it is there the clip is silent.
 */
public final class VideoTon
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, Boolean> LAEUFT = new ConcurrentHashMap<>();
    /** Videos without a readable track: asked once per session, not once per frame */
    private static final java.util.Set<String> OHNE = ConcurrentHashMap.newKeySet();

    private VideoTon()
    {}

    /** The extracted track of the video at {@code path}, or null while it is being made or the video has none */
    public static Link link(String path)
    {
        String name = path.replaceAll("[^A-Za-z0-9._-]", "_") + ".wav";
        File wav = new File(BBSMod.getAssetsFolder(), "video_audio/" + name);

        if (wav.isFile() && !LAEUFT.containsKey(path))
        {
            return Link.assets("video_audio/" + name);
        }

        if (!OHNE.contains(path) && LAEUFT.putIfAbsent(path, true) == null)
        {
            Thread thread = new Thread(() -> ausziehen(VideoTextures.resolve(path), wav, path), "BBS video audio");

            thread.setDaemon(true);
            thread.start();
        }

        return null;
    }

    private static void ausziehen(File video, File wav, String path)
    {
        try
        {
            wav.getParentFile().mkdirs();

            File teil = new File(wav.getPath() + ".part");
            Process process = new ProcessBuilder(FFMpegUtils.getFFMPEG(), "-hide_banner", "-loglevel", "error", "-y",
                "-i", video.getAbsolutePath(), "-vn", "-ac", "2", "-ar", "48000", "-c:a", "pcm_s16le", "-f", "wav", teil.getAbsolutePath())
                .redirectErrorStream(true).start();
            String ausgabe = new String(process.getInputStream().readAllBytes());

            if (process.waitFor() == 0 && teil.length() > 44 && teil.renameTo(wav))
            {
                LOGGER.info("[video] audio of {} -> {}", path, wav.getName());
            }
            else
            {
                teil.delete();
                OHNE.add(path);
                LOGGER.warn("[video] {} has no audio track ffmpeg could read: {}", path, ausgabe.trim());
            }
        }
        catch (Exception e)
        {
            OHNE.add(path);
            LOGGER.error("[video] cannot extract the audio of {}", path, e);
        }
        finally
        {
            LAEUFT.remove(path);
        }
    }
}
