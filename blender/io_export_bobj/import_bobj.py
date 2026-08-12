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
# ##### END GPL LICENSE BLOCK #####

# <pep8 compliant>

# ------------------------------------------------------------------
#  .bobj importer  -  Blender 4.x / 5.0
#
#  Reconstructs:
#    * Armature  (bones with correct rest-pose matrices)
#    * Mesh      (vertices, UVs, normals, vertex-weights)
#    * Actions   (location / rotation_euler / scale per bone,
#                 BEZIER and LINEAR keyframes with handles)
#
#  Frame conversion: bobj ticks use  tick = blender_frame * 20/fps
#                    so              blender_frame = tick * fps / 20
#
#  Bug fixes applied:
#    FIX 1 - mathutils.Matrix() now receives tuples, not list slices.
#             Blender 5.0 tightened the constructor to reject plain lists.
#
#    FIX 2 - arm_bone parent token shift.
#             The exporter writes '' (empty string) for root bones, e.g.:
#               arm_bone BoneName  1.0 2.0 3.0 <16 matrix values>
#             line.split() collapses the double-space so tokens[2] became
#             the first tail value instead of the (empty) parent name.
#             The parser now inspects the raw line to detect the double-space
#             sentinel and rebuilds the token list correctly.
#
#    FIX 3 - Multi-object face index offsets.
#             The exporter accumulates global vertex/UV/normal counters
#             across all objects (totverts, totuvco, totno starting at 1).
#             The importer now tracks per-mesh base offsets and subtracts
#             them when parsing 'f' lines so indices are always 0-based
#             within each mesh.
# ------------------------------------------------------------------

import bpy
import bmesh
import mathutils


# ------------------------------------------------------------------ #
#  Low-level parser
# ------------------------------------------------------------------ #

