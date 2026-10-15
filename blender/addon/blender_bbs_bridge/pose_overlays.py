"""BBS-owned pose layers on native NLA, preserving the editable base.

BBS (ModelFormRenderer.applyPose) adds an overlay transform while its weight `fix` is 0 and
otherwise lerps every channel toward the overlay value by `fix` (euler and location/scale
linearly, quaternions by slerp). Unweighted layers are ADD/COMBINE strips; weighted bones go
on a REPLACE strip whose influence is the fix curve, which is that same lerp for euler,
location and scale. Quaternion bones blend component-wise in Blender (nlerp, not slerp).
Editing derived NLA strips is not built.
"""
import functools
import json
import bpy
from bpy.app.handlers import persistent
from . import state

SOURCE = 'bbs_pose_overlay_source_v1'
BASE = 'BBS Bridge: Base'
PREFIX = 'BBS Bridge: Pose/'


def _tweak(obj):
    """Native upper-stack tweak mode keeps the original Action editable and visible.

    Nicht-fatal: aus einem Bridge-Tick/MCP-Aufruf scheitert nla.tweakmode_enter an seinem Poll
    (gemessen 2026-10-14, auch in der GUI-Sitzung). AnimData.use_tweak_mode schaltet die oberen
    Spuren ab (Overlays wirkungslos) und taugt nicht als Ersatz. Die Overlays wirken ohne Tweak;
    nur das Bearbeiten der Basis braucht dann Tab im NLA-Editor."""
    try:
        _tweak_enter(obj)
    except RuntimeError as e:
        print("[bbs-bridge] NLA-Tweak fuer %s nicht moeglich (%s) — Overlays wirken, Basis-Action "
              "per Tab im NLA-Editor bearbeiten" % (obj.name, e))


def _tweak_enter(obj):
    ad = obj.animation_data
    if ad.use_tweak_mode or _base(obj) is None:
        return
    if bpy.context.screen is None:
        raise RuntimeError('NLA base editing requires a Blender screen context')
    area = next((a for a in bpy.context.screen.areas if a.type == 'NLA_EDITOR'), bpy.context.screen.areas[0])
    old_type, old_active = area.type, bpy.context.view_layer.objects.active
    selected = list(bpy.context.selected_objects)
    try:
        for other in selected:
            other.select_set(False)
        obj.select_set(True)
        bpy.context.view_layer.objects.active = obj
        ad.nla_tracks.active = ad.nla_tracks.get(BASE)
        ad.nla_tracks.active.strips[0].select = True
        area.type = 'NLA_EDITOR'
        with bpy.context.temp_override(area=area, region=next(r for r in area.regions if r.type == 'WINDOW')):
            if bpy.ops.nla.tweakmode_enter(isolate_action=False, use_upper_stack_evaluation=True) != {'FINISHED'}:
                raise RuntimeError('Native NLA tweak-mode entry failed')
    finally:
        area.type = old_type
        obj.select_set(False)
        for other in selected:
            other.select_set(True)
        bpy.context.view_layer.objects.active = old_active


@persistent
def remember_frame(scene, _depsgraph=None):
    from . import actor_pose_preview
    for obj in scene.objects:
        if obj.get(SOURCE) is not None:
            actor_pose_preview.adopt(obj)


def register():
    if remember_frame not in bpy.app.handlers.frame_change_post:
        bpy.app.handlers.frame_change_post.append(remember_frame)


def unregister():
    if remember_frame in bpy.app.handlers.frame_change_post:
        bpy.app.handlers.frame_change_post.remove(remember_frame)


def _base(obj):
    ad = obj.animation_data
    track = ad.nla_tracks.get(BASE) if ad and obj.get(SOURCE) is not None else None
    return track.strips[0] if track and len(track.strips) == 1 else None


def action(obj):
    """Authored base, not the evaluated or flattened NLA result."""
    ad = obj.animation_data
    if ad is None:
        return None, None
    if ad.action is not None:
        return ad.action, getattr(ad, 'action_slot', None)
    strip = _base(obj)
    return (strip.action, getattr(strip, 'action_slot', None)) if strip else (None, None)


