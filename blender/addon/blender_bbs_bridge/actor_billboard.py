"""BillboardForm image quad; geometry and UV convention follow BBS' renderer."""
import base64
import hashlib
import json
import math
from pathlib import Path
import tempfile
import bpy
from .actor_texture import _resolve_texture_png, _load_image


def image_for(data):
    if float(data.get('width', 0)) * float(data.get('height', 0)) > 16 * 1024 * 1024:
        raise ValueError('Billboard image exceeds 16 megapixels')
    path = _resolve_texture_png(data['texture'])
    if not path and data.get('file') and Path(data['file']).is_file():
        path = data['file']
    if not path and data.get('imageBytes'):
        encoded = data['imageBytes']
        if len(encoded) > 6 * 1024 * 1024:
            raise ValueError('Billboard image payload exceeds limit')
        raw = base64.b64decode(encoded, validate=True)
        folder = Path(tempfile.gettempdir()) / 'bbs-bridge-image-cache'
        folder.mkdir(exist_ok=True)
        path = folder / (hashlib.sha256(raw).hexdigest() + '.png')
        if not path.exists():
            path.write_bytes(raw)
    if not path:
        raise RuntimeError('Billboard image unavailable: ' + data['texture'] +
                           ' ' + str(data.get('error', '')))
    image = _load_image(str(path))
    if image is None:
        raise RuntimeError('Billboard image could not be decoded: ' + str(path))
    if data.get('imageBytes') and not image.packed_file:
        image.pack()
    return image


def quad(data):
    w, h = float(data['width']), float(data['height'])
    if not 0 < w <= 32768 or not 0 < h <= 32768:
        raise ValueError('Invalid billboard dimensions')
    left, top, right, bottom = data.get('crop', (0, 0, 0, 0))
    u0, v0, u1, v1 = left / w, top / h, 1-right/w, 1-bottom/h
    gw, gh = w, h
    a, b, c, d = u0, v0, u1, v1
    if data.get('resizeCrop'):
        gw, gh = w-left-right, h-top-bottom
        a, b, c, d = 0, 0, 1, 1
    if gw <= 0 or gh <= 0:
        raise ValueError('Billboard crop removes the complete image')
    rx, ry = min(1., gh/gw), min(1., gw/gh)
    points = [((a-.5)*ry, -(b-.5)*rx), ((c-.5)*ry, -(b-.5)*rx),
              ((c-.5)*ry, -(d-.5)*rx), ((a-.5)*ry, -(d-.5)*rx)]
    # Preserve BBS' existing UV transform, including its centerY / width convention.
    cx, cy = (left+w-right)/2/w, (top+h-bottom)/2/w
    angle = math.radians(data.get('rotation', 0))
    co, si = math.cos(angle), math.sin(angle)
    ox, oy = data.get('offsetX', 0)/w, data.get('offsetY', 0)/h
    uvs=[]
    for u,v in ((u0,v0),(u1,v0),(u1,v1),(u0,v1)):
        x,y=u-cx+ox,v-cy+oy
        uvs.append((cx+co*x-si*y, 1-(cy+si*x+co*y)))
    return points, uvs


def apply(obj, replay):
    if replay.get('formType') != 'bbs:billboard':
        return
    data = replay.get('properties', {}).get('billboard')
    if not isinstance(data, dict):
        raise RuntimeError('BBS has not supplied billboard image data')
    digest = hashlib.sha256(json.dumps(data, sort_keys=True).encode()).hexdigest()
    mesh_obj = next((c for c in obj.children_recursive if c.get('bbs_billboard_mesh')), None)
    if mesh_obj is not None and obj.get('bbs_billboard_digest') == digest:
        return
    if not data.get('texture'):
        if mesh_obj:
            mesh_obj.hide_render = mesh_obj.hide_viewport = True
        obj['bbs_billboard_digest'] = digest
        return
    image = image_for(data)
    points, uvs = quad(data)
    if mesh_obj is None:
        mesh = bpy.data.meshes.new(obj.name + '_Image')
        mesh_obj = bpy.data.objects.new(obj.name + '_Image', mesh)
        obj.users_collection[0].objects.link(mesh_obj)
        mesh_obj.parent = obj
        mesh_obj['bbs_billboard_mesh'] = True
    mesh = mesh_obj.data
    mesh.clear_geometry()
    facing = bool(data.get('facingCamera'))
    mesh.from_pydata([(x,y,0) if facing else (x,0,y) for x,y in points], [], [(0,1,2,3)])
    uv = mesh.uv_layers.new(name='UVMap') if not mesh.uv_layers else mesh.uv_layers[0]
    for loop, value in zip(mesh.polygons[0].loop_indices, uvs):
        uv.data[loop].uv = value
    mesh.update()
    mat = mesh.materials[0] if mesh.materials else bpy.data.materials.new(
        'BBS_Billboard__' + str(obj.get('bbs_uuid', ''))[:8])
    if not mesh.materials:
        mesh.materials.append(mat)
    mat.use_nodes = True
    nodes, links = mat.node_tree.nodes, mat.node_tree.links
    tex = nodes.get('BBS_BillboardTexture') or nodes.new('ShaderNodeTexImage')
    tex.name = 'BBS_BillboardTexture'
    tex.image = image
    tex.interpolation = 'Linear' if data.get('linear') else 'Closest'
    tint = nodes.get('BBS_BillboardTint') or nodes.new('ShaderNodeMixRGB')
    tint.name = 'BBS_BillboardTint'; tint.blend_type = 'MULTIPLY'; tint.inputs[0].default_value = 1
    tint.inputs[2].default_value = data.get('color', (1,1,1,1))
    links.new(tex.outputs['Color'], tint.inputs[1])
    bsdf = next(n for n in nodes if n.type == 'BSDF_PRINCIPLED')
    if data.get('shading', True):
        links.new(tint.outputs[0], bsdf.inputs['Base Color'])
    else:
        for link in list(bsdf.inputs['Base Color'].links):
            links.remove(link)
        bsdf.inputs['Base Color'].default_value = (0,0,0,1)
    alpha = nodes.get('BBS_BillboardAlpha') or nodes.new('ShaderNodeMath')
    alpha.name='BBS_BillboardAlpha'; alpha.operation='MULTIPLY'
    alpha.inputs[1].default_value=data.get('color', (1,1,1,1))[3]
    links.new(tex.outputs['Alpha'], alpha.inputs[0]); links.new(alpha.outputs[0], bsdf.inputs['Alpha'])
    bsdf.inputs['Roughness'].default_value = 1
    bsdf.inputs['Emission Strength'].default_value = 0 if data.get('shading', True) else 1
    links.new(tint.outputs[0], bsdf.inputs['Emission Color'])
    mat.surface_render_method = 'DITHERED'
    constraint = mesh_obj.constraints.get('BBS_BillboardCamera')
    if facing:
        if bpy.context.scene.camera is None:
            raise RuntimeError('Camera-facing billboard requires a Blender scene camera')
        if constraint is None:
            constraint=mesh_obj.constraints.new('COPY_ROTATION');constraint.name='BBS_BillboardCamera'
        constraint.target=bpy.context.scene.camera
    elif constraint is not None:
        mesh_obj.constraints.remove(constraint)
    mesh_obj.hide_viewport = not replay.get('enabled', True)
    mesh_obj.hide_render = not replay.get('enabled', True)
    obj['bbs_billboard_digest'] = digest
