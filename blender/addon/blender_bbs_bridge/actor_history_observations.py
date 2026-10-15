"""Exact accepted BBS effects interleaved with local durable edit history."""
import copy
import bpy

from . import actor_channel_sync as sync, actor_curve_checkpoint as curves
from . import actor_document as document, actors
from .actor_outbox_store import encode

ERROR_KEY = 'bbs_shared_history_observation_error'
SEEN = 'bbs_shared_history_observation_seen'


def applied_patch(replay, plan):
    """Accepted path ownership; exact restorable curves come from native Blender data."""
    patch = {'uuid': replay['uuid']}
    source = sync.sections(replay)
    for section, supported in (('channels', set(sync.ROOT_CHANNELS) | set(sync.POSE_INPUTS)),
                               ('strings', actors._ITEM_CHANNELS)):
        names = set(plan['changes'][section]) & set(supported)
        if names:
            patch[section] = {name: copy.deepcopy(source[section].get(name)) for name in sorted(names)}
    bones = set(plan.get('bone_changes', ())) | set(plan.get('pose_track_changes', ()))
    bones |= set(plan.get('derived_bones', ()))
    if bones:
        explicit = sync._property_groups(replay)
        patch['bones'] = {name: copy.deepcopy(explicit['bones'].get(name)) for name in sorted(bones)}
    for bone, prop in plan.get('rig_changes', ()):
        patch.setdefault('rigProperties', {}).setdefault(bone, {})[prop] = {}
    if plan.get('visibility_changed'):
        patch['visibility'] = bool(replay.get('enabled', True))

    before = sync._shape_group({'properties': plan['previous']['properties']})
    after = sync._shape_group(replay)
    if plan.get('shape_timeline_changed') and before and after:
        # The shared grid affects every morph, including exact relative handles.
        patch['shapeGrid'] = copy.deepcopy(after)
    elif plan.get('shape_changes'):
        names = sorted(plan['shape_changes'])
        patch['shapeKeys'] = {name: copy.deepcopy(after.get(name)) for name in names}
        patch['expected'] = {
            'shapeKeys': {name: copy.deepcopy(before.get(name)) for name in names},
        }
        from . import actor_shapekeys
        patch['expected']['shapeTimeline'] = actor_shapekeys.shape_timeline(before)
    if plan.get('shape_timeline_changed'):
        from . import actor_shapekeys
        patch['shapeTimeline'] = actor_shapekeys.shape_timeline(after)
        patch.setdefault('expected', {})['shapeTimeline'] = actor_shapekeys.shape_timeline(before)
    return patch


def _subset(values, keys):
    return {key: copy.deepcopy(values[key]) for key in keys if key in values}


def _base_slice(base, patch):
    result = {'version': 1, 'tick_scale': base['tick_scale'],
              'sceneTiming': copy.deepcopy(base.get('sceneTiming')),
              'remote': sync.sections({}), 'local': {}}
    for section in ('channels', 'strings'):
        result['remote'][section] = _subset(base['remote'].get(section, {}), patch.get(section, {}))
        result['local'][section] = _subset(base['local'].get(section, {}), patch.get(section, {}))
    out = base.get('outbound') or {}
    result['outbound'] = {'remote': {}, 'local': {}}
    result['static'] = _subset(base.get('static', {}), patch.get('channels', {}))
    for section in ('bones', 'poseTrack'):
        result['outbound']['remote'][section] = _subset(
            (out.get('remote') or {}).get(section, {}), patch.get('bones', {}))
        result['remote']['properties'][section] = _subset(
            base['remote']['properties'].get(section, {}), patch.get('bones', {}))
    result['outbound']['local']['bones'] = _subset(
        (out.get('local') or {}).get('bones', {}), patch.get('bones', {}))
    if patch.get('rigProperties'):
        result['rigProperties'] = {}
        for section in ('remote', 'local'):
            source = (base.get('rigProperties') or {}).get(section, {})
            result['rigProperties'][section] = {
                bone: _subset(source.get(bone, {}), properties)
                for bone, properties in patch['rigProperties'].items()}
        result['remote']['properties']['rigProperties'] = copy.deepcopy(result['rigProperties']['remote'])
    if any(key in patch for key in ('shapeKeys', 'shapeGrid', 'shapeTimeline')):
        result['shapeKeys'] = copy.deepcopy(base.get('shapeKeys') or {})
        result['remote']['properties']['shapeKeys'] = copy.deepcopy(
            base['remote']['properties'].get('shapeKeys') or {})
    if 'visibility' in patch:
        result['visibility'] = copy.deepcopy(base['visibility'])
    return result


