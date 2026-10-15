"""Der haupt-tick (bpy.app.timers). Poll-loop: empfaengt/sendet auf dem BBS-socket,
haelt kamera + welt-export + actor-export/live-sync am laufen. Importiert aus allen
anderen modul-teilen -- das ist der einzige ort, der wirklich alles zusammenzieht."""

import bpy
import math
import time

from . import state, evaluation_scope
from . import shot_freeze   # auftrag REDACTED §2: kein poll bei eingefrorenem shot
from .core import (_connect, _poll_recv, _send, _next_seq, _get_sync_camera,
                    _blender_cam_to_bbs, _bbs_to_blender_cam)
from .world_import import _handle_world_export_result
from .actors import _handle_actors_export_result, _handle_actors_push
from .film_camera_import import _handle_film_camera_export_result
from . import replay_anim
from . import world_delta
from . import entity_import
from . import actor_export
from . import camera_clip_write
from . import light_export
from . import recording_live
from . import film_structure
from . import marker_sync
from . import actor_outbox, actor_leases, actor_authoring, actor_pose_preview
from . import camera_preview

_auto_playhead_endpoint = None
_auto_playhead_deadline = 0.0


def _screen_playing():
    screen = bpy.context.screen
    return bool(screen is not None and getattr(screen, "is_animation_playing", False))


def _start_playback():
    return bpy.ops.screen.animation_play()


def _stop_playback():
    return bpy.ops.screen.animation_cancel(restore_frame=False)


def _preview_range(scene):
    fps = scene.render.fps / (scene.render.fps_base or 1.0)
    enabled = bool(scene.use_preview_range)
    if not enabled:
        return False, 0, 0
    start = int(round(scene.frame_preview_start * 20.0 / fps))
    end = int(math.ceil((scene.frame_preview_end + 1) * 20.0 / fps))
    return True, max(0, start), max(start + 1, end)


def _exact_nonnegative_integer(value):
    return value if type(value) is int and value >= 0 else None


def _apply_playhead(fields):
    """Apply one BBS playhead sample without echoing frame or transport changes."""
    state._playhead_bbs_playing = bool(fields.get("playing", False))
    state._playhead_applying_remote = True
    try:
        if "tick" in fields:
            scene = bpy.context.scene
            fps = scene.render.fps / (scene.render.fps_base or 1.0)
            frame = round(fields["tick"] * fps / 20.0)
            state._playhead_last_remote_frame = frame
            scene.frame_set(frame)
            state._playhead_apply_count += 1
        if type(fields.get("loopEnabled")) is bool:
            enabled = fields["loopEnabled"]
            minimum = _exact_nonnegative_integer(fields.get("loopMin"))
            maximum = _exact_nonnegative_integer(fields.get("loopMax"))
            if minimum is not None and maximum is not None and maximum >= minimum:
                scene = bpy.context.scene
                state._playhead_bbs_loop = (enabled, minimum, maximum)
                scene.use_preview_range = enabled and maximum > minimum
                if scene.use_preview_range:
                    fps = scene.render.fps / (scene.render.fps_base or 1.0)
                    scene.frame_preview_start = int(round(minimum * fps / 20.0))
                    scene.frame_preview_end = max(
                        scene.frame_preview_start,
                        int(math.ceil(maximum * fps / 20.0)) - 1)
        playing = fields.get("playing")
        if type(playing) is bool:
            try:
                current = _screen_playing()
                if playing and not current:
                    _start_playback()
                elif not playing and current:
                    _stop_playback()
            except RuntimeError:
                pass  # No Screen context in background Blender; frame sync still applies.
    finally:
        state._playhead_applying_remote = False


