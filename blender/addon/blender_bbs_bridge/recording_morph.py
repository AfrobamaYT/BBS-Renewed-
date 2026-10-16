"""Disposable visible form replacement for the live replay-recording subject.

A form/model change is static subject data, not a pose sample. Keep the authoritative imported
actor intact and swap a temporary preview root in front of it; stop/disconnect removes the preview.
"""

import uuid

import bpy

from . import state


COLLECTION = "BBS_LiveRecordingMorph"
TARGET_PREFIX = "__bbs_live_morph__:"

_base_form = ("", "", "")
_preview_form = ("", "", "")
_preview_obj = None
_preview_target_uuid = ""
_session = ""
_suspended_visibility = []


def _fingerprint(form):
    form = form if isinstance(form, dict) else {}
    return (str(form.get("type") or ""), str(form.get("model") or ""),
            str(form.get("mob") or ""))


def _replay_fingerprint(replay):
    replay = replay if isinstance(replay, dict) else {}
    return (str(replay.get("formType") or ""), str(replay.get("formModel") or ""),
            str(replay.get("formMob") or ""))


def _alive(obj):
    try:
        return obj is not None and bpy.data.objects.get(obj.name) is obj
    except ReferenceError:
        return False


def _hierarchy(obj):
    return [] if not _alive(obj) else [obj] + list(obj.children_recursive)


def _visible(obj, show):
    for candidate in _hierarchy(obj):
        candidate.hide_viewport = not show
        candidate.hide_render = not show


def _collection():
    coll = bpy.data.collections.get(COLLECTION)
    if coll is None:
        coll = bpy.data.collections.new(COLLECTION)
        bpy.context.scene.collection.children.link(coll)
    return coll


def _remove_object(obj):
    if not _alive(obj):
        return
    from . import actors
    actors._remove_actor_root(obj)


def _remove_preview():
    global _preview_obj, _preview_target_uuid, _preview_form
    coll = bpy.data.collections.get(COLLECTION)
    if not _alive(_preview_obj) and not _preview_target_uuid and coll is None:
        _preview_obj = None
        _preview_form = ("", "", "")
        return
    _remove_object(_preview_obj)
    for obj in list(bpy.data.objects):
        try:
            target = str(obj.get("bbs_uuid") or "")
            owned = bool(obj.get("bbs_live_morph_preview"))
        except ReferenceError:
            continue
        if obj.parent is None and (owned or target.startswith(TARGET_PREFIX)):
            _remove_object(obj)
    coll = bpy.data.collections.get(COLLECTION)
    if coll is not None and len(coll.objects) == 0:
        bpy.data.collections.remove(coll)
    _preview_obj = None
    _preview_target_uuid = ""
    _preview_form = ("", "", "")


def begin(base_obj, replay, current_form, session):
    global _base_form, _session, _suspended_visibility
    _remove_preview()
    from . import actor_form_switch
    _suspended_visibility = actor_form_switch.suspend_for_recording(base_obj)
    _base_form = _replay_fingerprint(replay)
    _session = str(session or "")
    return display(base_obj, current_form)


def _build_preview(form):
    global _preview_obj, _preview_target_uuid
    from . import actors

    form_type, model, mob = _fingerprint(form)
    target = TARGET_PREFIX + (_session or uuid.uuid4().hex) + ":" + uuid.uuid4().hex[:8]
    replay = {
        "uuid": target,
        "name": "Live Morph Preview",
        "formType": form_type,
        "formModel": model,
        "formMob": mob,
        "enabled": True,
        "isGroup": False,
        "group": "",
    }
    want_models = getattr(bpy.context.scene, "bbsbridge_import_models", True)
    obj = actors._make_actor_root(replay, _collection(), want_models)
    obj["bbs_live_morph_preview"] = True
    obj["bbs_live_morph_form_type"] = form_type
    obj["bbs_live_morph_form_model"] = model
    obj["bbs_live_morph_form_mob"] = mob
    _preview_obj = obj

    if form_type == "bbs:mob" and obj.type == 'EMPTY' and mob:
        if "recording_morph_preview_v1" in state.peer_capabilities:
            from . import entity_import
            if entity_import.request_entity_export(mob_id=mob, target_uuid=target):
                _preview_target_uuid = target
        return obj

    try:
        del obj["bbs_uuid"]
    except KeyError:
        pass
    return obj


def display(base_obj, form):
    """Return the currently visible subject root for this frame."""
    global _preview_form
    fingerprint = _fingerprint(form)
    if not any(fingerprint) or fingerprint == _base_form:
        _remove_preview()
        _visible(base_obj, True)
        return base_obj

    if fingerprint != _preview_form or not _alive(_preview_obj):
        _remove_preview()
        _preview_form = fingerprint
        _build_preview(form)

    _visible(base_obj, False)
    _visible(_preview_obj, True)
    return _preview_obj if _alive(_preview_obj) else base_obj


def adopt_entity_preview(obj, target_uuid):
    """Called by entity_import when an async MobForm model replaces its placeholder."""
    global _preview_obj, _preview_target_uuid
    if not str(target_uuid).startswith(TARGET_PREFIX):
        return False
    if target_uuid != _preview_target_uuid or not _session:
        _remove_object(obj)
        return True
    _preview_obj = obj
    _preview_target_uuid = ""
    obj["bbs_live_morph_preview"] = True
    try:
        del obj["bbs_uuid"]
    except KeyError:
        pass
    _visible(obj, True)
    return True


def current_preview():
    return _preview_obj if _alive(_preview_obj) else None


def end(base_obj=None):
    global _base_form, _session, _suspended_visibility
    _remove_preview()
    from . import actor_form_switch
    actor_form_switch.resume_after_recording(_suspended_visibility)
    _suspended_visibility = []
    if base_obj is not None:
        _visible(base_obj, True)
    _base_form = ("", "", "")
    _session = ""
