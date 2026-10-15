"""New glTF/GLB imports become model actors through the existing film merge protocol."""
import pathlib
import time
import uuid

import bpy

from . import actor_channel_sync, film_structure, native_basis, rigid_model_export, state
from .core import _send, _next_seq

_queue = []
_pending = {}
_status = 'ready for new glTF/GLB models'


class glTF2ImportUserExtension:
    # This is Blender glTF's supported extension point, not an object/depsgraph scan.
    def gather_import_scene_after_animation_hook(self, gltf_scene, scene, gltf):
        if not state._connected or not getattr(scene, 'bbsbridge_import_new_models', True):
            return
        objects = {node.blender_object for node in gltf.vnodes.values()
                   if getattr(node, 'blender_object', None) is not None}
        roots = [obj for obj in objects if obj.parent not in objects]
        for root in roots:
            if any(obj.type == 'MESH' for obj in [root]+list(root.children_recursive)):
                _queue.append((root, str(gltf.filename), state.bound_film,
                               tuple(state._connected_endpoint or ())))


def submit(root, source):
    """Explicit retry/adoption for an import that predates enabling the hook."""
    if not state._connected or not state.bound_film or state.film_switch_detected:
        raise RuntimeError('Connect to a safely bound BBS film first')
    if bpy.context.mode != 'OBJECT':
        raise RuntimeError('Leave Edit/Pose Mode before importing a new model')
    if root.get('bbs_uuid'):
        raise RuntimeError('Object is already a BBS actor')
    pending_id = root.get('bbs_pending_model_import')
    if pending_id:
        record = _pending.get(str(pending_id))
        if record is None:
            raise RuntimeError('Previous model import has no receipt; do not create a duplicate')
        if record['film'] != state.bound_film or record['endpoint'] != tuple(state._connected_endpoint or ()):
            raise RuntimeError('Retry belongs to another BBS film/connection')
        record['deadline'] = time.monotonic()+30.
        record.pop('timeout', None)
        _send(record['message'])
        return record['rig']
    if 'film_structure_write_v1' not in state.peer_capabilities:
        raise RuntimeError('BBS does not support safe film structure merge')
    rigid_model_export.objects_for(root)
    assets = pathlib.Path(state.BBS_ASSETS_DIR).resolve()
    if not assets.is_dir():
        raise RuntimeError('BBS asset directory is not available on this machine')
    token = str(uuid.uuid4())
    source = pathlib.Path(source)
    label = source.parent.parent.name if source.stem == 'model' and source.parent.name == 'source' else source.stem
    model_id = 'blender_'+uuid.uuid4().hex[:12]
    directory = assets/'models'/model_id
    directory.mkdir(parents=True, exist_ok=False)
    rig, stage, originals = rigid_model_export.build(root, label or root.name)
    try:
        rigid_model_export.write(rig, directory)
        native_basis.apply(rig, str(directory/'model.bobj'))
    except Exception:
        rigid_model_export.discard(rig, stage)
        raise
    rig['bbs_uuid'] = token
    rig['bbs_form'] = model_id
    rig['bbs_form_type'] = 'bbs:model'
    rig['bbs_actor_snapshot_v1'] = ''
    rig['bbs_model_import_awaiting_echo'] = True
    rig['bbs_native_source'] = str(directory/'native_rig.blend')
    rig['bbs_model_import_source'] = str(source)
    rig.location = root.matrix_world.translation
    # Real initial curves keep disposable recording previews from appearing as
    # unkeyed local edits before the first authoritative BBS recording arrives.
    for axis in range(3):
        rig.keyframe_insert(data_path='location', index=axis, frame=0)
    rig.keyframe_insert(data_path='rotation_euler', index=2, frame=0)
    # Film merge creates a fresh replay with empty tracks. Preserve the actual
    # local baseline for every root path, not just paths changed by that empty echo.
    scale = bpy.context.scene.render.fps/(bpy.context.scene.render.fps_base or 1.)/20.
    actor_channel_sync.remember(rig, {'uuid':token,'channels':{},'properties':{}}, scale)
    actors = bpy.data.collections.get(state.ACTORS_COLLECTION)
    if actors is None:
        actors = bpy.data.collections.new(state.ACTORS_COLLECTION)
        bpy.context.scene.collection.children.link(actors)
    actors.objects.link(rig)
    stage.objects.unlink(rig)
    rig.hide_set(True)
    stage.hide_viewport = stage.hide_render = True
    request = uuid.uuid4().hex
    rig['bbs_model_import_status'] = 'waiting for BBS actor creation'
    _pending[request] = {'rig':rig, 'stage':stage, 'originals':originals,
                         'deadline':time.monotonic()+30., 'film':state.bound_film,
                         'scene':bpy.context.scene, 'endpoint':tuple(state._connected_endpoint or ())}
    root['bbs_pending_model_import'] = request
    # Only the new UUID travels: no deletion, reorder or writes to existing actors.
    message = {'type':'FILM_STRUCTURE_IMPORT', 'origin':'blender', 'uuid':'filmStructure',
           'seq':_next_seq(), 'fields':{'requestId':request, 'film':state.bound_film,
           'mode':'merge', 'replays':[{'uuid':token, 'label':label or root.name,
           'formType':'bbs:model', 'formModel':model_id, 'enabled':True, 'isGroup':False}]}}
    _pending[request]['message'] = message
    _send(message)
    return rig


