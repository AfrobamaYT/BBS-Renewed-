// Coded by Afrobama/Claude — Blender<->BBS live-sync bridge (Phase 3 actor export).
package com.blenderbridge.actors;

import com.blenderbridge.BridgeState;

import com.google.gson.JsonObject;

import com.mojang.logging.LogUtils;

import org.slf4j.Logger;

/**
 * ActorBridge — turns an ACTORS_EXPORT message into an {@link ActorExporter} run and back into an
 * ACTORS_EXPORT_RESULT payload. Mirrors {@code WorldExportBridge} for the world dump.
 *
 * <p>WIRE CONTRACT (agreed with the Blender add-on):</p>
 * <pre>
 * request:  {"type":"ACTORS_EXPORT","origin":"blender","uuid":"actors","seq":&lt;int&gt;,"fields":{}}
 * reply:    {"type":"ACTORS_EXPORT_RESULT","origin":"bbs","uuid":"actors","seq":&lt;int&gt;,
 *            "fields":{"ok":&lt;bool&gt;,"film":"&lt;id&gt;","source":"editor"|"playing",
 *                      "replays":[...],"error":"&lt;msg&gt;"}}
 * </pre>
 *
 * <p>The request takes no parameters — "export whatever film is open". See {@link ActorProtocol} for
 * the full reply shape. The reply always carries every key, {@code error} included (empty on
 * success), so the python side never has to branch on key presence.</p>
 *
 * <p>THREADING: {@link #export(JsonObject)} must be called on the client main thread — it is invoked
 * from {@code BlenderBridgeClient}'s client-tick dispatch and reads live film state. The same holds
 * for {@link #importActors(JsonObject, BridgeState)}, which WRITES live film state.</p>
 *
 * <p>Both directions of the actor sync are dispatched through this class: {@link #export(JsonObject)}
 * (BBS -&gt; Blender, see {@link ActorExporter}) and {@link #importActors(JsonObject, BridgeState)}
 * (Blender -&gt; BBS, see {@link ActorImporter}).</p>
 */
public final class ActorBridge
{
    private static final Logger LOGGER = LogUtils.getLogger();

    private ActorBridge()
    {
        /* static-only helper */
    }

    /**
     * Run an actor export.
     *
     * <p>MAIN THREAD ONLY. Never throws — every failure comes back as a payload with
     * {@code ok:false} and a readable {@code error}, so Blender gets an answer instead of a silent
     * timeout.</p>
     *
     * @param fields the request's {@code fields} object; unused (the export takes no parameters) and
     *               accepted only so the handler signature matches the other bridges
     * @return the ACTORS_EXPORT_RESULT payload; never null
     */
    public static JsonObject export(JsonObject fields)
    {
        /* exportActors() reports "no film open" as a normal ok:false payload; this catch is the
         * belt-and-braces guard so a bug in it can never kill the tick handler. */
        try
        {
            JsonObject payload = ActorExporter.exportActors();

            LOGGER.info("[blenderbridge] ACTORS_EXPORT -> ok={} film='{}'",
                payload.get(ActorProtocol.OK),
                payload.get(ActorProtocol.FILM));

            return payload;
        }
        catch (Throwable t)
        {
            LOGGER.error("[blenderbridge] ACTORS_EXPORT failed", t);

            return ActorExporter.failure(t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    /**
     * Run an actor import: write Blender-authored keyframe curves into the open film's replays.
     *
     * <p>MAIN THREAD ONLY. Never throws — every failure comes back as a payload with {@code ok:false}
     * and a readable {@code error}, so Blender gets an answer instead of a silent timeout.</p>
     *
     * <p>WIRE CONTRACT:</p>
     * <pre>
     * request:  {"type":"ACTORS_IMPORT","origin":"blender","uuid":"actors","seq":&lt;int&gt;,
     *            "fields":{"replays":[{"uuid":"&lt;stable uuid&gt;","channels":{"x":[{...}]}}]}}
     * reply:    {"type":"ACTORS_IMPORT_RESULT","origin":"bbs","uuid":"actors","seq":&lt;int&gt;,
     *            "fields":{"ok":&lt;bool&gt;,"applied":&lt;int&gt;,"skipped":&lt;int&gt;,"error":"&lt;msg&gt;"}}
     * </pre>
     *
     * @param fields the request's {@code fields} object — the import payload itself
     * @param state  the shared bridge state, providing the mandatory {@code applyingRemote} echo guard
     * @return the ACTORS_IMPORT_RESULT payload; never null
     */
    public static JsonObject importActors(JsonObject fields, BridgeState state)
    {
        /* applyActors() reports "no film open" as a normal ok:false payload; this catch is the
         * belt-and-braces guard so a bug in it can never kill the tick handler. */
        try
        {
            if (fields != null && fields.has("posePreview") && fields.get("posePreview").getAsBoolean())
            {
                return ActorPosePreview.handle(fields);
            }
            if (fields != null && fields.has("project"))
            {
                return ActorDurableTransactions.handle(fields, state, false);
            }
            if (ActorChannelPatch.isConditional(fields))
            {
                JsonObject payload = ActorChannelPatch.apply(fields, state);

                LOGGER.info("[blenderbridge] conditional ACTORS_IMPORT -> ok={} applied={} conflict={}",
                    payload.get(ActorProtocol.OK), payload.get(ActorProtocol.APPLIED), payload.get("conflict"));

                return payload;
            }

            /* Task 1.5 WRITE-SAFETY NET: snapshots the film about to be mutated and rotates a backup
             * to disk BEFORE this write lands, but only commits it once applyActors' own "applied"
             * count says something actually changed — see FilmBackup class-doc. */
            JsonObject payload = FilmBackup.protect(() -> ActorImporter.applyActors(fields, state));

            LOGGER.info("[blenderbridge] ACTORS_IMPORT -> ok={} applied={} skipped={}",
                payload.get(ActorProtocol.OK),
                payload.get(ActorProtocol.APPLIED),
                payload.get(ActorProtocol.SKIPPED));

            /* Task 1.6 ECHO GUARD: re-fingerprint the just-mutated film NOW and adopt it as
             * ActorPushTracker's baseline, BEFORE the client tick's later ActorPushTracker.tick()
             * call runs (see BlenderBridgeClient.onClientTick — dispatch, which reaches this
             * method, always runs first). Without this, that same tick's push-check would see the
             * values Blender just sent as a "change" and bounce them straight back. See
             * ActorPushTracker's class-doc for the full reasoning. */
            ActorPushTracker.markSynced();

            return payload;
        }
        catch (Throwable t)
        {
            LOGGER.error("[blenderbridge] ACTORS_IMPORT failed", t);

            return ActorImporter.failure(t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }
}
