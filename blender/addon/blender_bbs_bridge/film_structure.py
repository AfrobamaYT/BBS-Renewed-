"""Opt-in Blender -> BBS replay-list structure sync.

Merge creates/updates by stable UUID and never deletes or reorders. Replace is the exact,
destructive mirror used only behind the UI confirmation dialog. Animation curves stay on the
separate ACTORS_IMPORT channel.
"""

import time
import uuid as uuidlib

import bpy

from . import state
from .core import _next_seq, _send


TIMEOUT = 30.0
_pending = False
_deadline = 0.0
_request_id = ""
_status = "idle"
_last_result = {}
_remote_replays = {}


def _source_replays():
    return _remote_replays


def remember_replays(replays):
    """Keep the complete latest actor metadata; state._last_actors_payload is HUD-only."""
    global _remote_replays
    if not isinstance(replays, list):
        return
    _remote_replays = {str(replay.get("uuid") or ""): dict(replay)
                       for replay in replays
                       if isinstance(replay, dict) and replay.get("uuid")}


def _order(obj, source):
    try:
        return int(obj.get("bbs_order"))
    except (TypeError, ValueError):
        pass
    for value in (source.get("id"), obj.get("bbs_id")):
        try:
            return int(value)
        except (TypeError, ValueError):
            pass
    return 1_000_000


def actor_roots():
    coll = bpy.data.collections.get(state.ACTORS_COLLECTION)
    if coll is None:
        return []
    source = _source_replays()
    roots = [obj for obj in coll.objects if obj.parent is None and obj.get("bbs_uuid")]
    roots.sort(key=lambda obj: (_order(obj, source.get(str(obj.get("bbs_uuid")), {})), obj.name))
    return roots


def selected_root(context=None):
    obj = (context or bpy.context).active_object
    while obj is not None and not obj.get("bbs_uuid"):
        obj = obj.parent
    return obj


def _entry(obj, order, source):
    def value(override, wire, snapshot, fallback):
        if override in obj:
            return obj.get(override)
        if wire in source:
            return source.get(wire)
        return obj.get(snapshot, fallback)

    return {
        "uuid": str(obj.get("bbs_uuid") or ""),
        "label": str(obj.get("bbs_label_override")
                     if "bbs_label_override" in obj else source.get("label", obj.name)),
        "formType": str(value("bbs_form_type_override", "formType", "bbs_form_type", "")),
        "formModel": str(value("bbs_form_model_override", "formModel", "bbs_form_model", "")),
        "formMob": str(value("bbs_form_mob_override", "formMob", "bbs_form_mob", "")),
        "group": str(value("bbs_group_override", "group", "bbs_group", "")),
        "isGroup": bool(value("bbs_is_group_override", "isGroup", "bbs_is_group", False)),
        "enabled": bool(value("bbs_enabled_override", "enabled", "bbs_enabled", True)),
        "firstPerson": bool(value("bbs_first_person_override", "fp", "bbs_first_person", False)),
        "category": str(value("bbs_category_override", "category", "bbs_category", "")),
        "order": order,
    }


def entry_for_object(obj):
    if obj is None:
        return None
    source = _source_replays().get(str(obj.get("bbs_uuid") or ""), {})
    return _entry(obj, _order(obj, source), source)


def apply_overrides(obj, entry):
    if obj is None or not isinstance(entry, dict):
        return False
    for prop, wire in (
        ("bbs_label_override", "label"),
        ("bbs_form_type_override", "formType"),
        ("bbs_form_model_override", "formModel"),
        ("bbs_form_mob_override", "formMob"),
        ("bbs_group_override", "group"),
        ("bbs_is_group_override", "isGroup"),
        ("bbs_enabled_override", "enabled"),
        ("bbs_first_person_override", "firstPerson"),
        ("bbs_category_override", "category"),
    ):
        obj[prop] = entry.get(wire)
    return True


def build_fields(mode):
    roots = actor_roots()
    source = _source_replays()
    return {
        "requestId": uuidlib.uuid4().hex,
        "film": state.bound_film,
        "mode": mode,
        "replays": [_entry(obj, i, source.get(str(obj.get("bbs_uuid")), {}))
                    for i, obj in enumerate(roots)],
    }


def send(mode):
    global _pending, _deadline, _request_id, _status
    if mode not in ("merge", "replace"):
        _status = "invalid mode"
        return False
    if not state._connected or state._sock is None:
        _status = "not connected to BBS"
        return False
    if "film_structure_write_v1" not in state.peer_capabilities:
        _status = "BBS peer has no film_structure_write_v1 capability"
        return False
    if state.film_switch_detected or not state.bound_film:
        _status = "film is not safely bound"
        return False
    if _pending:
        _status = "previous structure write still pending"
        return False

    fields = build_fields(mode)
    if not fields["replays"]:
        _status = "no tagged actor roots in MC_Actors"
        return False

    uuids = [entry["uuid"] for entry in fields["replays"]]
    if len(uuids) != len(set(uuids)):
        _status = "duplicate bbs_uuid — select the duplicate and use New Replay ID"
        return False

    _request_id = fields["requestId"]
    _pending = True
    _deadline = time.time() + TIMEOUT
    _status = "%s pending (%d replays)" % (mode, len(fields["replays"]))
    _send({"type": "FILM_STRUCTURE_IMPORT", "origin": "blender", "uuid": "filmStructure",
           "seq": _next_seq(), "fields": fields})
    return True


def handle_result(fields):
    global _pending, _request_id, _status, _last_result
    if not _pending or str(fields.get("requestId") or "") != _request_id:
        return
    _pending = False
    _request_id = ""
    _last_result = dict(fields)
    if fields.get("ok"):
        _status = ("%s: +%d ~%d -%d moved=%d total=%d" %
                   (fields.get("mode"), fields.get("created", 0), fields.get("updated", 0),
                    fields.get("removed", 0), fields.get("moved", 0), fields.get("total", 0)))
    else:
        _status = "failed: %s" % (fields.get("error") or "unknown error")


def check_timeout():
    global _pending, _request_id, _status
    if _pending and time.time() > _deadline:
        _pending = False
        _request_id = ""
        _status = "structure write timed out"


def assign_new_uuid(context=None):
    obj = selected_root(context)
    if obj is None:
        return None
    old = str(obj.get("bbs_uuid") or "")
    source = _source_replays().get(old, {})
    entry = _entry(obj, _order(obj, source), source)
    entry["label"] = obj.name
    apply_overrides(obj, entry)
    obj["bbs_uuid"] = str(uuidlib.uuid4())
    obj["bbs_order"] = max((_order(root, {}) for root in actor_roots()), default=-1) + 1
    return obj


def move_selected(delta, context=None):
    obj = selected_root(context)
    roots = actor_roots()
    if obj not in roots:
        return False
    index = roots.index(obj)
    target = index + delta
    if target < 0 or target >= len(roots):
        return False
    for i, root in enumerate(roots):
        root["bbs_order"] = i
    obj["bbs_order"], roots[target]["bbs_order"] = target, index
    return True
