package com.afrobama.bbstools.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.audio.Wave;
import mchorse.bbs_mod.audio.wav.WaveWriter;
import mchorse.bbs_mod.camera.clips.misc.AudioClip;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/** A real saved audio clip, native audio render, actual AAC mux and FFmpeg signal statistics. */
final class FilmAudioExportProbe
{
    static void run(ClientGameTestContext context, Path evidence) throws Exception
    {
        Path assets = context.computeOnClient(client -> BBSMod.getAssetsFolder().toPath().toAbsolutePath());
        Path game = context.computeOnClient(client -> BBSMod.getGameFolder().toPath().toRealPath());
        if (!assets.toRealPath().startsWith(game)) throw new AssertionError("Audio fixture requires owned game assets");
        Path source = assets.resolve("ci-export-audio/tone.wav");
        Files.createDirectories(source.getParent());
        if (Files.exists(source)) throw new AssertionError("Preserve an existing audio fixture");
        ByteBuffer samples = ByteBuffer.allocate(48_000 * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < 48_000; i++)
            samples.putShort((short) Math.round(Math.sin(2D * Math.PI * 1000D * i / 48_000D) * 8191D));
        WaveWriter.write(source.toFile(), new Wave(1, 1, 48_000, 16, samples.array()));
        Files.copy(source, evidence.resolve("audio-source-tone.wav"));
        AtomicReference<Boolean> finished = new AtomicReference<>();
        context.runOnClient(client ->
        {
            AudioClip clip = new AudioClip();
            clip.audio.set(Link.assets("ci-export-audio/tone.wav"));
            clip.duration.set(20);
            panel().getData().camera.addClip(clip);
            panel().forceSave();
            panel().setCursor(0);
            BBSSettings.videoExportFilenameFormat.set("film export audio audit");
            BBSSettings.videoExportAudio.set(true);
            BBSSettings.videoArgumentsAudio.set(BBSSettings.DEFAULT_AUDIO_FFMPEG_ARGUMENTS);
            panel().recorder.setFinishedListener(finished::set);
        });
        context.waitTicks(5);
        FilmVideoExportGameTest.clickRecord(context);
        context.waitFor(client -> finished.get() != null);
        if (finished.get()) throw new AssertionError("Normal audio export was cancelled");
        context.waitTicks(2);
        context.takeScreenshot("film-export-audio-completed");
        Path movie = evidence.resolve("film export audio audit.mp4");
        Path probe = evidence.resolve("audio-ffprobe.json");
        FilmVideoExportGameTest.execute(List.of("ffprobe", "-v", "error", "-count_frames", "-show_streams",
            "-of", "json", movie.toString()), probe);
        JsonObject root = JsonParser.parseString(Files.readString(probe)).getAsJsonObject();
        JsonObject audio = root.getAsJsonArray("streams").asList().stream().map(value -> value.getAsJsonObject())
            .filter(value -> value.get("codec_type").getAsString().equals("audio")).findFirst()
            .orElseThrow(() -> new AssertionError("Export silently lost the requested audio track"));
        Path stats = evidence.resolve("audio-signal-stats.log");
        FilmVideoExportGameTest.execute(List.of("ffmpeg", "-i", movie.toString(), "-map", "0:a:0",
            "-af", "astats=reset=0", "-f", "null", "-"), stats);
        FilmVideoExportGameTest.execute(List.of("ffmpeg", "-v", "error", "-i", movie.toString(),
            "-map", "0:a:0", "-c:a", "pcm_s16le", evidence.resolve("audio-decoded.wav").toString()),
            evidence.resolve("audio-decode.log"));
        double rms = statistic(Files.readString(stats), "RMS level dB");
        double crossingRate = statistic(Files.readString(stats), "Zero crossings rate");
        Map<String, Object> result = Map.of("actualAudioClip", true, "normalRecordButton", true,
            "sourceFrequencyHz", 1000, "sourcePeak", 8191D / 32768D, "probe", audio,
            "rmsLevelDb", rms, "zeroCrossingsRate", crossingRate, "finishedCancelled", finished.get());
        String json = new com.google.gson.Gson().toJson(result);
        Files.writeString(evidence.resolve("audio-result.json"), json + "\n");
        System.out.println("BBS_FILM_AUDIO_EXPORT " + json);
        if (!audio.get("codec_name").getAsString().equals("aac") || audio.get("sample_rate").getAsInt() != 48_000
            || audio.get("channels").getAsInt() != 1 || Math.abs(audio.get("duration").getAsDouble() - 1D) > 0.05D
            || rms < -16.5D || rms > -13.5D || crossingRate < 0.04D || crossingRate > 0.044D)
            throw new AssertionError("Exported audio does not preserve the one-second1kHz source signal");
    }

    private static double statistic(String text, String name)
    {
        java.util.regex.Matcher match = java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(name) + ":\\s*([-0-9.]+)").matcher(text);
        Double result = null;
        while (match.find()) result = Double.parseDouble(match.group(1));
        if (result == null || !Double.isFinite(result)) throw new AssertionError("Missing finite FFmpeg statistic: " + name);
        return result;
    }

    private static UIFilmPanel panel()
    {
        return BBSModClient.getDashboard().getPanels().getPanel(UIFilmPanel.class);
    }
}
