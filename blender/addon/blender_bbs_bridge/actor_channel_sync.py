"""Persisted per-channel merge bases for the existing actor snapshot transport.

Unchanged remote channels never acknowledge or overwrite local work. Changed local
and remote versions of the same curve conflict before any channel is modified.
This module does not implement leases or a general material/rig transaction engine.
"""
import copy
import json
import struct
from . import actor_static_channels as static

KEY = 'bbs_actor_channel_base_v1'
ROOT_CHANNELS = ('x', 'y', 'z', 'yaw', 'bodyYaw', 'scale_x', 'scale_y', 'scale_z')
POSE_INPUTS = {'yaw', 'bodyYaw', 'headYaw', 'pitch', 'sneaking'}
SHAPE_EASINGS = {
    ('SINE', 'EASE_IN'): 'sine_in', ('SINE', 'EASE_OUT'): 'sine_out',
    ('SINE', 'EASE_IN_OUT'): 'sine_inout', ('CIRC', 'EASE_IN'): 'circle_in',
    ('CIRC', 'EASE_OUT'): 'circle_out', ('CIRC', 'EASE_IN_OUT'): 'circle_inout',
    ('QUAD', 'EASE_IN'): 'quad_in', ('QUAD', 'EASE_OUT'): 'quad_out',
    ('QUAD', 'EASE_IN_OUT'): 'quad_inout', ('CUBIC', 'EASE_IN'): 'cubic_in',
    ('CUBIC', 'EASE_OUT'): 'cubic_out', ('CUBIC', 'EASE_IN_OUT'): 'cubic_inout',
    ('QUART', 'EASE_IN'): 'quart_in', ('QUART', 'EASE_OUT'): 'quart_out',
    ('QUART', 'EASE_IN_OUT'): 'quart_inout', ('QUINT', 'EASE_IN'): 'quint_in',
    ('QUINT', 'EASE_OUT'): 'quint_out', ('QUINT', 'EASE_IN_OUT'): 'quint_inout',
    ('BACK', 'EASE_IN'): 'back_in', ('BACK', 'EASE_OUT'): 'back_out',
}
SHAPE_EASINGS_REVERSE = {value: key for key, value in SHAPE_EASINGS.items()}


def current_scene_timing(expected_scale=None):
    import math
    import bpy

    scene = bpy.context.scene
    fps = int(scene.render.fps)
    fps_base = float(scene.render.fps_base)
    if fps <= 0 or not math.isfinite(fps_base) or fps_base <= 0:
        raise RuntimeError('Scene FPS/fps_base is invalid')
    scale = fps / fps_base / 20.0
    if expected_scale is not None and abs(scale - float(expected_scale)) > 1e-12:
        raise RuntimeError('Scene FPS does not match the actor conversion basis')
    return {'fps': fps, 'fpsBase': fps_base}


def scene_timing_scale(timing):
    import math

    if (not isinstance(timing, dict) or type(timing.get('fps')) is not int
            or timing['fps'] <= 0 or type(timing.get('fpsBase')) not in (int, float)
            or not math.isfinite(timing['fpsBase']) or timing['fpsBase'] <= 0):
        raise RuntimeError('Durable scene timing is invalid')
    return timing['fps'] / float(timing['fpsBase']) / 20.0


def apply_scene_timing(timing):
    import bpy

    scene_timing_scale(timing)
    scene = bpy.context.scene
    scene.render.fps = timing['fps']
    scene.render.fps_base = float(timing['fpsBase'])


def read(obj):
    raw = obj.get(KEY)
    if raw is None:
        return None
    data = json.loads(raw)
    if data.get('version') != 1:
        raise RuntimeError('Unsupported actor channel baseline')
    return data


def changed(old, new):
    return {key for key in set(old) | set(new) if old.get(key) != new.get(key)}


def visibility_objects(obj):
    result = []
    stack = [obj]
    while stack:
        current = stack.pop()
        if current.get('bbs_rig_widget'):
            continue
        if current is not obj and current.type in ('CURVE', 'FONT') and current.parent_type == 'BONE':
            # Rig-Bedienelemente (Kurven/Texte an Knochen) gehoeren nicht zum sichtbaren Actor:
            # sie bleiben verborgen, statt mit BBS' visible-Spur eingeblendet zu werden
            # (gemessen 2026-10-14: Mogitys Gesichts-Regler im Hybrid-Render).
            continue
        result.append(current)
        stack.extend(reversed(list(current.children)))
    return result


def visibility_signature(obj):
    states = set()
    for current in visibility_objects(obj):
        if _visible_keyed(current):
            # BBS visible-kanal: hide_* sind bildabhaengig; der replay ist aktiviert
            # (apply_visible_keys raeumt die keys, sobald enabled false wird).
            states.add(True)
            continue
        viewport = bool(current.hide_viewport)
        render = bool(current.hide_render)
        if viewport != render:
            raise RuntimeError('Actor viewport/render visibility disagrees: ' + current.name)
        states.add(not viewport)
    if len(states) != 1:
        raise RuntimeError('Actor root and children must share one visibility state')
    return states.pop()


def apply_visibility(obj, enabled):
    hidden = not bool(enabled)
    for current in visibility_objects(obj):
        current.hide_viewport = hidden
        current.hide_render = hidden


VISIBLE_PATHS = ('hide_viewport', 'hide_render')


def _visible_keyed(obj):
    from . import actors
    return any(fc.data_path in VISIBLE_PATHS
               for coll in actors._object_fcurve_collections(obj) for fc in coll)


def apply_visible_keys(obj, replay, tick_scale):
    """BBS `visible`-kanal (properties.visible, 1/0 je tick) -> CONSTANT-keys auf hide_viewport
    und hide_render von root und kindern. Vor dem ersten key haelt BBS den ersten wert
    (KeyframeChannel.findSegment), Blenders CONSTANT-extrapolation tut dasselbe. Ein deaktivierter
    replay (`enabled` false) bleibt statisch versteckt. Gibt die zahl geschriebener keys."""
    from . import actors
    keys = ((replay.get('properties') or {}).get('visible') if isinstance(replay, dict) else None)
    targets = visibility_objects(obj)
    for current in obj.children_recursive:
        if current.type in ('CURVE', 'FONT') and current.parent_type == 'BONE':
            for coll in actors._object_fcurve_collections(current):
                while True:
                    victim = next((fc for fc in coll if fc.data_path in VISIBLE_PATHS), None)
                    if victim is None:
                        break
                    coll.remove(victim)
            current.hide_render = True   # im Viewport bleiben die Regler zum Animieren sichtbar
    for current in targets:
        for coll in actors._object_fcurve_collections(current):
            # einzeln mit re-scan: remove invalidiert referenzen (_clear_actor_object_fcurves)
            while True:
                victim = next((fc for fc in coll if fc.data_path in VISIBLE_PATHS), None)
                if victim is None:
                    break
                coll.remove(victim)
    if not replay.get('enabled', True) or not isinstance(keys, list) or not keys:
        apply_visibility(obj, replay.get('enabled', True))
        return 0
    written = 0
    for current in targets:
        for kf in keys:
            hidden = float(kf.get('value', 1.0)) < 0.5
            frame = float(kf.get('tick', 0.0)) * tick_scale
            for path in VISIBLE_PATHS:
                setattr(current, path, hidden)
                current.keyframe_insert(path, frame=frame)
                written += 1
        for coll in actors._object_fcurve_collections(current):
            for fc in coll:
                if fc.data_path in VISIBLE_PATHS:
                    for point in fc.keyframe_points:
                        point.interpolation = 'CONSTANT'
    return written

def _curve_signature(fc):
    if fc is None:
        return None
    if fc.modifiers:
        raise RuntimeError('Sync conflict: modified F-curve ' + fc.data_path)
    return {'keys': [[list(k.co), list(k.handle_left), list(k.handle_right),
                      k.interpolation, k.easing, k.handle_left_type, k.handle_right_type,
                      k.amplitude, k.back, k.period] for k in fc.keyframe_points],
            'extrapolation': fc.extrapolation, 'mute': fc.mute}


def signature(obj, section, key, replay):
    from . import actors
    if section == 'channels':
        spec = actors._actor_channel_spec(replay.get('channels') or {}, key)
        fc = actors._find_fcurve(obj, *spec[:2]) if spec else None
    else:
        prop = actors._ITEM_PROP_PREFIX + key
        fc = actors._find_fcurve_by_path(obj, '["%s"]' % prop)
    result = _curve_signature(fc)
    if result is not None and section == 'strings':
        result['ids'] = obj.get(prop + '_ids', '[]')
    return result


def sections(replay):
    return {name: copy.deepcopy(replay.get(name) or {})
            for name in ('channels', 'strings', 'properties')}

def _property_groups(replay):
    props = replay.get('properties') if isinstance(replay, dict) else None
    props = props if isinstance(props, dict) else {}
    return {name: copy.deepcopy(props.get(name) or {})
            for name in ('bones', 'poseTrack')}