def _scope(patch):
    result = {key: sorted(value) if isinstance(value, dict) else True
              for key, value in patch.items() if key not in ('uuid', 'expected', 'rigProperties')}
    if 'rigProperties' in patch:
        result['rigProperties'] = {bone: sorted(properties)
                                   for bone, properties in patch['rigProperties'].items()}
    result['firstGrid'] = sync.shape_patch_creates_first_grid(patch)
    return result


def _identity(obj):
    return {'type': obj.type, 'form': str(obj.get('bbs_form', '')),
            'formType': str(obj.get('bbs_form_type', ''))}


def validate_context(observation, roots):
    if observation.get('version') != 1:
        raise RuntimeError('Legacy remote observation needs protected resync')
    if observation.get('context') != document.binding():
        raise RuntimeError('Remote history document/film/project/endpoint binding changed')
    wanted = set(observation['curves'])
    if (set(observation.get('identities', {})) != wanted
            or set(observation.get('instances', {})) != wanted
            or set(observation.get('bases', {})) != wanted):
        raise RuntimeError('Remote history actor scope is incomplete')
    for uid in wanted:
        if (uid not in roots or roots[uid].get(document.INSTANCE) != observation['instances'][uid]
                or _identity(roots[uid]) != observation['identities'][uid]):
            raise RuntimeError('Remote history actor/form identity changed: ' + uid)


def apply(replays, plans, tick_scale, apply_snapshot):
    """Publish a protected incoming batch only with its durable exact observation.

    The existing curve restorer rolls back failed imports or journal writes. Resync
    already owns a wider rollback boundary and deliberately suppresses observations.
    """
    scene = bpy.context.scene
    db = document._store
    if document._restoring or document._scene != scene or db is None:
        apply_snapshot(replays, plans, tick_scale, document._restoring)
        return True
    info = document.binding()
    baseline, _sequence = db.baseline(info['document'])
    if baseline is None or baseline.get('version') == 1:
        apply_snapshot(replays, plans, tick_scale, False)
        return True
    if baseline.get('version') != 2 or scene.get(ERROR_KEY):
        raise RuntimeError('Remote history is incomplete or incompatible; protected resync required')
    roots = document.roots()
    for _anchor, observed in db.observations(info['document']):
        validate_context(observed, roots)
    if set(roots) != {replay['uuid'] for replay in replays}:
        raise RuntimeError('Remote actor structure needs an explicit new shared-history baseline')
    before, bases, fingerprints = {}, {}, {}
    for replay in replays:
        uid = replay['uuid']
        if uid not in plans:
            continue
        patch = applied_patch(replay, plans[uid])
        if set(patch) != {'uuid'}:
            before[uid] = curves.capture(roots[uid], patch)
        bases[uid] = roots[uid][sync.KEY]
        fingerprints[uid] = roots[uid][actors._ACTOR_SNAPSHOT_PROP]
    end, payload = scene.frame_end, copy.deepcopy(actors.state._last_actors_payload)
    try:
        applied = apply_snapshot(replays, plans, tick_scale, True)
        capture(applied)
        return True
    except BaseException:
        # Restore only touched protected paths. Untouched authored siblings keep
        # their native F-curves and every actor's pre-import merge basis.
        for uid, saved in before.items():
            curves.restore(roots[uid], saved)
        for uid, base in bases.items():
            roots[uid][sync.KEY] = base
            roots[uid][actors._ACTOR_SNAPSHOT_PROP] = fingerprints[uid]
        scene.frame_end = end
        actors.state._last_actors_payload = payload
        raise


