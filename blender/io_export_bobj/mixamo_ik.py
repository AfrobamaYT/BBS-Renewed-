# BBS-Fork: Ein-Klick-IK fuer Mixamo-Skelette (Aufgabe #65).
#
# Mixamo-FBX kommt OHNE IK-Constraints an — das IK der Mixamo-Webvorschau reist
# nicht mit. Dieser Operator erkennt das Skelett an seiner festen Namensgebung
# (mixamorig:*, prefix-tolerant), setzt Ziel- und Pol-Knochen, haengt die vier
# Standard-Ketten (Arme/Beine, Laenge 2) als IK-Constraints an und bestimmt den
# Pol-Winkel automatisch so, dass die Ruhepose erhalten bleibt. Der BOBJ-Export
# schreibt die Ketten dann als arm_ik-Zeilen mit (export_bobj.write_ik_chains).

import math

import bpy
from mathutils import Vector


# (endknochen-suffix, kettenspitze, kettenwurzel, zielname, polname, pol-fallback)
LIMBS = (
    ('LeftHand',  'LeftForeArm', 'LeftArm',   'IK.hand.L', 'IK.elbow.L', Vector((0, 1, 0))),
    ('RightHand', 'RightForeArm', 'RightArm', 'IK.hand.R', 'IK.elbow.R', Vector((0, 1, 0))),
    ('LeftFoot',  'LeftLeg',     'LeftUpLeg', 'IK.foot.L', 'IK.knee.L',  Vector((0, -1, 0))),
    ('RightFoot', 'RightLeg',    'RightUpLeg', 'IK.foot.R', 'IK.knee.R', Vector((0, -1, 0))),
)


def find_bone(armature, suffix):
    """Knochen ueber sein Mixamo-Suffix finden — prefix-tolerant (mixamorig:,
    mixamorig1:, oder gar keins), damit umbenannte Re-Importe weiter treffen."""
    for bone in armature.data.bones:
        name = bone.name
        base = name.split(':', 1)[1] if ':' in name else name

        if base == suffix:
            return name

    return None


def bend_direction(root_head, mid_head, end_head, fallback):
    """Richtung, in die das Gelenk in Ruhe zeigt: Mittelgelenk minus Sehne.
    Ein durchgestrecktes Glied hat keine — dann greift der Fallback."""
    chord_mid = (root_head + end_head) * 0.5
    direction = mid_head - chord_mid

    if direction.length < 1e-5:
        return fallback.normalized()

    return direction.normalized()


def fit_pole_angle(context, pbone, constraint, chain_names):
    """Pol-Winkel-Kandidaten durchmessen und den nehmen, der die Ruhepose am
    wenigsten verdreht — der etablierte Weg, statt den Winkel zu raten."""
    armature = pbone.id_data
    best = (float('inf'), 0.0)

    for degrees in range(-180, 181, 15):
        constraint.pole_angle = math.radians(degrees)
        context.view_layer.update()

        deviation = 0.0

        for name in chain_names:
            pose_bone = armature.pose.bones[name]
            rest = pose_bone.bone.matrix_local
            diff = pose_bone.matrix - rest
            deviation += sum(abs(diff[i][j]) for i in range(3) for j in range(4))

        if deviation < best[0]:
            best = (deviation, constraint.pole_angle)

    constraint.pole_angle = best[1]
    context.view_layer.update()


class BBS_OT_mixamo_ik(bpy.types.Operator):
    """Setzt auf einem Mixamo-Skelett die vier Standard-IK-Ketten auf (BBS #65)"""

    bl_idname = "bbs.mixamo_ik"
    bl_label = "BBS: Mixamo-IK aufsetzen"
    bl_options = {'REGISTER', 'UNDO'}

    @classmethod
    def poll(cls, context):
        return context.object is not None and context.object.type == 'ARMATURE'

    def execute(self, context):
        armature = context.object
        data = armature.data
        planned = []

        for end_suffix, tip_suffix, root_suffix, target_name, pole_name, fallback in LIMBS:
            end = find_bone(armature, end_suffix)
            tip = find_bone(armature, tip_suffix)
            root = find_bone(armature, root_suffix)

            if end is None or tip is None or root is None:
                self.report({'WARNING'}, "Glied uebersprungen, Knochen fehlen: %s/%s/%s"
                            % (end_suffix, tip_suffix, root_suffix))
                continue

            planned.append((end, tip, root, target_name, pole_name, fallback))

        if not planned:
            self.report({'ERROR'}, "Kein Mixamo-Skelett: keine der vier Gliedmassen gefunden")
            return {'CANCELLED'}

        bpy.ops.object.mode_set(mode='EDIT')

        for end, tip, root, target_name, pole_name, fallback in planned:
            end_head = Vector(data.edit_bones[end].head)
            tip_head = Vector(data.edit_bones[tip].head)
            root_head = Vector(data.edit_bones[root].head)
            limb_length = (end_head - tip_head).length + (tip_head - root_head).length

            if target_name not in data.edit_bones:
                target = data.edit_bones.new(target_name)
                target.head = end_head
                target.tail = end_head + Vector((0, 0, max(limb_length * 0.15, 0.05)))

            if pole_name not in data.edit_bones:
                direction = bend_direction(root_head, tip_head, end_head, fallback)
                pole = data.edit_bones.new(pole_name)
                pole.head = tip_head + direction * limb_length * 0.5
                pole.tail = pole.head + Vector((0, 0, max(limb_length * 0.1, 0.05)))

        bpy.ops.object.mode_set(mode='POSE')
        created = 0

        for end, tip, root, target_name, pole_name, fallback in planned:
            pbone = armature.pose.bones[tip]

            if any(c.type == 'IK' for c in pbone.constraints):
                continue

            constraint = pbone.constraints.new('IK')
            constraint.target = armature
            constraint.subtarget = target_name
            constraint.chain_count = 2
            constraint.pole_target = armature
            constraint.pole_subtarget = pole_name
            # Blenders Voreinstellung ist True — teleskopierende Gliedmassen
            # will ein Humanoid nicht.
            constraint.use_stretch = False

            fit_pole_angle(context, pbone, constraint, [tip, root])
            created += 1

        bpy.ops.object.mode_set(mode='OBJECT')
        self.report({'INFO'}, "%d IK-Kette(n) aufgesetzt" % created)

        return {'FINISHED'}