def _rig_group(replay):
    props = replay.get('properties') if isinstance(replay, dict) else None
    props = props if isinstance(props, dict) else {}
    rig = props.get('rigProperties')
    return copy.deepcopy(rig) if isinstance(rig, dict) else {}


def _rig_flat(group):
    return {(bone, prop): copy.deepcopy(spec)
            for bone, properties in group.items() if isinstance(bone, str)
            and isinstance(properties, dict)
            for prop, spec in properties.items() if isinstance(prop, str)}


def _rig_semantic_flat(group):
    """Only BBS-persisted channel keys participate in revision/conflict decisions."""
    def bbs_keys(spec):
        if spec is None or not isinstance(spec, dict):
            return spec
        result = []
        for raw in spec.get('keys') or ():
            key = copy.deepcopy(raw)
            # BBS Keyframe tick/handles and the channel's serialized numeric values travel through
            # float-backed native fields. Project both sides to that storage width before diffing;
            # otherwise 6.666666666 (Blender) versus 6.6666665 (BBS) creates a false conflict.
            for field in ('tick', 'value', 'lx', 'ly', 'rx', 'ry'):
                if type(key.get(field)) in (int, float):
                    key[field] = struct.unpack('!f', struct.pack('!f', float(key[field])))[0]
            result.append(key)
        return result

    return {path: bbs_keys(spec)
            for path, spec in _rig_flat(group).items()}


def _rig_nested_set(group, bone, prop, value):
    if value is None:
        properties = group.get(bone)
        if isinstance(properties, dict):
            properties.pop(prop, None)
            if not properties:
                group.pop(bone, None)
        return
    group.setdefault(bone, {})[prop] = copy.deepcopy(value)


def rig_signature(obj, bone, prop):
    from . import actors, actor_bones
    if obj is None or obj.type != 'ARMATURE':
        return None
    pose_bone = obj.pose.bones.get(bone)
    if pose_bone is None or prop not in pose_bone:
        return None
    value = pose_bone.get(prop)
    if isinstance(value, bool):
        kind = 'bool'
    elif isinstance(value, int):
        kind = 'int'
    elif isinstance(value, float):
        kind = 'float'
    else:
        return None
    ui = {}
    try:
        raw = pose_bone.id_properties_ui(prop).as_dict()
        ui = {key: raw[key] for key in ('default', 'min', 'max', 'soft_min', 'soft_max')
              if key in raw and isinstance(raw[key], (bool, int, float))}
    except (KeyError, TypeError, AttributeError, RuntimeError):
        pass
    path = actor_bones._rig_property_path(bone, prop)
    curve = _curve_signature(actors._find_fcurve_by_path(obj, path))
    # An animated ID property's live value legitimately changes with the playhead. Its FCurve is
    # the authored state; only an unkeyed control uses the scalar value as its edit signature.
    return {'kind': kind, 'value': value if curve is None else None, 'ui': ui, 'curve': curve}


def _all_rig_signatures(obj):
    from . import actor_export
    result = {}
    for bone, prop in actor_export._rig_property_paths(obj):
        _rig_nested_set(result, bone, prop, rig_signature(obj, bone, prop))
    return result


def _rig_value_patch(obj, tick_scale, base, degradations):
    from . import actor_export
    state = base.get('rigProperties')
    if not isinstance(state, dict):
        return {}, {}
    remote = _rig_flat(state.get('remote') or {})
    local = _rig_flat(state.get('local') or {})
    fps_changed = base.get('tick_scale') != tick_scale
    desired = {}
    expected = {}

    for bone, prop in sorted(set(remote) | set(local)):
        current = rig_signature(obj, bone, prop)
        baseline = local.get((bone, prop))
        animated = current is not None and current.get('curve') is not None
        if current == baseline and not (fps_changed and animated):
            continue
        value = (None if current is None else actor_export.rig_property_for_object(
            obj, bone, prop, tick_scale, degradations))
        desired.setdefault(bone, {})[prop] = copy.deepcopy(value)
        expected.setdefault(bone, {})[prop] = copy.deepcopy(remote.get((bone, prop)))

    return desired, expected


def _shape_group(replay):
    props = replay.get('properties') if isinstance(replay, dict) else None
    props = props if isinstance(props, dict) else {}
    shapes = props.get('shapeKeys')
    return copy.deepcopy(shapes) if isinstance(shapes, dict) else {}


def shape_signature(obj, name):
    from . import actor_shapekeys
    return _curve_signature(actor_shapekeys.find_shape_curve(obj, name))


def shape_timeline_signature(obj):
    from . import actor_shapekeys
    return _curve_signature(actor_shapekeys.find_shape_timeline_curve(obj))


def _shape_structure(signature_data):
    """Native curve structure excluding only scalar key values."""
    if signature_data is None:
        return None
    result = {'extrapolation': signature_data.get('extrapolation'),
              'mute': signature_data.get('mute'), 'keys': []}
    for saved in signature_data.get('keys', ()):
        co, left, right = saved[:3]
        interpolation, easing, left_type, right_type = saved[3:7]
        row = [round(float(co[0]), 6), interpolation, easing, left_type, right_type,
               saved[7], saved[8], saved[9]]
        if interpolation == 'BEZIER':
            row.extend((round(float(left[0]) - float(co[0]), 6),
                        round(float(left[1]) - float(co[1]), 6),
                        round(float(right[0]) - float(co[0]), 6),
                        round(float(right[1]) - float(co[1]), 6)))
        result['keys'].append(row)
    return result


def _shape_interp(saved, name):
    mode = saved[3]
    if mode == 'LINEAR':
        return 'linear'
    if mode == 'CONSTANT':
        return 'constant'
    if mode == 'BEZIER':
        return 'bezier'
    mapped = SHAPE_EASINGS.get((mode, saved[4]))
    if mapped is not None:
        if mode == 'BACK' and abs(float(saved[8]) - 1.7015800476074219) > 1e-6:
            raise RuntimeError('ShapeKey BACK overshoot is not the BBS default: ' + name)
        return mapped
    raise RuntimeError('Unsupported ShapeKey interpolation on %s: %s' % (name, mode))


def shape_blender_interpolation(interp):
    key = str(interp or 'linear').lower()
    if key == 'linear':
        return 'LINEAR', 'AUTO'
    if key == 'constant':
        return 'CONSTANT', 'AUTO'
    if key == 'bezier':
        return 'BEZIER', 'AUTO'
    if key in SHAPE_EASINGS_REVERSE:
        return SHAPE_EASINGS_REVERSE[key]
    raise RuntimeError('Unsupported exact ShapeKey interpolation: ' + key)


def _shape_wire(signature_data, tick_scale, name, values=True):
    import math
    if (not isinstance(signature_data, dict) or not signature_data.get('keys')
            or signature_data.get('extrapolation') != 'CONSTANT'
            or signature_data.get('mute')):
        raise RuntimeError('ShapeKey curve must be unmuted with constant extrapolation: ' + name)
    out = []
    previous = -1.0
    for saved in signature_data['keys']:
        if len(saved) < 10:
            raise RuntimeError('ShapeKey curve signature is incomplete: ' + name)
        co, left, right = saved[:3]
        frame, value = float(co[0]), float(co[1])
        tick = frame / tick_scale
        item = {
            'tick': tick,
            'interp': _shape_interp(saved, name),
            'lx': max(0.0, (frame - float(left[0])) / tick_scale),
            'ly': float(left[1]) - value,
            'rx': max(0.0, (float(right[0]) - frame) / tick_scale),
            'ry': float(right[1]) - value,
        }
        if values:
            if not math.isfinite(value) or value < 0.0 or value > 1.0:
                raise RuntimeError('ShapeKey value is outside [0,1]: ' + name)
            item['value'] = value
        if (not all(math.isfinite(float(v)) for key, v in item.items()
                    if key not in ('interp',)) or tick < 0.0 or tick <= previous):
            raise RuntimeError('ShapeKey ticks/handles are invalid: ' + name)
        out.append(item)
        previous = tick
    return out


def _same_shape_grid(left, right, tolerance=1e-5):
    if len(left) != len(right):
        return False
    for a, b in zip(left, right):
        if a.get('interp') != b.get('interp'):
            return False
        for field in ('tick', 'lx', 'ly', 'rx', 'ry'):
            if abs(float(a.get(field, 0.0)) - float(b.get(field, 0.0))) > tolerance:
                return False
    return True