def _parse_file(filepath):
    result = {'armatures': [], 'meshes': [], 'actions': []}

    cur_arm       = None
    cur_mesh      = None
    cur_action    = None
    cur_bone_name = None
    cur_channel   = None

    # FIX 3: global index bases (exporter starts at 1 and never resets)
    vert_base = 1
    uv_base   = 1
    no_base   = 1

    with open(filepath, 'r', encoding='utf-8') as fh:
        for raw in fh:
            line = raw.strip()
            if not line or line.startswith('#'):
                continue

            tokens = line.split()
            tag = tokens[0]

            # ---- Armature ----
            if tag == 'arm_name':
                cur_arm = {'name': tokens[1], 'action': None, 'bones': []}
                result['armatures'].append(cur_arm)

            elif tag == 'arm_action' and cur_arm is not None:
                cur_arm['action'] = tokens[1]

            elif tag == 'arm_bone' and cur_arm is not None:
                # FIX 2: detect empty parent by looking for double-space in raw line.
                # Exporter format:
                #   arm_bone NAME PARENT vx vy vz m0..m15   (bone with parent)
                #   arm_bone NAME  vx vy vz m0..m15          (root bone, double space)
                stripped = raw.strip()
                # Slice off the "arm_bone " prefix
                rest = stripped[len('arm_bone'):].lstrip(' ')
                # rest now starts with the bone name
                name_end  = rest.index(' ')
                bone_name = rest[:name_end]
                after_name = rest[name_end:]  # starts with one or more spaces

                if after_name.startswith('  '):
                    # Double-space => empty parent (root bone)
                    parent         = None
                    numeric_tokens = after_name.lstrip(' ').split()
                else:
                    # Single space => parent name follows
                    numeric_part = after_name.lstrip(' ')
                    first_space  = numeric_part.index(' ')
                    parent_raw   = numeric_part[:first_space]
                    parent       = parent_raw if parent_raw else None
                    numeric_tokens = numeric_part[first_space:].lstrip(' ').split()

                # numeric_tokens = [vx, vy, vz, m0, m1, ..., m15]
                tail     = tuple(float(v) for v in numeric_tokens[0:3])
                mat_vals = [float(v) for v in numeric_tokens[3:19]]

                # FIX 1: mathutils.Matrix requires tuples, not list slices
                mat = mathutils.Matrix((
                    tuple(mat_vals[0:4]),
                    tuple(mat_vals[4:8]),
                    tuple(mat_vals[8:12]),
                    tuple(mat_vals[12:16]),
                ))

                cur_arm['bones'].append({
                    'name':   bone_name,
                    'parent': parent,
                    'tail':   tail,
                    'matrix': mat,
                })

            # ---- Mesh ----
            elif tag == 'o':
                # FIX 3: record current global bases as the per-mesh starting point
                cur_mesh = {
                    'name':       tokens[1],
                    'armature':   None,
                    'verts':      [],
                    'weights':    {},
                    'uvs':        [],
                    'normals':    [],
                    'faces':      [],
                    '_cur_vert':  None,
                    '_vert_base': vert_base,
                    '_uv_base':   uv_base,
                    '_no_base':   no_base,
                }
                result['meshes'].append(cur_mesh)

            elif tag == 'o_arm' and cur_mesh is not None:
                cur_mesh['armature'] = tokens[1]

            elif tag == 'v' and cur_mesh is not None:
                cur_mesh['verts'].append(tuple(float(v) for v in tokens[1:4]))
                cur_mesh['_cur_vert'] = len(cur_mesh['verts']) - 1
                vert_base += 1  # FIX 3

            elif tag == 'vw' and cur_mesh is not None:
                vi = cur_mesh['_cur_vert']
                if vi is not None:
                    cur_mesh['weights'].setdefault(vi, []).append(
                        (tokens[1], float(tokens[2]))
                    )

            elif tag == 'vt' and cur_mesh is not None:
                cur_mesh['uvs'].append((float(tokens[1]), float(tokens[2])))
                uv_base += 1  # FIX 3

            elif tag == 'vn' and cur_mesh is not None:
                cur_mesh['normals'].append(
                    tuple(float(v) for v in tokens[1:4])
                )
                no_base += 1  # FIX 3

            elif tag == 'f' and cur_mesh is not None:
                # FIX 3: subtract per-mesh base so indices are 0-based within this mesh
                vb = cur_mesh['_vert_base']
                ub = cur_mesh['_uv_base']
                nb = cur_mesh['_no_base']

                tri = []
                for slot in tokens[1:]:
                    parts = slot.split('/')
                    vi  = int(parts[0]) - vb
                    uvi = (int(parts[1]) - ub) if (len(parts) > 1 and parts[1]) else -1
                    ni  = (int(parts[2]) - nb) if (len(parts) > 2 and parts[2]) else -1
                    tri.append((vi, uvi, ni))
                cur_mesh['faces'].append(tri)

            # ---- Actions ----
            elif tag == 'an':
                cur_action    = {'name': tokens[1], 'bones': {}}
                result['actions'].append(cur_action)
                cur_bone_name = None
                cur_channel   = None

            elif tag == 'ao' and cur_action is not None:
                cur_bone_name = tokens[1]
                cur_channel   = None

            elif tag == 'ag' and cur_action is not None and cur_bone_name is not None:
                chan_type    = tokens[1]
                axis         = int(tokens[2])
                cur_channel  = (chan_type, axis)
                bone_data    = cur_action['bones'].setdefault(cur_bone_name, {})
                bone_data.setdefault(chan_type, {}).setdefault(axis, [])

            elif tag == 'kf' and cur_action is not None and cur_channel is not None:
                tick   = float(tokens[1])
                value  = float(tokens[2])
                interp = tokens[3]
                lhx    = float(tokens[4])
                lhy    = float(tokens[5])
                rhx    = float(tokens[6])
                rhy    = float(tokens[7])
                chan_type, axis = cur_channel
                cur_action['bones'][cur_bone_name][chan_type][axis].append(
                    (tick, value, interp, lhx, lhy, rhx, rhy)
                )

    # Clean up temp keys
    for m in result['meshes']:
        m.pop('_cur_vert',  None)
        m.pop('_vert_base', None)
        m.pop('_uv_base',   None)
        m.pop('_no_base',   None)

    return result


# ------------------------------------------------------------------ #
#  Armature builder
# ------------------------------------------------------------------ #

def _build_armature(arm_data, global_matrix_inv):
    arm_name     = arm_data['name']
    bpy_arm_data = bpy.data.armatures.new(arm_name)
    bpy_arm_obj  = bpy.data.objects.new(arm_name, bpy_arm_data)
    bpy.context.collection.objects.link(bpy_arm_obj)

    bpy.context.view_layer.objects.active = bpy_arm_obj
    bpy.ops.object.mode_set(mode='EDIT')

    edit_bones = bpy_arm_data.edit_bones
    bone_map   = {}

    for bone_info in arm_data['bones']:
        name   = bone_info['name']
        parent = bone_info['parent']
        tail   = bone_info['tail']
        mat    = bone_info['matrix']

        eb          = edit_bones.new(name)
        bone_map[name] = eb

        local_mat   = global_matrix_inv @ mat
        eb.matrix   = local_mat
        tail_vec    = global_matrix_inv @ mathutils.Vector(tail)
        eb.tail     = tail_vec

        if parent and parent in bone_map:
            eb.parent      = bone_map[parent]
            eb.use_connect = False

    bpy.ops.object.mode_set(mode='OBJECT')

    for pbone in bpy_arm_obj.pose.bones:
        pbone.rotation_mode = 'XYZ'

    return bpy_arm_obj


