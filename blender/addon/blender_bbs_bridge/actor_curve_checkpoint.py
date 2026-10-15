"""Exact native Blender F-curve checkpoints for queued protected actor paths.

This stores authored Blender coordinates/handles, not another BBS conversion.
Only paths named by a protected patch are restored; other actions and curves stay.
"""
import json
import math
import struct
import bpy
from . import actors

KEY_FIELDS = ('interpolation', 'easing', 'handle_left_type', 'handle_right_type',
              'amplitude', 'back', 'period')


def _f32(value):
    return struct.unpack('!f', struct.pack('!f', float(value)))[0]


def paths(obj, patch):
    result = set()
    for key in patch.get('channels', {}):
        spec = actors._actor_channel_spec(patch['channels'], key)
        if spec:
            result.add(spec[:2])
    for key in patch.get('strings', {}):
        if key not in actors._ITEM_CHANNELS:
            raise RuntimeError('Unprotected item path in checkpoint')
        result.add(('["%s%s"]' % (actors._ITEM_PROP_PREFIX, key), 0))
    for name in patch.get('bones', {}):
        bone = obj.pose.bones.get(name) if obj.type == 'ARMATURE' else None
        if bone is None:
            raise RuntimeError('Missing queued bone: ' + name)
        for prop, size in (('location', 3), ('rotation_euler', 3), ('rotation_quaternion', 4),
                           ('rotation_axis_angle', 4), ('scale', 3)):
            result.update((bone.path_from_id(prop), index) for index in range(size))
    return sorted(result)


def check_target(obj, path, index):
    ad = obj.animation_data
    if ad is not None:
        if any(fc.data_path == path and fc.array_index == index for fc in ad.drivers):
            raise RuntimeError('Queued curve is driven: ' + path)
        if ad.action is not None and any(other != obj and other.animation_data is not None
                and other.animation_data.action == ad.action
                and getattr(other.animation_data, 'action_slot', None) == getattr(ad, 'action_slot', None)
                for other in bpy.data.objects):
            raise RuntimeError('Queued action slot is shared; make it single-user first')
    matches = [fc for coll in actors._object_fcurve_collections(obj) for fc in coll
               if fc.data_path == path and fc.array_index == index]
    if len(matches) > 1 or any(fc.modifiers for fc in matches):
        raise RuntimeError('Queued curve has layers or modifiers: ' + path)
    return matches[0] if matches else None


def _shape_target(obj, name):
    from . import actor_shapekeys

    mesh = actor_shapekeys._mesh_with_shape_keys(obj)
    if mesh is None:
        raise RuntimeError('Queued ShapeKey mesh missing: ' + name)
    key_data = mesh.data.shape_keys
    block = key_data.key_blocks.get(name)
    if block is None:
        raise RuntimeError('Queued ShapeKey morph missing: ' + name)
    path = actor_shapekeys.shape_path(name)
    ad = key_data.animation_data
    if ad is not None:
        if any(fc.data_path == path and fc.array_index == 0 for fc in ad.drivers):
            raise RuntimeError('Queued ShapeKey curve is driven: ' + name)
        if ad.action is not None and any(
                other != key_data and getattr(other, 'animation_data', None) is not None
                and other.animation_data.action == ad.action
                and getattr(other.animation_data, 'action_slot', None)
                == getattr(ad, 'action_slot', None)
                for other in getattr(bpy.data, 'shape_keys', ())):
            raise RuntimeError('Queued ShapeKey action slot is shared: ' + name)
    fc = actor_shapekeys.find_shape_curve(obj, name)
    if fc is not None and fc.modifiers:
        raise RuntimeError('Queued ShapeKey curve has modifiers: ' + name)
    return key_data, block, path, fc


def _shape_timeline_target(obj):
    from . import actor_shapekeys

    mesh = actor_shapekeys._mesh_with_shape_keys(obj)
    if mesh is None:
        raise RuntimeError('Queued ShapeKey timeline mesh missing')
    key_data = mesh.data.shape_keys
    path = actor_shapekeys.shape_timeline_path()
    ad = key_data.animation_data
    if ad is not None:
        if any(fc.data_path == path and fc.array_index == 0 for fc in ad.drivers):
            raise RuntimeError('Queued ShapeKey timeline is driven')
        if ad.action is not None and any(
                other != key_data and getattr(other, 'animation_data', None) is not None
                and other.animation_data.action == ad.action
                and getattr(other.animation_data, 'action_slot', None)
                == getattr(ad, 'action_slot', None)
                for other in getattr(bpy.data, 'shape_keys', ())):
            raise RuntimeError('Queued ShapeKey action slot is shared: timeline')
    fc = actor_shapekeys.find_shape_timeline_curve(obj)
    if fc is not None and fc.modifiers:
        raise RuntimeError('Queued ShapeKey timeline has modifiers')
    return key_data, path, fc


def _rig_target(obj, bone, prop):
    from . import actor_bones

    if obj.type != 'ARMATURE' or obj.pose.bones.get(bone) is None:
        raise RuntimeError('Queued rig-control bone missing: ' + bone)
    pose_bone = obj.pose.bones[bone]
    path = actor_bones._rig_property_path(bone, prop)
    fc = check_target(obj, path, 0)
    if fc is not None and prop not in pose_bone:
        raise RuntimeError('Rig-control FCurve targets a missing property: ' + bone + '/' + prop)
    return pose_bone, path, fc


