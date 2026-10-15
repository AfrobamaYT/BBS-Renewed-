"""Restore exported Blender widgets and their property UI without executing asset scripts."""
import json
from pathlib import Path
import bpy


def active_rig(context):
    obj = context.object
    while obj is not None:
        if obj.type == 'ARMATURE' and obj.get('bbs_native_source'):
            return obj
        obj = obj.parent
    return None


def apply(root, bobj_path):
    path = Path(bobj_path).with_name('rig_roh.json')
    if not path.is_file():
        return 0
    data = json.loads(path.read_text())
    shapes = data.get('shapes', {})
    count = 0
    for spec in data.get('ctrls', []):
        bone = root.pose.bones.get(spec['name'])
        geometry = shapes.get(spec.get('shape'))
        if bone is None or geometry is None:
            continue
        if bone.custom_shape is None:
            mesh = bpy.data.meshes.new(root.name + '__widget__' + spec['name'])
            mesh.from_pydata(geometry['verts'], geometry['edges'], [])
            widget = bpy.data.objects.new(mesh.name, mesh)
            root.users_collection[0].objects.link(widget)
            widget.parent = root
            widget['bbs_rig_widget'] = True
            widget['bbs_native_role'] = 'widget'
            widget.hide_render = True
            widget.hide_viewport = True
            bone.custom_shape = widget
            bone.custom_shape_translation = spec.get('cs_t', (0, 0, 0))
            bone.custom_shape_rotation_euler = spec.get('cs_r', (0, 0, 0))
            bone.custom_shape_scale_xyz = spec.get('cs_s', (1, 1, 1))
            bone.use_custom_shape_bone_size = spec.get('bone_size', True)
            bone.custom_shape_transform = root.pose.bones.get(spec.get('cs_at') or '')
            bone.custom_shape_wire_width = spec.get('wire_width', 1.)
            color = spec.get('farbe', {})
            bone.color.palette = 'CUSTOM'
            for field, key in (('normal', 'normal'), ('select', 'select'), ('active', 'aktiv')):
                value = color.get(key)
                if value:
                    setattr(bone.color.custom, field, tuple(int(value[i:i+2], 16)/255 for i in (0,2,4)))
        count += 1
    if not root.get('bbs_control_ui'):
        for name, spec in data.get('collections', {}).items():
            collection = root.data.collections.get(name)
            if collection:
                collection.is_visible = bool(spec.get('an', False))
    root.show_in_front = True
    root['bbs_control_ui'] = json.dumps(data.get('eigenschaften', {}))
    root['bbs_control_layers'] = json.dumps(list(data.get('collections', {})))
    return count


class BBSBRIDGE_OT_select_control_rig(bpy.types.Operator):
    bl_idname = 'bbsbridge.select_control_rig'
    bl_label = 'Control-Rig bearbeiten'

    def execute(self, context):
        root = active_rig(context)
        if root is None:
            return {'CANCELLED'}
        if context.object and context.object.mode != 'OBJECT':
            bpy.ops.object.mode_set(mode='OBJECT')
        bpy.ops.object.select_all(action='DESELECT')
        root.select_set(True)
        context.view_layer.objects.active = root
        bpy.ops.object.mode_set(mode='POSE')
        return {'FINISHED'}


class BBSBRIDGE_PT_control_rig(bpy.types.Panel):
    bl_label = 'Control-Rig'
    bl_idname = 'BBSBRIDGE_PT_control_rig'
    bl_space_type = 'VIEW_3D'
    bl_region_type = 'UI'
    bl_category = 'BBS Bridge'

    @classmethod
    def poll(cls, context):
        return active_rig(context) is not None

    def draw(self, context):
        root = active_rig(context)
        layout = self.layout
        if context.object != root or root.mode != 'POSE':
            layout.operator('bbsbridge.select_control_rig', icon='ARMATURE_DATA')
        layers = layout.column(align=True)
        layers.label(text='Control-Gruppen')
        grid = layers.grid_flow(columns=2, even_columns=True, align=True)
        for name in json.loads(root.get('bbs_control_layers', '[]')):
            collection = root.data.collections.get(name)
            if collection:
                grid.prop(collection, 'is_visible', text=name, toggle=True)
        active = root.data.bones.active
        if root.mode != 'POSE' or active is None:
            layout.label(text='Im Pose Mode ein Control auswaehlen.')
            return
        box = layout.column(align=True)
        for owner, specs in json.loads(root.get('bbs_control_ui', '{}')).items():
            bone = root.pose.bones.get(owner)
            if bone is None:
                continue
            for key, spec in specs.items():
                visible = owner == active.name or any(active.name in group for group in spec.get('sichtbar_wenn', []))
                if visible and key in bone and isinstance(bone[key], (float, int)):
                    box.prop(bone, '["'+bpy.utils.escape_identifier(key)+'"]', text=spec.get('titel', key))


classes = (BBSBRIDGE_OT_select_control_rig, BBSBRIDGE_PT_control_rig)


def register():
    for cls in classes:
        bpy.utils.register_class(cls)


def unregister():
    for cls in reversed(classes):
        bpy.utils.unregister_class(cls)
