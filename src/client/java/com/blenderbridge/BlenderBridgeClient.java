// Coded by Afrobama/Claude — Blender<->BBS live-sync bridge (Phase 1 transport).
package com.blenderbridge;

import com.blenderbridge.actors.ActorBridge;
import com.blenderbridge.world.WorldExportBridge;

import com.google.gson.JsonObject;

import com.mojang.logging.LogUtils;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;

/**
 * BlenderBridgeClient — the Fabric client entrypoint for the Blender live-sync bridge.
 *
 * <p>This is a SEPARATE add-on: it lives in its own package and touches NO BBS source. It
 * is registered as an additional {@code entrypoints.client} in fabric.mod.json alongside
 * the existing BBS/BBSTools client initializers.</p>
 *
 * <p>WHAT IT WIRES:</p>
 * <ol>
 *   <li>Starts {@link BridgeServer} (non-blocking TCP on 127.0.0.1:9880) on its own I/O
 *       thread — never blocks the game.</li>
 *   <li>Registers a {@link ClientTickEvents#END_CLIENT_TICK} handler that drains the
 *       server inbox ON THE MAIN THREAD and dispatches each message. This is the ONLY
 *       place BBS/MC state may be touched.</li>
 * </ol>
 *
 * <p>Phase 1 dispatch: PING → PONG, HELLO logged, CAMERA → LWW-filter → parked in
 * {@link BridgeState} for the camera-apply worker. The actual read/write of the BBS
 * camera is NOT done here — see {@link BridgeState#takePendingCamera()} and the TODO in
 * {@link #applyPendingCamera()}.</p>
 */
