"""TASK 6.2 — CAMERA_CLIP_WRITE-sender: die in Blender gebakte/editierte kamera-fahrt ZURUECK
in den offenen BBS-film schreiben, damit BBS SELBST die sequenz rendert (renderfahrt weg B:
echtes MC-bild + first-person hand/HUD + Snowstorm-/BBS-partikel — der zweite render-pfad).

Die GEGENRICHTUNG zu film_camera_import.py (task 1.3, BBS -> Blender): dort sampelt java
`FilmCameraExporter` die BBS-kamera-clip-timeline dicht pro tick und film_camera_import baut
daraus F-curves auf der EIGENEN kamera `BBS_FilmCam`. HIER lesen wir dieselben F-curves von
`BBS_FilmCam` zurueck, invertieren sie in den BBS-kanal-raum und schicken sie als EINEN
KeyframeClip an java `CameraClipWriter` (:9880). Java snapshottet den film davor (FilmBackup,
task 1.5), ersetzt die BASIS-kamera-clips (Idle/Dolly/Path/Keyframe) durch unseren clip und
laesst tracker-/modifier-clips in ruhe (bzw. schreibt einen POV-tracker, wenn wir einen
mitschicken).

Dieses modul ist das kamera-analogon zu actor_export.py (W9-B1, der ACTORS_IMPORT-sender) —
gleiche haltung: read-only am Blender-objekt, invertierung zur laufzeit aus dem VORWAERTS-
mapping abgeleitet (nie doppelt pflegen), alle 4 handles mitsenden, leere kanaele NIE als
leeres array schicken, ehrliche degradationen geloggt.

WIRE (aus CameraClipWriter/BlenderBridgeClient.dispatch ABGELESEN, nicht geraten):
  request: {"type":"CAMERA_CLIP_WRITE","origin":"blender","uuid":"cameraClip","seq":N,
            "fields":{"requestId":"<hex>",
                      "channels":{"x":[{tick,value,interp,lx,ly,rx,ry},...], ... ,
                                  "roll":[...],"fov":[...]},
                      "replaceAll":false,
                      "tracker":{"showHand":bool,"showHud":bool,"selector":int,
                                 "tickStart":int,"tickEnd":int}}}   # tracker OPTIONAL
  reply:   CAMERA_CLIP_WRITE_RESULT, fields =
           {ok,film,source,keysWritten,cameraClipsRemoved,trackerClipsWritten,
            replaceAll,backup,error,requestId}

KOORDINATEN — INVERSE der film_camera_import-hinrichtung (COORDINATE_CONTRACT.md), zur laufzeit
aus `film_camera_import._CAM_OBJECT_CHANNELS` abgeleitet:
  vorwaerts (BBS->Blender):  bl = bbs*scale + const   (x/y/z/pitch/yaw/roll)
  rueckwaerts (Blender->BBS): bbs = (bl - const)/scale
  x: (location[0],  1.0, 0)   y: (location[2],  1.0, 0)   z: (location[1], -1.0, 0)
  pitch: (rotation_euler[0], -DEG, 0)   yaw: (rotation_euler[2], -DEG, 0)
  roll:  (rotation_euler[1],  DEG, 0)   (film_camera_import-eigenes mapping, actors kennt kein roll)
FOV geht NICHT ueber einen der euler-kanaele, sondern rueckwaerts aus `BBS_FilmCam.data.lens`
(mm) via der EXAKTEN inverse von film_camera_import._lens_for_vfov_deg:
  vfov_deg = 2*atan(sensor_height / (2*lens))  (sensor_fit='VERTICAL', sensor_height beim
  anlegen von BBS_FilmCam festgenagelt und nie wieder angefasst — deterministisch umkehrbar).
`distance` senden wir NICHT (BBS_FilmCam hat keinen distance-kanal — der ist ein orbit-offset,
den film_camera_import gar nicht importiert; ein KeyframeClip mit leerem distance = kein offset).

TICK <-> FRAME: tick = frame * 20/fps (invers zu tick_scale=fps/20, fps inkl. fps_base) — exakt
wie actor_export und film_camera_import.

HANDLES (invers zu actors._apply_kf_shape, die BezierUtils.get()-semantik traegt, WIEDERVERWENDET
aus actor_export._keyframe_dict): lx=(frame-hl.x)/tick_scale, ly=(hl.y-value)/scale (const kuerzt
sich — dieselbe wert-raum-regel), rx/ry analog. IMMER alle 4 senden (BBS liest sie nur, wenn der
angrenzende key bezier ist — genau dann hat Blender sie auch benutzt).

REPLACE-SEMANTIK: default `replaceAll=False` — java ersetzt nur die BASIS-kamera-clips, tracker/
modifier bleiben. `replaceAll=True` = ganze kamera-timeline leeren (from-scratch-render, bewusstes
opt-in). POV: wenn `tracker` mitgeschickt wird, schreibt java EINEN TrackerClip (show_hand/show_hud/
selector) — ohne den rendert BBS NIE hand/HUD (beide default false, task 5.4). Ohne `tracker` im
payload bleiben die vorhandenen tracker-clips des films unangetastet.

KEIN eigener socket-status-spiegel in state.py (fremdes territorium) — modul-lokales `_pending`
(replay_anim/actor_export-muster). Java echot requestId, das result-gate vergleicht sie (ce2d139).
"""