def _rig_saved(obj, bone, prop):
    pose_bone, path, fc = _rig_target(obj, bone, prop)
    exists = prop in pose_bone
    result = {'path': path, 'exists': exists, 'curve': _saved_curve(fc)}
    if exists:
        value = pose_bone.get(prop)
        if isinstance(value, bool):
            kind = 'bool'
        elif isinstance(value, int):
            kind = 'int'
        elif isinstance(value, float):
            kind = 'float'
        else:
            raise RuntimeError('Queued rig control is not numeric: ' + bone + '/' + prop)
        ui = {}
        try:
            raw = pose_bone.id_properties_ui(prop).as_dict()
            ui = {key: raw[key] for key in ('default', 'min', 'max', 'soft_min', 'soft_max')
                  if key in raw and isinstance(raw[key], (bool, int, float))}
        except (KeyError, TypeError, AttributeError, RuntimeError):
            pass
        result.update(kind=kind, value=value, ui=ui)
    return result


def _saved_curve(fc):
    if fc is None:
        return None
    return {'extrapolation': fc.extrapolation, 'mute': fc.mute,
            'keys': [[list(k.co), list(k.handle_left), list(k.handle_right)]
                     + [getattr(k, name) for name in KEY_FIELDS] for k in fc.keyframe_points]}


def _visibility_saved(obj):
    from . import actor_channel_sync as sync

    return [{'name': current.name, 'viewport': bool(current.hide_viewport),
             'render': bool(current.hide_render)}
            for current in sorted(sync.visibility_objects(obj), key=lambda item: item.name)]


def _visibility_targets(obj, saved):
    from . import actor_channel_sync as sync

    current = {item.name: item for item in sync.visibility_objects(obj)}
    if (not isinstance(saved, list) or len(saved) != len(current)
            or any(not isinstance(entry, dict)
                   or set(entry) != {'name', 'viewport', 'render'}
                   or entry['name'] not in current
                   or type(entry['viewport']) is not bool
                   or type(entry['render']) is not bool for entry in saved)
            or len({entry['name'] for entry in saved}) != len(saved)):
        raise RuntimeError('Checkpoint actor visibility hierarchy changed')
    return current


def _timeline_shape_names(obj):
    from . import actor_channel_sync as sync

    base = sync.read(obj) or {}
    shapes = base.get('shapeKeys') or {}
    names = set((shapes.get('remote') or {}).keys()) | set((shapes.get('local') or {}).keys())
    if not names:
        raise RuntimeError('ShapeKey timeline has no synchronized morph targets')
    return sorted(names)


def _capture_timeline_morphs(obj, names=None):
    result = {}
    for name in sorted(_timeline_shape_names(obj) if names is None else set(names)):
        _key_data, _block, path, fc = _shape_target(obj, name)
        if fc is None:
            raise RuntimeError('Synchronized ShapeKey curve missing: ' + name)
        result[name] = {
            'path': path,
            'version': 2,
            'keys': [_timeline_effect_key(point) for point in fc.keyframe_points],
        }
    return result


def _timeline_effect_key(point):
    """Capture timeline-owned state relative to the scalar value it must not own."""
    frame, value = float(point.co.x), float(point.co.y)
    return {
        'frame': frame,
        'left': [float(point.handle_left.x) - frame, float(point.handle_left.y) - value],
        'right': [float(point.handle_right.x) - frame, float(point.handle_right.y) - value],
        **{name: getattr(point, name) for name in KEY_FIELDS},
    }


def _timeline_effect_v2(fragment):
    return isinstance(fragment, dict) and fragment.get('version') == 2


def has_full_timeline_effect(checkpoint):
    fragments = checkpoint.get('shapeTimelineMorphs') if isinstance(checkpoint, dict) else None
    return bool(fragments) and all(_timeline_effect_v2(fragment) for fragment in fragments.values())


def _validate_timeline_effect(fragment, name, count):
    keys = fragment.get('keys') if isinstance(fragment, dict) else None
    if not isinstance(keys, list) or len(keys) != count:
        raise RuntimeError('Checkpoint ShapeKey timeline grid changed: ' + name)
    if not _timeline_effect_v2(fragment):
        for key in keys:
            if (not isinstance(key, list) or len(key) != 2
                    or type(key[0]) not in (int, float) or not math.isfinite(key[0])
                    or not isinstance(key[1], str)):
                raise RuntimeError('Legacy ShapeKey timeline effect is invalid: ' + name)
        return keys

    expected = {'frame', 'left', 'right'} | set(KEY_FIELDS)
    previous = -math.inf
    for key in keys:
        if not isinstance(key, dict) or set(key) != expected:
            raise RuntimeError('ShapeKey timeline effect fields changed: ' + name)
        if (type(key['frame']) not in (int, float) or not math.isfinite(key['frame'])
                or key['frame'] <= previous):
            raise RuntimeError('ShapeKey timeline effect frames are invalid: ' + name)
        for side in ('left', 'right'):
            pair = key[side]
            if (not isinstance(pair, list) or len(pair) != 2
                    or any(type(value) not in (int, float) or not math.isfinite(value)
                           for value in pair)):
                raise RuntimeError('ShapeKey timeline effect handles are invalid: ' + name)
        if any(not isinstance(key[field], str) for field in KEY_FIELDS[:4]):
            raise RuntimeError('ShapeKey timeline effect modes are invalid: ' + name)
        if any(type(key[field]) not in (int, float) or not math.isfinite(key[field])
               for field in KEY_FIELDS[4:]):
            raise RuntimeError('ShapeKey timeline effect easing values are invalid: ' + name)
        previous = key['frame']
    return keys


