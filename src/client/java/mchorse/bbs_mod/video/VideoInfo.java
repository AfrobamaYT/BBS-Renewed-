package mchorse.bbs_mod.video;

import mchorse.bbs_mod.utils.FFMpegUtils;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Groesse, Bildrate und Dauer einer Videodatei, gelesen aus der Kopfzeile von {@code ffmpeg -i}. */
public final class VideoInfo
{
    private static final Pattern STREAM = Pattern.compile("Stream #.*Video: .*?, (\\d{2,5})x(\\d{2,5})[ ,].*?([\\d.]+) (?:fps|tbr)");
    private static final Pattern DURATION = Pattern.compile("Duration: (\\d+):(\\d+):([\\d.]+)");

    public final int width;
    public final int height;
    public final double fps;
    public final double seconds;

    private VideoInfo(int width, int height, double fps, double seconds)
    {
        this.width = width;
        this.height = height;
        this.fps = fps;
        this.seconds = seconds;
    }

    /** Liest die Kopfdaten; wirft mit lesbarer Meldung, statt still ein schwarzes Bild zu liefern. */
    public static VideoInfo probe(File file) throws Exception
    {
        String ffmpeg = FFMpegUtils.getFFMPEG();

        if (ffmpeg == null || ffmpeg.isEmpty())
        {
            throw new IllegalStateException("ffmpeg was not found -- video forms need it");
        }

        if (!file.isFile())
        {
            throw new IllegalArgumentException("Video file not found: " + file);
        }

        Process process = new ProcessBuilder(ffmpeg, "-hide_banner", "-i", file.getAbsolutePath()).redirectErrorStream(true).start();
        String text;

        try (InputStream in = process.getInputStream())
        {
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        process.waitFor();

        Matcher stream = STREAM.matcher(text);

        if (!stream.find())
        {
            throw new IllegalArgumentException("No video stream in " + file.getName());
        }

        Matcher duration = DURATION.matcher(text);
        double seconds = duration.find()
            ? Integer.parseInt(duration.group(1)) * 3600 + Integer.parseInt(duration.group(2)) * 60 + Double.parseDouble(duration.group(3))
            : 0D;

        return new VideoInfo(Integer.parseInt(stream.group(1)), Integer.parseInt(stream.group(2)), Double.parseDouble(stream.group(3)), seconds);
    }
}
