"""Keep the authored rig beside BOBJ so the bridge can append it without reconstruction."""
import json
import os

import bpy


def write(armature, directory):
    if armature.type != 'ARMATURE':
        raise RuntimeError('Native rig export requires an armature')
    widgets = {bone.custom_shape for bone in armature.pose.bones if bone.custom_shape}
    objects = {armature, *armature.children_recursive, *widgets}
    objects.update(obj for obj in bpy.data.objects if obj.type == 'MESH'
                   and any(mod.type == 'ARMATURE' and mod.object == armature
                           for mod in obj.modifiers))
    meshes = [obj for obj in objects if obj.type == 'MESH' and obj not in widgets
              and any(mod.type == 'ARMATURE' and mod.object == armature
                      for mod in obj.modifiers)]
    if not meshes:
        raise RuntimeError('Native rig export has no bound render mesh')
    path = os.path.join(directory, 'native_rig.blend')
    config_path = os.path.join(directory, 'config.json')
    config = {}
    if os.path.isfile(config_path):
        with open(config_path, encoding='utf-8') as source:
            config = json.load(source)
        if not isinstance(config, dict):
            raise RuntimeError('Model config must be an object')
    # A dedicated library collection excludes the metarig, archived sources and
    # studio cameras. Blender writes dependencies, including drivers and widgets.
    collection = bpy.data.collections.new('BBS_Native_Actor')
    try:
        for obj in objects:
            collection.objects.link(obj)
        bpy.data.libraries.write(path, {collection}, path_remap='RELATIVE',
                                 fake_user=True, compress=True)
        config['blender_source'] = {'file': 'native_rig.blend',
                                    'collection': collection.name, 'root': armature.name}
        with open(config_path, 'w', encoding='utf-8') as target:
            json.dump(config, target, ensure_ascii=False, indent=2)
    finally:
        bpy.data.collections.remove(collection)
    return {'file': path, 'objects': len(objects), 'meshes': len(meshes),
            'widgets': len(widgets)}
