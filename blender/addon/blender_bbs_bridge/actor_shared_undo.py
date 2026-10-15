"""Coherent durable actor undo/redo across Blender and BBS.

This is an explicit compensating history, not Blender's generic Ctrl+Z stack. Exact
Blender-native curve checkpoints move locally; the durable BBS transaction moves the
same protected paths remotely. Newer local edits are never overwritten on receipts.
"""
import copy
import uuid
import bpy

from . import state
from . import actor_channel_sync as sync
from . import actor_curve_checkpoint as curves
from .actor_outbox_store import encode

_status = 'shared history unavailable'


def _kind(record):
    if record.get('kind'):
        return record['kind']
    payload = record.get('payload') or {}
    if payload.get('resync'):
        return 'resync'
    if payload.get('compensateOf'):
        return 'compensation'
    return 'edit'


def _full_records():
    from . import actor_document as doc
    db = doc.activate(restore=False)
    info = doc.binding()
    return db.records(info['document'], 0)


def _capture_baseline():
    from . import actor_document as doc
    result = {'version': 2, 'actors': {}}
    for uid, obj in doc.roots().items():
        instance = str(obj.get(doc.INSTANCE) or '')
        if not instance:
            raise RuntimeError('Actor lacks durable instance identity: ' + uid)
        result['actors'][uid] = {'instance': instance, 'checkpoint': curves.full(obj)}
    return result


def ensure_baseline():
    """Create/reset the exact shared-history origin only at a fully clean acknowledged state."""
    global _status
    try:
        from . import actor_document as doc, actor_export
        if not doc.binding():
            _status = 'enable durable actor editing first'
            return False
        db = doc.activate(restore=False)
        info = doc.binding()
        baseline, sequence = db.baseline(info['document'])
        ack = int(bpy.context.scene.get(doc.ACK, 0))
        records = db.records(info['document'], 0)
        reset = baseline is None or any(
            r['payload']['transactionSeq'] > sequence and _kind(r) == 'resync'
            and (r.get('receipt') or {}).get('ok') is True for r in records)
        if not reset:
            if baseline.get('version') != 2:
                raise RuntimeError('legacy shared history needs protected resync before undo/redo')
            from .actor_history_observations import ERROR_KEY
            if bpy.context.scene.get(ERROR_KEY):
                raise RuntimeError('remote history incomplete; resync required: '
                                   + str(bpy.context.scene[ERROR_KEY]))
            _status = 'shared history ready'
            return True
        if any(r['payload']['transactionSeq'] > ack for r in records):
            _status = 'shared history waits for the durable queue to settle'
            return False
        fields, _, _, _ = actor_export.build_conditional_payload()
        if fields['replays']:
            _status = 'shared history waits until protected local edits are confirmed'
            return False
        data = _capture_baseline()
        db.set_baseline(info['document'], ack, data)
        from .actor_history_observations import ERROR_KEY
        bpy.context.scene.pop(ERROR_KEY, None)
        _status = 'shared history ready at transaction %d' % ack
        return True
    except Exception as exc:
        _status = 'shared history unavailable: %s' % exc
        return False