def _timeline_morph_targets(obj, checkpoint):
    saved = checkpoint.get('shapeTimelineMorphs')
    from . import actor_channel_sync as sync
    names = (set(checkpoint['patch'].get('shapeKeys') or {})
             if sync.shape_patch_creates_first_grid(checkpoint['patch'])
             else set(_timeline_shape_names(obj)))
    if not isinstance(saved, dict) or set(saved) != names:
        raise RuntimeError('Checkpoint ShapeKey timeline morph targets changed')
    result = {}
    for name, fragment in saved.items():
        _key_data, _block, path, fc = _shape_target(obj, name)
        if not isinstance(fragment, dict) or fragment.get('path') != path or fc is None:
            raise RuntimeError('Checkpoint ShapeKey timeline grid changed: ' + name)
        keys = _validate_timeline_effect(fragment, name, len(fc.keyframe_points))
        result[name] = (fc, keys)
    return result


def _validate_present_grid_checkpoint(obj, checkpoint, names):
    saved_shapes = checkpoint.get('shapeCurves')
    fragments = checkpoint.get('shapeTimelineMorphs')
    timeline = checkpoint.get('shapeTimelineCurve')
    timeline_curve = timeline.get('curve') if isinstance(timeline, dict) else None
    if (not names or not isinstance(saved_shapes, dict) or set(saved_shapes) != names
            or not isinstance(fragments, dict) or set(fragments) != names
            or not has_full_timeline_effect(checkpoint)
            or not isinstance(timeline_curve, dict)
            or not isinstance(timeline_curve.get('keys'), list)
            or not timeline_curve['keys']):
        raise RuntimeError('Variable ShapeKey grid checkpoint is incomplete')
    count = len(timeline_curve['keys'])
    reference = _validate_timeline_effect(fragments[sorted(names)[0]], sorted(names)[0], count)
    if any(abs(float(row[0][0]) - float(effect['frame'])) > 1e-4
           for row, effect in zip(timeline_curve['keys'], reference)):
        raise RuntimeError('Variable ShapeKey metadata/timeline grid differs')
    for name in sorted(names):
        _key_data, _block, path, _fc = _shape_target(obj, name)
        saved = saved_shapes[name]
        curve = saved.get('curve') if isinstance(saved, dict) else None
        fragment = fragments[name]
        if (not isinstance(curve, dict) or not isinstance(curve.get('keys'), list)
                or len(curve['keys']) != count or fragment.get('path') != path):
            raise RuntimeError('Variable ShapeKey grid checkpoint differs: ' + name)
        keys = _validate_timeline_effect(fragment, name, count)
        if any(abs(float(row[0][0]) - float(effect['frame'])) > 1e-4
               for row, effect in zip(curve['keys'], keys)):
            raise RuntimeError('Variable ShapeKey curve/timeline grid differs: ' + name)


def _validate_shape_grid_checkpoint(obj, checkpoint):
    _validate_present_grid_checkpoint(
        obj, checkpoint, set(checkpoint['patch'].get('shapeGrid') or {}))


def _validate_first_grid_checkpoint(obj, checkpoint, names):
    timeline = checkpoint.get('shapeTimelineCurve')
    curve = timeline.get('curve') if isinstance(timeline, dict) else None
    fragments = checkpoint.get('shapeTimelineMorphs')
    if curve is None:
        if (not isinstance(timeline, dict) or timeline.get('exists') is not False
                or fragments != {}):
            raise RuntimeError('Absent first ShapeKey grid checkpoint is invalid')
        return
    _validate_present_grid_checkpoint(obj, checkpoint, names)


def _apply_fragment_to_shape_curves(checkpoint):
    fragments = checkpoint.get('shapeTimelineMorphs') or {}
    for name, saved in checkpoint.get('shapeCurves', {}).items():
        curve = saved.get('curve')
        fragment = fragments.get(name)
        if curve is None or fragment is None:
            continue
        keys = fragment['keys']
        if len(curve['keys']) != len(keys):
            raise RuntimeError('ShapeKey timeline effect grid differs from explicit morph: ' + name)
        if not _timeline_effect_v2(fragment):
            if any(abs(float(row[0][0]) - float(key[0])) > 1e-4
                   for row, key in zip(curve['keys'], keys)):
                raise RuntimeError(
                    'Legacy ShapeKey timeline effect grid differs from explicit morph: ' + name)
            for row, key in zip(curve['keys'], keys):
                row[3] = key[1]
            continue
        for row, key in zip(curve['keys'], keys):
            frame, value = _f32(key['frame']), _f32(row[0][1])
            row[0][0] = frame
            row[1] = [_f32(frame + float(key['left'][0])),
                      _f32(value + float(key['left'][1]))]
            row[2] = [_f32(frame + float(key['right'][0])),
                      _f32(value + float(key['right'][1]))]
            for index, field in enumerate(KEY_FIELDS, 3):
                row[index] = key[field]