def _shape_new_desired(obj, name, current_sig, remote, local, tick_scale):
    """Project a new morph onto BBS' existing shared grid without changing that grid."""
    import math

    template_name = next((candidate for candidate in sorted(remote)
                          if isinstance(remote.get(candidate), list) and remote[candidate]
                          and isinstance(local.get(candidate), dict)), None)
    if template_name is None:
        return _shape_wire(current_sig, tick_scale, name)
    template = remote[template_name]
    desired = _shape_wire(current_sig, tick_scale, name)
    if len(desired) != len(template):
        raise RuntimeError('New ShapeKey morph must use the existing BBS key grid: ' + name)
    for point, target in zip(desired, template):
        if (point['interp'] != target.get('interp')
                or abs(point['tick'] - float(target.get('tick', 0.0))) > 1e-5):
            raise RuntimeError('New ShapeKey morph must use the existing BBS key grid: ' + name)
        if point['interp'] == 'bezier':
            if any(abs(point[field] - float(target.get(field, 0.0))) > 1e-5
                   for field in ('lx', 'ly', 'rx', 'ry')):
                raise RuntimeError('New ShapeKey morph must use the existing BBS key grid: ' + name)
        else:
            # Handles have no effect outside Bezier. Preserve BBS' shared metadata instead of
            # treating Blender's spacing-derived auto handles as a structural difference.
            for field in ('lx', 'ly', 'rx', 'ry'):
                point[field] = copy.deepcopy(target.get(
                    field, 5.0 if field in ('lx', 'rx') else 0.0))
    return desired


def _shape_grid_patch(obj, tick_scale, base):
    """Build one atomic replacement when every synchronized morph changes key count."""
    from . import actor_shapekeys

    state = base.get('shapeKeys')
    if not isinstance(state, dict):
        return None, None
    remote = state.get('remote') if isinstance(state.get('remote'), dict) else {}
    local = state.get('local') if isinstance(state.get('local'), dict) else {}
    expected_timeline = actor_shapekeys.shape_timeline(remote)
    baseline_timeline = state.get('timelineLocal')
    current_timeline = shape_timeline_signature(obj)
    if not remote or not expected_timeline or not isinstance(baseline_timeline, dict):
        return None, None

    signatures = {name: shape_signature(obj, name) for name in remote}
    # A missing whole morph curve is the existing map-member removal path, not a shared-grid edit.
    if any(signature is None for signature in signatures.values()):
        return None, None
    expected_count = len(expected_timeline)
    timeline_count = len((current_timeline or {}).get('keys') or ())
    counts = {name: len((signature or {}).get('keys') or ())
              for name, signature in signatures.items()}
    if timeline_count == expected_count and all(count == expected_count for count in counts.values()):
        return None, None

    if set(remote) != set(local):
        raise RuntimeError('ShapeKey key-count edit cannot be combined with morph add/remove')
    if current_timeline is None or timeline_count == 0:
        raise RuntimeError('ShapeKey shared key grid must retain at least one key')
    if (current_timeline.get('extrapolation') != baseline_timeline.get('extrapolation')
            or current_timeline.get('mute') != baseline_timeline.get('mute')):
        raise RuntimeError('ShapeKey shared timeline extrapolation/mute edits are not enabled yet')
    if any(count != timeline_count for count in counts.values()):
        raise RuntimeError('All synchronized ShapeKeys must use the same shared key grid')

    mesh = actor_shapekeys._mesh_with_shape_keys(obj)
    if mesh is None:
        raise RuntimeError('ShapeKey shared grid lost its mesh')
    reference = mesh.data.shape_keys.reference_key
    keyed_names = {block.name for block in mesh.data.shape_keys.key_blocks
                   if block != reference and actor_shapekeys.find_shape_curve(obj, block.name) is not None}
    if keyed_names != set(remote):
        raise RuntimeError('ShapeKey key-count edit cannot be combined with morph add/remove')

    timeline = _shape_wire(current_timeline, tick_scale, '$grid', values=False)
    for saved in current_timeline.get('keys') or ():
        if len(saved) < 1 or abs(float(saved[0][1])) > 1e-7:
            raise RuntimeError('ShapeKey timeline metadata value edits have no BBS meaning')

    desired = {}
    for name in sorted(remote):
        curve = _shape_wire(signatures[name], tick_scale, name)
        if len(curve) != len(timeline):
            raise RuntimeError('All synchronized ShapeKeys must use the same shared key grid')
        for point, shared in zip(curve, timeline):
            if (point['interp'] != shared['interp']
                    or abs(float(point['tick']) - float(shared['tick'])) > 1e-5):
                raise RuntimeError('All synchronized ShapeKeys must use the same shared key grid')
            if point['interp'] == 'bezier':
                if any(abs(float(point[field]) - float(shared[field])) > 1e-5
                       for field in ('lx', 'ly', 'rx', 'ry')):
                    raise RuntimeError('All synchronized ShapeKeys must use the same shared key grid')
            else:
                for field in ('lx', 'ly', 'rx', 'ry'):
                    point[field] = copy.deepcopy(shared[field])
        desired[name] = curve
    return desired, copy.deepcopy(remote)


def _shape_timeline_patch(obj, tick_scale, base):
    """Build a same-key-count shared tick/interpolation/handle patch."""
    state = base.get('shapeKeys')
    if not isinstance(state, dict) or 'timelineLocal' not in state:
        return None, None
    expected = state.get('timelineRemote')
    baseline = state.get('timelineLocal')
    current = shape_timeline_signature(obj)
    fps_changed = base.get('tick_scale') != tick_scale
    if current == baseline and not fps_changed:
        return None, None
    if fps_changed and not expected and current is None and baseline is None:
        return None, None
    if not isinstance(expected, list) or not expected or not isinstance(baseline, dict) or current is None:
        raise RuntimeError('ShapeKey shared timeline baseline is incomplete')
    if (current.get('extrapolation') != baseline.get('extrapolation')
            or current.get('mute') != baseline.get('mute')):
        raise RuntimeError('ShapeKey shared timeline extrapolation/mute edits are not enabled yet')

    current_keys = current.get('keys') or []
    baseline_keys = baseline.get('keys') or []
    if len(current_keys) != len(baseline_keys) or len(current_keys) != len(expected):
        raise RuntimeError('ShapeKey shared timeline key count/tick edits are not enabled yet')

    desired = _shape_wire(current, tick_scale, '$timeline', values=False)
    for index, (now, before) in enumerate(zip(current_keys, baseline_keys)):
        if len(now) < 10 or len(before) < 10:
            raise RuntimeError('ShapeKey shared timeline signature is incomplete')
        if abs(float(now[0][1]) - float(before[0][1])) > 1e-7:
            raise RuntimeError('ShapeKey timeline metadata value edits have no BBS meaning')
        if (not fps_changed and now[0][0] == before[0][0]
                and now[1] == before[1] and now[2] == before[2]):
            # An interpolation-only edit must not rewrite ignored Blender auto handles with
            # spacing-derived coordinates. Preserve the exact BBS metadata until the user
            # actually moves a tick/handle (or explicitly changes the FPS basis).
            for field in ('tick', 'lx', 'ly', 'rx', 'ry'):
                desired[index][field] = copy.deepcopy(expected[index][field])
    return desired, copy.deepcopy(expected)


def _shape_value_patch(obj, tick_scale, base):
    """Build per-morph value/add/remove patches without changing BBS' shared key grid."""
    import math
    from . import actor_shapekeys

    state = base.get('shapeKeys')
    if not isinstance(state, dict):
        return {}, {}
    remote = state.get('remote') if isinstance(state.get('remote'), dict) else {}
    local = state.get('local') if isinstance(state.get('local'), dict) else {}

    changed_values, expected_values = {}, {}
    for name in sorted(set(remote) | set(local)):
        current_sig = shape_signature(obj, name)
        baseline_sig = local.get(name)
        if current_sig == baseline_sig:
            continue
        if name not in remote:
            raise RuntimeError('ShapeKey baseline missing remote morph: ' + name)
        expected = remote[name]
        if current_sig is None:
            changed_values[name] = None
            expected_values[name] = copy.deepcopy(expected)
            continue
        if baseline_sig is None or _shape_structure(current_sig) != _shape_structure(baseline_sig):
            raise RuntimeError('ShapeKey structural edit is unsafe; shared key timing/interpolation is unchanged: ' + name)
        keys = current_sig.get('keys') or []
        if not isinstance(expected, list) or len(keys) != len(expected):
            raise RuntimeError('ShapeKey key grid changed: ' + name)
        desired = copy.deepcopy(expected)
        for saved, target in zip(keys, desired):
            frame, value = float(saved[0][0]), float(saved[0][1])
            if (not math.isfinite(value) or value < 0.0 or value > 1.0
                    or abs(frame - float(target.get('tick', 0.0)) * tick_scale) > 1e-4):
                raise RuntimeError('ShapeKey value/key grid is invalid: ' + name)
            target['value'] = value
        changed_values[name] = desired
        expected_values[name] = copy.deepcopy(expected)

    mesh = actor_shapekeys._mesh_with_shape_keys(obj)
    if mesh is not None:
        reference = getattr(mesh.data.shape_keys, 'reference_key', None)
        new_names = [block.name for block in mesh.data.shape_keys.key_blocks
                     if block != reference and block.name not in remote and block.name not in local
                     and actor_shapekeys.find_shape_curve(obj, block.name) is not None]
        for name in sorted(new_names):
            current_sig = shape_signature(obj, name)
            changed_values[name] = _shape_new_desired(
                obj, name, current_sig, remote, local, tick_scale)
            expected_values[name] = None

    return changed_values, expected_values