def _projected():
    """Return (full exact checkpoints, baseline sequence), or fail across an unresolved resync."""
    from . import actor_document as doc
    from . import actor_history_observations as observations
    if bpy.context.scene.get(observations.ERROR_KEY):
        raise RuntimeError('remote history incomplete; resync required')
    db = doc.activate(restore=False)
    info = doc.binding()
    baseline, sequence = db.baseline(info['document'])
    if baseline is None:
        raise RuntimeError('shared history baseline is not established')
    if baseline.get('version') != 2:
        raise RuntimeError('legacy shared history needs protected resync before undo/redo')
    roots = doc.roots()
    checkpoints = {}
    if set(baseline.get('actors', {})) != set(roots):
        raise RuntimeError('shared history actor structure changed')
    for uid, saved in baseline['actors'].items():
        if roots[uid].get(doc.INSTANCE) != saved.get('instance'):
            raise RuntimeError('shared history actor instance changed: ' + uid)
        checkpoints[uid] = copy.deepcopy(saved['checkpoint'])
    incoming = iter(db.observations(info['document'], sequence))
    next_incoming = next(incoming, None)
    for record in db.records(info['document'], sequence):
        while next_incoming is not None and next_incoming[0] < record['payload']['transactionSeq']:
            observations.merge(checkpoints, roots, next_incoming[1])
            next_incoming = next(incoming, None)
        kind = _kind(record)
        if kind == 'resync':
            raise RuntimeError('shared history needs a clean baseline after resync')
        if record.get('receipt') is not None and record['receipt'].get('ok') is not True:
            continue
        for uid, saved in (record.get('curves') or {}).items():
            if uid not in checkpoints:
                raise RuntimeError('shared history references an unknown actor')
            projected = (record.get('historyCurves') or {}).get(uid, saved)
            checkpoints[uid] = curves.merge(checkpoints[uid], projected)
    while next_incoming is not None:
        observations.merge(checkpoints, roots, next_incoming[1])
        next_incoming = next(incoming, None)
    return checkpoints, sequence


def before_checkpoints(replays):
    """Only an absent history may yield no checkpoint; damaged history must stop the edit."""
    from . import actor_document as doc
    db = doc.activate(restore=False)
    info = doc.binding()
    if db is None or not info or not db.has_baseline(info['document']):
        return {}
    _baseline, sequence = db.baseline(info['document'])
    db.observations(info['document'], sequence)
    ack = int(bpy.context.scene.get(doc.ACK, 0))
    if any(_kind(record) == 'resync' and record['payload']['transactionSeq'] <= ack
           and (record.get('receipt') or {}).get('ok') is True
           for record in db.records(info['document'], sequence)):
        # Keep-local reconciliation may still have unsent edits. They precede the
        # next clean history origin; this is an explicit completed fence, not an error.
        return {}
    checkpoints, _ = _projected()
    roots = doc.roots()
    result = {}
    for patch in replays:
        uid = patch['uuid']
        if (sync.shape_patch_is_structural(patch)
                and not set(patch.get('shapeKeys', {})) <= set(checkpoints[uid].get('shapeCurves', {}))):
            raise RuntimeError('durable structural ShapeKey edit needs an existing saved '
                               'ShapeKey-block baseline: ' + uid)
        result[uid] = curves.slice_checkpoint(roots[uid], checkpoints[uid], patch)
    return result


def _history():
    """Derive strict LIFO undo/redo stacks from confirmed durable receipts."""
    from . import actor_document as doc
    db = doc.activate(restore=False)
    info = doc.binding()
    baseline, sequence = db.baseline(info['document'])
    if baseline is None:
        raise RuntimeError('shared history baseline is not established')
    undo, redo = [], []
    for record in db.records(info['document'], sequence):
        receipt = record.get('receipt')
        if receipt is None:
            raise RuntimeError('shared history waits for pending durable work')
        kind = _kind(record)
        if kind == 'resync':
            raise RuntimeError('shared history needs a clean baseline after resync')
        if receipt.get('ok') is not True:
            continue
        if kind == 'edit' and receipt.get('undoAvailable') is True:
            if not record.get('beforeCurves'):
                raise RuntimeError('edit predates the exact shared-history baseline')
            undo.append(record)
            redo.clear()
        elif kind == 'compensation':
            target = record.get('targetRequest')
            direction = record.get('direction')
            if direction == 'undo':
                if not undo or undo[-1]['payload']['requestId'] != target:
                    raise RuntimeError('shared undo history is non-linear')
                redo.append(undo.pop())
            elif direction == 'redo':
                if not redo or redo[-1]['payload']['requestId'] != target:
                    raise RuntimeError('shared redo history is non-linear')
                undo.append(redo.pop())
            else:
                raise RuntimeError('invalid shared compensation history')
    return undo, redo