def project_timeline_effect(checkpoint, timeline, tick_scale, full):
    """Project the exact post-ACK timeline effect without owning morph scalar values."""
    from . import actor_channel_sync as sync

    result = json.loads(json.dumps(checkpoint, allow_nan=False))
    fragments = result.get('shapeTimelineMorphs')
    if (not isinstance(fragments, dict) or not isinstance(timeline, list)
            or type(tick_scale) not in (int, float) or not math.isfinite(tick_scale)
            or tick_scale <= 0):
        raise RuntimeError('ShapeKey timeline checkpoint lacks its morph effect')
    for name, fragment in fragments.items():
        keys = _validate_timeline_effect(fragment, name, len(timeline))
        if not _timeline_effect_v2(fragment):
            # Existing on-disk v1 records only ever represented interpolation-only writes.
            for key, raw in zip(keys, timeline):
                interpolation = str(raw.get('interp') or '').lower() if isinstance(raw, dict) else ''
                if interpolation not in ('linear', 'constant'):
                    raise RuntimeError('Unsupported legacy durable ShapeKey interpolation')
                key[1] = interpolation.upper()
            continue
        if len(keys) != len(timeline):
            raise RuntimeError('ShapeKey timeline effect length differs: ' + name)
        for key, raw in zip(keys, timeline):
            interpolation = str(raw.get('interp') or '').lower() if isinstance(raw, dict) else ''
            mode, easing = sync.shape_blender_interpolation(interpolation)
            key['interpolation'] = mode
            key['easing'] = easing
            if not full:
                continue
            values = [raw.get(field) for field in ('tick', 'lx', 'ly', 'rx', 'ry')]
            if any(type(value) not in (int, float) or not math.isfinite(value) for value in values):
                raise RuntimeError('Invalid durable ShapeKey timeline transform')
            tick, lx, ly, rx, ry = map(float, values)
            if tick < 0 or lx < 0 or rx < 0:
                raise RuntimeError('Invalid durable ShapeKey timeline transform')
            key.update(
                frame=tick * tick_scale,
                left=[-lx * tick_scale, ly],
                right=[rx * tick_scale, ry],
                handle_left_type='FREE',
                handle_right_type='FREE',
            )
    _apply_fragment_to_shape_curves(result)
    return json.loads(json.dumps(result, allow_nan=False))


def project_first_grid_effect(obj, checkpoint, shape_keys, tick_scale):
    """Build the deterministic post-ACK metadata checkpoint, leaving Blender unchanged."""
    from . import actor_shapekeys

    timeline = actor_shapekeys.shape_timeline(shape_keys)
    mesh = actor_shapekeys._mesh_with_shape_keys(obj)
    if mesh is None or not timeline or actor_shapekeys.find_shape_timeline_curve(obj) is not None:
        raise RuntimeError('First ShapeKey grid projection needs an absent metadata timeline')
    key_data = mesh.data.shape_keys
    prop = 'bbs_shape_timeline'
    existed = prop in key_data
    old_value = key_data.get(prop, 0.0)
    result = json.loads(json.dumps(checkpoint, allow_nan=False))
    try:
        actor_shapekeys._apply_shape_timeline(key_data, timeline, tick_scale)
        _key_data, _path, curve = _shape_timeline_target(obj)
        result['shapeTimelineCurve'] = {
            'exists': True, 'value': float(key_data.get(prop, 0.0)),
            'curve': _saved_curve(curve),
        }
        result['shapeTimelineMorphs'] = _capture_timeline_morphs(obj, shape_keys)
    finally:
        actor_shapekeys._remove_shape_timeline_curve(key_data)
        if existed:
            key_data[prop] = old_value
        else:
            key_data.pop(prop, None)
    return json.loads(json.dumps(result, allow_nan=False))