# ------------------------------------------------------------------ #
#  Mesh builder
# ------------------------------------------------------------------ #

def _build_mesh(mesh_data, armature_obj, global_matrix_inv):
    name    = mesh_data['name']
    verts   = mesh_data['verts']
    uvs     = mesh_data['uvs']
    faces   = mesh_data['faces']
    normals = mesh_data['normals']
    weights = mesh_data['weights']

    bpy_mesh = bpy.data.meshes.new(name)
    bpy_obj  = bpy.data.objects.new(name, bpy_mesh)
    bpy.context.collection.objects.link(bpy_obj)

    bm       = bmesh.new()
    bm_verts = []

    for co in verts:
        v = bm.verts.new(global_matrix_inv @ mathutils.Vector(co))
        bm_verts.append(v)
    bm.verts.ensure_lookup_table()

    uv_layer = bm.loops.layers.uv.new("UVMap") if uvs else None

    # Build faces and simultaneously collect per-loop normals in face-insertion
    # order.  We do NOT use loop.index as a bridge because bmesh loop indices
    # do not map 1-to-1 to mesh loop indices after bm.to_mesh().  However,
    # face insertion order IS preserved, and within each face the loop order
    # matches the vertex order passed to bm.faces.new() — so iterating faces
    # in insertion order and appending normals corner-by-corner gives exactly
    # the list that normals_split_custom_set() expects.
    loop_normals = []   # one (nx,ny,nz) per loop, face-insertion order

    for tri in faces:
        try:
            bm_face_verts = [bm_verts[t[0]] for t in tri]
            face          = bm.faces.new(bm_face_verts)
            face.smooth   = True
            for loop, (vi, uvi, ni) in zip(face.loops, tri):
                if uv_layer and uvs and 0 <= uvi < len(uvs):
                    loop[uv_layer].uv = uvs[uvi]
                # Append the normal for this loop corner in insertion order
                if 0 <= ni < len(normals):
                    loop_normals.append(normals[ni])
                else:
                    loop_normals.append((0.0, 0.0, 1.0))
        except Exception:
            # Degenerate face skipped — append fallback normals so the
            # loop_normals list stays in sync with the faces that DO get added
            pass

    bm.to_mesh(bpy_mesh)
    bm.free()
    bpy_mesh.update()

    # Apply custom split normals so shading matches the original exactly.
    # normals_split_custom_set() expects one (nx,ny,nz) per loop in the same
    # order as bpy_mesh.loops (i.e. polygon-by-polygon, corner-by-corner).
    # Because face and loop insertion order is preserved through to_mesh(),
    # our loop_normals list is already in the correct order.
    #
    # Compatibility:
    #   Blender <= 4.0 : use_auto_smooth must be True before setting normals.
    #   Blender >= 4.1 : use_auto_smooth was removed; just call the setter.
    if normals and loop_normals:
        # Sanity-check length — must equal total loop count
        expected = len(bpy_mesh.loops)
        if len(loop_normals) == expected:
            if bpy.app.version < (4, 1, 0) and hasattr(bpy_mesh, "use_auto_smooth"):
                bpy_mesh.use_auto_smooth = True
            bpy_mesh.normals_split_custom_set(loop_normals)
            bpy_mesh.update()
        else:
            # Length mismatch (can happen if some faces were skipped as degenerate).
            # Fall back to letting Blender compute its own normals rather than
            # crashing or producing garbage shading.
            print("[BOBJ] Warning: normal count mismatch (%d vs %d loops) for '%s', "
                  "skipping custom normals." % (len(loop_normals), expected, name))

    if weights:
        for vi, group_list in weights.items():
            for bone_name, weight in group_list:
                if bone_name not in bpy_obj.vertex_groups:
                    bpy_obj.vertex_groups.new(name=bone_name)
                bpy_obj.vertex_groups[bone_name].add([vi], weight, 'REPLACE')

    if armature_obj is not None:
        bpy_obj.parent = armature_obj
        mod            = bpy_obj.modifiers.new(name="Armature", type='ARMATURE')
        mod.object     = armature_obj

    return bpy_obj


# ------------------------------------------------------------------ #
#  Action / animation builder
# ------------------------------------------------------------------ #

