package com.afrobama.bbstools.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.film.VideoExportSession;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.utils.VideoRecorder;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** The normal Escape key must cancel the actual export and return a usable editor. */
final class FilmExportCancellationProbe
{
    static void run(ClientGameTestContext context, Path evidence) throws Exception
    {
        AtomicReference<Boolean> finished = new AtomicReference<>();
        context.runOnClient(client ->
        {
            BBSSettings.videoExportFilenameFormat.set("film export cancelled");
            BBSSettings.videoExportAudio.set(false);
            panel().getData().camera.get(0).duration.set(200);
            panel().forceSave();
            panel().setCursor(0);
            panel().recorder.setFinishedListener(finished::set);
        });
        context.waitTicks(3);
        FilmVideoExportGameTest.clickRecord(context);
        context.waitFor(client -> panel().recorder.isRecording() && BBSModClient.getVideoRecorder().getCounter() >= 2);
        Process encoder = context.computeOnClient(client ->
        {
            try
            {
                java.lang.reflect.Field field = VideoRecorder.class.getDeclaredField("process");
                field.setAccessible(true);
                return (Process) field.get(BBSModClient.getVideoRecorder());
            }
            catch (ReflectiveOperationException failure) { throw new AssertionError("Cannot identify owned encoder", failure); }
        });
        if (encoder == null || !encoder.isAlive()) throw new AssertionError("Encoder exited before the real Escape key");
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitFor(client -> finished.get() != null);
        if (!encoder.waitFor(3L, TimeUnit.SECONDS)) throw new AssertionError("Cancellation left the owned encoder running");
        context.waitTicks(2);
        boolean usable = context.computeOnClient(client -> client.gui.screen() instanceof UIScreen
            && !panel().isRunning() && !panel().recorder.isExporting() && !VideoExportSession.laeuftExport()
            && panel().getContext().menu.main.isEnabled());
        Map<String, Object> result = Map.of("actualEscapeKey", true, "finishedCancelled", finished.get(),
            "editorUsable", usable, "ownedEncoderPid", encoder.pid(), "encoderExit", encoder.exitValue(),
            "encoderAlive", encoder.isAlive(), "submittedFrames", context.computeOnClient(client -> BBSModClient.getVideoRecorder().getCounter()));
        String json = new com.google.gson.Gson().toJson(result);
        Files.writeString(evidence.resolve("cancel-result.json"), json + "\n");
        System.out.println("BBS_FILM_EXPORT_CANCEL " + json);
        context.takeScreenshot("film-export-cancelled");
        if (!Boolean.TRUE.equals(finished.get()) || !usable || encoder.isAlive() || encoder.exitValue() != 0)
            throw new AssertionError("Real Escape key did not cancel and restore the actual film editor");
    }

    private static UIFilmPanel panel()
    {
        return BBSModClient.getDashboard().getPanels().getPanel(UIFilmPanel.class);
    }
}