def shape_patch_is_structural(patch):
    if patch.get('shapeGrid'):
        return True
    shapes = patch.get('shapeKeys') or {}
    expected = ((patch.get('expected') or {}).get('shapeKeys') or {})
    return any(value is None or expected.get(name) is None
               for name, value in shapes.items())


def shape_patch_creates_first_grid(patch):
    shapes = patch.get('shapeKeys') or {}
    preconditions = patch.get('expected') or {}
    expected = preconditions.get('shapeKeys') or {}
    return (not preconditions.get('shapeTimeline')
            and bool(shapes) and set(shapes) <= set(expected)
            and all(expected.get(name) is None and isinstance(value, list)
                    for name, value in shapes.items()))


def shape_timeline_is_interp_only(patch):
    desired = patch.get('shapeTimeline')
    expected = (patch.get('expected') or {}).get('shapeTimeline')
    if not desired:
        return True
    if not isinstance(expected, list) or len(desired) != len(expected):
        return False
    return all(all(after.get(field) == before.get(field)
                   for field in ('tick', 'lx', 'ly', 'rx', 'ry'))
               for after, before in zip(desired, expected))


def shape_patch_uses_easing(patch):
    timelines = []
    if patch.get('shapeTimeline'):
        timelines.append(patch['shapeTimeline'])
    timelines.extend(value for value in (patch.get('shapeGrid') or {}).values()
                     if isinstance(value, list))
    timelines.extend(value for value in (patch.get('shapeKeys') or {}).values()
                     if isinstance(value, list))
    return any(str(point.get('interp') or '').lower() in SHAPE_EASINGS_REVERSE
               for timeline in timelines for point in timeline if isinstance(point, dict))


def _outbound_local(obj, tick_scale):
    from . import actor_export
    return {'bones': actor_export.bones_for_object(obj, tick_scale, set())}


def _native_channel_keys(channels):
    """Compare Keyframe's float timing/handles without rounding its double root values.

    An ACK remembers the sent JSON; a later BBS export serializes native floats.
    That representation change is not a remote edit and must not rebuild Blender
    curves (which would replace native AUTO handles with imported FREE handles).
    """
    result = copy.deepcopy(channels)
    for points in result.values():
        if not isinstance(points, list):
            continue
        for point in points:
            for field in ('tick', 'lx', 'ly', 'rx', 'ry'):
                if type(point.get(field)) in (int, float):
                    point[field] = struct.unpack('!f', struct.pack('!f', point[field]))[0]
    return result


def prepare(obj, replay, tick_scale):
    from . import actors
    base = read(obj)
    if base is None and obj.get(actors._ACTOR_SNAPSHOT_PROP) != '':
        raise RuntimeError('Sync-Basis fehlt fuer ' + obj.name + '; explicit import required')
    previous = (base or {}).get('remote') or sections({})
    incoming = sections(replay)
    changes = {name: changed(previous[name], incoming[name]) for name in incoming}
    changes['channels'] = changed(_native_channel_keys(previous['channels']),
                                  _native_channel_keys(incoming['channels']))
    if base and base['tick_scale'] != tick_scale:
        changes['channels'] |= set(previous['channels']) | set(incoming['channels'])
        changes['strings'] |= set(previous['strings']) | set(incoming['strings'])
    if bool(previous['channels'].get('bodyYaw')) != bool(incoming['channels'].get('bodyYaw')):
        changes['channels'] |= {'yaw', 'bodyYaw'}
    for section, supported in (('channels', ROOT_CHANNELS), ('strings', actors._ITEM_CHANNELS)):
        for key in changes[section] & set(supported):
            if base and (signature(obj, section, key, previous) != base['local'][section].get(key)
                         or (section == 'channels' and static.changed(obj, base, key, previous))):
                raise RuntimeError('Sync conflict: %s / %s.%s' % (obj.name, section, key))

    incoming_visibility = bool(replay.get('enabled', True))
    visibility_state = (base or {}).get('visibility')
    visibility_changed = bool(base and (
        not isinstance(visibility_state, dict)
        or visibility_state.get('remote') != incoming_visibility))
    if visibility_changed and isinstance(visibility_state, dict):
        current_visibility = visibility_signature(obj)
        if (current_visibility != visibility_state.get('local')
                and current_visibility != incoming_visibility):
            raise RuntimeError('Sync conflict: %s / visibility' % obj.name)

    old_props, new_props = _property_groups({'properties': previous['properties']}), _property_groups(replay)
    bone_changes = changed(old_props['bones'], new_props['bones'])
    pose_track_changes = changed(old_props['poseTrack'], new_props['poseTrack'])
    pose_reset = bool(old_props['poseTrack']) and not new_props['poseTrack']
    outbound = (base or {}).get('outbound') or {'local': {'bones': {}}}
    current_out = _outbound_local(obj, tick_scale) if base else {'bones': {}}
    local_bones = changed(outbound.get('local', {}).get('bones', {}), current_out['bones'])
    if pose_reset and local_bones:
        raise RuntimeError('Sync conflict: whole pose reset would replace local bone keys: ' + obj.name)
    if (bone_changes | pose_track_changes) & local_bones:
        bone = sorted((bone_changes | pose_track_changes) & local_bones)[0]
        raise RuntimeError('Sync conflict: %s / bones.%s' % (obj.name, bone))

    derived_bones = set()
    model_pose_changed = ((pose_reset or bool(changes['channels'] & POSE_INPUTS))
                          and replay.get('formType') == 'bbs:model'
                          and any(source['channels'].get(key)
                                  for source in (previous, incoming)
                                  for key in ('headYaw', 'bodyYaw', 'sneaking')))
    if model_pose_changed:
        from . import model_pose
        derived_bones = model_pose.owned_bones(obj)
        if derived_bones & local_bones:
            bone = sorted(derived_bones & local_bones)[0]
            raise RuntimeError('Sync conflict: %s / derived pose.%s' % (obj.name, bone))

    old_rig = _rig_semantic_flat(_rig_group({'properties': previous['properties']}))
    new_rig = _rig_semantic_flat(_rig_group(replay))
    rig_changes = changed(old_rig, new_rig)
    rig_state = (base or {}).get('rigProperties')
    if base and rig_changes:
        if not isinstance(rig_state, dict):
            if any(rig_signature(obj, *path) is not None for path in rig_changes):
                raise RuntimeError('Sync-Basis fehlt fuer %s / rigProperties; explicit import required'
                                   % obj.name)
        else:
            local_rig = _rig_flat(rig_state.get('local') or {})
            for path in rig_changes:
                if rig_signature(obj, *path) != local_rig.get(path):
                    raise RuntimeError('Sync conflict: %s / rigProperties.%s.%s'
                                       % (obj.name, path[0], path[1]))

    from . import actor_shapekeys
    old_shapes = _shape_group({'properties': previous['properties']})
    new_shapes = _shape_group(replay)
    old_timeline = actor_shapekeys.shape_timeline(old_shapes)
    new_timeline = actor_shapekeys.shape_timeline(new_shapes)
    timeline_changed = old_timeline != new_timeline
    shape_changes = changed(old_shapes, new_shapes)
    if base and base['tick_scale'] != tick_scale:
        shape_changes |= set(old_shapes) | set(new_shapes)
        timeline_changed = bool(old_timeline or new_timeline)
    shape_state = (base or {}).get('shapeKeys')
    if base and shape_changes:
        if shape_state is None:
            if old_shapes or any(shape_signature(obj, name) is not None for name in shape_changes):
                raise RuntimeError('Sync-Basis fehlt fuer %s / shapeKeys; explicit import required'
                                   % obj.name)
        else:
            local_shapes = shape_state.get('local') or {}
            for name in shape_changes:
                if shape_signature(obj, name) != local_shapes.get(name):
                    raise RuntimeError('Sync conflict: %s / shapeKeys.%s' % (obj.name, name))
    if base and timeline_changed:
        if shape_state is None:
            if shape_timeline_signature(obj) is not None:
                raise RuntimeError('Sync-Basis fehlt fuer %s / shapeKeys timeline; explicit import required'
                                   % obj.name)
        else:
            baseline_timeline = shape_state.get('timelineLocal')
            current_timeline = shape_timeline_signature(obj)
            if baseline_timeline is None and 'timelineLocal' not in shape_state:
                if current_timeline is not None:
                    raise RuntimeError(
                        'Sync-Basis fehlt fuer %s / shapeKeys timeline; explicit import required'
                        % obj.name)
            elif current_timeline != baseline_timeline:
                raise RuntimeError('Sync conflict: %s / shapeKeys.$timeline' % obj.name)

    return {'base': base, 'previous': previous, 'changes': changes,
            'model_pose': model_pose_changed, 'derived_bones': derived_bones, 'pose_reset': pose_reset,
            'properties': bool(changes['properties']),
            'bone_changes': bone_changes, 'pose_track_changes': pose_track_changes,
            'rig_changes': rig_changes, 'visibility_changed': visibility_changed,
            'shape_changes': shape_changes, 'shape_timeline_changed': timeline_changed,
            'pose': (bool(changes['channels'] & POSE_INPUTS)
                     or bool(bone_changes | pose_track_changes | rig_changes))}