import bpy
import json
import math
import time
import uuid

from . import state
from .core import _send, _next_seq
from .actors import _object_fcurve_collections
from . import film_camera_import as _fcam
from .actor_export import _keyframe_dict

# ---- rueckwaerts-mapping der objekt-kanaele, aus film_camera_import abgeleitet ----
# (data_path, array_index) -> (bbs-kanal, scale, const).  _CAM_OBJECT_CHANNELS ist
# _ACTOR_CHANNELS + roll (film_camera_import baut es beim import genauso auf).
_REVERSE_CAM_CHANNELS = {
    (dp, idx): (ch, scale, const)
    for ch, (dp, idx, scale, const) in _fcam._CAM_OBJECT_CHANNELS.items()
}

# ---- modul-lokales pending (KEIN state.py-edit; actor_export/replay_anim-muster) ----
_pending = False
_deadline = 0.0
_status = "idle"
_last_result = None      # fields-dict des letzten results (gates lesen das)
_last_payload = None     # zuletzt gesendetes fields-dict (gates vergleichen dagegen)
_request_id = ""         # ce2d139-korrelation (java echot sie)
CAMERA_CLIP_WRITE_TIMEOUT = 30.0


def _vfov_deg_for_lens(lens_mm, sensor_height):
    """EXAKTE inverse von film_camera_import._lens_for_vfov_deg:
    lens = sensor_height/(2*tan(vfov/2))  ->  vfov = 2*atan(sensor_height/(2*lens)).
    Geklemmt gegen entartete lens (0 mm) — ein kaputter kamera-wert darf den ganzen send
    nicht mit ZeroDivisionError killen (gleiche haltung wie die hinrichtung)."""
    lens_mm = max(float(lens_mm), 1e-6)
    half = math.atan(sensor_height / (2.0 * lens_mm))
    return math.degrees(2.0 * half)


def _object_channels(cam_obj, tick_scale, degradations):
    """Die gemappten objekt-level fcurves von BBS_FilmCam -> {"x":[kf,...], ...} in BBS-raum.

    READ-ONLY (kein fc.update(), kein schreiben). Nur kanaele mit exaktem (data_path,index)-
    treffer im _REVERSE_CAM_CHANNELS werden gelesen — fremde/user-kurven fallen automatisch raus.
    Wiederverwendet actor_export._keyframe_dict (die handle-invertierung + wert-raum-regel sind
    identisch; roll hat scale=+DEG, das dict rechnet das generisch)."""
    channels = {}
    for coll in _object_fcurve_collections(cam_obj):
        for fc in coll:
            spec = _REVERSE_CAM_CHANNELS.get((fc.data_path, fc.array_index))
            if spec is None:
                continue
            ch, scale, const = spec
            pts = sorted(fc.keyframe_points, key=lambda p: p.co[0])
            arr = [_keyframe_dict(kp, scale, const, tick_scale, degradations) for kp in pts]
            if arr:                    # leeres array wuerde den BBS-kanal LEEREN (actor_export)
                channels[ch] = arr
    return channels


