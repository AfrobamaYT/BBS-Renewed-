# ##### BEGIN GPL LICENSE BLOCK #####
#
#  This program is free software; you can redistribute it and/or
#  modify it under the terms of the GNU General Public License
#  as published by the Free Software Foundation; either version 2
#  of the License, or (at your option) any later version.
#
#  This program is distributed in the hope that it will be useful,
#  but WITHOUT ANY WARRANTY; without even the implied warranty of
#  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
#  GNU General Public License for more details.
#
#  You should have received a copy of the GNU General Public License
#  along with this program; if not, write to the Free Software Foundation,
#  Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
#
# ##### END GPL LICENSE BLOCK #####

# <pep8 compliant>

# ------------------------------------------------------------------ #
#  Ported to Blender 4.x / 5.0 by Claude (Anthropic)
#
#  Key changes vs. the original (Blender 2.8 – 3.4) version:
#
#  __init__.py
#    • bl_info "blender" min version bumped to (4, 0, 0)
#    • path_mode declared as an annotated property (path_mode: …)
#      instead of a bare assignment so Blender 4+ registers it correctly
#
#  export_bobj.py (this file)
#    • ProgressReport / ProgressReportSubstep removed – those helpers
#      were dropped in Blender 4.0.  A lightweight no-op shim replaces
#      them so the rest of the call-sites are unchanged.
#    • me.calc_normals_split() removed – normals are now always stored
#      per-loop in Blender 4+ and must be read via loops[i].normal
#      directly (after calling mesh.calc_loop_triangles() if needed).
#      The normals export section now skips the old call entirely.
#    • mesh.uv_layers.active.data[:] API is unchanged but the active
#      check is made robust (None-guard added).
#    • ob_for_convert.to_mesh_clear() is still present and valid in
#      Blender 5.0 – no change needed there.
#    • Bone rotation-mode note: all pose bones MUST use XYZ Euler
#      rotation mode for the animation export to be correct.  The
#      exporter now emits a warning in the console if it detects bones
#      with a different rotation mode.
# ------------------------------------------------------------------ #

import math
import os

import bmesh
import bpy
import mathutils


# ------------------------------------------------------------------
# Minimal ProgressReport shim (the real one was removed in Blender 4)
# ------------------------------------------------------------------
class _FakeProgress:
    """No-op context manager / progress tracker so existing call sites work."""

    def __init__(self, *args, **kwargs):
        pass

    def __enter__(self):
        return self

    def __exit__(self, *_):
        pass

    def enter_substeps(self, n, *args):
        pass

    def leave_substeps(self, *args):
        pass

    def step(self, *args):
        pass


class _FakeSubProgress(_FakeProgress):
    def __init__(self, parent, steps, *args):
        pass


# Aliases so the rest of the code keeps the old names
ProgressReport = _FakeProgress
ProgressReportSubstep = _FakeSubProgress


# ------------------------------------------------------------------
# Helpers
# ------------------------------------------------------------------

def name_compat(name):
    """Replace spaces in *name* so it is a single token in the file."""
    return 'None' if name is None else name.replace(' ', '_')


def mesh_triangulate(me):
    """Triangulate all polygons in *me* in-place."""
    bm = bmesh.new()
    bm.from_mesh(me)
    bmesh.ops.triangulate(bm, faces=bm.faces)
    bm.to_mesh(me)
    bm.free()


# ------------------------------------------------------------------
# Armature writer
# ------------------------------------------------------------------

def write_armature(fw, armature, global_matrix):
    fw('# Armature data\n')
    fw('arm_name %s\n' % armature.data.name)

    if (armature.animation_data is not None
            and armature.animation_data.action is not None):
        fw('arm_action %s\n' % name_compat(armature.animation_data.action.name))

    for bone in armature.data.bones:
        parent = name_compat(bone.parent.name) if bone.parent is not None else ''

        tail = bone.matrix_local.copy()
        tail.translation = bone.tail_local
        tail = global_matrix @ armature.matrix_world @ tail

        vx, vy, vz = tail.translation[:]

        mat = global_matrix @ armature.matrix_world @ bone.matrix_local

        m = ""
        for xx in range(4):
            for yy in range(4):
                m += str(mat.row[xx][yy]) + ' '

        string = 'arm_bone %s %s ' % (name_compat(bone.name), parent)
        string += '%f %f %f ' % (vx, vy, vz)
        string += m + '\n'

        fw(string)

    write_ik_chains(fw, armature)