def handle_result(fields):
    global _status
    request = str(fields.get('requestId') or '')
    record = _pending.get(request)
    if record is None:
        return
    rig = record['rig']
    try:
        rig.name
    except ReferenceError:
        _status = 'import actor missing locally; recover the existing BBS UUID, do not create a duplicate'
        _pending.pop(request)
        return
    if not fields.get('ok'):
        record['timeout'] = True
        _status = 'model import failed: '+str(fields.get('error') or 'unknown error')
        rig['bbs_model_import_status'] = _status
        return
    if (bpy.context.scene != record['scene'] or bpy.context.mode != 'OBJECT'
            or state.bound_film != record['film']
            or tuple(state._connected_endpoint or ()) != record['endpoint']):
        record['ack'] = dict(fields)
        _status = 'actor created; return to the original scene/film in Object Mode'
        rig['bbs_model_import_status'] = _status
        return
    _pending.pop(request)
    selected = list(bpy.context.selected_objects)
    active = bpy.context.view_layer.objects.active
    archive = bpy.data.collections.get('BBS_Model_Import_Originals')
    if archive is None:
        archive = bpy.data.collections.new('BBS_Model_Import_Originals')
        bpy.context.scene.collection.children.link(archive)
    archive.hide_viewport = archive.hide_render = True
    for obj in record['originals']:
        if 'bbs_pending_model_import' in obj:
            del obj['bbs_pending_model_import']
        archive.objects.link(obj)
        for collection in list(obj.users_collection):
            if collection != archive:
                collection.objects.unlink(obj)
    target = bpy.data.collections[state.ACTORS_COLLECTION]
    for obj in list(record['stage'].objects):
        target.objects.link(obj)
        record['stage'].objects.unlink(obj)
    bpy.data.collections.remove(record['stage'])
    rig.hide_set(False)
    rig.update_tag()
    bpy.context.view_layer.update()
    if active in record['originals']:
        for obj in selected:
            if obj in record['originals']:
                obj.select_set(False)
        rig.select_set(True)
        bpy.context.view_layer.objects.active = rig
    _status = 'in BBS: '+rig.name
    rig['bbs_model_import_status'] = _status
    # Explicitly use the incremental poll path: an unmarked export reply is a full import.
    if not state._actors_pending:
        state._actors_pending = state._live_polling = True
        state._actors_deadline = time.time()+state.ACTORS_EXPORT_TIMEOUT
        _send({'type':'ACTORS_EXPORT', 'origin':'blender', 'uuid':'actors',
               'seq':_next_seq(), 'fields':{}})


def pump():
    global _status
    for request, record in list(_pending.items()):
        if record.get('ack'):
            handle_result(record['ack'])
        elif not record.get('timeout') and time.monotonic() > record['deadline']:
            record['timeout'] = True
            _status = 'BBS model import unconfirmed; original objects kept'
            record['rig']['bbs_model_import_status'] = _status
    if not _queue:
        return
    if any(window.modal_operators for window in bpy.context.window_manager.windows):
        return
    root, source, film, endpoint = _queue.pop(0)
    try:
        if film != state.bound_film or endpoint != tuple(state._connected_endpoint or ()):
            raise RuntimeError('BBS film/connection changed after glTF import; no actor created')
        submit(root, source)
        _status = 'new model sent to BBS'
    except Exception as error:
        _status = 'model import failed: '+str(error)
        print('[bbs-bridge] '+_status)


class BBSBRIDGE_OT_send_new_model(bpy.types.Operator):
    bl_idname = 'bbsbridge.send_new_model'
    bl_label = 'Neues Modell nach BBS'
    bl_description = 'Import the selected rigid hierarchy as a new BBS model actor; originals are retained'

    def execute(self, context):
        root = context.object
        if root is None:
            self.report({'ERROR'}, 'Select the imported model first')
            return {'CANCELLED'}
        while root.parent is not None:
            root = root.parent
        try:
            submit(root, root.name)
        except Exception as error:
            self.report({'ERROR'}, str(error))
            return {'CANCELLED'}
        return {'FINISHED'}


def register():
    bpy.utils.register_class(BBSBRIDGE_OT_send_new_model)
    bpy.types.Scene.bbsbridge_import_new_models = bpy.props.BoolProperty(
        name='Neue glTF-Modelle nach BBS', default=True,
        description='Create a BBS model actor for new rigid glTF/GLB imports while connected')


def unregister():
    _queue.clear()
    bpy.utils.unregister_class(BBSBRIDGE_OT_send_new_model)
    if hasattr(bpy.types.Scene, 'bbsbridge_import_new_models'):
        del bpy.types.Scene.bbsbridge_import_new_models
