"""TASK 1.3 — film-kamera-clips (JEDER typ: Idle/Dolly/Path/Keyframe/...) -> Blender F-curves,
auf einer EIGENEN neuen kamera "BBS_FilmCam". EINSEITIG (BBS -> Blender); der rueckweg
(Blender-kamerafahrt zurueck nach BBS) ist ein SEPARATER task, hier bewusst nicht begonnen.

BBS' `FilmCameraExporter` (com.blenderbridge.camera, java) sampelt die GESAMTE kamera-clip-
timeline des offenen films dicht pro tick (kein clip-typ-uebersetzen -- es fragt denselben
clip-evaluator den BBS' eigener Runner/Play-controller benutzt) und schickt eine flache
frame-liste `[{tick,x,y,z,pitch,yaw,roll,fov}, ...]`. Hier wird daraus PRO KANAL eine
keyframe-liste gebaut (jeder sample-tick wird ein echter Blender-keyframe, LINEAR interpoliert
-- die dichte sample-rate selbst ist die "kurvenform", siehe plan-v2 task 1.3) und ueber
denselben, bereits gegen 4.4/5.2 slotted actions getesteten mechanismus wie actors.py
(`_build_fcurve`) auf die neue kamera gelegt.

WARUM NICHT die sync-kamera (core._get_sync_camera) oder irgendeine kamera des users: diese
kamera ist reserviert fuer die LIVE single-pose synchronisation (Phase 1) bzw. gehoert dem
user. Ein import hier darf NICHTS anfassen ausser genau diesem einen, klar benannten,
selbst angelegten objekt -- rueckstandslos entfernbar (objekt loeschen genuegt).

Koordinaten-umrechnung = dieselbe inverse wie core._bbs_to_blender_cam / actors._ACTOR_CHANNELS
(COORDINATE_CONTRACT.md): bl_x=bbs_x, bl_y=-bbs_z, bl_z=bbs_y, bl_pitch_deg=90-bbs_pitch,
bl_yaw_deg=-bbs_yaw, bl_roll_deg=bbs_roll. FOV geht NICHT ueber camera.data.angle_y (Blender-
5.2-falle, siehe paket-CLAUDE.md: "driver nur auf camera.data.lens, nicht angle") sondern wird
pro sample in `lens` (mm) umgerechnet und dort gekeyframt -- deterministisch, weil diese kamera
NUR uns gehoert und wir sensor_fit/sensor_height selbst festnageln.

TASK 1.3b (entity-tracking im offline-sampler): java loest tracking-clips (Look/Orbit/Tracker)
jetzt offline auf (OfflineEntityResolver speist die film-replays in den CameraClipContext) und
schickt zusaetzlich `warnings: [{type,tickStart,tickEnd,reason}]` fuer alles was offline (oder
live!) NICHT aufloesbar ist (kein/deaktiviertes ziel, riding-ziele, werfender clip). Anzeige:
suffix an state._film_cam_status (die zeile die ui.py schon rendert -- ui.py/state.py sind
fremdes flotten-territorium, siehe _warning_suffix) + volle liste in der konsole. Der import
laeuft trotz warnungen normal (stufe-1-vertrag: warnen, nie abbrechen). Ausserdem traegt der
handler seither das solicited-gate (bb20355-disziplin, siehe docstring dort).

W8-B4 (requestId-korrelation): zusaetzlich zum solicited-gate wird jedes result gegen die
EIGENE request-id abgeglichen (ce2d139-muster wie WORLD_EXPORT/ENTITY_EXPORT) — ui.py schickt
fields.requestId (via new_request_id()), java echot sie, fremde results laufen ins log statt
auf BBS_FilmCam. Details am _request_id-header unten.
"""

import bpy
import json
import math

from . import state
from .actors import _ACTOR_CHANNELS, _build_fcurve, _find_fcurve, _apply_kf_shape, _kf_float

_DEG = math.pi / 180.0

BBS_FILM_CAM_NAME = "BBS_FilmCam"

# Blender-default (36x24mm sensor -> 24mm sensor_height). Wir legen sensor_fit/sensor_height
# beim ANLEGEN fest und ruehren sie danach nie wieder an -- das macht lens<->vfov eindeutig
# umkehrbar, ohne auf irgendeine vom user evtl. veraenderte kamera-einstellung angewiesen zu sein.
FILM_CAM_SENSOR_HEIGHT = 24.0

