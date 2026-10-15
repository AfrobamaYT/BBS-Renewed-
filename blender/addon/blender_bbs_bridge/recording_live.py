"""Live player replay recording preview and Blender-side recording controls.

The preview stream is intentionally disposable. BBS sends one static subject description at
recording start and one timestamped sample per recorded tick. When recording stops, the existing
ACTORS_EXPORT_RESULT push replaces it with the authoritative simplified curves.
"""

import math
import time
import uuid

import bpy

from . import state
from . import recording_morph
from .core import _next_seq, _send


_session = ""
_replay_uuid = ""
_status = "idle"
_frames = 0
_last_tick = None
_control_pending = False
_control_request_id = ""
_control_deadline = 0.0
_last_control_action = ""
_last_control_ok = None
_visibility_before = []


def _restore_visibility():
    global _visibility_before
    for candidate, hide_viewport, hide_render in _visibility_before:
        try:
            candidate.hide_viewport = hide_viewport
            candidate.hide_render = hide_render
        except ReferenceError:
            pass
    _visibility_before = []


def _reevaluate_timeline_visibility():
    """Let authoritative hide_* F-curves win after disposable preview visibility cleanup."""
    scene = bpy.context.scene
    was_applying_remote = state._playhead_applying_remote
    state._playhead_applying_remote = True
    state._playhead_last_remote_frame = scene.frame_current
    try:
        try:
            scene.frame_set(scene.frame_current)
        except (RuntimeError, TypeError):
            bpy.context.view_layer.update()
    finally:
        state._playhead_applying_remote = was_applying_remote


def _actor_root(replay_uuid):
    coll = bpy.data.collections.get(state.ACTORS_COLLECTION)
    if coll is None:
        return None
    for obj in coll.objects:
        if str(obj.get("bbs_uuid") or "") == replay_uuid:
            return obj
    return None


def _ensure_actor(replay):
    from . import actors

    return actors._ensure_actor_root(replay)


def handle_start(fields):
    global _session, _replay_uuid, _status, _frames, _last_tick, _visibility_before
    _restore_visibility()
    _session = str(fields.get("session") or "")
    _replay_uuid = str(fields.get("replayUuid") or "")
    _frames = 0
    _last_tick = None
    replay = fields.get("replay") if isinstance(fields.get("replay"), dict) else {}
    obj = _ensure_actor(replay)
    if obj is not None:
        _visibility_before = []
        for candidate in [obj] + list(obj.children_recursive):
            _visibility_before.append((candidate, candidate.hide_viewport, candidate.hide_render))
            candidate.hide_viewport = False
            candidate.hide_render = False
        obj["bbs_live_recording"] = True
        obj["bbs_live_session"] = _session
        recording_morph.begin(obj, replay, fields.get("form"), _session)
    _status = "recording %s (countdown %s)" % (
        _replay_uuid or "(unknown actor)", fields.get("countdown", 0))


def _apply_transform(obj, values):
    from .actors import _ACTOR_CHANNELS
    values = dict(values)
    if "bodyYaw" in values:
        values["yaw"] = values["bodyYaw"]
    for channel, spec in _ACTOR_CHANNELS.items():
        if channel not in values:
            continue
        try:
            raw = float(values[channel])
        except (TypeError, ValueError):
            continue
        data_path, index, scale, const = spec
        target = getattr(obj, data_path, None)
        if target is not None:
            target[index] = raw * scale + const


def _apply_live_properties(obj, values, strings):
    for name, value in values.items():
        if name not in {"x", "y", "z", "pitch", "yaw"}:
            try:
                obj["bbs_live_" + str(name)] = float(value)
            except (TypeError, ValueError):
                pass
    for name, value in strings.items():
        obj["bbs_live_" + str(name)] = str(value)