def capture(obj, patch):
    result = {'patch': patch, 'curves': [], 'props': {}, 'modes': {}}
    from . import actor_channel_sync as sync
    first_grid = sync.shape_patch_creates_first_grid(patch)
    shape_paths = patch.get('shapeGrid') or patch.get('shapeKeys')
    if shape_paths:
        result['shapeCurves'] = {}
        for name in shape_paths:
            _key_data, block, _path, fc = _shape_target(obj, name)
            result['shapeCurves'][name] = {
                'value': float(block.value), 'curve': _saved_curve(fc)}
    if 'shapeTimeline' in patch or patch.get('shapeGrid') or first_grid:
        key_data, path, fc = _shape_timeline_target(obj)
        prop = json.loads(path[1:-1])
        result['shapeTimelineCurve'] = {
            'exists': prop in key_data,
            'value': float(key_data.get(prop, 0.0)),
            'curve': _saved_curve(fc),
        }
        result['shapeTimelineMorphs'] = (
            _capture_timeline_morphs(obj, patch.get('shapeKeys')) if fc is not None and first_grid
            else _capture_timeline_morphs(obj) if fc is not None else {})
    if patch.get('rigProperties'):
        result['rigProperties'] = {}
        for bone, properties in patch['rigProperties'].items():
            for prop in properties:
                result['rigProperties'].setdefault(bone, {})[prop] = _rig_saved(
                    obj, bone, prop)
    if 'visibility' in patch:
        result['visibility'] = _visibility_saved(obj)
    for name in patch.get('bones', {}):
        result['modes'][name] = obj.pose.bones[name].rotation_mode
    for key in patch.get('strings', {}):
        prop = actors._ITEM_PROP_PREFIX + key
        for suffix, default in (('', 0), ('_ids', '[]')):
            name = prop + suffix
            result['props'][name] = {'exists': name in obj, 'value': obj.get(name, default)}
    for path, index in paths(obj, patch):
        fc = check_target(obj, path, index)
        if path.startswith('["'):
            value = obj.get(json.loads(path[1:-1]), 0)
        else:
            value = obj.path_resolve(path)
        entry = {'path': path, 'index': index,
                 'value': float(value if isinstance(value, (float, int)) else value[index]),
                 'curve': _saved_curve(fc)}
        result['curves'].append(entry)
    return json.loads(json.dumps(result, allow_nan=False))


def validate(obj, checkpoint):
    from . import actor_channel_sync as sync
    first_grid = sync.shape_patch_creates_first_grid(checkpoint['patch'])
    allowed = set(paths(obj, checkpoint['patch']))
    grid_edit = bool(checkpoint['patch'].get('shapeGrid'))
    wanted_shapes = set(checkpoint['patch'].get('shapeKeys', {})) | set(
        checkpoint['patch'].get('shapeGrid', {}))
    saved_shapes = checkpoint.get('shapeCurves', {})
    if set(saved_shapes) != wanted_shapes:
        raise RuntimeError('Checkpoint ShapeKey paths do not match its protected patch')
    for name in wanted_shapes:
        _shape_target(obj, name)
    wants_timeline = 'shapeTimeline' in checkpoint['patch'] or grid_edit or first_grid
    if ('shapeTimelineCurve' in checkpoint) != wants_timeline:
        raise RuntimeError('Checkpoint ShapeKey timeline does not match its protected patch')
    if wants_timeline:
        _shape_timeline_target(obj)
        if first_grid:
            _validate_first_grid_checkpoint(obj, checkpoint, wanted_shapes)
        elif grid_edit:
            _validate_shape_grid_checkpoint(obj, checkpoint)
        else:
            _timeline_morph_targets(obj, checkpoint)
    wanted_rig = {(bone, prop) for bone, properties
                  in checkpoint['patch'].get('rigProperties', {}).items()
                  for prop in properties}
    saved_rig = {(bone, prop) for bone, properties
                 in checkpoint.get('rigProperties', {}).items()
                 for prop in properties}
    if saved_rig != wanted_rig:
        raise RuntimeError('Checkpoint rig-control paths do not match its protected patch')
    for bone, prop in wanted_rig:
        pose_bone, path, _fc = _rig_target(obj, bone, prop)
        saved = checkpoint['rigProperties'][bone][prop]
        if saved.get('path') != path or type(saved.get('exists')) is not bool:
            raise RuntimeError('Checkpoint rig-control identity changed: ' + bone + '/' + prop)
        if saved['exists'] and saved.get('kind') not in ('float', 'int', 'bool'):
            raise RuntimeError('Checkpoint rig-control type is invalid: ' + bone + '/' + prop)
    wants_visibility = 'visibility' in checkpoint['patch']
    if ('visibility' in checkpoint) != wants_visibility:
        raise RuntimeError('Checkpoint visibility does not match its protected patch')
    if wants_visibility:
        _visibility_targets(obj, checkpoint['visibility'])
    if set((entry['path'], entry['index']) for entry in checkpoint['curves']) != allowed:
        raise RuntimeError('Checkpoint paths do not match its protected patch')
    for entry in checkpoint['curves']:
        check_target(obj, entry['path'], entry['index'])
    for name in checkpoint['modes']:
        if obj.type != 'ARMATURE' or obj.pose.bones.get(name) is None:
            raise RuntimeError('Queued bone no longer exists: ' + name)
    json.dumps(checkpoint, allow_nan=False)


def _apply_saved_curve(fc, data):
    fc.keyframe_points.clear()
    fc.extrapolation, fc.mute = data['extrapolation'], data['mute']
    fc.keyframe_points.add(len(data['keys']))
    for point, saved in zip(fc.keyframe_points, data['keys']):
        point.handle_left_type = point.handle_right_type = 'FREE'
        point.co, point.handle_left, point.handle_right = saved[:3]
        for prop, value in zip(KEY_FIELDS, saved[3:]):
            setattr(point, prop, value)
    fc.update()


def _remove_curve(obj, path, index=0):
    for collection in actors._object_fcurve_collections(obj):
        found = collection.find(path, index=index)
        if found is not None:
            collection.remove(found)
            return


def _cast_rig(value, kind):
    if kind == 'bool':
        return bool(round(value))
    if kind == 'int':
        return int(round(value))
    return float(value)


