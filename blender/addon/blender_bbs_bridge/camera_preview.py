"""Send the Blender camera to a separate BBS render view, without camera ownership fights."""
import time
import uuid
import math
import bpy
from mathutils import Vector, Euler
from . import state
from .core import _get_sync_camera, _blender_cam_to_bbs, _send, _next_seq

PROP = 'bbsbridge_render_camera'
# A second full world render leaked framegraph/target state in 26.2 and crashed
# the main sky pass after closing its window. Keep old saved scene values inert.
SUPPORTED = False
_session = uuid.uuid4().hex
_next = 0.
_was_enabled = False


def enabled():
    return bool(SUPPORTED and getattr(bpy.context.scene, PROP, False)
                and 'camera_render_preview_v1' in state.peer_capabilities)


def changed(scene, context):
    if getattr(scene, PROP, False) and getattr(scene, 'bbsbridge_drive_camera', False):
        scene.bbsbridge_drive_camera = False


def camera_fields(camera):
    fields = _blender_cam_to_bbs(camera)
    rotation = camera.matrix_world.to_quaternion().to_matrix()
    forward = rotation @ Vector((0, 0, -1))
    pitch = math.degrees(math.asin(max(-1., min(1., -forward.z))))
    yaw = (math.degrees(math.atan2(-forward.x, -forward.y)) + 360) % 360 - 180
    base = Euler((math.radians(90-pitch), 0, math.radians(-yaw)), 'XYZ').to_matrix()
    local = base.transposed() @ rotation
    roll = -math.degrees(math.atan2(local[1][0], local[0][0]))
    fields.update(pitch=pitch, yaw=yaw, roll=roll)
    return fields


def handle(fields):
    if fields.get('previewSession') == _session and fields.get('closed') is True:
        setattr(bpy.context.scene, PROP, False)


def pump():
    global _session, _next, _was_enabled
    if not SUPPORTED:
        return
    now = time.monotonic()
    if now < _next:
        return
    _next = now + .1
    if not state._connected or not state._peer_ready or 'camera_render_preview_v1' not in state.peer_capabilities:
        return
    active = enabled()
    if active and not _was_enabled:
        _session = uuid.uuid4().hex
    if not active:
        if _was_enabled:
            _send({'type':'CAMERA', 'origin':'blender', 'uuid':'render-camera-'+_session,
                   'seq':_next_seq(), 'fields':{'preview':True, 'enabled':False, 'previewSession':_session}})
        _was_enabled = False
        return
    camera = _get_sync_camera()
    if camera is None:
        return
    fields = camera_fields(camera)
    fields.update(preview=True, enabled=True, previewSession=_session)
    _send({'type':'CAMERA', 'origin':'blender', 'uuid':'render-camera-'+_session,
           'seq':_next_seq(), 'fields':fields})
    _was_enabled = True


def register():
    setattr(bpy.types.Scene, PROP, bpy.props.BoolProperty(
        name='Separate BBS-Renderkamera', default=False, options={'SKIP_SAVE'}, update=changed,
        description='Blender-Kamera in einer eigenen BBS-Vorschau rendern; BBS-Editoransicht bleibt frei'))


def unregister():
    if hasattr(bpy.types.Scene, PROP):
        delattr(bpy.types.Scene, PROP)
