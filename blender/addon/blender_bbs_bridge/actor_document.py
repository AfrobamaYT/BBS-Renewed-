"""Saved Blender document binding and replay of its durable actor intent journal."""
import json
import os
import pathlib
import re
import uuid
import bpy
from . import state, actor_channel_sync as sync, actor_curve_checkpoint as curves
from .actor_outbox_store import Outbox, encode

BINDING = 'bbs_durable_document_v1'
INSTANCE = 'bbs_actor_instance_v1'
ACK = 'bbs_durable_ack_v1'
SEEN = 'bbs_durable_seen_v1'
GENERATION = 'bbs_durable_save_generation_v1'
_store = None
_lock = None
_scene = None
_loaded_checkpoint = None
_restoring = False


def binding():
    raw = bpy.context.scene.get(BINDING)
    return json.loads(raw) if raw else None


def store_path(document):
    if not re.fullmatch('[0-9a-f]{32}', document):
        raise RuntimeError('Invalid durable document identifier')
    folder = pathlib.Path(bpy.utils.user_resource('CONFIG', path='bbs-bridge', create=True))
    return folder / (document + '.sqlite')


def roots(record=None):
    from .actor_export import _actor_roots
    result = {str(obj.get('bbs_uuid')): obj for obj in _actor_roots()}
    if record is not None:
        for uid, instance in record['instances'].items():
            if uid not in result or result[uid].get(INSTANCE) != instance or sync.read(result[uid]) is None:
                raise RuntimeError('Queued actor identity changed; no automatic replacement: ' + uid)
            curves.validate(result[uid], record['curves'][uid])
    return result


def close():
    global _store, _lock, _scene
    if _store is not None:
        _store.close()
    if _lock is not None:
        _lock.close()  # OS releases the process lock, including after a crash
    _store = _lock = _scene = None