def _restore_rig(obj, bone, prop, saved):
    pose_bone, path, fc = _rig_target(obj, bone, prop)
    data = saved['curve']
    if not saved['exists']:
        if fc is not None:
            _remove_curve(obj, path)
        pose_bone.pop(prop, None)
        return

    kind = saved['kind']
    pose_bone[prop] = _cast_rig(saved['value'], kind)
    try:
        ui = pose_bone.id_properties_ui(prop)
        kwargs = {key: _cast_rig(value, kind) for key, value in saved.get('ui', {}).items()}
        if kwargs:
            ui.update(**kwargs)
    except (KeyError, TypeError, AttributeError, RuntimeError):
        pass
    if data is None:
        if fc is not None:
            _remove_curve(obj, path)
        return
    if fc is None:
        frame = data['keys'][0][0][0] if data['keys'] else 0
        if not obj.keyframe_insert(data_path=path, frame=frame):
            raise RuntimeError('Could not restore queued rig-control FCurve: ' + bone + '/' + prop)
        fc = actors._find_fcurve_by_path(obj, path)
        if fc is None:
            raise RuntimeError('Restored rig-control FCurve missing: ' + bone + '/' + prop)
    _apply_saved_curve(fc, data)


def restore(obj, checkpoint):
    validate(obj, checkpoint)
    for bone, properties in checkpoint.get('rigProperties', {}).items():
        for prop, saved in properties.items():
            _restore_rig(obj, bone, prop, saved)
    if 'visibility' in checkpoint:
        targets = _visibility_targets(obj, checkpoint['visibility'])
        for saved in checkpoint['visibility']:
            targets[saved['name']].hide_viewport = saved['viewport']
            targets[saved['name']].hide_render = saved['render']
    if 'shapeTimelineCurve' in checkpoint:
        from . import actor_shapekeys

        key_data, path, fc = _shape_timeline_target(obj)
        prop = json.loads(path[1:-1])
        saved = checkpoint['shapeTimelineCurve']
        data = saved['curve']
        if saved['exists']:
            key_data[prop] = saved['value']
        elif data is None:
            key_data.pop(prop, None)
        if data is None:
            if fc is not None:
                actor_shapekeys._remove_shape_timeline_curve(key_data)
        else:
            if prop not in key_data:
                key_data[prop] = saved['value']
            if fc is None:
                frame = data['keys'][0][0][0] if data['keys'] else 0
                if not key_data.keyframe_insert(data_path=path, frame=frame):
                    raise RuntimeError('Could not restore queued ShapeKey timeline F-curve')
                fc = actor_shapekeys.find_shape_timeline_curve(obj)
                if fc is None:
                    raise RuntimeError('Restored ShapeKey timeline F-curve missing')
            _apply_saved_curve(fc, data)
    if checkpoint.get('shapeCurves'):
        from . import actor_shapekeys

        for name, saved in checkpoint['shapeCurves'].items():
            key_data, block, path, fc = _shape_target(obj, name)
            block.value = saved['value']
            data = saved['curve']
            if data is None:
                if fc is not None:
                    actor_shapekeys._remove_shape_curve(key_data, name)
                continue
            if fc is None:
                frame = data['keys'][0][0][0] if data['keys'] else 0
                if not key_data.keyframe_insert(data_path=path, frame=frame):
                    raise RuntimeError('Could not restore queued ShapeKey F-curve: ' + name)
                fc = actor_shapekeys.find_shape_curve(obj, name)
                if fc is None:
                    raise RuntimeError('Restored ShapeKey F-curve missing: ' + name)
            _apply_saved_curve(fc, data)
    if checkpoint.get('shapeTimelineMorphs'):
        fragments = checkpoint['shapeTimelineMorphs']
        for name, (fc, keys) in _timeline_morph_targets(obj, checkpoint).items():
            if not _timeline_effect_v2(fragments[name]):
                for point, saved in zip(fc.keyframe_points, keys):
                    point.interpolation = saved[1]
                fc.update()
                continue
            points = list(fc.keyframe_points)
            for point, saved in zip(points, keys):
                value = float(point.co.y)
                frame = float(saved['frame'])
                point.handle_left_type = point.handle_right_type = 'FREE'
                point.co.x = frame
                point.handle_left = (frame + float(saved['left'][0]),
                                     value + float(saved['left'][1]))
                point.handle_right = (frame + float(saved['right'][0]),
                                      value + float(saved['right'][1]))
                for field in KEY_FIELDS:
                    setattr(point, field, saved[field])
            fc.update()
    for name, mode in checkpoint['modes'].items():
        obj.pose.bones[name].rotation_mode = mode
    for entry in checkpoint['curves']:
        path, index, value = entry['path'], entry['index'], entry['value']
        fc = check_target(obj, path, index)
        data = entry['curve']
        if path.startswith('["'):
            obj[json.loads(path[1:-1])] = entry['value']
        else:
            obj.path_resolve(path)[index] = entry['value']
        if data is None:
            if fc is not None:
                for coll in actors._object_fcurve_collections(obj):
                    found = coll.find(path, index=index)
                    if found is not None:
                        coll.remove(found)
                        break
            continue
        if fc is None:
            if not obj.keyframe_insert(data_path=path, index=-1 if path.startswith('["') else index,
                                       frame=data['keys'][0][0][0] if data['keys'] else 0):
                raise RuntimeError('Could not restore queued F-curve: ' + path)
            fc = actors._find_fcurve(obj, path, index)
        _apply_saved_curve(fc, data)
    for prop, saved in checkpoint['props'].items():
        if isinstance(saved, dict) and set(saved) == {'exists', 'value'}:
            if saved['exists']:
                obj[prop] = saved['value']
            else:
                obj.pop(prop, None)
        else:
            # Schema-v1 queued checkpoints stored the value directly.
            obj[prop] = saved


