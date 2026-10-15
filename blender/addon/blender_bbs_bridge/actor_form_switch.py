"""BBS FormProperties `model` track -> constant Blender model visibility switches.

The authoritative actor root keeps all world-transform F-curves. Alternate model roots follow its
location/rotation with constraints, retain their own authored scale, and are selected by constant
hide_viewport/hide_render keys. This mirrors BBS' StringKeyframeFactory (hold previous value).
"""

import json

import bpy


OWNER = "bbs_form_switch_owner"
VALUE = "bbs_form_switch_value"
FINGERPRINT = "bbs_form_switch_fingerprint"
ORIGINAL_VIEW = "bbs_form_switch_original_hide_viewport"
ORIGINAL_RENDER = "bbs_form_switch_original_hide_render"


def _track(replay):
    properties = replay.get("properties") if isinstance(replay, dict) else None
    track = properties.get("formIdentity") if isinstance(properties, dict) else None
    if not isinstance(track, dict) or track.get("property") != "model":
        return None
    keys = [key for key in (track.get("keys") or [])
            if isinstance(key, dict) and str(key.get("value") or "")]
    keys.sort(key=lambda key: float(key.get("tick") or 0.0))
    return keys or None


def _variants(actor):
    uuid = str(actor.get("bbs_uuid") or "")
    if not uuid:
        return []
    return [obj for obj in bpy.data.objects
            if obj.parent is None and str(obj.get(OWNER) or "") == uuid]


def suspend_for_recording(actor):
    """Mute only bridge-owned visibility curves while the live stream is authoritative."""
    from . import actors
    token = []
    variants = _variants(actor)
    for root in [actor] + variants:
        for obj in _hierarchy(root):
            for collection in actors._object_fcurve_collections(obj):
                for curve in collection:
                    if curve.data_path in {"hide_viewport", "hide_render"}:
                        token.append((obj.session_uid, obj.animation_data.action.session_uid,
                                      curve.data_path, curve.array_index, curve.as_pointer(),
                                      bool(curve.mute)))
                        curve.mute = True
    for variant in variants:
        for obj in _hierarchy(variant):
            obj.hide_viewport = True
            obj.hide_render = True
    return token


def resume_after_recording(token):
    """Resolve live IDs again; removed FCurve RNA wrappers can segfault on access."""
    from . import actors
    objects = {obj.session_uid: obj for obj in bpy.data.objects}
    for entry in token or []:
        if len(entry) != 6:
            continue  # A pre-reload token may still contain unsafe RNA references.
        object_id, action_id, path, index, address, was_muted = entry
        obj = objects.get(object_id)
        animation = obj.animation_data if obj is not None else None
        if animation is None or animation.action is None or animation.action.session_uid != action_id:
            continue
        for collection in actors._object_fcurve_collections(obj):
            for curve in collection:
                if (curve.data_path == path and curve.array_index == index
                        and curve.as_pointer() == address):
                    curve.mute = was_muted


def remove_for_actor(actor):
    from . import actors
    for variant in list(_variants(actor)):
        actors._remove_actor_root(variant)


def _hierarchy(root):
    return [root] + list(root.children_recursive)


def _remove_visibility_curves(obj):
    from . import actors
    for collection in actors._object_fcurve_collections(obj):
        for curve in list(collection):
            if curve.data_path in {"hide_viewport", "hide_render"}:
                try:
                    collection.remove(curve)
                except (RuntimeError, ReferenceError):
                    pass


def _restore_original_visibility(root):
    for obj in _hierarchy(root):
        _remove_visibility_curves(obj)
        if ORIGINAL_VIEW in obj:
            obj.hide_viewport = bool(obj[ORIGINAL_VIEW])
        if ORIGINAL_RENDER in obj:
            obj.hide_render = bool(obj[ORIGINAL_RENDER])