# Objekt-kanaele: x/y/z/pitch/yaw sind das IDENTISCHE mapping wie bei actors (dieselbe
# COORDINATE_CONTRACT-achse/winkel-umrechnung) -- hier NUR um "roll" ergaenzt, das actor-
# replays gar nicht senden. dict(...) kopiert, damit wir NIE das actors.py-original mutieren.
_CAM_OBJECT_CHANNELS = {key: _ACTOR_CHANNELS[key] for key in ('x', 'y', 'z', 'yaw')}
# ⛔ KAMERA-pitch hat +90°-const (bl_x = 90 - bbs_pitch, core._bbs_to_blender_cam) — das
# actor-mapping (const 0) gilt fuer BOBJ-figuren, deren daten schon Z-up konvertiert sind.
# Die blanke dict-kopie uebernahm const 0: BBS_FilmCam schaute 90° zu tief (gemessen
# 2026-08-16: BBS pitch 10° -> bl_x -10° statt 80°, modelle nie im Blender-frustum).
_CAM_OBJECT_CHANNELS["pitch"] = ("rotation_euler", 0, -_DEG, 90.0 * _DEG)
_CAM_OBJECT_CHANNELS["roll"] = ("rotation_euler", 1, _DEG, 0.0)   # bl_roll_deg = bbs_roll (core.py)


def _find_or_create_film_cam():
    """BBS_FilmCam holen oder frisch anlegen. NIE die sync-/user-kamera anfassen -- eigener,
    klar benannter, jederzeit rueckstandslos loeschbarer datenblock."""
    obj = bpy.data.objects.get(BBS_FILM_CAM_NAME)
    if obj is not None and obj.type == 'CAMERA' and obj.data is not None:
        return obj
    cam_data = bpy.data.cameras.new(BBS_FILM_CAM_NAME)
    cam_data.sensor_fit = 'VERTICAL'
    cam_data.sensor_height = FILM_CAM_SENSOR_HEIGHT
    obj = bpy.data.objects.new(BBS_FILM_CAM_NAME, cam_data)
    obj.rotation_mode = 'XYZ'      # MUSS vor rotation_euler-keyframes stehen (wie actors.py)
    bpy.context.scene.collection.objects.link(obj)
    print("[bbs-bridge] created %s" % BBS_FILM_CAM_NAME)
    return obj


def _clear_film_cam_animation(obj):
    """Alte action(s) weg (objekt- UND kamera-daten-seite) bevor neu gebaut wird -- re-import
    ERSETZT, stapelt nicht. Gleiche fake-user-hygiene wie actors._clear_actors_collection,
    nur ohne collection-drumherum (ein einzelnes objekt)."""
    for holder in (obj, obj.data):
        ad = getattr(holder, "animation_data", None)
        if ad is None or ad.action is None:
            continue
        act = ad.action
        holder.animation_data_clear()
        try:
            act.use_fake_user = False
            if act.users == 0:
                bpy.data.actions.remove(act)
        except (ReferenceError, RuntimeError):
            pass


def _lens_for_vfov_deg(vfov_deg, sensor_height):
    """Blenders eigene angle_y<->lens-beziehung bei sensor_fit='VERTICAL' umgekehrt:
    angle_y = 2*atan(sensor_height / (2*lens))  ->  lens = sensor_height / (2*tan(angle_y/2)).
    Geklemmt gegen entartete winkel (0 deg/180 deg) -- ein kaputter sample-wert darf den
    ganzen import nicht mit einer ZeroDivisionError killen."""
    half = math.radians(vfov_deg) / 2.0
    half = min(max(half, math.radians(0.01)), math.radians(179.9))
    return sensor_height / (2.0 * math.tan(half))