def full_patch(obj):
    """Checkpoint editable paths, not rig-driven outputs that cannot be restored."""
    from . import actor_channel_sync as sync, actor_shapekeys, actor_bones

    driven = {(fc.data_path, fc.array_index) for fc in obj.animation_data.drivers} if obj.animation_data else set()
    channels = {key: [0] for key in sync.ROOT_CHANNELS}
    strings = {key: [] for key in actors._ITEM_CHANNELS}
    bones = ({bone.name: {} for bone in obj.pose.bones
              if not driven.intersection(paths(obj, {'bones': {bone.name: {}}}))}
             if obj.type == 'ARMATURE' else {})
    mesh = actor_shapekeys._mesh_with_shape_keys(obj)
    shape_keys = {}
    timeline = None
    if mesh is not None:
        key_data = mesh.data.shape_keys
        reference = key_data.reference_key
        shape_driven = {fc.data_path for fc in key_data.animation_data.drivers} if key_data.animation_data else set()
        shape_keys = {block.name: [] for block in key_data.key_blocks
                      if block != reference and actor_shapekeys.shape_path(block.name) not in shape_driven}
        timeline = actor_shapekeys.find_shape_timeline_curve(obj)
    patch = {'uuid': str(obj.get('bbs_uuid') or ''), 'channels': channels,
             'strings': strings, 'bones': bones, 'shapeKeys': shape_keys,
             'visibility': True}
    if obj.type == 'ARMATURE':
        rig = {}
        for pose_bone in obj.pose.bones:
            for prop in pose_bone.keys():
                if (prop != '_RNA_UI' and isinstance(pose_bone.get(prop), (bool, int, float))
                        and (actor_bones._rig_property_path(pose_bone.name, prop), 0) not in driven):
                    rig.setdefault(pose_bone.name, {})[prop] = {}
        if rig:
            patch['rigProperties'] = rig
    if timeline is not None:
        patch['shapeTimeline'] = []
    return patch


def full(obj):
    return capture(obj, full_patch(obj))


def slice_checkpoint(obj, checkpoint, patch):
    """Select exact stored native Blender state for one protected transaction path-set."""
    wanted = set(paths(obj, patch))
    entries = {(entry['path'], entry['index']): entry for entry in checkpoint['curves']}
    if not wanted <= set(entries):
        raise RuntimeError('Shared history baseline lacks a protected curve path; use Keep-local Resync')
    string_props = set()
    for key in patch.get('strings', {}):
        prefix = actors._ITEM_PROP_PREFIX + key
        string_props.update((prefix, prefix + '_ids'))
    from . import actor_channel_sync as sync
    grid_edit = bool(patch.get('shapeGrid'))
    first_grid = sync.shape_patch_creates_first_grid(patch)
    wanted_shapes = set(patch.get('shapeKeys', {})) | set(patch.get('shapeGrid', {}))
    saved_shapes = checkpoint.get('shapeCurves', {})
    if not wanted_shapes <= set(saved_shapes):
        raise RuntimeError('Shared history baseline lacks a protected ShapeKey path')
    result = {
        'patch': json.loads(json.dumps(patch, allow_nan=False)),
        'curves': [entries[key] for key in sorted(wanted)],
        'props': {key: checkpoint['props'][key] for key in sorted(string_props)},
        'modes': {key: checkpoint['modes'][key] for key in patch.get('bones', {})},
    }
    if 'visibility' in patch:
        if 'visibility' not in checkpoint:
            raise RuntimeError('Shared history baseline lacks actor visibility')
        result['visibility'] = checkpoint['visibility']
    if wanted_shapes:
        result['shapeCurves'] = {
            key: saved_shapes[key] for key in sorted(wanted_shapes)}
    if first_grid:
        if 'shapeTimelineCurve' in checkpoint:
            raise RuntimeError('First ShapeKey grid baseline unexpectedly has a timeline')
        result['shapeTimelineCurve'] = {'exists': False, 'value': 0.0, 'curve': None}
        result['shapeTimelineMorphs'] = {}
    elif 'shapeTimeline' in patch or grid_edit:
        if 'shapeTimelineCurve' not in checkpoint:
            raise RuntimeError('Shared history baseline lacks the ShapeKey timeline')
        result['shapeTimelineCurve'] = checkpoint['shapeTimelineCurve']
        if 'shapeTimelineMorphs' not in checkpoint:
            raise RuntimeError('Shared history baseline lacks the ShapeKey timeline morph effect')
        result['shapeTimelineMorphs'] = checkpoint['shapeTimelineMorphs']
    wanted_rig = {(bone, prop) for bone, properties in patch.get('rigProperties', {}).items()
                  for prop in properties}
    if wanted_rig:
        saved_rig = checkpoint.get('rigProperties', {})
        if any(prop not in saved_rig.get(bone, {}) for bone, prop in wanted_rig):
            raise RuntimeError('Shared history baseline lacks a protected rig-control path')
        result['rigProperties'] = {}
        for bone, prop in sorted(wanted_rig):
            result['rigProperties'].setdefault(bone, {})[prop] = saved_rig[bone][prop]
    return json.loads(json.dumps(result, allow_nan=False))