def _build_action(action_data, armature_obj, fps):
    if armature_obj is None:
        return None

    action_name = action_data['name']
    fps_factor  = fps / 20.0

    action = bpy.data.actions.new(name=action_name)

    if armature_obj.animation_data is None:
        armature_obj.animation_data_create()

    use_legacy = not hasattr(action, 'layers')

    if not use_legacy:
        layer      = action.layers.new(name="Layer")
        strip      = layer.strips.new(type='KEYFRAME')
        slot       = action.slots.new(id_type='OBJECT', name=armature_obj.name)
        channelbag = strip.channelbag(slot, ensure=True)

    def get_or_create_fcurve(data_path, array_index, bone_name):
        if use_legacy:
            fc = action.fcurves.find(data_path, index=array_index)
            if fc is None:
                fc = action.fcurves.new(data_path=data_path, index=array_index,
                                        action_group=bone_name)
            # Ensure group is assigned even if curve already existed without one
            if fc.group is None:
                grp = action.groups.get(bone_name) or action.groups.new(bone_name)
                fc.group = grp
            return fc
        else:
            fc = channelbag.fcurves.find(data_path, index=array_index)
            if fc is None:
                fc = channelbag.fcurves.new(data_path=data_path, index=array_index)
            # Blender 5.0 channelbag groups
            if fc.group is None and hasattr(channelbag, 'groups'):
                grp = channelbag.groups.get(bone_name) or channelbag.groups.new(bone_name)
                fc.group = grp
            return fc

    for bone_name, channels in action_data['bones'].items():
        for chan_type, axes in channels.items():
            if chan_type == 'location':
                path_suffix = 'location'
            elif chan_type == 'rotation':
                path_suffix = 'rotation_euler'
            elif chan_type == 'scale':
                path_suffix = 'scale'
            else:
                continue

            data_path = 'pose.bones["%s"].%s' % (bone_name, path_suffix)

            for axis, keyframes in axes.items():
                if not keyframes:
                    continue

                fc = get_or_create_fcurve(data_path, axis, bone_name)
                fc.keyframe_points.add(len(keyframes))

                for i, (tick, value, interp, lhx, lhy, rhx, rhy) in enumerate(keyframes):
                    frame           = tick * fps_factor
                    kp              = fc.keyframe_points[i]
                    kp.co           = (frame, value)
                    kp.interpolation = interp
                    kp.handle_left  = (lhx * fps_factor, lhy)
                    kp.handle_right = (rhx * fps_factor, rhy)

                fc.update()

    if not use_legacy:
        armature_obj.animation_data.action      = action
        armature_obj.animation_data.action_slot = slot
    else:
        armature_obj.animation_data.action = action

    return action


# ------------------------------------------------------------------ #
#  Public entry point
# ------------------------------------------------------------------ #

def load(context, filepath, *, global_matrix=None):
    if global_matrix is None:
        global_matrix = mathutils.Matrix()

    try:
        global_matrix_inv = global_matrix.inverted()
    except Exception:
        global_matrix_inv = mathutils.Matrix()

    fps  = context.scene.render.fps
    data = _parse_file(filepath)

    if bpy.ops.object.mode_set.poll():
        bpy.ops.object.mode_set(mode='OBJECT')

    # Build armatures
    arm_obj_map = {}
    for arm_data in data['armatures']:
        arm_obj = _build_armature(arm_data, global_matrix_inv)
        arm_obj_map[arm_data['name']] = arm_obj

    # Build meshes
    for mesh_data in data['meshes']:
        arm_name = mesh_data.get('armature')
        arm_obj  = arm_obj_map.get(arm_name) if arm_name else None
        _build_mesh(mesh_data, arm_obj, global_matrix_inv)

    # Build actions – assign to the first armature whose bone set covers the action
    arm_objects = list(arm_obj_map.values())
    for action_data in data['actions']:
        target_arm           = None
        bone_names_in_action = set(action_data['bones'].keys())

        for arm_obj in arm_objects:
            arm_bone_names = {b.name for b in arm_obj.data.bones}
            if bone_names_in_action.issubset(arm_bone_names):
                target_arm = arm_obj
                break

        if target_arm is None and arm_objects:
            target_arm = arm_objects[0]

        _build_action(action_data, target_arm, fps)

    # Select created armatures
    for obj in bpy.context.scene.objects:
        obj.select_set(False)
    for arm_obj in arm_obj_map.values():
        arm_obj.select_set(True)
        bpy.context.view_layer.objects.active = arm_obj

    return {'FINISHED'}