def write_ik_chains(fw, armature):
    """BBS-Fork: IK-Constraints als ``arm_ik``-Zeilen mitschreiben.

    Nur Ketten, deren Ziel (und Pol-Ziel) ein Knochen DERSELBEN Armature ist,
    reisen mit — nur solche Knochen existieren im BOBJ. Winkel in Grad, wie
    BBS' ModelIKConfig sie erwartet. Format:
    ``arm_ik <tip> <target> <chainLen> <poleTarget|-> <poleAngleDeg> <weight> <tipRot> <stretch>``
    """
    for pbone in armature.pose.bones:
        for con in pbone.constraints:
            if con.type != 'IK' or getattr(con, 'mute', False) or con.influence <= 0.0:
                continue

            if con.target is None or con.target != armature or not con.subtarget:
                print("[BOBJ] arm_ik uebersprungen: '%s' zielt nicht auf einen Knochen "
                      "dieser Armature (Ziel reist im BOBJ nicht mit)." % pbone.name)
                continue

            pole = ''
            pole_angle = 0.0

            if (con.pole_target is not None and con.pole_target == armature
                    and con.pole_subtarget):
                pole = con.pole_subtarget
                pole_angle = math.degrees(con.pole_angle)

            # Blender-IK zielt mit dem TAIL des Constraint-Halters (chain_count zaehlt
            # ab ihm); BBS' Solver zielt mit dem PIVOT des tip und nimmt dessen eigene
            # Winkel aus der Loesung. 1:1 uebersetzt loeste nur die Schulter und zielte
            # auf den Ellbogen (gemessen 2026-08-14: 2 Grad statt Beugung). Darum: tip
            # eine Stufe zum EINDEUTIGEN Kind (Hand/Fuss), Kette um 1 laenger.
            tip = pbone
            kette = con.chain_count

            if len(pbone.children) == 1:
                tip = pbone.children[0]
                kette += 1

            fw('arm_ik %s %s %d %s %f %f %d %d\n' % (
                name_compat(tip.name),
                name_compat(con.subtarget),
                kette,
                name_compat(pole) if pole else '-',
                pole_angle,
                con.influence,
                1 if getattr(con, 'use_rotation', False) else 0,
                1 if getattr(con, 'use_stretch', False) else 0))


# ------------------------------------------------------------------
# Animation / action writers
# ------------------------------------------------------------------

def _warn_rotation_modes(armature):
    """Print a console warning for any pose bone not in XYZ Euler mode."""
    if armature is None:
        return
    for pbone in armature.pose.bones:
        if pbone.rotation_mode != 'XYZ':
            print(
                "[BOBJ] WARNING: pose bone '%s' uses rotation_mode '%s'. "
                "Change it to XYZ Euler or animations will be incorrect!"
                % (pbone.name, pbone.rotation_mode)
            )


def write_actions(context, fw):
    fw('# Animation data\n')
    for key, action in bpy.data.actions.items():
        write_action(context, fw, key, action)


def get_fcurves(action):
    """Return all FCurves from an action, compatible with Blender 2.8 to 5.0.

    Blender 5.0 introduced a layered action system where F-curves live inside
    layers -> strips -> channelbags instead of directly on action.fcurves.
    """
    # Blender 5.0+ layered action API
    if hasattr(action, 'layers'):
        fcurves = []
        for layer in action.layers:
            for strip in layer.strips:
                if hasattr(strip, 'channelbags'):
                    for cb in strip.channelbags:
                        fcurves.extend(cb.fcurves)
                elif hasattr(strip, 'channelbag'):
                    try:
                        fcurves.extend(strip.channelbag.fcurves)
                    except Exception:
                        pass
        return fcurves
    # Legacy API (Blender 2.8 - 4.x)
    if hasattr(action, 'fcurves'):
        return list(action.fcurves)
    return []


