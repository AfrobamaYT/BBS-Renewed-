"""Explicit keep-local reconciliation, fenced by a durable monotone server operation."""
import copy
import json
import uuid
import bpy
from . import actor_document as doc, actor_outbox as outbox, actor_channel_sync as sync
from . import actor_curve_checkpoint as curves, state, actors, actor_export, actor_shapekeys, shot_freeze
from .actor_outbox_store import encode


def suffix(records):
    start = max((i for i, record in enumerate(records) if record['payload'].get('resync')), default=0)
    return records[start:]


def checkpoint(obj, scale):
    channels = actor_export.channels_for_object(obj, scale, set(), include_unkeyed=True)
    base = sync.read(obj) or {}
    shape_state = base.get('shapeKeys') or {}
    shape_names = set(shape_state.get('local') or {}) | set(shape_state.get('remote') or {})
    rig_state = base.get('rigProperties') or {}
    rig_paths = set(sync._rig_flat(rig_state.get('local') or {})) | set(
        sync._rig_flat(rig_state.get('remote') or {}))
    rig = {}
    for bone, prop in rig_paths:
        rig.setdefault(bone, {})[prop] = {}
    patch = {'channels': {k: v for k, v in channels.items() if k in sync.ROOT_CHANNELS},
             'strings': sync.strings_for_object(obj, scale),
             'bones': actor_export.bones_for_object(obj, scale, set()),
             'shapeKeys': {name: [] for name in shape_names},
             'rigProperties': rig}
    if (actor_shapekeys.find_shape_timeline_curve(obj) is not None
            or shape_state.get('timelineLocal') is not None):
        patch['shapeTimeline'] = []
    return curves.capture(obj, patch)


def _adopt_unchanged_shape_interpolation(saved, previous, imported):
    """A dirty scalar does not own unchanged interpolation on the shared grid."""
    for name, entry in saved.get('shapeCurves', {}).items():
        local, old, remote = entry.get('curve'), previous.get(name), imported.get(name)
        if (local is None or old is None or remote is None
                or sync._shape_structure(local) != sync._shape_structure(old)):
            continue
        local_keys, remote_keys = local['keys'], remote['keys']
        if (len(local_keys) != len(remote_keys)
                or any(abs(a[0][0] - b[0][0]) > 1e-4 or b[3] not in ('LINEAR', 'CONSTANT')
                       for a, b in zip(local_keys, remote_keys))):
            continue
        for point, accepted in zip(local_keys, remote_keys):
            point[3] = accepted[3]
    for name, fragment in saved.get('shapeTimelineMorphs', {}).items():
        target = imported.get(name)
        keys = fragment.get('keys') if isinstance(fragment, dict) else None
        target_keys = target.get('keys') if isinstance(target, dict) else None
        if (not isinstance(keys, list) or not isinstance(target_keys, list)
                or len(keys) != len(target_keys)):
            continue
        def frame(saved_key):
            return saved_key.get('frame') if isinstance(saved_key, dict) else saved_key[0]

        def interpolation(saved_key):
            return (saved_key.get('interpolation') if isinstance(saved_key, dict)
                    else saved_key[1])

        if any(abs(float(frame(saved_key)) - float(target_key[0][0])) > 1e-4
               for saved_key, target_key in zip(keys, target_keys)):
            continue
        old_keys = (previous.get(name) or {}).get('keys') or []
        if len(old_keys) != len(keys):
            continue
        for target_key, saved_key, old_key in zip(target_keys, keys, old_keys):
            # The fragment is restored after the scalar curve. Adopt remote
            # interpolation there too, but never mutate the new remote baseline.
            # A locally changed interpolation still belongs to the local editor.
            if (abs(float(frame(saved_key)) - float(old_key[0][0])) > 1e-4
                    or interpolation(saved_key) != old_key[3]):
                continue
            if isinstance(saved_key, dict):
                saved_key['interpolation'] = target_key[3]
            else:
                saved_key[1] = target_key[3]