def remember(obj, replay, tick_scale, plan=None):
    from . import actors
    base = copy.deepcopy((plan or {}).get('base') or {})
    local = base.setdefault('local', {'channels': {}, 'strings': {}})
    for section, supported in (('channels', ROOT_CHANNELS), ('strings', actors._ITEM_CHANNELS)):
        touched = set(supported) if plan is None else plan['changes'][section] & set(supported)
        for key in touched:
            local[section][key] = signature(obj, section, key, replay)
            if section == 'channels':
                static.remember(base, obj, key, replay)

    incoming_out = _property_groups(replay)
    current_out = _outbound_local(obj, tick_scale)
    outbound = base.setdefault('outbound', {'remote': {'bones': {}, 'poseTrack': {}},
                                            'local': {'bones': {}}})
    if plan is None:
        outbound['remote'] = incoming_out
        outbound['local'] = current_out
    else:
        affected_bones = set(plan.get('bone_changes', ())) | set(plan.get('pose_track_changes', ()))
        if plan.get('pose_reset') and obj.type == 'ARMATURE':
            affected_bones.update(obj.pose.bones.keys())
        for bone in affected_bones:
            for section in ('bones', 'poseTrack'):
                value = incoming_out[section].get(bone)
                if value is None:
                    outbound['remote'].setdefault(section, {}).pop(bone, None)
                else:
                    outbound['remote'].setdefault(section, {})[bone] = copy.deepcopy(value)
            value = current_out['bones'].get(bone)
            if value is None:
                outbound['local'].setdefault('bones', {}).pop(bone, None)
            else:
                outbound['local'].setdefault('bones', {})[bone] = copy.deepcopy(value)

        # Generated look/crouch curves are accepted remote effects, not local edits.
        for bone in plan.get('derived_bones', ()):
            value = current_out['bones'].get(bone)
            if value is None:
                outbound['local'].setdefault('bones', {}).pop(bone, None)
            else:
                outbound['local'].setdefault('bones', {})[bone] = copy.deepcopy(value)

    visibility_state = base.setdefault('visibility', {})
    if (plan is None or plan.get('visibility_changed')
            or type(visibility_state.get('remote')) is not bool
            or type(visibility_state.get('local')) is not bool):
        visibility_state['remote'] = bool(replay.get('enabled', True))
        visibility_state['local'] = visibility_signature(obj)

    incoming_rig = _rig_group(replay)
    rig_state = base.setdefault('rigProperties', {'remote': {}, 'local': {}})
    if plan is None:
        rig_state['remote'] = copy.deepcopy(incoming_rig)
        # Capture every numeric rig control, not only already-remote channels. This is the
        # explicit baseline that lets a later unkeyed Blender control become a safe constant BBS
        # channel without guessing whether it changed before the bridge saw it.
        rig_state['local'] = _all_rig_signatures(obj)
    else:
        incoming_flat = _rig_flat(incoming_rig)
        for bone, prop in plan.get('rig_changes', ()):
            _rig_nested_set(rig_state.setdefault('remote', {}), bone, prop,
                            incoming_flat.get((bone, prop)))
            signature_data = rig_signature(obj, bone, prop)
            _rig_nested_set(rig_state.setdefault('local', {}), bone, prop, signature_data)

    from . import actor_shapekeys
    incoming_shapes = _shape_group(replay)
    incoming_timeline = actor_shapekeys.shape_timeline(incoming_shapes)
    shape_state = base.setdefault('shapeKeys', {'remote': {}, 'local': {}})
    if plan is None:
        shape_state['remote'] = copy.deepcopy(incoming_shapes)
        shape_state['local'] = {name: shape_signature(obj, name) for name in incoming_shapes}
        shape_state['timelineRemote'] = copy.deepcopy(incoming_timeline)
        shape_state['timelineLocal'] = shape_timeline_signature(obj)
    else:
        for name in plan.get('shape_changes', ()):
            value = incoming_shapes.get(name)
            if value is None:
                shape_state['remote'].pop(name, None)
                shape_state['local'].pop(name, None)
            else:
                shape_state['remote'][name] = copy.deepcopy(value)
                shape_state['local'][name] = shape_signature(obj, name)
        if plan.get('shape_timeline_changed'):
            shape_state['timelineRemote'] = copy.deepcopy(incoming_timeline)
            shape_state['timelineLocal'] = shape_timeline_signature(obj)
    remote = sections(replay)
    if plan is not None:
        # Keep the exact acknowledged basis for unchanged paths, including during
        # receipt recovery. Native float formatting alone cannot advance that basis.
        touched = plan['changes']['channels']
        previous_channels = (base.get('remote') or sections({}))['channels']
        remote['channels'] = {
            **{key: copy.deepcopy(value) for key, value in previous_channels.items() if key not in touched},
            **{key: value for key, value in remote['channels'].items() if key in touched},
        }
    base.update(version=1, tick_scale=tick_scale, remote=remote,
                sceneTiming=current_scene_timing(tick_scale))
    obj[KEY] = json.dumps(base, sort_keys=True, separators=(',', ':'), allow_nan=False)


def apply_roots(obj, replay, tick_scale, plan):
    from . import actors
    affected = set()
    for key in plan['changes']['channels']:
        for version in (plan['previous'], replay):
            spec = actors._actor_channel_spec(version.get('channels') or {}, key)
            if spec:
                affected.add(spec[:2])
    for collection in actors._object_fcurve_collections(obj):
        for index in range(len(collection) - 1, -1, -1):
            curve = collection[index]
            if (curve.data_path, curve.array_index) in affected:
                collection.remove(curve)
    count, maximum = 0, 0.0
    for data_path, index in affected:
        getattr(obj, data_path)[index] = 1.0 if data_path == 'scale' else 0.0
    for key, frames in (replay.get('channels') or {}).items():
        spec = actors._actor_channel_spec(replay.get('channels') or {}, key)
        if spec and spec[:2] in affected and frames:
            n, end = actors._build_fcurve(obj, frames, spec, tick_scale)
            count += n
            maximum = max(maximum, end)
    return count, maximum


def strings_for_object(obj, tick_scale):
    from . import actors
    result = {}
    for key in actors._ITEM_CHANNELS:
        prop = actors._ITEM_PROP_PREFIX + key
        fc = actors._find_fcurve_by_path(obj, '["%s"]' % prop)
        if fc is None:
            continue
        ids = json.loads(obj.get(prop + '_ids', '[]'))
        pairs = []
        for point in fc.keyframe_points:
            index = round(float(point.co.y))
            if index < 0 or index >= len(ids):
                raise RuntimeError('Invalid item index: ' + prop)
            pairs.append([float(point.co.x) / tick_scale, ids[index]])
        result[key] = pairs
    return result

def validate(replay):
    import math
    def number(value):
        return isinstance(value, (int, float)) and not isinstance(value, bool) and math.isfinite(value)
    if 'enabled' in replay and type(replay['enabled']) is not bool:
        raise ValueError('Actor enabled must be boolean')
    for key, frames in (replay.get('channels') or {}).items():
        if not isinstance(frames, list):
            raise ValueError('Invalid numeric channel: ' + key)
        for frame in frames:
            if not isinstance(frame, dict) or not all(number(frame.get(k)) for k in ('tick', 'value')):
                raise ValueError('Invalid numeric keyframe: ' + key)
            if any(k in frame and not number(frame[k]) for k in ('lx', 'ly', 'rx', 'ry')):
                raise ValueError('Invalid keyframe handles: ' + key)
    for key, frames in (replay.get('strings') or {}).items():
        if not isinstance(frames, list):
            raise ValueError('Invalid item channel: ' + key)
        for frame in frames:
            if (not isinstance(frame, (list, tuple)) or len(frame) != 2
                    or not number(frame[0]) or not isinstance(frame[1], str) or not frame[1]):
                raise ValueError('Invalid item keyframe: ' + key)
    for bone, properties in (replay.get('rigProperties') or {}).items():
        if not isinstance(bone, str) or not bone or not isinstance(properties, dict):
            raise ValueError('Invalid rig-property bone')
        for prop, spec in properties.items():
            if not isinstance(prop, str) or not prop:
                raise ValueError('Invalid rig-property name')
            if spec is None:
                continue
            if (not isinstance(spec, dict) or spec.get('kind') not in ('float', 'int', 'bool')
                    or not isinstance(spec.get('keys'), list) or not spec['keys']):
                raise ValueError('Invalid rig-property schema: %s/%s' % (bone, prop))
            for frame in spec['keys']:
                if (not isinstance(frame, dict)
                        or not all(number(frame.get(key)) for key in ('tick', 'value'))
                        or any(key in frame and not number(frame[key])
                               for key in ('lx', 'ly', 'rx', 'ry'))):
                    raise ValueError('Invalid rig-property keyframe: %s/%s' % (bone, prop))