def _fov_channel(cam_obj, tick_scale, degradations):
    """Der fov-kanal aus BBS_FilmCam.data.lens (mm) rueckgerechnet -> [{tick,value(vfov_deg),...}].

    lens liegt auf obj.data (eigener animation_data-holder — daten-seite, nicht objekt-seite,
    siehe render_drive.film_frame_range). value = vfov in GRAD (BBS Angle.fov-einheit). Handles
    werden fuer lens NICHT invertiert (die lens<->fov-abbildung ist nichtlinear, ein handle im
    lens-raum entspraeche keinem sauberen handle im fov-raum) -> LINEAR + java-default-handles;
    das ist konsistent damit, dass film_camera_import den fov beim import ebenfalls als LINEAR-
    dense-key baut (kurvenform steckt in der dichten sample-rate). Als bewusste, geloggte
    degradation vermerkt, falls der user den lens-kanal mit nicht-linearen keys editiert hat."""
    data = getattr(cam_obj, "data", None)
    if data is None:
        return None
    sensor_height = getattr(data, "sensor_height", _fcam.FILM_CAM_SENSOR_HEIGHT)
    arr = []
    saw_nonlinear = False
    for coll in _object_fcurve_collections(data):
        for fc in coll:
            if fc.data_path != "lens":
                continue
            for kp in sorted(fc.keyframe_points, key=lambda p: p.co[0]):
                if kp.interpolation not in ('LINEAR', 'CONSTANT'):
                    saw_nonlinear = True
                frame = float(kp.co[0])
                arr.append({
                    "tick": frame / tick_scale,
                    "value": _vfov_deg_for_lens(float(kp.co[1]), sensor_height),
                    "interp": "constant" if kp.interpolation == 'CONSTANT' else "linear",
                })
    if saw_nonlinear:
        degradations.add("lens-kanal hat nicht-lineare keys -> als LINEAR fov gesendet "
                         "(lens<->fov ist nichtlinear, handles nicht uebertragbar)")
    return arr or None


def build_payload(replace_all=False, tracker=None, cam_name=None):
    """Das CAMERA_CLIP_WRITE-fields-dict bauen, ohne zu senden (gates inspizieren es so).

    tracker: None (kein POV-clip, vorhandene tracker unangetastet) ODER dict mit
    {show_hand,show_hud,selector,tick_start,tick_end} (tick_*=None -> java default = fahrt-range).
    Gibt (fields, degradations, reason) zurueck; fields=None wenn nichts zu senden (grund in
    `reason`)."""
    cam_name = cam_name or _fcam.BBS_FILM_CAM_NAME
    cam = bpy.data.objects.get(cam_name)
    if cam is None or cam.type != 'CAMERA' or cam.data is None:
        return None, set(), "keine film-kamera '%s' — erst film-kamera importieren" % cam_name

    scene = bpy.context.scene
    fps = scene.render.fps / (scene.render.fps_base or 1.0)
    tick_scale = fps / 20.0
    degradations = set()
    try:
        source = json.loads(cam.get('bbs_camera_source_v1', 'null'))
    except (TypeError, ValueError):
        source = None
    if (not isinstance(source, dict) or source.get('version') != 1
            or not state.bound_film or source.get('film') != state.bound_film
            or not state._connected_endpoint
            or source.get('endpoint') != list(state._connected_endpoint)):
        return None, degradations, 'camera source film/endpoint differs; explicitly import the intended film camera'
    scale = source.get('tick_scale')
    if (type(scale) not in (int, float) or not math.isfinite(scale)
            or abs(scale - tick_scale) > 1e-12):
        return None, degradations, 'camera FPS basis changed; explicit reimport/rebase required'

    channels = _object_channels(cam, tick_scale, degradations)
    fov = _fov_channel(cam, tick_scale, degradations)
    if fov is not None:
        channels["fov"] = fov

    if not channels:
        return None, degradations, ("'%s' hat keine gebakten kamera-fcurves — erst film-kamera "
                                    "importieren/editieren" % cam_name)

    fields = {"film": source['film'], "channels": channels, "replaceAll": bool(replace_all)}
    if tracker is not None:
        fields["tracker"] = _tracker_fields(tracker, channels, tick_scale)
    return fields, degradations, None


def _tracker_fields(tracker, channels, tick_scale):
    """POV-tracker-abschnitt bauen. tick_start/tick_end default = die AUFGELOESTE fahrt-range
    (min/max tick ueber alle kamera-kanaele) — deckt die ganze fahrt ab, wenn der user keinen
    engeren bereich nennt. selector = replay-INDEX des POV-actors (java EntityClip.selector,
    -1 = keiner)."""
    ticks = [kf["tick"] for arr in channels.values() for kf in arr]
    lo = min(ticks) if ticks else 0.0
    hi = max(ticks) if ticks else 0.0
    ts = tracker.get("tick_start")
    te = tracker.get("tick_end")
    return {
        "showHand": bool(tracker.get("show_hand", False)),
        "showHud": bool(tracker.get("show_hud", False)),
        "selector": int(tracker.get("selector", -1)),
        "tickStart": int(lo if ts is None else ts),
        "tickEnd": int(math.ceil(hi) if te is None else te),
    }