def capture(applied):
    """Persist the complete accepted batch; the caller rolls back if this fails."""
    snapshots, instances, scope, bases, identities = {}, {}, {}, {}, {}
    for obj, replay, plan in applied:
        patch = applied_patch(replay, plan)
        if set(patch) == {'uuid'}:
            continue
        uid = replay['uuid']
        if not obj.get(document.INSTANCE):
            raise RuntimeError('Remote actor structure needs a new shared-history baseline')
        snapshots[uid] = curves.capture(obj, patch)
        bases[uid] = _base_slice(sync.read(obj), patch)
        instances[uid] = obj[document.INSTANCE]
        identities[uid] = _identity(obj)
        scope[uid] = _scope(patch)
    if snapshots:
        row_id = document._store.observe(document.binding()['document'],
            int(bpy.context.scene.get(document.ACK, 0)), encode(scope),
            {'version': 1, 'context': document.binding(), 'curves': snapshots,
             'instances': instances, 'identities': identities, 'bases': bases})
        bpy.context.scene[SEEN] = row_id


def merge(checkpoints, roots, observation):
    validate_context(observation, roots)
    for uid, saved in observation['curves'].items():
        if uid not in checkpoints:
            raise RuntimeError('Remote history actor identity changed: ' + uid)
        checkpoints[uid] = curves.merge(checkpoints[uid], saved)


def _guard(obj, before, after, patch):
    from . import actor_export

    def unchanged(actual, previous, desired, path):
        if actual != previous and actual != desired:
            raise RuntimeError('Saved local edit precedes remote history recovery: ' + path)

    if (before['tick_scale'] != after['tick_scale']
            or sync.current_scene_timing() != after.get('sceneTiming')):
        raise RuntimeError('Scene timing differs from remote history; protected resync required')
    for section in ('channels', 'strings'):
        for key in patch.get(section, {}):
            unchanged(sync.signature(obj, section, key, before['remote']),
                      before['local'].get(section, {}).get(key), after['local'].get(section, {}).get(key), key)
            if section == 'channels':
                sync.static.guard(obj, key, patch, before, after)
    if patch.get('bones'):
        current = actor_export.bones_for_object(obj, before['tick_scale'], set())
        for bone in patch['bones']:
            unchanged(current.get(bone), before['outbound']['local']['bones'].get(bone),
                      after['outbound']['local']['bones'].get(bone), bone)
    for bone, properties in patch.get('rigProperties', {}).items():
        for prop in properties:
            unchanged(sync.rig_signature(obj, bone, prop),
                      ((before.get('rigProperties') or {}).get('local', {}).get(bone) or {}).get(prop),
                      ((after.get('rigProperties') or {}).get('local', {}).get(bone) or {}).get(prop), bone + '/' + prop)
    for name in set(patch.get('shapeKeys', {})) | set(patch.get('shapeGrid', {})):
        unchanged(sync.shape_signature(obj, name),
                  (before.get('shapeKeys') or {}).get('local', {}).get(name),
                  (after.get('shapeKeys') or {}).get('local', {}).get(name), name)
    if 'shapeTimeline' in patch or patch.get('shapeGrid'):
        unchanged(sync.shape_timeline_signature(obj),
                  (before.get('shapeKeys') or {}).get('timelineLocal'),
                  (after.get('shapeKeys') or {}).get('timelineLocal'), 'shapeTimeline')
    if 'visibility' in patch:
        unchanged(sync.visibility_signature(obj), before['visibility']['local'],
                  after['visibility']['local'], 'visibility')


def recover(observation):
    roots = document.roots()
    validate_context(observation, roots)
    bases = {}
    for uid, saved in observation['curves'].items():
        if uid not in roots or roots[uid].get(document.INSTANCE) != observation['instances'].get(uid):
            raise RuntimeError('Remote recovery actor identity changed: ' + uid)
        curves.validate(roots[uid], saved)
        current = sync.read(roots[uid])
        source = observation['bases'][uid]
        _guard(roots[uid], current, source, saved['patch'])
        bases[uid] = sync.merge_patch_base(current, source, saved['patch'])
    for uid, saved in observation['curves'].items():
        curves.restore(roots[uid], saved)
        roots[uid][sync.KEY] = encode(bases[uid])
        sync.remember_remote_fingerprint(roots[uid], bases[uid], bases[uid]['tick_scale'])
    bpy.context.scene[SEEN] = observation['_observation_id']