def local_patch(obj, tick_scale, desired_channels, desired_strings, desired_bones=None,
                basis=None, degradations=None):
    """Return locally changed protected paths plus their last remote values."""
    from . import actors, state
    base = read(obj) if basis is None else basis
    if base is None:
        raise RuntimeError('Sync-Basis fehlt fuer ' + obj.name + '; explicit import required')
    remote = base.get('remote') or sections({})
    local = base.get('local') or {'channels': {}, 'strings': {}}
    current = {'channels': desired_channels or {}, 'strings': desired_strings or {}}
    patch = {'uuid': str(obj.get('bbs_uuid') or ''), 'expected': {}}
    if degradations is None:
        degradations = set()
    fps_changed = base.get('tick_scale') != tick_scale

    for section, supported in (('channels', ROOT_CHANNELS), ('strings', actors._ITEM_CHANNELS)):
        changed_values = {}
        expected_values = {}
        context = {'channels': current['channels'], 'strings': current['strings']}
        for key, value in current[section].items():
            if key not in supported:
                continue
            sig = signature(obj, section, key, context)
            if (not (fps_changed and sig and sig.get('keys')) and sig == local.get(section, {}).get(key)
                    and not (section == 'channels' and static.changed(obj, base, key, context))):
                continue
            if section == 'channels' and key.startswith('scale_'):
                if 'actor_root_scale_v1' not in state.peer_capabilities:
                    raise RuntimeError('BBS peer lacks actor root-scale support')
            changed_values[key] = copy.deepcopy(value)
            expected_values[key] = copy.deepcopy(remote.get(section, {}).get(key))
        if section == 'channels' and set(changed_values) & {'yaw', 'bodyYaw'}:
            for key in ('yaw', 'bodyYaw'):
                if key in current[section]:
                    changed_values[key] = copy.deepcopy(current[section][key])
                    expected_values[key] = copy.deepcopy(remote.get(section, {}).get(key))
        if changed_values:
            patch[section] = changed_values
            patch['expected'][section] = expected_values

    outbound = base.get('outbound') or {'remote': {}, 'local': {}}
    remote_out = outbound.get('remote') or {}
    local_out = outbound.get('local') or {}
    desired_bones = desired_bones or {}
    changed_bones = {bone: copy.deepcopy(value) for bone, value in desired_bones.items()
                     if fps_changed or value != local_out.get('bones', {}).get(bone)}
    if changed_bones:
        patch['bones'] = changed_bones
        patch['expected']['bones'] = {bone: copy.deepcopy(remote_out.get('bones', {}).get(bone))
                                      for bone in changed_bones}
        patch['expected']['poseTrack'] = {
            bone: copy.deepcopy(remote_out.get('poseTrack', {}).get(bone)) for bone in changed_bones}

    changed_rig, expected_rig = _rig_value_patch(obj, tick_scale, base, degradations)
    if changed_rig:
        patch['rigProperties'] = changed_rig
        patch['expected']['rigProperties'] = expected_rig

    visibility_state = base.get('visibility') or {}
    current_visibility = visibility_signature(obj)
    if current_visibility != visibility_state.get('local'):
        if type(visibility_state.get('remote')) is not bool:
            raise RuntimeError('Actor visibility baseline is incomplete')
        patch['visibility'] = current_visibility
        patch['expected']['visibility'] = visibility_state['remote']

    grid, expected_grid = _shape_grid_patch(obj, tick_scale, base)
    if grid:
        patch['shapeGrid'] = grid
        patch['expected']['shapeGrid'] = expected_grid
    else:
        changed_shapes, expected_shapes = _shape_value_patch(obj, tick_scale, base)
        if changed_shapes:
            patch['shapeKeys'] = changed_shapes
            patch['expected']['shapeKeys'] = expected_shapes
            # A new morph can join an existing physical timeline. Carry that basis
            # so durable checkpoints do not mistake it for first-grid creation.
            if any(value is None for value in expected_shapes.values()):
                patch['expected']['shapeTimeline'] = copy.deepcopy(
                    (base.get('shapeKeys') or {}).get('timelineRemote') or [])

        timeline, expected_timeline = _shape_timeline_patch(obj, tick_scale, base)
        if timeline:
            patch['shapeTimeline'] = timeline
            patch['expected']['shapeTimeline'] = expected_timeline

    return patch if any(name in patch for name in (
        'channels', 'strings', 'bones', 'rigProperties', 'shapeKeys', 'shapeTimeline',
        'shapeGrid', 'visibility')) else None


def capture_export_signatures(obj, patch):
    """Capture authored signatures before sending, never when the receipt arrives."""
    result = {section: {key: signature(obj, section, key, patch)
                        for key in (patch.get(section) or {})}
              for section in ('channels', 'strings')}
    result['static'] = {key: actual for key in patch.get('channels', {})
                        if (actual := static.value(obj, key, patch)) is not None}
    result['shapeKeys'] = {name: shape_signature(obj, name)
                           for name in (patch.get('shapeKeys') or {})}
    if patch.get('shapeGrid'):
        result['shapeGrid'] = {name: shape_signature(obj, name)
                               for name in patch['shapeGrid']}
        result['shapeGridTimeline'] = shape_timeline_signature(obj)
    result['rigProperties'] = {}
    for bone, properties in (patch.get('rigProperties') or {}).items():
        for prop in properties:
            result['rigProperties'].setdefault(bone, {})[prop] = rig_signature(obj, bone, prop)
    if 'visibility' in patch:
        result['visibility'] = visibility_signature(obj)
    if patch.get('shapeTimeline'):
        base = read(obj) or {}
        names = ((base.get('shapeKeys') or {}).get('remote') or {}).keys()
        result['shapeTimeline'] = shape_timeline_signature(obj)
        result['shapeTimelineMorphs'] = {
            name: shape_signature(obj, name) for name in names}
    base = read(obj) or {}
    result['sceneTiming'] = current_scene_timing()
    result['previousSceneTiming'] = copy.deepcopy(base.get('sceneTiming'))
    return result


def _signature_with_timeline_interp(signature_data, timeline, source_signature=None):
    if signature_data is None:
        return None
    result = copy.deepcopy(signature_data)
    keys = result.get('keys') or []
    if len(keys) != len(timeline):
        return result
    source_keys = ((source_signature or {}).get('keys') or [])
    if source_signature is not None and len(source_keys) != len(keys):
        raise RuntimeError('Cannot project ShapeKey easing across different key counts')
    for index, (saved, raw) in enumerate(zip(keys, timeline)):
        mode, easing = shape_blender_interpolation(raw.get('interp'))
        saved[3] = mode
        saved[4] = source_keys[index][4] if source_signature is not None else easing
    return result


def _signature_with_timeline_full(signature_data, timeline, scale):
    result = _signature_with_timeline_interp(signature_data, timeline)
    if result is None:
        return None
    keys = result.get('keys') or []
    if len(keys) != len(timeline):
        return result
    for saved, raw in zip(keys, timeline):
        f32 = lambda value: struct.unpack('!f', struct.pack('!f', float(value)))[0]
        frame = f32(float(raw.get('tick', 0.0)) * scale)
        value = f32(saved[0][1])
        saved[0][0] = frame
        saved[1] = [f32(frame - float(raw.get('lx', 5.0)) * scale),
                    f32(value + float(raw.get('ly', 0.0)))]
        saved[2] = [f32(frame + float(raw.get('rx', 5.0)) * scale),
                    f32(value + float(raw.get('ry', 0.0)))]
        saved[5] = saved[6] = 'FREE'
    return result


def _signature_with_timeline_effect(signature_data, source_signature):
    """Copy only shared timeline-owned fields while retaining current scalar values."""
    if signature_data is None or source_signature is None:
        return None
    result = copy.deepcopy(signature_data)
    keys = result.get('keys') or []
    source_keys = source_signature.get('keys') or []
    if len(keys) != len(source_keys):
        raise RuntimeError('Cannot project ShapeKey timeline effect across different key counts')
    f32 = lambda value: struct.unpack('!f', struct.pack('!f', float(value)))[0]
    for saved, source in zip(keys, source_keys):
        if len(saved) < 10 or len(source) < 10:
            raise RuntimeError('Cannot project incomplete ShapeKey timeline signatures')
        value = f32(saved[0][1])
        source_value = float(source[0][1])
        saved[0][0] = f32(source[0][0])
        saved[1] = [f32(source[1][0]), f32(value + float(source[1][1]) - source_value)]
        saved[2] = [f32(source[2][0]), f32(value + float(source[2][1]) - source_value)]
        saved[3] = source[3]
        saved[4] = source[4]
        saved[5] = source[5]
        saved[6] = source[6]
    return result