def ready():
    return ensure_baseline()


def availability():
    global _status
    if not ensure_baseline():
        return False, False
    try:
        undo, redo = _history()
        return bool(undo), bool(redo)
    except Exception as exc:
        _status = 'shared history paused: %s' % exc
        return False, False


def _target(direction):
    undo, redo = _history()
    stack = undo if direction == 'undo' else redo
    if not stack:
        raise RuntimeError('nothing to shared-%s' % direction)
    return stack[-1]


def _after_curves(record):
    effects = record.get('historyCurves') or {}
    return {uid: copy.deepcopy(effects.get(uid, saved))
            for uid, saved in record['curves'].items()}


def _scene_timing_pair(target, direction):
    before = target.get('sceneTimingBefore')
    after = target.get('sceneTimingAfter')
    if before is None and after is None:
        return None, None
    sync.scene_timing_scale(before)
    sync.scene_timing_scale(after)
    return (after, before) if direction == 'undo' else (before, after)


def queue(direction):
    """Persist compensation intent, then move exact local paths before network send."""
    global _status
    from . import actor_document as doc, actor_outbox as outbox, actor_leases
    if direction not in ('undo', 'redo'):
        raise ValueError(direction)
    mutation_started = False
    try:
        if (not outbox.bound() or not state._connected or not state._peer_ready
                or 'actor_shared_undo_v1' not in state.peer_capabilities):
            raise RuntimeError('shared undo/redo needs the connected durable peer')
        if outbox._pending is not None or doc.records():
            raise RuntimeError('shared undo/redo waits for the durable queue to settle')
        if not ensure_baseline():
            raise RuntimeError(_status)
        target = _target(direction)
        timing_expected, timing_desired = _scene_timing_pair(target, direction)
        if timing_expected is not None:
            if sync.current_scene_timing() != timing_expected:
                raise RuntimeError('Scene FPS changed after the target transaction')
        elif abs(float(target['scale']) - (
                bpy.context.scene.render.fps / (bpy.context.scene.render.fps_base or 1) / 20)) > 1e-12:
            raise RuntimeError('FPS changed since the target transaction; resync first')
        roots = doc.roots(target)
        after = _after_curves(target)
        expected = after if direction == 'undo' else target['beforeCurves']
        desired = target['beforeCurves'] if direction == 'undo' else after
        for uid in desired:
            curves.validate(roots[uid], expected[uid])
            curves.validate(roots[uid], desired[uid])
            if not curves.matches(roots[uid], expected[uid]):
                raise RuntimeError('newer local edit on shared-%s target: %s' % (direction, uid))

        payload = copy.deepcopy(target['payload'])
        payload['requestId'] = uuid.uuid4().hex
        payload['compensateOf'] = target['payload']['requestId']
        payload['compensateDirection'] = direction
        payload.pop('transactionSeq', None)
        payload.pop('client', None)
        record = {
            'kind': 'compensation', 'targetRequest': target['payload']['requestId'],
            'direction': direction, 'payload': payload, 'scale': target['scale'],
            'curves': copy.deepcopy(desired), 'beforeCurves': copy.deepcopy(expected),
            'instances': {uid: roots[uid][doc.INSTANCE] for uid in desired},
            'leasePaths': actor_leases.required(payload),
            'generation': int(bpy.context.scene.get(doc.GENERATION, 0)),
        }
        if timing_expected is not None:
            record['sceneTiming'] = copy.deepcopy(timing_desired)
            record['beforeSceneTiming'] = copy.deepcopy(timing_expected)
        db = doc.activate(restore=False)
        mutation_started = True
        outbox._paused = True
        saved = db.enqueue(doc.binding()['document'], record)
        bpy.context.scene[doc.SEEN] = saved['payload']['transactionSeq']
        restored = []
        try:
            if timing_desired is not None:
                sync.apply_scene_timing(timing_desired)
            for uid in desired:
                curves.restore(roots[uid], desired[uid])
                restored.append(uid)
        except Exception:
            for uid in restored:
                curves.restore(roots[uid], expected[uid])
            if timing_expected is not None:
                sync.apply_scene_timing(timing_expected)
            raise
        outbox._pending = None
        outbox.retry()
        outbox._status = 'shared %s queued; exact Blender checkpoint applied' % direction
        _status = 'shared %s queued' % direction
        outbox.pump()
        return True
    except Exception as exc:
        # A rejected preflight must not stop an older valid queued operation.
        if mutation_started:
            outbox._paused = True
        _status = 'shared %s not queued: %s' % (direction, exc)
        return False