def _push_playhead(scene, playing=None):
    if (evaluation_scope.active() or state._playhead_applying_remote or not state._connected
            or state.playhead_owner != "BLENDER"):
        return False
    fps = scene.render.fps / (scene.render.fps_base or 1.0)
    tick = int(round(scene.frame_current * 20.0 / fps))
    if playing is None:
        playing = _screen_playing()
    loop_enabled, loop_min, loop_max = _preview_range(scene)
    _send({"type": "PLAYHEAD", "origin": "blender", "uuid": "playhead", "seq": _next_seq(),
           "fields": {"tick": tick, "playing": bool(playing), "owner": "BLENDER",
                      "loopEnabled": loop_enabled, "loopMin": loop_min, "loopMax": loop_max}})
    state._playhead_push_count += 1
    state._playhead_last_preview_sent = (loop_enabled, loop_min, loop_max)
    return True


def _push_local_playhead(scene, playing=None):
    """Give a local scrub/playback a short lease without locking out BBS input."""
    global _auto_playhead_endpoint, _auto_playhead_deadline
    if (evaluation_scope.active() or state._playhead_applying_remote
            or not state._connected or not state._peer_ready
            or not state.bound_film or 'playhead' not in state.peer_capabilities):
        return False
    if getattr(scene, 'bbsbridge_drive_timeline', False):
        return _push_playhead(scene, playing)
    if state._playhead_bbs_playing and state.playhead_owner != 'BLENDER':
        return False
    endpoint = tuple(state._connected_endpoint or ())
    if _auto_playhead_endpoint is not None and _auto_playhead_endpoint != endpoint:
        _auto_playhead_endpoint = None
    if state.playhead_owner == 'BLENDER' and _auto_playhead_endpoint is None:
        # An explicit owner or the pose preview already controls this cursor.
        return False
    if state.playhead_owner != 'BLENDER':
        state.playhead_owner = 'BLENDER'
        _auto_playhead_endpoint = endpoint
    _auto_playhead_deadline = time.monotonic() + 0.6
    return _push_playhead(scene, playing)


def _release_auto_playhead():
    global _auto_playhead_endpoint
    if _auto_playhead_endpoint is None:
        return
    endpoint = _auto_playhead_endpoint
    if not state._connected or tuple(state._connected_endpoint or ()) != endpoint:
        _auto_playhead_endpoint = None
        return
    if getattr(bpy.context.scene, 'bbsbridge_drive_timeline', False):
        _auto_playhead_endpoint = None
        return
    if (time.monotonic() < _auto_playhead_deadline or _screen_playing()
            or actor_pose_preview._active):
        return
    _auto_playhead_endpoint = None
    if state.playhead_owner == 'BLENDER':
        state.playhead_owner = 'NOBODY'
        _send({'type': 'PLAYHEAD', 'origin': 'blender', 'uuid': 'playhead',
               'seq': _next_seq(), 'fields': {'owner': 'NOBODY'}})


def _pump_preview_range():
    if state._connected and state.playhead_owner == "BLENDER":
        current = _preview_range(bpy.context.scene)
        if current != state._playhead_last_preview_sent:
            _push_playhead(bpy.context.scene)


def _probe_missing_project(on_hello=False):
    """Refresh a bound document on HELLO; keep probing while the world identity is absent."""
    if not state._connected or not state._peer_ready or not actor_outbox.bound():
        return
    if not on_hello and (state.peer_project or time.monotonic() < state._context_probe_next):
        return
    state._context_probe_next = time.monotonic() + 1.0
    # Do not set import/poll flags: a reply uses the incremental push path, and
    # accept_snapshot still rejects foreign projects before adopting their identity.
    _send({'type': 'ACTORS_EXPORT', 'origin': 'blender', 'uuid': 'actors',
           'seq': _next_seq(), 'fields': {}})


