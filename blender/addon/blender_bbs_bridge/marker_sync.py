"""Conflict-safe live synchronization for persistent film timeline markers.

Regular Blender timeline markers have no color and reject custom ID properties. Stable BBS UUID,
ARGB color and exact source tick therefore live in one scene-level JSON checkpoint. Camera-bound
Blender markers are deliberately outside this channel because BBS has no camera-binding field;
they are never removed or rewritten.
"""

import difflib
import json
import re
import time
import uuid as uuidlib

import bpy

from . import state
from .actors import check_film
from .core import _next_seq, _send


META_KEY = "bbs_marker_sync_v1"
DEFAULT_COLOR = -11615745  # signed ARGB 0xFF4EC1FF, BBS Marker.DEFAULT_COLOR
TIMEOUT = 30.0
_REVISION = re.compile(r"[0-9a-f]{64}")
_pending = None
_queued_snapshot = None
_blocked_local = None


def _fps(scene):
    return scene.render.fps / (scene.render.fps_base or 1.0)


def tick_to_frame(scene, tick):
    return int(round(tick * _fps(scene) / 20.0))


def frame_to_tick(scene, frame):
    if frame < 0:
        raise ValueError("negative Blender marker frames cannot be represented in BBS")
    return int(round(frame * 20.0 / _fps(scene)))


def _regular_markers(scene):
    return [marker for marker in scene.timeline_markers if marker.camera is None]


def _wire(entries):
    return [{"uuid": str(item["uuid"]), "tick": int(item["tick"]),
             "label": str(item["label"]), "color": int(item["color"])}
            for item in entries]


def _local_key(entries):
    return json.dumps(_wire(entries), sort_keys=True, separators=(",", ":"))


def argb_to_rgba(color):
    unsigned = int(color) & 0xFFFFFFFF
    return tuple(((unsigned >> shift) & 0xFF) / 255.0 for shift in (16, 8, 0, 24))


def rgba_to_argb(rgba):
    red, green, blue, alpha = (max(0, min(255, int(round(float(value) * 255))))
                               for value in rgba)
    unsigned = (alpha << 24) | (red << 16) | (green << 8) | blue
    return unsigned - 2 ** 32 if unsigned >= 2 ** 31 else unsigned


def selected_color(scene):
    metadata = _load_metadata(scene)
    if metadata is None:
        return None
    regular = _regular_markers(scene)
    selected = [index for index, marker in enumerate(regular) if marker.select]
    if len(selected) != 1:
        return None
    entries = _local_entries(scene, metadata)
    return argb_to_rgba(entries[selected[0]]["color"])


def set_selected_color(scene, rgba):
    """Set BBS-only ARGB metadata for exactly one selected regular Blender marker."""
    metadata = _retime(scene, _load_metadata(scene))
    if metadata is None:
        raise ValueError("no synchronized marker metadata")
    regular = _regular_markers(scene)
    selected = [index for index, marker in enumerate(regular) if marker.select]
    if len(selected) != 1:
        raise ValueError("select exactly one regular timeline marker")
    entries = _local_entries(scene, metadata)
    entries[selected[0]]["color"] = rgba_to_argb(rgba)
    _store_metadata(scene, metadata["film"], metadata["revision"], entries,
                    base=metadata["base"])
    state._marker_status = "marker color changed in Blender; write pending"
    return entries[selected[0]]["color"]


def _load_metadata(scene):
    raw = scene.get(META_KEY)
    if not isinstance(raw, str):
        return None
    try:
        value = json.loads(raw)
    except (TypeError, ValueError):
        return None
    if not isinstance(value, dict) or value.get("version") != 1:
        return None
    if not isinstance(value.get("markers"), list):
        return None
    # Backward-compatible with the accepted inbound-only checkpoint.
    if not isinstance(value.get("base"), list):
        value["base"] = _wire(value["markers"])
    return value


def _store_metadata(scene, film, revision, entries, base=None):
    document = {
        "version": 1,
        "film": film,
        "revision": revision,
        "base": _wire(entries if base is None else base),
        "markers": entries,
        "fps": _fps(scene),
    }
    scene[META_KEY] = json.dumps(document, sort_keys=True, separators=(",", ":"))


