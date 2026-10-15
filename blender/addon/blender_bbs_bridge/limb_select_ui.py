"""Native viewport gizmo: hover the mesh, select its existing pose bone."""
import bpy
import gpu
from gpu_extras.batch import batch_for_shader
from . import limb_select as picking

PROP = 'bbsbridge_surface_select'
_status = ''


def selection(pose_bone):
    # Blender 5 stores pose selection on PoseBone, older versions on Bone.
    return pose_bone if hasattr(pose_bone, 'select') else pose_bone.bone


class BBSBRIDGE_OT_select_limb(bpy.types.Operator):
    bl_idname = 'bbsbridge.select_limb'
    bl_label = 'Körperteil auswählen'
    bl_options = {'UNDO'}
    bone: bpy.props.StringProperty()
    extend: bpy.props.BoolProperty(default=False)

    @classmethod
    def poll(cls, context):
        return context.mode == 'POSE' and picking.plain_rig(context.object) is not None

    @classmethod
    def description(cls, context, properties):
        return properties.bone + ' auswählen; Shift-Klick: Mehrfachauswahl. R/G/S und I bleiben Blender-Werkzeuge'

    def invoke(self, context, event):
        hit = picking.pick(context, (event.mouse_region_x, event.mouse_region_y))
        if hit is None:
            return {'CANCELLED'}
        self.bone, self.extend = hit[1], event.shift
        return self.execute(context)

    def execute(self, context):
        rig = picking.plain_rig(context.object)
        bone = rig.data.bones.get(self.bone) if rig is not None else None
        if bone is None or not picking.selectable(bone):
            return {'CANCELLED'}
        selected = not selection(rig.pose.bones[bone.name]).select if self.extend else True
        if not self.extend:
            for other in rig.pose.bones:
                selection(other).select = False
        selection(rig.pose.bones[bone.name]).select = selected
        rig.data.bones.active = bone if selected else next(
            (b.bone for b in rig.pose.bones if selection(b).select), None)
        for area in context.screen.areas:
            area.tag_redraw()
        return {'FINISHED'}


class BBSBRIDGE_GT_limb_surface(bpy.types.Gizmo):
    bl_idname = 'BBSBRIDGE_GT_limb_surface'

    def setup(self):
        self.use_draw_scale = False
        self.use_select_background = True  # Normal transform gizmos have priority.
        self.use_event_handle_all = False
        self._bone = ''
        self._rig = 0
        self._target = self.target_set_operator('bbsbridge.select_limb')

    def test_select(self, context, location):
        global _status
        try:
            hit = picking.pick(context, location)
            self._bone = hit[1] if hit is not None else ''
            self._rig = hit[0].session_uid if hit is not None else 0
            self._target.bone = self._bone
            _status = ''
            return 0 if hit is not None else -1
        except Exception as error:
            self._bone = ''
            _status = 'Oberflächenauswahl: ' + str(error)
            return -1

    def draw(self, context):
        global _status
        rig = picking.plain_rig(context.object)
        if rig is None:
            return
        active = rig.data.bones.active
        hover = self.is_highlight and self._rig == rig.session_uid
        name = self._bone if hover else active.name if active is not None and selection(rig.pose.bones[active.name]).select else ''
        if not name:
            return
        try:
            vertices = picking.surface(context, rig, name)
            if not vertices:
                return
            shader = gpu.shader.from_builtin('UNIFORM_COLOR')
            batch = batch_for_shader(shader, 'TRIS', {'pos': vertices})
            blend, depth, mask = gpu.state.blend_get(), gpu.state.depth_test_get(), gpu.state.depth_mask_get()
            try:
                gpu.state.blend_set('ALPHA')
                gpu.state.depth_test_set('LESS_EQUAL')
                gpu.state.depth_mask_set(False)
                shader.bind()
                shader.uniform_float('color', (0.05, 0.8, 1.0, 0.38) if hover else (1.0, 0.45, 0.05, 0.25))
                batch.draw(shader)
            finally:
                gpu.state.depth_mask_set(mask)
                gpu.state.depth_test_set(depth)
                gpu.state.blend_set(blend)
        except Exception as error:
            _status = 'Oberflächenanzeige: ' + str(error)