public final class BlenderBridgeClient implements ClientModInitializer
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Single shared transport instance. */
    public static final BridgeServer SERVER = new BridgeServer();

    /** Single shared sync-state instance (echo-suppression + LWW + pending camera). */
    public static final BridgeState STATE = new BridgeState();

    @Override
    public void onInitializeClient()
    {
        LOGGER.info("[blenderbridge] client init — starting transport");

        SERVER.start();
        com.blenderbridge.actors.ActorNativeLease.register();

        /* AUTONOMIE 2026-07-27: BOBJ-drag-drop direkt eintragen.
         *
         * Im CML-fork lief das ueber BridgeImporterAddon am entrypoint "bbs-addon-client":
         * BBS feuerte RegisterImportersEvent, das addon rief event.register(). Der fs-kern
         * hat weder das addon-system noch postet er dieses event — er fuellt Importers in
         * einem static-block. Das event lag also da und WURDE NIE GEFEUERT: unser importer
         * war still tot, drag-drop von .bobj/.gltf/.vox/.obj tat nichts.
         * Ein ganzes addon fuer einen einzeiler braucht es hier nicht. */
        mchorse.bbs_mod.importers.Importers.register(new com.blenderbridge.importers.ModelDropImporter());
        LOGGER.info("[blenderbridge] ModelDropImporter registriert — {} endungen per drag-drop",
            com.blenderbridge.importers.ModelDropImporter.MODEL_EXTENSIONS.length);

        /* MAIN-THREAD drain: everything that touches BBS/MC happens here, not on the I/O thread. */
        ClientTickEvents.END_CLIENT_TICK.register(BlenderBridgeClient::onClientTick);

        /* Best-effort clean shutdown so the port is released on game exit. */
        Runtime.getRuntime().addShutdownHook(new Thread(SERVER::stop, "blenderbridge-shutdown"));

        LOGGER.info("[blenderbridge] client init done");
    }

    /**
     * Runs every client tick on the MAIN thread. Drains the inbox and dispatches messages,
     * then hands any accepted camera delta to the apply hook.
     */
    private static void onClientTick(Minecraft client)
    {
        com.blenderbridge.actors.ActorNativeLease.tick();
        JsonObject message;

        while ((message = SERVER.pollInbox()) != null)
        {
            dispatch(message);
        }

        detectBbsCameraGrab();
        applyPendingCamera();
        pushCameraIfChanged();

        /* Task 4.3b (ENTITY_BAKE): per-tick drain for any in-progress entity pose bake. Body-only
         * addition — com.blenderbridge.entity.EntityBakeHandler is a NEW class (lazy-loadable, no
         * boot needed); this call adds no new member to BlenderBridgeClient itself
         * (canAddMethod=false on this JVM, task 0.2 — see that package's class docs). No-op when no
         * bake job is active, cheap to call unconditionally every tick. */
        com.blenderbridge.entity.EntityBakeHandler.tick();

        /* Hybrid-Render: WeltbildFahrt/WorldShot reihen ihre Arbeit in RenderThread ein. Geleert
         * wurde die Schlange nur vom Dev-Begleit-Jar (StudioClient); ohne ihn lief jeder Welt-Frame
         * in den 5-s-Timeout (gemessen 2026-10-14: 0 Bilder nach 6,5 min). Doppeltes Leeren mit
         * dem Begleit-Jar ist harmlos (poll auf einer gemeinsamen Schlange). */
        com.mcstudio.util.RenderThread.drain();

        /* Live player recording: preview frames first; ActorPushTracker below sends the durable
         * simplified film curves after the recorder commits on stop. */
        com.blenderbridge.actors.ReplayRecordingLiveLink.tick();

        /* Task 1.6 (push statt poll): per-tick fingerprint of the open film's actors, pushes an
         * unsolicited ACTORS_EXPORT_RESULT the instant something actually changed. Same body-only
         * pattern as the EntityBakeHandler call above — com.blenderbridge.actors.ActorPushTracker
         * is a NEW class (lazy-loadable, no boot needed), this call adds no new member to
         * BlenderBridgeClient itself (canAddMethod=false, task 0.2). No-op when idle or no film
         * is open, cheap to call unconditionally every tick (see that class' docs). */
        com.blenderbridge.actors.ActorPushTracker.tick();

        /* Task 1.4 (playhead-sync): per-tick fingerprint of the open film editor's playhead
         * (UIFilmPanel.getCursor()/isRunning()), pushes an unsolicited PLAYHEAD the instant the
         * scrub position or play-state changed and Blender is not currently the owner. Same
         * body-only pattern as the two calls above — com.blenderbridge.film.PlayheadSync is a
         * NEW class (lazy-loadable, no boot needed), this call adds no new member to
         * BlenderBridgeClient itself (canAddMethod=false, task 0.2). No-op when no film editor is
         * open or nothing changed, cheap to call unconditionally every tick (see that class' docs). */
        com.blenderbridge.film.PlayheadSync.tick();

        /* Persistent film markers: full revisioned snapshot on connect/film/content change. */
        com.blenderbridge.film.MarkerSync.tick();

        /* Task 3.6 (live-block-delta): per-tick budgeted sweep over the region of the last
         * successful world export, pushes an unsolicited BLOCK_DELTA when a block in it changed.
         * Same body-only pattern as the three calls above — com.blenderbridge.world.
         * BlockDeltaTracker is a NEW class (lazy-loadable, no boot needed), this call adds no new
         * member to BlenderBridgeClient itself. No-op until an export arms it (see that class'
         * docs for the measured "no mixin-free block event exists" finding + the budget model). */
        com.blenderbridge.world.BlockDeltaTracker.tick();
    }

    /**
     * Ownership transition "user grabs the camera in BBS" (panel BLOCKER 1: with the override
     * stamping every frame, editor input is invisible to normal reads — the ONLY trace is the
     * pre-overwrite wish that {@link CameraBridge#writeCamera} parks in
     * {@link com.blenderbridge.camera.CameraAuthority}). The authority diffs successive wishes;
     * a real change while an input-type controller (Orbit) runs means the BBS user moved the
     * camera -> take ownership back and tell Blender. Runner/Play = film playback -> playback
     * must never steal ownership (design doc C). Pure body change: ALL state (including the
     * per-tick diff baseline and the connection-lost flag) lives in the lazy-loadable
     * CameraAuthority, so THIS loaded class gains no fields (plan v2 task 1.1, panel H37).
     */
    private static void detectBbsCameraGrab()
    {
        /* transport died since last tick -> release ownership on the main thread */
        if (com.blenderbridge.camera.CameraAuthority.consumeConnectionLost())
        {
            com.blenderbridge.camera.CameraAuthority.request(
                com.blenderbridge.camera.CameraAuthority.Owner.NOBODY, "connection lost");
            LiveCameraOverride.clear();
        }

        boolean grabbed = com.blenderbridge.camera.CameraAuthority.grabDetected();

        if (com.blenderbridge.camera.CameraAuthority.get() != com.blenderbridge.camera.CameraAuthority.Owner.BLENDER)
        {
            return;
        }

        /* renderfahrt (plan 6.2): a scripted BBS render run may drive the camera deliberately */
        if (com.blenderbridge.camera.CameraAuthority.renderfahrt())
        {
            return;
        }

        if (grabbed && isUserInputCameraController())
        {
            com.blenderbridge.camera.CameraAuthority.request(
                com.blenderbridge.camera.CameraAuthority.Owner.BBS, "BBS user input (orbit controller moved)");
            LiveCameraOverride.clear();
            long seq = STATE.nextOutboundSeq();
            JsonObject handover = BridgeProtocol.envelope(BridgeProtocol.TYPE_CAMERA_OWNER, "camera", seq, "bbs");
            BridgeProtocol.getFields(handover).addProperty("owner", "BBS");
            SERVER.send(handover);
        }
    }

    /**
     * True when the active BBS camera controller is a direct-user-input type (orbit), false for
     * playback types (Runner/Play) and unknown ones. Name-based on purpose: we must not link
     * against BBS' controller classes harder than necessary, and an unknown controller defaulting
     * to "not user input" only means ownership stays with Blender — the safe direction.
     */
    private static boolean isUserInputCameraController()
    {
        try
        {
            Object controller = mchorse.bbs_mod.BBSModClient.getCameraController();
            java.lang.reflect.Field current = controller.getClass().getDeclaredField("current");
            current.setAccessible(true);
            Object active = current.get(controller);
            return active != null && active.getClass().getSimpleName().contains("Orbit");
        }
        catch (ReflectiveOperationException | RuntimeException e)
        {
            return false;
        }
    }

    /**
     * Dispatch a single inbound message on the main thread. Applies LWW where the message
     * carries an identity + sequence.
     */
    private static void dispatch(JsonObject message)
    {
        String type = BridgeProtocol.getType(message);

        switch (type)
        {
            case BridgeProtocol.TYPE_PING ->
            {
                /* Reply with a PONG carrying our own outbound sequence. */
                long seq = STATE.nextOutboundSeq();
                String uuid = BridgeProtocol.getUuid(message);
                SERVER.send(BridgeProtocol.pong(seq, uuid));
            }
            case BridgeProtocol.TYPE_PONG ->
            {
                /* Liveness confirmation — nothing to do in Phase 1. */
            }
            case BridgeProtocol.TYPE_HELLO ->
            {
                JsonObject peerFields = BridgeProtocol.getFields(message);
                STATE.updatePeerHandshake(peerFields);
                /* A HELLO means a peer (re)connected — its outbound seq counter restarted at 1.
                 * Our lastSeqPerUuid still holds that peer's PREVIOUS run (e.g. camera=129), so
                 * every fresh message would look stale to shouldApply() and be dropped: the sync
                 * silently dies after any Blender restart / addon reload until the peer counts
                 * past the old high-water mark. Clearing on HELLO scopes the LWW window to the
                 * current connection, which is what it is meant to protect (in-flight reordering),
                 * not across process lifetimes. */
                STATE.resetAllSeq();
                /* Ownership dies with the connection: a reconnecting peer must re-request the
                 * camera explicitly (panel: persistenter besitz über reconnect = verboten). */
                com.blenderbridge.camera.CameraAuthority.request(
                    com.blenderbridge.camera.CameraAuthority.Owner.NOBODY, "HELLO (peer reconnect)");
                LiveCameraOverride.clear();
                /* Task 1.4: same hygiene for the playhead owner — a reconnecting peer must
                 * re-claim "Drive timeline" explicitly, never inherit a stale BLENDER owner from
                 * before the reconnect (that would silently block BBS' default direction forever). */
                com.blenderbridge.film.PlayheadSync.reset();
                com.blenderbridge.film.MarkerSync.reset();
                /* Task 1.0 IDENTITY FOUNDATION: answer the peer's HELLO with the id of the film the
                 * bridge is currently addressing, so the add-on can BIND to it and detect a later
                 * film-switch (its own writes get tagged with this id; ActorImporter rejects a write
                 * whose tag no longer matches the open film — the datenverlust seal). Raw "film"
                 * field on a plain HELLO envelope: no BridgeProtocol constant/method added (that class
                 * is loaded — deploy rules), and FilmIdentity is a NEW lazy class so this is a pure
                 * body-swap here. Empty when no film is open (dashboard closed / unsaved) — the add-on
                 * treats "" as "nothing bound yet" and re-binds from the first ACTORS_EXPORT_RESULT
                 * (which already carries the same id). */
                long helloSeq = STATE.nextOutboundSeq();
                JsonObject helloBack = BridgeProtocol.envelope(BridgeProtocol.TYPE_HELLO, "hello", helloSeq, BridgeProtocol.ORIGIN_BBS);
                JsonObject helloFields = BridgeProtocol.getFields(helloBack);
                BridgeProtocol.addLocalHelloFields(helloFields);
                helloFields.addProperty("film", com.blenderbridge.FilmIdentity.currentFilmId());
                helloFields.addProperty("project", com.blenderbridge.actors.ActorProject.identity(FilmIdentity.resolveOpenFilm()));
                helloFields.addProperty("engine", com.blenderbridge.actors.ActorProject.ENGINE);
                /* 2026-08-16: assets-baum mitschicken — das addon uebernimmt ihn und die
                 * handgepflegte, instanzgebundene BBS_ASSETS_DIR-konstante stirbt. */
                helloFields.addProperty("assets", mchorse.bbs_mod.BBSMod.getAssetsFolder().getAbsolutePath());
                SERVER.send(helloBack);

                /* A camera baseline belongs to the BBS process, while HELLO belongs to one
                 * connection.  A second Blender client used to receive no CAMERA at all when
                 * the BBS camera had not moved since the first client connected; its factory
                 * camera then centred WORLD_EXPORT around the origin.  Send one authoritative
                 * snapshot on every HELLO regardless of the dirty baseline. */
                JsonObject cameraFields = CameraBridge.readCurrentCamera();
                if (cameraFields != null)
                {
                    SERVER.send(BridgeProtocol.camera(
                        STATE.nextOutboundSeq(),
                        BridgeProtocol.getDouble(cameraFields, BridgeProtocol.CAM_X, 0D),
                        BridgeProtocol.getDouble(cameraFields, BridgeProtocol.CAM_Y, 0D),
                        BridgeProtocol.getDouble(cameraFields, BridgeProtocol.CAM_Z, 0D),
                        BridgeProtocol.getDouble(cameraFields, BridgeProtocol.CAM_PITCH, 0D),
                        BridgeProtocol.getDouble(cameraFields, BridgeProtocol.CAM_YAW, 0D),
                        BridgeProtocol.getDouble(cameraFields, BridgeProtocol.CAM_ROLL, 0D),
                        BridgeProtocol.getDouble(cameraFields, BridgeProtocol.CAM_FOV, 70D),
                        BlenderBridgeClient.currentFilmTick()));
                }
                LOGGER.info("[blenderbridge] HELLO from {} v{} protocol={} recording-live={} — film='{}'",
                    BridgeProtocol.getOrigin(message), STATE.getPeerVersion(), STATE.getPeerProtocol(),
                    STATE.peerHasCapability("recording_live_v1"),
                    com.blenderbridge.FilmIdentity.currentFilmId());
            }
            case BridgeProtocol.TYPE_CAMERA_OWNER ->
            {
                /* Explicit camera handover from Blender ("Drive BBS camera" toggle). Replaces the
                 * historical implicit grab-on-first-frame that pinned the user's camera all session.
                 * Unknown/garbled owner values fall back to NOBODY — releasing is always safe,
                 * silently keeping ownership never is. */
                String rawOwner = "";
                try
                {
                    rawOwner = BridgeProtocol.getFields(message).get("owner").getAsString();
                    com.blenderbridge.camera.CameraAuthority.request(
                        com.blenderbridge.camera.CameraAuthority.Owner.valueOf(rawOwner), "CAMERA_OWNER from blender");
                }
                catch (RuntimeException e)
                {
                    com.blenderbridge.camera.CameraAuthority.request(
                        com.blenderbridge.camera.CameraAuthority.Owner.NOBODY, "CAMERA_OWNER unusable value");
                    LOGGER.warn("[blenderbridge] CAMERA_OWNER with unusable owner '{}' — released to NOBODY", rawOwner);
                }
                if (com.blenderbridge.camera.CameraAuthority.get() != com.blenderbridge.camera.CameraAuthority.Owner.BLENDER)
                {
                    LiveCameraOverride.clear();
                }
            }
            case BridgeProtocol.TYPE_CAMERA -> handleCamera(message);
            case BridgeProtocol.TYPE_EXEC -> handleExec(message);
            case BridgeProtocol.TYPE_WORLD_EXPORT -> handleWorldExport(message);
            case BridgeProtocol.TYPE_ACTORS_EXPORT -> handleActorsExport(message);
            case BridgeProtocol.TYPE_ACTORS_IMPORT, BridgeProtocol.TYPE_ACTORS_IMPORT_RECEIPT, "ACTORS_PROJECT_OPEN", "ACTORS_LEASE" -> handleActorsImport(message);
            case BridgeProtocol.TYPE_REPLAY_RECORD_CONTROL ->
                com.blenderbridge.actors.ReplayRecordingControl.handle(message);
            case "FILM_STRUCTURE_IMPORT" ->
                com.blenderbridge.actors.FilmStructureImporter.handle(message, STATE);
            /* Task 4.3b: ENTITY_BAKE dispatches straight into the new com.blenderbridge.entity
             * package — same body-only pattern as detectBbsCameraGrab()'s CameraAuthority calls
             * above, no new method added to this (already-loaded) class. */
            case BridgeProtocol.TYPE_ENTITY_BAKE -> com.blenderbridge.entity.EntityBakeHandler.handle(message);
            /* Task 1.3: FILM_CAMERA_EXPORT — raw string case (no BridgeProtocol.TYPE_* constant;
             * see com.blenderbridge.camera.FilmCameraExporter's class-doc "MESSAGE WIRING" for why).
             * Same body-only pattern as the ENTITY_BAKE case above: FilmCameraExporter is a brand
             * new, lazy-loadable class, this switch merely widens by one more case. */
            case "FILM_CAMERA_EXPORT" -> com.blenderbridge.camera.FilmCameraExporter.handle(message);
            /* Task 1.4: PLAYHEAD — raw string case (no BridgeProtocol.TYPE_* constant; see
             * com.blenderbridge.film.PlayheadSync's class-doc "OWNER MODEL" for why). Same
             * body-only pattern as the FILM_CAMERA_EXPORT case above: brand new, lazy-loadable
             * class, this switch merely widens by one more case. */
            case "PLAYHEAD" -> com.blenderbridge.film.PlayheadSync.handle(message);
            case "MARKERS_WRITE" -> com.blenderbridge.film.MarkerSync.handle(message, STATE);
            /* Task 4.3a: REPLAY_POSE_EXPORT — raw string case (no BridgeProtocol.TYPE_* constant;
             * see com.blenderbridge.entity.ReplayPoseReader's class-doc "WHY A NEW, SELF-CONTAINED
             * CLASS" for why). Same body-only pattern as FILM_CAMERA_EXPORT/PLAYHEAD above: brand
             * new, lazy-loadable class, this switch merely widens by one more case. Unlike
             * ENTITY_BAKE, this answers synchronously within dispatch — no onClientTick drain needed
             * (a replay's whole performance already exists as keyframes; nothing to wait for). */
            case "REPLAY_POSE_EXPORT" -> com.blenderbridge.entity.ReplayPoseReader.handle(message);
            /* Task 4.1 wire-anschluss (W7-B1): ENTITY_EXPORT — raw string case (no BridgeProtocol
             * .TYPE_* constant; see com.blenderbridge.entity.EntityExportBridge's class doc). Same
             * body-only pattern as REPLAY_POSE_EXPORT above: brand new, lazy-loadable class, this
             * switch merely widens by one more case. Answers synchronously within dispatch (one
             * mob's OBJ/PNG/rig.json — bounded, same order of work as the alt-pfad world export). */
            case "ENTITY_EXPORT" -> com.blenderbridge.entity.EntityExportBridge.handle(message);
            /* Task 6.2: CAMERA_CLIP_WRITE — the INVERSE of FILM_CAMERA_EXPORT (Blender's baked
             * camera fahrt -> BBS camera-clips, so BBS itself renders the sequence, "renderfahrt"
             * weg B). Raw string case (no BridgeProtocol.TYPE_* constant; see CameraClipWriter's
             * class-doc "MESSAGE WIRING"). Like ACTORS_IMPORT it WRITES live film state, so STATE is
             * passed for the echo guard; CameraClipWriter is a brand new, lazy-loadable class, this
             * switch merely widens by one more case. */
            case "CAMERA_CLIP_WRITE" -> com.blenderbridge.camera.CameraClipWriter.handle(message, STATE);
            /* Phase 4: LIGHTS_IMPORT — Blender lights -> BBS LightForm actors (the new portable
             * Blender->BBS sync; a LightForm only emits via LambDynLights when it is an actor's form,
             * so this creates one LightForm replay per Blender light). Raw string case (no
             * BridgeProtocol.TYPE_* constant; see LightImporter's class-doc "MESSAGE WIRING"). Like
             * ACTORS_IMPORT/CAMERA_CLIP_WRITE it WRITES live film state, so STATE is passed for the
             * echo guard; LightImporter is a brand new, lazy-loadable class, this switch merely
             * widens by one more case. */
            case "LIGHTS_IMPORT" -> com.blenderbridge.actors.LightImporter.handle(message, STATE);
            /* Weg A (P7): Antwort des Addons auf unseren RENDER-Auftrag — loest die wartende
             * /render_composite-Route auf. Neue lazy Klasse, der Switch weitet nur um einen Fall. */
            case "RENDER_RESULT" -> com.blenderbridge.render.RenderComposite.handleResult(message);
            default -> LOGGER.debug("[blenderbridge] ignoring unknown message type '{}'", type);
        }
    }

    /**
     * Handle an EXEC request: pull the code string, run it IN-JVM via {@link BridgeExec#eval(String)},
     * and send the structured result back as an EXEC_RESULT.
     *
     * <p>Runs on the MAIN thread (dispatch is in the client tick) — which is exactly where
     * {@link BridgeExec#eval} must run, since the snippet touches BBS/MC state and JShell is
     * single-threaded. The gating (dev-only) lives inside {@code BridgeExec.eval}, so a disabled
     * EXEC still returns a well-formed {@code {"ok":false,"error":"exec disabled"}} the caller can
     * read.</p>
     */
    private static void handleExec(JsonObject message)
    {
        JsonObject fields = BridgeProtocol.getFields(message);
        String code = BridgeProtocol.getFieldString(fields, BridgeProtocol.EXEC_CODE, null);

        /* eval() itself handles null/empty code and the disabled case, returning a well-formed result. */
        JsonObject payload = BridgeExec.eval(code);

        /* ⛔ WER HAT GEFRAGT? OHNE DAS VERTAUSCHEN ZWEI GLEICHZEITIGE MESSER IHRE ANTWORTEN
         * (2026-08-02 gemessen: 11 von 12 Antworten gehoerten zu einer FREMDEN Anfrage).
         *
         * Der Server sendet jede Antwort an JEDE offene Verbindung (BridgeServer.send), und
         * execResult stempelt UUID_EXEC samt eigener Sequenznummer darauf. Damit trug die
         * Antwort bisher KEIN Feld, das den Fragesteller nennt: wer zeitgleich mit einem
         * anderen misst, liest dessen Ergebnis — vollstaendig, plausibel, und zur falschen
         * Frage. Genau das ist die teuerste Sorte Messfehler in diesem Projekt.
         *
         * Die Client-Seite hatte dafuer eine Notloesung (eine Dateisperre in tools/kanal.py,
         * die alle EXECs hintereinander reiht). Die deckt aber nur, wer ueber kanal.py geht —
         * tools/warte-bis-oben.py und die Blender-Seite sprechen das Protokoll selbst.
         * Hier ist die Ursache, also gehoert der Fix hierher.
         *
         * ⚠️ ZUSATZFELD, KEINE UMBENENNUNG. Die uuid des Umschlags bleibt UUID_EXEC, damit
         * jeder vorhandene Leser unveraendert weiterlaeuft; wer korrelieren will, liest
         * fields.fuer. Ein Client, der das Feld nicht kennt, merkt nichts. */
        String frager = BridgeProtocol.getUuid(message);

        if (payload != null && frager != null && !frager.isEmpty())
        {
            payload.addProperty(BridgeProtocol.EXEC_FUER, frager);
        }

        long seq = STATE.nextOutboundSeq();
        SERVER.send(BridgeProtocol.execResult(seq, payload));
    }

    /**
     * Handle a WORLD_EXPORT request: dump the requested block region to an OBJ/MTL/PNG set and
     * reply with a WORLD_EXPORT_RESULT carrying the absolute {@code objPath} for Blender to import.
     *
     * <p>Runs on the MAIN thread (dispatch is in the client tick), which is required — the export
     * walks the live world and the block-render manager. It is SYNCHRONOUS and stalls the game
     * while it runs; {@code WorldExporter.MAX_VOLUME} (128^3 blocks) bounds that stall, and larger
     * regions come back as an {@code ok:false} error rather than a freeze.</p>
     *
     * <p>No LWW/echo filtering here: an export is an idempotent command with a reply, not a synced
     * value, so there is no feedback loop to suppress.</p>
     *
     * <p>STREAMING (Task 3.1): when {@code fields.streaming=true}, {@code WorldExportBridge.export}
     * delegates to {@code StreamingWorldExporter}, which returns {@code null} on a successful start —
     * that is the signal that IT now owns sending progress (WORLD_EXPORT_PROGRESS, ~1/s) and the
     * eventual WORLD_EXPORT_RESULT itself, asynchronously, over later ticks. Sending a reply here too
     * would be a premature/duplicate result the instant the job starts. A non-null payload (the
     * synchronous alt-pfad, or a streaming job that failed to even START) is sent immediately, exactly
     * as before.</p>
     */
    private static void handleWorldExport(JsonObject message)
    {
        JsonObject fields = BridgeProtocol.getFields(message);

        /* export() handles null/incomplete fields itself and always returns a well-formed payload,
         * EXCEPT for a successfully-started streaming job — see the STREAMING note above. */
        JsonObject payload = WorldExportBridge.export(fields);

        if (payload == null)
        {
            return;
        }

        /* End-review fix 2 (request correlation): echo the request's own requestId into every
         * SYNCHRONOUS reply (alt-path success/failure, streaming start-failure, busy-reject) so the
         * add-on can match the result to ITS request — the envelope itself carries a fresh seq and
         * the constant uuid "world", i.e. no requester identity, and BridgeServer broadcasts to ALL
         * clients. The asynchronous streaming result gets the same echo via ExportCorrelation (see
         * that class's doc). Absent/empty id (old add-on, manual EXEC caller) stamps nothing —
         * byte-identical pre-correlation reply. */
        String requestId = BridgeProtocol.getFieldString(fields, "requestId", "");

        if (!requestId.isEmpty())
        {
            payload.addProperty("requestId", requestId);
        }

        long seq = STATE.nextOutboundSeq();
        SERVER.send(BridgeProtocol.worldExportResult(seq, payload));
    }

    /**
     * Handle an ACTORS_EXPORT request: serialise the open film's replays and reply with an
     * ACTORS_EXPORT_RESULT carrying their keyframe channels for Blender to rebuild.
     *
     * <p>Runs on the MAIN thread (dispatch is in the client tick), which is required — the exporter
     * reads live film/editor state. It is cheap compared to a world export (it walks keyframe lists,
     * touches no world or render state), so it needs no volume guard.</p>
     *
     * <p>No LWW/echo filtering here: an export is an idempotent command with a reply, not a synced
     * value, so there is no feedback loop to suppress. "No film open" is a normal answer and comes
     * back as {@code ok:false}, not as a dropped message.</p>
     */
    private static void handleActorsExport(JsonObject message)
    {
        JsonObject fields = BridgeProtocol.getFields(message);

        /* export() takes no parameters and always returns a well-formed payload. */
        JsonObject payload = ActorBridge.export(fields);

        long seq = STATE.nextOutboundSeq();
        SERVER.send(BridgeProtocol.actorsExportResult(seq, payload));
    }

    /**
     * Handle an ACTORS_IMPORT request: write the Blender-authored keyframe curves in the payload into
     * the open film's replays and reply with an ACTORS_IMPORT_RESULT carrying the applied/skipped
     * counts.
     *
     * <p>Runs on the MAIN thread (dispatch is in the client tick), which is required — the importer
     * mutates live film state and rebuilds the film editor's timeline sheets.</p>
     *
     * <p>ECHO GUARD: unlike the exports around it, this one WRITES. {@code ActorImporter.applyActors}
     * wraps the whole mutation in {@code STATE.withRemoteApply(...)}, so BBS-side change-detection
     * cannot see the values we just received from Blender as a local change and bounce them back.
     * That is why {@link #STATE} is passed down here.</p>
     *
     * <p>No LWW filtering: like the exports, an import is an explicit command with a reply, not a
     * continuously synced value — dropping one as "stale" would silently lose a user's edit. "No film
     * open" comes back as {@code ok:false}, not as a dropped message.</p>
     */
    private static void handleActorsImport(JsonObject message)
    {
        JsonObject fields = BridgeProtocol.getFields(message);

        /* Receipt recovery is a separate read-only command, never a retry of the mutation. */
        JsonObject payload = "ACTORS_LEASE".equals(BridgeProtocol.getType(message))
            ? com.blenderbridge.actors.ActorLeases.handle(fields)
            : "ACTORS_PROJECT_OPEN".equals(BridgeProtocol.getType(message))
            ? com.blenderbridge.actors.ActorProject.open(fields)
            : BridgeProtocol.TYPE_ACTORS_IMPORT_RECEIPT.equals(BridgeProtocol.getType(message))
            ? com.blenderbridge.actors.ActorChannelPatch.recover(fields)
            : com.blenderbridge.actors.ActorLeaseAccess.write(fields,
                BridgeProtocol.getFieldString(message, "leaseToken", ""), () -> ActorBridge.importActors(fields, STATE));

        long seq = STATE.nextOutboundSeq();
        SERVER.send(BridgeProtocol.actorsImportResult(seq, payload));
    }

    /**
     * LWW-filter a CAMERA message and, if accepted, park it for the apply hook. Runs on the
     * main thread. We ignore camera messages that originated from BBS itself (echo).
     */
    private static void handleCamera(JsonObject message)
    {
        if (BridgeProtocol.ORIGIN_BBS.equals(BridgeProtocol.getOrigin(message)))
        {
            /* Our own echo bounced back — never re-apply it. */
            return;
        }

        String uuid = BridgeProtocol.getUuid(message);
        long seq = BridgeProtocol.getSeq(message, Long.MIN_VALUE);

        if (!STATE.shouldApply(uuid, seq))
        {
            /* Stale / out-of-order — drop. */
            return;
        }

        STATE.setPendingCamera(message);
    }

    /* ================================================================= *
     *  HOOK for the camera-apply worker.                                *
     * ================================================================= */

    /**
     * Apply any pending inbound camera delta to BBS. Runs on the MAIN thread every tick.
     *
     * <p>TODO(camera-worker): implement the actual BBS camera read/write. The transport,
     * LWW filtering, echo-suppression flag, and pending-camera hand-off are all done — the
     * remaining work is purely "take the parked envelope and push its fields into the BBS
     * camera" (and, separately, read the BBS camera and {@code SERVER.send(...)} a CAMERA
     * envelope built via {@link BridgeProtocol#camera}).</p>
     *
     * <p>Wire it like this:</p>
     * <pre>
     *   JsonObject cam = STATE.takePendingCamera();
     *   if (cam == null) { return; }
     *   JsonObject fields = BridgeProtocol.getFields(cam);
     *   double x     = BridgeProtocol.getDouble(fields, BridgeProtocol.CAM_X, 0);
     *   double y     = BridgeProtocol.getDouble(fields, BridgeProtocol.CAM_Y, 0);
     *   double z     = BridgeProtocol.getDouble(fields, BridgeProtocol.CAM_Z, 0);
     *   double pitch = BridgeProtocol.getDouble(fields, BridgeProtocol.CAM_PITCH, 0);
     *   double yaw   = BridgeProtocol.getDouble(fields, BridgeProtocol.CAM_YAW, 0);
     *   double roll  = BridgeProtocol.getDouble(fields, BridgeProtocol.CAM_ROLL, 0);
     *   double fov   = BridgeProtocol.getDouble(fields, BridgeProtocol.CAM_FOV, 70);
     *   // Guard with the echo-suppression flag so BBS' change-detection won't re-emit:
     *   STATE.withRemoteApply(() -&gt; {
     *       // ... set the BBS camera from x/y/z/pitch/yaw/roll/fov ...
     *   });
     * </pre>
     */
    private static void applyPendingCamera()
    {
        JsonObject cam = STATE.takePendingCamera();

        if (cam == null)
        {
            return;
        }

        /* CameraBridge.applyCamera wraps the whole write in STATE.withRemoteApply(...), so the
         * echo-suppression flag is raised for the duration of the apply and lowered in a
         * finally. It also arms LiveCameraOverride so the per-frame mixin keeps the Blender
         * pose from being clobbered by the editor/play camera on later frames. */
        JsonObject fields = BridgeProtocol.getFields(cam);

        CameraBridge.applyCamera(fields, STATE);
    }

    /**
     * Read the live BBS camera and, if it changed since the last push (and we are not currently
     * applying a remote value), send it to Blender as a CAMERA envelope. Runs on the MAIN thread
     * every tick.
     *
     * <p>{@link CameraBridge#cameraChanged(BridgeState)} consults the echo-suppression flag and
     * returns false while a Blender value is being applied, so a value we just wrote from Blender
     * is never bounced straight back.</p>
     */
    private static void pushCameraIfChanged()
    {
        if (!CameraBridge.cameraChanged(STATE))
        {
            return;
        }

        JsonObject fields = CameraBridge.readCurrentCamera();

        if (fields == null)
        {
            return;
        }

        long seq = STATE.nextOutboundSeq();
        long tick = BlenderBridgeClient.currentFilmTick();

        JsonObject envelope = BridgeProtocol.camera(
            seq,
            BridgeProtocol.getDouble(fields, BridgeProtocol.CAM_X, 0D),
            BridgeProtocol.getDouble(fields, BridgeProtocol.CAM_Y, 0D),
            BridgeProtocol.getDouble(fields, BridgeProtocol.CAM_Z, 0D),
            BridgeProtocol.getDouble(fields, BridgeProtocol.CAM_PITCH, 0D),
            BridgeProtocol.getDouble(fields, BridgeProtocol.CAM_YAW, 0D),
            BridgeProtocol.getDouble(fields, BridgeProtocol.CAM_ROLL, 0D),
            BridgeProtocol.getDouble(fields, BridgeProtocol.CAM_FOV, 70D),
            tick
        );

        SERVER.send(envelope);
    }

    /**
     * Best-effort current film/playback tick for the outbound CAMERA envelope. Phase 1 does not
     * yet map the editor cursor precisely, so this returns 0 (the envelope's {@code tick} is
     * informational for live sync). TODO: wire to the active film runner's tick when the film
     * panel is focused.
     */
    private static long currentFilmTick()
    {
        return 0L;
    }
}