def send_camera_clips(replace_all=False, tracker=None, cam_name=None):
    """Blender-kamera-fahrt (BBS_FilmCam) als CAMERA_CLIP_WRITE nach BBS schicken.

    True = request raus (antwort async ueber tick.py -> _handle_camera_clip_write_result,
    status in camera_clip_write._status). False = nicht gesendet (grund in _status)."""
    global _pending, _deadline, _status, _last_payload, _request_id, _last_result
    if not state._connected or state._sock is None:
        _status = "not connected"
        return False
    if (not state._peer_ready or state.film_switch_detected
            or not {'camera_clip_write', 'camera_clip_bound_v1'} <= state.peer_capabilities):
        _status = "camera write needs the bound film and a fresh camera_clip_bound_v1 peer"
        return False
    if _pending:
        _status = "write already pending"
        return False

    try:
        fields, degradations, reason = build_payload(replace_all=replace_all, tracker=tracker,
                                                     cam_name=cam_name)
    except (ValueError, TypeError, RuntimeError, OverflowError) as exc:
        _status = 'cannot build camera write: %s' % exc
        return False
    _last_result = None
    if fields is None:
        _status = reason or "nothing to send"
        print("[bbs-bridge] CAMERA_CLIP_WRITE: %s" % _status)
        return False

    _request_id = uuid.uuid4().hex
    fields["requestId"] = _request_id
    _last_payload = fields
    _pending = True
    _deadline = time.time() + CAMERA_CLIP_WRITE_TIMEOUT
    n_keys = sum(len(arr) for arr in fields["channels"].values())
    trk = " +tracker" if "tracker" in fields else ""
    _status = "sent %d keys / %d channels%s" % (n_keys, len(fields["channels"]), trk)
    _send({"type": "CAMERA_CLIP_WRITE", "origin": "blender", "uuid": "cameraClip",
           "seq": _next_seq(), "fields": fields})
    print("[bbs-bridge] CAMERA_CLIP_WRITE sent: %d keys, %d channels%s (replaceAll=%s)"
          % (n_keys, len(fields["channels"]), trk, replace_all))
    for d in sorted(degradations):
        print("[bbs-bridge] CAMERA_CLIP_WRITE degradation: %s" % d)
    return True


def _handle_camera_clip_write_result(fields):
    """Antwort auf UNSEREN send (tick.py ruft das nur bei _pending=True — solicited-gate). Nie
    werfen: der tick-loop darf an keinem result sterben. requestId-gate wie world_import/actor
    (ce2d139): fremde id -> ignorieren, pending+deadline unangetastet."""
    global _pending, _status, _last_result, _request_id
    if not _pending:
        print("[bbs-bridge] unsolicited CAMERA_CLIP_WRITE_RESULT ignored (no request pending)")
        return
    if (not isinstance(fields, dict) or not _request_id
            or fields.get('requestId') != _request_id or type(fields.get('ok')) is not bool
            or (fields['ok'] and (not _last_payload
                or fields.get('film') != _last_payload.get('film')))):
        print('[bbs-bridge] unidentified, foreign or malformed CAMERA_CLIP_WRITE_RESULT ignored')
        return
    _pending = False
    _request_id = ""
    _last_result = dict(fields) if isinstance(fields, dict) else {}
    try:
        if fields.get("ok"):
            _status = ("wrote %s keys / removed %s cam-clips / %s tracker -> film '%s'%s"
                       % (fields.get("keysWritten", "?"), fields.get("cameraClipsRemoved", "?"),
                          fields.get("trackerClipsWritten", "?"), fields.get("film", "?"),
                          (" | backup %s" % fields.get("backup")) if fields.get("backup") else ""))
        else:
            _status = "write failed: %s" % (fields.get("error") or "unknown")
        print("[bbs-bridge] CAMERA_CLIP_WRITE result: %s" % _status)
    except Exception as e:   # noqa: BLE001 — timer-regel
        _status = "result unlesbar: %s" % e
        print("[bbs-bridge] CAMERA_CLIP_WRITE result unlesbar:", e)


def _check_timeout():
    """Von tick.py gerufen (neben den world/actors/film-cam/actor-import-deadlines) — ein
    verlorenes result darf das pending-fenster nicht fuer immer blockieren."""
    global _pending, _status
    if _pending and time.time() > _deadline:
        _pending = False
        _status = "write timed out"
        print("[bbs-bridge] CAMERA_CLIP_WRITE timed out (no result after %ds)"
              % CAMERA_CLIP_WRITE_TIMEOUT)
