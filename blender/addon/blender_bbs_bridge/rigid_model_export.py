"""Adapt a rigid imported hierarchy to the existing BOBJ/native-library exporters."""
import importlib.util
import pathlib
import re

import bpy
from mathutils import Vector


def objects_for(root):
    if not root.visible_get():
        raise RuntimeError('Select a visible model root')
    if root.parent is not None:
        raise RuntimeError('Select a model root, not a nested object')
    if root.matrix_world.to_quaternion().angle > .00001 or max(abs(v-1.) for v in root.matrix_world.to_scale()) > .00001:
        raise RuntimeError('Apply root rotation/scale before rigid model import')
    if root.matrix_world.translation.length > .00001:
        raise RuntimeError('Rigid import currently requires a model at the origin')
    objects = [root] + list(root.children_recursive)
    if any(obj.type not in {'EMPTY', 'MESH'} for obj in objects):
        raise RuntimeError('Rigid model import supports Empty/Mesh hierarchies; use Control-Rig export for skins')
    for obj in objects:
        if obj.animation_data or obj.constraints or obj.modifiers:
            raise RuntimeError('Animated/constrained imported objects need Control-Rig export: '+obj.name)
        matrix = root.matrix_world.inverted() @ obj.matrix_world
        if max(abs(value-1.) for value in matrix.to_scale()) > .00001:
            raise RuntimeError('Non-unit part scale needs baking before rigid import: '+obj.name)
    meshes = [obj for obj in objects if obj.type == 'MESH']
    if not meshes:
        raise RuntimeError('Imported model has no mesh')
    if any(obj.data.shape_keys for obj in meshes):
        raise RuntimeError('Shape-key meshes need Control-Rig export')
    if any(len(obj.data.materials) != 1 for obj in meshes):
        raise RuntimeError('Rigid model import currently requires one material per mesh')
    return objects, meshes


def build(root, name):
    """Create independent skin/rig data; never rewrite the imported objects or mesh data."""
    objects, meshes = objects_for(root)
    owners = [obj for obj in objects if obj.type == 'EMPTY'] or [root]
    names = {}
    for obj in owners:
        base = re.sub(r'[^A-Za-z0-9_.-]', '_', obj.name) or 'part'
        name_for_bone = base
        while name_for_bone in names.values():
            name_for_bone += '_'
        names[obj] = name_for_bone
    collection = bpy.data.collections.new('BBS_Model_Import_Stage')
    bpy.context.scene.collection.children.link(collection)
    data = bpy.data.armatures.new(name)
    rig = bpy.data.objects.new(name, data)
    collection.objects.link(rig)
    created = [rig]
    selected = list(bpy.context.selected_objects)
    active = bpy.context.view_layer.objects.active
    inverse = root.matrix_world.inverted()
    try:
        bpy.ops.object.select_all(action='DESELECT')
        rig.select_set(True)
        bpy.context.view_layer.objects.active = rig
        bpy.ops.object.mode_set(mode='EDIT')
        for obj in owners:
            bone = data.edit_bones.new(names[obj])
            matrix = inverse @ obj.matrix_world
            bone.head = matrix.translation
            bone.tail = matrix @ Vector((0., .15, 0.))
            bone.matrix = matrix
        for obj in owners:
            parent = obj.parent
            while parent is not None and parent not in names:
                parent = parent.parent
            if parent is not None:
                data.edit_bones[names[obj]].parent = data.edit_bones[names[parent]]
        bpy.ops.object.mode_set(mode='OBJECT')
        for source in meshes:
            owner = source if source in names else source.parent
            while owner is not None and owner not in names:
                owner = owner.parent
            if owner is None:
                owner = root
            if owner not in names:
                raise RuntimeError('Mesh has no rigid part: '+source.name)
            mesh = bpy.data.objects.new(source.name, source.data.copy())
            created.append(mesh)
            collection.objects.link(mesh)
            mesh.data.transform(inverse @ source.matrix_world)
            mesh.vertex_groups.new(name=names[owner]).add(list(range(len(mesh.data.vertices))), 1., 'REPLACE')
            mesh.parent = rig
            modifier = mesh.modifiers.new('Armature', 'ARMATURE')
            modifier.object = rig
        for bone in rig.pose.bones:
            bone.rotation_mode = 'XYZ'
        rig.show_in_front = True
        return rig, collection, objects
    except Exception:
        if bpy.context.mode != 'OBJECT':
            bpy.ops.object.mode_set(mode='OBJECT')
        discard(rig, collection)
        raise
    finally:
        for obj in list(bpy.context.selected_objects):
            obj.select_set(False)
        for obj in selected:
            obj.select_set(True)
        bpy.context.view_layer.objects.active = active


def discard(rig, collection):
    blocks = [obj.data for obj in collection.objects if obj.data is not None]
    for obj in list(collection.objects):
        bpy.data.objects.remove(obj, do_unlink=True)
    bpy.data.collections.remove(collection)
    bpy.data.batch_remove(ids=[block for block in blocks if block.users == 0])


def write(rig, directory):
    from io_export_bobj import native_rig_export, export_bbs_rig
    directory = pathlib.Path(directory)
    selected = list(bpy.context.selected_objects)
    active = bpy.context.view_layer.objects.active
    meshes = [obj for obj in rig.children if obj.type == 'MESH']
    try:
        bpy.ops.object.select_all(action='DESELECT')
        for obj in [rig]+meshes:
            obj.select_set(True)
        bpy.context.view_layer.objects.active = rig
        with bpy.context.temp_override(selected_objects=[rig]+meshes, active_object=rig, object=rig):
            result = bpy.ops.export_scene.bobj(filepath=str(directory/'model.bobj'), use_selection=True)
        if result != {'FINISHED'}:
            raise RuntimeError('BOBJ export failed: '+str(result))
        path = export_bbs_rig._werkzeug('textur-blender.py')
        spec = importlib.util.spec_from_file_location('bbs_import_texture_helper', path)
        module = importlib.util.module_from_spec(spec)
        # The helper's standalone entry point expects figur when its environment is set.
        module.figur = meshes
        spec.loader.exec_module(module)
        textures = module.texturen(meshes, str(directory))
        if textures['ohne_textur']:
            raise RuntimeError('Missing diffuse texture: '+str(textures['ohne_textur']))
        native_rig_export.write(rig, str(directory))
    finally:
        for obj in list(bpy.context.selected_objects):
            obj.select_set(False)
        for obj in selected:
            obj.select_set(True)
        bpy.context.view_layer.objects.active = active
