"""Coalesced native remeshing; never overwrite an authored or frozen world silently."""
import hashlib
import json
import time
import uuid
import bpy
from . import state, shot_freeze

_baseline = None
_identity = None
_dirty = False
_after = 0.0
_request = ''
_status = 'idle'


def _value(value):
    if isinstance(value, (str, bool, int, float)) or value is None:
        return value
    return list(value)


def _settings(owner):
    return [(prop.identifier, _value(getattr(owner, prop.identifier)))
            for prop in owner.bl_rna.properties
            if not prop.is_readonly and prop.type in {'BOOLEAN', 'INT', 'FLOAT', 'ENUM', 'STRING'}
            and prop.identifier not in {'location', 'width', 'height', 'select'}]


def _material_signature(material):
    if material is None:
        return None
    from . import actors
    tree = material.node_tree
    curves = []
    animated = set()
    if tree is not None:
        if tree.animation_data and tree.animation_data.drivers:
            raise RuntimeError('world material has locally driven parameters')
        for collection in actors._object_fcurve_collections(tree):
            for curve in collection:
                animated.add(curve.data_path)
                curves.append((curve.data_path, curve.array_index, curve.extrapolation, curve.mute,
                               [(list(k.co), list(k.handle_left), list(k.handle_right),
                                 k.interpolation, k.handle_left_type, k.handle_right_type,
                                 k.easing, k.amplitude, k.back, k.period) for k in curve.keyframe_points],
                               [_settings(modifier) for modifier in curve.modifiers]))
    nodes, links = [], []
    for node in tree.nodes if tree else ():
        image = getattr(node, 'image', None)
        if image is not None and image.is_dirty:
            raise RuntimeError('world texture has unsaved local pixel edits')
        inputs = [(socket.name, _value(socket.default_value)) for socket in node.inputs
                  if hasattr(socket, 'default_value')
                  and socket.path_from_id('default_value') not in animated]
        settings = _settings(node)
        if getattr(node, 'node_tree', None) is not None:
            raise RuntimeError('world material has local node-group dependencies')
        nodes.append((node.name, node.bl_idname, inputs, settings,
                      image.filepath if image else None))
    if tree:
        links = [(link.from_node.name, link.from_socket.identifier,
                  link.to_node.name, link.to_socket.identifier) for link in tree.links]
    return (material.name, list(material.diffuse_color), material.use_nodes,
            material.use_backface_culling, getattr(material, 'surface_render_method', None),
            nodes, sorted(links), curves)


def signature():
    from .render_drive import _mesh_fingerprint
    coll = bpy.data.collections.get(state.WORLD_COLLECTION)
    objects = []
    for obj in sorted(coll.all_objects if coll else (), key=lambda ob: ob.name):
        if obj.type != 'MESH' or obj.modifiers or obj.animation_data or obj.data.shape_keys:
            raise RuntimeError('world has local modifiers, animation or non-mesh objects')
        if obj.vertex_groups:
            raise RuntimeError('world has local vertex groups')
        objects.append((obj.name, _mesh_fingerprint(obj.data),
                        [list(row) for row in obj.matrix_basis], obj.hide_viewport, obj.hide_render,
                        [[list(loop.uv) for loop in layer.data] for layer in obj.data.uv_layers],
                        [polygon.material_index for polygon in obj.data.polygons],
                        [_material_signature(mat) for mat in obj.data.materials]))
    return hashlib.sha256(json.dumps(objects, sort_keys=True).encode('utf8')).hexdigest()


def identity():
    return state.BBS_PORT, state.BBS_ASSETS_DIR, state.peer_project


def accepted(automatic=False):
    global _baseline, _identity, _dirty, _request, _status
    _request = ''
    if not automatic:
        _dirty = False
    _identity = identity()
    try:
        _baseline = signature()
        _status = 'native world baseline ready'
    except Exception as error:
        _baseline = None
        _status = 'live world refresh paused: ' + str(error)


def changed():
    global _dirty
    _dirty = True


def ready():
    if _identity != identity() or _baseline is None:
        raise RuntimeError('world identity/baseline changed; explicitly import again')
    if shot_freeze.ist_eingefroren():
        raise RuntimeError('shot is frozen')
    if signature() != _baseline:
        raise RuntimeError('world has local edits; explicitly reimport or restore the baseline')


def pump():
    global _after, _request, _dirty, _status
    if _request and not state._world_pending:
        _request = ''
        _dirty = True
    if not _dirty or not state._connected or state._world_pending or time.monotonic() < _after:
        return
    _after = time.monotonic() + 1.0
    try:
        from . import actors, world_delta
        if not actors._can_create_actor() or world_delta._armed_region is None:
            return
        ready()
        from .core import _send, _next_seq
        fields = dict(zip(('minX', 'minY', 'minZ', 'maxX', 'maxY', 'maxZ'),
                          world_delta._armed_region))
        _request = uuid.uuid4().hex
        fields['requestId'] = _request
        state._world_pending = True
        state._world_request_id = _request
        state._world_deadline = time.time() + state.WORLD_EXPORT_TIMEOUT
        _dirty = False
        _send({'type': 'WORLD_EXPORT', 'origin': 'blender', 'uuid': 'world',
               'seq': _next_seq(), 'fields': fields})
        _status = 'live world: native remesh requested'
        state._world_status = _status
    except Exception as error:
        _status = 'live world refresh paused: ' + str(error)
        state._world_status = _status
        state._world_incomplete = True


def receive(fields):
    """Return None for a manual response; reject a stale/unsafe automatic response."""
    global _request, _status, _dirty
    if not _request or fields.get('requestId') != _request:
        return None
    _request = ''
    try:
        ready()
        if not fields.get('ok'):
            raise RuntimeError('native export failed: ' + str(fields.get('error')))
        if fields.get('chunksLoaded', 0) < fields.get('chunksTotal', 0):
            raise RuntimeError('incomplete chunk coverage; retaining previous world mesh')
        return True
    except Exception as error:
        _dirty = True
        _status = 'live world refresh paused: ' + str(error)
        state._world_status = _status
        state._world_incomplete = True
        return False
