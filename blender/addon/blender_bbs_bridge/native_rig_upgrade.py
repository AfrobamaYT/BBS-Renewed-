"""Explicit, reversible promotion of a BOBJ actor after its native rig is supplied."""
import bpy

from . import actors, native_model


def promote(old, bobj_path):
    if bpy.context.mode != 'OBJECT' or old.type != 'ARMATURE':
        raise RuntimeError('Native promotion requires Object Mode and an armature')
    animation = old.animation_data
    if old.get('bbs_native_source') or old.constraints or (animation and
            (animation.drivers or animation.nla_tracks)):
        raise RuntimeError('Actor already has native rig, constraints, drivers or NLA')
    children = list(old.children_recursive)
    if any(obj.animation_data or obj.constraints for obj in children):
        raise RuntimeError('Actor children have local animation or constraints')
    selected = list(bpy.context.selected_objects)
    active = bpy.context.view_layer.objects.active
    name = old.name
    uid = old.get('bbs_uuid')
    if not uid:
        raise RuntimeError('Actor UUID is missing')
    collections = list(old.users_collection)
    if len(collections) != 1:
        raise RuntimeError('Actor must belong to one bridge collection')
    config = actors._read_bobj_config(bobj_path)
    root = native_model.import_for_bobj(bobj_path, config, collections[0], name)
    if root is None:
        raise RuntimeError('Native source is not declared')
    for key in old.keys():
        if not key.startswith('bbs_native_'):
            root[key] = old[key]
    root.matrix_world = old.matrix_world.copy()
    root.hide_viewport = old.hide_viewport
    root.hide_render = old.hide_render
    root.hide_set(old.hide_get())
    if animation and animation.action:
        destination = root.animation_data_create()
        destination.action = animation.action
        if animation.action_slot:
            destination.action_slot = animation.action_slot
    # Preserve old data and curves, but remove its live UUID to avoid duplicate actors.
    archive = bpy.data.collections.get('BBS_Before_Native_Rigs')
    if archive is None:
        archive = bpy.data.collections.new('BBS_Before_Native_Rigs')
        bpy.context.scene.collection.children.link(archive)
    archive.hide_viewport = True
    archive.hide_render = True
    for obj in [old] + children:
        archive.objects.link(obj)
        for collection in list(obj.users_collection):
            if collection != archive:
                collection.objects.unlink(obj)
    old['bbs_previous_uuid'] = uid
    del old['bbs_uuid']
    old.name = name + '__before_native'
    root.name = name
    render_mesh = next(obj for obj in root.children_recursive if obj.type == 'MESH'
                       and not obj.get('bbs_rig_widget') and obj.data.shape_keys)
    replacements = {old: root, **{child: render_mesh for child in children}}
    for obj in list(bpy.context.selected_objects):
        obj.select_set(False)
    for obj in selected:
        replacements.get(obj, obj).select_set(True)
    bpy.context.view_layer.objects.active = replacements.get(active, active)
    root.update_tag()
    bpy.context.view_layer.update()
    return root