def _build_scalar_fcurve(target, keyframes, data_path, tick_scale):
    """Wie actors._build_fcurve, aber fuer eine SKALARE property (kein index/subscript) --
    `lens` ist ein einzelner float, nicht location[i]/rotation_euler[i]. Selbe technik
    (keyframe_insert() ueber die echte API legt action+slot 4.4/5.2-korrekt an) und dieselbe
    _apply_kf_shape fuer interpolation -- importiert aus actors.py, kein doppelter code."""
    pts = []
    seen = set()
    for kf in sorted(keyframes, key=lambda k: _kf_float(k, "tick", 0.0)):
        if not isinstance(kf, dict):
            continue
        frame = _kf_float(kf, "tick", 0.0) * tick_scale
        marker = round(frame, 4)
        if marker in seen:
            continue
        seen.add(marker)
        pts.append((frame, _kf_float(kf, "value", 0.0), kf))
    if not pts:
        return 0, 0.0

    first_frame, first_value, _ = pts[0]
    try:
        setattr(target, data_path, first_value)
        if not target.keyframe_insert(data_path=data_path, frame=first_frame):
            return 0, 0.0
    except (RuntimeError, TypeError, AttributeError) as e:
        print("[bbs-bridge] keyframe_insert failed for %s: %s" % (data_path, e))
        return 0, 0.0
    fc = _find_fcurve(target, data_path, 0)
    if fc is None:
        print("[bbs-bridge] could not find fcurve %s after insert" % (data_path,))
        return 0, 0.0

    max_frame = first_frame
    for frame, value, kf in pts:
        kp = fc.keyframe_points.insert(frame, value, options={'FAST'})
        _apply_kf_shape(kp, kf, frame, value, 1.0, tick_scale)
        if frame > max_frame:
            max_frame = frame
    fc.update()
    return len(pts), max_frame


def _frames_to_channel(frames, key):
    """Die flache sample-liste (`[{tick,x,y,z,pitch,yaw,roll,fov}, ...]`) in EINE kanal-
    keyframe-liste umbauen -- LINEAR interpoliert, weil die dichte pro-tick-sample-rate selbst
    schon die kurvenform traegt (plan-v2 task 1.3: "sonst dense baken" -- hier ist JEDER
    sample-tick ein keyframe, kein bezier-handle-raten noetig)."""
    return [{"tick": f.get("tick", 0), "value": f.get(key, 0.0), "interp": "linear"} for f in frames]


def _import_film_camera(fields):
    """Frames -> F-curves auf BBS_FilmCam. Laeuft im timer = main-thread (wie _build_actors)."""
    frames = fields.get("frames") or []
    if not frames:
        state._film_cam_status = "film has no camera frames"
        print("[bbs-bridge] film camera export ok but no frames (film='%s')" % fields.get("film"))
        return

    film = fields.get('film')
    if not isinstance(film, str) or not film or not isinstance(frames, list):
        raise RuntimeError('Film camera payload lacks an identified film/frame list')
    for frame in frames:
        if (not isinstance(frame, dict) or any(
                type(frame.get(key)) not in (int, float) or not math.isfinite(frame[key])
                for key in ('tick', 'x', 'y', 'z', 'pitch', 'yaw', 'roll', 'fov'))):
            raise RuntimeError('Film camera payload contains an invalid sample')

    scene = bpy.context.scene
    fps = scene.render.fps / (scene.render.fps_base or 1.0)
    tick_scale = fps / 20.0   # MC laeuft 20 ticks/s, siehe actors._build_actors

    obj = _find_or_create_film_cam()
    _clear_film_cam_animation(obj)
    # §3 zeitbasis: die keys liegen bei frame = tick * (fps/20) — mit DIESER fps. Wer den
    # film spaeter rendert/muxt, muss sie kennen (render_drive.render_film liest sie); die
    # szenen-fps kann sich bis dahin geaendert haben (weg-A-restore stellt sie zurueck).
    obj["bbs_fps_bake"] = float(fps)

    n_keys = 0
    max_frame = 0.0
    for ch_id, spec in _CAM_OBJECT_CHANNELS.items():
        keyframes = _frames_to_channel(frames, ch_id)
        k, mf = _build_fcurve(obj, keyframes, spec, tick_scale)
        n_keys += k
        if mf > max_frame:
            max_frame = mf

    fov_keyframes = _frames_to_channel(frames, "fov")
    lens_keyframes = [
        {"tick": kf["tick"], "value": _lens_for_vfov_deg(kf["value"], obj.data.sensor_height),
         "interp": "linear"}
        for kf in fov_keyframes
    ]
    k, mf = _build_scalar_fcurve(obj.data, lens_keyframes, "lens", tick_scale)
    n_keys += k
    if mf > max_frame:
        max_frame = mf

    end = int(math.ceil(max_frame))
    if end > scene.frame_end:
        scene.frame_end = end

    obj['bbs_camera_source_v1'] = json.dumps({
        'version': 1, 'film': film, 'endpoint': list(state._connected_endpoint or ()),
        'tick_scale': tick_scale}, sort_keys=True)
    state._film_cam_status = "%d frames / %d keys -> %s" % (len(frames), n_keys, BBS_FILM_CAM_NAME)
    print("[bbs-bridge] film camera import done: %d sampled frames (film='%s', source=%s, "
          "duration=%s ticks, %d clips) -> %d keyframes on %d channels on '%s' (%.3f frames/tick)"
          % (len(frames), fields.get("film"), fields.get("source"), fields.get("duration"),
             fields.get("clipCount", 0), n_keys, len(_CAM_OBJECT_CHANNELS) + 1, BBS_FILM_CAM_NAME,
             tick_scale))


