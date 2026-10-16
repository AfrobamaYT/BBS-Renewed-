"""Unkeyed pose channels as a conditional, expiring render-only BBS preview.

Viewport transforms are not msgbus notifications. Sample editable RNA channels on
the main-thread tick; never sample the constrained/evaluated bone matrix as input.
No F-curves, film values or durable history are authored by this module.
"""
import copy
import math
import time
import uuid
import bpy
from mathutils import Euler, Quaternion
from . import state, actor_channel_sync as sync, actor_outbox, recording_live, shot_freeze
from .core import _send, _next_seq

PROP = 'bbsbridge_pose_preview'
_bases = {}
_active = {}
_pending = {}
_serial = 0
_session = uuid.uuid4().hex
_next = 0.
_status = 'waiting for an imported pose'
_borrowed_timeline_endpoint = None


def raw(pb):
    prop = ('rotation_quaternion' if pb.rotation_mode == 'QUATERNION'
            else 'rotation_axis_angle' if pb.rotation_mode == 'AXIS_ANGLE' else 'rotation_euler')
    return {'location': tuple(pb.location), 'scale': tuple(pb.scale),
            prop: tuple(getattr(pb, prop)), 'mode': pb.rotation_mode}


def wire(values):
    mode = values['mode']
    if mode == 'QUATERNION':
        q = Quaternion(values['rotation_quaternion'])
    elif mode == 'AXIS_ANGLE':
        angle, x, y, z = values['rotation_axis_angle']
        q = Quaternion((x, y, z), angle) if x*x+y*y+z*z > 1e-12 else Quaternion()
    else:
        q = Euler(values['rotation_euler'], mode).to_quaternion()
    q.normalize()
    return {'location': list(values['location']), 'scale': list(values['scale']),
            'quaternion': list(q)}


def same(a, b):
    return all(abs(x-y) < 1e-6 for key in a for x, y in zip(a[key], b[key]))


def adopt(obj, names=None):
    """Called only for actually imported bones, never for unrelated remote changes."""
    if obj.type != 'ARMATURE':
        return
    key = (obj.as_pointer(), str(obj.get('bbs_uuid', '')))
    if key not in _bases:
        names = None
    base = _bases.setdefault(key, {})
    for pb in obj.pose.bones:
        if names is None or pb.name in names:
            base[pb.name] = raw(pb)


def baseline(obj, pb, frame):
    from .actors import _object_fcurve_collections
    key = (obj.as_pointer(), str(obj.get('bbs_uuid', '')))
    if key not in _bases:
        adopt(obj)
    values = copy.deepcopy(_bases[key].setdefault(pb.name, raw(pb)))
    if values['mode'] != pb.rotation_mode:
        # A mode conversion is not an edit; adopt its equivalent representation.
        values = raw(pb)
        _bases[key][pb.name] = values
    from . import pose_overlays
    if obj.get(pose_overlays.SOURCE) is not None:
        return wire(values)
    for collection in _object_fcurve_collections(obj):
        for fc in collection:
            for prop in ('location', 'scale', 'rotation_euler', 'rotation_quaternion', 'rotation_axis_angle'):
                if prop in values and fc.data_path == pb.path_from_id(prop):
                    components = list(values[prop])
                    components[fc.array_index] = fc.evaluate(frame)
                    values[prop] = components
    return wire(values)


def context():
    scene = bpy.context.scene
    return (scene.as_pointer(), state.bound_film, tuple(state._connected_endpoint or ()),
            state.peer_engine, round(scene.frame_current * 20 * scene.render.fps_base / scene.render.fps))


def send(obj, values, ctx):
    global _serial
    base = sync.read(obj)
    remote = (base or {}).get('outbound', {}).get('remote', {})
    expected = {section: {name: copy.deepcopy(remote.get(section, {}).get(name)) for name in values}
                for section in ('bones', 'poseTrack')}
    from . import pose_units
    values = pose_units.preview(obj, values)
    _serial += 1
    rid = uuid.uuid4().hex
    fields = {'posePreview': True, 'film': ctx[1], 'engine': ctx[3], 'tick': ctx[4],
              'client': _session, 'serial': _serial, 'requestId': rid,
              'replays': [{'uuid': str(obj.get('bbs_uuid')), 'bones': values, 'expected': expected}]}
    _send({'type': 'ACTORS_IMPORT', 'origin': 'blender', 'uuid': 'pose-preview',
           'seq': _next_seq(), 'fields': fields})
    _pending[str(obj.get('bbs_uuid'))] = (rid, time.monotonic())


def _claim_timeline(ctx):
    global _borrowed_timeline_endpoint
    if ctx[4] < 0:
        raise RuntimeError('pose preview requires a non-negative BBS tick')
    if state.playhead_owner == 'BLENDER':
        return
    if 'playhead' not in state.peer_capabilities:
        raise RuntimeError('BBS playhead support is required for live pose preview')
    # BBS validates a pose preview against its current cursor. A Blender control
    # edit at another frame must seek first; TCP preserves this message order.
    state.playhead_owner = 'BLENDER'
    _borrowed_timeline_endpoint = ctx[2]
    _send({'type': 'PLAYHEAD', 'origin': 'blender', 'uuid': 'playhead',
           'seq': _next_seq(), 'fields': {'owner': 'BLENDER', 'tick': ctx[4]}})