def queue():
    try:
        if (not outbox.bound() or not state._peer_ready or not state._connected
                or 'actor_resync_v1' not in state.peer_capabilities or shot_freeze.ist_eingefroren()):
            raise RuntimeError('resync needs the connected durable peer and an unfrozen document')
        db = doc.activate(restore=False)
        info = doc.binding()
        if state.peer_project != info['project'] or state.film_switch_detected:
            raise RuntimeError('resync cannot cross a project boundary')
        scale = bpy.context.scene.render.fps / (bpy.context.scene.render.fps_base or 1) / 20
        previous = doc.records()
        fields = {'channelPatch': 1, 'requestId': uuid.uuid4().hex, 'project': info['project'],
                  'film': info['film'], 'resync': True, 'replays': []}
        record = {'kind': 'resync', 'payload': fields, 'scale': scale, 'local': {},
                  'curves': {}, 'instances': {}, 'before': {}, 'dirty': {},
                  'generation': int(bpy.context.scene.get(doc.GENERATION, 0))}
        for uid, obj in doc.roots().items():
            base = sync.read(obj)
            saved = checkpoint(obj, scale)
            mask = {section: set() for section in (
                'channels', 'strings', 'bones', 'shapeKeys', 'rigProperties')}
            for section, supported in (('channels', sync.ROOT_CHANNELS), ('strings', actors._ITEM_CHANNELS)):
                for key in supported:
                    if (sync.signature(obj, section, key, saved['patch']) != base['local'][section].get(key)
                            or (section == 'channels' and sync.static.changed(obj, base, key, saved['patch']))):
                        mask[section].add(key)
            mask['bones'] |= sync.changed(base.get('outbound', {}).get('local', {}).get('bones', {}),
                                           saved['patch']['bones'])
            local_rig = sync._rig_flat((base.get('rigProperties') or {}).get('local') or {})
            for bone, properties in saved['patch'].get('rigProperties', {}).items():
                for prop in properties:
                    if sync.rig_signature(obj, bone, prop) != local_rig.get((bone, prop)):
                        mask['rigProperties'].add(json.dumps([bone, prop], separators=(',', ':')))
            shape_local = (base.get('shapeKeys') or {}).get('local') or {}
            for name in saved['patch'].get('shapeKeys', {}):
                if sync.shape_signature(obj, name) != shape_local.get(name):
                    mask['shapeKeys'].add(name)
            timeline_dirty = (sync.shape_timeline_signature(obj)
                              != (base.get('shapeKeys') or {}).get('timelineLocal'))
            for queued in previous:
                for patch in queued['payload']['replays']:
                    if patch['uuid'] == uid:
                        for section in mask:
                            if section == 'rigProperties':
                                mask[section].update(
                                    json.dumps([bone, prop], separators=(',', ':'))
                                    for bone, properties in patch.get(section, {}).items()
                                    for prop in properties)
                            else:
                                mask[section].update(patch.get(section, {}))
                        timeline_dirty |= 'shapeTimeline' in patch
            record['instances'][uid] = obj[doc.INSTANCE]
            record['curves'][uid], record['before'][uid] = saved, base
            record['dirty'][uid] = {section: sorted(keys) for section, keys in mask.items()}
            record['dirty'][uid]['shapeTimeline'] = timeline_dirty
        result = db.enqueue(info['document'], record)
        bpy.context.scene[doc.SEEN] = result['payload']['transactionSeq']
        outbox._pending = None
        outbox.retry()
        outbox._status = 'resync fence queued; local edits retained'
        return True
    except Exception as exc:
        outbox._status = 'resync paused: %s' % exc
        return False


