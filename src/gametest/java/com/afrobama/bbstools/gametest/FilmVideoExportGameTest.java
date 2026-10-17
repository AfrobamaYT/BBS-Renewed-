package com.afrobama.bbstools.gametest;

import com.afrobama.bbstools.client.dashboard.AnnouncementGate;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.clips.overwrite.DollyClip;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.VideoExportSession;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.utils.MathUtils;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Normal editor input, real GPU capture and independently decoded encoder output. */
public final class FilmVideoExportGameTest implements FabricClientGameTest
{
    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (TestSingleplayerContext world = context.worldBuilder().create())
        {
            try { verify(context, world); }
            finally
            {
                context.runOnClient(client -> { if (panel().recorder.isExporting()) panel().recorder.cancel(); });
            }
        }
        catch (Exception failure)
        {
            throw new AssertionError("Cannot inspect the actual film export", failure);
        }
    }

    private static void verify(ClientGameTestContext context, TestSingleplayerContext world) throws Exception
    {
        world.getConnection().waitForChunksRender();
        BlockPos origin = world.getServer().computeOnServer(server ->
        {
            BlockPos start = server.getPlayerList().getPlayers().getFirst().blockPosition().offset(0, 1, 5);
            server.overworld().setBlockAndUpdate(start, Blocks.GOLD_BLOCK.defaultBlockState());
            server.overworld().setBlockAndUpdate(start.east(), Blocks.DIAMOND_BLOCK.defaultBlockState());
            return start;
        });
        context.waitFor(client -> client.level.getBlockState(origin).is(Blocks.GOLD_BLOCK)
            && client.level.getBlockState(origin.east()).is(Blocks.DIAMOND_BLOCK));
        context.getInput().lookAt(origin);
        context.setScreen(() ->
        {
            AnnouncementGate.markShown();
            mchorse.bbs_mod.ui.hilfe.HilfeErststart.hilfeGeoeffnet();
            return new UIScreen(Component.empty(), BBSModClient.getDashboard());
        });
        AtomicReference<Boolean> finished = new AtomicReference<>();
        Path evidence = context.computeOnClient(client -> prepare(client, finished));
        context.waitTicks(10);
        context.takeScreenshot("film-export-ready");
        double[] point = context.computeOnClient(client ->
        {
            List<com.mcstudio.nano.NanoFlaeche> surfaces = panel().preview.werkzeuge.getChildren(com.mcstudio.nano.NanoFlaeche.class);
            if (surfaces.size() != 1) throw new AssertionError("Expected the actual visible viewport toolbar surface");
            com.mcstudio.nano.NanoFlaeche surface = surfaces.getFirst();
            int index = surface.zonenNamen().indexOf("Record video");
            if (index < 0) throw new AssertionError("Visible toolbar did not publish its real Record video hit zone");
            float[] zone = surface.zonenMasse().get(index);
            float scale = (float) client.getWindow().getScreenWidth() / panel().getContext().menu.width;
            return new double[] {(surface.area.x + (zone[0] + zone[2] / 2F) / surface.skala()) * scale,
                (surface.area.y + (zone[1] + zone[3] / 2F) / surface.skala()) * scale};
        });
        context.getInput().setCursorPos(point[0], point[1]);
        context.waitTicks(2);
        context.getInput().holdMouse(mchorse.bbs_mod.graphics.window.Window.nativeMouseButton(0));
        context.waitTick();
        context.getInput().releaseMouse(mchorse.bbs_mod.graphics.window.Window.nativeMouseButton(0));
        context.waitFor(client -> finished.get() != null);
        int submitted = context.computeOnClient(client -> BBSModClient.getVideoRecorder().getCounter());
        boolean restored = context.computeOnClient(client -> !panel().isRunning()
            && !panel().recorder.isExporting() && !VideoExportSession.laeuftExport()
            && panel().getContext().menu.main.isEnabled());
        context.waitTicks(2);
        context.takeScreenshot("film-export-completed");
        Path movie = evidence.resolve("film export audit.mp4");
        if (finished.get() || !restored || !Files.isRegularFile(movie))
            throw new AssertionError("Normal editor export did not finish and restore its usable editor");
        Path probe = evidence.resolve("ffprobe.json");
        execute(List.of("ffprobe", "-v", "error", "-count_frames", "-select_streams", "v:0",
            "-show_entries", "stream=codec_name,width,height,r_frame_rate,nb_read_frames,duration",
            "-of", "json", movie.toString()), probe);
        JsonObject stream = JsonParser.parseString(Files.readString(probe)).getAsJsonObject()
            .getAsJsonArray("streams").get(0).getAsJsonObject();
        Path frames = Files.createDirectories(evidence.resolve("decoded-frames"));
        execute(List.of("ffmpeg", "-v", "error", "-i", movie.toString(), "-vsync", "0",
            frames.resolve("frame-%03d.png").toString()), evidence.resolve("decode.log"));
        Map<String, Object> result = Map.of("normalRecordButton", true, "finishedCancelled", finished.get(),
            "editorRestored", restored, "submittedFrames", submitted, "probe", stream,
            "movie", movie.toString(), "visualAcceptance", "Requires inspection of the actual exported motion");
        String json = new com.google.gson.Gson().toJson(result);
        Files.writeString(evidence.resolve("result.json"), json + "\n");
        System.out.println("BBS_FILM_VIDEO_EXPORT " + json);
        if (!stream.get("codec_name").getAsString().equals("h264") || stream.get("width").getAsInt() != 320
            || stream.get("height").getAsInt() != 180 || !stream.get("r_frame_rate").getAsString().equals("20/1")
            || stream.get("nb_read_frames").getAsInt() != 20 || submitted != 20)
            throw new AssertionError("Exported bytes do not contain the requested one-second 320x180/20fps film");
    }

    private static Path prepare(Minecraft client, AtomicReference<Boolean> finished)
    {
        Path evidence = BBSMod.getGameFolder().toPath().toAbsolutePath().resolve("ci-film-export-evidence");
        try { Files.createDirectories(evidence); }
        catch (java.io.IOException failure) { throw new AssertionError("Cannot create owned export evidence", failure); }
        BBSSettings.videoExportPath.set(evidence.toString());
        BBSSettings.videoExportFilenameFormat.set("film export audit");
        BBSSettings.videoWidth.set(320);
        BBSSettings.videoHeight.set(180);
        BBSSettings.videoFrameRate.set(20);
        BBSSettings.videoDelay.set(0F);
        BBSSettings.videoMotionBlur.set(0);
        BBSSettings.videoSupersampling.set(1);
        BBSSettings.videoHeldFrames.set(1);
        BBSSettings.videoExportAudio.set(false);
        BBSSettings.videoExportMinecraftSounds.set(false);
        BBSSettings.videoOpenFolderAfterExport.set(false);
        BBSSettings.videoPlaySoundAfterExport.set(false);
        BBSSettings.editorLoop.set(false);
        BBSSettings.videoArguments.set(BBSSettings.DEFAULT_FFMPEG_ARGUMENTS);
        BBSSettings.userIntefaceScale.set(2F);
        Film film = new Film();
        film.setId("ci_video_export");
        Camera camera = new Camera();
        camera.set(client.player, MathUtils.toRad(client.options.fov().get()));
        DollyClip clip = new DollyClip();
        clip.fromCamera(camera);
        clip.duration.set(20);
        clip.distance.set(2F);
        clip.yaw.set(90F);
        clip.pitch.set(0F);
        film.camera.addClip(clip);
        BBSModClient.getDashboard().setPanel(panel());
        panel().fill(film);
        BBSModClient.getDashboard().copyCurrentEntityCamera();
        panel().setCursor(0);
        panel().recorder.setFinishedListener(finished::set);
        return evidence;
    }

    private static UIFilmPanel panel()
    {
        return BBSModClient.getDashboard().getPanels().getPanel(UIFilmPanel.class);
    }

    private static void execute(List<String> command, Path log) throws Exception
    {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        if (!process.waitFor(20, TimeUnit.SECONDS))
        {
            process.destroyForcibly();
            throw new AssertionError("Export inspection child exceeded20 seconds: " + command.getFirst());
        }
        if (process.exitValue() != 0)
            throw new AssertionError("Export inspection failed: " + command.getFirst() + "; see " + log);
    }
}