def _tick():
    if evaluation_scope.active():
        return 0.05
    from . import model_adoption
    model_adoption.pump()
    from . import world_refresh
    world_refresh.pump()
    actor_leases.pump()
    actor_outbox.pump()
    actor_authoring.pump()
    marker_sync.pump()
    actor_export._check_timeout()
    if not state._connected:
        _release_auto_playhead()
        _connect()
        return 1.0   # jede sekunde reconnect versuchen

    _probe_missing_project()

    # 1. eingehende nachrichten anwenden
    for msg in _poll_recv():
        mtype = msg.get("type")
        if mtype == "PING":
            _send({"type": "PONG", "origin": "blender", "seq": _next_seq(), "fields": {}})
        elif mtype == "HELLO":
            hello_fields = msg.get("fields") or {}
            try:
                state.peer_protocol = int(hello_fields.get("protocol") or 0)
            except (TypeError, ValueError):
                state.peer_protocol = 0
            state.peer_version = str(hello_fields.get("version") or "")
            state.peer_project = str(hello_fields.get("project") or "")
            state.peer_engine = str(hello_fields.get("engine") or "")
            capabilities = hello_fields.get("capabilities")
            state.peer_capabilities = set(capabilities) if isinstance(capabilities, list) else set()
            state._initial_camera_pending = True
            # task 1.0: BBS antwortet auf unsere connect-HELLO mit der stabilen id des offenen
            # films (java BlenderBridgeClient HELLO-case -> FilmIdentity.currentFilmId()). Daran
            # binden wir uns: writes werden mit dieser id getaggt, ein spaeterer filmwechsel wird
            # erkannt (actors.check_film) und der sync pausiert BEVOR ein write in den falschen
            # film geht. Leere id (kein film offen bei der HELLO) -> nichts binden, die erste
            # ACTORS_EXPORT_RESULT bindet nach (die traegt dieselbe id).
            from .actors import check_film
            check_film(hello_fields.get("film", ""))
            state._peer_ready = True
            # 2026-08-16: BBS nennt im HELLO seinen assets-baum — damit stirbt die
            # handgepflegte, instanzgebundene BBS_ASSETS_DIR-konstante (war nach dem
            # Linux-umzug ein stiller windows-pfad, modelle unauffindbar).
            _assets = hello_fields.get("assets", "")
            if _assets:
                state.BBS_ASSETS_DIR = _assets
                print("[bbs-bridge] assets-baum von BBS uebernommen: %s" % _assets)
            # A crash can precede observation persistence. A known project does not
            # imply that this saved .blend has seen the peer's current actor state.
            _probe_missing_project(on_hello=True)
        elif mtype == "CAMERA_PREVIEW_STATUS":
            camera_preview.handle(msg.get('fields') or {})
        elif mtype == "CAMERA":
            initial_camera = state._initial_camera_pending
            state._initial_camera_pending = False
            if camera_preview.enabled() and not initial_camera:
                continue
            uuid = msg.get("uuid", "camera")
            seq = msg.get("seq", 0)
            # LWW: ältere seq als zuletzt angewandt -> verwerfen
            if state._last_seq_in.get(uuid, -1) >= seq:
                continue
            state._last_seq_in[uuid] = seq
            cam = _get_sync_camera()
            if cam is not None:
                _bbs_to_blender_cam(cam, msg["fields"])
                # ECHO-FIX (2026-07-16): adopt the applied pose as our own push baseline.
                # Without this the next tick sees the just-applied BBS pose as a LOCAL change
                # (because state._last_cam_sent still holds the old value) and pushes it back to BBS,
                # which re-applies it with float drift and pushes again -> the camera jitters
                # left/right for 30-60s. state._applying_remote alone only covers the apply itself,
                # not the tick after it. Re-reading the camera here (instead of trusting the
                # incoming fields) also absorbs Blender's own float rounding, so the comparison
                # in step 2 is exact. (state._last_cam_sent lives in state.py -- a plain attribute
                # write, no `global` declaration needed here or anywhere else.)
                state._last_cam_sent = _blender_cam_to_bbs(cam)
        elif mtype == "CAMERA_OWNER":
            # explizite besitz-uebergabe (owner-modell, state.cam_owner-header). Java sendet
            # das nach dem boot-fenster, wenn BBS-user-input (orbit-controller) oder eine
            # HELLO den besitz nimmt/reset. Kein LWW/seq-gate noetig: reiner besitz-flag-
            # broadcast, kein bewegungs-stream mit zwischenwerten die reordering vertragen muessten.
            owner = (msg.get("fields") or {}).get("owner")
            if owner not in ("NOBODY", "BBS", "BLENDER"):
                print("[bbs-bridge] CAMERA_OWNER: unbekannter owner-wert %r -> NOBODY" % (owner,))
                owner = "NOBODY"
            state.cam_owner = owner
        elif mtype == "WORLD_EXPORT_RESULT":
            # kein LWW/seq-gate: das ist eine antwort auf genau EINEN request, kein state-stream.
            _handle_world_export_result(msg.get("fields") or {})
        elif mtype == "ACTORS_EXPORT_RESULT":
            # wie oben kein seq-gate -- aber ANDERS als WORLD_EXPORT_RESULT gibt es hier seit
            # task 1.6 ZWEI moegliche absender: unsere eigene anfrage (button ODER der alte
            # 0.5s-poll-fallback, state._actors_pending/_live_polling dann True) — das ist die
            # ANTWORT auf EINEN request wie eh und je — ODER BBS' java-seitiges
            # ActorPushTracker, das sich UNGEFRAGT meldet weil sich der film geaendert hat
            # (beide flags False). Letzteres MUSS durch den live-sync-pfad, nie durch den
            # vollimport-zweig von _handle_actors_export_result (der wuerde bei jedem push
            # saemtliche meshes neu importieren).
            f = msg.get("fields") or {}
            if not actor_outbox.accept_snapshot(f):
                state._live_status = "durable project mismatch; actor snapshot paused"
                continue
            state.peer_project = str(f.get("project") or "")
            state.peer_engine = str(f.get("engine") or "")
            film_structure.remember_replays(f.get("replays"))
            if state._actors_pending or state._live_polling:
                _handle_actors_export_result(f)
            else:
                _handle_actors_push(f)
        elif mtype == "ACTORS_IMPORT_RESULT":
            # Correlation lives in the handler; a matching receipt may arrive after timeout.
            if not actor_pose_preview.handle(msg.get("fields") or {}) and not actor_leases.handle(msg.get("fields") or {}) and not actor_outbox.handle_result(msg.get("fields") or {}):
                actor_export._handle_actors_import_result(msg.get("fields") or {})
        elif mtype == "FILM_CAMERA_EXPORT_RESULT":
            # task 1.3: raw string type, kein seq-gate -- antwort auf GENAU EINEN request,
            # kein state-stream (gleiche haltung wie WORLD_EXPORT_RESULT/ACTORS_EXPORT_RESULT).
            _handle_film_camera_export_result(msg.get("fields") or {})
        elif mtype == "REPLAY_POSE_EXPORT_RESULT":
            # task 4.3a-rest: raw string type (kein neuer BridgeProtocol.TYPE_*, siehe java-
            # klassendoc ReplayPoseReader "WHY A NEW, SELF-CONTAINED CLASS"), kein seq-gate --
            # SOLICITED, antwort auf GENAU EINEN von uns gestellten REPLAY_POSE_EXPORT-request
            # (anders als ACTORS_EXPORT_RESULT gibt es hier keinen unsolicited-push-zweig,
            # java sendet das nie von sich aus). Trotzdem gated auf replay_anim._pending --
            # dieselbe solicited-schutzhaltung wie world_import._world_pending (dossier-
            # merkregel bb20355: die java-bridge broadcastet results an ALLE clients, ein
            # fremder agent-request wuerde sonst unsere szene mit dessen replay ueberschreiben).
            if replay_anim._pending:
                replay_anim._handle_replay_pose_export_result(msg.get("fields") or {})
        elif mtype == "ENTITY_EXPORT_RESULT":
            # task 4.1 (W7-B1): raw string type, SOLICITED, requestId-korreliert. Anders als
            # der replay_anim-zweig oben liegt das gate IM modul (der id-vergleich braucht
            # die fields): nur das result mit der EIGENEN requestId wird angewandt, fremde
            # results lassen pending+deadline unangetastet, ohne laufenden request wird
            # alles ignoriert (dossier-merkregel bb20355/ce2d139 — von ANFANG AN verbaut,
            # kein id-loser fallback noetig weil es diesen kanal nie ohne korrelation gab).
            entity_import._handle_entity_export_result(msg.get("fields") or {})
        elif mtype == "LIGHTS_IMPORT_RESULT":
            # phase 4 (light_export): antwort auf UNSEREN lichter-send. SOLICITED, gated +
            # requestId-korreliert IM modul (prueft _pending + requestId selbst). 2026-08-16
            # nachverdrahtet: der fall FEHLTE — das modul kam aus dem umzugs-backup zurueck,
            # seine tick-verdrahtung existierte in diesem baum nie (result fiel still durch,
            # pending blieb bis zum timeout haengen — und der timeout-hook fehlte AUCH, s.u.).
            light_export._handle_lights_import_result(msg.get("fields") or {})
        elif mtype == "CAMERA_CLIP_WRITE_RESULT":
            # task 6.2 (R62): antwort auf UNSEREN camera_clip_write-sender (Blender-cam-fahrt ->
            # BBS-film, damit BBS den 2. render-pfad fährt). SOLICITED, gated + requestId-korreliert
            # IM modul (_handle_camera_clip_write_result prüft _pending + requestId selbst, wie
            # ENTITY_EXPORT_RESULT). Handler wirft nie (timer-regel).
            camera_clip_write._handle_camera_clip_write_result(msg.get("fields") or {})
        elif mtype == "BLOCK_DELTA":
            # task 3.6 (live-block-delta): unsolicited PUSH von java BlockDeltaTracker — KEIN
            # pending-gate (es gibt keinen request), der schutz ist das REGION-GATE im modul
            # (nur pushes fuer die region des EIGENEN letzten imports werden angewandt, siehe
            # world_delta-modul-doc). Handler faengt intern jede exception (timer-regel).
            world_delta.handle_block_delta(msg.get("fields") or {})
        elif mtype == "RENDER":
            # WEG A (P7): BBS stoesst den composite-render an. Blockt bis fertig (render_bridge-
            # modul-header) — der handler faengt intern jede exception und antwortet IMMER.
            from . import render_bridge
            render_bridge.handle_render(msg.get("fields") or {})
        elif mtype == "REPLAY_RECORD_CONTROL_RESULT":
            recording_live.handle_control_result(msg.get("fields") or {})
        elif mtype == "REPLAY_RECORD_START":
            recording_live.handle_start(msg.get("fields") or {})
        elif mtype == "REPLAY_RECORD_FRAME":
            recording_live.handle_frame(msg.get("fields") or {})
        elif mtype == "REPLAY_RECORD_STOP":
            recording_live.handle_stop(msg.get("fields") or {})
        elif mtype == "FILM_STRUCTURE_IMPORT_RESULT":
            model_adoption.handle_result(msg.get("fields") or {})
            film_structure.handle_result(msg.get("fields") or {})
        elif mtype == "PLAYHEAD":
            # Java suppresses new BBS samples while Blender owns the playhead.
            # Discard a sample already in flight when a local scrub just claimed it.
            if state.playhead_owner != 'BLENDER':
                _apply_playhead(msg.get("fields") or {})
        elif mtype == "MARKERS_SYNC":
            uuid = msg.get("uuid", "markers")
            seq = msg.get("seq", 0)
            if state._last_seq_in.get(uuid, -1) >= seq:
                continue
            state._last_seq_in[uuid] = seq
            marker_sync.handle_snapshot(msg.get("fields") or {}, seq)
        elif mtype == "MARKERS_WRITE_RESULT":
            marker_sync.handle_result(msg.get("fields") or {}, msg.get("seq"))

    # 1b. verlorenen export freigeben (sonst bleibt der import-button für immer grau)
    if state._world_pending and time.time() > state._world_deadline:
        state._world_pending = False
        state._world_status = "export timed out"
        print("[bbs-bridge] WORLD_EXPORT timed out (no result after %ds)" % state.WORLD_EXPORT_TIMEOUT)
    if state._actors_pending and time.time() > state._actors_deadline:
        state._actors_pending = False
        state._actors_status = "export timed out"
        print("[bbs-bridge] ACTORS_EXPORT timed out (no result after %ds)" % state.ACTORS_EXPORT_TIMEOUT)
    if state._film_cam_pending and time.time() > state._film_cam_deadline:
        state._film_cam_pending = False
        state._film_cam_status = "export timed out"
        print("[bbs-bridge] FILM_CAMERA_EXPORT timed out (no result after %ds)" % state.FILM_CAM_EXPORT_TIMEOUT)
    replay_anim._check_timeout()   # task 4.3a-rest: eigenes modul-lokales pending/deadline (siehe replay_anim.py)
    entity_import._check_timeout()   # task 4.1 (W7-B1): dito, modul-lokal (siehe entity_import.py)
    actor_export._check_timeout()   # W9-B1 (ACTORS_IMPORT-sender): dito, modul-lokal (siehe actor_export.py)
    camera_clip_write._check_timeout()   # task 6.2 (R62): camera-clip-write deadline, modul-lokal
    light_export._check_timeout()   # phase 4: lichter-send deadline (2026-08-16 nachverdrahtet, s. dispatch)
    recording_live.check_timeout()
    film_structure.check_timeout()
    # WEG A selbsteinrichtung (render_bridge-zustandsmaschine): phasen je tick vorantreiben.
    from . import render_bridge
    render_bridge.fortschritt()

    # 2. eigene kamera-änderung nach BBS pushen (dirty-check, echo-schutz)
    # HARTES GATE (owner-modell, 2026-07-16): nur pushen wenn state.cam_owner == "BLENDER"
    # (gesetzt EINZIG vom "Drive BBS camera"-toggle, siehe ui._on_drive_camera_toggle). Das
    # versiegelt den ur-bug blender-seitig -- Blender kann die BBS-kamera nie mehr ungefragt
    # anfassen, unabhaengig davon ob/wann die java-seite CAMERA_OWNER selbst auswertet.
    if not state._applying_remote:
        cam = _get_sync_camera()
        if cam is not None:
            fields = _blender_cam_to_bbs(cam)
            if not state._cam_adopted:
                # ERSTER TICK NACH VERBINDUNG: NICHT pushen. UNGEGATED (bleibt wie vor dem
                # owner-modell) -- wir muessen BBS' start-kamera immer als baseline uebernehmen,
                # sonst sieht der allererste dirty-check (sobald wir spaeter besitzer werden)
                # Blenders zufaellige boot-pose als "geaendert" statt nur echter aenderungen
                # SEIT der uebernahme.
                # GEMESSEN 2026-07-16 (user: "kamera in bbs komplett stuck"): state._last_cam_sent
                # startete als None, also galt jede Blender-kamera als "dirty" und wurde sofort
                # nach BBS geschickt — Blenders beliebige startposition ueberschrieb damit die
                # film-kamera des users. Er bewegte sie, wir knallten den alten wert zurueck.
                # BBS ist beim start die WAHRHEIT (dort steht der film): erst dessen wert als
                # baseline uebernehmen, danach nur noch echte Blender-aenderungen pushen.
                # Spiegelbild von CameraBridge.hasBaseline auf der java-seite.
                state._last_cam_sent = fields
                state._cam_adopted = True
            elif state.cam_owner == "BLENDER" and fields != state._last_cam_sent:
                state._last_cam_sent = fields
                _send({"type": "CAMERA", "origin": "blender", "uuid": "camera",
                       "seq": _next_seq(), "fields": fields})

    # 3. LIVE-ACTORS POLL-FALLBACK (task 1.6): seit ActorPushTracker (java) pusht BBS von SICH
    #    AUS bei jeder aenderung (schritt 1, ACTORS_EXPORT_RESULT-zweig oben) -- der 0.5s-poll
    #    hier laeuft nur noch an, wenn seit PUSH_STALE_AFTER kein push ankam (aeltere BBS ohne
    #    den tracker, oder der push-pfad haengt). Solange push lebt waere ein paralleler poll
    #    nur eine zweite quelle fuer dieselben daten.
    #    Nur EIN request gleichzeitig (state._actors_pending) — sonst stapeln sich bei einem langsamen
    #    export die anfragen und jede antwort baut die F-curves erneut.
    # bpy.context.scene direkt: _tick hat keine lokale 'scene' (die gibt es nur in _build_actors)
    push_stale = (time.time() - state._push_last_seen) >= state.PUSH_STALE_AFTER
    if (state._connected and not state._actors_pending and push_stale
            and getattr(bpy.context.scene, "bbsbridge_live_actors", False)
            and not shot_freeze.ist_eingefroren(bpy.context.scene)):   # §2: eingefroren = kein poll
        now = time.time()
        if now >= state._live_next_poll:
            state._live_next_poll = now + state.LIVE_ACTORS_INTERVAL
            state._live_polling = True
            state._actors_pending = True
            state._actors_deadline = now + state.ACTORS_EXPORT_TIMEOUT
            _send({"type": "ACTORS_EXPORT", "origin": "blender", "uuid": "actors",
                   "seq": _next_seq(), "fields": {}})

    _pump_preview_range()
    from .actors import _pump_deferred_actors
    _pump_deferred_actors()
    actor_pose_preview.pump()
    camera_preview.pump()
    # Drain in-flight BBS samples while Blender still owns the cursor, then
    # release. Otherwise an old sample can undo the last local scrub.
    _release_auto_playhead()

    return 0.05   # ~20 hz poll (matcht BBS 20 tps)