def collections(obj):
    act, slot = action(obj)
    if act is None:
        return []
    result = []
    if slot is not None:
        for layer in act.layers:
            for strip in layer.strips:
                bag = strip.channelbag(slot)
                if bag is not None:
                    result.append(bag.fcurves)
    legacy = getattr(act, 'fcurves', None)
    return result or ([legacy] if legacy is not None else [])


def _layers(replay):
    value = (replay.get('properties') or {}).get('poseOverlays', [])
    if not isinstance(value, list):
        raise RuntimeError('Invalid BBS pose-overlay payload')
    return value


def _fix_keys(source):
    return [k for k in source.get('fix', []) if isinstance(k, dict)]


def _weighted(keys):
    return any(abs(float(k['value'])) > 1e-7 for k in keys)


def _strip(obj, name, act, slot, blend):
    track = obj.animation_data_create().nla_tracks.new()
    track.name = name
    track.lock = name != BASE
    strip = track.strips.new(name, 0, act)
    if slot is not None:
        strip.action_slot = slot
    end = max(1., float(act.frame_range[1]))
    strip.action_frame_start, strip.action_frame_end = 0., end
    strip.frame_start, strip.frame_end = 0., end
    strip.blend_type, strip.extrapolation = blend, 'HOLD'
    return strip


def _remove_layers(obj):
    for track in list(obj.animation_data.nla_tracks):
        if track.name.startswith(PREFIX):
            acts = [strip.action for strip in track.strips]
            obj.animation_data.nla_tracks.remove(track)
            for act in acts:
                if act.users == 0:
                    bpy.data.actions.remove(act)


def _validate(obj, layers):
    for layer in layers:
        for name, source in layer['bones'].items():
            pb = obj.pose.bones.get(name) if obj.type == 'ARMATURE' else None
            # BBS ignores poses for bones absent from the current model too.
            # Euler-Overlay auf Quaternion-Knochen (oder umgekehrt): actor_bones._apply_source
            # rechnet die Keys um, die COMBINE-Spur verknuepft sie. Exakt, solange die Basis an
            # diesem Knochen nicht selbst rotiert; sonst addiert BBS Euler-Winkel, Blender
            # multipliziert Quaternionen.
            if pb is not None and bool(source.get('quat')) != (pb.rotation_mode == 'QUATERNION'):
                if any(abs(float(k.get('value', 0.0))) > 1e-7
                       for axis in ('rx', 'ry', 'rz') for k in source.get(axis, [])):
                    print("[bbs-bridge] overlay %s/%s: Rotation umgerechnet (Euler->Quaternion); "
                          "nur exakt bei unrotierter Basis" % (layer.get('id'), name))


def _build_layer(obj, layer, scale):
    """Bones sharing one fix curve share strips; unweighted bones keep the additive strips."""
    groups = {}
    for name, source in layer['bones'].items():
        keys = _fix_keys(source)
        signature = json.dumps(keys, sort_keys=True) if _weighted(keys) else ''
        groups.setdefault(signature, {})[name] = source
    for index, (signature, bones) in enumerate(sorted(groups.items())):
        if not signature:
            _build_additive(obj, layer['id'], bones, scale)
        else:
            suffix = '' if index == 0 else '#%d' % index
            _build_weighted(obj, layer['id'] + suffix, bones, scale, json.loads(signature))


def _write_action(obj, bones, scale, keep):
    """Curves of `bones` through the actor-bone writer on a temporary armature; `keep(fc)` filters."""
    from . import actor_bones
    writer = bpy.data.objects.new('BBS_OverlayCurveWriter', obj.data)
    act, slot = None, None
    try:
        bpy.context.scene.collection.objects.link(writer)
        bpy.context.view_layer.update()
        for name, source in bones.items():
            pb = writer.pose.bones[name]
            pb.rotation_mode = obj.pose.bones[name].rotation_mode
            actor_bones._apply_source(writer, pb, source, scale, set())
        act, slot = action(writer)
        if act is not None:
            for collection in collections(writer):
                for fc in list(collection):
                    if not keep(fc):
                        collection.remove(fc)
    finally:
        bpy.data.objects.remove(writer)
    if act is not None and not any(len(c) for c in _collections_of(act, slot)):
        if act.users == 0:
            bpy.data.actions.remove(act)
        return None, None
    return act, slot