# ---- Task 1.3b: stage-1-warnungen (entity-tracking offline) ------------------------------
# Modul-LOKAL statt state.py (W6-B2-konvention wie replay_anim._pending: state.py/ui.py sind
# fremdes flotten-territorium; die panel-anzeige laeuft ueber state._film_cam_status, den
# ui.py SCHON rendert -- warnungen erscheinen als suffix dieser zeile, details in der konsole).
_last_warnings = []

# ---- W8-B4: requestId-korrelation (ce2d139-muster, schliesst die dbdd08c-luecke) ---------
# Das solicited-gate (pending-flag) schuetzte nur die IDLE-instanz: WAEHREND des eigenen
# 30s-fensters gewann weiterhin das ERSTE ankommende result, egal wessen (die java-bridge
# broadcastet an ALLE clients — exakt die gate-inversion die WORLD_EXPORT mit end-review
# fix 2 geschlossen hat). Jetzt: ui.py holt sich pro request via new_request_id() eine
# uuid4-hex und schickt sie als fields.requestId mit; java (FilmCameraExporter.handle)
# echot sie auf JEDEM antwort-pfad; der handler unten akzeptiert bei gesetztem _request_id
# NUR das result mit DERSELBEN id — fremde lassen pending+deadline UNANGETASTET (timeout
# bleibt der einzige andere ausgang). Results ohne exakte requestId oder boolesches ok
# werden nicht ueber einen Legacy-Fallback importiert. Der Anforderungszustand schuetzt
# ausserdem Kamera-Edits und Film-/Endpoint-/FPS-Wechsel vor einer spaeten Antwort.
_request_id = ""
_request_context = None


def _camera_request_state():
    """Snapshot authored data and identities, not dependency-graph evaluated values."""
    from .actors import _object_fcurve_collections
    from .actor_channel_sync import _curve_signature
    scene = bpy.context.scene
    cam = bpy.data.objects.get(BBS_FILM_CAM_NAME)
    saved = []
    if cam is not None:
        if cam.type != 'CAMERA' or cam.name not in scene.objects:
            raise RuntimeError('Film camera name belongs to another object or scene')
        for holder in (cam, cam.data):
            curves = {(fc.data_path, fc.array_index): _curve_signature(fc)
                      for collection in _object_fcurve_collections(holder) for fc in collection}
            ad = holder.animation_data
            saved.append((holder, ad.action if ad else None, curves))
        static = {}
        for path, index, _, _ in _CAM_OBJECT_CHANNELS.values():
            if (path, index) not in saved[0][2]:
                static[(path, index)] = float(getattr(cam, path)[index])
        if ('lens', 0) not in saved[1][2]:
            static[('lens', 0)] = float(cam.data.lens)
        saved.append((cam.rotation_mode, cam.data.sensor_fit, cam.data.sensor_height, static))
    return (scene, state._connected_endpoint, state.bound_film, state.peer_engine,
            scene.render.fps, scene.render.fps_base, saved)


def new_request_id():
    """Frische korrelations-id fuer den NAECHSTEN FILM_CAMERA_EXPORT-request erzeugen +
    merken (aufgerufen von ui.py's operator, der sie als fields.requestId mitschickt).
    Ein neuer request ueberschreibt die alte id — genau ein request ist offen (pending-flag),
    ein nachzuegler des vorherigen requests faellt damit automatisch durchs id-gate."""
    global _request_id, _request_context
    import uuid
    _request_context = _camera_request_state()
    _request_id = uuid.uuid4().hex
    return _request_id


