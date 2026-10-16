// Coded by Afrobama/Codex — Blender<->BBS live player recording control.
package com.blenderbridge.actors;

import com.blenderbridge.BBSCompat;
import com.blenderbridge.BlenderBridgeClient;
import com.blenderbridge.BridgeProtocol;

import com.google.gson.JsonObject;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.Films;
import mchorse.bbs_mod.film.Recorder;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * Starts and stops BBS' real outside-player recorder from Blender.
 *
 * <p>This deliberately calls the same {@link Films#startRecording} and
 * {@link UIFilmPanel#applyRecordedKeyframes} paths used by BBS' own record key. Blender does not
 * emulate recording and does not write directly into a film. A successful stop first commits the
 * recorder into the target replay; {@link ActorPushTracker} then sends the authoritative simplified
 * curves through the normal actor channel.</p>
 */
public final class ReplayRecordingControl
{
    private ReplayRecordingControl()
    {
        /* static-only helper */
    }

    /** Handle one main-thread control request and always emit a correlated result. */
    public static void handle(JsonObject message)
    {
        JsonObject fields = BridgeProtocol.getFields(message);
        String action = BridgeProtocol.getFieldString(fields, "action", "status").toLowerCase();
        String requestId = BridgeProtocol.getFieldString(fields, "requestId", "");
        String replayUuid = BridgeProtocol.getFieldString(fields, "replayUuid", "");
        JsonObject result;

        try
        {
            result = switch (action)
            {
                case "start" -> start(replayUuid);
                case "stop" -> stop();
                case "status" -> status();
                default -> failure("unknown action: " + action);
            };
        }
        catch (RuntimeException e)
        {
            result = failure(e.getClass().getSimpleName() + ": " + e.getMessage());
        }

        result.addProperty("action", action);
        result.addProperty("requestId", requestId);
        send(result);
    }

    private static JsonObject start(String replayUuid)
    {
        UIFilmPanel panel = panel();

        if (panel == null || panel.getData() == null)
        {
            return failure("no film open");
        }

        Films films = BBSModClient.getFilms();

        if (films == null)
        {
            return failure("BBS film runtime unavailable");
        }
        if (films.getRecorder() != null)
        {
            return failure("a replay recording is already active");
        }

        Film film = panel.getData();
        List<Replay> replays = film.replays.getList();
        int index = findReplay(replays, replayUuid, panel.replayEditor.getReplay());

        if (index < 0)
        {
            return failure(replayUuid.isEmpty() ? "no replay selected" : "unknown replay uuid");
        }

        Replay replay = replays.get(index);

        /* The outside recorder needs the game view, exactly like UIFilmController's outside mode. */
        Minecraft.getInstance().gui.setScreen(null);
        films.startRecording(film, index, panel.getCursor());

        JsonObject out = success();

        out.addProperty("film", film.getId());
        out.addProperty("replayUuid", replay.uuid.get());
        out.addProperty("recording", true);

        return out;
    }

    private static JsonObject stop()
    {
        UIFilmPanel panel = panel();
        Films films = BBSModClient.getFilms();

        if (films == null || films.getRecorder() == null)
        {
            return failure("no replay recording is active");
        }

        Recorder recorder = films.stopRecording();

        if (recorder == null)
        {
            return failure("recorder disappeared while stopping");
        }
        if (recorder.hasNotStarted())
        {
            return failure("recording stopped during countdown");
        }
        if (panel == null || panel.getData() == null || panel.getData() != recorder.film)
        {
            return failure("recorded film is not open in the editor");
        }

        Replay replay = replay(recorder);

        panel.applyRecordedKeyframes(recorder, panel.getData());
        panel.save();

        /* A recording stop is an authoritative transaction boundary, not merely a local edit.
         * ActorPushTracker only emits when its simplified fingerprint changed; a repeated/static
         * take can legitimately simplify to byte-identical curves and would otherwise leave
         * Blender waiting forever despite REPLAY_RECORD_STOP promising this follow-up. Send the
         * committed film explicitly, then adopt it as the change tracker's baseline to avoid a
         * duplicate push on the next client tick. */
        JsonObject payload = ActorBridge.export(new JsonObject());
        long actorSeq = BlenderBridgeClient.STATE.nextOutboundSeq();
        BlenderBridgeClient.SERVER.send(BridgeProtocol.actorsExportResult(actorSeq, payload));
        ActorPushTracker.markSynced();

        JsonObject out = success();

        out.addProperty("film", recorder.film.getId());
        out.addProperty("replayUuid", replay == null ? "" : replay.uuid.get());
        out.addProperty("recording", false);
        out.addProperty("lastTick", recorder.getTick());

        return out;
    }

    private static JsonObject status()
    {
        Recorder recorder = BBSModClient.getFilms() == null ? null : BBSModClient.getFilms().getRecorder();
        Replay replay = replay(recorder);
        JsonObject out = success();

        out.addProperty("recording", recorder != null);
        out.addProperty("film", recorder == null ? "" : recorder.film.getId());
        out.addProperty("replayUuid", replay == null ? "" : replay.uuid.get());
        out.addProperty("tick", recorder == null ? -1 : recorder.getTick());
        out.addProperty("countdown", recorder == null ? 0 : recorder.countdown);

        return out;
    }

    private static int findReplay(List<Replay> replays, String uuid, Replay selected)
    {
        if (!uuid.isEmpty())
        {
            for (int i = 0; i < replays.size(); i++)
            {
                Replay replay = replays.get(i);

                if (replay != null && uuid.equals(replay.uuid.get()))
                {
                    return i;
                }
            }

            return -1;
        }

        return selected == null ? -1 : replays.indexOf(selected);
    }

    private static Replay replay(Recorder recorder)
    {
        if (recorder == null || recorder.film == null)
        {
            return null;
        }

        List<Replay> replays = recorder.film.replays.getList();

        return recorder.exception >= 0 && recorder.exception < replays.size()
            ? replays.get(recorder.exception)
            : null;
    }

    private static UIFilmPanel panel()
    {
        UIDashboard dashboard = BBSCompat.peekDashboard();

        return dashboard == null ? null : dashboard.getPanel(UIFilmPanel.class);
    }

    private static JsonObject success()
    {
        JsonObject out = new JsonObject();

        out.addProperty("ok", true);
        out.addProperty("error", "");

        return out;
    }

    private static JsonObject failure(String error)
    {
        JsonObject out = new JsonObject();

        out.addProperty("ok", false);
        out.addProperty("error", error == null ? "" : error);
        out.addProperty("recording", BBSModClient.getFilms() != null
            && BBSModClient.getFilms().getRecorder() != null);

        return out;
    }

    private static void send(JsonObject fields)
    {
        long seq = BlenderBridgeClient.STATE.nextOutboundSeq();
        JsonObject envelope = BridgeProtocol.envelope(
            BridgeProtocol.TYPE_REPLAY_RECORD_CONTROL_RESULT,
            "replay-record-control",
            seq,
            BridgeProtocol.ORIGIN_BBS
        );

        envelope.add(BridgeProtocol.KEY_FIELDS, fields);
        BlenderBridgeClient.SERVER.send(envelope);
    }
}