class BBSBRIDGE_GGT_limb_surface(bpy.types.GizmoGroup):
    bl_idname = 'BBSBRIDGE_GGT_limb_surface'
    bl_label = 'BBS Körperteil-Auswahl'
    bl_space_type = 'VIEW_3D'
    bl_region_type = 'WINDOW'
    bl_options = {'3D', 'PERSISTENT', 'DEPTH_3D'}

    @classmethod
    def poll(cls, context):
        return (context.mode == 'POSE' and getattr(context.scene, PROP, False)
                and picking.plain_rig(context.object) is not None)

    def setup(self, context):
        self.gizmos.new(BBSBRIDGE_GT_limb_surface.bl_idname)


class BBSBRIDGE_OT_surface_pose(bpy.types.Operator):
    bl_idname = 'bbsbridge.surface_pose'
    bl_label = 'Direkt am Modell animieren'
    bl_description = 'Ein einfaches BBS-Modell im Pose Mode direkt an seiner Oberfläche auswählen'

    @classmethod
    def poll(cls, context):
        return context.mode in {'OBJECT', 'POSE'} and picking.plain_rig(context.object) is not None

    def execute(self, context):
        rig = picking.plain_rig(context.object)
        if context.mode == 'OBJECT':
            for obj in context.selected_objects:
                obj.select_set(False)
            rig.select_set(True)
            context.view_layer.objects.active = rig
            bpy.ops.object.mode_set(mode='POSE')
        setattr(context.scene, PROP, True)
        if context.space_data.type == 'VIEW_3D':
            context.space_data.show_gizmo = True
        return {'FINISHED'}


class BBSBRIDGE_PT_surface_pose(bpy.types.Panel):
    bl_idname = 'BBSBRIDGE_PT_surface_pose'
    bl_label = 'Direkte Modell-Animation'
    bl_order = -10
    bl_space_type = 'VIEW_3D'
    bl_region_type = 'UI'
    bl_category = 'BBS Bridge'

    @classmethod
    def poll(cls, context):
        return picking.plain_rig(context.object) is not None

    def draw(self, context):
        layout = self.layout
        if context.mode == 'OBJECT':
            layout.operator('bbsbridge.surface_pose', icon='POSE_HLT')
            return
        layout.prop(context.scene, PROP, text='Körperteile am Modell auswählen')
        layout.prop(context.space_data.overlay, 'show_bones', text='Knochen anzeigen')
        layout.label(text='Klick: Körperteil auswählen')
        layout.label(text='Shift-Klick: Mehrfachauswahl')
        layout.label(text='R / G / S: bearbeiten · I: Keyframe')
        if not context.space_data.show_gizmo:
            layout.prop(context.space_data, 'show_gizmo', text='Gizmos aktivieren')
        if _status:
            box = layout.box()
            box.alert = True
            box.label(text=_status, icon='ERROR')


classes = (BBSBRIDGE_OT_select_limb, BBSBRIDGE_GT_limb_surface,
           BBSBRIDGE_GGT_limb_surface, BBSBRIDGE_OT_surface_pose, BBSBRIDGE_PT_surface_pose)


def register():
    setattr(bpy.types.Scene, PROP, bpy.props.BoolProperty(name='BBS Surface Selection', default=True))
    picking.register()
    for cls in classes:
        bpy.utils.register_class(cls)
    bpy.types.VIEW3D_MT_object.append(draw_menu)
    bpy.types.VIEW3D_MT_pose.append(draw_menu)


def unregister():
    bpy.types.VIEW3D_MT_object.remove(draw_menu)
    bpy.types.VIEW3D_MT_pose.remove(draw_menu)
    for cls in reversed(classes):
        bpy.utils.unregister_class(cls)
    picking.unregister()
    delattr(bpy.types.Scene, PROP)


def draw_menu(self, context):
    if picking.plain_rig(context.object) is not None:
        self.layout.operator('bbsbridge.surface_pose', icon='POSE_HLT')