def recover_local(record):
    """Crash/load recovery for a queued compensation without overwriting newer saved edits."""
    from . import actor_document as doc
    baseline, _ = doc._store.baseline(doc.binding()['document'])
    if baseline is None or baseline.get('version') != 2:
        raise RuntimeError('legacy compensation requires explicit protected resync')
    roots = doc.roots(record)
    generation = int(bpy.context.scene.get(doc.GENERATION, 0))
    timing = record.get('sceneTiming')
    before_timing = record.get('beforeSceneTiming')
    if timing is not None:
        current_timing = sync.current_scene_timing()
        if current_timing == before_timing and generation <= record['generation']:
            sync.apply_scene_timing(timing)
        elif current_timing != timing and generation <= record['generation']:
            raise RuntimeError('new scene timing precedes shared compensation recovery')
    for uid, desired in record['curves'].items():
        expected = record['beforeCurves'][uid]
        current = curves.capture(roots[uid], desired['patch'])
        if curves.same(current, desired):
            continue
        if curves.same(current, expected) and generation <= record['generation']:
            curves.restore(roots[uid], desired)
        elif generation <= record['generation']:
            raise RuntimeError('new local edit precedes shared compensation recovery: ' + uid)


def _target_record(request_id):
    return next((r for r in _full_records()
                 if r['payload']['requestId'] == request_id), None)


def acknowledge(record):
    """Advance/rollback local half after the durable server compensation receipt."""
    global _status
    from . import actor_document as doc
    receipt = record['receipt']
    target = _target_record(record['targetRequest'])
    if target is None:
        raise RuntimeError('shared compensation target vanished from durable history')
    roots = doc.roots(record)
    if receipt.get('ok') is True:
        for patch in target['payload']['replays']:
            uid = patch['uuid']
            current = sync.read(roots[uid])
            if current is None:
                raise RuntimeError('shared compensation actor lost its merge base: ' + uid)
            if record['direction'] == 'undo':
                source = target['before'][uid]
            else:
                source = sync.advanced_base(target['before'][uid], patch,
                                            target['scale'], target['local'][uid])
            timed = target.get('sceneTimingBefore') is not None
            base = sync.merge_patch_base(current, source, patch, allow_time_basis=timed)
            if timed:
                base['tick_scale'] = source['tick_scale']
                base['sceneTiming'] = copy.deepcopy(source['sceneTiming'])
            roots[uid][sync.KEY] = encode(base)
            sync.remember_remote_fingerprint(roots[uid], base, base['tick_scale'])
        final_status = 'shared %s confirmed' % record['direction']
    else:
        kept_newer = False
        for uid, desired in record['curves'].items():
            if curves.matches(roots[uid], desired):
                curves.restore(roots[uid], record['beforeCurves'][uid])
            else:
                kept_newer = True
        if record.get('sceneTiming') is not None:
            if sync.current_scene_timing() == record['sceneTiming']:
                sync.apply_scene_timing(record['beforeSceneTiming'])
            else:
                kept_newer = True
        final_status = ('shared %s conflict; newer local edits kept' if kept_newer
                        else 'shared %s conflict; Blender rolled back') % record['direction']
    bpy.context.scene[doc.ACK] = record['payload']['transactionSeq']
    history_ok = ensure_baseline()
    history_status = _status
    _status = final_status if history_ok else final_status + '; ' + history_status
    return True