def remember_remote_fingerprint(obj, base, tick_scale):
    from . import actors

    replay = {'uuid': str(obj.get('bbs_uuid') or ''), **copy.deepcopy(base['remote'])}
    replay['enabled'] = bool((base.get('visibility') or {}).get('remote', True))
    fingerprint = actors._snapshot_fingerprint([replay], tick_scale)
    obj[actors._ACTOR_SNAPSHOT_PROP] = fingerprint or ''


def acknowledge_export(obj, patch, tick_scale, sent_local):
    """Acknowledge only sent state; the pure projection is also used by the durable queue."""
    base = read(obj)
    if base is None:
        return False
    first_shapes = patch.get('shapeKeys') or {}
    shape_remote = ((base.get('shapeKeys') or {}).get('remote') or {})
    if first_shapes and not shape_remote:
        from . import actor_shapekeys
        timeline = actor_shapekeys.shape_timeline(
            {name: curve for name, curve in first_shapes.items() if isinstance(curve, list)})
        if timeline:
            mesh = actor_shapekeys._mesh_with_shape_keys(obj)
            if mesh is None:
                raise RuntimeError('First ShapeKey ACK lost its mesh')
            actor_shapekeys._apply_shape_timeline(mesh.data.shape_keys, timeline, tick_scale)
            sent_local['shapeTimeline'] = shape_timeline_signature(obj)
    if patch.get('shapeTimeline'):
        from . import actor_shapekeys
        names = ((base.get('shapeKeys') or {}).get('remote') or {}).keys()
        actor_shapekeys.apply_shape_timeline_interpolation(
            obj, patch['shapeTimeline'], tick_scale, names,
            sent_local.get('shapeTimelineMorphs') or {},
            full=not shape_timeline_is_interp_only(patch))
    base = advanced_base(base, patch, tick_scale, sent_local)
    obj[KEY] = json.dumps(base, sort_keys=True, separators=(',', ':'), allow_nan=False)
    # No echo snapshot follows a successful Blender write. Advance the per-object dedup basis to
    # the accepted remote state here; otherwise a later native BBS undo back to the pre-write
    # snapshot is mistaken for an already-seen snapshot and never reaches Blender.
    remember_remote_fingerprint(obj, base, tick_scale)
    return True


def advanced_base(base, patch, tick_scale, sent_local):
    """Project an accepted/queued patch without marking any Blender object as synced."""
    from . import actors
    base = copy.deepcopy(base)
    remote = copy.deepcopy(base.get('remote') or sections({}))
    local = base.setdefault('local', {'channels': {}, 'strings': {}})

    for section in ('channels', 'strings'):
        for key, value in (patch.get(section) or {}).items():
            remote.setdefault(section, {})[key] = copy.deepcopy(value)

    for section, supported in (('channels', ROOT_CHANNELS), ('strings', actors._ITEM_CHANNELS)):
        for key in set(patch.get(section) or {}) & set(supported):
            local.setdefault(section, {})[key] = copy.deepcopy(sent_local[section][key])
            if section == 'channels':
                base.setdefault('static', {}).pop(key, None)
                if key in sent_local.get('static', {}):
                    base['static'][key] = sent_local['static'][key]

    outbound = base.setdefault('outbound', {'remote': {'bones': {}, 'poseTrack': {}},
                                            'local': {'bones': {}}})
    props = remote.setdefault('properties', {})
    for bone, value in (patch.get('bones') or {}).items():
        outbound['remote'].setdefault('bones', {})[bone] = copy.deepcopy(value)
        outbound['remote'].setdefault('poseTrack', {}).pop(bone, None)
        outbound['local'].setdefault('bones', {})[bone] = copy.deepcopy(value)
        props.setdefault('bones', {})[bone] = copy.deepcopy(value)
        if isinstance(props.get('poseTrack'), dict):
            props['poseTrack'].pop(bone, None)

    rig_state = base.setdefault('rigProperties', {'remote': {}, 'local': {}})
    rig_props = props.setdefault('rigProperties', {})
    sent_rig = sent_local.get('rigProperties') or {}
    for bone, properties in (patch.get('rigProperties') or {}).items():
        for prop, value in properties.items():
            _rig_nested_set(rig_state.setdefault('remote', {}), bone, prop, value)
            _rig_nested_set(rig_props, bone, prop, value)
            signature_data = ((sent_rig.get(bone) or {}).get(prop))
            _rig_nested_set(rig_state.setdefault('local', {}), bone, prop, signature_data)
    if not rig_props:
        props.pop('rigProperties', None)

    if 'visibility' in patch:
        visibility_state = base.setdefault('visibility', {})
        visibility_state['remote'] = bool(patch['visibility'])
        visibility_state['local'] = bool(sent_local['visibility'])

    shape_state = base.setdefault('shapeKeys', {'remote': {}, 'local': {}})
    shape_props = props.setdefault('shapeKeys', {})
    if patch.get('shapeGrid'):
        from . import actor_shapekeys
        grid = copy.deepcopy(patch['shapeGrid'])
        shape_state['remote'] = copy.deepcopy(grid)
        shape_state['local'] = copy.deepcopy(sent_local.get('shapeGrid') or {})
        shape_state['timelineRemote'] = actor_shapekeys.shape_timeline(grid)
        shape_state['timelineLocal'] = copy.deepcopy(sent_local.get('shapeGridTimeline'))
        props['shapeKeys'] = shape_props = copy.deepcopy(grid)
    for name, value in (patch.get('shapeKeys') or {}).items():
        if value is None:
            shape_state.setdefault('remote', {}).pop(name, None)
            shape_state.setdefault('local', {}).pop(name, None)
            shape_props.pop(name, None)
        else:
            shape_state.setdefault('remote', {})[name] = copy.deepcopy(value)
            shape_state.setdefault('local', {})[name] = copy.deepcopy(
                (sent_local.get('shapeKeys') or {}).get(name))
            shape_props[name] = copy.deepcopy(value)

    if (not shape_state.get('timelineRemote') and shape_props
            and any(isinstance(value, list) for value in (patch.get('shapeKeys') or {}).values())):
        from . import actor_shapekeys
        first_timeline = actor_shapekeys.shape_timeline(shape_props)
        if first_timeline:
            shape_state['timelineRemote'] = copy.deepcopy(first_timeline)
            shape_state['timelineLocal'] = copy.deepcopy(sent_local.get('shapeTimeline'))

    timeline = patch.get('shapeTimeline')
    if timeline:
        shape_state['timelineRemote'] = copy.deepcopy(timeline)
        shape_state['timelineLocal'] = copy.deepcopy(sent_local.get('shapeTimeline'))
        sent_morphs = sent_local.get('shapeTimelineMorphs') or {}
        for name, signature_data in sent_morphs.items():
            projector = (_signature_with_timeline_interp if shape_timeline_is_interp_only(patch)
                         else lambda signature, raw: _signature_with_timeline_full(
                             signature, raw, tick_scale))
            shape_state.setdefault('local', {})[name] = projector(signature_data, timeline)
        for curves in (shape_state.setdefault('remote', {}), shape_props):
            for curve in curves.values():
                if not isinstance(curve, list) or len(curve) != len(timeline):
                    continue
                for point, metadata in zip(curve, timeline):
                    if not isinstance(point, dict):
                        continue
                    for field in ('tick', 'interp', 'lx', 'ly', 'rx', 'ry'):
                        point[field] = copy.deepcopy(metadata[field])

    if not shape_props:
        props.pop('shapeKeys', None)
    base.update(version=1, tick_scale=tick_scale, remote=remote)
    timing = sent_local.get('sceneTiming')
    if isinstance(timing, dict):
        base['sceneTiming'] = copy.deepcopy(timing)
    return base