def handle_frame(fields):
    global _status, _frames, _last_tick
    if not _session or str(fields.get("session") or "") != _session:
        return
    replay_uuid = str(fields.get("replayUuid") or _replay_uuid)
    base_obj = _actor_root(replay_uuid)
    if base_obj is None:
        _status = "recording frame arrived but actor %s is missing" % (replay_uuid or "?")
        return

    tick = fields.get("tick")
    values = fields.get("values") if isinstance(fields.get("values"), dict) else {}
    strings = fields.get("strings") if isinstance(fields.get("strings"), dict) else {}
    try:
        tick_value = float(tick)
    except (TypeError, ValueError):
        tick_value = 0.0

    scene = bpy.context.scene
    fps = scene.render.fps / (scene.render.fps_base or 1.0)
    frame = int(round(tick_value * fps / 20.0))
    was_applying_remote = state._playhead_applying_remote
    state._playhead_applying_remote = True
    state._playhead_last_remote_frame = frame
    try:
        scene.frame_set(frame)
    finally:
        state._playhead_applying_remote = was_applying_remote

    form = fields.get("form") if isinstance(fields.get("form"), dict) else {}
    obj = recording_morph.display(base_obj, form)
    _apply_transform(base_obj, values)
    _apply_live_properties(base_obj, values, strings)
    if obj is not base_obj:
        _apply_transform(obj, values)
        _apply_live_properties(obj, values, strings)
    try:
        from . import model_pose
        model_pose.apply_live_actor_pose(obj, values)
    except Exception as exc:  # noqa: BLE001 - timer path must not die on one preview pose
        print("[bbs-bridge] live recording pose skipped: %s" % exc)
    for candidate in ((base_obj, obj) if obj is not None and obj is not base_obj else (base_obj,)):
        candidate["bbs_live_recording"] = True
        candidate["bbs_live_tick"] = tick_value
        candidate["bbs_live_form_type"] = str(form.get("type") or "")
        candidate["bbs_live_form_model"] = str(form.get("model") or "")
        candidate["bbs_live_form_mob"] = str(form.get("mob") or "")
    bpy.context.view_layer.update()

    _frames += 1
    _last_tick = tick_value
    if fields.get("duringCountdown"):
        _status = "countdown %s" % fields.get("countdown", 0)
    else:
        _status = "live %s: tick %s (%d frames)" % (replay_uuid or "actor", tick, _frames)


def handle_stop(fields):
    global _session, _status, _visibility_before
    if not _session or str(fields.get("session") or "") != _session:
        return
    obj = _actor_root(str(fields.get("replayUuid") or _replay_uuid))
    if obj is not None:
        obj["bbs_live_recording"] = False
    recording_morph.end(obj)
    _restore_visibility()
    _reevaluate_timeline_visibility()
    _status = "recorded %d preview frames; waiting for authoritative actor curves" % _frames
    _session = ""


def handle_control_result(fields):
    global _control_pending, _status, _last_control_action, _last_control_ok
    if _control_pending and str(fields.get("requestId") or "") != _control_request_id:
        return
    _control_pending = False
    _last_control_action = str(fields.get("action") or "")
    _last_control_ok = bool(fields.get("ok"))
    if fields.get("ok"):
        _status = "%s ok" % (fields.get("action") or "record control")
    else:
        _status = "%s failed: %s" % (
            fields.get("action") or "record control", fields.get("error") or "unknown error")


def request_control(action, replay_uuid=""):
    global _control_pending, _control_request_id, _control_deadline, _status
    if not state._connected or state._sock is None:
        _status = "not connected to BBS"
        return False
    if _control_pending:
        _status = "record control already pending"
        return False
    _control_request_id = uuid.uuid4().hex
    _control_pending = True
    _control_deadline = time.time() + 10.0
    _status = "%s requested" % action
    _send({
        "type": "REPLAY_RECORD_CONTROL",
        "origin": "blender",
        "uuid": "replay-record-control",
        "seq": _next_seq(),
        "fields": {
            "action": action,
            "requestId": _control_request_id,
            "replayUuid": replay_uuid,
        },
    })
    return True


def check_timeout():
    global _control_pending, _status, _session
    if _control_pending and time.time() > _control_deadline:
        _control_pending = False
        _status = "record control timed out"
    if _session and not state._connected:
        recording_morph.end(_actor_root(_replay_uuid))
        _restore_visibility()
        _reevaluate_timeline_visibility()
        _session = ""
        _status = "recording preview disconnected"


def selected_replay_uuid(context):
    obj = getattr(context, "active_object", None)
    while obj is not None:
        replay_uuid = str(obj.get("bbs_uuid") or "")
        if replay_uuid:
            return replay_uuid
        obj = obj.parent
    return ""


def reset_for_test():
    """Reset module-local session state for headless gates and add-on reloads."""
    global _session, _replay_uuid, _status, _frames, _last_tick
    global _control_pending, _control_request_id, _control_deadline
    global _last_control_action, _last_control_ok
    global _visibility_before
    _session = ""
    _replay_uuid = ""
    _status = "idle"
    _frames = 0
    _last_tick = None
    _control_pending = False
    _control_request_id = ""
    _control_deadline = 0.0
    _last_control_action = ""
    _last_control_ok = None
    _visibility_before = []
    recording_morph.end()