def write_action(context, fw, name, action):
    groups = {}

    def get_or_create(key):
        if key not in groups:
            groups[key] = []
        return groups[key]

    for fc in get_fcurves(action):
        if fc.data_path.startswith('pose.bones["'):
            key = fc.data_path[12:]
            key = key[:key.index('"')]
            get_or_create(key).append(fc)

    if not groups:
        return

    fw('an %s\n' % name)

    for key, group in groups.items():
        fw('ao %s\n' % name_compat(key))

        for fcurve in group:
            data_path = fcurve.data_path
            index = fcurve.array_index
            length = len(fcurve.keyframe_points)
            dvalue = 0

            if data_path.endswith('location'):
                data_path = 'location'
            elif data_path.endswith('rotation_euler'):
                data_path = 'rotation'
            elif data_path.endswith('scale'):
                data_path = 'scale'
                dvalue = 1
            else:
                continue

            if length <= 0:
                continue

            # Skip curves that are entirely at their default value
            all_default = all(kf.co[1] == dvalue for kf in fcurve.keyframe_points)
            if all_default:
                continue

            fw('ag %s %d\n' % (data_path, index))

            last_frame = None
            for keyframe in fcurve.keyframe_points:
                if last_frame == keyframe.co[0]:
                    continue
                fw(stringify_keyframe(context, keyframe) + '\n')
                last_frame = keyframe.co[0]


def stringify_keyframe(context, keyframe):
    fps = context.scene.render.fps
    f = 20 / fps

    interp = keyframe.interpolation
    result = 'kf %d %f %s' % (keyframe.co[0] * f, keyframe.co[1], interp)
    result += ' %f %f %f %f' % (
        keyframe.handle_left[0] * f,
        keyframe.handle_left[1],
        keyframe.handle_right[0] * f,
        keyframe.handle_right[1],
    )
    return result


# ------------------------------------------------------------------
# Public entry point
# ------------------------------------------------------------------

def save(context,
         filepath,
         *,
         keep_vertex_order=False,
         use_selection=True,
         include_keyframes=True,
         include_geometry=True,
         global_matrix=None):

    _write(
        context, filepath,
        EXPORT_KEEP_VERT_ORDER=keep_vertex_order,
        EXPORT_SEL_ONLY=use_selection,
        EXPORT_KEYFRAMES=include_keyframes,
        EXPORT_GEOMETRY=include_geometry,
        EXPORT_GLOBAL_MATRIX=global_matrix,
    )
    return {'FINISHED'}


# ------------------------------------------------------------------
# Core writer
# ------------------------------------------------------------------

def _write(context, filepath,
           EXPORT_KEEP_VERT_ORDER,
           EXPORT_SEL_ONLY,
           EXPORT_KEYFRAMES,
           EXPORT_GEOMETRY,
           EXPORT_GLOBAL_MATRIX):

    with ProgressReport(context.window_manager) as progress:
        scene = context.scene
        depsgraph = context.evaluated_depsgraph_get()

        # Exit edit mode so current object states are exported properly
        if bpy.ops.object.mode_set.poll():
            bpy.ops.object.mode_set(mode='OBJECT')

        objects = context.selected_objects if EXPORT_SEL_ONLY else scene.objects

        progress.enter_substeps(1)
        write_file(
            context, filepath, objects, depsgraph, scene,
            EXPORT_KEEP_VERT_ORDER,
            EXPORT_GLOBAL_MATRIX,
            EXPORT_KEYFRAMES,
            EXPORT_GEOMETRY,
            progress,
        )
        progress.leave_substeps()