def _on_frame_change_post(scene, depsgraph=None):
    """bpy.app.handlers.frame_change_post (task 1.4) -- fires whenever the scene's CURRENT
    frame actually changes (scrub, playback advancing, or a script's frame_set()), registered/
    unregistered in __init__.py alongside the other timer/handler hookups.

    A local scrub or playback step claims BLENDER ownership briefly. Remote BBS
    frame_set calls are guarded by _playhead_applying_remote, so they never claim it.
    The explicit Drive timeline toggle keeps ownership until turned off.

    DEFENSIVE (dossier rule, tick.py table row): a bpy.app.handlers callback that raises gets
    the ENTIRE handler list silently deregistered by Blender -- no crash, no visible error, just
    scrubbing/playback quietly stops reaching us. Every access here is guarded; nothing may
    escape this function as an exception.
    """
    try:
        if evaluation_scope.active():
            state._playhead_last_remote_frame = scene.frame_current
            return
        if scene.frame_current == state._playhead_last_remote_frame:
            # Blender can deliver this callback after frame_set() returned, when
            # _playhead_applying_remote has already been cleared.
            return
        state._playhead_last_remote_frame = None
        if state._playhead_applying_remote:
            # echo guard: this frame-change is OUR OWN scene.frame_set from an inbound PLAYHEAD
            # (see the PLAYHEAD branch in _tick above), not a user/script scrub -- must not
            # bounce it straight back out (task 1.4 ECHO-GATE).
            return
        _push_local_playhead(scene)
    except Exception as e:   # noqa: BLE001 -- see docstring: must never escape (kills the handler list)
        print("[bbs-bridge] playhead push (frame_change_post) failed:", e)


def _on_playback_pre(scene, depsgraph=None):
    try:
        _push_local_playhead(scene, True)
    except Exception as exc:  # noqa: BLE001 - handlers must never escape
        print("[bbs-bridge] playback-start push failed:", exc)


def _on_playback_post(scene, depsgraph=None):
    try:
        _push_local_playhead(scene, False)
    except Exception as exc:  # noqa: BLE001 - handlers must never escape
        print("[bbs-bridge] playback-stop push failed:", exc)
