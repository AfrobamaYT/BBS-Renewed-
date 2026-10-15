"""Keep transport state outside Blender's native undo snapshots in live-edit mode."""
import bpy

from . import actor_document as document, actor_channel_sync as sync, actors
from . import actor_curve_checkpoint as curves, actor_history_observations as observations
from . import actor_undo_barriers as barriers

_before = None
_error = ''


def _identity(obj):
    return (obj.get(document.INSTANCE), obj.type, obj.get('bbs_form'), obj.get('bbs_form_type'))


def _fail(exc):
    global _error
    from . import actor_outbox, state
    _error = 'Native undo paused live editing: ' + str(exc)
    actor_outbox._paused = True
    actor_outbox._status = _error
    state._live_status = _error
    print('[bbs-bridge]', _error)


@bpy.app.handlers.persistent
def before(_dummy):
    global _before
    from . import actor_authoring
    _before = None
    if not getattr(bpy.context.scene, actor_authoring.PROP, False):
        return
    try:
        db = document.activate()
        if db is None:
            return
        scene = bpy.context.scene
        info = document.binding()
        _before = {
            'binding': info, 'file': bpy.data.filepath,
            'scene': {key: scene.get(key) for key in (document.ACK, document.SEEN,
                document.GENERATION, observations.SEEN)},
            'roots': {uid: {'identity': _identity(obj), 'base': obj[sync.KEY],
                            'fingerprint': obj.get(actors._ACTOR_SNAPSHOT_PROP)}
                      for uid, obj in document.roots().items()},
            'observations': db.observations(info['document']),
            'external_barriers': barriers.capture(db, info['document'], document.roots()),
        }
    except Exception as exc:
        _fail(exc)


@bpy.app.handlers.persistent
def after(_dummy):
    global _before
    from . import actor_authoring
    saved, _before = _before, None
    if saved is None:
        return
    try:
        if document.binding() != saved['binding'] or bpy.data.filepath != saved['file']:
            raise RuntimeError('document binding changed; explicit reconciliation required')
        roots = document.roots()
        if set(roots) != set(saved['roots']) or any(
                _identity(roots[uid]) != value['identity'] for uid, value in saved['roots'].items()):
            raise RuntimeError('actor/form structure changed; explicit reconciliation required')
        scene = bpy.context.scene
        old_seen = int(scene.get(observations.SEEN, 0))
        accepted_seen = int(saved['scene'].get(observations.SEEN) or 0)
        incoming = [value for _anchor, value in saved['observations']
                    if old_seen < value['_observation_id'] <= accepted_seen]
        # A native snapshot predates external edits. Reapply only accepted remote
        # scopes newer than that snapshot; an X undo cannot erase a later BBS Y edit.
        for observed in incoming:
            observations.validate_context(observed, roots)
            for uid, checkpoint in observed['curves'].items():
                curves.validate(roots[uid], checkpoint)
        rollback = {uid: curves.full(roots[uid])
                    for observed in incoming for uid in observed['curves']}
        try:
            for observed in incoming:
                for uid, checkpoint in observed['curves'].items():
                    curves.restore(roots[uid], checkpoint)
        except Exception:
            for uid, checkpoint in rollback.items():
                curves.restore(roots[uid], checkpoint)
            raise
        barriers.restore(roots, saved['external_barriers'])
        for uid, value in saved['roots'].items():
            roots[uid][sync.KEY] = value['base']
            if value['fingerprint'] is None:
                roots[uid].pop(actors._ACTOR_SNAPSHOT_PROP, None)
            else:
                roots[uid][actors._ACTOR_SNAPSHOT_PROP] = value['fingerprint']
        for key, value in saved['scene'].items():
            if value is None:
                scene.pop(key, None)
            else:
                scene[key] = value
        document.close()
        document._loaded_checkpoint = None
        setattr(scene, actor_authoring.PROP, True)
        actor_authoring.reset()
    except Exception as exc:
        _fail(exc)


def register():
    global _error
    _error = ''
    for handlers, function in ((bpy.app.handlers.undo_pre, before),
            (bpy.app.handlers.redo_pre, before), (bpy.app.handlers.undo_post, after),
            (bpy.app.handlers.redo_post, after)):
        if function not in handlers:
            handlers.append(function)


def unregister():
    global _before, _error
    for handlers, function in ((bpy.app.handlers.undo_pre, before),
            (bpy.app.handlers.redo_pre, before), (bpy.app.handlers.undo_post, after),
            (bpy.app.handlers.redo_post, after)):
        if function in handlers:
            handlers.remove(function)
    _before, _error = None, ''