def write_shape_keys(fw, ob, totverts):
    """Shape Keys als `sk`/`skv`-Bloecke — BBS mischt sie vor dem Skinning.

    ⛔ Die Lagen kommen vom ORIGINAL-Mesh, nicht von der ausgewerteten Kopie: auf der
    sind die Keys schon eingerechnet, dort stuende ueberall die Basis.

    ⛔ Nur, was sich WIRKLICH bewegt. Ein Key fasst selten das ganze Netz an; alle
    Vertices zu schreiben blaeht die Datei um ein Vielfaches. Der Index zaehlt ab 1 und
    ist der GLOBALE (totverts + lokaler Index), genau wie in den `f`-Zeilen.
    """
    keys = getattr(ob.data, "shape_keys", None)

    if keys is None or len(keys.key_blocks) < 2:
        return

    basis = keys.key_blocks[0].data

    for block in keys.key_blocks[1:]:
        if len(block.data) != len(basis):
            continue

        fw('sk %s\n' % name_compat(block.name))

        for i, punkt in enumerate(block.data):
            b = basis[i].co

            if (punkt.co - b).length > 1e-6:
                fw('skv %d %.6f %.6f %.6f\n' % (totverts + i, punkt.co[0], punkt.co[1], punkt.co[2]))


def write_file(context, filepath, objects, depsgraph, scene,
               EXPORT_KEEP_VERT_ORDER=False,
               EXPORT_GLOBAL_MATRIX=None,
               EXPORT_KEYFRAMES=True,
               EXPORT_GEOMETRY=True,
               progress=None):

    if progress is None:
        progress = ProgressReport()

    if EXPORT_GLOBAL_MATRIX is None:
        EXPORT_GLOBAL_MATRIX = mathutils.Matrix()

    def veckey3d(v):
        return round(v.x, 4), round(v.y, 4), round(v.z, 4)

    def veckey2d(v):
        return round(v[0], 5), round(v[1], 5)

    with ProgressReportSubstep(progress, 2,
                                "BOBJ Export path: %r" % filepath,
                                "BOBJ Export Finished") as subprogress1:

        with open(filepath, "w", encoding="utf8", newline="\n") as f:
            fw = f.write

            # Header
            fw('# Blender v%s Blockbuster OBJ File: %r\n'
               % (bpy.app.version_string, os.path.basename(bpy.data.filepath)))
            fw('# www.blender.org\n')

            totverts = totuvco = totno = 1
            face_vert_index = 1

            if EXPORT_GEOMETRY:
                subprogress1.enter_substeps(len(objects))

                for i, ob_main in enumerate(objects):
                    # Skip dupli children
                    if ob_main.parent and ob_main.parent.instance_type in {'VERTS', 'FACES'}:
                        subprogress1.step("Ignoring %s, dupli child..." % ob_main.name)
                        continue

                    obs = [(ob_main, ob_main.matrix_world)]
                    if ob_main.is_instancer:
                        obs += [
                            (dup.instance_object.original, dup.matrix_world.copy())
                            for dup in depsgraph.object_instances
                            if dup.parent and dup.parent.original == ob_main
                        ]

                    subprogress1.enter_substeps(len(obs))

                    for ob, ob_mat in obs:
                        with ProgressReportSubstep(subprogress1, 6) as subprogress2:
                            uv_unique_count = no_unique_count = 0

                            # ---- Armature ----
                            if ob.type == 'ARMATURE':
                                write_armature(fw, ob, EXPORT_GLOBAL_MATRIX)
                                # Warn about non-XYZ rotation modes
                                _warn_rotation_modes(ob)

                            ob_for_convert = ob.original

                            try:
                                me = ob_for_convert.to_mesh()
                            except RuntimeError:
                                me = None

                            if me is None:
                                continue

                            # Apply transforms and triangulate
                            me.transform(EXPORT_GLOBAL_MATRIX @ ob_mat)
                            mesh_triangulate(me)

                            # ---- UV setup ----
                            faceuv = (len(me.uv_layers) > 0
                                      and me.uv_layers.active is not None)
                            if faceuv:
                                uv_layer = me.uv_layers.active.data[:]

                            me_verts = me.vertices[:]

                            face_index_pairs = [
                                (face, index)
                                for index, face in enumerate(me.polygons)
                            ]

                            if not (len(face_index_pairs) + len(me.vertices)):
                                ob_for_convert.to_mesh_clear()
                                continue

                            # ----------------------------------------------------------------
                            # Normals – Blender 4 / 5
                            #
                            # calc_normals_split() was removed in Blender 4.1.
                            # Loop normals are now always available after the mesh has been
                            # evaluated; just call mesh.calc_normals_split() only on older
                            # builds, or skip it entirely on 4+.
                            # We detect the Blender version at runtime to stay compatible.
                            # ----------------------------------------------------------------
                            if bpy.app.version < (4, 1, 0):
                                me.calc_normals_split()

                            loops = me.loops

                            # Sort faces by smooth flag (mirrors original behaviour)
                            face_index_pairs.sort(key=lambda a: a[0].use_smooth)

                            # ---- Object header ----
                            fw('# Mesh data\n')
                            fw('o %s\n' % name_compat(ob.name))

                            if ob.parent and ob.parent.type == 'ARMATURE':
                                fw('o_arm %s\n' % name_compat(ob.parent.data.name))

                            subprogress2.step()

                            # ---- Vertices + weights ----
                            for v in me_verts:
                                fw('v %.6f %.6f %.6f\n' % v.co[:])
                                for vgroup in v.groups:
                                    fw('vw %s %.6f\n' % (
                                        name_compat(ob.vertex_groups[vgroup.group].name),
                                        vgroup.weight,
                                    ))

                            write_shape_keys(fw, ob, totverts)

                            subprogress2.step()

                            # ---- UVs ----
                            if faceuv:
                                uv_face_mapping = [None] * len(face_index_pairs)
                                uv_dict = {}
                                uv_get = uv_dict.get

                                for f, f_index in face_index_pairs:
                                    uv_ls = uv_face_mapping[f_index] = []
                                    for uv_index, l_index in enumerate(f.loop_indices):
                                        uv = uv_layer[l_index].uv
                                        uv_key = loops[l_index].vertex_index, veckey2d(uv)
                                        uv_val = uv_get(uv_key)
                                        if uv_val is None:
                                            uv_val = uv_dict[uv_key] = uv_unique_count
                                            fw('vt %.4f %.4f\n' % uv[:])
                                            uv_unique_count += 1
                                        uv_ls.append(uv_val)

                                del uv_dict, uv_get

                            subprogress2.step()

                            # ---- Normals ----
                            normals_to_idx = {}
                            no_get = normals_to_idx.get
                            loops_to_normals = [0] * len(loops)

                            for f, f_index in face_index_pairs:
                                for l_idx in f.loop_indices:
                                    no_key = veckey3d(loops[l_idx].normal)
                                    no_val = no_get(no_key)
                                    if no_val is None:
                                        no_val = normals_to_idx[no_key] = no_unique_count
                                        fw('vn %.4f %.4f %.4f\n' % no_key)
                                        no_unique_count += 1
                                    loops_to_normals[l_idx] = no_val

                            del normals_to_idx, no_get

                            subprogress2.step()

                            # ---- Faces ----
                            for f, f_index in face_index_pairs:
                                f_v = [
                                    (vi, me_verts[v_idx], l_idx)
                                    for vi, (v_idx, l_idx)
                                    in enumerate(zip(f.vertices, f.loop_indices))
                                ]

                                fw('f')
                                if faceuv:
                                    for vi, v, li in f_v:
                                        fw(" %d/%d/%d" % (
                                            totverts + v.index,
                                            totuvco + uv_face_mapping[f_index][vi],
                                            totno + loops_to_normals[li],
                                        ))
                                else:
                                    # No UV layer – write vertex/normal only
                                    for vi, v, li in f_v:
                                        fw(" %d//%d" % (
                                            totverts + v.index,
                                            totno + loops_to_normals[li],
                                        ))

                                face_vert_index += len(f_v)
                                fw('\n')

                            subprogress2.step()

                            # Accumulate global index offsets
                            totverts += len(me_verts)
                            totuvco += uv_unique_count
                            totno += no_unique_count

                            ob_for_convert.to_mesh_clear()

                    subprogress1.leave_substeps(
                        "Finished writing geometry of '%s'." % ob_main.name
                    )

                subprogress1.leave_substeps()

            # ---- Keyframes ----
            if EXPORT_KEYFRAMES:
                write_actions(context, fw)

        subprogress1.step("Finished exporting geometry")
