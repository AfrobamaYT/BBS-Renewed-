"""Durable FIFO actor sender over the existing bridge transport; main thread only."""
import copy
import time
import uuid
import bpy
from . import state, actor_document as document, actor_channel_sync as sync
from . import actor_curve_checkpoint as curves
from . import actor_leases
from .core import _send, _next_seq

_status = 'disabled'
_opening = None
_pending = None
_next_attempt = 0.0
_paused = False
_canonical_refresh_due = False
_canonical_refresh_pending = False
_canonical_refresh_sent_at = 0.0


def bound():
    return bool(bpy.context.scene.get(document.BINDING))


def enable():
    global _opening, _status
    from . import actor_export
    if (bound() or _opening or actor_export._request_id or not bpy.data.filepath
            or not state._connected or not state._peer_ready
            or 'actor_durable_v1' not in state.peer_capabilities or not state.bound_film
            or state.film_switch_detected or not document.roots()):
        _status = 'save .blend, connect and import actors before enabling durable editing'
        return False
    request_id = uuid.uuid4().hex
    _opening = (request_id, bpy.context.scene, state.bound_film, state._connected_endpoint, time.monotonic() + 30)
    _send({'type': 'ACTORS_PROJECT_OPEN', 'origin': 'blender', 'seq': _next_seq(),
           'fields': {'requestId': request_id, 'film': state.bound_film}})
    _status = 'establishing durable project binding'
    return True


def projected_bases():
    """The comparison basis includes queued intent, including while disconnected."""
    roots = document.roots()
    bases = {uid: sync.read(obj) for uid, obj in roots.items()}
    for previous in document.records():
        if previous.get('kind') == 'compensation':
            raise RuntimeError('wait for shared undo/redo before queueing more edits')
        if previous['payload'].get('resync'):
            raise RuntimeError('wait for reconciliation before queueing more edits')
        if previous['receipt'] is not None and previous['receipt'].get('ok') is not True:
            raise RuntimeError('resolve the rejected operation before queueing dependent edits')
        for patch in previous['payload']['replays']:
            uid = patch['uuid']
            bases[uid] = sync.advanced_base(bases[uid], patch, previous['scale'], previous['local'][uid])
    return roots, bases


