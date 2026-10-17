package com.afrobama.bbstools.gametest;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.film.VideoExportSession;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIMessageOverlayPanel;
import mchorse.bbs_mod.utils.VideoRecorder;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Kill the actual owned encoder, then finalize through the normal editor session. */
final class FilmExportFailureProbe
{
    static void run(ClientGameTestContext context, Path evidence) throws Exception
    {
        run(context, evidence, false);
        run(context, evidence, true);
    }

    private static void run(ClientGameTestContext context, Path evidence, boolean finalizeImmediately) throws Exception
    {
        AtomicReference<Boolean> finished = new AtomicReference<>();
        context.runOnClient(client ->
        {
            BBSSettings.videoExportFilenameFormat.set("film export interrupted");
            BBSSettings.videoExportAudio.set(false);
            panel().getData().camera.get(0).duration.set(200);
            panel().forceSave();
            panel().setCursor(0);
            panel().recorder.setFinishedListener(finished::set);
        });
        context.waitTicks(3);
        FilmVideoExportGameTest.clickRecord(context);
        context.waitFor(client -> panel().recorder.isRecording() && BBSModClient.getVideoRecorder().getCounter() >= 2);
        Map<String, Object> injection = context.computeOnClient(client -> terminateEncoder(finalizeImmediately));
        context.waitFor(client -> finished.get() != null);
        context.waitTicks(2);
        Map<String, Object> state = context.computeOnClient(client ->
        {
            Map<String, Object> result = new LinkedHashMap<>(injection);
            result.put("finishedCancelled", finished.get());
            result.put("editorIdle", !panel().isRunning() && !panel().recorder.isExporting() && !VideoExportSession.laeuftExport());
            result.put("errorMessages", panel().getContext().menu.overlay.getChildren(UIMessageOverlayPanel.class).stream()
                .map(message -> message.message.getText().get()).toList());
            return result;
        });
        String json = new com.google.gson.Gson().toJson(state);
        Files.writeString(evidence.resolve(finalizeImmediately ? "encoder-failure.json" : "encoder-frame-failure.json"), json + "\n");
        System.out.println("BBS_ENCODER_FAILURE " + json);
        context.takeScreenshot(finalizeImmediately ? "film-export-encoder-failure" : "film-export-encoder-frame-failure");
        boolean errorVisible = ((java.util.List<?>) state.get("errorMessages")).stream()
            .anyMatch(message -> message.toString().contains("Video export failed"));
        if (!Boolean.TRUE.equals(finished.get()) || !Boolean.TRUE.equals(state.get("editorIdle")) || !errorVisible
            || Boolean.TRUE.equals(state.get("encoderAliveAfterStop")) || ((Number) state.get("ownedEncoderChildrenAlive")).longValue() != 0L)
            throw new AssertionError("Failed encoder finalization was reported as success or lost its visible failure");
        context.runOnClient(client -> panel().getContext().menu.overlay.getChildren(UIMessageOverlayPanel.class)
            .forEach(UIMessageOverlayPanel::close));
        context.waitTicks(2);
    }

    private static Map<String, Object> terminateEncoder(boolean finalizeImmediately)
    {
        try
        {
            VideoRecorder recorder = BBSModClient.getVideoRecorder();
            java.lang.reflect.Field field = VideoRecorder.class.getDeclaredField("process");
            field.setAccessible(true);
            Process process = (Process) field.get(recorder);
            if (process == null || !process.isAlive()) throw new AssertionError("Owned encoder exited before fault injection");
            long pid = process.pid();
            int frames = recorder.getCounter();
            /* Windows' executable shim can own the real encoder child. Both belong to this recorder. */
            java.util.List<ProcessHandle> children = process.descendants().toList();
            children.forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            if (!process.waitFor(3L, TimeUnit.SECONDS)) throw new AssertionError("Owned encoder did not terminate");
            int exit = process.exitValue();
            if (exit == 0) throw new AssertionError("Fault injection did not produce an encoder failure");
            if (finalizeImmediately) panel().recorder.stop();
            return Map.of("ownedEncoderPid", pid, "encoderExit", exit, "submittedFramesAtFailure", frames,
                "encoderAliveAfterStop", process.isAlive(), "ownedEncoderChildrenAlive", children.stream().filter(ProcessHandle::isAlive).count(),
                "finalizedImmediately", finalizeImmediately);
        }
        catch (ReflectiveOperationException failure)
        {
            throw new AssertionError("Cannot inspect the real recorder's owned encoder process", failure);
        }
        catch (InterruptedException failure)
        {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while terminating the owned encoder", failure);
        }
    }

    private static UIFilmPanel panel()
    {
        return BBSModClient.getDashboard().getPanels().getPanel(UIFilmPanel.class);
    }
}