def _release_timeline_if_idle():
    global _borrowed_timeline_endpoint
    if _active or _borrowed_timeline_endpoint is None:
        return
    endpoint = _borrowed_timeline_endpoint
    _borrowed_timeline_endpoint = None
    if (state.playhead_owner == 'BLENDER'
            and not getattr(bpy.context.scene, 'bbsbridge_drive_timeline', False)):
        state.playhead_owner = 'NOBODY'
        if state._connected and tuple(state._connected_endpoint or ()) == endpoint:
            _send({'type': 'PLAYHEAD', 'origin': 'blender', 'uuid': 'playhead',
                   'seq': _next_seq(), 'fields': {'owner': 'NOBODY'}})


def release(uid):
    entry = _active.pop(uid, None)
    if entry:
        obj, ctx, _names = entry
        try:
            if state._connected and tuple(state._connected_endpoint or ()) == ctx[2]:
                send(obj, {}, ctx)
        except ReferenceError:
            pass


def clear():
    for uid in list(_active):
        release(uid)
    _release_timeline_if_idle()


def handle(fields):
    global _status
    uid = next((uid for uid, pending in _pending.items()
                if fields.get('requestId') == pending[0]), None)
    if uid is None:
        return False
    _pending.pop(uid)
    if fields.get('ok') is True:
        _status = 'live pose preview: %d actors' % len(_active) if _active else 'pose preview released'
    else:
        _status = 'pose preview rejected: ' + str(fields.get('error', 'unknown error'))
        _active.pop(uid, None)
        _release_timeline_if_idle()
    return True


def pump():
    global _next, _status
    now = time.monotonic()
    if now < _next:
        return
    _next = now + .1
    scene = bpy.context.scene
    if (not getattr(scene, PROP, True) or not state._connected or not state._peer_ready
            or state.film_switch_detected or shot_freeze.ist_eingefroren()
            or recording_live._session or state._playhead_bbs_playing
            or any(w.screen.is_animation_playing for w in bpy.context.window_manager.windows)):
        clear()
        return
    if 'actor_pose_preview_v1' not in state.peer_capabilities:
        _status = 'BBS pose-preview support is required'
        return
    if actor_outbox._canonical_refresh_due or actor_outbox._canonical_refresh_pending:
        clear()
        _status = 'waiting for authoritative BBS bone state'
        return
    obj = bpy.context.view_layer.objects.active
    ctx = context()
    from .actors import _find_actor_by_uuid
    for uid, (actor, previous, names) in list(_active.items()):
        if previous != ctx or _find_actor_by_uuid(uid) != actor:
            release(uid)
    # Reclaim a new frame only after the old frame's preview has been cleared.
    # Otherwise our temporary BLENDER ownership hides the required new seek.
    _release_timeline_if_idle()
    candidates = {uid: actor for uid, (actor, _, _) in _active.items()}
    if obj is not None and obj.type == 'ARMATURE' and obj.get('bbs_uuid') and sync.read(obj):
        uid = str(obj['bbs_uuid'])
        if _find_actor_by_uuid(uid) == obj:
            candidates[uid] = obj
    for uid, actor in candidates.items():
        pending = _pending.get(uid)
        if pending and now - pending[1] < 1.:
            continue
        _pending.pop(uid, None)
        names = set(_active.get(uid, (None, None, set()))[2])
        if actor == obj and actor.mode == 'POSE':
            names.update(pb.name for pb in actor.pose.bones if pb.select)
        values = changed_values(actor, names, scene.frame_current)
        if not values:
            release(uid)
            continue
        if uid not in _active and len(_active) >= 32:
            _status = 'pose preview capacity exceeded (32 actors)'
            continue
        try:
            _claim_timeline(ctx)
            _active[uid] = (actor, ctx, set(values))
            send(actor, values, ctx)
        except Exception:
            _active.pop(uid, None)
            _release_timeline_if_idle()
            raise
    _release_timeline_if_idle()
    if not _active and not _pending:
        _status = 'pose preview ready'


def changed_values(obj, names, frame):
    global _status
    values = {}
    driven = {fc.data_path for fc in obj.animation_data.drivers} if obj.animation_data else set()
    for name in names:
        pb = obj.pose.bones.get(name)
        if pb is None or any(pb.path_from_id(p) in driven for p in
                             ('location', 'scale', 'rotation_euler', 'rotation_quaternion', 'rotation_axis_angle')):
            continue
        current = wire(raw(pb))
        if not all(math.isfinite(v) for group in current.values() for v in group):
            _status = 'pose preview contains non-finite values'
            return {}
        if not same(current, baseline(obj, pb, frame)):
            values[name] = current
    return values


def register():
    from . import pose_overlays
    pose_overlays.register()
    setattr(bpy.types.Scene, PROP, bpy.props.BoolProperty(
        name='Live Pose Preview', default=True,
        description='Preview unkeyed pose edits in BBS without changing film keyframes'))


def unregister():
    from . import pose_overlays
    pose_overlays.unregister()
    clear()
    _bases.clear()
    if hasattr(bpy.types.Scene, PROP):
        delattr(bpy.types.Scene, PROP)