def queue(uuids=None):
    global _status, _next_attempt
    from . import actor_export
    try:
        db = document.activate()
        if db is None:
            raise RuntimeError('durable document is not enabled')
        info = document.binding()
        roots, bases = projected_bases()
        fields, _, _, scale = actor_export.build_conditional_payload(uuids, bases)
        if not fields['replays']:
            _status = 'no new protected edits beyond the queued state'
            return False
        if (any(sync.shape_patch_creates_first_grid(patch) for patch in fields['replays'])
                and 'actor_first_shape_durable_v1' not in state.peer_capabilities):
            raise RuntimeError('peer lacks durable first-ShapeKey support')
        if (any(patch.get('shapeGrid') for patch in fields['replays'])
                and 'actor_shape_grid_durable_v1' not in state.peer_capabilities):
            raise RuntimeError('peer lacks durable ShapeKey grid support')
        if (any(sync.shape_patch_uses_easing(patch) for patch in fields['replays'])
                and 'actor_shape_easing_patch_v1' not in state.peer_capabilities):
            raise RuntimeError('peer lacks exact ShapeKey easing support')
        if (any('visibility' in patch for patch in fields['replays'])
                and 'actor_visibility_durable_v1' not in state.peer_capabilities):
            raise RuntimeError('peer lacks durable actor visibility support')
        if (any(patch.get('shapeTimeline') and not sync.shape_timeline_is_interp_only(patch)
                for patch in fields['replays'])
                and 'actor_shape_timeline_durable_transform_v1' not in state.peer_capabilities):
            raise RuntimeError('peer lacks durable ShapeKey timeline-transform support')
        fps_changed = [uid for uid in roots if bases[uid].get('tick_scale') != scale]
        timing_before = timing_after = None
        if fps_changed:
            if 'actor_scene_timing_durable_v1' not in state.peer_capabilities:
                raise RuntimeError('peer lacks durable scene FPS history support')
            if set(patch['uuid'] for patch in fields['replays']) != set(roots):
                raise RuntimeError('durable FPS regridding must include every bound actor')
            timings = [bases[uid].get('sceneTiming') for uid in sorted(roots)]
            if (not timings or any(timing != timings[0] for timing in timings)
                    or abs(sync.scene_timing_scale(timings[0])
                           - float(bases[sorted(roots)[0]]['tick_scale'])) > 1e-12):
                raise RuntimeError('durable FPS regridding needs one exact clean scene timing baseline')
            timing_before = copy.deepcopy(timings[0])
            timing_after = sync.current_scene_timing(scale)
        fields.update(film=info['film'], project=info['project'])
        from . import actor_shared_undo
        history_before = actor_shared_undo.before_checkpoints(fields['replays'])
        structural = [patch for patch in fields['replays']
                      if sync.shape_patch_is_structural(patch)]
        missing = [patch['uuid'] for patch in structural
                   if patch['uuid'] not in history_before]
        if missing:
            raise RuntimeError(
                'durable structural ShapeKey edit needs an existing saved '
                'ShapeKey-block baseline: ' + ', '.join(sorted(set(missing))))
        full_timeline = [patch['uuid'] for patch in fields['replays']
                         if patch.get('shapeGrid') or (patch.get('shapeTimeline')
                         and not sync.shape_timeline_is_interp_only(patch))]
        legacy_timeline = [uid for uid in full_timeline
                           if uid not in history_before
                           or not curves.has_full_timeline_effect(history_before[uid])]
        if legacy_timeline:
            raise RuntimeError(
                'durable ShapeKey timeline transform needs a version-2 clean history '
                'baseline (Keep-local Resync): ' + ', '.join(sorted(set(legacy_timeline))))
        record = {'kind': 'edit', 'payload': fields, 'scale': scale, 'local': {}, 'curves': {},
                  'historyCurves': {},
                  'beforeCurves': {}, 'instances': {}, 'before': {},
                  'generation': int(bpy.context.scene.get(document.GENERATION, 0))}
        if timing_before is not None:
            record['sceneTimingBefore'] = timing_before
            record['sceneTimingAfter'] = timing_after
        for patch in fields['replays']:
            uid = patch['uuid']
            obj = roots[uid]
            if not obj.get(document.INSTANCE):
                raise RuntimeError('new actor needs a saved document checkpoint before queueing')
            record['instances'][uid] = obj[document.INSTANCE]
            record['before'][uid] = bases[uid]
            record['local'][uid] = sync.capture_export_signatures(obj, patch)
            record['curves'][uid] = curves.capture(obj, patch)
            if sync.shape_patch_creates_first_grid(patch):
                record['historyCurves'][uid] = curves.project_first_grid_effect(
                    obj, record['curves'][uid], patch['shapeKeys'], scale)
                record['local'][uid]['shapeTimeline'] = copy.deepcopy(
                    record['historyCurves'][uid]['shapeTimelineCurve']['curve'])
            elif patch.get('shapeTimeline'):
                record['historyCurves'][uid] = curves.project_timeline_effect(
                    record['curves'][uid], patch['shapeTimeline'], scale,
                    not sync.shape_timeline_is_interp_only(patch))
            if uid in history_before:
                record['beforeCurves'][uid] = history_before[uid]
        record['leasePaths'] = actor_leases.required(fields)
        saved = db.enqueue(info['document'], record)
        bpy.context.scene[document.SEEN] = saved['payload']['transactionSeq']
        _status = 'queued protected operation %s (saved on disk)' % saved['payload']['transactionSeq']
        _next_attempt = 0.0
        return True
    except Exception as exc:
        _status = 'not queued: %s' % exc
        return False


def retry():
    global _paused, _next_attempt
    _paused, _next_attempt = False, 0.0


def pump():
    global _pending, _next_attempt, _status, _opening
    global _canonical_refresh_due, _canonical_refresh_pending, _canonical_refresh_sent_at
    try:
        if _opening and time.monotonic() > _opening[4]:
            _opening = None
            _status = 'project-open reply missing; no Blender binding created'
        if not bound():
            return
        db = document.activate()
        info = document.binding()
        if _pending:
            if _pending[0] == info['document'] and state._connected and time.monotonic() < _pending[2]:
                return
            _pending = None
        if _paused or time.monotonic() < _next_attempt:
            return
        _next_attempt = time.monotonic() + 0.5
        document.acknowledge()
        from .actor_resync import suffix
        remaining = suffix(document.records())
        if not remaining:
            if (_canonical_refresh_due or (_canonical_refresh_pending
                    and time.monotonic() - _canonical_refresh_sent_at >= 5.0)):
                if (state._connected and state._peer_ready and not state.film_switch_detected
                        and state.peer_project == info['project'] and state.bound_film == info['film']
                        and list(state._connected_endpoint or ()) == info['endpoint']):
                    _send({'type': 'ACTORS_EXPORT', 'origin': 'blender', 'uuid': 'actors',
                           'seq': _next_seq(), 'fields': {}})
                    _canonical_refresh_due = False
                    _canonical_refresh_pending = True
                    _canonical_refresh_sent_at = time.monotonic()
            _status = ('waiting for authoritative BBS bone state'
                       if _canonical_refresh_due or _canonical_refresh_pending
                       else 'all queued operations confirmed')
            return
        record = remaining[0]
        if record['receipt'] is not None:
            _status = 'conflict: ' + str(record['receipt'].get('conflict') or record['receipt'].get('error'))
            return
        document.roots(record)
        if not state._connected or not state._peer_ready:
            _status = 'offline: queued edits retained on disk'
            return
        if ('actor_durable_v1' not in state.peer_capabilities or state.film_switch_detected
                or state.peer_project != info['project'] or state.bound_film != info['film']
                or list(state._connected_endpoint or ()) != info['endpoint']):
            _status = 'different project/endpoint or incompatible peer: queue paused'
            return
        payload = record['payload']
        kind = 'ACTORS_IMPORT_RECEIPT' if record['sent'] else 'ACTORS_IMPORT'
        authorization = {} if record['sent'] else actor_leases.envelope(record)
        db.mark_sent(info['document'], payload['transactionSeq'])
        _pending = (info['document'], payload['requestId'], time.monotonic() + 10)
        _send({'type': kind, 'origin': 'blender', 'uuid': 'actors', 'seq': _next_seq(), 'fields': payload, **authorization})
        _status = 'recovering receipt' if record['sent'] else 'sending queued operation'
    except Exception as exc:
        _status = 'durable queue paused: %s' % exc