def activate(restore=True):
    global _store, _lock, _scene, _restoring
    info = binding()
    if not info:
        close()
        return None
    if info['version'] != 1 or str(pathlib.Path(bpy.data.filepath).resolve()) != info['path']:
        raise RuntimeError('Durable document moved/copied; explicit new binding required')
    if sum(scene.get(BINDING) == bpy.context.scene.get(BINDING) for scene in bpy.data.scenes) != 1:
        raise RuntimeError('Duplicated scene identity; durable sender paused')
    if _scene == bpy.context.scene and _store is not None:
        return _store
    close()
    path = store_path(info['document'])
    if not path.is_file():
        raise RuntimeError('Durable outbox file is missing; no new identity or write created')
    lock = path.with_suffix('.lock').open('a+b')
    try:
        if os.name == 'nt':
            import msvcrt
            lock.seek(0); lock.write(b'0'); lock.flush(); lock.seek(0)
            msvcrt.locking(lock.fileno(), msvcrt.LK_NBLCK, 1)
        else:
            import fcntl
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        db = Outbox(path)
    except BaseException:
        lock.close()
        raise
    _store, _lock = db, lock
    try:
        scene = bpy.context.scene
        _scene = scene
        _restoring = restore
        state.bound_film = info['film']
        from .actor_resync import suffix, apply
        from . import actor_history_observations as observations
        pending = suffix(db.records(info['document'], int(scene.get(SEEN, 0)))) if restore else []
        incoming = [entry for entry in db.observations(info['document'])
                    if entry[1]['_observation_id'] > int(scene.get(observations.SEEN, 0))] if restore else []
        skipped_observation = 0
        if pending and pending[0]['payload'].get('resync'):
            fence = pending[0]['payload']['transactionSeq']
            skipped_observation = max((item[1]['_observation_id'] for item in incoming if item[0] < fence), default=0)
            incoming = [item for item in incoming if item[0] >= fence]

        def restore_incoming(before=None):
            while incoming and (before is None or incoming[0][0] < before):
                anchor, observed = incoming.pop(0)
                acknowledge(through=min(anchor, int(scene.get(SEEN, 0))))
                observations.recover(observed)

        if (pending or incoming) and _loaded_checkpoint is not None:
            from .actor_resync import checkpoint
            checkpoint_scale = pending[0]['scale'] if pending else sync.scene_timing_scale(sync.current_scene_timing())
            if (pending and pending[0].get('sceneTimingBefore') is not None
                    and sync.current_scene_timing() == pending[0]['sceneTimingBefore']):
                checkpoint_scale = sync.scene_timing_scale(pending[0]['sceneTimingBefore'])
            current = {uid: checkpoint(obj, checkpoint_scale) for uid, obj in roots().items()}
            if current != _loaded_checkpoint:
                raise RuntimeError('New edits after file load; automatic journal replay paused')
        recovery_roots = roots()
        for _anchor, observed in incoming:
            observations.validate_context(observed, recovery_roots)
        for record in pending:
            roots(record)  # preflight the complete recovery suffix before restoring a curve
        for record in pending:
            restore_incoming(record['payload']['transactionSeq'])
            if record.get('kind') == 'compensation':
                from . import actor_shared_undo
                actor_shared_undo.recover_local(record)
                scene[SEEN] = record['payload']['transactionSeq']
                continue
            actors = roots(record)
            if int(scene.get(GENERATION, 0)) <= record['generation']:
                if record.get('sceneTimingAfter') is not None:
                    timing = sync.current_scene_timing()
                    if timing == record.get('sceneTimingBefore'):
                        sync.apply_scene_timing(record['sceneTimingAfter'])
                    elif timing != record['sceneTimingAfter']:
                        raise RuntimeError('New scene timing precedes queued recovery')
                for patch in record['payload']['replays']:
                    uid = patch['uuid']
                    for section in ('channels', 'strings'):
                        for key in patch.get(section, {}):
                            actual = sync.signature(actors[uid], section, key, patch)
                            before = record['before'][uid]['local'][section].get(key)
                            sent = record['local'][uid][section][key]
                            if actual != before and actual != sent:
                                raise RuntimeError('New local edits precede queued recovery: ' + uid + '/' + key)
                            if section == 'channels':
                                sync.static.guard(actors[uid], key, patch, record['before'][uid],
                                    {'remote': patch, 'local': record['local'][uid],
                                     'static': record['local'][uid].get('static', {})})
                    for bone, properties in patch.get('rigProperties', {}).items():
                        for prop in properties:
                            actual = sync.rig_signature(actors[uid], bone, prop)
                            before = ((((record['before'][uid].get('rigProperties') or {})
                                        .get('local') or {}).get(bone) or {}).get(prop))
                            sent = (((record['local'][uid].get('rigProperties') or {})
                                     .get(bone) or {}).get(prop))
                            if actual != before and actual != sent:
                                raise RuntimeError(
                                    'New local edits precede queued recovery: '
                                    + uid + '/rigProperties/' + bone + '/' + prop)
                    for name in patch.get('shapeKeys', {}):
                        actual = sync.shape_signature(actors[uid], name)
                        before = ((record['before'][uid].get('shapeKeys') or {})
                                  .get('local', {}).get(name))
                        sent = (record['local'][uid].get('shapeKeys') or {}).get(name)
                        if actual != before and actual != sent:
                            raise RuntimeError(
                                'New local edits precede queued recovery: '
                                + uid + '/shapeKeys/' + name)
                    if 'shapeTimeline' in patch:
                        before = ((record['before'][uid].get('shapeKeys') or {})
                                  .get('timelineLocal'))
                        sent = record['local'][uid].get('shapeTimeline')
                        if any(not isinstance(sig, dict) or not sig.get('keys')
                               for sig in (before, sent)):
                            raise RuntimeError('Queued timeline baseline is incomplete: ' + uid + '/shapeTimeline')
                        actual = sync.shape_timeline_signature(actors[uid])
                        if actual != before and actual != sent:
                            raise RuntimeError('New local edits precede queued recovery: ' + uid + '/shapeTimeline')
                    if patch.get('shapeGrid'):
                        actual = curves.capture(actors[uid], patch)
                        before = (record.get('beforeCurves') or {}).get(uid)
                        sent = (record.get('curves') or {}).get(uid)
                        if (before is None or sent is None
                                or (not curves.same(actual, before) and not curves.same(actual, sent))):
                            raise RuntimeError(
                                'New local edits precede queued recovery: ' + uid + '/shapeGrid')
                for uid, saved in record['curves'].items():
                    curves.restore(actors[uid], saved)
            scene[SEEN] = record['payload']['transactionSeq']
            if record['payload'].get('resync') and skipped_observation:
                scene[observations.SEEN] = max(int(scene.get(observations.SEEN, 0)), skipped_observation)
            if record['payload'].get('resync') and (record.get('receipt') or {}).get('ok') is True:
                apply(record)
                scene[ACK] = record['payload']['transactionSeq']
        restore_incoming()
        if restore:
            acknowledge()
        return db
    except BaseException:
        close()
        raise
    finally:
        _restoring = False


