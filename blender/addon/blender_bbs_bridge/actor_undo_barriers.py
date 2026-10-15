"""Native undo cannot revoke still-current external edits through a stale scene counter."""
from . import actor_curve_checkpoint as curves, actor_leases as leases
from . import actor_history_observations as observations


def _parts(patch):
    uid = patch['uuid']
    for section in ('channels', 'strings', 'bones'):
        for name, value in patch.get(section, {}).items():
            yield {'uuid': uid, section: {name: value}}
    for bone, properties in patch.get('rigProperties', {}).items():
        for prop, value in properties.items():
            yield {'uuid': uid, 'rigProperties': {bone: {prop: value}}}
    if 'visibility' in patch:
        yield {'uuid': uid, 'visibility': patch['visibility']}
    shape = {key: patch[key] for key in ('shapeKeys', 'shapeGrid', 'shapeTimeline', 'expected')
             if key in patch}
    if any(key in shape for key in ('shapeKeys', 'shapeGrid', 'shapeTimeline')):
        yield {'uuid': uid, **shape}


def _resources(patch):
    return leases.keys(leases.payload_paths({'replays': [patch]}))


def capture(db, document, roots):
    """Protect only latest remote paths that have no newer local intent or authored edit."""
    latest_local = {}
    resync = 0
    for record in db.records(document):
        if record.get('receipt') is not None and record['receipt'].get('ok') is not True:
            continue
        payload = record['payload']
        sequence = payload['transactionSeq']
        if payload.get('resync'):
            resync = max(resync, sequence)
        for patch in payload.get('replays', []):
            for key in _resources(patch):
                latest_local[key] = max(sequence, latest_local.get(key, 0))
    seen, result = set(), []
    for anchor, observed in reversed(db.observations(document)):
        observations.validate_context(observed, roots)
        for uid, checkpoint in observed['curves'].items():
            for patch in _parts(checkpoint['patch']):
                keys = _resources(patch)
                superseded = bool(keys & seen)
                seen.update(keys)
                if (not keys or superseded or resync > anchor
                        or any(latest_local.get(key, 0) > anchor for key in keys)):
                    continue
                saved = curves.slice_checkpoint(roots[uid], checkpoint, patch)
                if curves.matches(roots[uid], saved):
                    result.append((uid, saved))
    return result


def restore(roots, barriers):
    """Verify after normal restoration; roll back this recovery batch if it fails."""
    changed = [(uid, saved) for uid, saved in barriers if not curves.matches(roots[uid], saved)]
    for uid, saved in changed:
        curves.validate(roots[uid], saved)
    rollback = [(uid, curves.capture(roots[uid], saved['patch'])) for uid, saved in changed]
    try:
        for uid, saved in changed:
            curves.restore(roots[uid], saved)
        if any(not curves.matches(roots[uid], saved) for uid, saved in barriers):
            raise RuntimeError('External undo checkpoint was not restored exactly')
    except Exception:
        for uid, saved in rollback:
            curves.restore(roots[uid], saved)
        raise
    if changed:
        print('[bbs-bridge] native undo: restored %d external checkpoints independently of scene counter'
              % len(changed))