def _constant_visibility_key(obj, frame, active):
    from . import actors
    if ORIGINAL_VIEW not in obj:
        obj[ORIGINAL_VIEW] = bool(obj.hide_viewport)
    if ORIGINAL_RENDER not in obj:
        obj[ORIGINAL_RENDER] = bool(obj.hide_render)
    for path in ("hide_viewport", "hide_render"):
        original = bool(obj[ORIGINAL_VIEW if path == "hide_viewport" else ORIGINAL_RENDER])
        setattr(obj, path, original if active else True)
        try:
            obj.keyframe_insert(data_path=path, frame=frame)
        except (RuntimeError, TypeError):
            continue
        for collection in actors._object_fcurve_collections(obj):
            for curve in collection:
                if curve.data_path != path:
                    continue
                for point in curve.keyframe_points:
                    if abs(float(point.co[0]) - float(frame)) < 1e-4:
                        point.interpolation = 'CONSTANT'


def _follow_actor(variant, actor):
    location = variant.constraints.new('COPY_LOCATION')
    location.name = "BBS Form Switch Location"
    location.target = actor
    location.owner_space = 'WORLD'
    location.target_space = 'WORLD'
    rotation = variant.constraints.new('COPY_ROTATION')
    rotation.name = "BBS Form Switch Rotation"
    rotation.target = actor
    rotation.owner_space = 'WORLD'
    rotation.target_space = 'WORLD'


def apply(actor, replay, tick_scale):
    """Build/update model variants. Returns the number of alternate model roots."""
    if actor is None or str(replay.get("formType") or "") != "bbs:model":
        return 0
    keys = _track(replay)
    if not keys:
        remove_for_actor(actor)
        _restore_original_visibility(actor)
        if FINGERPRINT in actor:
            del actor[FINGERPRINT]
        return 0

    fingerprint = "hierarchy-v2:" + json.dumps(keys, sort_keys=True, separators=(",", ":"))
    existing = _variants(actor)
    if str(actor.get(FINGERPRINT) or "") == fingerprint and existing:
        return len(existing)

    remove_for_actor(actor)
    _restore_original_visibility(actor)
    base_value = str(replay.get("formModel") or replay.get("form") or "")
    values = []
    for key in keys:
        value = str(key.get("value") or "")
        if value not in values:
            values.append(value)

    coll = actor.users_collection[0] if actor.users_collection else None
    if coll is None:
        return 0

    from . import actors
    roots = {base_value: actor}
    owner_uuid = str(actor.get("bbs_uuid") or "")
    for value in values:
        if value == base_value:
            continue
        pseudo = dict(replay)
        pseudo.update({"uuid": "__form_switch__" + owner_uuid + "__" + value,
                       "name": "%s [%s]" % (actor.name, value),
                       "formModel": value, "form": value})
        variant = actors._make_actor_root(pseudo, coll, True)
        if "bbs_uuid" in variant:
            del variant["bbs_uuid"]
        variant[OWNER] = owner_uuid
        variant[VALUE] = value
        _follow_actor(variant, actor)
        roots[value] = variant

        try:
            from . import model_pose
            model_pose.apply_actor_pose(variant, replay, tick_scale, transform_obj=actor)
            from . import actor_bones
            actor_bones.apply_bone_pose(variant, replay, tick_scale)
        except Exception as exc:  # noqa: BLE001 - one incompatible variant must not kill import
            print("[bbs-bridge] form-switch pose skipped for %s: %s" % (value, exc))

    current_frame = bpy.context.scene.frame_current
    for value, root in roots.items():
        for obj in _hierarchy(root):
            _constant_visibility_key(obj, 0.0, value == base_value)
    for key in keys:
        frame = float(key.get("tick") or 0.0) * tick_scale
        active = str(key.get("value") or "")
        for value, root in roots.items():
            for obj in _hierarchy(root):
                _constant_visibility_key(obj, frame, value == active)
    bpy.context.scene.frame_set(current_frame)
    actor[FINGERPRINT] = fingerprint
    print("[bbs-bridge] form-switch: %d keys / %d models on '%s'"
          % (len(keys), len(roots), actor.name))
    return max(0, len(roots) - 1)