def _validated(fields):
    if not isinstance(fields, dict):
        raise ValueError("marker payload is not an object")
    film = fields.get("film")
    revision = fields.get("revision")
    raw = fields.get("markers")
    if not isinstance(film, str) or not film:
        raise ValueError("marker payload has no film id")
    if not isinstance(revision, str) or _REVISION.fullmatch(revision) is None:
        raise ValueError("marker payload has no SHA-256 revision")
    if not isinstance(raw, list):
        raise ValueError("marker payload has no marker list")

    entries = []
    uuids = set()
    ticks = set()
    for index, item in enumerate(raw):
        if not isinstance(item, dict):
            raise ValueError("marker %d is not an object" % index)
        uuid = item.get("uuid")
        tick = item.get("tick")
        label = item.get("label")
        color = item.get("color")
        if not isinstance(uuid, str) or not uuid or len(uuid) > 128 or uuid in uuids:
            raise ValueError("marker %d has missing/duplicate uuid" % index)
        if type(tick) is not int or tick < 0 or tick in ticks:
            raise ValueError("marker %d has invalid/duplicate tick" % index)
        if not isinstance(label, str):
            raise ValueError("marker %d has no label" % index)
        if type(color) is not int or not -(2 ** 31) <= color < 2 ** 31:
            raise ValueError("marker %d has invalid ARGB color" % index)
        uuids.add(uuid)
        ticks.add(tick)
        entries.append({"uuid": uuid, "tick": tick, "label": label, "color": color})
    return film, revision, entries


def _local_entries(scene, metadata):
    """Attach saved UUID/color/tick to current native markers without modifying them."""
    current = _regular_markers(scene)
    old = metadata.get("markers", []) if metadata else []
    old_signatures = [(item.get("display_label", item.get("label")), item.get("frame")) for item in old]
    new_signatures = [(marker.name, marker.frame) for marker in current]
    matcher = difflib.SequenceMatcher(a=old_signatures, b=new_signatures, autojunk=False)
    assignment = {}
    used_old = set()
    used_new = set()

    for block in matcher.get_matching_blocks():
        for offset in range(block.size):
            oi = block.a + offset
            ni = block.b + offset
            assignment[ni] = old[oi]
            used_old.add(oi)
            used_new.add(ni)

    old_left = [index for index in range(len(old)) if index not in used_old]
    new_left = [index for index in range(len(current)) if index not in used_new]
    for oi, ni in zip(old_left, new_left):
        assignment[ni] = old[oi]

    entries = []
    ticks = set()
    for index, marker in enumerate(current):
        if marker.frame < 0:
            raise ValueError("negative Blender marker frames cannot be represented in BBS")
        previous = assignment.get(index)
        if previous is None:
            item = {"uuid": str(uuidlib.uuid4()), "color": DEFAULT_COLOR}
            tick = frame_to_tick(scene, marker.frame)
        else:
            item = {"uuid": previous["uuid"], "color": previous["color"]}
            tick = (previous["tick"] if marker.frame == previous.get("frame")
                    else frame_to_tick(scene, marker.frame))
        if tick in ticks:
            raise ValueError("multiple Blender markers map to BBS tick %d" % tick)
        ticks.add(tick)
        label = (previous["label"] if previous is not None
                 and marker.name == previous.get("display_label", previous["label"]) else marker.name)
        entries.append({**item, "tick": tick, "label": label, "frame": marker.frame,
                        "display_label": marker.name})
    return entries


def _initial_merge(local, remote):
    by_tick = {item["tick"]: item for item in remote}
    merged = list(remote)
    for item in local:
        existing = by_tick.get(item["tick"])
        if existing is not None:
            if existing["label"] != item["label"]:
                raise ValueError("both editors have different markers at tick %d" % item["tick"])
            continue
        merged.append(item)
    return sorted(merged, key=lambda item: (item["tick"], item["uuid"]))


def _merge_entry(uuid, base, local, remote):
    if local == base:
        return remote, []
    if remote == base:
        return local, []
    if local == remote:
        return local, []
    if base is None:
        return (local, []) if remote is None else (remote, []) if local is None else (None, [uuid])
    if local is None or remote is None:
        return None, [uuid]

    merged = {"uuid": uuid}
    conflicts = []
    for field in ("tick", "label", "color"):
        b_value = base[field]
        l_value = local[field]
        r_value = remote[field]
        if l_value == b_value:
            merged[field] = r_value
        elif r_value == b_value or l_value == r_value:
            merged[field] = l_value
        else:
            conflicts.append(uuid + "/" + field)
    return (merged if not conflicts else None), conflicts


