// Coded by Afrobama/Codex — Blender<->BBS live player recording preview.
package com.blenderbridge.actors;

import com.blenderbridge.BlenderBridgeClient;
import com.blenderbridge.BridgeProtocol;

import com.google.gson.JsonObject;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.film.Recorder;
import mchorse.bbs_mod.film.replays.Replay;
import java.util.List;
import java.util.UUID;

/**
 * Streams the live player recorder as timestamped preview frames while recording is in progress.
 *
 * <p>The stream is intentionally not the recording of record. It may be dropped or replaced on the
 * Blender side to stay responsive. When BBS stops the recorder, the normal {@link ActorPushTracker}
 * sends the complete simplified keyframe curves through ACTORS_EXPORT_RESULT. This mirrors
 * established Live Link systems: static subject data once, frame samples while live, a durable
 * asset/curve update after capture.</p>
 */
public final class ReplayRecordingLiveLink
{
    private static Recorder active;
    private static String session = "";
    private static String replayUuid = "";
    private static int lastTick = Integer.MIN_VALUE;
    private static int lastCountdown = Integer.MIN_VALUE;

    private ReplayRecordingLiveLink()
    {
        /* static-only helper */
    }

    /** Main-thread tick, called after the recorder itself has sampled the local player. */
    public static void tick()
    {
        Recorder recorder = BBSModClient.getFilms() == null ? null : BBSModClient.getFilms().getRecorder();

        if (recorder != active)
        {
            if (active != null)
            {
                sendStop(active);
            }

            active = recorder;
            session = "";
            replayUuid = "";
            lastTick = Integer.MIN_VALUE;
            lastCountdown = Integer.MIN_VALUE;

            if (active != null)
            {
                sendStart(active);
            }
        }

        if (active == null || BlenderBridgeClient.SERVER.connectionCount() == 0)
        {
            return;
        }

        if (active.hasNotStarted())
        {
            if (active.countdown != lastCountdown)
            {
                lastCountdown = active.countdown;
                sendFrame(active, true);
            }

            return;
        }

        int tick = active.getTick();

        if (tick == lastTick)
        {
            return;
        }

        lastTick = tick;
        sendFrame(active, false);
    }

    private static void sendStart(Recorder recorder)
    {
        if (BlenderBridgeClient.SERVER.connectionCount() == 0)
        {
            return;
        }

        session = UUID.randomUUID().toString();
        Replay replay = replay(recorder);
        replayUuid = replay == null ? "" : replay.uuid.get();
        JsonObject fields = base(recorder);

        fields.addProperty("tickRate", 20);
        fields.addProperty("initialTick", recorder.initialTick);
        fields.addProperty("countdown", recorder.countdown);
        fields.add("replay", ActorExporter.recordingReplayPayload(replayUuid));
        fields.add("form", ActorExporter.recordingCurrentForm());
        send(BridgeProtocol.TYPE_REPLAY_RECORD_START, fields);
    }

    private static void sendFrame(Recorder recorder, boolean countdown)
    {
        JsonObject fields = base(recorder);

        fields.addProperty("countdown", recorder.countdown);
        fields.addProperty("preview", true);
        fields.addProperty("duringCountdown", countdown);
        fields.add("values", ActorExporter.recordingNumericValues(recorder));
        fields.add("strings", ActorExporter.recordingStringValues(recorder));
        fields.add("form", ActorExporter.recordingCurrentForm());
        send(BridgeProtocol.TYPE_REPLAY_RECORD_FRAME, fields);
    }

    private static void sendStop(Recorder recorder)
    {
        if (session.isEmpty() || BlenderBridgeClient.SERVER.connectionCount() == 0)
        {
            return;
        }

        JsonObject fields = base(recorder);

        fields.addProperty("completed", !recorder.hasNotStarted());
        fields.addProperty("authoritativeFollowup", BridgeProtocol.TYPE_ACTORS_EXPORT_RESULT);
        send(BridgeProtocol.TYPE_REPLAY_RECORD_STOP, fields);
    }

    private static JsonObject base(Recorder recorder)
    {
        JsonObject fields = new JsonObject();

        fields.addProperty("session", session);
        fields.addProperty("film", recorder.film == null ? "" : recorder.film.getId());
        fields.addProperty("replayUuid", replayUuid);
        fields.addProperty("tick", recorder.getTick());

        return fields;
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

    private static void send(String type, JsonObject fields)
    {
        long seq = BlenderBridgeClient.STATE.nextOutboundSeq();
        JsonObject envelope = BridgeProtocol.envelope(type, session, seq, BridgeProtocol.ORIGIN_BBS);

        envelope.add(BridgeProtocol.KEY_FIELDS, fields);
        BlenderBridgeClient.SERVER.send(envelope);
    }
}