def _collections_of(act, slot):
    result = []
    if slot is not None:
        for layer in act.layers:
            for strip in layer.strips:
                bag = strip.channelbag(slot)
                if bag is not None:
                    result.append(bag.fcurves)
    legacy = getattr(act, 'fcurves', None)
    return result or ([legacy] if legacy is not None else [])


def _influence(strip, keys, scale):
    """The strip's influence follows `keys` ({tick, value, interp...}) like any BBS channel."""
    from . import actors
    strip.use_animated_influence = True
    fc = next((f for f in strip.fcurves if f.data_path == 'influence'), None)
    if fc is None:
        strip.keyframe_insert('influence', frame=0.0)
        fc = next(f for f in strip.fcurves if f.data_path == 'influence')
    while len(fc.keyframe_points):
        fc.keyframe_points.remove(fc.keyframe_points[0])
    pts = []
    for kf in sorted(keys, key=lambda k: float(k['tick'])):
        frame, value = float(kf['tick']) * scale, min(1.0, max(0.0, float(kf['value'])))
        kp = fc.keyframe_points.insert(frame, value, options={'FAST'})
        actors._apply_kf_shape(kp, kf, frame, value, 1.0, scale)
        pts.append((frame, value, kf))
    fc.update()
    actors._exact_hermite(fc, pts)


def _build_weighted(obj, ident, raw, scale, fix):
    """REPLACE with influence = fix: (1 - fix) * below + fix * overlay, as BBS' lerp. Ticks where
    the fix curve itself sits at 0 add instead (BBS switches mode there): an ADD strip whose
    influence steps to 1 exactly at those keys carries that part."""
    from . import pose_units
    bones = {name: source for name, source in pose_units.curves(obj, raw, True).items()
             if name in obj.pose.bones}
    act, slot = _write_action(obj, bones, scale, lambda fc: True)
    if act is not None:
        _influence(_strip(obj, PREFIX + ident + '/REPLACE', act, slot, 'REPLACE'), fix, scale)
    if any(abs(float(k['value'])) <= 1e-7 for k in fix):
        steps = [dict(k, value=1.0 if abs(float(k['value'])) <= 1e-7 else 0.0, interp='constant') for k in fix]
        for quaternion, blend in ((False, 'ADD'), (True, 'COMBINE')):
            act, slot = _write_action(obj, bones, scale, _additive_filter(quaternion))
            if act is not None:
                _influence(_strip(obj, PREFIX + ident + '/' + blend, act, slot, blend), steps, scale)


def _additive_filter(quaternion):
    def keep(fc):
        if fc.data_path.endswith('.rotation_quaternion') != quaternion:
            return False
        if fc.data_path.endswith('.scale'):
            for key in fc.keyframe_points:
                key.co.y -= 1.
                key.handle_left.y -= 1.
                key.handle_right.y -= 1.
            fc.update()
        return True
    return keep


def _build_additive(obj, ident, raw, scale):
    from . import actor_bones, pose_units
    bones = {name: source for name, source in pose_units.curves(obj, raw, True).items()
             if name in obj.pose.bones}
    layer = {'id': ident}
    # Scalar/Euler addition and quaternion composition use different native blends.
    for quaternion, blend in ((False, 'ADD'), (True, 'COMBINE')):
        writer = bpy.data.objects.new('BBS_OverlayCurveWriter', obj.data)
        act = None
        try:
            bpy.context.scene.collection.objects.link(writer)
            bpy.context.view_layer.update()
            for name, source in bones.items():
                pb = writer.pose.bones[name]
                pb.rotation_mode = obj.pose.bones[name].rotation_mode
                actor_bones._apply_source(writer, pb, source, scale, set())
            act, slot = action(writer)
            if act is None:
                continue
            for collection in collections(writer):
                for fc in list(collection):
                    if fc.data_path.endswith('.rotation_quaternion') != quaternion:
                        collection.remove(fc)
                    elif fc.data_path.endswith('.scale'):
                        for key in fc.keyframe_points:
                            key.co.y -= 1.
                            key.handle_left.y -= 1.
                            key.handle_right.y -= 1.
                        fc.update()
            if any(len(c) for c in collections(writer)):
                _strip(obj, PREFIX + layer['id'] + '/' + blend, act, slot, blend)
        finally:
            bpy.data.objects.remove(writer)
            if act is not None and act.users == 0:
                bpy.data.actions.remove(act)