def enable(project, film, endpoint):
    if binding() or not bpy.data.filepath:
        raise RuntimeError('Save an unbound .blend first')
    document = uuid.uuid4().hex
    db = Outbox(store_path(document))
    db.initialize(document)
    db.close()
    info = {'version': 1, 'document': document, 'project': project, 'film': film,
            'path': str(pathlib.Path(bpy.data.filepath).resolve()), 'endpoint': list(endpoint)}
    actors = roots()
    if not actors:
        raise RuntimeError('Import the film actors before enabling durable editing')
    for obj in actors.values():
        obj[INSTANCE] = uuid.uuid4().hex
    bpy.context.scene[BINDING] = encode(info)
    bpy.context.scene[ACK] = bpy.context.scene[SEEN] = bpy.context.scene[GENERATION] = 0
    if bpy.ops.wm.save_as_mainfile(filepath=bpy.data.filepath) != {'FINISHED'}:
        bpy.context.scene.pop(BINDING, None)
        raise RuntimeError('Binding could not be saved; durable editing not enabled')
    activate()
    from . import actor_shared_undo
    actor_shared_undo.ensure_baseline()


def records():
    return _store.records(binding()['document'], int(bpy.context.scene.get(ACK, 0)))


def acknowledge(through=None):
    from .actor_resync import suffix, apply
    from . import actor_shared_undo
    handled_compensation = False
    for record in suffix(records()):
        if through is not None and record['payload']['transactionSeq'] > through:
            break
        receipt = record['receipt']
        if receipt is None:
            break
        if record.get('kind') == 'compensation':
            actor_shared_undo.acknowledge(record)
            handled_compensation = True
            continue
        if receipt.get('ok') is not True:
            break
        if record['payload'].get('resync'):
            apply(record)
            bpy.context.scene[ACK] = record['payload']['transactionSeq']
            continue
        actors = roots(record)
        for patch in record['payload']['replays']:
            sync.acknowledge_export(actors[patch['uuid']], patch, record['scale'], record['local'][patch['uuid']])
        bpy.context.scene[ACK] = record['payload']['transactionSeq']
    if not handled_compensation:
        actor_shared_undo.ensure_baseline()


@bpy.app.handlers.persistent
def load_post(_dummy):
    from . import actor_leases
    actor_leases.forget()
    global _loaded_checkpoint
    close()
    _loaded_checkpoint = None
    try:
        if binding():
            from .actor_resync import checkpoint
            scene = bpy.context.scene
            scale = scene.render.fps / (scene.render.fps_base or 1) / 20
            _loaded_checkpoint = {uid: checkpoint(obj, scale) for uid, obj in roots().items()}
    except Exception as exc:
        print('[bbs-bridge] loaded document checkpoint unavailable:', exc)


@bpy.app.handlers.persistent
def save_pre(_dummy):
    try:
        if binding():
            db = activate()
            seen = [r['generation'] for r in db.records(binding()['document'])]
            bpy.context.scene[GENERATION] = max([int(bpy.context.scene.get(GENERATION, 0))] + seen) + 1
    except Exception as exc:
        print('[bbs-bridge] durable save checkpoint not advanced:', exc)