def merge(checkpoint, delta):
    """Project an exact partial checkpoint onto a full shared-history checkpoint."""
    result = json.loads(json.dumps(checkpoint, allow_nan=False))
    curves = {(entry['path'], entry['index']): entry for entry in result['curves']}
    for entry in delta['curves']:
        key = (entry['path'], entry['index'])
        if key not in curves:
            raise RuntimeError('Shared history cannot grow a path outside its baseline')
        curves[key] = entry
    result['curves'] = [curves[key] for key in sorted(curves)]
    result['props'].update(delta['props'])
    result['modes'].update(delta['modes'])
    if 'visibility' in delta:
        if 'visibility' not in result:
            raise RuntimeError('Shared history cannot grow visibility outside its baseline')
        result['visibility'] = delta['visibility']
    delta_shapes = delta.get('shapeCurves', {})
    if delta_shapes:
        target_shapes = result.get('shapeCurves', {})
        if not set(delta_shapes) <= set(target_shapes):
            raise RuntimeError('Shared history cannot grow a ShapeKey path outside its baseline')
        target_shapes.update(delta_shapes)
        result['shapeCurves'] = target_shapes
    from . import actor_channel_sync as sync
    first_grid = sync.shape_patch_creates_first_grid(delta.get('patch') or {})
    if 'shapeTimelineCurve' in delta:
        if 'shapeTimelineCurve' not in result:
            if not first_grid:
                raise RuntimeError('Shared history cannot grow the ShapeKey timeline outside its baseline')
        result['shapeTimelineCurve'] = delta['shapeTimelineCurve']
        if first_grid:
            if delta['shapeTimelineCurve'].get('curve') is None:
                result['patch'].pop('shapeTimeline', None)
            else:
                result['patch']['shapeTimeline'] = []
    if 'shapeTimelineMorphs' in delta:
        if 'shapeTimelineMorphs' not in result:
            if not first_grid:
                raise RuntimeError('Shared history cannot grow the ShapeKey timeline effect')
        if (not first_grid
                and set(delta['shapeTimelineMorphs']) != set(result['shapeTimelineMorphs'])):
            raise RuntimeError('Shared history ShapeKey timeline targets changed')
        result['shapeTimelineMorphs'] = delta['shapeTimelineMorphs']
        if delta['shapeTimelineMorphs']:
            _apply_fragment_to_shape_curves(result)
    if delta.get('rigProperties'):
        target = result.get('rigProperties', {})
        for bone, properties in delta['rigProperties'].items():
            if any(prop not in target.get(bone, {}) for prop in properties):
                raise RuntimeError('Shared history cannot grow a rig-control path outside its baseline')
            target[bone].update(properties)
        result['rigProperties'] = target
    return json.loads(json.dumps(result, allow_nan=False))


def _authored(checkpoint):
    """Drop only dependency-graph evaluated values when an F-curve owns that path."""
    result = json.loads(json.dumps(checkpoint, allow_nan=False))
    animated_props = set()
    for entry in result['curves']:
        if entry['curve'] is None:
            continue
        entry.pop('value', None)
        if entry['path'].startswith('["'):
            animated_props.add(json.loads(entry['path'][1:-1]))
    for prop in animated_props:
        saved = result['props'].get(prop)
        if isinstance(saved, dict) and set(saved) == {'exists', 'value'}:
            saved.pop('value', None)
    for saved in result.get('shapeCurves', {}).values():
        if saved.get('curve') is not None:
            saved.pop('value', None)
    timeline = result.get('shapeTimelineCurve')
    if timeline and timeline.get('curve') is not None:
        timeline.pop('value', None)
    for properties in result.get('rigProperties', {}).values():
        for saved in properties.values():
            if saved.get('curve') is not None:
                saved.pop('value', None)
    # Relative handle offsets pass through Blender's float32 coordinates. Normalizing only
    # the comparison copy avoids false conflicts after adding an offset to a newer scalar.
    for fragment in result.get('shapeTimelineMorphs', {}).values():
        if not _timeline_effect_v2(fragment):
            continue
        for key in fragment['keys']:
            key['frame'] = round(float(key['frame']), 5)
            for side in ('left', 'right'):
                key[side] = [round(float(value), 5) for value in key[side]]
    return result


def same(left, right):
    """Compare Blender-authored state, not the transient value evaluated at the current frame."""
    return _authored(left) == _authored(right)


def matches(obj, checkpoint):
    """Exact authored-state comparison for only the checkpoint's protected paths."""
    return same(capture(obj, checkpoint['patch']), checkpoint)
