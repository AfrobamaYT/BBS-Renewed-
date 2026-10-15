"""Explicit property lease with renewal; ownership never survives a lost session."""
import copy
import time
import math
import uuid
import urllib.parse
import bpy
from . import state, actor_document as doc
from .core import _send, _next_seq

_held = None
_pending = None
_status = 'no property held'
TTL_MS = 10000


def keys(paths):
    return {(p['uuid'], p['section'],
             '*' if p['section'] == 'shapeKeys'
             else 'yaw' if p['section'] == 'channels' and p['path'] == 'bodyYaw'
             else p['path'])
            for p in paths}


def payload_paths(payload):
    result = [{'uuid': r['uuid'], 'section': section, 'path': path}
              for r in payload['replays']
              for section in ('channels', 'strings', 'bones', 'shapeKeys')
              for path in r.get(section, {})]
    result.extend({'uuid': r['uuid'], 'section': 'visibility', 'path': 'enabled'}
                  for r in payload['replays'] if 'visibility' in r)
    result.extend({'uuid': r['uuid'], 'section': 'rigProperties',
                   'path': 'rig:%s:%s' % (urllib.parse.quote_plus(bone, safe=''),
                                           urllib.parse.quote_plus(prop, safe=''))}
                  for r in payload['replays']
                  for bone, properties in (r.get('rigProperties') or {}).items()
                  for prop in properties)
    result.extend({'uuid': r['uuid'], 'section': 'shapeKeys', 'path': '$timeline'}
                  for r in payload['replays'] if r.get('shapeTimeline'))
    result.extend({'uuid': r['uuid'], 'section': 'shapeKeys', 'path': '$grid'}
                  for r in payload['replays'] if r.get('shapeGrid'))
    return result


def context():
    try:
        info = doc.binding()
    except (TypeError, ValueError):
        return None
    if (not info or not state._connected or not state._peer_ready or state.film_switch_detected
            or 'actor_leases_v1' not in state.peer_capabilities or state.peer_project != info['project']
            or state.bound_film != info['film'] or list(state._connected_endpoint or ()) != info['endpoint']):
        return None
    return (info['document'], info['project'], info['film'], tuple(info['endpoint']), state.peer_engine)


def request(action, paths=None):
    global _pending, _status
    current = context()
    if current is None or _pending or (action == 'acquire' and _held) or (action != 'acquire' and not _held):
        _status = 'lease operation unavailable in the current session'
        return False
    fields = {'requestId': uuid.uuid4().hex, 'project': current[1], 'film': current[2],
              'client': current[0], 'action': action, 'paths': paths or [], 'ttlMs': TTL_MS,
              'leaseToken': _held['token'] if _held else ''}
    _pending = (fields, current, time.monotonic(), time.monotonic() + 3)
    _send({'type': 'ACTORS_LEASE', 'origin': 'blender', 'seq': _next_seq(), 'fields': fields})
    _status = 'requesting lease ' + action
    return True


def forget(_dummy=None):
    global _held, _pending, _status
    # Best effort release to the original endpoint; otherwise the server's TTL releases it.
    if _held and context() == _held['context']:
        current = _held['context']
        _send({'type': 'ACTORS_LEASE', 'origin': 'blender', 'seq': _next_seq(),
               'fields': {'requestId': uuid.uuid4().hex, 'project': current[1], 'film': current[2],
                          'client': current[0], 'action': 'release', 'leaseToken': _held['token']}})
    _held = _pending = None
    _status = 'no property held; reacquire explicitly after a session change'


def pump():
    global _held, _pending, _status
    now = time.monotonic()
    if _held and (context() != _held['context'] or now >= _held['expires']):
        forget()
    if _pending and (context() != _pending[1] or now >= _pending[3]):
        _pending = None
        _held = None
        _status = 'lease outcome unavailable; editing hold is not active'
    if _held and not _pending and now >= _held['renew']:
        request('renew')