def apply(obj, replay, scale):
    layers = _layers(replay)
    encoded = json.dumps(layers, sort_keys=True, separators=(',', ':'))
    if obj.get(SOURCE) == encoded:
        _tweak(obj)
        return
    base = _base(obj)
    if not layers and base is None:
        return
    ad = obj.animation_data_create()
    if base is None and ad.nla_tracks:
        raise RuntimeError('Existing native NLA needs explicit overlay reconciliation')
    _validate(obj, layers)
    if ad.use_tweak_mode:
        ad.use_tweak_mode = False
    if base is None:
        act, slot = action(obj)
        if act is None:
            raise RuntimeError('BBS overlays require an imported base action')
        _strip(obj, BASE, act, slot, 'REPLACE')
        ad.action = None
    _remove_layers(obj)
    for layer in layers:
        _build_layer(obj, layer, scale)
    obj[SOURCE] = encoded
    if not layers:
        base = _base(obj)
        ad.action = base.action
        if base.action_slot is not None:
            ad.action_slot = base.action_slot
        ad.nla_tracks.remove(ad.nla_tracks.get(BASE))
        del obj[SOURCE]
    bpy.context.view_layer.update()
    if layers:
        _tweak(obj)
        bpy.context.view_layer.update()
        from . import actor_pose_preview
        actor_pose_preview.adopt(obj)


def importing(function):
    """Existing channel writers edit the base temporarily, never a new empty action."""
    @functools.wraps(function)
    def run(replays, *args, **kwargs):
        from . import actors
        state._import_incomplete = {}
        skip = set()
        for replay in replays:
            try:
                layers = _layers(replay)
                obj = actors._find_actor_by_uuid(replay.get('uuid', ''))
                if obj is not None:
                    _validate(obj, layers)
            except RuntimeError as e:
                # Nur dieser actor verliert seine overlays (laut gemeldet), der film-import laeuft.
                uuid = replay.get('uuid', '')
                skip.add(uuid)
                state._import_incomplete[uuid] = (replay.get('name') or replay.get('label') or uuid[:8], str(e))
                print("[bbs-bridge] UNVOLLSTAENDIG %s: %s -> ohne Pose-Overlays" % (uuid[:8], e))
        held = []
        for replay in replays:
            obj = actors._find_actor_by_uuid(replay.get('uuid', ''))
            base = _base(obj) if obj is not None else None
            if base is not None and obj.animation_data.action is None:
                ad = obj.animation_data
                obj.animation_data.nla_tracks.get(BASE).mute = True
                ad.action = base.action
                if base.action_slot is not None:
                    ad.action_slot = base.action_slot
                held.append(obj)
        try:
            result = function(replays, *args, **kwargs)
        finally:
            for obj in held:
                try:
                    obj.animation_data.action = None
                    obj.animation_data.nla_tracks.get(BASE).mute = False
                except ReferenceError:
                    pass
        scale = bpy.context.scene.render.fps / bpy.context.scene.render.fps_base / 20.
        for replay in replays:
            if replay.get('uuid', '') in skip:
                continue
            obj = actors._find_actor_by_uuid(replay.get('uuid', ''))
            if obj is not None:
                apply(obj, replay, scale)
        if state._import_incomplete:
            hinweis = " · UNVOLLSTAENDIG: " + "; ".join(
                "%s (%s)" % (name, grund) for name, grund in state._import_incomplete.values())
            state._actors_status += hinweis
            state._live_status += hinweis
        return result
    return run