def _three_way(base, local, remote):
    maps = [{item["uuid"]: item for item in entries} for entries in (base, local, remote)]
    merged = []
    conflicts = []
    for uuid in sorted(set(maps[0]) | set(maps[1]) | set(maps[2])):
        item, item_conflicts = _merge_entry(uuid, maps[0].get(uuid), maps[1].get(uuid),
                                            maps[2].get(uuid))
        conflicts.extend(item_conflicts)
        if item is not None:
            merged.append(item)
    if conflicts:
        raise ValueError("concurrent marker conflict: " + ", ".join(conflicts))
    merged.sort(key=lambda item: (item["tick"], item["uuid"]))
    ticks = [item["tick"] for item in merged]
    if len(ticks) != len(set(ticks)):
        raise ValueError("merged markers collide on a BBS tick")
    return merged


def _checked_frame(scene, frame):
    parameter = bpy.types.TimelineMarker.bl_rna.properties['frame']
    if type(frame) is not int or not parameter.hard_min <= frame <= parameter.hard_max:
        raise ValueError('marker frame %r is outside Blender creation range %d..%d'
                         % (frame, parameter.hard_min, parameter.hard_max))
    return frame


def _retime(scene, metadata):
    if metadata is None or metadata.get('fps', _fps(scene)) == _fps(scene):
        return metadata
    entries = _local_entries(scene, metadata)
    old = {entry['uuid']: entry for entry in metadata['markers']}
    if (set(old) != {entry['uuid'] for entry in entries}
            or any(entry['frame'] != old[entry['uuid']]['frame'] for entry in entries)):
        raise ValueError('FPS and local marker positions changed together; reconcile marker timing explicitly')
    frames = [_checked_frame(scene, tick_to_frame(scene, entry['tick'])) for entry in entries]
    native = _regular_markers(scene)
    previous = [marker.frame for marker in native]
    state._markers_applying_remote = True
    try:
        for marker, entry, frame in zip(native, entries, frames):
            marker.frame = entry['frame'] = frame
        _store_metadata(scene, metadata['film'], metadata['revision'], entries, base=metadata['base'])
    except Exception:
        for marker, frame in zip(native, previous):
            marker.frame = frame
        raise
    finally:
        state._markers_applying_remote = False
    return _load_metadata(scene)


def _replace_regular(scene, film, revision, entries, base=None):
    prepared = [(entry, _checked_frame(scene, entry.get('frame', tick_to_frame(scene, entry['tick']))))
                for entry in entries]
    previous = _regular_markers(scene)
    created = []
    state._markers_applying_remote = True
    try:
        stored = []
        # Stage every native marker before deleting any existing one. A native API
        # failure must not turn a rejected snapshot into an empty local marker list.
        try:
            for entry, frame in prepared:
                marker = scene.timeline_markers.new(entry['label'] or 'Marker', frame=0)
                created.append(marker)
                marker.frame = frame
                marker.name = entry['label']
                stored.append({**_wire([entry])[0], 'frame': marker.frame,
                               'display_label': marker.name})
            _store_metadata(scene, film, revision, stored, base=base)
        except Exception:
            for marker in created:
                scene.timeline_markers.remove(marker)
            raise
        for marker in previous:
            scene.timeline_markers.remove(marker)
        state._marker_revision = revision
        state._marker_apply_count += 1
    finally:
        state._markers_applying_remote = False


def handle_snapshot(fields, sequence=None):
    """Apply or merge one authoritative BBS snapshot without losing local work."""
    global _queued_snapshot, _blocked_local
    if _pending is not None:
        _queued_snapshot = (sequence, dict(fields) if isinstance(fields, dict) else fields)
        return True
    try:
        film, revision, remote = _validated(fields)
        if not check_film(film):
            state._marker_status = "film switch; marker sync paused"
            return False
        scene = bpy.context.scene
        metadata = _load_metadata(scene)
        if metadata is not None and metadata.get("film") != film:
            state._marker_status = "marker metadata belongs to another film"
            return False
        metadata = _retime(scene, metadata)
        if metadata is not None and metadata.get("revision") == revision:
            state._marker_revision = revision
            return True

        local = _local_entries(scene, metadata)
        merged = (_initial_merge(local, remote) if metadata is None else
                  _three_way(_wire(metadata["base"]), _wire(local), remote))
        _replace_regular(scene, film, revision, merged, base=remote)
        _blocked_local = None
        if _wire(merged) == remote:
            state._marker_status = "live: %d markers" % len(remote)
        else:
            state._marker_status = "merged local+BBS markers; write pending"
        return True
    except (KeyError, TypeError, ValueError) as exception:
        state._marker_status = "marker sync paused: %s" % exception
        if "concurrent marker conflict" in str(exception) and 'local' in locals():
            _blocked_local = _local_key(local)
        return False


