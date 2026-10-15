"""Merge metadata for native root values without an animation curve."""
import struct


def value(obj, key, context):
    from . import actors
    spec = actors._actor_channel_spec(context.get('channels') or {}, key)
    if spec is None:
        return None
    path, index = spec[:2]
    curve = actors._find_fcurve(obj, path, index)
    if curve is not None and curve.keyframe_points:
        return None
    return float(getattr(obj, path)[index])


def basis(base, key):
    from . import actors
    if key in base.get('static', {}):
        return base['static'][key]
    remote = (base.get('remote') or {}).get('channels') or {}
    spec = actors._actor_channel_spec(remote, key)
    if spec is None:
        return None
    signature = (base.get('local') or {}).get('channels', {}).get(key)
    if signature and signature.get('keys'):
        return None
    keys = remote.get(key) or []
    if keys:
        if len(keys) != 1:
            raise RuntimeError('Legacy static root basis needs protected resync: ' + key)
        return struct.unpack('!f', struct.pack('!f', float(keys[0]['value']) * spec[2] + spec[3]))[0]
    return 1.0 if spec[0] == 'scale' else 0.0


def changed(obj, base, key, context):
    actual = value(obj, key, context)
    return actual is not None and actual != basis(base, key)


def remember(base, obj, key, context):
    actual = value(obj, key, context)
    target = base.setdefault('static', {})
    if actual is None:
        target.pop(key, None)
    else:
        target[key] = actual


def guard(obj, key, context, before, after):
    actual = value(obj, key, context)
    if actual is not None and actual not in (basis(before, key), basis(after, key)):
        raise RuntimeError('New static root edit precedes journal recovery: ' + key)