def merge_patch_base(current, source, patch, allow_time_basis=False):
    """Copy only a compensated patch's merge metadata, preserving newer sibling bases."""
    timeline = None
    full_timeline_effect = False
    if 'shapeTimeline' in patch:
        import math
        fixed = ('tick', 'lx', 'ly', 'rx', 'ry')
        fields = set(fixed) | {'interp'}

        def number(value):
            return type(value) in (int, float) and math.isfinite(value)

        def signature_keys(signature, raw, scale):
            if (not isinstance(signature, dict)
                    or not isinstance(signature.get('keys'), list)
                    or len(signature['keys']) != len(raw)
                    or signature.get('extrapolation') not in ('CONSTANT', 'LINEAR')
                    or type(signature.get('mute')) is not bool):
                raise ValueError('missing or incompatible local signature')
            for saved, key in zip(signature['keys'], raw):
                if (not isinstance(saved, list) or len(saved) != 10
                        or any(not isinstance(pair, list) or len(pair) != 2
                               or not all(number(v) for v in pair) for pair in saved[:3])
                        or not all(isinstance(v, str) for v in saved[3:7])
                        or not all(number(v) for v in saved[7:])
                        or saved[3] != shape_blender_interpolation(key['interp'])[0]
                        or (key['interp'] in SHAPE_EASINGS_REVERSE
                            and saved[4] != shape_blender_interpolation(key['interp'])[1])
                        or abs(saved[0][0] - key['tick'] * scale) > 1e-4):
                    raise ValueError('local signature grid/interpolation differs from timeline')
            return signature['keys']

        def timeline_basis(base):
            try:
                shape = base['shapeKeys']
                raw, scale = shape['timelineRemote'], base['tick_scale']
                if (base['version'] != 1 or not number(scale) or scale <= 0
                        or not isinstance(raw, list) or not raw):
                    raise ValueError('missing timeline or invalid FPS basis')
                for i, key in enumerate(raw):
                    if (not isinstance(key, dict) or set(key) != fields
                            or not all(number(key[f]) for f in fixed)
                            or key['interp'] not in (
                                {'linear', 'constant', 'bezier'} | set(SHAPE_EASINGS_REVERSE))
                            or key['tick'] < 0 or key['lx'] < 0 or key['rx'] < 0
                            or (i and key['tick'] <= raw[i - 1]['tick'])):
                        raise ValueError('invalid shared timeline metadata')
                signature_keys(shape['timelineLocal'], raw, scale)
                remote_shapes, local_shapes = shape['remote'], shape['local']
                if (not isinstance(remote_shapes, dict) or not remote_shapes
                        or not isinstance(local_shapes, dict)
                        or set(remote_shapes) != set(local_shapes)
                        or remote_shapes != base['remote']['properties']['shapeKeys']):
                    raise ValueError('missing or disagreeing ShapeKey bases')
                for name, points in remote_shapes.items():
                    if (not isinstance(points, list) or len(points) != len(raw)):
                        raise ValueError('remote morph grid differs from timeline')
                    for point, key in zip(points, raw):
                        if (not isinstance(point, dict) or not fields <= set(point)
                                or not all(number(point[f]) for f in fixed)
                                or not number(point.get('value'))
                                or any(point[f] != key[f] for f in fields)):
                            raise ValueError('remote morph metadata differs from timeline')
                    signature_keys(local_shapes[name], raw, scale)
                return shape, raw, scale
            except (KeyError, TypeError, ValueError, OverflowError) as exc:
                raise RuntimeError('Cannot compensate ShapeKey timeline base: ' + str(exc)) from exc

        source_shapes, timeline, scale = timeline_basis(source)
        current_shapes, previous_timeline, previous_scale = timeline_basis(current)
        full_timeline_effect = not shape_timeline_is_interp_only(patch)
        if ((not allow_time_basis and scale != previous_scale)
                or len(timeline) != len(previous_timeline)
                or set(source_shapes['local']) != set(current_shapes['local'])):
            raise RuntimeError('Cannot compensate ShapeKey timeline base: key count/targets/FPS changed')
        if (not full_timeline_effect
                and (any(a[f] != b[f] for a, b in zip(timeline, previous_timeline) for f in fixed)
                     or _signature_with_timeline_interp(
                         current_shapes['timelineLocal'], timeline,
                         source_shapes['timelineLocal'])
                     != source_shapes['timelineLocal'])):
            raise RuntimeError('Cannot compensate interpolation-only ShapeKey timeline base: grid changed')

    current = copy.deepcopy(current)
    source = source or {}
    remote = current.setdefault('remote', sections({}))
    source_remote = source.get('remote') or sections({})
    local = current.setdefault('local', {'channels': {}, 'strings': {}})
    source_local = source.get('local') or {}

    def copy_slot(target, origin, key):
        if key in origin:
            target[key] = copy.deepcopy(origin[key])
        else:
            target.pop(key, None)

    for section in ('channels', 'strings'):
        for key in (patch.get(section) or {}):
            copy_slot(remote.setdefault(section, {}), source_remote.get(section) or {}, key)
            copy_slot(local.setdefault(section, {}), source_local.get(section) or {}, key)
            if section == 'channels':
                copy_slot(current.setdefault('static', {}), source.get('static') or {}, key)

    if 'visibility' in patch:
        source_visibility = source.get('visibility') or {}
        if (type(source_visibility.get('remote')) is not bool
                or type(source_visibility.get('local')) is not bool):
            raise RuntimeError('Cannot compensate incomplete actor visibility base')
        current['visibility'] = copy.deepcopy(source_visibility)

    outbound = current.setdefault('outbound', {'remote': {'bones': {}, 'poseTrack': {}},
                                               'local': {'bones': {}}})
    source_outbound = source.get('outbound') or {}
    props = remote.setdefault('properties', {})
    source_props = source_remote.get('properties') or {}
    for bone in (patch.get('bones') or {}):
        for section in ('bones', 'poseTrack'):
            copy_slot(outbound.setdefault('remote', {}).setdefault(section, {}),
                      (source_outbound.get('remote') or {}).get(section) or {}, bone)
            copy_slot(props.setdefault(section, {}), source_props.get(section) or {}, bone)
        copy_slot(outbound.setdefault('local', {}).setdefault('bones', {}),
                  (source_outbound.get('local') or {}).get('bones') or {}, bone)

    current_rig = current.setdefault('rigProperties', {'remote': {}, 'local': {}})
    source_rig = source.get('rigProperties') or {'remote': {}, 'local': {}}
    rig_props = props.setdefault('rigProperties', {})
    source_rig_props = source_props.get('rigProperties') or {}
    for bone, properties in (patch.get('rigProperties') or {}).items():
        for prop in properties:
            for section in ('remote', 'local'):
                origin = ((source_rig.get(section) or {}).get(bone) or {})
                value = origin.get(prop) if prop in origin else None
                _rig_nested_set(current_rig.setdefault(section, {}), bone, prop, value)
            origin = source_rig_props.get(bone) or {}
            value = origin.get(prop) if prop in origin else None
            _rig_nested_set(rig_props, bone, prop, value)
    if not rig_props:
        props.pop('rigProperties', None)

    current_shapes = current.setdefault('shapeKeys', {'remote': {}, 'local': {}})
    source_shapes = source.get('shapeKeys') or {'remote': {}, 'local': {}}
    source_shape_props = source_props.get('shapeKeys') or {}
    shape_props = props.setdefault('shapeKeys', {})
    if patch.get('shapeGrid'):
        from . import actor_shapekeys
        names = set(patch['shapeGrid'])
        source_grid = source_shapes.get('remote') or {}
        source_local_shapes = source_shapes.get('local') or {}
        timeline_grid = source_shapes.get('timelineRemote')
        if ((not allow_time_basis and source.get('tick_scale') != current.get('tick_scale'))
                or set(source_grid) != names or set(source_local_shapes) != names
                or source_shape_props != source_grid
                or actor_shapekeys.shape_timeline(source_grid) != timeline_grid
                or not isinstance(source_shapes.get('timelineLocal'), dict)):
            raise RuntimeError('Cannot compensate incomplete ShapeKey grid base')
        current_shapes = copy.deepcopy(source_shapes)
        current['shapeKeys'] = current_shapes
        shape_props = copy.deepcopy(source_shape_props)
        props['shapeKeys'] = shape_props
    for name in (patch.get('shapeKeys') or {}):
        copy_slot(current_shapes.setdefault('remote', {}),
                  source_shapes.get('remote') or {}, name)
        copy_slot(current_shapes.setdefault('local', {}),
                  source_shapes.get('local') or {}, name)
        copy_slot(shape_props, source_shape_props, name)
    if shape_patch_creates_first_grid(patch):
        for key in ('timelineRemote', 'timelineLocal'):
            copy_slot(current_shapes, source_shapes, key)
    if timeline is not None:
        # Compensate metadata, never roll sibling values back to the target's old snapshot.
        current_shapes['timelineRemote'] = copy.deepcopy(timeline)
        current_shapes['timelineLocal'] = copy.deepcopy(source_shapes['timelineLocal'])
        for remote_shapes in (current_shapes['remote'], shape_props):
            for points in remote_shapes.values():
                for point, metadata in zip(points, timeline):
                    point.update(copy.deepcopy(metadata))
        for name, signature in current_shapes['local'].items():
            current_shapes['local'][name] = (
                _signature_with_timeline_effect(signature, source_shapes['local'][name])
                if full_timeline_effect
                else _signature_with_timeline_interp(
                    signature, timeline, source_shapes['local'][name]))
    if not shape_props:
        props.pop('shapeKeys', None)

    current.update(version=1, remote=remote)
    return current