def _request_context(scene):
    return {'scene': scene.session_uid, 'file': bpy.data.filepath,
            'project': state.peer_project, 'endpoint': state._connected_endpoint}


def _send_local(scene, metadata, entries):
    global _pending
    request_id = uuidlib.uuid4().hex
    desired = _wire(entries)
    _pending = {
        "requestId": request_id,
        "deadline": time.monotonic() + TIMEOUT,
        "film": metadata["film"],
        "baseRevision": metadata["revision"],
        "desired": desired,
        "lifetime": state._marker_lifetime,
        "context": _request_context(scene),
    }
    if entries != metadata["markers"]:
        _store_metadata(scene, metadata["film"], metadata["revision"], entries,
                        base=metadata["base"])
    _send({"type": "MARKERS_WRITE", "origin": "blender", "uuid": "markers",
           "seq": _next_seq(), "fields": {"requestId": request_id,
                                               "film": metadata["film"],
                                               "baseRevision": metadata["revision"],
                                               "markers": desired}})
    state._marker_status = "Blender marker write pending: %d" % len(desired)


def pump():
    """20 Hz dirtiness poll; TimelineMarker has no change callback in Blender's API."""
    global _pending, _blocked_local
    if _pending is not None and _pending["lifetime"] != state._marker_lifetime:
        _pending = None
    if _pending is not None and time.monotonic() > _pending["deadline"]:
        _pending = None
        state._marker_status = "marker write timed out; retrying safely"
    if (_pending is not None or state._markers_applying_remote or not state._connected
            or not state._peer_ready or state.film_switch_detected
            or "markers_write_v1" not in state.peer_capabilities):
        return
    scene = bpy.context.scene
    metadata = _load_metadata(scene)
    if metadata is None or metadata.get("film") != state.bound_film:
        return
    try:
        metadata = _retime(scene, metadata)
        local = _local_entries(scene, metadata)
        local_key = _local_key(local)
        if _blocked_local == local_key:
            return
        if _blocked_local is not None:
            _blocked_local = None
        if local != metadata["markers"]:
            _store_metadata(scene, metadata["film"], metadata["revision"], local,
                            base=metadata["base"])
        if _wire(local) != _wire(metadata["base"]):
            _send_local(scene, metadata, local)
    except (KeyError, TypeError, ValueError) as exception:
        state._marker_status = "marker write paused: %s" % exception


def handle_result(fields, sequence=None):
    global _pending, _queued_snapshot, _blocked_local
    if (_pending is None or _pending["lifetime"] != state._marker_lifetime
            or not isinstance(fields, dict)):
        return False
    if fields.get("requestId") != _pending["requestId"]:
        return False
    scene = bpy.context.scene
    metadata = _load_metadata(scene)
    if (_pending.get('context') != _request_context(scene)
            or metadata is None or metadata.get('film') != _pending['film']
            or state.bound_film != _pending['film'] or state.film_switch_detected):
        state._marker_status = 'marker receipt paused: return to the original scene/file/project'
        return False
    if fields.get('ok') is True and fields.get('film') != _pending['film']:
        state._marker_status = 'invalid marker receipt: different film'
        return False

    _pending = None
    if fields.get("ok"):
        try:
            film, revision, remote = _validated(fields)
            scene = bpy.context.scene
            metadata = _retime(scene, _load_metadata(scene))
            local = _local_entries(scene, metadata)
            _store_metadata(scene, film, revision, local, base=remote)
            state._marker_revision = revision
            _blocked_local = None
            state._marker_status = ("live: %d markers%s" %
                                    (len(remote), " (retry acknowledged)"
                                     if fields.get("alreadyApplied") else ""))
        except (KeyError, TypeError, ValueError) as exception:
            state._marker_status = "invalid marker receipt: %s" % exception
    elif fields.get("conflict") == "revision":
        handle_snapshot(fields)
    else:
        state._marker_status = "marker write failed: %s" % (fields.get("error") or "unknown")
        metadata = _load_metadata(bpy.context.scene)
        if metadata is not None:
            try:
                _blocked_local = _local_key(_local_entries(bpy.context.scene, metadata))
            except (KeyError, TypeError, ValueError):
                pass

    queued, _queued_snapshot = _queued_snapshot, None
    if queued is not None:
        queued_sequence, queued_fields = queued
        # BBS outbound sequence is monotonic. A snapshot emitted before this receipt describes
        # the pre-write state and must not roll a successful write back after ACK.
        if (queued_sequence is None or sequence is None or queued_sequence > sequence):
            handle_snapshot(queued_fields, queued_sequence)
    return True