def handle_result(fields):
    global _opening, _status, _pending, _paused, _next_attempt, _canonical_refresh_due
    if not isinstance(fields, dict):
        return False
    if _opening and fields.get('requestId') == _opening[0]:
        _, scene, film, endpoint, _ = _opening
        _opening = None
        try:
            if fields.get('ok') is not True or not fields.get('project'):
                raise RuntimeError(fields.get('error') or 'project identity unavailable')
            if scene != bpy.context.scene or film != state.bound_film or endpoint != state._connected_endpoint:
                raise RuntimeError('editor context changed during project binding')
            document.enable(fields['project'], film, endpoint)
            state.peer_project = fields['project']
            _status = 'durable editing enabled; document saved'
        except Exception as exc:
            _status = 'durable editing not enabled: %s' % exc
        return True
    if not bound():
        return False
    try:
        db = document.activate()
        info = document.binding()
        record = next((r for r in document.records() if r['payload']['requestId'] == fields.get('requestId')), None)
        if record is None:
            return False
        _pending = None
        if (fields.get('notApplied') is True and fields.get('durable') is not True
                and fields.get('project') == info['project']):
            db.mark_sent(info['document'], record['payload']['transactionSeq'], False)
            _next_attempt = time.monotonic() + max(0, float(fields.get('retryAfterMs', 0))) / 1000
        elif (type(fields.get('ok')) is bool and fields.get('durable') is True
              and fields.get('project') == info['project'] and not fields.get('receiptUnknown')):
            receipt = dict(fields)
            receipt.pop('receiptRecovered', None)
            db.confirm(info['document'], record['payload']['transactionSeq'], receipt)
            document.acknowledge()
            if receipt['ok'] and any(replay.get('bones')
                                     for replay in record['payload'].get('replays', [])):
                # BBS stores a shared native interpolation for PoseTransform keys.
                # Our optimistic sent curves are not valid preview preconditions until
                # the authoritative export replaces them (including after a queued write).
                _canonical_refresh_due = True
            if ((record.get('sceneTimingAfter') is not None or record.get('sceneTiming') is not None)
                    and int(bpy.context.scene.get(document.ACK, 0)) >= record['payload']['transactionSeq']):
                # A reconnect snapshot may predate this regrid. Read again only after
                # the durable receipt establishes the new frame/tick basis locally.
                _send({'type': 'ACTORS_EXPORT', 'origin': 'blender', 'uuid': 'actors',
                       'seq': _next_seq(), 'fields': {}})
            _status = 'durably confirmed' if receipt['ok'] else 'conflict: %s' % receipt.get('conflict')
        else:
            _paused = True
            _status = 'outcome unresolved: %s; intent retained' % fields.get('error', 'malformed receipt')
        return True
    except Exception as exc:
        _paused = True
        _status = 'receipt retained, local recovery paused: %s' % exc
        return True


def accept_snapshot(fields):
    global _canonical_refresh_due, _canonical_refresh_pending
    if not bound():
        return True
    try:
        document.activate()
        if fields.get('project') != document.binding()['project']:
            return False
        incoming = {r.get('uuid') for r in fields.get('replays', []) if isinstance(r, dict)}
        if set(document.roots()) - incoming:
            # Structural reconciliation is explicit; never delete a durable actor with queued work.
            return False
        if fields.get('ok') is True:
            _canonical_refresh_due = False
            _canonical_refresh_pending = False
        return True
    except Exception:
        return False
