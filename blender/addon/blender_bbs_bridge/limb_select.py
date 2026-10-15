"""Surface picking for plain BBS armatures; uses Blender's evaluated geometry.

No rig conversion, pose mutation, keyframe insertion or bridge message happens here.
Ray intersections and barycentric coordinates use Blender's native math utilities.
"""
import bpy
from mathutils import Vector
from mathutils.bvhtree import BVHTree
from mathutils.geometry import barycentric_transform

_cache = {}


def plain_rig(obj):
    if obj is None:
        return None
    rig = obj if obj.type == 'ARMATURE' else obj.find_armature() if obj.type == 'MESH' else None
    if (rig is None or not rig.get('bbs_uuid') or rig.get('bbs_native_source')
            or rig.get('bbs_control_ui') or not rig.is_editable):
        return None
    if any(bone.custom_shape is not None for bone in rig.pose.bones):
        return None  # A control rig keeps its own selection scheme.
    return rig


def selectable(bone):
    return not bone.hide and not bone.hide_select and (
        not bone.collections or any(c.is_visible_effectively for c in bone.collections))

def geometry(obj, rig, depsgraph):
    key = (obj.session_uid, rig.session_uid, depsgraph.as_pointer())
    if key in _cache:
        return _cache[key]
    evaluated = obj.evaluated_get(depsgraph)
    mesh = evaluated.to_mesh(preserve_all_data_layers=True, depsgraph=depsgraph)
    try:
        mesh.calc_loop_triangles()
        vertices = [v.co.copy() for v in mesh.vertices]
        triangles = [tuple(t.vertices) for t in mesh.loop_triangles]
        groups = {g.index: g.name for g in obj.vertex_groups if g.name in rig.data.bones}
        weights = [{groups[g.group]: g.weight for g in v.groups
                    if g.group in groups and g.weight > 0} for v in mesh.vertices]
        result = {'vertices': vertices, 'triangles': triangles, 'weights': weights,
                  'tree': BVHTree.FromPolygons(vertices, triangles, all_triangles=True)}
    finally:
        evaluated.to_mesh_clear()
    if len(_cache) >= 32:
        _cache.clear()
    _cache[key] = result
    return result


def weighted_bone(data, index, point):
    tri = data['triangles'][index]
    a, b, c = (data['vertices'][i] for i in tri)
    bary = barycentric_transform(point, a, b, c,
        Vector((1, 0, 0)), Vector((0, 1, 0)), Vector((0, 0, 1)))
    scores = {}
    for vertex, factor in zip(tri, bary):
        for name, weight in data['weights'][vertex].items():
            scores[name] = scores.get(name, 0.0) + max(0.0, factor) * weight
    ordered = sorted(scores.items(), key=lambda item: (-item[1], item[0]))
    if not ordered or ordered[0][1] <= 1e-6:
        return None
    if len(ordered) > 1 and abs(ordered[0][1] - ordered[1][1]) < 1e-6:
        return None  # Equal influence is ambiguous, not permission to choose a name.
    return ordered[0][0]


def visible_meshes(context):
    space = context.space_data
    for obj in context.visible_objects:
        if obj.type != 'MESH' or not obj.visible_get(view_layer=context.view_layer):
            continue
        if space is not None and space.type == 'VIEW_3D' and not obj.visible_in_viewport_get(space):
            continue
        yield obj


def ray_pick(context, rig, origin, direction):
    """Closest visible surface wins; unrelated/locked surfaces still occlude."""
    depsgraph = context.evaluated_depsgraph_get()
    closest, picked = float('inf'), None
    direction = Vector(direction).normalized()
    origin = Vector(origin)
    for obj in visible_meshes(context):
        evaluated = obj.evaluated_get(depsgraph)
        matrix = evaluated.matrix_world.copy()
        if abs(matrix.determinant()) < 1e-12:
            continue
        inverse = matrix.inverted()
        start = inverse @ origin
        vector = (inverse.to_3x3() @ direction).normalized()
        candidate = obj.find_armature() == rig and not obj.hide_select
        data = geometry(obj, rig, depsgraph) if candidate else None
        if data is not None:
            point, _normal, index, _distance = data['tree'].ray_cast(start, vector)
            hit = point is not None
        else:
            hit, point, _normal, index = evaluated.ray_cast(start, vector, depsgraph=depsgraph)
        if not hit:
            continue
        distance = ((matrix @ point) - origin).length
        if distance >= closest:
            continue
        closest, picked = distance, None
        if data is None:
            continue
        name = weighted_bone(data, index, point)
        if name is not None and selectable(rig.data.bones[name]):
            picked = (rig, name, obj, matrix @ point)
    return picked


def pick(context, coordinate):
    from bpy_extras import view3d_utils
    rig = plain_rig(context.object)
    if rig is None or context.mode != 'POSE' or context.region_data is None:
        return None
    origin = view3d_utils.region_2d_to_origin_3d(context.region, context.region_data, coordinate)
    direction = view3d_utils.region_2d_to_vector_3d(context.region, context.region_data, coordinate)
    return ray_pick(context, rig, origin, direction)


def surface(context, rig, name):
    """Evaluated triangles belonging primarily to this limb, in world space."""
    vertices = []
    depsgraph = context.evaluated_depsgraph_get()
    for obj in visible_meshes(context):
        if obj.find_armature() != rig:
            continue
        data = geometry(obj, rig, depsgraph)
        matrix = obj.evaluated_get(depsgraph).matrix_world
        for index, tri in enumerate(data['triangles']):
            points = [data['vertices'][v] for v in tri]
            center = (points[0] + points[1] + points[2]) / 3
            if weighted_bone(data, index, center) == name:
                vertices.extend(tuple(matrix @ point) for point in points)
    return vertices


@bpy.app.handlers.persistent
def invalidate(*_unused):
    _cache.clear()


def register():
    for event in ('depsgraph_update_post', 'frame_change_post', 'load_pre', 'undo_pre', 'redo_pre'):
        handlers = getattr(bpy.app.handlers, event)
        if invalidate not in handlers:
            handlers.append(invalidate)


def unregister():
    for event in ('depsgraph_update_post', 'frame_change_post', 'load_pre', 'undo_pre', 'redo_pre'):
        handlers = getattr(bpy.app.handlers, event)
        if invalidate in handlers:
            handlers.remove(invalidate)
    invalidate()
