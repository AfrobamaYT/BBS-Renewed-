"""Cubic BBS pose translations are X-mirrored pixel offsets, not BOBJ model units.

A cubic group's rest rotation (bind geometry stays unrotated, CubicModelExport) is the constant
euler offset BBS adds to every pose of that group: Model.applyPose sums current.rotate = initial +
pose and builds the ZYX (= Blender 'XYZ') rotation from the sum. Incoming poses get it added, the
way back (export, live preview) takes it out again, so round trips never accumulate it.
"""
import copy
import json

from mathutils import Euler, Quaternion

_R = ('rx', 'ry', 'rz')
_Q = ('qw', 'qx', 'qy', 'qz')


def curves(obj, values, incoming):
    factors = obj.get('bbs_pose_translation_scale')
    if factors is None:
        return values
    result = copy.deepcopy(values)
    for bone in result.values():
        for key, factor in zip(('tx','ty','tz'), factors):
            factor = float(factor) if incoming else 1/float(factor)
            for point in bone.get(key, []):
                for field in ('value','ly','ry'):
                    if field in point: point[field] *= factor
    return result


def preview(obj, values):
    factors = obj.get('bbs_pose_translation_scale')
    offsets = rest(obj)
    if factors is None and not offsets:
        return values
    result = copy.deepcopy(values)
    for name, bone in result.items():
        if factors is not None and 'location' in bone:
            bone['location'] = [v/float(f) for v,f in zip(bone['location'], factors)]
        if name in offsets and 'rotation_quaternion' in bone:
            q = _rest_quat(offsets[name]).inverted() @ Quaternion(bone['rotation_quaternion'])
            bone['rotation_quaternion'] = [q.w, q.x, q.y, q.z]
        elif name in offsets and 'rotation_euler' in bone:
            bone['rotation_euler'] = [v - o for v, o in zip(bone['rotation_euler'], offsets[name])]
    return result


def rest(obj):
    """{bone: (rx, ry, rz)} in radians, from the cubic export; {} for every other model."""
    raw = obj.get('bbs_pose_rest_rotation') if obj is not None else None
    if not raw:
        return {}
    try:
        return {name: tuple(float(v) for v in values) for name, values in json.loads(raw).items()}
    except (TypeError, ValueError):
        return {}


def _rest_quat(offset):
    return Euler(offset, 'XYZ').to_quaternion()


def _tick(kf):
    return round(float(kf.get('tick', 0.0)), 4)


def _lerp_at(keys, tick):
    points = sorted((_tick(kf), float(kf.get('value', 0.0))) for kf in keys)
    if not points:
        return None
    if tick <= points[0][0]:
        return points[0][1]
    for (t0, v0), (t1, v1) in zip(points, points[1:]):
        if tick <= t1:
            return v1 if t1 <= t0 else v0 + (v1 - v0) * (tick - t0) / (t1 - t0)
    return points[-1][1]


def _aligned(sub, names, defaults):
    """Every named sub-curve gets a key on every tick any of them has (readers match exact ticks)."""
    keys = {n: [kf for kf in (sub.get(n) or []) if isinstance(kf, dict)] for n in names}
    ticks = sorted({_tick(kf) for ks in keys.values() for kf in ks})
    for n, default in zip(names, defaults):
        have = {_tick(kf) for kf in keys[n]}
        for t in ticks:
            if t not in have:
                value = _lerp_at(keys[n], t)
                keys[n].append({'tick': t, 'value': default if value is None else value, 'interp': 'linear'})
        keys[n].sort(key=_tick)
        sub[n] = keys[n]
    return ticks


def add_rest(obj, bones, track):
    """Incoming BBS pose -> Blender total rotation. One track per bone carries the offset."""
    offsets = rest(obj)
    if not offsets:
        return bones, track
    bones, track = copy.deepcopy(bones), copy.deepcopy(track)
    for name, offset in offsets.items():
        sub = bones.get(name) if isinstance(bones.get(name), dict) else track.get(name)
        if not isinstance(sub, dict):
            continue
        if sub.get('quat'):
            if not _aligned(sub, _Q, (1.0, 0.0, 0.0, 0.0)):
                continue
            base = _rest_quat(offset)
            for i in range(len(sub['qw'])):
                q = base @ Quaternion(tuple(float(sub[n][i]['value']) for n in _Q))
                for n, v in zip(_Q, (q.w, q.x, q.y, q.z)):
                    sub[n][i]['value'] = v
        elif _aligned(sub, _R, (0.0, 0.0, 0.0)):
            for n, v in zip(_R, offset):
                for kf in sub[n]:
                    kf['value'] = float(kf['value']) + v
    return bones, track


def remove_rest(obj, out):
    """Outgoing Blender total rotation -> BBS pose (inverse of add_rest), per exported key."""
    for name, offset in rest(obj).items():
        sub = out.get(name)
        if not isinstance(sub, dict):
            continue
        if sub.get('quat'):
            inverse = _rest_quat(offset).inverted()
            for i in range(min(len(sub.get(n) or []) for n in _Q)):
                q = inverse @ Quaternion(tuple(float(sub[n][i]['value']) for n in _Q))
                for n, v in zip(_Q, (q.w, q.x, q.y, q.z)):
                    sub[n][i]['value'] = v
        else:
            for n, v in zip(_R, offset):
                for kf in sub.get(n) or []:
                    kf['value'] = float(kf['value']) - v
    return out


def apply_rest_base(obj):
    """Bones without rotation keys still stand in their rest rotation, as in BBS."""
    for name, offset in rest(obj).items():
        pb = obj.pose.bones.get(name)
        if pb is None:
            continue
        q = _rest_quat(offset)
        if pb.rotation_mode == 'QUATERNION':
            pb.rotation_quaternion = q
        elif pb.rotation_mode == 'AXIS_ANGLE':
            axis, angle = q.to_axis_angle()
            pb.rotation_axis_angle = (angle, axis.x, axis.y, axis.z)
        else:
            pb.rotation_euler = q.to_euler(pb.rotation_mode)