def _warning_suffix(warnings):
    """Kompakter panel-suffix fuer state._film_cam_status. Erste warnung wird benannt (typ +
    tick-fenster), der rest gezaehlt -- die volle liste steht in der konsole (print unten).
    Defensiv gegen halbe eintraege (fehlende keys -> defaults), nie werfen."""
    if not warnings:
        return ""
    first = warnings[0] if isinstance(warnings[0], dict) else {}
    label = "%s@%s-%s" % (first.get("type", "?"), first.get("tickStart", "?"), first.get("tickEnd", "?"))
    more = " +%d" % (len(warnings) - 1) if len(warnings) > 1 else ""
    return " | WARN %d tracking (%s%s)" % (len(warnings), label, more)


def _handle_film_camera_export_result(fields):
    """Antwort auf unseren FILM_CAMERA_EXPORT-request. Jeder fehlerpfad loggt + laeuft weiter,
    nie crashen -- ein kaputter import darf den tick-loop nicht killen (gleiche haltung wie
    actors._handle_actors_export_result / world_import._handle_world_export_result).

    SOLICITED-GATE (task 1.3b, dossier-merkregel bb20355): die java-bridge broadcastet results
    an ALLE clients -- ohne gate wuerde diese instanz fremde agent-/zweitclient-exports auf
    BBS_FilmCam importieren. Nur importieren wenn WIR einen request offen haben; ein result
    ohne offenen request wird geloggt + verworfen (auch der timeout-fall: nach 30s hat die
    statuszeile den request schon als verloren erklaert, ein spaeter nachzuegler importiert
    nicht mehr still).

    REQUESTID-GATE: nur die exakte eigene id mit booleschem Ergebnis annehmen.
    Fehlende/fremde ids lassen pending und deadline unveraendert. Vor erfolgreichem
    Import muss auch der beim Anfordern erfasste Kamera-/Szenen-Kontext noch passen;
    spaetere lokale Edits werden nicht durch die Antwort ersetzt."""
    global _request_id, _request_context
    if not state._film_cam_pending:
        print("[bbs-bridge] unsolicited FILM_CAMERA_EXPORT_RESULT ignored (no request pending)")
        return
    if (not isinstance(fields, dict) or not _request_id
            or fields.get('requestId') != _request_id or type(fields.get('ok')) is not bool):
        print('[bbs-bridge] unidentified or malformed FILM_CAMERA_EXPORT_RESULT ignored')
        return
    if fields['ok']:
        try:
            unchanged = (_request_context is not None and _camera_request_state() == _request_context
                         and state._peer_ready and not state.film_switch_detected
                         and (not state.bound_film or fields.get('film') == state.bound_film))
        except (ReferenceError, RuntimeError, AttributeError):
            unchanged = False
        if not unchanged:
            state._film_cam_pending = False
            _request_id, _request_context = '', None
            state._film_cam_status = 'camera import paused: local camera or film/endpoint/FPS changed'
            print('[bbs-bridge] ' + state._film_cam_status)
            return
    state._film_cam_pending = False
    _request_id, _request_context = '', None

    global _last_warnings
    _last_warnings = [w for w in (fields.get("warnings") or []) if isinstance(w, dict)]

    if not fields.get("ok", False):
        err = fields.get("error") or "unknown error"
        state._film_cam_status = err
        print("[bbs-bridge] film camera export failed:", err)
        return
    try:
        _import_film_camera(fields)
    except Exception as e:   # noqa: BLE001 — der tick-loop darf an KEINEM import sterben
        state._film_cam_status = "import failed"
        print("[bbs-bridge] film camera import failed:", e)

    # Task 1.3b: stage-1-warnungen ("abschnitt X-Y nutzt entity-tracking, offline nicht
    # aufloesbar") ANS status anhaengen -- NACH dem import, der export/import laeuft trotz
    # warnungen normal weiter (plan v2 1.3b stufe 1: warnen, nie abbrechen).
    if _last_warnings:
        state._film_cam_status += _warning_suffix(_last_warnings)
        for w in _last_warnings:
            print("[bbs-bridge] film camera tracking warning: %s @ %s-%s: %s"
                  % (w.get("type", "?"), w.get("tickStart", "?"), w.get("tickEnd", "?"),
                     w.get("reason", "")))