def apply(record):
    """Adopt a known server snapshot; only explicitly dirty local paths survive it."""
    fields = record['receipt']['snapshot']
    if fields.get('project') != doc.binding()['project'] or fields.get('ok') is not True:
        raise RuntimeError('resync snapshot has the wrong project')
    replays = fields['replays']
    actors._validate_live_replays(replays)
    roots = doc.roots(record)
    if set(roots) != {r['uuid'] for r in replays}:
        raise RuntimeError('resync actor structure differs; local actors retained for explicit structural resolution')
    if shot_freeze.ist_eingefroren():
        raise RuntimeError('resync waits while shot is frozen')
    scale = record['scale']
    before = {uid: checkpoint(obj, scale) for uid, obj in roots.items()}
    bases = {uid: obj[sync.KEY] for uid, obj in roots.items()}
    dirty_checkpoints = {}
    previous_shapes = {}
    dirty = copy.deepcopy(record['dirty'])
    for uid, saved in before.items():
        mask = dirty[uid]
        mask.setdefault('rigProperties', [])
        base = sync.read(roots[uid])
        previous_shapes[uid] = (base.get('shapeKeys') or {}).get('local') or {}
        for section, supported in (('channels', sync.ROOT_CHANNELS), ('strings', actors._ITEM_CHANNELS)):
            for key in supported:
                if (sync.signature(roots[uid], section, key, saved['patch']) != base['local'][section].get(key)
                        or (section == 'channels' and sync.static.changed(roots[uid], base, key, saved['patch']))):
                    if key not in mask[section]:
                        mask[section].append(key)
        for bone in sync.changed(base.get('outbound', {}).get('local', {}).get('bones', {}), saved['patch']['bones']):
            if bone not in mask['bones']:
                mask['bones'].append(bone)
        local_rig = sync._rig_flat((base.get('rigProperties') or {}).get('local') or {})
        for bone, properties in saved['patch'].get('rigProperties', {}).items():
            for prop in properties:
                token = json.dumps([bone, prop], separators=(',', ':'))
                if (sync.rig_signature(roots[uid], bone, prop) != local_rig.get((bone, prop))
                        and token not in mask['rigProperties']):
                    mask['rigProperties'].append(token)
        shape_local = (base.get('shapeKeys') or {}).get('local') or {}
        for name in saved['patch'].get('shapeKeys', {}):
            if (sync.shape_signature(roots[uid], name) != shape_local.get(name)
                    and name not in mask['shapeKeys']):
                mask['shapeKeys'].append(name)
        timeline_dirty = (mask.get('shapeTimeline', False)
                          or sync.shape_timeline_signature(roots[uid])
                          != (base.get('shapeKeys') or {}).get('timelineLocal'))
        patch = {section: {k: saved['patch'].get(section, {}).get(k) for k in mask[section]}
                 for section in ('channels', 'strings', 'bones', 'shapeKeys')}
        patch['rigProperties'] = {}
        for token in mask['rigProperties']:
            bone, prop = json.loads(token)
            patch['rigProperties'].setdefault(bone, {})[prop] = (
                saved['patch'].get('rigProperties', {}).get(bone, {}).get(prop))
        if timeline_dirty:
            patch['shapeTimeline'] = []
        dirty_checkpoints[uid] = curves.capture(roots[uid], patch)
    succeeded = False
    was_restoring = doc._restoring
    doc._restoring = True
    try:
        for uid, obj in roots.items():
            base = sync.read(obj)
            captured = sync.capture_export_signatures(obj, before[uid]['patch'])
            base['local'] = {section: captured[section] for section in ('channels', 'strings')}
            base['static'] = copy.deepcopy(captured.get('static', {}))
            base.setdefault('rigProperties', {'remote': {}, 'local': {}})['local'] = copy.deepcopy(
                captured.get('rigProperties', {}))
            shape_base = base.setdefault('shapeKeys', {'remote': {}, 'local': {}})
            shape_base['local'] = copy.deepcopy(captured.get('shapeKeys', {}))
            if 'shapeTimeline' in before[uid]['patch']:
                shape_base['timelineLocal'] = sync.shape_timeline_signature(obj)
            base.setdefault('outbound', {})['local'] = {'bones': before[uid]['patch']['bones']}
            obj[sync.KEY] = encode(base)
            obj[actors._ACTOR_SNAPSHOT_PROP] = ''
        if not actors._live_sync_actors(replays):
            raise RuntimeError('resync snapshot was not applied')
        for replay in replays:
            uid = replay['uuid']
            obj = roots[uid]
            # A full reconciliation needs an actual imported basis even for remote-unchanged paths.
            plan = {'previous': replay, 'changes': {'channels': set(sync.ROOT_CHANNELS)}}
            sync.apply_roots(obj, replay, scale, plan)
            actors._apply_item_channels(obj, replay, scale, actors._ITEM_CHANNELS)
            base = sync.read(obj)
            remote_shapes = (base.get('shapeKeys') or {}).get('remote') or {}
            actor_shapekeys.apply_form_channels(
                obj, replay, scale, set(remote_shapes), False)
            base['local'] = {section: {key: sync.signature(obj, section, key, replay) for key in supported}
                             for section, supported in (('channels', sync.ROOT_CHANNELS), ('strings', actors._ITEM_CHANNELS))}
            for key in sync.ROOT_CHANNELS:
                sync.static.remember(base, obj, key, replay)
            shape_base = base.setdefault('shapeKeys', {'remote': {}, 'local': {}})
            shape_base['local'] = {name: sync.shape_signature(obj, name) for name in remote_shapes}
            shape_base['timelineRemote'] = actor_shapekeys.shape_timeline(remote_shapes)
            shape_base['timelineLocal'] = sync.shape_timeline_signature(obj)
            _adopt_unchanged_shape_interpolation(
                dirty_checkpoints[uid], previous_shapes[uid], shape_base['local'])
            for bone in dirty[uid]['bones']:
                # An absent remote bone has no acknowledged outgoing local counterpart.
                if bone not in (replay.get('properties') or {}).get('bones', {}):
                    base['outbound']['local']['bones'].pop(bone, None)
            curves.restore(obj, dirty_checkpoints[uid])
            obj[sync.KEY] = encode(base)
        from . import actor_native_undo
        actor_native_undo._error = ''
        succeeded = True
    finally:
        doc._restoring = was_restoring
        if not succeeded:
            for uid, obj in roots.items():
                curves.restore(obj, before[uid])
                obj[sync.KEY] = bases[uid]
                obj[actors._ACTOR_SNAPSHOT_PROP] = ''