def handle(fields):
    global _pending, _held, _status
    if not _pending or fields.get('requestId') != _pending[0]['requestId']:
        return False
    sent, bound, started, _ = _pending
    _pending = None
    if (fields.get('ok') is not True or context() != bound or fields.get('project') != bound[1]
            or fields.get('engine') != bound[4]):
        _held = None
        _status = 'property hold failed: ' + str(fields.get('error') or 'session changed')
        return True
    ttl_ms = fields.get('ttlMs')
    if (not isinstance(ttl_ms, (int, float)) or isinstance(ttl_ms, bool)
            or not math.isfinite(ttl_ms) or not 100 <= ttl_ms <= 30000
            or not isinstance(fields.get('leaseToken'), str) or not fields['leaseToken']
            or (sent['action'] == 'renew' and _held is None)):
        _held = None
        _status = 'invalid lease receipt; no property held'
        return True
    if sent['action'] == 'release':
        _held = None
        _status = 'property released'
    else:
        ttl = float(fields['ttlMs']) / 1000.0
        _held = {'token': fields['leaseToken'], 'context': bound,
                 'paths': copy.deepcopy(sent['paths'] if sent['action'] == 'acquire' else _held['paths']),
                 'expires': started + ttl * .85, 'renew': started + ttl / 3}
        _status = 'holding ' + ', '.join(p['section'] + '/' + p['path'] for p in _held['paths'])
    return True


def required(payload):
    if not _held:
        return []
    owned = keys(_held['paths'])
    return [p for p in payload_paths(payload) if next(iter(keys([p]))) in owned]


def envelope(record):
    pump()
    if _pending and _pending[0]['action'] == 'release':
        raise RuntimeError('property release is pending; gesture send paused')
    needed = record.get('leasePaths') or []
    if needed and (not _held or context() != _held['context'] or not keys(needed) <= keys(_held['paths'])):
        raise RuntimeError('queued protected gesture needs its property lease reacquired explicitly')
    return {'leaseToken': _held['token']} if _held and context() == _held['context'] else {}


class BBSBRIDGE_OT_hold_property(bpy.types.Operator):
    bl_idname = 'bbsbridge.hold_property'
    bl_label = 'Hold Actor Property'
    bl_description = ('Temporarily hold one protected actor resource; Shape Keys escalate '
                      'to the actor shared shape_keys channel')
    section: bpy.props.EnumProperty(items=[
        ('channels', 'Root channel', ''),
        ('strings', 'Item slot', ''),
        ('bones', 'Bone', ''),
        ('rigProperties', 'Rig control', 'Use bone/property, or canonical rig:... path'),
        ('shapeKeys', 'Shape Key channel', 'All morphs share one physical BBS ShapeKey channel'),
    ])
    path: bpy.props.StringProperty(name='BBS channel, bone or morph', default='x')

    def invoke(self, context, event):
        return context.window_manager.invoke_props_dialog(self)

    def execute(self, context):
        obj = context.active_object
        if obj is None or not obj.get('bbs_uuid') or doc.roots().get(str(obj.get('bbs_uuid'))) != obj:
            self.report({'ERROR'}, 'Select an imported actor root first')
            return {'CANCELLED'}
        path = self.path
        if self.section == 'rigProperties' and not path.startswith('rig:'):
            if '/' not in path:
                self.report({'ERROR'}, 'Rig control path must be bone/property')
                return {'CANCELLED'}
            bone, prop = path.split('/', 1)
            path = 'rig:%s:%s' % (urllib.parse.quote_plus(bone, safe=''),
                                  urllib.parse.quote_plus(prop, safe=''))
        if not request('acquire', [{'uuid': obj['bbs_uuid'], 'section': self.section, 'path': path}]):
            self.report({'ERROR'}, _status)
            return {'CANCELLED'}
        return {'FINISHED'}


class BBSBRIDGE_OT_release_property(bpy.types.Operator):
    bl_idname = 'bbsbridge.release_property'
    bl_label = 'Release Held Property'
    bl_description = 'End the explicit property hold; unfinished journaled gestures still require a hold before sending'

    def execute(self, context):
        return {'FINISHED'} if request('release') else {'CANCELLED'}


classes = (BBSBRIDGE_OT_hold_property, BBSBRIDGE_OT_release_property)
